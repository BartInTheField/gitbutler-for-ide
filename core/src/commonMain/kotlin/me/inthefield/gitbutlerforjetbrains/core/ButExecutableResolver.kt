package me.inthefield.gitbutlerforjetbrains.core

/**
 * Resolves the path to the `but` CLI binary. Pure Kotlin operating on strings — the OS
 * name, PATH, PATHEXT, and home directory are passed in, and separators are derived from
 * the target OS rather than the host JVM, so resolution is deterministic when unit-tested
 * cross-platform.
 *
 * On Windows a bare `but` on PATH is really `but.exe` (or a `but.cmd`/`but.bat` shim); the
 * shell finds it via PATHEXT, so each PATH entry must be probed with those suffixes.
 */
object ButExecutableResolver {
    private const val BINARY_NAME = "but"
    private const val DEFAULT_PATHEXT = ".COM;.EXE;.BAT;.CMD"

    fun resolve(
        osName: String,
        path: String?,
        pathExt: String?,
        home: String?,
        exists: (String) -> Boolean,
    ): String? = candidates(osName, path, pathExt, home).firstOrNull(exists)

    /**
     * Candidate paths to probe, in order: for each PATH entry every executable name, then
     * the Unix fallback install locations. On Windows the executable name gets each PATHEXT
     * suffix; elsewhere it is the bare name.
     */
    fun candidates(osName: String, path: String?, pathExt: String?, home: String?): List<String> {
        val windows = osName.lowercase().contains("win")
        val pathSep = if (windows) ';' else ':'
        val fileSep = if (windows) '\\' else '/'
        val names = binaryNames(windows, pathExt)

        val result = mutableListOf<String>()
        path?.split(pathSep)
            ?.filter { it.isNotBlank() }
            ?.forEach { dir ->
                val base = dir.trimEnd(fileSep)
                names.forEach { name -> result.add("$base$fileSep$name") }
            }

        if (!windows) {
            if (!home.isNullOrBlank()) result.add("${home.trimEnd('/')}/.local/bin/$BINARY_NAME")
            result.add("/opt/homebrew/bin/$BINARY_NAME")
            result.add("/usr/local/bin/$BINARY_NAME")
        }
        return result
    }

    private fun binaryNames(windows: Boolean, pathExt: String?): List<String> {
        if (!windows) return listOf(BINARY_NAME)
        val exts = (pathExt?.takeIf { it.isNotBlank() } ?: DEFAULT_PATHEXT)
            .split(';')
            .filter { it.isNotBlank() }
            .map { it.lowercase() }
        return exts.map { "$BINARY_NAME$it" } + BINARY_NAME
    }
}
