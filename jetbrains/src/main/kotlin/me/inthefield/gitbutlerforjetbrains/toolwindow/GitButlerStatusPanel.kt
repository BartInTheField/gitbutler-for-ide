package me.inthefield.gitbutlerforjetbrains.toolwindow

import com.intellij.icons.AllIcons
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.ui.PopupHandler
import com.intellij.openapi.vcs.FileStatus
import com.intellij.openapi.vcs.LocalFilePath
import com.intellij.openapi.vcs.changes.ChangeListManager
import com.intellij.openapi.vcs.changes.actions.diff.ShowDiffAction
import com.intellij.openapi.vcs.changes.Change
import com.intellij.util.ui.update.MergingUpdateQueue
import com.intellij.util.ui.update.Update
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryChangeListener
import git4idea.GitContentRevision
import git4idea.GitRevisionNumber
import com.intellij.ui.ColoredTreeCellRenderer
import com.intellij.ui.DoubleClickListener
import com.intellij.ui.JBColor
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.tree.TreeUtil
import com.intellij.ui.treeStructure.Tree
import com.intellij.vcs.log.impl.HashImpl
import com.intellij.vcs.log.impl.VcsLogNavigationUtil
import me.inthefield.gitbutlerforjetbrains.core.ButResult
import me.inthefield.gitbutlerforjetbrains.core.ButStack
import me.inthefield.gitbutlerforjetbrains.core.GitButlerService
import me.inthefield.gitbutlerforjetbrains.core.UncommittedChange
import me.inthefield.gitbutlerforjetbrains.core.ButCommit
import me.inthefield.gitbutlerforjetbrains.core.VirtualBranch
import me.inthefield.gitbutlerforjetbrains.core.WorkspaceStatus
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.SimpleToolWindowPanel
import java.awt.event.KeyEvent
import java.awt.event.MouseEvent
import javax.swing.JComponent
import javax.swing.JTree
import javax.swing.KeyStroke
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel

/**
 * Renders the GitButler workspace as a tree — unassigned changes, then each stack's
 * branches, their commits, and each commit's changed files — visually analogous to
 * `but status`. A Refresh toolbar button re-runs the load. `status()` always runs on a
 * pooled thread; every Swing mutation happens on the EDT.
 */
class GitButlerStatusPanel(private val project: Project) : SimpleToolWindowPanel(true, true) {

    private val rootNode = DefaultMutableTreeNode()
    private val treeModel = DefaultTreeModel(rootNode)

    /** True while a pull/unapply/push runs; gates the actions so operations never overlap. */
    private var operationInFlight = false
    private val tree = Tree(treeModel).apply {
        isRootVisible = false
        showsRootHandles = true
        cellRenderer = StatusCellRenderer()
        emptyText.text = "Loading GitButler status…"
    }

    init {
        toolbar = buildToolbar()
        setContent(JBScrollPane(tree))
        installActivation()
        installContextMenu()
        GitButlerTreeDnD.install(
            tree = tree,
            project = project,
            rowPayload = { path ->
                when (val payload = (path.lastPathComponent as? DefaultMutableTreeNode)?.userObject) {
                    is ChangeNode -> DnDRow.Change(payload.change.filePath)
                    is BranchNode -> DnDRow.Branch(payload.branch.name)
                    is CommitNode -> DnDRow.Commit(payload.commit.effectiveId(), payload.commit.message)
                    else -> null
                }
            },
            isOperationInFlight = { operationInFlight },
            runOperation = ::runOperation,
        )
    }

    /**
     * Makes rows navigable: double-clicking or pressing Enter on a change row — an
     * uncommitted change, or a file inside a commit — opens its diff. Commit rows are
     * containers now, so a double-click on one just expands/collapses it, like a branch.
     * [activate] returns true only when it handled the node, so a double-click on a
     * container still toggles it.
     */
    private fun installActivation() {
        object : DoubleClickListener() {
            override fun onDoubleClick(event: MouseEvent): Boolean {
                val path = tree.getPathForLocation(event.x, event.y) ?: return false
                return activate(path.lastPathComponent)
            }
        }.installOn(tree)

        tree.registerKeyboardAction(
            {
                val path = tree.selectionPath ?: return@registerKeyboardAction
                // Non-navigable rows keep their default Enter behaviour: toggle expansion.
                if (!activate(path.lastPathComponent)) {
                    if (tree.isExpanded(path)) tree.collapsePath(path) else tree.expandPath(path)
                }
            },
            KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0),
            JComponent.WHEN_FOCUSED,
        )
    }

    /** Dispatches on the node payload; returns true iff activation consumed the event. */
    private fun activate(component: Any?): Boolean {
        return when (val payload = (component as? DefaultMutableTreeNode)?.userObject) {
            is ChangeNode -> {
                openChangeDiff(payload.change)
                true
            }

            is CommitFileNode -> {
                openCommitFileDiff(payload.commit, payload.change)
                true
            }

            else -> false
        }
    }

    private fun openChangeDiff(change: UncommittedChange) {
        val root = repoRoot() ?: return
        val absolutePath = "${root.path}/${change.filePath}"
        // LocalFilePath (not a VirtualFile lookup) so deleted files still resolve to a Change.
        val vcsChange = ChangeListManager.getInstance(project).getChange(LocalFilePath(absolutePath, false))
        if (vcsChange != null) {
            ShowDiffAction.showDiffForChange(project, listOf(vcsChange))
            return
        }
        // Untracked / unknown to VCS: fall back to opening the file, or silently do nothing.
        val file = root.findFileByRelativePath(change.filePath) ?: return
        OpenFileDescriptor(project, file).navigate(true)
    }

    /**
     * Opens the diff for a single file as changed by [commit]: before-revision is the commit's
     * parent (`<sha>^`), after-revision is the commit itself. Added/untracked files have no
     * parent-side content; deleted files have no commit-side content.
     */
    private fun openCommitFileDiff(commit: ButCommit, change: UncommittedChange) {
        val root = repoRoot() ?: return
        val filePath = LocalFilePath("${root.path}/${change.filePath}", false)
        val changeType = change.changeType.lowercase()
        val beforeRevision = if (changeType == "added" || changeType == "untracked") {
            null
        } else {
            GitContentRevision.createRevision(filePath, GitRevisionNumber("${commit.commitId}^"), project)
        }
        val afterRevision = if (changeType == "deleted") {
            null
        } else {
            GitContentRevision.createRevision(filePath, GitRevisionNumber(commit.commitId), project)
        }
        ShowDiffAction.showDiffForChange(project, listOf(Change(beforeRevision, afterRevision)))
    }

    private fun repoRoot() = GitButlerService.getInstance(project).workspaceRepository()?.root

    private fun buildToolbar(): JComponent {
        val group = DefaultActionGroup().apply {
            add(RefreshAction())
            add(PullWorkspaceAction())
        }
        val actionToolbar = ActionManager.getInstance()
            .createActionToolbar("GitButlerToolWindow", group, true)
        actionToolbar.targetComponent = tree
        return actionToolbar.component
    }

    /** Right-click menu: unapply/push a selected branch, or rename/uncommit a selected commit. */
    private fun installContextMenu() {
        // Select the row under the cursor before the menu's update() reads the selection —
        // don't rely on the tree doing this on popup trigger.
        tree.addMouseListener(object : java.awt.event.MouseAdapter() {
            override fun mousePressed(e: MouseEvent) = selectOnPopupTrigger(e)
            override fun mouseReleased(e: MouseEvent) = selectOnPopupTrigger(e)

            private fun selectOnPopupTrigger(e: MouseEvent) {
                if (!e.isPopupTrigger) {
                    return
                }
                val path = tree.getPathForLocation(e.x, e.y) ?: return
                if (!tree.isPathSelected(path)) {
                    tree.selectionPath = path
                }
            }
        })

        val group = DefaultActionGroup().apply {
            add(UnapplyBranchAction())
            add(PushBranchAction())
            addSeparator()
            add(ShowInGitLogAction())
            add(RenameCommitAction())
            add(UncommitAction())
            add(UncommitFileAction())
        }
        PopupHandler.installPopupMenu(tree, group, "GitButlerStatusPopup")
    }

    /** The [VirtualBranch] of the currently-selected [BranchNode], or null if the selection is elsewhere. */
    private fun selectedBranch(): VirtualBranch? {
        val node = tree.selectionPath?.lastPathComponent as? DefaultMutableTreeNode
        return (node?.userObject as? BranchNode)?.branch
    }

    /** The [ButCommit] of the currently-selected [CommitNode], or null if the selection is elsewhere. */
    private fun selectedCommit(): ButCommit? {
        val node = tree.selectionPath?.lastPathComponent as? DefaultMutableTreeNode
        return (node?.userObject as? CommitNode)?.commit
    }

    /** The [CommitFileNode] payload of the currently-selected commit file row, or null if the selection is elsewhere. */
    private fun selectedCommitFile(): CommitFileNode? {
        val node = tree.selectionPath?.lastPathComponent as? DefaultMutableTreeNode
        return node?.userObject as? CommitFileNode
    }

    /** The stable id to pass to `but reword`/`but uncommit`: the GitButler change id, falling back to the sha. */
    private fun ButCommit.effectiveId(): String = cliId.ifBlank { commitId }

    /**
     * Runs a GitButler mutation on a background thread (the service asserts non-EDT, which
     * [Task.Backgroundable] satisfies), then on the EDT notifies success/failure through the
     * "GitButler" notification group and refreshes the tree.
     */
    private fun runOperation(title: String, successMessage: String, operation: () -> ButResult<Unit>) {
        if (operationInFlight) {
            return
        }
        operationInFlight = true
        val task = object : Task.Backgroundable(project, title) {
            private var result: ButResult<Unit>? = null

            override fun run(indicator: ProgressIndicator) {
                result = operation()
            }

            // Always called on the EDT — also after a failure or cancellation, so the
            // in-flight flag can never get stuck.
            override fun onFinished() {
                operationInFlight = false
                if (project.isDisposed) {
                    return
                }
                val group = NotificationGroupManager.getInstance().getNotificationGroup("GitButler")
                when (val r = result) {
                    is ButResult.Ok ->
                        group.createNotification(successMessage, NotificationType.INFORMATION).notify(project)

                    is ButResult.Err ->
                        group.createNotification(r.message, NotificationType.ERROR).notify(project)

                    null -> {}
                }
                refresh()
            }
        }
        ProgressManager.getInstance().run(task)
    }

    /**
     * Subscribes to git repository changes so the tree auto-refreshes when the working tree,
     * branches or commits change. The listener fires on an arbitrary thread; bursts are
     * debounced through a [MergingUpdateQueue] (~500ms) whose flush runs [refresh] on the EDT.
     * [parent] scopes both the queue and the message-bus subscription to the tool window content.
     */
    fun installAutoRefresh(parent: Disposable) {
        val queue = MergingUpdateQueue("GitButlerAutoRefresh", 500, true, this, parent)
        project.messageBus.connect(parent).subscribe(
            GitRepository.GIT_REPO_CHANGE,
            GitRepositoryChangeListener {
                queue.queue(object : Update("refresh") {
                    override fun run() = refresh()
                })
            },
        )
    }

    /** Re-runs `but status` off the EDT and re-renders the tree on the EDT. */
    fun refresh() {
        if (project.isDisposed) {
            return
        }
        if (!GitButlerService.getInstance(project).isGitButlerWorkspace()) {
            showEmpty("Not a GitButler workspace")
            return
        }

        tree.emptyText.text = "Loading GitButler status…"
        ApplicationManager.getApplication().executeOnPooledThread {
            val result = GitButlerService.getInstance(project).status()
            ApplicationManager.getApplication().invokeLater {
                if (project.isDisposed) {
                    return@invokeLater
                }
                when (result) {
                    is ButResult.Ok -> renderStatus(result.value)
                    is ButResult.Err -> showEmpty(result.message)
                }
            }
        }
    }

    private fun showEmpty(message: String) {
        rootNode.removeAllChildren()
        treeModel.reload()
        tree.emptyText.text = message
    }

    private fun renderStatus(status: WorkspaceStatus) {
        rootNode.removeAllChildren()

        // Identity set of every change assigned to a stack; the flat uncommittedChanges list
        // holds the same instances, so identity filtering yields the truly-unassigned changes.
        val assignedInstances = java.util.Collections.newSetFromMap(
            java.util.IdentityHashMap<UncommittedChange, Boolean>(),
        )
        status.stacks.forEach { stack -> assignedInstances.addAll(stack.assignedChanges) }
        val trulyUnassigned = status.uncommittedChanges.filterNot { it in assignedInstances }

        val unassigned = DefaultMutableTreeNode(
            UnassignedNode(trulyUnassigned.size),
        )
        trulyUnassigned.forEach { change ->
            unassigned.add(DefaultMutableTreeNode(ChangeNode(change)))
        }
        rootNode.add(unassigned)

        status.stacks.forEach { stack -> addStack(stack) }

        treeModel.reload()
        tree.emptyText.text = ""
        // Expand everything except commit file lists, which stay collapsed until the
        // user opens them.
        TreeUtil.expandAll(tree)
        TreeUtil.treeNodeTraverser(rootNode)
            .filter { (it as? DefaultMutableTreeNode)?.userObject is CommitNode }
            .forEach { tree.collapsePath(TreeUtil.getPath(rootNode, it)) }
    }

    private fun addStack(stack: ButStack) {
        stack.branches.forEachIndexed { index, branch ->
            val branchNode = DefaultMutableTreeNode(BranchNode(branch))
            // The stack's assigned changes belong to its first branch — surface them there,
            // before the commit children, mirroring `but status`'s "staged to <branch>" lane.
            if (index == 0 && stack.assignedChanges.isNotEmpty()) {
                val assignedNode = DefaultMutableTreeNode(AssignedNode(stack.assignedChanges.size))
                stack.assignedChanges.forEach { change ->
                    assignedNode.add(DefaultMutableTreeNode(ChangeNode(change)))
                }
                branchNode.add(assignedNode)
            }
            if (branch.commits.isEmpty()) {
                branchNode.add(DefaultMutableTreeNode(NoCommitsNode))
            } else {
                branch.commits.forEach { commit ->
                    val commitNode = DefaultMutableTreeNode(CommitNode(commit))
                    commit.changes.forEach { change ->
                        commitNode.add(DefaultMutableTreeNode(CommitFileNode(commit, change)))
                    }
                    branchNode.add(commitNode)
                }
            }
            rootNode.add(branchNode)
        }
    }

    private inner class RefreshAction :
        AnAction("Refresh", "Re-run GitButler status", AllIcons.Actions.Refresh) {
        override fun actionPerformed(e: AnActionEvent) {
            refresh()
        }
    }

    private inner class PullWorkspaceAction :
        AnAction("Pull Workspace", "Fetch remote and rebase applied branches", AllIcons.Actions.CheckOut) {
        override fun getActionUpdateThread() = ActionUpdateThread.EDT

        override fun update(e: AnActionEvent) {
            e.presentation.isEnabled =
                !operationInFlight && GitButlerService.getInstance(project).isGitButlerWorkspace()
        }

        override fun actionPerformed(e: AnActionEvent) {
            runOperation("Pulling GitButler workspace", "Workspace updated") {
                GitButlerService.getInstance(project).pull()
            }
        }
    }

    private inner class UnapplyBranchAction : AnAction() {
        override fun getActionUpdateThread() = ActionUpdateThread.EDT

        override fun update(e: AnActionEvent) {
            // setText(_, false): branch names carry underscores/ampersands — keep them literal, no mnemonics.
            e.presentation.setText("Unapply Branch", false)
            e.presentation.isEnabledAndVisible = selectedBranch() != null && !operationInFlight
        }

        override fun actionPerformed(e: AnActionEvent) {
            val name = selectedBranch()?.name ?: return
            runOperation("Unapplying $name", "Unapplied $name") {
                GitButlerService.getInstance(project).unapply(name)
            }
        }
    }

    private inner class PushBranchAction : AnAction(AllIcons.Vcs.Push) {
        override fun getActionUpdateThread() = ActionUpdateThread.EDT

        override fun update(e: AnActionEvent) {
            // setText(_, false): branch names carry underscores/ampersands — keep them literal, no mnemonics.
            e.presentation.setText("Push Branch", false)
            e.presentation.isEnabledAndVisible = selectedBranch() != null && !operationInFlight
        }

        override fun actionPerformed(e: AnActionEvent) {
            val name = selectedBranch()?.name ?: return
            runOperation("Pushing $name", "Pushed $name") {
                GitButlerService.getInstance(project).push(name)
            }
        }
    }

    private inner class ShowInGitLogAction : AnAction() {
        override fun getActionUpdateThread() = ActionUpdateThread.EDT

        override fun update(e: AnActionEvent) {
            e.presentation.setText("Show in Git Log", false)
            e.presentation.isEnabledAndVisible = selectedCommit()?.commitId?.isNotBlank() == true
        }

        override fun actionPerformed(e: AnActionEvent) {
            val commit = selectedCommit() ?: return
            if (commit.commitId.isBlank()) return
            val root = repoRoot() ?: return
            VcsLogNavigationUtil.jumpToRevisionAsync(project, root, HashImpl.build(commit.commitId), null)
        }
    }

    private inner class RenameCommitAction : AnAction() {
        override fun getActionUpdateThread() = ActionUpdateThread.EDT

        override fun update(e: AnActionEvent) {
            e.presentation.setText("Rename Commit", false)
            val commit = selectedCommit()
            e.presentation.isEnabledAndVisible =
                commit != null && commit.effectiveId().isNotBlank() && !operationInFlight
        }

        override fun actionPerformed(e: AnActionEvent) {
            val commit = selectedCommit() ?: return
            val commitId = commit.effectiveId()
            if (commitId.isBlank()) {
                return
            }
            val newMessage = Messages.showMultilineInputDialog(
                project,
                "Commit message:",
                "Rename Commit",
                commit.message,
                null,
                null,
            )
            if (newMessage.isNullOrBlank() || newMessage == commit.message) {
                return
            }
            runOperation("Renaming commit", "Commit renamed") {
                GitButlerService.getInstance(project).reword(commitId, newMessage)
            }
        }
    }

    private inner class UncommitAction : AnAction() {
        override fun getActionUpdateThread() = ActionUpdateThread.EDT

        override fun update(e: AnActionEvent) {
            e.presentation.setText("Uncommit", false)
            val commit = selectedCommit()
            e.presentation.isEnabledAndVisible =
                commit != null && commit.effectiveId().isNotBlank() && !operationInFlight
        }

        override fun actionPerformed(e: AnActionEvent) {
            val commit = selectedCommit() ?: return
            val commitId = commit.effectiveId()
            if (commitId.isBlank()) {
                return
            }
            runOperation("Uncommitting", "Commit uncommitted — changes are back in your working tree") {
                GitButlerService.getInstance(project).uncommit(commitId)
            }
        }
    }

    private inner class UncommitFileAction : AnAction() {
        override fun getActionUpdateThread() = ActionUpdateThread.EDT

        override fun update(e: AnActionEvent) {
            e.presentation.setText("Uncommit File", false)
            val commitFile = selectedCommitFile()
            e.presentation.isEnabledAndVisible =
                commitFile != null && commitFile.change.cliId.isNotBlank() && !operationInFlight
        }

        override fun actionPerformed(e: AnActionEvent) {
            val commitFile = selectedCommitFile() ?: return
            val cliId = commitFile.change.cliId
            if (cliId.isBlank()) {
                return
            }
            runOperation("Uncommitting file", "File uncommitted — change is back in your working tree") {
                GitButlerService.getInstance(project).uncommit(cliId)
            }
        }
    }

    // --- Tree node payloads -------------------------------------------------

    private class UnassignedNode(val count: Int)
    private class AssignedNode(val count: Int)
    private class ChangeNode(val change: UncommittedChange)
    private class BranchNode(val branch: VirtualBranch)
    private class CommitNode(val commit: ButCommit)
    private class CommitFileNode(val commit: ButCommit, val change: UncommittedChange)
    private object NoCommitsNode

    private class StatusCellRenderer : ColoredTreeCellRenderer() {
        override fun customizeCellRenderer(
            tree: JTree,
            value: Any?,
            selected: Boolean,
            expanded: Boolean,
            leaf: Boolean,
            row: Int,
            hasFocus: Boolean,
        ) {
            when (val payload = (value as? DefaultMutableTreeNode)?.userObject) {
                is UnassignedNode -> {
                    append("Unassigned changes (${payload.count})")
                }

                is AssignedNode -> {
                    append("Assigned changes (${payload.count})", SimpleTextAttributes.GRAYED_ATTRIBUTES)
                }

                is ChangeNode -> {
                    appendChange(payload.change.changeType, payload.change.filePath)
                }

                is BranchNode -> {
                    append(payload.branch.name, SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES)
                    val suffix = statusSuffix(payload.branch.branchStatus)
                    if (suffix.isNotEmpty()) {
                        append("  $suffix", SimpleTextAttributes.GRAYED_ATTRIBUTES)
                    }
                }

                is CommitNode -> {
                    append("${payload.commit.commitId.take(7)} ", SimpleTextAttributes.GRAYED_ATTRIBUTES)
                    append(payload.commit.message.lineSequence().firstOrNull().orEmpty())
                    if (payload.commit.conflicted) {
                        append(" (conflicted)", SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, JBColor.RED))
                    }
                }

                is CommitFileNode -> {
                    appendChange(payload.change.changeType, payload.change.filePath)
                }

                NoCommitsNode -> {
                    append("(no commits)", SimpleTextAttributes.GRAYED_ATTRIBUTES)
                }
            }
        }

        /** Shared by [ChangeNode] and [CommitFileNode]: changeType-colored prefix, then the path with a grayed directory and colored filename. */
        private fun appendChange(changeType: String, filePath: String) {
            val statusAttributes = changeTypeAttributes(changeType)
            append("$changeType ", statusAttributes)
            val slash = filePath.lastIndexOf('/')
            if (slash >= 0) {
                append(filePath.substring(0, slash + 1), SimpleTextAttributes.GRAYED_ATTRIBUTES)
                append(filePath.substring(slash + 1), statusAttributes)
            } else {
                append(filePath, statusAttributes)
            }
        }

        /**
         * Same theme-aware colors the IDE commit window uses for changed files, keyed on the
         * CLI's changeType words. Unknown types stay in the regular foreground.
         */
        private fun changeTypeAttributes(changeType: String): SimpleTextAttributes {
            val status = when (changeType.lowercase()) {
                "added", "addition", "a", "untracked" -> FileStatus.ADDED
                "modified", "modification", "m" -> FileStatus.MODIFIED
                "deleted", "deletion", "d" -> FileStatus.DELETED
                "renamed", "rename", "r", "moved" -> FileStatus.MODIFIED
                else -> null
            } ?: return SimpleTextAttributes.REGULAR_ATTRIBUTES
            return SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, status.color)
        }

        private fun statusSuffix(branchStatus: String): String = when (branchStatus) {
            "" -> ""
            "nothingToPush" -> "✓ pushed"
            "unpushedCommits" -> "unpushed"
            "completelyUnpushed" -> "unpushed"
            else -> branchStatus
        }
    }
}
