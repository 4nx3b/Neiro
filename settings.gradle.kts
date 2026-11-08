pluginManagement {
    repositories {
        google()
        // Google's Maven Central mirror, ahead of the original: identical
        // bytes, but repo.maven.apache.org's CDN 429-rate-limits CI runners
        // and now redirects its biggest jars to GitHub release downloads,
        // which 504 on the runners often enough to fail whole builds. The
        // mirror answers neither way; the original stays as its fallback.
        maven("https://maven-central.storage-download.googleapis.com/maven2/")
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        // Same mirror, same reason, for the build's own classpaths — the
        // Kotlin compiler jar is the one that died through the redirect.
        maven("https://maven-central.storage-download.googleapis.com/maven2/")
        mavenCentral()
        // NewPipeExtractor is published via JitPack.
        maven("https://jitpack.io")
    }
}

rootProject.name = "BitChord"
include(":shared")
include(":sharedUi")

// The Android module only when there is an SDK to build it against. A machine
// set up for the desktop alone has none, and including it there fails at
// configuration time — before the desktop module it has nothing to do with can
// build at all.
// Read as a Properties file, not line by line: Android Studio writes the path
// escaped (`C\:\Users\...` on Windows), and only Properties undoes that.
val androidSdk: String? = file("local.properties")
    .takeIf { it.isFile }
    ?.let { f -> java.util.Properties().apply { f.inputStream().use(::load) } }
    ?.getProperty("sdk.dir")
    ?.trim()
    ?: System.getenv("ANDROID_HOME")
    ?: System.getenv("ANDROID_SDK_ROOT")

if (androidSdk != null && file(androidSdk).isDirectory) {
    include(":app")
    include(":tv")
    include(":wearos")
} else {
    logger.lifecycle("No Android SDK found — building the desktop targets only.")
}
