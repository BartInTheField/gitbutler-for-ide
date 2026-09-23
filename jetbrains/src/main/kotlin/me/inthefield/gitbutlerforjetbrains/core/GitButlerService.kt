package me.inthefield.gitbutlerforjetbrains.core

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/**
 * IntelliJ adapter over the shared [ButClient]. Supplies the host-specific [ButEnvironment]
 * seam — repository detection via git4idea, `but` resolution via [ButExecutableResolver],
 * process execution via [GeneralCommandLine] — and exposes the same per-operation API the
 * commit handler, tool window and branch menu already call. All CLI orchestration, JSON
 * parsing and path mapping live in :core and are shared with the VSCode extension.
 */
@Service(Service.Level.PROJECT)
class GitButlerService(private val project: Project) : ButEnvironment {

    @Volatile
    private var cachedExecutable: String? = null

    /**
     * True while a workspace mutation started from the branch menu is running; callers
     * gate on it (compareAndSet before starting, set(false) when finished) so `but`
     * mutations never overlap.
     */
    val mutationInFlight = AtomicBoolean(false)

    private val client = ButClient(this) { msg -> LOG.warn(msg) }

    /**
     * True iff some git repository of this project has current branch named exactly
     * "gitbutler/workspace". Cheap — no CLI call.
     */
    fun isGitButlerWorkspace(): Boolean =
        repositories().any { it.currentBranchName == ButClient.WORKSPACE_BRANCH }

    // --- ButEnvironment (host seam) --------------------------------------------------

    override fun repoRoot(): String? = workspaceRepository()?.root?.path

    /**
     * Absolute path to the `but` binary or null. Search order: PATH entries (with PATHEXT
     * suffixes on Windows), then ~/.local/bin/but, /opt/homebrew/bin/but, /usr/local/bin/but.
     * Cached after first success.
     */
    override fun executable(): String? {
        cachedExecutable?.let { return it }
        val found = ButExecutableResolver.resolve(
            osName = System.getProperty("os.name").orEmpty(),
            path = System.getenv("PATH"),
            pathExt = System.getenv("PATHEXT"),
            home = System.getProperty("user.home"),
        ) { File(it).let { f -> f.isFile && f.canExecute() } }
        if (found != null) {
            cachedExecutable = found
        }
        return found
    }

    override suspend fun run(exe: String, repoRoot: String, args: List<String>, timeoutMs: Int): ProcessResult =
        withContext(Dispatchers.IO) {
            try {
                val cmd = GeneralCommandLine(exe)
                cmd.addParameters(args)
                cmd.workDirectory = File(repoRoot)
                cmd.environment["BUT_OUTPUT_FORMAT"] = "json"
                val output = CapturingProcessHandler(cmd).runProcess(timeoutMs)
                if (output.isTimeout) {
                    ProcessResult(timedOut = true)
                } else {
                    ProcessResult(exitCode = output.exitCode, stdout = output.stdout, stderr = output.stderr)
                }
            } catch (e: Exception) {
                ProcessResult(spawnError = e.message ?: e.toString())
            }
        }

    // --- Operations (delegate to the shared client; must not run on the EDT) ---------

    /** Absolute path to the resolved `but` binary, or null. */
    fun butExecutable(): String? = executable()

    fun status(): ButResult<WorkspaceStatus> {
        assertBackgroundThread()
        return runBlocking { client.status() }
    }

    fun push(branchName: String): ButResult<Unit> {
        assertBackgroundThread()
        return runBlocking { client.push(branchName) }
    }

    fun pull(): ButResult<Unit> {
        assertBackgroundThread()
        return runBlocking { client.pull() }
    }

    fun apply(branchName: String): ButResult<Unit> {
        assertBackgroundThread()
        return runBlocking { client.apply(branchName) }
    }

    fun unapply(branchName: String): ButResult<Unit> {
        assertBackgroundThread()
        return runBlocking { client.unapply(branchName) }
    }

    fun newBranch(name: String): ButResult<Unit> {
        assertBackgroundThread()
        return runBlocking { client.newBranch(name) }
    }

    fun commit(branchName: String, message: String, filePaths: List<String>): ButResult<String> {
        assertBackgroundThread()
        return runBlocking { client.commit(branchName, message, filePaths) }
    }

    fun amend(commitId: String, filePaths: List<String>): ButResult<Unit> {
        assertBackgroundThread()
        return runBlocking { client.amend(commitId, filePaths) }
    }

    fun uncommit(id: String): ButResult<Unit> {
        assertBackgroundThread()
        return runBlocking { client.uncommit(id) }
    }

    fun reword(commitId: String, message: String): ButResult<Unit> {
        assertBackgroundThread()
        return runBlocking { client.reword(commitId, message) }
    }

    private fun repositories(): List<GitRepository> =
        GitRepositoryManager.getInstance(project).repositories

    /** The repository this plugin operates on: prefer the one on gitbutler/workspace, else the first. */
    fun workspaceRepository(): GitRepository? {
        val repos = repositories()
        return repos.firstOrNull { it.currentBranchName == ButClient.WORKSPACE_BRANCH } ?: repos.firstOrNull()
    }

    private fun assertBackgroundThread() {
        ApplicationManager.getApplication().assertIsNonDispatchThread()
    }

    companion object {
        private val LOG = Logger.getInstance(GitButlerService::class.java)

        fun getInstance(project: Project): GitButlerService = project.service()
    }
}
