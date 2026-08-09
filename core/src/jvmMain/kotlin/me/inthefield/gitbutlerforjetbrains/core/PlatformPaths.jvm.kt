package me.inthefield.gitbutlerforjetbrains.core

import java.io.File
import java.io.IOException

internal actual object PlatformPaths {
    actual fun canonicalize(path: String): String =
        try {
            File(path).canonicalPath
        } catch (_: IOException) {
            File(path).absolutePath
        }

    actual fun isAbsolute(path: String): Boolean = File(path).isAbsolute

    actual val separatorChar: Char = File.separatorChar
}
