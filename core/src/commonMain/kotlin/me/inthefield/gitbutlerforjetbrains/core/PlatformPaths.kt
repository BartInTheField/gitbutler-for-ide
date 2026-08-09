package me.inthefield.gitbutlerforjetbrains.core

/**
 * The small slice of filesystem/path behavior [ButPathMapper] needs, provided per target:
 * the JVM actual uses java.io.File; the JS actual uses Node's `path`/`fs`. Both must
 * canonicalize symlinked roots (e.g. macOS /tmp -> /private/tmp) so relativizing is stable.
 */
internal expect object PlatformPaths {
    /** Resolve symlinks and make absolute; fall back to a best-effort absolute path on error. */
    fun canonicalize(path: String): String

    fun isAbsolute(path: String): Boolean

    val separatorChar: Char
}
