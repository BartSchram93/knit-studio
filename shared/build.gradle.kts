// :shared — the knitting engine, data model, persistence, file format and PDF writer.
// Deliberately free of any UI dependency so it can be tested on the JVM in isolation.
plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKmpLibrary)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.sqldelight)
}

kotlin {
    androidLibrary {
        namespace = "studio.knit.shared"
        compileSdk = libs.versions.androidCompileSdk.get().toInt()
        minSdk = libs.versions.androidMinSdk.get().toInt()
    }
    jvm()
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.sqldelight.runtime)
            implementation(libs.sqldelight.coroutines)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }
        androidMain.dependencies { implementation(libs.sqldelight.android) }
        jvmMain.dependencies { implementation(libs.sqldelight.jvm) }
        iosMain.dependencies { implementation(libs.sqldelight.native) }
    }
}

sqldelight {
    databases {
        create("KnitDatabase") {
            packageName.set("studio.knit.db")
        }
    }
}
