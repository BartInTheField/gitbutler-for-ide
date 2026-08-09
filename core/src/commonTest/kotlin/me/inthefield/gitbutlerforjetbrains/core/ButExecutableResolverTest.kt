package me.inthefield.gitbutlerforjetbrains.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ButExecutableResolverTest {

    @Test
    fun windows_resolvesExeOnPath() {
        val present = setOf("C:\\bin\\but.exe")

        val found = ButExecutableResolver.resolve(
            osName = "Windows 11",
            path = "C:\\tools;C:\\bin",
            pathExt = ".COM;.EXE;.BAT;.CMD",
            home = "C:\\Users\\me",
            exists = present::contains,
        )

        assertEquals("C:\\bin\\but.exe", found)
    }

    @Test
    fun windows_resolvesCmdShim() {
        val present = setOf("C:\\bin\\but.cmd")

        val found = ButExecutableResolver.resolve(
            osName = "Windows 10",
            path = "C:\\bin",
            pathExt = ".COM;.EXE;.BAT;.CMD",
            home = null,
            exists = present::contains,
        )

        assertEquals("C:\\bin\\but.cmd", found)
    }

    @Test
    fun windows_missingPathExt_usesDefaults() {
        val names = ButExecutableResolver.candidates("Windows 10", "C:\\bin", null, null)

        assertTrue(names.contains("C:\\bin\\but.exe"))
        assertTrue(names.contains("C:\\bin\\but.cmd"))
    }

    @Test
    fun windows_probesExtensionsBeforeBareName() {
        val candidates = ButExecutableResolver.candidates("Windows 10", "C:\\bin", ".EXE", null)

        assertEquals(listOf("C:\\bin\\but.exe", "C:\\bin\\but"), candidates)
    }

    @Test
    fun unix_resolvesBareNameOnPath() {
        val present = setOf("/usr/bin/but")

        val found = ButExecutableResolver.resolve(
            osName = "Linux",
            path = "/opt/x/bin:/usr/bin",
            pathExt = null,
            home = "/home/me",
            exists = present::contains,
        )

        assertEquals("/usr/bin/but", found)
    }

    @Test
    fun unix_fallsBackToLocalBin() {
        val present = setOf("/home/me/.local/bin/but")

        val found = ButExecutableResolver.resolve(
            osName = "Mac OS X",
            path = "/usr/bin",
            pathExt = null,
            home = "/home/me",
            exists = present::contains,
        )

        assertEquals("/home/me/.local/bin/but", found)
    }

    @Test
    fun unix_doesNotProbeWindowsExtensions() {
        val candidates = ButExecutableResolver.candidates("Linux", "/usr/bin", ".EXE;.CMD", null)

        assertTrue(candidates.contains("/usr/bin/but"))
        assertTrue(candidates.none { it.endsWith(".exe") || it.endsWith(".cmd") })
    }
}
