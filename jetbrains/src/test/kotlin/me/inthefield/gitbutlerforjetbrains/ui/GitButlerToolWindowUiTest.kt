package me.inthefield.gitbutlerforjetbrains.ui

import com.intellij.remoterobot.RemoteRobot
import com.intellij.remoterobot.fixtures.ComponentFixture
import com.intellij.remoterobot.search.locators.byXpath
import com.intellij.remoterobot.utils.waitFor
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.Duration

/**
 * Remote Robot UI test: connects to a running `runIdeForUiTests` sandbox (started separately
 * on a display), waits for the IDE frame with the seeded GitButler project open, activates
 * the GitButler tool window, and captures a screenshot of the running IDE. This is the
 * JetBrains analog of the VSCode Playwright/ExTester UI e2e.
 *
 * Run: start `runIdeForUiTests -PuiProject=<seeded>` in the background, then `uiTest`.
 */
class GitButlerToolWindowUiTest {

    private val robot = RemoteRobot(System.getProperty("robot-server.url", "http://127.0.0.1:8082"))

    private fun anyMatch(xpath: String): Boolean =
        robot.findAll(ComponentFixture::class.java, byXpath(xpath)).isNotEmpty()

    private fun capture(name: String): File {
        val dir = File("build/uiTestScreenshots").apply { mkdirs() }
        val out = File(dir, name)
        ProcessBuilder("import", "-window", "root", out.absolutePath)
            .redirectErrorStream(true)
            .start()
            .waitFor()
        return out
    }

    @Test
    fun opensGitButlerToolWindowAndCaptures() {
        // 1. Wait for the main IDE frame (indexing on first open can take a while).
        waitFor(Duration.ofSeconds(120), Duration.ofSeconds(2)) {
            anyMatch("//div[@class='IdeFrameImpl']")
        }

        // 2. Trust dialog may appear for a freshly opened project — accept it if present.
        for (label in listOf("Trust Project", "Trust", "Trust and Open")) {
            runCatching {
                robot.find(ComponentFixture::class.java, byXpath("//div[@visible_text='$label']"), Duration.ofSeconds(3)).click()
            }
        }

        // 3. Locate + activate the GitButler tool-window stripe button (new-UI square button
        //    is icon-only with an accessible name; fall back to text/tooltip for the old UI).
        val stripeXpaths = listOf(
            "//div[@accessiblename='GitButler' and @class='SquareStripeButton']",
            "//div[@class='SquareStripeButton' and contains(@accessiblename,'GitButler')]",
            "//div[@tooltiptext='GitButler']",
            "//div[@text='GitButler']",
            "//div[contains(@accessiblename,'GitButler')]",
        )
        var opened = false
        capture("00-ide-frame.png")
        waitFor(Duration.ofSeconds(60), Duration.ofSeconds(2)) { stripeXpaths.any { anyMatch(it) } }
        for (xp in stripeXpaths) {
            if (anyMatch(xp)) {
                runCatching { robot.find(ComponentFixture::class.java, byXpath(xp), Duration.ofSeconds(5)).click() }
                    .onSuccess { opened = true }
                if (opened) break
            }
        }

        Thread.sleep(4000)
        val shot = capture("01-gitbutler-toolwindow.png")

        // 4. Log any text visible in the opened tool window for observation.
        val texts = robot.findAll(ComponentFixture::class.java, byXpath("//div[@class='DnDAwareTree']"))
            .flatMap { it.findAllText() }
            .map { it.text.trim() }
            .filter { it.isNotEmpty() }
        println("TOOLWINDOW_TREE_TEXT=$texts")

        assertTrue("expected to find + click the GitButler tool-window stripe button", opened)
        assertTrue("screenshot should be captured", shot.exists() && shot.length() > 0)
    }
}
