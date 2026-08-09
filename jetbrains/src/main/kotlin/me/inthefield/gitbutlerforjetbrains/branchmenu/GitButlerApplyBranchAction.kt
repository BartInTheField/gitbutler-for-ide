package me.inthefield.gitbutlerforjetbrains.branchmenu

import com.intellij.openapi.project.Project
import git4idea.GitBranch
import git4idea.GitRemoteBranch
import me.inthefield.gitbutlerforjetbrains.core.ButResult
import me.inthefield.gitbutlerforjetbrains.core.GitButlerService

/** Runs `but apply <branch>` for the branch selected in the Git branch menu. */
class GitButlerApplyBranchAction : GitButlerBranchActionBase() {
    // The CLI resolves the SHORT name, preferring a local branch. If a remote branch
    // is shadowed by a same-named local one, applying "the remote branch" would
    // silently apply the local — hide Apply there instead of doing the wrong thing.
    override fun isApplicableTo(project: Project, branch: GitBranch): Boolean =
        branch !is GitRemoteBranch || !GitButlerBranchActionGroup.isShadowedByLocal(project, branch)

    override fun progressTitle(branchName: String) = "Applying $branchName"
    override fun successMessage(branchName: String) = "Applied $branchName"
    override fun execute(service: GitButlerService, branchName: String): ButResult<Unit> =
        service.apply(branchName)
}
