package me.inthefield.gitbutlerforjetbrains.commit

import me.inthefield.gitbutlerforjetbrains.core.ButResult
import me.inthefield.gitbutlerforjetbrains.core.GitButlerService
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Project-level holder for the UI-facing state of the GitButler commit toolbar
 * combo: the selected virtual branch (persisted), the last fetched branch list,
 * and the last refresh error. Refreshes happen asynchronously off the EDT.
 */
@Service(Service.Level.PROJECT)
class GitButlerCommitSelection(private val project: Project) {

    @Volatile
    private var selectedBranchField: String? =
        PropertiesComponent.getInstance(project).getValue(SELECTED_BRANCH_KEY)

    @Volatile
    private var cachedBranchesField: List<String> = emptyList()

    @Volatile
    private var lastErrorField: String? = null

    @Volatile
    private var lastRefreshMs: Long = 0

    private val refreshInFlight = AtomicBoolean(false)

    /** Currently selected virtual branch name, or null = commit with plain git. Persisted. */
    var selectedBranch: String?
        get() = selectedBranchField
        set(value) {
            selectedBranchField = value
            val props = PropertiesComponent.getInstance(project)
            if (value == null) {
                props.unsetValue(SELECTED_BRANCH_KEY)
            } else {
                props.setValue(SELECTED_BRANCH_KEY, value)
            }
        }

    /** Last successfully fetched branch names (may be empty). */
    val cachedBranches: List<String>
        get() = cachedBranchesField

    /** Last error from a refresh, or null. */
    val lastError: String?
        get() = lastErrorField

    /**
     * Triggers a background refresh via [GitButlerService.status] if none is in flight
     * and the cache is older than [STALE_MS]. Safe to call from any thread; returns immediately.
     */
    fun refreshIfStale() {
        if (System.currentTimeMillis() - lastRefreshMs < STALE_MS) {
            return
        }
        launchRefresh()
    }

    /** Force refresh regardless of staleness (same async mechanics). */
    fun refreshNow() {
        launchRefresh()
    }

    private fun launchRefresh() {
        if (!refreshInFlight.compareAndSet(false, true)) {
            return
        }
        ApplicationManager.getApplication().executeOnPooledThread {
            try {
                when (val result = GitButlerService.getInstance(project).status()) {
                    is ButResult.Ok -> {
                        cachedBranchesField = result.value.branches.map { it.name }
                        lastErrorField = null
                    }
                    is ButResult.Err -> {
                        lastErrorField = result.message
                    }
                }
                // If the persisted/selected branch no longer exists, KEEP the selection —
                // the CLI errors clearly at commit time if it is truly gone.
            } finally {
                lastRefreshMs = System.currentTimeMillis()
                refreshInFlight.set(false)
            }
        }
    }

    companion object {
        const val SELECTED_BRANCH_KEY = "me.inthefield.gitbutlerforjetbrains.selectedBranch"
        private const val STALE_MS = 10_000L

        fun getInstance(project: Project): GitButlerCommitSelection = project.service()
    }
}
