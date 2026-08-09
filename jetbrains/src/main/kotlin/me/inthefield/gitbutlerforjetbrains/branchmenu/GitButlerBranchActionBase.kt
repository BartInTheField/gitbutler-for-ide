package me.inthefield.gitbutlerforjetbrains.branchmenu

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtil
import git4idea.GitBranch
import me.inthefield.gitbutlerforjetbrains.core.ButResult
import me.inthefield.gitbutlerforjetbrains.core.GitButlerService

/**
 * Shared plumbing for the branch-menu actions: reads the selected branch, runs the
 * CLI operation on a background task (the service asserts non-EDT), then surfaces
 * the result through the "GitButler" notification group. Applicability is checked in
 * [update] as well as by [GitButlerBranchActionGroup] — the actions are also reachable
 * outside the submenu (Find Action, keymaps), where the group's update never runs.
 * A wrong invocation (e.g. applying an already-applied branch) surfaces the CLI's own
 * error message.
 */
abstract class GitButlerBranchActionBase : DumbAwareAction() {

    protected abstract fun progressTitle(branchName: String): String
    protected abstract fun successMessage(branchName: String): String
    protected abstract fun execute(service: GitButlerService, branchName: String): ButResult<Unit>

    /** Whether this action supports the selected branch (e.g. Unapply is local-only). */
    protected open fun isApplicableTo(project: Project, branch: GitBranch): Boolean = true

    // selectedBranch touches git repository state — keep update() off the EDT.
    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        val project = e.project
        val branch = if (project == null) {
            null
        } else {
            GitButlerBranchActionGroup.selectedBranch(e)?.takeIf { isApplicableTo(project, it) }
        }
        e.presentation.isVisible = branch != null
        e.presentation.isEnabled = branch != null && project != null &&
            !GitButlerService.getInstance(project).mutationInFlight.get()
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val branch = GitButlerBranchActionGroup.selectedBranch(e) ?: return
        if (!isApplicableTo(project, branch)) {
            return
        }
        val branchName = GitButlerBranchActionGroup.cliBranchName(branch)
        val service = GitButlerService.getInstance(project)

        // Never overlap `but` mutations; released in onFinished, which the platform
        // guarantees to run (also after failure or cancellation), so it cannot stick.
        if (!service.mutationInFlight.compareAndSet(false, true)) {
            return
        }

        val task = object : Task.Backgroundable(project, progressTitle(branchName)) {
            private var result: ButResult<Unit>? = null

            override fun run(indicator: ProgressIndicator) {
                result = execute(service, branchName)
                if (result is ButResult.Ok) {
                    // apply/unapply rewrite working-tree files: refresh the VFS so open
                    // editors update, and re-read repository state so GIT_REPO_CHANGE
                    // fires and the GitButler tool window refreshes promptly.
                    service.workspaceRepository()?.root?.let { root ->
                        VfsUtil.markDirtyAndRefresh(true, true, false, root)
                    }
                    service.workspaceRepository()?.update()
                }
            }

            override fun onFinished() {
                service.mutationInFlight.set(false)
                if (project.isDisposed) {
                    return
                }
                val group = NotificationGroupManager.getInstance().getNotificationGroup("GitButler")
                when (val r = result) {
                    is ButResult.Ok ->
                        group.createNotification(successMessage(branchName), NotificationType.INFORMATION)
                            .notify(project)

                    is ButResult.Err ->
                        group.createNotification(r.message, NotificationType.ERROR).notify(project)

                    null -> {}
                }
            }
        }
        task.queue()
    }
}
