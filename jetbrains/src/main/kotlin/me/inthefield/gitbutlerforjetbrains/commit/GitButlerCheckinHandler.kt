package me.inthefield.gitbutlerforjetbrains.commit

import me.inthefield.gitbutlerforjetbrains.core.ButResult
import me.inthefield.gitbutlerforjetbrains.core.GitButlerService
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.vcs.CheckinProjectPanel
import com.intellij.openapi.vcs.changes.CommitExecutor
import com.intellij.openapi.vcs.changes.VcsDirtyScopeManager
import com.intellij.openapi.vcs.checkin.CheckinHandler
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.util.PairConsumer

/**
 * Routes the commit through the GitButler CLI when a virtual branch is selected
 * in the always-visible commit-toolbar combo ([GitButlerBranchComboAction]).
 * Otherwise steps aside so IntelliJ's normal git commit runs untouched.
 */
class GitButlerCheckinHandler(
    private val panel: CheckinProjectPanel,
) : CheckinHandler() {

    private val project get() = panel.project
    private val service get() = GitButlerService.getInstance(project)

    override fun beforeCheckin(
        executor: CommitExecutor?,
        additionalDataConsumer: PairConsumer<Any, Any>?,
    ): ReturnResult {
        val selected = GitButlerCommitSelection.getInstance(project).selectedBranch
        // Re-check the workspace here: the factory ran once when the commit UI was
        // created, possibly before git repositories were registered.
        if (!service.isGitButlerWorkspace() || selected == null) {
            return ReturnResult.COMMIT
        }

        if (panel.commitMessage.isBlank()) {
            NotificationGroupManager.getInstance()
                .getNotificationGroup(NOTIFICATION_GROUP)
                .createNotification("GitButler commit failed", "Commit message is empty", NotificationType.ERROR)
                .notify(project)
            return ReturnResult.CANCEL
        }

        val message = panel.commitMessage
        val filePaths = panel.files.map { it.absolutePath }
        // git4idea.checkin.GitCommitAndPushExecutor is Kotlin-`internal`, so it cannot be
        // referenced from another module. Match on its stable public executor id instead.
        val andPush = executor?.id == GIT_COMMIT_AND_PUSH_EXECUTOR_ID

        var commitResult: ButResult<String>? = null
        var pushResult: ButResult<Unit>? = null
        ProgressManager.getInstance().runProcessWithProgressSynchronously(
            {
                val committed = service.commit(selected, message, filePaths)
                commitResult = committed
                if (andPush && committed is ButResult.Ok) {
                    pushResult = service.push(selected)
                }
            },
            "Committing to GitButler branch…",
            false,
            project,
        )

        return when (val r = commitResult) {
            is ButResult.Ok -> {
                // The commit id may be unknown when the CLI's output shape differs.
                val shortId = r.value.take(7).ifBlank { "Commit created" }
                val group = NotificationGroupManager.getInstance().getNotificationGroup(NOTIFICATION_GROUP)
                when (val p = pushResult) {
                    is ButResult.Err ->
                        group.createNotification(
                            "Committed to $selected, but push failed",
                            p.message,
                            NotificationType.WARNING,
                        ).notify(project)
                    is ButResult.Ok ->
                        group.createNotification(
                            "Committed to $selected and pushed",
                            shortId,
                            NotificationType.INFORMATION,
                        ).notify(project)
                    null ->
                        group.createNotification(
                            "Committed to $selected",
                            shortId,
                            NotificationType.INFORMATION,
                        ).notify(project)
                }
                VcsDirtyScopeManager.getInstance(project).markEverythingDirty()
                VirtualFileManager.getInstance().asyncRefresh(null)
                GitButlerCommitSelection.getInstance(project).refreshNow()
                ReturnResult.CLOSE_WINDOW
            }
            is ButResult.Err -> {
                NotificationGroupManager.getInstance()
                    .getNotificationGroup(NOTIFICATION_GROUP)
                    .createNotification("GitButler commit failed", r.message, NotificationType.ERROR)
                    .notify(project)
                ReturnResult.CANCEL
            }
            null -> ReturnResult.CANCEL
        }
    }

    companion object {
        const val NOTIFICATION_GROUP = "GitButler"

        /** Public id of git4idea's internal GitCommitAndPushExecutor (its `getId()`). */
        private const val GIT_COMMIT_AND_PUSH_EXECUTOR_ID = "Git.Commit.And.Push.Executor"
    }
}
