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
    api(platform("androidx.compose:compose-bom:2024.12.01"))
    api("androidx.compose.ui:ui")
    api("androidx.compose.foundation:foundation:1.11.0-beta02")
    api("androidx.compose.material3:material3:1.5.0-alpha18")
    api(project(":glasmio:glass"))
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("sh.calvin.reorderable:reorderable:3.1.0")
}
