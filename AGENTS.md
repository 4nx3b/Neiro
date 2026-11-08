# AGENTS.md — Neiro (4nx3b fork of kushagrasinghx/BitChord)

Active development on `dev`, tracking `4nx3b/Neiro@main` (merged on release). This
fork exists to port the owner's ArchiveTune features (sources, player, tooling)
into BitChord's multiplatform architecture — one feature per commit, researched
and adapted, never blindly copied. Any agent working here must preserve the
invariants below.

## Mandatory reading order (every task, before any coding)

1. **`rules.md`** (repo root) — the repository owner's standing rules, in full.
   The non-negotiables:
   - **Never compile locally.** Push to `dev`, monitor the CI build for **7 minutes**,
     and fix it if it fails.
   - **Senior Engineer Mode + Strict Mode**: understand before changing, smallest
     complete solution, zero regressions, evidence-driven verification, adversarial
     self-review, closed-loop process (Inspect → Understand → Plan → Implement →
     Integrate → Verify → Review).
   - **Always maintain `AGENTS.md` and `worklog.md`. Always use English.**
2. **`AGENTS.md`** (this file) — fork invariants, module layout, build contracts,
   porting rules.
3. **`worklog.md`** — what previous tasks did; append an entry for the current task
   before finishing (format: `Task ID` / `Agent` / `Task` / `Work Log` /
   `Stage Summary`).

## Porting rules — the ArchiveTune feature pipeline

Features arrive from `4nx3b/ArchiveTune` (single-module Android app, package
`moe.rukamori.archivetune`, InnerTube-based). Neiro is a different architecture:
Kotlin Multiplatform (`shared`, `sharedUi`, `app`, `tv`, `wearos`, `web`,
`backend`), package `com.music.bitchord`, its own InnerTubeX/NewPipe stack, and
its own source system. Porting rules:

- **One feature per commit** (small, reviewable, CI-verified). A feature may be
  split into a chain of commits when it is large; each link must compile and pass
  CI on its own.
- **Adapt, never transplant.** Map the feature onto BitChord's contracts
  (`MusicSource`, `AppSettings`, `AppRoute`, `PlayerHost`, taggers, provers).
  ArchiveTune code is the *behavioural reference* (endpoints, crypto, scoring,
  gotchas), not the shape of the implementation.
- **Prefer existing BitChord machinery** over re-implementations: DASH/HLS
  manifests flow through `StreamContainer` + `OfflineDash`/`ManifestFile`;
  downloads through `Downloader.probe` + `DownloadStore.storable`; playback
  resolution through `SourceResolver`/`SourceRegistry`; login through the
  WebView-capture pattern (`SpotifyLoginScreen`).
- **Keep upstream's documentation voice** in the files you touch where it exists;
  new files created by agents follow rules.md Rule 3 (no comment blocks beyond
  the GPL header — the ArchiveTune convention the owner enforces on his code).
  Do NOT strip BitChord's existing explanatory comments: zero-regression.
- **Never rewire playback around a ported feature.** The resolver chain
  (`bitchord://` → `ResolvingDataSource` → `SourceResolver.resolve`) is the only
  sanctioned path for a new source.

## Fork invariants — never break

- **Tidal source**: `app/.../data/tidal/` (`TidalService` — search, playbackinfo,
  manifest decode, PKCE; `TidalAccountStore` — encrypted token persistence,
  refresh, and the `Subscription` enum) + `data/sources/TidalSource.kt` (the
  `MusicSource` implementation) + `SourceKind.TIDAL` (rank 2, above JioSaavn,
  `canServeLossless`, `worthPrefetching`). Quality
  tiers ride `SourceConfig.qualityOptions`/`pickedQuality` (seeded
  `TidalSource.QUALITY_OPTIONS`); a Lossless request asks at the picked tier,
  with no pick, a stale pick, or a lossy pick all defaulting to 16-bit LOSSLESS
  — Hi-Res is an opt-in, never a landing spot. Streams: AAC/HIGH answers a JSON
  manifest with a direct
  URL; LOSSLESS/HI_RES answers an MPD, which the source writes to
  `cacheDir/tidal-manifests/<trackId>-<tier>.mpd` and hands back as a
  `file://…mpd` URL — playback rides BitChord's DASH machinery
  (`StreamContainer.manifestMimeOf` off the `.mpd` extension, progressive
  sniff-failure → `replayAsManifest` reopen) through
  `ResolvedFileRoutingDataSource` under the resolver, downloads ride
  `Downloader.probe`'s local-file branch → `ManifestFile` assembly. The account
  is a single session (PKCE primary, listen.tidal.com Bearer-capture fallback),
  refreshed through `auth.tidal.com` with the same client id/secret pair as
  ArchiveTune — those constants are protocol, do not change them. Tests:
  `TidalSourceTest` (manifest decode, tier selection, token payload, the local
  MPD download through a real MockWebServer).
- **CI dependency mirrors** (GitHub's archive/release CDN flakes on the
  runners): the Gradle wrapper downloads from the gradle-distributions GitHub
  mirror and the workflows pre-seed it with a patient curl; CMake's Oboe and
  libusb `FetchContent_Declare` list the fork's own `deps` release asset
  (https://github.com/4nx3b/Neiro/releases/tag/deps) before the canonical URL,
  both hash-pinned; `settings.gradle.kts` declares Google's Maven Central
  mirror (`maven-central.storage-download.googleapis.com`) before `mavenCentral()`
  in BOTH `pluginManagement` and `dependencyResolutionManagement` — do not
  reorder, the original redirects its largest jars to GitHub and 504s. Bumping
  Oboe/libusb means uploading the new tarball to the `deps` release (hash
  pinned in the same change) and updating both URLs.
- **Locale parity is test-enforced**: `LocaleStringsTest` fails the build when
  any of the 17 offered languages misses a key from `values/strings.xml`
  (string/plurals/string-array, `translatable="false"` exempt). Every new
  string ships translated into all locale catalogues — including `values-hi`
  (upstream's Diwali strings live there already; never append a duplicate) and
  the untested `values-b+zh+Hant`/`values-zh-rHK`, which are maintained in
  parallel. Upstream itself is currently missing `automix_overview` + the
  Diwali strings outside Hindi; the fork carries the full set.
- **Release signing is secret-gated** (`android.yml`): the fork has no
  `KEYSTORE_BASE64`, so the workflow writes no keystore and the build scripts'
  absent-keystore path produces an unsigned release APK. With the owner's
  signing secrets set, the signed flow is upstream's unchanged.
- **Protected files**: `applicationId` (`com.music.bitchord` dev/prod flavors) is
  upstream identity — never adopt changes that move the shipped package. The
  upstream `bitchord-auth` addon attestation and its server stay untouched.
- **No comment blocks in new fork code** (rules.md Rule 3): the GPL header only.
  When touching a BitChord file, leave its comments alone; when creating a new
  file, keep it comment-clean.

## Modules & submodules

- Gradle modules: `:app` (Android phone), `:tv`, `:wearos`, `:shared` (plain JVM
  common data), `:sharedUi` (Compose Multiplatform screens), `:web`, `:backend`
  (Go Listen-Together server). Android modules are only included when an SDK is
  present (`settings.gradle.kts`).
- `shared/…/data/model/Models.kt` owns `Song`; the app module has no model file.
- Source system: `app/…/data/sources/` — `MusicSource` (search + stream),
  `SourceKind` (rank-ordered), `SourceRegistry` (encrypted-prefs persistence,
  instance rebuilds), `SourceResolver` (matching, upgrades, downloads).
  A non-YouTube track's `videoId` is `src:<configId>::<trackId>`
  (`SourceRegistry.trackKey`), which `Song.toMediaItem()` turns into
  `bitchord://source?s=&t=&n=&a=&d=&l=&e=&m=` for the resolver.
- Downloads: `app/…/download/` — a plain foreground `Service` with 4 workers;
  `Downloads.prepare/routeFor` walks sources, `Downloader.probe` sniffs bytes,
  `ManifestFile` reassembles CMAF manifests into `.flac`/`.m4a`, `OfflineDash`/
  `OfflineHls` save playable packages, taggers embed cover + lyrics.

## Build & test

- JDK 17, Android SDK (compileSdk 37, targetSdk 36), flavors dev/prod.
- CI (`check.yml`): `./gradlew testDevDebugUnitTest` + `assembleDevDebug` + Go
  backend build/vet/test. Push to `dev` or open a PR to run it.
- Fork contract tests live in `app/src/test/java/com/music/bitchord/` — add one
  per ported feature where the logic is JVM-testable (manifest parsing, tier
  mapping, URL shaping).
- New `.kt` files in the fork carry no license-header requirement beyond
  matching upstream files' conventions.

## Agent hygiene

- Never commit credentials, tokens, keystores or agent session state.
- Local working notes are scratch memory — never ship them.
