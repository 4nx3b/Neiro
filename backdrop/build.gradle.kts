/*
 * The liquid-glass renderer, vendored from AndroidLiquidGlass
 * (github.com/guozhiqiang123/AndroidLiquidGlass, a performance fork of
 * Kyant0/backdrop — "Backdrop - OpenLiquid fork", Apache-2.0).
 *
 * Every file under src/main/kotlin/com/kyant/backdrop is the fork's
 * `backdrop` module — its commonMain plus the Android actuals, with the
 * expect/actual pairs collapsed into single files for this plain Android
 * library — at the commit recorded in UPSTREAM_SHA. The fork's performance
 * work rides with it: effect invalidation scoped to the draw node,
 * saturation fused into the lens shader, stationary backdrops that avoid
 * redundant redraws, and cached recorded decoration layers.
 *
 * One deliberate change against the fork's sources: drawBackdrop and
 * drawPlainBackdrop take a `samplingScale: (Int, Int) -> Float` parameter,
 * multiplied with the global BackdropRendering.scale, so a surface can
 * record and process its backdrop at a reduced resolution per call site —
 * the parameter this app's glass surfaces are tuned around (a third, and a
 * quarter under the frost switch), which the fork exposes only globally.
 *
 * The fork is not published to Maven Central and has no JitPack build (its
 * Gradle is AGP 9.3.2 / KMP / publishToMavenCentral), which is why the
 * sources are vendored here and compiled by this app's own toolchain.
 * Its one Maven dependency is: io.github.kyant0:shapes (Kyant's SDF shape
 * library the lens's corner-radius extraction reads).
 */
plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.kyant.backdrop"
    compileSdk = 37

    defaultConfig {
        // The renderer itself guards RenderEffect at API 31 and RuntimeShader
        // at API 33; the module stays open so any consumer can adopt it.
        minSdk = 21
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    // The app-wide 1.12 line (BOM 2026.09.00), the same pin :app and
    // :archivetuneplayer compile their Compose against — see app/build.gradle.kts.
    val composeBom = platform("androidx.compose:compose-bom:2026.09.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.annotation:annotation")
    // The @Language("AGSL") annotations on the shader strings.
    implementation("org.jetbrains:annotations:26.1.0")
    // Kyant's SDF shapes — Capsule and the rounded-rectangular shape the lens
    // extracts its corner radii from. `api` so consumers building glass
    // components of their own see the same shapes the renderer does.
    api("io.github.kyant0:shapes:1.2.1")
}
