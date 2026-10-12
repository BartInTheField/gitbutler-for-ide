package me.inthefield.gitbutlerforjetbrains.setup

import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.util.ui.update.MergingUpdateQueue
import com.intellij.util.ui.update.Update
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryChangeListener

/**
 * Asks once, after Git repositories are known, whether to set the project up for GitButler.
 * Git mapping often lands after startup, so the same check also runs on repository changes.
 */
class GitButlerSetupActivity : ProjectActivity, DumbAware {

    override suspend fun execute(project: Project) {
        val queue = MergingUpdateQueue("GitButlerSetupPrompt", 500, true, null, project)
        project.messageBus.connect(project).subscribe(
            GitRepository.GIT_REPO_CHANGE,
            GitRepositoryChangeListener {
                queue.queue(Update.create("prompt") {
                    GitButlerSetupPrompter.getInstance(project).consider()
                })
            },
        )
        GitButlerSetupPrompter.getInstance(project).consider()
    }
}
