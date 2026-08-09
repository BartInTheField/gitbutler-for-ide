package me.inthefield.gitbutlerforjetbrains.branchmenu

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import git4idea.GitBranch
import git4idea.GitReference
import git4idea.GitRemoteBranch
import me.inthefield.gitbutlerforjetbrains.core.GitButlerService

/**
 * The "GitButler" submenu added to the IDE's native Git branch context menu
 * (action group `Git.Branch`). Hides itself entirely unless the project is a
 * GitButler workspace and exactly one branch — local or remote, other than
 * `gitbutler/workspace` — is selected. Which items apply to a remote branch is
 * decided per action (see [GitButlerBranchActionBase.isApplicableTo]).
 */
class GitButlerBranchActionGroup : DefaultActionGroup(), DumbAware {

    // isGitButlerWorkspace touches git repository state — keep update() off the EDT.
    // Never call the CLI here.
    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        val project = e.project
        val branch = selectedBranch(e)
        // A shadowed remote branch (same-named local exists) hides Apply, and Unapply
        // is local-only — hide the whole submenu rather than showing it empty.
        e.presentation.isEnabledAndVisible = project != null && branch != null &&
            (branch !is GitRemoteBranch || !isShadowedByLocal(project, branch))
    }

    companion object {
        private const val WORKSPACE_BRANCH = "gitbutler/workspace"

        // The selected branch's DataKey moved between IDE lines, so both are read BY
        // NAME (DataKey.create interns by name) instead of referencing the git4idea
        // key holders — a compiled-in field reference raises NoSuchFieldError on the
        // line that doesn't have it (observed live on 2026.1).
        // 2025.1: GitBranchActionsDataKeys.BRANCHES, a List<GitBranch>.
        private val BRANCHES_KEY: DataKey<List<GitBranch>> = DataKey.create("Git.Branches")
        // 2026.1+: GitSingleRefActions.SELECTED_REF_DATA_KEY, a single GitReference.
        private val SELECTED_REF_KEY: DataKey<GitReference> = DataKey.create("Git.Selected.Ref")

        /**
         * The single selected branch the GitButler actions may target, or null when
         * the submenu should not apply: no project, not a GitButler workspace, zero
         * or multiple branches selected, or the workspace branch itself (also its
         * remote form, e.g. `origin/gitbutler/workspace`).
         */
        fun selectedBranch(e: AnActionEvent): GitBranch? {
            val project = e.project ?: return null
            if (!GitButlerService.getInstance(project).isGitButlerWorkspace()) {
                return null
            }
            val branch = e.getData(BRANCHES_KEY)?.filterIsInstance<GitBranch>()?.singleOrNull()
                ?: e.getData(SELECTED_REF_KEY) as? GitBranch
                ?: return null
            if (cliBranchName(branch) == WORKSPACE_BRANCH) {
                return null
            }
            return branch
        }

        /**
         * The branch name to pass to the `but` CLI: for remote branches the short name
         * (`origin/feature/x` → `feature/x`), for local branches the name as-is.
         */
        fun cliBranchName(branch: GitBranch): String =
            if (branch is GitRemoteBranch) branch.nameForRemoteOperations else branch.name

        /**
         * True when a local branch with the remote branch's short name exists. The CLI
         * resolves short names preferring local branches, so applying such a remote
         * branch would silently target the local one — callers hide Apply instead.
         */
        fun isShadowedByLocal(project: Project, branch: GitRemoteBranch): Boolean {
            val repo = GitButlerService.getInstance(project).workspaceRepository() ?: return false
            return repo.branches.findLocalBranch(branch.nameForRemoteOperations) != null
        }
    }
}
