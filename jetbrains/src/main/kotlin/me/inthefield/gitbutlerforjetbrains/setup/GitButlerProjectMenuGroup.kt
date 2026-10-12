package me.inthefield.gitbutlerforjetbrains.setup

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.project.DumbAware
import me.inthefield.gitbutlerforjetbrains.core.GitButlerService

/**
 * Git → GitButler submenu for project-level actions. Hidden once the repo is already
 * on `gitbutler/workspace` so the menu does not show an empty submenu (Leave lives in
 * the tool window overflow instead).
 */
class GitButlerProjectMenuGroup : DefaultActionGroup(), DumbAware {

    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        val project = e.project
        val service = project?.let { GitButlerService.getInstance(it) }
        e.presentation.isEnabledAndVisible = service != null &&
            service.workspaceRepository() != null &&
            !service.isGitButlerWorkspace()
    }
}
