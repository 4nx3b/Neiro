plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "io.github.neoruaa.glasmio.glass"
    compileSdk = 37
    defaultConfig { minSdk = 33 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { compose = true }
    externalNativeBuild { cmake { path = file("src/main/cpp/CMakeLists.txt"); version = "3.22.1" } }
    defaultConfig { consumerProguardFiles("consumer-rules.pro") }
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
    api(project(":glasmio:core"))
    api(project(":glasmio:icons"))
    api("io.github.kyant0:shapes:1.2.1")
}
