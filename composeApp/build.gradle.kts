// :composeApp — the shared Compose Multiplatform user interface.
plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKmpLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    androidLibrary {
        namespace = "studio.knit.ui"
        compileSdk = libs.versions.androidCompileSdk.get().toInt()
        minSdk = libs.versions.androidMinSdk.get().toInt()
        // Needed so the bundled fonts (Compose resources) are packaged into the APK.
        androidResources { enable = true }
    }
    jvm()
    listOf(iosArm64(), iosSimulatorArm64()).forEach { target ->
        target.binaries.framework {
            baseName = "ComposeApp"
            isStatic = true
        }
        // The SQLite driver needs the system SQLite library. The Xcode app links it itself
        // (-lsqlite3); the test executables built by Gradle need it here.
        target.binaries.all { linkerOpts("-lsqlite3") }
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":shared"))
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            // Bundled fonts (Fraunces for headings, SIL Open Font License: licenses/Fraunces-OFL.txt).
            implementation(compose.components.resources)
            implementation(libs.navigation.compose)
            implementation(libs.lifecycle.viewmodel.compose)
            implementation(libs.lifecycle.runtime.compose)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.datetime)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
        androidMain.dependencies {
            implementation(libs.androidx.activity.compose)
            // Reads the text of pattern PDFs. Bouncy Castle is only needed for certificate-encrypted
            // PDFs, which knitting patterns are not; leaving it out keeps the APK ~8 MB smaller.
            val pdfbox = libs.pdfbox.android.get()
            implementation("${pdfbox.module}:${pdfbox.versionConstraint.requiredVersion}") {
                exclude(group = "org.bouncycastle")
            }
        }
        jvmMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.pdfbox)
            implementation(libs.kotlinx.coroutines.swing)
        }
        jvmTest.dependencies {
            // Headless UI tests and screenshots of the shared interface.
            @OptIn(org.jetbrains.compose.ExperimentalComposeLibrary::class)
            implementation(compose.uiTest)
            implementation(compose.desktop.currentOs)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.sqldelight.jvm)
        }
    }
}

compose.resources {
    packageOfResClass = "studio.knit.ui.res"
    generateResClass = always
}
