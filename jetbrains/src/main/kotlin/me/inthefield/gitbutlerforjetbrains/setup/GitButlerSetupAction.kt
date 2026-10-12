package me.inthefield.gitbutlerforjetbrains.setup

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAware
import me.inthefield.gitbutlerforjetbrains.core.GitButlerService

/**
 * Git → GitButler → Set Up GitButler for This Project. Visible on a plain Git repo that
 * is not already on `gitbutler/workspace`. Runs `but setup` (never `but setup --init`).
 */
class GitButlerSetupAction : AnAction(), DumbAware {

    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        val project = e.project
        val service = project?.let { GitButlerService.getInstance(it) }
        val applicable = service != null &&
            service.workspaceRepository() != null &&
            !service.isGitButlerWorkspace()
        e.presentation.isEnabledAndVisible = applicable
        if (applicable && service.butExecutable() == null) {
            e.presentation.isEnabled = false
            e.presentation.description = "GitButler CLI (but) was not found on PATH"
        }
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        GitButlerWorkspaceOps.runSetup(project)
    }
}
