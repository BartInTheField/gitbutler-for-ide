package me.inthefield.gitbutlerforjetbrains.setup

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowManager
import git4idea.repo.GitRepositoryManager
import me.inthefield.gitbutlerforjetbrains.core.ButResult
import me.inthefield.gitbutlerforjetbrains.core.ButSetupResult
import me.inthefield.gitbutlerforjetbrains.core.ButTeardown
import me.inthefield.gitbutlerforjetbrains.core.ButTeardownResult
import me.inthefield.gitbutlerforjetbrains.core.GitButlerService

/**
 * Runs `but setup` / `but teardown` off the EDT, then asks Git to re-read HEAD so the
 * tool window and commit routing follow the new branch without an IDE restart.
 */
object GitButlerWorkspaceOps {

    fun runSetup(project: Project) {
        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Setting up GitButler", true) {
            private var result: ButResult<ButSetupResult>? = null

            override fun run(indicator: ProgressIndicator) {
                result = GitButlerService.getInstance(project).setup()
            }

            override fun onSuccess() {
                if (project.isDisposed) return
                when (val r = result) {
                    is ButResult.Ok -> {
                        notify(project, setupMessage(r.value), NotificationType.INFORMATION)
                        refreshGitState(project, showToolWindow = true)
                    }
                    is ButResult.Err -> notify(project, r.message, NotificationType.ERROR)
                    null -> {}
                }
            }
        })
    }

    /**
     * Loads applied and local branches, confirms with [GitButlerLeaveDialog], then runs
     * `but teardown`. [onFinished] runs on the EDT when the flow ends, including cancel.
     * The boolean is true only when teardown succeeded, so the tool window can drop to
     * the dormant state before Git's cached branch has caught up.
     */
    fun confirmAndLeave(project: Project, onFinished: (left: Boolean) -> Unit) {
        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Preparing to leave GitButler mode", true) {
            private var applied: List<String> = emptyList()
            private var local: List<String> = emptyList()
            private var continued = false

            override fun run(indicator: ProgressIndicator) {
                val service = GitButlerService.getInstance(project)
                when (val status = service.status()) {
                    is ButResult.Ok -> applied = status.value.branches.map { it.name }
                    is ButResult.Err -> {}
                }
                local = ApplicationManager.getApplication().runReadAction<List<String>> {
                    service.workspaceRepository()?.branches?.localBranches?.map { it.name }.orEmpty()
                }
            }

            override fun onSuccess() {
                if (project.isDisposed) return
                val candidates = ButTeardown.checkoutCandidates(applied, local)
                val dialog = GitButlerLeaveDialog(
                    project,
                    candidates,
                    allowDefault = ButTeardown.hasCheckoutDefault(applied),
                )
                if (!dialog.showAndGet()) return
                continued = true
                runTeardown(project, dialog.checkoutTo(), onFinished)
            }

            override fun onFinished() {
                if (!continued) onFinished(false)
            }
        })
    }

    private fun runTeardown(project: Project, checkoutTo: String?, onFinished: (left: Boolean) -> Unit) {
        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Leaving GitButler mode", true) {
            private var result: ButResult<ButTeardownResult>? = null

            override fun run(indicator: ProgressIndicator) {
                result = GitButlerService.getInstance(project).teardown(checkoutTo)
            }

            override fun onFinished() {
                val left = !project.isDisposed && result is ButResult.Ok
                if (!project.isDisposed) {
                    when (val r = result) {
                        is ButResult.Ok -> {
                            val branch = r.value.checkedOutBranch.ifBlank { checkoutTo.orEmpty() }
                            val message = if (branch.isBlank()) {
                                "Left GitButler mode"
                            } else {
                                "Left GitButler mode. Checked out $branch."
                            }
                            notify(project, message, NotificationType.INFORMATION)
                            refreshGitState(project, showToolWindow = false)
                        }
                        is ButResult.Err -> notify(project, r.message, NotificationType.ERROR)
                        null -> {}
                    }
                }
                onFinished(left)
            }
        })
    }

    private fun setupMessage(result: ButSetupResult): String {
        val target = result.target?.branchName?.takeIf { it.isNotBlank() }
        return if (target == null) {
            "GitButler is set up. This project is on gitbutler/workspace."
        } else {
            "GitButler is set up. This project is on gitbutler/workspace (target $target)."
        }
    }

    private fun refreshGitState(project: Project, showToolWindow: Boolean) {
        GitRepositoryManager.getInstance(project).repositories.forEach { it.update() }
        if (!showToolWindow) return
        ToolWindowManager.getInstance(project).getToolWindow("GitButler")?.show()
    }

    private fun notify(project: Project, message: String, type: NotificationType) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup("GitButler")
            .createNotification(message, type)
            .notify(project)
    }
}
