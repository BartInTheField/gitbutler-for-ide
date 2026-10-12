package me.inthefield.gitbutlerforjetbrains.setup

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.StoragePathMacros
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project

/**
 * Per-project "don't ask again" for the GitButler setup prompt. Stored in the IDE
 * workspace file so it follows this project without being committed to the repository.
 * [promptedWhileUnset] is session-only: dismissing the balloon hides it until the next
 * project open, or until the project is a GitButler workspace and then leaves again.
 */
@State(name = "GitButlerProjectSettings", storages = [Storage(StoragePathMacros.WORKSPACE_FILE)])
@Service(Service.Level.PROJECT)
class GitButlerProjectSettings : PersistentStateComponent<GitButlerProjectSettings.State> {

    class State {
        var suppressSetupPrompt: Boolean = false
    }

    private var state = State()

    /** Set once the setup balloon has been shown while the repo is not a GitButler workspace. */
    var promptedWhileUnset: Boolean = false

    override fun getState(): State = state

    override fun loadState(state: State) {
        this.state = state
    }

    var suppressSetupPrompt: Boolean
        get() = state.suppressSetupPrompt
        set(value) {
            state.suppressSetupPrompt = value
        }

    companion object {
        fun getInstance(project: Project): GitButlerProjectSettings = project.service()
    }
}
