package me.inthefield.gitbutlerforjetbrains.core

/** Maps IDE/editor-selected file paths to GitButler cliIds. Pure Kotlin over [PlatformPaths]. */
object ButPathMapper {
    data class MapResult(val cliIds: List<String>, val missing: List<String>)

    /** filePaths may be absolute or repo-relative. Matching must survive symlinked roots
     *  (/tmp -> /private/tmp): canonicalize both repoRoot and absolute inputs before
     *  relativizing. Separators normalized to '/'. Order of cliIds follows filePaths. */
    fun map(repoRoot: String, filePaths: List<String>, changes: List<UncommittedChange>): MapResult {
        // One filePath can carry several cliIds (e.g. hunks of a file split between the
        // unassigned area and a stack's assigned changes) — commit all of them, or the
        // result silently contains less than the user selected.
        val byPath = changes.groupBy { it.filePath }

        val cliIds = mutableListOf<String>()
        val missing = mutableListOf<String>()
        for (path in filePaths) {
            val relative = toRepoRelative(repoRoot, path)
            val matches = byPath[relative]
            if (matches.isNullOrEmpty()) {
                missing.add(path)
            } else {
                matches.mapTo(cliIds) { it.cliId }
            }
        }
        return MapResult(cliIds = cliIds, missing = missing)
    }

    private fun toRepoRelative(repoRoot: String, path: String): String {
        if (!PlatformPaths.isAbsolute(path)) {
            return normalizeSeparators(path)
        }
        val rootPath = PlatformPaths.canonicalize(repoRoot)
        val absolute = PlatformPaths.canonicalize(path)
        val sep = PlatformPaths.separatorChar
        val relative = when {
            absolute == rootPath -> ""
            absolute.startsWith(rootPath + sep) -> absolute.substring(rootPath.length + 1)
            else -> absolute
        }
        return normalizeSeparators(relative)
    }

    private fun normalizeSeparators(path: String): String =
        if (PlatformPaths.separatorChar == '/') path else path.replace(PlatformPaths.separatorChar, '/')
}
