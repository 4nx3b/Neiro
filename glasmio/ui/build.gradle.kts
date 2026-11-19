plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "io.github.neoruaa.glasmio.ui"
    compileSdk = 37
    defaultConfig { minSdk = 33 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { compose = true }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    // The app-wide 1.12 line — upstream Glasmio's own BOM, so the vendored
    // sources compile against the APIs they were written for (LayerOutsets
    // and friends arrived with ui 1.12).
    api(platform("androidx.compose:compose-bom:2026.09.00"))
    api("androidx.compose.ui:ui")
    api("androidx.compose.material3:material3:1.5.0-rc01")
    api(project(":glasmio:glass"))
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("sh.calvin.reorderable:reorderable:3.1.0")
}
