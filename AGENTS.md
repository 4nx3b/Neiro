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
  ladder where the *tier match* outranks progressive-vs-HLS — a deliberate
  deviation from ArchiveTune, which hard-prefers progressive because its HLS
  was a concatenating shim, while Neiro's HlsMediaSource is first-class;
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
