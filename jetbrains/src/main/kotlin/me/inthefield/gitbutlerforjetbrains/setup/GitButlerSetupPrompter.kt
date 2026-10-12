package me.inthefield.gitbutlerforjetbrains.setup

import com.intellij.notification.Notification
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import me.inthefield.gitbutlerforjetbrains.core.GitButlerService
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Shows one dismissible balloon when this project is a Git repo, `but` is on PATH, and
 * the repo is not on `gitbutler/workspace`. "Don't ask again" is remembered per project;
 * closing the balloon only suppresses it for the rest of this IDE session.
 */
@Service(Service.Level.PROJECT)
class GitButlerSetupPrompter(private val project: Project) {

    private val checking = AtomicBoolean(false)
    private val pending = AtomicBoolean(false)
    private var notification: Notification? = null

    /** Coalesces overlapping calls. A git event that arrives mid-check is not dropped. */
    fun consider() {
        if (project.isDisposed) return
        pending.set(true)
        drain()
    }

    private fun drain() {
        if (!checking.compareAndSet(false, true)) return
        if (!pending.compareAndSet(true, false)) {
            checking.set(false)
            if (pending.get()) drain()
            return
        }
        ApplicationManager.getApplication().executeOnPooledThread {
            val service = GitButlerService.getInstance(project)
            val isRepo = service.workspaceRepository() != null
            val hasBut = service.butExecutable() != null
            val isWorkspace = service.isGitButlerWorkspace()
            ApplicationManager.getApplication().invokeLater {
                checking.set(false)
                if (!project.isDisposed) {
                    apply(isRepo, hasBut, isWorkspace)
                }
                if (pending.get()) drain()
            }
        }
    }

    private fun apply(isRepo: Boolean, hasBut: Boolean, isWorkspace: Boolean) {
        if (isWorkspace) {
            GitButlerProjectSettings.getInstance(project).promptedWhileUnset = false
            notification?.expire()
            notification = null
            return
        }
        val settings = GitButlerProjectSettings.getInstance(project)
        if (settings.suppressSetupPrompt || settings.promptedWhileUnset) return
        if (!isRepo || !hasBut) return
        settings.promptedWhileUnset = true
        show()
    }

    private fun show() {
        notification?.expire()
        val notification = NotificationGroupManager.getInstance()
            .getNotificationGroup(SETUP_GROUP)
            .createNotification(
                "This repository isn't managed by GitButler yet — Set up GitButler",
                "GitButler will switch this repo to gitbutler/workspace so the IDE can commit to virtual branches.",
                NotificationType.INFORMATION,
            )
        notification.addAction(NotificationAction.createSimpleExpiring("Set up GitButler") {
            GitButlerWorkspaceOps.runSetup(project)
        })
        notification.addAction(NotificationAction.createSimpleExpiring("Don't ask again") {
            GitButlerProjectSettings.getInstance(project).suppressSetupPrompt = true
        })
        notification.notify(project)
        this.notification = notification
    }

    companion object {
        const val SETUP_GROUP = "GitButler.Setup"

        fun getInstance(project: Project): GitButlerSetupPrompter = project.service()
    }
}
