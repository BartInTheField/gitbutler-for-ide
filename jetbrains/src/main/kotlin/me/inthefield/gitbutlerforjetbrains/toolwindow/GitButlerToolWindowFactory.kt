package me.inthefield.gitbutlerforjetbrains.toolwindow

import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory

/**
 * Registers the "GitButler" tool window (bottom-left stripe, like the Git tool window).
 * Installs a single [GitButlerStatusPanel] as its only content and kicks off an initial load.
 */
class GitButlerToolWindowFactory : ToolWindowFactory, DumbAware {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val panel = GitButlerStatusPanel(project)
        val content = toolWindow.contentManager.factory.createContent(panel, null, false)
        content.isCloseable = false
        toolWindow.contentManager.addContent(content)
        // Auto-refresh subscription dies with the tool window content.
        panel.installAutoRefresh(content)
        panel.refresh()
    }
}
