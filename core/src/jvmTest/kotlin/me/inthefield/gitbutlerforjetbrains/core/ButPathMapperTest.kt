package me.inthefield.gitbutlerforjetbrains.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import java.io.File
import java.nio.file.Files

class ButPathMapperTest {

    private fun change(cliId: String, filePath: String) =
        UncommittedChange(cliId = cliId, filePath = filePath, changeType = "modified")

    @Test
    fun absolutePathUnderRepoRoot_mapsToCliId() {
        val repoRoot = Files.createTempDirectory("but-repo").toFile()
        val changes = listOf(change("aa", "src/one.txt"))
        val absolute = File(repoRoot, "src/one.txt").absolutePath

        val result = ButPathMapper.map(repoRoot.absolutePath, listOf(absolute), changes)

        assertEquals(listOf("aa"), result.cliIds)
        assertTrue(result.missing.isEmpty())
    }

    @Test
    fun repoRelativePath_maps() {
        val repoRoot = Files.createTempDirectory("but-repo").toFile()
        val changes = listOf(change("bb", "two.txt"))

        val result = ButPathMapper.map(repoRoot.absolutePath, listOf("two.txt"), changes)

        assertEquals(listOf("bb"), result.cliIds)
        assertTrue(result.missing.isEmpty())
    }

    @Test
    fun unknownPath_appearsInMissing() {
        val repoRoot = Files.createTempDirectory("but-repo").toFile()
        val changes = listOf(change("cc", "known.txt"))

        val result = ButPathMapper.map(repoRoot.absolutePath, listOf("unknown.txt"), changes)

        assertTrue(result.cliIds.isEmpty())
        assertEquals(listOf("unknown.txt"), result.missing)
    }

    @Test
    fun symlinkedRoot_stillMaps() {
        val realDir = Files.createTempDirectory("but-real").toFile()
        val linkPath = File.createTempFile("but-link", "").let {
            it.delete()
            it.toPath()
        }
        try {
            Files.createSymbolicLink(linkPath, realDir.toPath())
        } catch (e: Exception) {
            // Symlink creation may be unsupported (e.g. Windows without privileges); skip.
            return
        }

        val changes = listOf(change("dd", "nested/file.txt"))
        // repoRoot given via the symlink, the file path given via the real (canonical) directory.
        val fileViaReal = File(realDir, "nested/file.txt").absolutePath

        val result = ButPathMapper.map(linkPath.toString(), listOf(fileViaReal), changes)

        assertEquals(listOf("dd"), result.cliIds)
        assertTrue(result.missing.isEmpty())
    }

    @Test
    fun duplicateFilePath_yieldsAllCliIds() {
        val repoRoot = Files.createTempDirectory("but-repo").toFile()
        val changes = listOf(
            change("aa", "split.txt"),
            change("bb", "split.txt"),
        )

        val result = ButPathMapper.map(repoRoot.absolutePath, listOf("split.txt"), changes)

        assertEquals(listOf("aa", "bb"), result.cliIds)
        assertTrue(result.missing.isEmpty())
    }

    @Test
    fun cliIds_preserveFilePathsOrder() {
        val repoRoot = Files.createTempDirectory("but-repo").toFile()
        val changes = listOf(
            change("aa", "a.txt"),
            change("bb", "b.txt"),
            change("cc", "c.txt"),
        )

        val result = ButPathMapper.map(
            repoRoot.absolutePath,
            listOf("c.txt", "a.txt", "b.txt"),
            changes,
        )

        assertEquals(listOf("cc", "aa", "bb"), result.cliIds)
        assertTrue(result.missing.isEmpty())
    }
}
