package me.inthefield.gitbutlerforjetbrains.commit

import com.intellij.openapi.vcs.CheckinProjectPanel
import com.intellij.openapi.vcs.changes.CommitContext
import com.intellij.openapi.vcs.checkin.CheckinHandler
import com.intellij.openapi.vcs.checkin.CheckinHandlerFactory

/**
 * Registers the GitButler commit interceptor with IntelliJ's commit flow.
 *
 * Always returns a real handler: the factory runs once when the commit session UI
 * is created, possibly before git repositories are registered, so an early false
 * negative here would permanently disable the plugin for that session. The handler
 * decides per commit whether to route through GitButler.
 */
class GitButlerCheckinHandlerFactory : CheckinHandlerFactory() {
    override fun createHandler(panel: CheckinProjectPanel, commitContext: CommitContext): CheckinHandler {
        return GitButlerCheckinHandler(panel)
    }
}
