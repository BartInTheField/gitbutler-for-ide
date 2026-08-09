package me.inthefield.gitbutlerforjetbrains.core

private val nodePath: dynamic = js("require('path')")
private val nodeFs: dynamic = js("require('fs')")

internal actual object PlatformPaths {
    actual fun canonicalize(path: String): String =
        try {
            nodeFs.realpathSync(path) as String
        } catch (_: Throwable) {
            nodePath.resolve(path) as String
        }

    actual fun isAbsolute(path: String): Boolean = nodePath.isAbsolute(path) as Boolean

    actual val separatorChar: Char = (nodePath.sep as String)[0]
}
