plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

group = "com.suginach.typesetter"

compose.resources {
    publicResClass = true
    packageOfResClass = "com.suginach.typesetter.resources"
    generateResClass = always
}

kotlin {
    @OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)
    wasmJs {
        browser {
            commonWebpackConfig {
                outputFileName = "composeApp.js"
            }
        }
        binaries.executable()
    }

    jvm("desktop")

    sourceSets {
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.components.resources)
            implementation(libs.kotlin.coroutines.core)
            implementation(libs.kotlin.serialization.json)
        }

        val desktopMain by getting {
            dependencies {
                implementation(compose.desktop.currentOs)
            }
        }

        val wasmJsMain by getting {
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-browser:0.3")
            }
        }
    }
}

compose.desktop {
    application {
        mainClass = "com.suginach.typesetter.Main_desktopKt"
        nativeDistributions {
            targetFormats(org.jetbrains.compose.desktop.application.dsl.TargetFormat.Msi, org.jetbrains.compose.desktop.application.dsl.TargetFormat.Exe)
            packageName = "ToraBookTypesetter"
            packageVersion = "1.0.0"
        }
    }
}
