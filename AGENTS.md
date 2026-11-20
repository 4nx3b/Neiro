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
- **Deezer source**: `app/.../data/deezer/` (`DeezerCrypto` — the
  Blowfish/CBC stripe over 2048-byte chunks, every third chunk starting at
  zero, key = MD5(trackId) hex XOR the salt `g4el58wc0zvf9na1`, and the
  `deezer://stream?u=&k=&s=` URI built/parsed in pure Kotlin so the JVM tests
  reach it; `DeezerService` — gw-light.php gateway with the arl cookie, the
  45-minute session (checkForm api_token + license_token), public-API search
  with gateway fallback, `media.deezer.com/v1/get_url` with the
  BF_CBC_STRIPE cipher ladder, the three anonymous resolvers as the
  signed-in fallback, ISRC lookup, and the decrypting probe/fetch for
  downloads; `DeezerAccountStore` — the encrypted arl + account name +
  lossless flag; `DeezerDecryptingDataSource` — the Media3 DataSource) +
  `data/sources/DeezerSource.kt` + `SourceKind.DEEZER` (rank 2, beside Tidal).
  The account is the `arl` cookie deezer.com sets — no refresh machinery, an
  ARL works until it is stood down. Signed out, search and stream answer
  nothing (the anonymous resolvers are a signed-in fallback, not a
  default-on catalogue). Streams answer `deezer://stream?...`, which
  `ResolvedFileRoutingDataSource` routes to the decrypting DataSource under
  the resolver, so the cache stores PLAIN bytes under the phone key and
  downloads ride `Downloader.probe`/`fetchDirect`'s deezer branches with no
  Deezer-specific code outside the deezer package. Tests: `DeezerSourceTest`
  (key derivation, chunk schedule, URI round trip, payload decodes, tier
  ladder, the decrypting download over a striped MockWebServer body).
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
- **Release signing is secret-gated** (`android.yml`): the fork's release key
  lives in the `KEYSTORE_*` repo secrets (set 2026-11-08; the keystore backup
  and its password were handed to the owner out-of-band — losing them means no
  build can update an existing install). Nightlies are therefore signed and
  install over one another. The nightly artifact is the **arm64-v8a phone APK
  only** (`:app:assembleProdRelease -Pbitchord.abis=arm64-v8a`, artifact
  `bitchord-prod-release-arm64-v8a-signed`). The `bitchord.abis` property
  (comma-separated) narrows `:app`'s `splits.abi` at configuration time and
  turns the universal split off — NEVER substitute AGP's
  `android.injected.build.abi`: that IDE-deployment property is process-global
  and reaches `:wearos`, where it leaves the module without its single normal
  APK output and `bundleWearApkProdRelease` dies on "Expected exactly one
  watch APK". The wear APK still builds and rides INSIDE the phone APK as the
  in-app watch-install asset (Settings → Wear OS); it is just not uploaded as
  a pipeline artifact of its own. With the secrets absent (a PR from a fresh
  clone), the build scripts' own absent-keystore path produces an unsigned
  release APK instead of failing.
- **Protected files**: `applicationId` (`com.music.bitchord` dev/prod flavors) is
  upstream identity — never adopt changes that move the shipped package. The
  upstream `bitchord-auth` addon attestation and its server stay untouched.
- **No comment blocks in new fork code** (rules.md Rule 3): the GPL header only.
  When touching a BitChord file, leave its comments alone; when creating a new
  file, keep it comment-clean.

- **Liquid Glass (2026-11-20).** The earlier JitPack glass fork and its
  second-renderer crash patches are gone. The
  glass now rides the vendored AndroidLiquidGlass backdrop module (below),
  which draws through the ordinary per-surface graphics layer.

## Modules & submodules

- Gradle modules: `:app` (Android phone), `:tv`, `:wearos`, `:shared` (plain JVM
  common data), `:sharedUi` (Compose Multiplatform screens), `:backdrop` (the
  vendored liquid-glass renderer), `:web`, `:backend`
  (Go Listen-Together server). Android modules are only included when an SDK is
  present (`settings.gradle.kts`).
- **The Compose line is the 1.12 era, whole** (`compose-bom:2026.09.00`, so
  ui/foundation 1.12.1; material3 pinned app-wide at 1.5.0-rc01 for the ported
  player styles' expressive widgets). The old split line (BOM 2024.12.01 with
  foundation 1.11.0-beta02 and material3 1.5.0-alpha18) resolved a split
  runtime — the JetBrains Compose plugin sharedUi rides elevated androidx ui
  while those pins held foundation/material3 back — and the mixed APK crashed
  the release build on launch. Do not pin
  modules to a different line than the app resolves.
- **The Liquid Glass renderer is `:backdrop`** — the backdrop module of
  guozhiqiang123/AndroidLiquidGlass (Kyant's fork, Apache-2.0), vendored
  verbatim (commonMain + the Android actuals, expect/actual collapsed) at
  the commit in `backdrop/UPSTREAM_SHA` and compiled by this repo's own
  toolchain — the fork is published nowhere (no Maven Central, no JitPack
  build) so vendoring is the only reliable consume. The local changes
  against the fork's sources are listed in `backdrop/PATCHES.md`
  (samplingScale, Lens `chromaticStrength`, the process-wide
  `RuntimeShaderPool` + `prewarmBackdropShaders()` called from
  `BitChordApplication`). Its one Maven dependency is Kyant's
  `io.github.kyant0:shapes:1.2.1` (the SDF capsules the lens reads). The
  module minSdks 21, below the app's 26 — no `tools:overrideLibrary` is
  needed anywhere. The liquid-glass controls — sliders, toggles, the tab
  bar, the tracking highlight — are ported from the fork's catalog into
  `app/…/ui/components/liquid/` (BitChord's theme, backdrop and tuning
  applied); MainActivity wires the app backdrop and the `LocalGlassSlider`
  slot over them.
  - **Owners of each surface.**
    - `IosSwitch` → `LiquidGlassSwitch` (the catalog toggle, 64×28 with the
      40×24 lens thumb) wherever Liquid Glass is on and supported; the plain
      iOS 26 capsule stays below API 31, glass-off and read-out rows.
    - The settings `SliderRow` → `LiquidGlassSlider`, the drag quantised to
      M3's own steps + 1 intervals at the call site; M3's `Slider` stays as
      the fallback.
    - sharedUi `ThinSlider` (scrubber, volume, lyrics offset, audio output)
      → `GlassSlider` (`LiquidGlassSlider` + `snapToSteps`, in
      `ui/components/GlassControls.kt`) through the `LocalGlassSlider` slot
      MainActivity provides. A degenerate or non-finite range, or a device
      without the AGSL lens (`liquidGlassControlsSupported`, API 33+), falls
      back to Material's `Slider`. ThinSlider keeps drawing itself while it
      is loading, mixing, pulsing or showing an Automix window.
    - `IosMenuPopover` and `HeldContextMenu` → the popup morph rides the
      pre-port spring constants (damping 0.82, stiffness 300) (`GlassMorph.kt`, tested in
      `GlassMorphTest`); :archivetuneplayer's menus run ArchiveTune's
      own original springs again.
    - `GlassNavBar`, `ui/components/floatingtabbar/` (FloatingTabBar,
      GlassSelectionPill, RubberStretch), `FloatingBottomBar` and
      `MiniPlayer` are a port of rezapranata089-bit/Zenith@01e2ab1 (package
      renamed only; springs, thresholds, JellyPush and RubberStretch kept
      verbatim). Zenith's `AppSettings.glassIndicator` is
      `data/settings/GlassIndicator.kt`, pinned to Zenith's DEFAULT preset with
      no settings UI. Zenith's player panel morph (`PlayerDock.panelShown`) is
      not ported: Neiro's full player is a sheet, not a panel.
      `CompactControlCircles.kt` holds Neiro's ArchiveTune compact circles.
    - Page push/pop and the album zoom → the original iOS push/pop slide
      (`IosPageTransitions`/`PageMotion`; `PageMotionTest` pins it).
  - The renderer itself guards `RenderEffect` at API 31 and `RuntimeShader`
    at API 33; `isGlassSupported()` (API 31) gates every glass surface, and
    the lens-based controls are additionally gated on
    `liquidGlassControlsSupported` (API 33).
- Glass invariant: no drawBackdrop may sample a LayerBackdrop whose recorded
  content contains it. In-page liquid controls default to `emptyBackdrop()`.
  Defaults trace to `ui/components/liquid/LiquidGlassDefaults.kt`
  (AndroidLiquidGlass catalog @0ed8b08). Pages inside `layerBackdrop(appBackdrop)`
  get `LocalAppBackdrop = emptyBackdrop()`; the nav bar is drawn outside that
  box (`GlassRecursionGuardTest`).
- Performance invariants: never trade quality for speed (no samplingScale,
  `BackdropRendering.scale`, blur or refraction reductions). `app/` and `tv/`
  ship `src/main/baseline-prof.txt` plus profileinstaller; keep it covering
  the UI packages, `com/kyant/backdrop` and the ArchiveTune UI. Read
  per-frame values in layout/draw lambdas only. launch-smoke runs an
  interaction phase and prints a perf block (cold start ×3, gfxinfo),
  as installed and after speed-profile.
- While a portrait player is fully up, MainActivity's page root skips
  `drawContent()` (`coveredByPlayer`, a derivedStateOf on
  `playerDock.openFraction() >= 1f`). That box holds both the
  `layerBackdrop(appBackdrop)` recorder and `GlassNavBar`, so the page is not
  recorded while covered, and recording resumes on the first frame the sheet
  moves down. Nothing in the player (its own dialog window) samples
  `appBackdrop`. Keep both inside that box.
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
- **main is the active line** (since 2026-11-18: the liquid-glass port and the
  Compose 1.12 / material3 1.5.0-rc01 move landed there; dev is behind).
  Branch from `origin/main` and PR into main.
- **Launch smoke** (`launch-smoke.yml`, on every PR and by hand). It builds
  `:app:assembleDevDebug` and `:app:assembleProdRelease` (R8) for x86_64
  once and prints the resolved Compose line. An unsigned release is signed
  with the debug key. It then installs and launches each APK on an API 30
  (below the glass gate) and an API 34 emulator, using
  `.github/scripts/launch-smoke.sh`.
  - The packages are `com.dev.bitchord` (dev) and `com.music.bitchord`
    (prod).
  - A leg fails if the process is dead 25 s after launch, or on a
    `FATAL EXCEPTION`, native crash or ANR. The 60 lines after each fatal
    exception are printed into the job log.
  - The logcats and screenshots are the `launch-smoke-api<N>` artifacts.
  - This is the only launch evidence there is (no SDK or device in the
    agent sandbox). Read it before guessing at a crash.
- material3 1.5.0-rc01 removed `thumb`/`track` from the value-based `Slider`.
  A custom track needs the `SliderState` overload: a state keyed on the
  range, with the external value synced each recomposition. See SliderRow
  and archivetuneplayer's PlayerComponents.
- Fork contract tests live in `app/src/test/java/com/music/bitchord/` — add one
  per ported feature where the logic is JVM-testable (manifest parsing, tier
  mapping, URL shaping).
- New `.kt` files in the fork carry no license-header requirement beyond
  matching upstream files' conventions.

## Agent hygiene

- Never commit credentials, tokens, keystores or agent session state.
- Local working notes are scratch memory — never ship them.

- **Qobuz source**: `app/.../data/qobuz/` (`QobuzService` — the official API's
  `track/search`, `track/getFileUrl` with the MD5 `request_sig` over the
  sorted parameter string (`trackgetFileUrlformat_id{n}intentstreamtrack_id{id}{ts}{secret}`),
  `user/get` subscription standing, and the pure payload parsers;
  `QobuzAccountStore` — the encrypted user_auth_token + app id + app secret +
  premium flag) + `data/sources/QobuzSource.kt` + `SourceKind.QOBUZ`
  (rank 2, beside Tidal/Deezer). The credentials come from the sign-in
  WebView: the web player's own X-User-Auth-Token and X-App-Id read off its
  requests, and the app secret scraped from its JS bundles (32-char hex), with
  a manual paste field when scraping fails — see `auth/QobuzLoginScreen.kt`.
  Streams are plain signed HTTPS URLs (no decryption layer, unlike Deezer);
  format ids ride the quality picker (MAX 27 / Hi-Res 7 / FLAC 6 / MP3 5) and
  a Lossless request defaults to 16-bit FLAC (6) — 24-bit is an opt-in, the
  same rule as Tidal's Hi-Res. `sample: true` answers are previews: rejected
  and failure-cached, never played. Tests: `QobuzSourceTest` (the signature
  MD5, payload decodes, preview rejection, the tier mapping, and a signed
  end-to-end getFileUrl through a MockWebServer via `apiBaseForTest`).
- **SoundCloud source**: `app/.../data/soundcloud/` (`SoundCloudService` —
  the web app's client id + app version scraped off soundcloud.com's HTML and
  sndcdn JS bundles, 12h TTL with a force-refresh retry; api-v2
  `search/tracks`, `tracks/{id}`, and the transcoding walk
  `media.transcodings[]` → selected rendition → its `url` endpoint answered
  with `client_id` + `track_authorization`; the AAC_160/MP3_128/AAC_96 tier
  ladder — a deliberate repair of ArchiveTune's, whose
  `maxWithOrNull(compareByDescending { score })` silently selects the
  *lowest*-scored candidate (a descending comparator inverts what maxWith
  calls greatest), so its "progressive preferred" 1000-point bonus actually
  lost to HLS whenever both existed; Neiro scores with
  `maxWithOrNull(compareBy { … })` so the tier match genuinely wins, the
  bitrate comes from the preset (the API's HLS mimes — `audio/mp4`,
  `audio/mpegurl` — never say "aac", so the preset is the only honest
  signal), and the codec is preset-first for the same reason;
  SNIP-policy and unstreamable rows dropped; stream/failure caches with
  5/10-minute TTLs; `normalizeAuthToken` for the OAuth token, applied as
  `Authorization: OAuth` on api-v2 calls — ArchiveTune captured the token but
  never attached it, so login-gated uploads stayed gated there;
  `SoundCloudAccountStore` — the encrypted OAuth token + username) +
  `data/sources/SoundCloudSource.kt` + `SourceKind.SOUNDCLOUD` (rank 3, beside
  JioSaavn, **opt-in**: `SourceConfig.enabled` defaults false for it — the
  catalogue is user uploads and a title match can be the wrong recording).
  Anonymous search/stream works signed-out (health stays `Ok`); the optional
  sign-in (`auth/SoundCloudLoginScreen.kt`) loads soundcloud.com/signin and
  sweeps cookies, localStorage, sessionStorage and the runtime `SC.connector`
  for the token, verified through `/me`, with a manual paste field below.
  HLS answers declare `StreamContainer.declare(url, "hls")` so the extension
  sniff is not load-bearing; progressive answers carry
  `SoundCloudService.mediaHeaders()` on the DataSpec, and the shared
  `Http.client` gained a sndcdn/media-streaming host interceptor that stamps
  the browser UA + Referer/Origin **only when absent** — Media3's HLS loader
  builds its own playlist/segment requests, which never see a DataSpec's
  headers, so the interceptor is the only layer that reaches them (the same
  reason WebDAV's auth rides there). Tests: `SoundCloudSourceTest` (the
  scraping regexes, the search decode with SNIP/unstreamable filters, the
  tier ladder, the preset bitrate fallbacks, token normalization, the
  request-tier mapping, the end-to-end transcoding walk over a MockWebServer
  via `apiBaseForTest`/`clientIdForTest`/`appVersionForTest`, and the
  failure cache).
- **Music Together** (the Listen Together page's replacement, ported from
  ArchiveTune's `together` package): `app/.../data/together/` — the protocol
  (`TogetherMessages`, a `type`-discriminated sealed `TogetherMessage` with
  chat: `ChatSendRequest` → `ChatMessage`), `TogetherServer` (the host IS the
  server: an embedded Ktor CIO engine on `0.0.0.0:<togetherDefaultPort>` with
  a `/together` WebSocket, protocol version + session key handshake, approval
  queue, host transfer, chat rate limit 10/5s), the online relay stack
  (`TogetherOnlineEndpoint` resolves the relay base from
  `NeiroKoiverseServer.txt` on the repo's `dev` branch, 6h cache;
  `TogetherOnlineApi` create/resolve/end with the `TOGETHER_BEARER_TOKEN`
  BuildConfig field — seeded from local.properties/env, empty is a supported
  "online unavailable" state; `TogetherOnlineHost` is the host's relay
  client), `TogetherClient` (the guest socket, ws→wss retry),
  `TogetherEngine` (the object every surface reads: session/room state flows,
  500ms host broadcasts, guest control/add request planning, NTP-ish
  `TogetherClock`, `applyingRemote` echo suppression, the room-events
  notification channel) — behind the `data/listentogether/ListenTogether.kt`
  facade that keeps the old `State` shape (`inParty`/`controlsLocked`/
  `awaitingStart`/`nickname()`) the player surfaces, PlaybackService's
  AutoPlay election and MiniPlayer already speak, and the rewritten
  `playback/PartySync.kt` that owns the service-side doors
  (`onLocalIntent` debounced to guest `ControlRequest`/`AddTrackRequest`,
  `onPlayWhenReadyChanged` focus-loss dropout, `onLockedTransport`,
  `shouldDeferPlay`, queue-drag flushing). The UI is
  `ui/screens/MusicTogetherScreen.kt` (status/playback/host/join/participants/
  chat/activity-log cards + the welcome dialog); the deep link is
  `bitchord://together?host=&port=&sid=&key=` (`TogetherLink.decode` +
  `TogetherDeepLink.consume`, the manifest filter on MainActivity). Because
  `:tv` compiles the phone app's sources, its build.gradle carries the same
  ktor-server trio and `TOGETHER_BEARER_TOKEN` field. LAN sessions are
  cleartext `ws://` — dev builds allow it, prod keeps
  `usesCleartextTraffic="false"`, so the relay is the only path there. The
  together_* strings ship `translatable="false"` until the locale pass; the
  old party-server BuildConfig (`LISTEN_TOGETHER_SERVER`) and its CI seeding
  are gone. The Go `backend/` and `web/` modules still exist for the old
  party protocol and are untouched by this port.

- **Liquid Glass is always on (curialy/glass-always).** There is no master switch and no
  per-element switch for the nav bar or mini player; `isGlassSupported()` (API 33+
  RuntimeShader) is the only gate. CrashLoopGuard no longer turns glass off. Nav-bar drag
  invariants: the swell scales only the bar's surface Box, never the tab Row;
  `GlassPillMotion.dragTo` writes the snapshot position synchronously (no `scope.launch`);
  `PillTinted` clips to `PillGeometry.visibleWidth`. Guarded by LiquidGlassAlwaysOnGuardTest.
- **Diagnostics (dev debug only).** LeakCanary 2.14 is a `debugImplementation`, and StrictMode
  (penaltyLog) is set by `app/src/debug/kotlin/.../DebugDiagnosticsProvider.kt`. Keep that file
  under `src/debug/kotlin`: tv's debug source set adds only `app/src/debug/java`. Compose
  stability lives in `compose_stability.conf` (list only vals-only classes).
- **Ported player styles (curialy/drift-lyrics-acrylic-perf).** Vendored edits marked
  `BRIDGE(neiro)`: FlamingoPlayer.kt and TikTokSongPage.kt carry `driftArtworkSlot` (the dock
  drift); FlamingoBackground.kt and TikTokSongPage.kt use `bridge/BitChordWander.kt` (a verbatim
  copy of sharedUi's BlurWanderDrift: 18 dp, ±3°, 5-10 s legs, 1.06 overscan) under "Moving blur";
  LyricsUtils.kt's `ENHANCED_LRC_WORD_TIME_REGEX` has capture groups (without them
  `toLrcTimestampMs()` threw and enhanced-LRC word timing never parsed). Acrylic blur
  (`AppSettings.acrylicBlur`, `acrylic_blur`) swaps every `optimizedHazeEffect` style for
  `acrylicFrost` (32 dp, 0.12 noise, 0.72 tint) via sharedUi `AcrylicMaterial`; glass is untouched.
- **Startup (curialy/drift-lyrics-acrylic-perf).** `:baselineprofile` (com.android.test →
  :app, androidx.baselineprofile 1.5.0) holds `BaselineProfileGenerator` (startup profile
  included) and `StartupBenchmark` (None vs Partial(Require)); `.github/workflows/baseline-profile.yml`
  runs both on an API 34 emulator and uploads `app/src/**/generated/baselineProfiles/`. Commit
  the generated files; the hand-written `app/src/main/baseline-prof.txt` stays until then.
  The shader prewarm and the ArchiveTune player host start two frames after the first frame;
  `ReportDrawnWhen` fires once the home feed leaves Loading.
- **Liquid Glass fine tuning.** `data/settings/LiquidGlassUpstream.kt` holds the upstream
  defaults (AndroidLiquidGlass playground: corner 0.5, chromatic 0; Zenith GlassPreset.DEFAULT:
  clarity 0.13, saturation 1, refraction width 0.36, rainbow 0, rim 1, quality 1). Each is applied
  relative to its default, so the defaults draw today's glass exactly (LiquidGlassUpstreamTest).
