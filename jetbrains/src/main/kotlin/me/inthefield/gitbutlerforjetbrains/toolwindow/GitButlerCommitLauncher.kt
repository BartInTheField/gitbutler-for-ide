package me.inthefield.gitbutlerforjetbrains.toolwindow

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.LocalFilePath
import com.intellij.openapi.vcs.changes.ChangeListManager
import com.intellij.openapi.wm.ToolWindowManager
import me.inthefield.gitbutlerforjetbrains.commit.GitButlerCommitSelection
import me.inthefield.gitbutlerforjetbrains.core.GitButlerService

/**
 * Bridge from the tool window to the IDE's Commit UI, shared by the "Commit to Branch"
 * action and the drag-a-change-onto-a-branch drop: preselect the virtual branch in the
 * commit toolbar's GitButler combo, open the Commit tool window, and optionally replace
 * its checked inclusion with an exact set of files. The commit itself is then finished
 * through the normal IDE flow, which routes it through [GitButlerService.commit].
 */
internal object GitButlerCommitLauncher {

    private val LOG = Logger.getInstance(GitButlerCommitLauncher::class.java)

    /**
     * Preselects [branchName] and activates the Commit tool window. When [relativePaths] is
     * non-null the window's checked state is replaced with exactly those repo-relative files;
     * null leaves whatever the user already had checked untouched.
     */
    fun openCommitFor(project: Project, branchName: String, relativePaths: List<String>?) {
        GitButlerCommitSelection.getInstance(project).selectedBranch = branchName
        val toolWindows = ToolWindowManager.getInstance(project)
        val commitWindow = toolWindows.getToolWindow("Commit") ?: toolWindows.getToolWindow("Version Control")
        // Set the inclusion after the tool window is up: the non-modal commit handler may not
        // exist before the Commit tool window has been created for the first time.
        commitWindow?.activate {
            if (relativePaths != null) {
                includeOnly(project, relativePaths)
            }
        }
    }

    /**
     * Replaces the Commit tool window's checked state with exactly [relativePaths]: tracked
     * changes are included as [com.intellij.openapi.vcs.changes.Change]s, untracked ones as
     * [com.intellij.openapi.vcs.FilePath]s (the shape the non-modal commit UI's unversioned
     * nodes use). Best effort — when the non-modal handler is unavailable (e.g. commit dialog
     * mode) the window still opens with the branch preselected.
     */
    private fun includeOnly(project: Project, relativePaths: List<String>) {
        val root = GitButlerService.getInstance(project).workspaceRepository()?.root?.path ?: return
        val changeListManager = ChangeListManager.getInstance(project)
        val items: List<Any> = relativePaths.map { rel ->
            val filePath = LocalFilePath("$root/$rel", false)
            changeListManager.getChange(filePath) ?: filePath
        }
        if (!CommitInclusion.setInclusion(project, changeListManager.defaultChangeList, items)) {
            LOG.warn("Non-modal commit workflow handler unavailable; files not auto-included")
        }
    }
}
