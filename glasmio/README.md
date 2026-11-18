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
  Neiro uses its own app-wide line: BOM 2024.12.01, foundation 1.11.0-beta02
  and material3 1.5.0-alpha18. If CI shows these sources need 1.12 APIs, move
  the whole app line in one commit and record why in AGENTS.md.
- Upstream pins `ndkVersion` 27.1.12297006. Here it is left unset, so the
  library uses the same default NDK as `:app`'s own CMake build.
- `activity-compose` is 1.9.3 (the app's pin) instead of 1.13.0.
- The modules keep upstream's `minSdk = 33`. Consumers with a lower minSdk
  need `tools:overrideLibrary`, and must gate every call on API 33 or higher.
