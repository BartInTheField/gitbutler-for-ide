// Root build for the GitButler monorepo. Each deliverable lives in its own module:
//   :core      — Kotlin Multiplatform library (jvm + js) with all CLI orchestration/parsing
//   :jetbrains — IntelliJ Platform plugin, consumes :core (jvm target)
//   vscode/    — VSCode extension (TypeScript), consumes :core (js library output)
//
// Plugin versions are declared here once (apply false) so the Kotlin plugin is loaded a
// single time for the whole build; the modules apply them without a version.
plugins {
    kotlin("multiplatform") version "2.1.20" apply false
    kotlin("jvm") version "2.1.20" apply false
    kotlin("plugin.serialization") version "2.1.20" apply false
    id("org.jetbrains.intellij.platform") version "2.6.0" apply false
}
