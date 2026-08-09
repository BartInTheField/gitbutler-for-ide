package me.inthefield.gitbutlerforjetbrains.toolwindow

import com.intellij.ide.util.PropertiesComponent
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.LocalFilePath
import com.intellij.openapi.vcs.changes.ChangeListManager
import com.intellij.openapi.ui.DoNotAskOption
import com.intellij.openapi.ui.MessageDialogBuilder
import com.intellij.openapi.wm.ToolWindowManager
import me.inthefield.gitbutlerforjetbrains.commit.GitButlerCommitSelection
import me.inthefield.gitbutlerforjetbrains.core.ButResult
import me.inthefield.gitbutlerforjetbrains.core.GitButlerService
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable
import java.awt.datatransfer.UnsupportedFlavorException
import javax.swing.DropMode
import javax.swing.JComponent
import javax.swing.JTree
import javax.swing.TransferHandler
import javax.swing.tree.TreePath

/**
 * Row payloads the tool window tree can export or accept during drag-and-drop, decoupled
 * from the panel's private tree-node classes: the panel adapts its own nodes into these via
 * the `rowPayload` lambda passed to [GitButlerTreeDnD.install].
 */
sealed interface DnDRow {
    data class Change(val relativePath: String) : DnDRow
    data class Branch(val name: String) : DnDRow
    data class Commit(val cliId: String, val message: String) : DnDRow
}

/**
 * Installs drag-and-drop on the GitButler status tree.
 *
 * Drag source: a selection is draggable only if every row in it is a [DnDRow.Change]; the
 * dragged payload is the list of repo-relative paths.
 *
 * Drop target: dropping onto a [DnDRow.Branch] preselects that branch in
 * [GitButlerCommitSelection] and opens the IDE commit UI so the user finishes the commit
 * through the default flow. Dropping onto a [DnDRow.Commit] amends the dragged files into it,
 * after a confirmation the user can permanently suppress. The amend runs through the panel's
 * `runOperation` so it shares the single in-flight gate, notifications, and tree refresh with
 * every other GitButler mutation.
 */
object GitButlerTreeDnD {

    private val CHANGE_PATHS_FLAVOR = DataFlavor(List::class.java, "GitButler uncommitted change paths")
    private const val AMEND_CONFIRM_SUPPRESSED_KEY = "gitbutler.amend.confirm.suppressed"
    private val LOG = com.intellij.openapi.diagnostic.Logger.getInstance(GitButlerTreeDnD::class.java)

    fun install(
        tree: JTree,
        project: Project,
        rowPayload: (TreePath) -> DnDRow?,
        isOperationInFlight: () -> Boolean,
        runOperation: (title: String, successMessage: String, operation: () -> ButResult<Unit>) -> Unit,
    ) {
        tree.dragEnabled = true
        tree.dropMode = DropMode.ON
        tree.transferHandler = object : TransferHandler() {

            // Gating on isOperationInFlight mirrors the context-menu actions' update():
            // during a mutation the drag is visibly refused instead of silently no-oping
            // in runOperation after the user already confirmed.
            override fun getSourceActions(c: JComponent): Int {
                if (isOperationInFlight()) return NONE
                val source = c as? JTree ?: return NONE
                return if (draggableChangePaths(selectedPayloads(source, rowPayload)) != null) COPY else NONE
            }

            override fun createTransferable(c: JComponent): Transferable? {
                val source = c as? JTree ?: return null
                val paths = draggableChangePaths(selectedPayloads(source, rowPayload)) ?: return null
                return ChangePathsTransferable(paths)
            }

            override fun canImport(support: TransferSupport): Boolean {
                if (isOperationInFlight()) return false
                if (!support.isDrop || !support.isDataFlavorSupported(CHANGE_PATHS_FLAVOR)) return false
                return dropTarget(dropRowPayload(support, rowPayload)) != null
            }

            override fun importData(support: TransferSupport): Boolean {
                if (!support.isDrop || isOperationInFlight()) return false
                val target = dropTarget(dropRowPayload(support, rowPayload)) ?: return false
                val paths = extractPaths(support.transferable)
                if (paths.isNullOrEmpty()) return false

                return when (target) {
                    is DnDRow.Branch -> {
                        dropOnBranch(project, target, paths)
                        true
                    }

                    is DnDRow.Commit -> {
                        dropOnCommit(project, target, paths, runOperation)
                        true
                    }

                    is DnDRow.Change -> false
                }
            }
        }
    }

    /**
     * The repo-relative paths of [payloads] if every one is present and is a [DnDRow.Change] —
     * the only selection shape the tree may drag — else null.
     */
    fun draggableChangePaths(payloads: List<DnDRow?>): List<String>? {
        if (payloads.isEmpty() || payloads.any { it !is DnDRow.Change }) return null
        return payloads.map { (it as DnDRow.Change).relativePath }
    }

    /**
     * [payload] if it is a row the tree accepts drops on: a [DnDRow.Branch], or a [DnDRow.Commit]
     * with a usable id — else null.
     */
    fun dropTarget(payload: DnDRow?): DnDRow? = when (payload) {
        is DnDRow.Branch -> payload
        is DnDRow.Commit -> payload.takeIf { it.cliId.isNotBlank() }
        else -> null
    }

    private fun selectedPayloads(tree: JTree, rowPayload: (TreePath) -> DnDRow?): List<DnDRow?> =
        tree.selectionPaths?.map(rowPayload) ?: emptyList()

    private fun dropRowPayload(support: TransferHandler.TransferSupport, rowPayload: (TreePath) -> DnDRow?): DnDRow? {
        val dropLocation = support.dropLocation as? JTree.DropLocation ?: return null
        val path = dropLocation.path ?: return null
        return rowPayload(path)
    }

    @Suppress("UNCHECKED_CAST")
    private fun extractPaths(transferable: Transferable): List<String>? {
        if (!transferable.isDataFlavorSupported(CHANGE_PATHS_FLAVOR)) return null
        return try {
            transferable.getTransferData(CHANGE_PATHS_FLAVOR) as? List<String>
        } catch (e: Exception) {
            LOG.warn("Failed to read dragged change paths", e)
            null
        }
    }

    private fun dropOnBranch(project: Project, branch: DnDRow.Branch, relativePaths: List<String>) {
        GitButlerCommitSelection.getInstance(project).selectedBranch = branch.name
        val toolWindows = ToolWindowManager.getInstance(project)
        val commitWindow = toolWindows.getToolWindow("Commit") ?: toolWindows.getToolWindow("Version Control")
        // Set the inclusion after the tool window is up: the non-modal commit handler may not
        // exist before the Commit tool window has been created for the first time.
        commitWindow?.activate { includeOnlyDraggedFiles(project, relativePaths) }
    }

    /**
     * Replaces the Commit tool window's checked state with exactly the dragged files: tracked
     * changes are included as [com.intellij.openapi.vcs.changes.Change]s, untracked ones as
     * [com.intellij.openapi.vcs.FilePath]s (the shape the non-modal commit UI's unversioned
     * nodes use). Best effort — when the non-modal handler is unavailable (e.g. commit dialog
     * mode) the window still opens with the branch preselected.
     */
    private fun includeOnlyDraggedFiles(project: Project, relativePaths: List<String>) {
        val root = GitButlerService.getInstance(project).workspaceRepository()?.root?.path ?: return
        val changeListManager = ChangeListManager.getInstance(project)
        val items: List<Any> = relativePaths.map { rel ->
            val filePath = LocalFilePath("$root/$rel", false)
            changeListManager.getChange(filePath) ?: filePath
        }
        if (!CommitInclusion.setInclusion(project, changeListManager.defaultChangeList, items)) {
            LOG.warn("Non-modal commit workflow handler unavailable; dragged files not auto-included")
        }
    }

    private fun dropOnCommit(
        project: Project,
        commit: DnDRow.Commit,
        relativePaths: List<String>,
        runOperation: (title: String, successMessage: String, operation: () -> ButResult<Unit>) -> Unit,
    ) {
        val firstLine = commit.message.lineSequence().firstOrNull().orEmpty()
        val properties = PropertiesComponent.getInstance(project)
        val confirmed = properties.getBoolean(AMEND_CONFIRM_SUPPRESSED_KEY, false) ||
            MessageDialogBuilder.yesNo(
                "Amend Files",
                "Amend ${relativePaths.size} file(s) into commit \"$firstLine\"?",
            ).doNotAsk(AmendConfirmationOption(project)).ask(project)
        if (!confirmed) return

        val root = GitButlerService.getInstance(project).workspaceRepository()?.root?.path
        if (root == null) {
            notify(project, "No git repository found for this project", NotificationType.ERROR)
            return
        }
        val absolutePaths = relativePaths.map { "$root/$it" }

        runOperation(
            "Amending ${firstLine.ifBlank { commit.cliId }}",
            "Amended ${absolutePaths.size} file(s) into $firstLine",
        ) {
            GitButlerService.getInstance(project).amend(commit.cliId, absolutePaths)
        }
    }

    private fun notify(project: Project, message: String, type: NotificationType) {
        NotificationGroupManager.getInstance().getNotificationGroup("GitButler")
            .createNotification(message, type)
            .notify(project)
    }

    private class AmendConfirmationOption(private val project: Project) : DoNotAskOption.Adapter() {
        override fun getDoNotShowMessage(): String = "Don't ask again"

        // Persist only on Yes: suppressing after a "No" would silently amend future drops.
        override fun rememberChoice(isSelected: Boolean, exitCode: Int) {
            if (isSelected && exitCode == com.intellij.openapi.ui.Messages.YES) {
                PropertiesComponent.getInstance(project).setValue(AMEND_CONFIRM_SUPPRESSED_KEY, true, false)
            }
        }
    }

    private class ChangePathsTransferable(private val paths: List<String>) : Transferable {
        override fun getTransferDataFlavors(): Array<DataFlavor> = arrayOf(CHANGE_PATHS_FLAVOR)

        override fun isDataFlavorSupported(flavor: DataFlavor): Boolean = flavor == CHANGE_PATHS_FLAVOR

        override fun getTransferData(flavor: DataFlavor): Any {
            if (flavor != CHANGE_PATHS_FLAVOR) throw UnsupportedFlavorException(flavor)
            return paths
        }
    }
}
