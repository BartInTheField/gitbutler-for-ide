package me.inthefield.gitbutlerforjetbrains.commit

import me.inthefield.gitbutlerforjetbrains.core.GitButlerService
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.KeepPopupOnPerform
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.actionSystem.ex.ComboBoxAction
import javax.swing.JComponent

/**
 * Always-visible virtual-branch selector shown inline in the Commit tool window's
 * message-area toolbar (the strip that holds the Amend toggle). Picking a branch
 * routes the next commit through the GitButler CLI; picking "no virtual branch"
 * lets IntelliJ's normal git commit run.
 */
class GitButlerBranchComboAction : ComboBoxAction() {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        val project = e.project
        val visible = project != null && GitButlerService.getInstance(project).isGitButlerWorkspace()
        e.presentation.isEnabledAndVisible = visible
        if (!visible || project == null) {
            return
        }

        val selection = GitButlerCommitSelection.getInstance(project)
        selection.refreshIfStale()

        val selected = selection.selectedBranch
        // setText(..., false): branch names may contain '_'/'&', which the default
        // setter would swallow as mnemonic markers. Truncate so a long branch name
        // cannot stretch the toolbar and shift the rest of the commit UI.
        e.presentation.setText(shorten(selected ?: GIT_ITEM), false)
        e.presentation.description =
            if (selected != null) {
                "Commits are routed to GitButler virtual branch \"$selected\""
            } else {
                "Commits run as plain git; pick a GitButler virtual branch to route them"
            }
    }

    override fun createPopupActionGroup(button: JComponent, dataContext: DataContext): DefaultActionGroup {
        val group = DefaultActionGroup()
        val project = CommonDataKeys.PROJECT.getData(dataContext)
        val selection = project?.let { GitButlerCommitSelection.getInstance(it) }

        group.add(SelectBranchAction(GIT_ITEM, null, selection))
        group.addSeparator()

        val branches = selection?.cachedBranches.orEmpty()
        if (branches.isEmpty()) {
            selection?.lastError?.let { group.add(DisabledInfoAction(it)) }
        } else {
            branches.forEach { branch ->
                group.add(SelectBranchAction(branch, branch, selection))
            }
        }

        group.addSeparator()
        group.add(RefreshAction(selection))
        return group
    }

    /** Selects [branchName] (null = plain git) into the shared selection state. */
    private class SelectBranchAction(
        text: String,
        private val branchName: String?,
        private val selection: GitButlerCommitSelection?,
    ) : ToggleAction() {
        init {
            // Mnemonic-free: branch names may contain '_'/'&'.
            templatePresentation.setText(text, false)
            // ToggleActions default to keeping the popup open (checkbox semantics);
            // this is a single-choice selector, so close it once a branch is picked.
            templatePresentation.setKeepPopupOnPerform(KeepPopupOnPerform.Never)
        }

        override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

        override fun isSelected(e: AnActionEvent): Boolean =
            selection?.selectedBranch == branchName

        override fun setSelected(e: AnActionEvent, state: Boolean) {
            selection?.selectedBranch = branchName
        }
    }

    /** Non-interactive item that surfaces the last refresh error. */
    private class DisabledInfoAction(message: String) : AnAction() {
        init {
            templatePresentation.setText(message, false)
        }

        override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

        override fun update(e: AnActionEvent) {
            e.presentation.isEnabled = false
        }

        override fun actionPerformed(e: AnActionEvent) {
            // no-op
        }
    }

    /** Forces an immediate refresh of the cached branch list. */
    private class RefreshAction(private val selection: GitButlerCommitSelection?) :
        AnAction("Refresh branches") {
        override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

        override fun actionPerformed(e: AnActionEvent) {
            selection?.refreshNow()
        }
    }

    companion object {
        const val GIT_ITEM = "Git: no virtual branch"
        private const val MAX_LABEL = 28

        /** Keeps the toolbar button a stable width; the full name is in the tooltip. */
        private fun shorten(text: String): String =
            if (text.length <= MAX_LABEL) text else text.take(MAX_LABEL - 1).trimEnd() + "…"
    }
}
