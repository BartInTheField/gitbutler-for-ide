plugins {
    kotlin("jvm")
    id("org.jetbrains.intellij.platform")
}

group = "me.inthefield.gitbutlerforjetbrains"
// CalVer (YYYY.M.D.BUILD) is injected by CI via -PpluginVersion; local builds use the fallback.
version = (findProperty("pluginVersion") as String?) ?: "0.1.0-dev"

repositories {
    mavenCentral()

    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    implementation(project(":core"))

    // :core exposes suspend functions (ButClient/ButEnvironment). The IntelliJ Platform
    // bundles kotlinx-coroutines at runtime, so we compile against it but never bundle it
    // (see the runtimeClasspath exclusion below) — bundling a second copy breaks the
    // platform's coroutine dispatchers.
    compileOnly("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")

    intellijPlatform {
        intellijIdeaCommunity("2025.1.4.1")
        bundledPlugin("Git4Idea")
    }

    testImplementation(kotlin("test"))
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.testcontainers:testcontainers:1.21.0")

    // IntelliJ Remote Robot — Swing UI automation client for the runIdeForUiTests task.
    testImplementation("com.intellij.remoterobot:remote-robot:0.11.23")
    testImplementation("com.intellij.remoterobot:remote-fixtures:0.11.23")
}

// The IntelliJ Platform ships the Kotlin stdlib and JetBrains annotations at runtime, so
// bundling them into the plugin zip is redundant (~2.3 MB) and can shadow the platform's
// own copies. kotlinx-serialization is OUR dependency (via :core) and stays bundled.
// This restores the lean distribution the pre-monorepo `kotlin.stdlib.default.dependency`
// setting gave, without disabling stdlib for the multiplatform :core build.
configurations.runtimeClasspath {
    exclude(group = "org.jetbrains.kotlin", module = "kotlin-stdlib")
    exclude(group = "org.jetbrains", module = "annotations")
    // Provided by the IntelliJ Platform at runtime (see compileOnly above).
    exclude(group = "org.jetbrains.kotlinx", module = "kotlinx-coroutines-core")
    exclude(group = "org.jetbrains.kotlinx", module = "kotlinx-coroutines-core-jvm")
}

intellijPlatform {
    pluginConfiguration {
        ideaVersion {
            sinceBuild = "251"
            untilBuild = provider { null }
        }
    }

    // `./gradlew :jetbrains:verifyPlugin` runs the JetBrains Plugin Verifier against the
    // same IDE build we compile against (already downloaded — no extra fetch), catching
    // unresolved references such as the platform-provided kotlinx-coroutines.
    pluginVerification {
        ides {
            ide(org.jetbrains.intellij.platform.gradle.IntelliJPlatformType.IntellijIdeaCommunity, "2025.1.4.1")
        }
    }
}

// Launches a sandbox IDE with the JetBrains robot-server plugin so a Remote Robot client
// (the `*.ui.*` tests) can drive the Swing UI and capture screenshots — the JetBrains
// analog of ExTester/Playwright for the VSCode side. Run it in the background on a display
// (xvfb) and then run the UI test against http://127.0.0.1:8082.
val runIdeForUiTests by intellijPlatformTesting.runIde.registering {
    task {
        jvmArgumentProviders += org.gradle.process.CommandLineArgumentProvider {
            listOf(
                "-Drobot-server.port=8082",
                "-Drobot-server.host.public=true",
                "-Dide.mac.message.dialogs.as.sheets=false",
                "-Djb.privacy.policy.text=<!--999.999-->",
                "-Djb.consents.confirmation.enabled=false",
                "-Dide.show.tips.on.startup.default.value=false",
                "-Dide.browser.jcef.enabled=false",
                "-Didea.trust.all.projects=true",
            )
        }
        // Open a GitButler workspace on launch: -PuiProject=/abs/path
        providers.gradleProperty("uiProject").orNull?.let { args(it) }
    }
    plugins {
        robotServerPlugin()
    }
}

kotlin {
    jvmToolchain(21)
}

// Keep the distributable named gitbutler-intellij-<version>.zip (the module is :jetbrains,
// but the released artifact name must stay stable across the monorepo migration).
tasks.named("buildPlugin", Zip::class) {
    archiveBaseName.set("gitbutler-intellij")
}

tasks.test {
    // The Remote Robot UI tests need a running runIdeForUiTests sandbox, so keep them out of
    // the normal unit/integration run; execute them explicitly via the `uiTest` task.
    filter {
        isFailOnNoMatchingTests = false
        excludeTestsMatching("me.inthefield.gitbutlerforjetbrains.ui.*")
    }

    // Testcontainers (used by the GitButler CLI integration tests) needs to reach the Docker
    // daemon. On setups where the active daemon is not on the default /var/run/docker.sock
    // (e.g. colima, rootless), the docker-java client fails to auto-detect it. Resolve the
    // socket of the active `docker context` in doFirst — execution time, so no external
    // process runs at configuration time (configuration-cache safe). Under colima only, also
    // disable Ryuk — its reaper container fails to start there. All of it is a no-op when
    // Docker is absent; the tests then self-skip via an Assume on
    // DockerClientFactory.isDockerAvailable, so the build still succeeds.
    // The IntelliJ test runtime injects -Djna.boot.library.path=<ide>/lib/jna and
    // -Djna.noclasspath=true through a JVM argument provider, which breaks the JNA jar
    // Testcontainers loads (native 7.0.0 vs expected 6.1.6) — Docker detection then dies
    // with java.lang.Error and the integration tests silently self-skip. Appending our
    // provider after the plugin's lets these later -D flags win, so JNA unpacks its own
    // matching native from the classpath jar. Plain `systemProperty` loses that race.
    jvmArgumentProviders.add {
        listOf("-Djna.boot.library.path=", "-Djna.noclasspath=false", "-Djna.nosys=true")
    }
    doFirst {
        if (System.getenv("DOCKER_HOST").isNullOrBlank()) {
            val dockerHost = runCatching {
                val proc = ProcessBuilder(
                    "docker", "context", "inspect", "--format", "{{.Endpoints.docker.Host}}",
                ).redirectErrorStream(true).start()
                if (proc.waitFor() == 0) proc.inputStream.bufferedReader().readText().trim() else null
            }.getOrNull()
            if (!dockerHost.isNullOrBlank()) {
                environment("DOCKER_HOST", dockerHost)
                if (dockerHost.contains("colima")) {
                    environment("TESTCONTAINERS_RYUK_DISABLED", "true")
                }
            }
        }
    }
}

// Runs ONLY the Remote Robot UI tests against an already-running runIdeForUiTests sandbox.
// Pass the seeded GitButler project via -Dgitbutler.uiTestProject=<path>.
val uiTest by tasks.registering(Test::class) {
    val testSources = sourceSets.test.get()
    testClassesDirs = testSources.output.classesDirs
    classpath = testSources.runtimeClasspath
    useJUnit()
    // remote-robot's Gson reflects into java.base (e.g. Throwable) — open it on JDK 21.
    jvmArgs(
        "--add-opens=java.base/java.lang=ALL-UNNAMED",
        "--add-opens=java.base/java.util=ALL-UNNAMED",
        "--add-opens=java.base/java.lang.reflect=ALL-UNNAMED",
        "--add-opens=java.base/java.io=ALL-UNNAMED",
    )
    filter { includeTestsMatching("me.inthefield.gitbutlerforjetbrains.ui.*") }
    systemProperty("gitbutler.uiTestProject", System.getProperty("gitbutler.uiTestProject", ""))
    systemProperty("robot-server.url", System.getProperty("robot-server.url", "http://127.0.0.1:8082"))
}
