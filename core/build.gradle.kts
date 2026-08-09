plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
}

group = "me.inthefield.gitbutlerforjetbrains"
version = (findProperty("coreVersion") as String?) ?: "0.1.0-dev"

repositories {
    mavenCentral()
}

kotlin {
    jvmToolchain(21)

    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

    jvm()

    js(IR) {
        outputModuleName.set("gitbutler-core")
        nodejs()
        binaries.library()
        generateTypeScriptDefinitions()
        compilerOptions {
            moduleKind.set(org.jetbrains.kotlin.gradle.dsl.JsModuleKind.MODULE_COMMONJS)
            optIn.add("kotlin.js.ExperimentalJsExport")
        }
    }

    sourceSets {
        commonMain {
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.0")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
            }
        }
        commonTest {
            dependencies {
                implementation(kotlin("test"))
            }
        }
    }
}
