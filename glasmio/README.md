# Glasmio, vendored as source

Source: https://github.com/NEORUAA/Glasmio at commit
`905919bc2a289ce7e873fa3185e59c064c89b95d`, Apache-2.0 (see `LICENSE`,
`THIRD_PARTY_NOTICES.md`, `licenses/Apache-2.0.txt`).

| Neiro module      | Upstream module   |
|-------------------|-------------------|
| `:glasmio:core`   | `glasmio-core`    |
| `:glasmio:glass`  | `glasmio-glass`   |
| `:glasmio:icons`  | `glasmio-icons`   |
| `:glasmio:ui`     | `glasmio-ui`      |

Each module's `src/main` (Kotlin, resources, `cpp/`, META-INF notices) is the
upstream tree byte for byte: `diff -r` against a checkout of the pinned commit
must print nothing. `glasmio-glass/consumer-rules.pro` is copied unchanged too.
`glasmio-scaffold`, `glasmio-preference` and `sample` are not vendored:
scaffold's navigation brings in navigation3, navigationevent and miuix, and
Neiro keeps its own router.

Only the build files are Neiro's. They differ from upstream like this:

- Upstream uses AGP 9.4.1 with built-in Kotlin. Neiro uses AGP 9.1.1 with
  `org.jetbrains.kotlin.android` and `android.builtInKotlin=false`.
- Upstream targets JVM 21. Neiro builds JVM 17 bytecode, so these modules'
  inline functions inline into the app.
- Upstream pins Compose BOM 2026.09.00, foundation 1.12.1 and material3 1.4.0.
  Neiro rides the same BOM (2026.09.00 — foundation 1.12.1 through it) with
  material3 at 1.5.0-rc01, the app-wide 1.12 line: the old split line (BOM
  2024.12.01 with foundation pinned to 1.11.0-beta02 and material3 to
  1.5.0-alpha18) resolved a runtime that crashed the release build on
  launch, and CI showed these sources need the 1.12 APIs anyway
  (GlasmioSidebarMorphSurface's LayerOutsets). The move is recorded in
  AGENTS.md; material3 stays on the app's 1.5.0 line because the ported
  player styles draw its expressive widgets, which exist only from the 1.5
  alphas on.
- Upstream pins `ndkVersion` 27.1.12297006. Here it is left unset, so the
  library uses the same default NDK as `:app`'s own CMake build.
- The modules keep upstream's `minSdk = 33`. Consumers with a lower minSdk
  need `tools:overrideLibrary`, and must gate every call on API 33 or higher.
