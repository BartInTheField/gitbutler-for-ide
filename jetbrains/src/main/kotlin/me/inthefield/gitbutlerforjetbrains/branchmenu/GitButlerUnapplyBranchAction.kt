package me.inthefield.gitbutlerforjetbrains.branchmenu

import com.intellij.openapi.project.Project
import git4idea.GitBranch
import me.inthefield.gitbutlerforjetbrains.core.ButResult
import me.inthefield.gitbutlerforjetbrains.core.GitButlerService

/** Runs `but unapply <branch>` for the branch selected in the Git branch menu. */
class GitButlerUnapplyBranchAction : GitButlerBranchActionBase() {
    // Unapply only makes sense for a branch that is applied, i.e. exists locally.
    override fun isApplicableTo(project: Project, branch: GitBranch) = !branch.isRemote

    override fun progressTitle(branchName: String) = "Unapplying $branchName"
    override fun successMessage(branchName: String) = "Unapplied $branchName"
    override fun execute(service: GitButlerService, branchName: String): ButResult<Unit> =
        service.unapply(branchName)
}
