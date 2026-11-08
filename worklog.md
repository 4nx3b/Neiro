# Worklog

---

Task ID: 1
Agent: Super Z (main agent, session web-60bd54af)

Task: Found the Neiro fork (4nx3b/Neiro, a fresh fork of kushagrasinghx/BitChord at
acf219a7, no owner commits yet) and set it up as the porting target for the
ArchiveTune feature pipeline: repo rules, agent invariants, worklog, dev branch,
and a working CI loop.

Work Log:
- Researched both sides before writing anything: BitChord's architecture (KMP
  modules app/shared/sharedUi/tv/wearos/web/backend; single-Activity + hand-rolled
  AppNavigator; MainViewModel; Media3 1.11 playback with ResolvingDataSource over
  `bitchord://` virtual URIs; the MusicSource/SourceKind/SourceRegistry/
  SourceResolver contract; Downloads/Downloader.probe/ManifestFile/OfflineDash;
  StreamContainer manifest typing + replayAsManifest recovery; the encrypted-prefs
  auth pattern) and the ArchiveTune fork inventory (AGENTS.md invariants: Tidal,
  Deezer, Qobuz, Spotify direct, SoundCloud, Telegram sources; Flamingo player;
  SukiSU glass navbar/About; acoustic audit; predictive back; video preloader;
  Oboe DSP; download extras; UX fix batch).
- Confirmed the feature mapping is real, not assumed: BitChord already plays and
  downloads Tidal-shaped DASH manifests through module sources (media3-exoplayer-
  dash, OfflineDash, ManifestFile all reference Tidal by name), so the native
  Tidal source ports onto existing machinery instead of duplicating it.
- Added rules.md (the owner's standing rules, operational notes adapted to
  Neiro's Check workflow), AGENTS.md (porting rules + fork invariants + module
  map), and this worklog.
- Extended the workflow push triggers from `branches: [main]` to `branches:
  [main, dev]` in check.yml and android.yml: upstream ran CI only on main
  pushes and PRs, so a dev push — this fork's integration branch per Rule 1 —
  built nothing. PRs were already covered and stay as they were.
- Created the `dev` branch from main (acf219a7) as the integration branch.

Stage Summary:
- Neiro is a porting target with standing rules, agent invariants, and a dev
  branch. CI trigger fixed for dev pushes; first port (the Tidal source) starts
  from the research above.

---

Task ID: 2
Agent: Super Z (main agent, session web-60bd54af)

Task: Port feature 1 of the ArchiveTune pipeline — the Tidal source — into
Neiro: account sign-in, search, streaming (AAC direct + FLAC DASH), downloads,
quality tiers, and the sources-screen integration.

Work Log:
- Ported the account layer as `data/tidal/TidalAccountStore.kt`: one encrypted
  session (PKCE with durable refresh, or a 1-hour listen.tidal.com
  Bearer-capture session), token refresh through auth.tidal.com with the same
  client pair ArchiveTune uses, JWT-expiry reading for web-capture sessions,
  and 401 surfaced as UnauthorizedException so the resolver skips the source.
- Ported the API as `data/tidal/TidalService.kt`: PKCE challenge/exchange,
  bearer session validation, subscription lookup, account search, and
  playbackinfopostpaywall with the PREVIEW tier-downgrade ladder
  (HI_RES_LOSSLESS → LOSSLESS → HIGH). The manifest decode follows
  ArchiveTune's split: a JSON envelope answers its direct URL (kbps normalised
  between the kbps/bps conventions), an MPD is sanitized (raw & → &amp;) and
  written to `cacheDir/tidal-manifests/<track>-<tier>.mpd`, handed back as a
  `file://` URL with the representation's codec/sampleRate/bitDepth.
- `data/sources/TidalSource.kt` implements MusicSource: health speaks in
  session terms (Rejected "sign in" when signed out, Ok with
  username/subscription/country when not), search maps rows to
  trackKey-identified Songs with LOSSLESS/HIGH sourceQuality, stream asks at
  the tier the request implies (lossless pick for Lossless; HIGH for
  Best/Capped). `SourceKind.TIDAL` (rank 2, above JioSaavn, canServeLossless,
  worthPrefetching) + registry build/seeding with TidalSource.QUALITY_OPTIONS,
  so the addon quality-picker machinery serves Tidal's tiers unchanged.
- Playback: no new playback code. The resolver's child factory now routes a
  resolved `file://` answer to FileDataSource (ResolvedFileRoutingDataSource in
  PlaybackService, the SmbDataSource routing pattern one level down), and the
  existing DASH machinery does the rest — `.mpd` extension → manifestMimeOf →
  DashMediaSource, with the progressive sniff-failure → replayAsManifest reopen
  for first plays and AudioCache.discard on reopen.
- Downloads: Downloader.probe gained a local-file branch (same sniff on the
  opening bytes), fetchDirect a local copy branch, and ManifestFile.write reads
  a file:// manifest from disk, resolving segment references against the
  manifest's own BaseURL (or absolute refs) — so a Tidal FLAC download
  assembles through the existing ManifestFile FLAC writer, and offline-package
  routes still work.
- UI: SourcesScreen probes Tidal's health (it has no server but its health is
  the session), shows the Waves icon and the quality picker on the row, and a
  Tidal account group (signed-in name / not signed in → sign in, sign out with
  confirmation) raised to MainActivity like every other full-screen overlay;
  TidalLoginScreen is the ArchiveTune PKCE WebView login with the
  listen.tidal.com Bearer-capture fallback, following SpotifyLoginScreen's
  hosting pattern. Strings added (English; locales fall back per upstream's
  untranslated-keys convention).
- Tests (TidalSourceTest): JSON/FLAC-JSON/DASH manifest decodes, MPD
  sanitization and file write, tier selection per request and pick, token
  payload parsing, track-key identity, and an end-to-end local-manifest
  download through MockWebServer asserting segment URL resolution and m4a
  assembly. testImplementation org.json added (the mockable android.jar stubs
  it into no-ops — the same fix Google documents for org.json-based tests).

Stage Summary:
- Tidal source ported end-to-end on BitChord's own machinery; pushed to dev
  for CI. On-device verification (sign-in, FLAC playback, lossless download)
  pending user testing.

---

Task ID: 2a
Agent: Super Z (main agent, session web-60bd54af)

Task: Repair the CI loop — every Check/Android run on dev (three attempts
across half an hour, including a docs-only commit) died before Gradle started:
`Downloading from https://services.gradle.org/distributions/gradle-8.11.1-bin.zip
failed: timeout` on the runner.

Work Log:
- services.gradle.org serves that URL as a 307 redirect to
  github.com/gradle/gradle-distributions/releases/download/v8.11.1/... — the
  same artifact. Pointed the wrapper's distributionUrl at the GitHub URL
  directly so the runner skips the redirect hop that is timing out. Same zip,
  same version; the wrapper stores no checksum to invalidate.

Stage Summary:
- gradle-wrapper.properties now downloads from the Gradle GitHub mirror;
  next push re-runs the Tidal commit's CI.

---

Task ID: 3
Agent: Super Z (main agent, session web-60bd54af)

Task: Close the CI loop on the Tidal port (task 2) and open the expanded
porting scope the owner set: not just sources — every settings-page feature,
the dependencies they need, and the UI design, each adapted to BitChord's
UI principles, one feature per commit.

Work Log:
- Audited the whole un-CI'd Tidal diff symbol by symbol before pushing
  (Rule 1 means the first compile is on the runner): found and fixed four
  real compile errors the previous session left — `random::nextBytes()`
  called as a reference (reserved syntax), `decodeJsonManifest` reading a
  `durationSec` never passed to it, the `Subscription` enum referenced but
  never declared, and `BaseDataSource.Factory()` (no such nested class in
  media3 1.11 — the router implements `DataSource.Factory` directly).
- Fixed two test-compile errors (`$RepresentationID`/`$Number` parsed as
  Kotlin interpolation inside the MPD raw strings; `ManifestFile.write`
  is suspend and the test now runs it inside `runBlocking`) and one test
  failure: `tierFor` answered the seeded list's first entry (Hi-Res) when
  no tier was picked; the documented and tested default is 16-bit LOSSLESS
  — a stale or lossy pick falls back the same way.
- Fixed `SourceRegistry` seeding (`it` inside a lambda whose parameter is
  named `kind` — the last real compile error, found on the runner).
- Built the CI dependency-mirror layer the fork now runs on, because every
  GitHub-hosted download path flaked on the runners this window:
  - `deps` release (https://github.com/4nx3b/Neiro/releases/tag/deps)
    carrying hash-verified mirrors of oboe 1.11.0 (54MB) and libusb 1.0.27;
    CMake's FetchContent tries the mirror before the canonical URL, both
    pinned by the same SHA256, so integrity never depends on the source.
  - Google's Maven Central mirror ahead of `mavenCentral()` in
    settings.gradle.kts (both repository blocks): repo.maven.apache.org
    redirects its largest jars (kotlin-compiler-embeddable among them) to
    GitHub release downloads, which 504 on the runners.
- Locale parity: translated the nine Tidal strings into all 19 catalogues,
  plus the four upstream keys every locale was missing
  (`automix_overview`, the Diwali greeting — upstream translated it into
  Hindi only and has not noticed because their Check runs on PRs, where
  recent branches fail on their own compile errors first); removed the
  duplicate the append created in values-hi. LocaleStringsTest green.
- Release signing: the fork inherits no secrets, and writing an empty jks
  killed `:wearos:packageProdRelease` inside the keystore parser; the
  workflow now writes the keystore files only when the secret exists, and
  the build scripts' own absent-keystore path produces an unsigned release.
- Re-ran the one NDK-install flake (dl.google.com died mid-install on TV;
  the same job passed on the commit before and on the re-run).
- Updated AGENTS.md: the Tidal invariant now names the real files and the
  tier-default rule, and four new invariants document the mirror layer,
  the locale-parity contract, and the signing gate.

Stage Summary:
- dev @ 8c418e57: Check (1271 unit tests + assembleDevDebug), Backend Go,
  Android Release APK (unsigned, full pipeline) all green; TV green on the
  code-touching sha below the head. The Tidal port is CI-verified
  end-to-end; on-device sign-in/FLAC/download verification still pending
  user testing.
- Porting pipeline for the expanded scope, one feature per commit, next
  candidates in research order: (a) Deezer source (ArchiveTune's
  DeezerCrypto + decrypting DataSource map onto SourceKind/MusicSource
  like Tidal did), (b) Qobuz via the kouzu.in mirror, (c) SoundCloud
  (anonymous client_id resolve + HLS-through-scheme pattern), (d) Spotify
  direct streaming behind its experimental gate, (e) settings-page
  features that ride existing machinery (Libre.fm alongside last.fm,
  lyrics romanisation, Android Auto), (f) UI-design ports re-expressed in
  BitChord's components. Dependencies land with the feature that needs
  them, via the mirrored repositories.

---

Task ID: 4
Agent: Super Z (main agent, session web-60bd54af)

Task: Port the expanded scope's first settings-page feature — Libre.fm as a
selectable scrobbling service (ArchiveTune's Last.fm/Libre.fm provider
choice) — adapted to BitChord's UI principles, one feature per commit.

Work Log:
- Researched both sides first: ArchiveTune models it as LastFmProvider
  {LASTFM, LIBREFM, CUSTOM} with per-provider credential overrides and the
  compat pair ("any non-empty key works — libre.fm does not validate
  registrations"); BitChord already ships LIBREFM_API_ENDPOINT, the
  libre.fm auth-URL branch in LastFM.getAuthUrl, a lastfmEndpoint setting
  the playback service reconfigures on, and a mobile-session login alert.
- The provider state rides the endpoint setting itself — one truth, no new
  persistence: AppSettings.setLastfmProvider writes the libre.fm endpoint
  or blank, and the sign-in row, login alert and the ScrobblingSnapshot
  collector all follow it with zero new wiring.
- Credentials: LastFM.credentialsFor (pure, unit-tested) swaps the build's
  Last.fm pair or a blank store for the compat pair on the Libre.fm
  endpoint and never touches a pair the user typed. Wired into
  BitChordApplication.initLastfm, the PlaybackService collector (whose
  shouldEnable now judges the effective pair — Libre.fm scrobbling works
  on builds whose LASTFM_API_KEY is unset), and the login alert.
- UI: a "Scrobbling service" row in Account & integrations (SfIcons.Cloud,
  subtitle naming the active service) opening the standard ChoiceAlert;
  the Last.fm row retitles to Libre.fm when it is active; switching
  services clears the session — the same five writes as signing out,
  because a session belongs to the service it was made on.
- Strings translated into all 19 catalogues; LastFmProviderTest pins the
  credential mapping (default passthrough, custom-endpoint passthrough,
  compat swap for build pair and for blank store, user override kept,
  half-override kept whole).

Stage Summary:
- dev @ 49d6dfdf: all four workflows green (Check, Go, TV, Release).
  Feature 2 of the expanded porting scope is CI-verified; on-device
  Libre.fm sign-in/scrobble verification pending user testing. Next in the
  pipeline per the task-3 roadmap: the Deezer source.

---

Task ID: 5
Agent: Super Z (main agent, session web-60bd54af)

Task: Repair the nightly release pipeline after the signing + arm64-v8a-only
change (commit 12a64bac) left `build-prod-release` red on dev.

Work Log:
- Read the failing run (42494542074) end to end: `:wearos:packageProdRelease`
  and the whole wear build completed, and the only failure was
  `:app:bundleWearApkProdRelease` — "Expected exactly one watch APK in
  wearos/build/outputs/apk/prod/release". Check and TV were green on the same
  sha, so the regression was confined to the release job's new build command.
- Root cause: the command switched to
  `:app:assembleProdRelease -Pandroid.injected.build.abi=arm64-v8a`. That
  AGP property is the IDE-deployment channel and is process-global: it
  reaches the `:wearos` module too, where the module has no abi splits of
  its own, so the injected restriction leaves the watch bundling without its
  single expected APK output. The phone app still wires
  `bundleWearApk<Variant>` into its asset sources (the watch APK rides inside
  the phone APK as the in-app watch-install feature), so the wear build is
  always part of `:app:assembleProdRelease` and anything that disturbs the
  wear module's output discovery breaks the phone build.
- Fix: the ABI restriction now rides a fork-local property instead.
  `app/build.gradle.kts` reads `bitchord.abis` (comma-separated) and, when
  set, narrows `splits.abi` to exactly those ABIs at configuration time and
  turns the universal split off; unset, the split matrix is byte-for-byte the
  old one. The nightly builds with
  `:app:assembleProdRelease -Pbitchord.abis=arm64-v8a`, which produces
  exactly one phone APK (`*-arm64-v8a-release.apk`) and leaves `:wearos`
  untouched. The workflow comment, the AGENTS.md signing invariant and
  wearos/README.md now describe the property and the never-use-injected-abi
  rule; the README also documents that nightlies still embed the watch APK
  (Settings → Wear OS installs it from the phone), only the standalone watch
  upload is gone.
- Verified the local-file trigger lines are intact: the earlier
  `branches: ain, dev]` reading was a display artifact of this session's tool
  transport eating `[m` sequences, not repo content (boolean byte-probe
  confirms `[main, dev]` is on disk; the Edit tool refuses to match the
  "mangled" form). No changes made there.

Stage Summary:
- dev pushed with the pipeline repair; Check/Android/TV monitored for 7
  minutes. The nightly contract after this task: signed arm64-v8a phone APK
  only (artifact `bitchord-prod-release-arm64-v8a-signed`), watch APK embedded
  in-phone but not uploaded, everything else discarded.

---

Task ID: 6
Agent: Super Z (main agent, session web-60bd54af)

Task: Port the Deezer source — task 3's roadmap item (a) — end-to-end onto
BitChord's source machinery: ARL sign-in, search, tiered streams, in-flight
decryption, downloads, and the sources-screen integration.

Work Log:
- Researched ArchiveTune's deezer package first (DeezerCrypto, the 1033-line
  DeezerAudioProvider, DeezerDecryptingDataSource, DeezerInstances) and the
  pool machinery it leans on, then mapped it onto the contracts the Tidal port
  established instead of transplanting them.
- `data/deezer/DeezerCrypto.kt`: the Blowfish/CBC stripe (2048-byte chunks,
  every third starting at zero, key = MD5(trackId) hex XOR the salt), with
  buildUri/parseUri in pure Kotlin — android.net.Uri returns nulls under the
  stubbed JVM test jar, so the URI layer had to be framework-free to be
  testable at all.
- `data/deezer/DeezerService.kt`: the gw-light.php gateway with the arl cookie,
  the 45-minute session (checkForm api_token + license_token + lossless flags),
  public-API search with a gateway fallback, song.getData + media.deezer.com
  get_url with the BF_CBC_STRIPE cipher ladder (FLAC falls to 320 on a
  standard account), the three anonymous resolvers as a signed-in fallback
  (their expiry honoured in the stream cache), ISRC lookup, and the decrypting
  probe/fetch helpers downloads need. Stream/failure caches mirror the
  ArchiveTune TTLs (30 min / 10 min).
- `data/deezer/DeezerAccountStore.kt` + `auth/DeezerLoginScreen.kt`: the
  encrypted arl store and the sign-in surface — a WebView that watches for the
  arl cookie plus the manual-ARL paste field underneath, because Deezer is
  geo-fenced and the cookie never appears in exactly the countries that need
  the escape hatch. Both paths verify through deezer.getUserData before
  anything is stored. Hosted as a root overlay in MainActivity exactly like
  the Tidal login.
- `data/sources/DeezerSource.kt` + `SourceKind.DEEZER` (rank 2 beside Tidal,
  canServeLossless, worthPrefetching) + registry seeding with the
  FLAC/MP3_320/MP3_128 quality options. Signed out, search and stream answer
  nothing and health says why — the anonymous resolvers are a fallback for a
  signed-in session whose get_url failed, not a default-on catalogue.
- Playback: no new playback code. `ResolvedFileRoutingDataSource` grew a
  `deezer` branch routing to `DeezerDecryptingDataSource` over the same chunked
  client, so the resolver's `deezer://stream` answer decrypts under the
  cache — the cache stores PLAIN bytes under the phone key, and seeking,
  read-ahead, quality upgrade and the watch speaker need no Deezer-specific
  code. Downloads: `Downloader.probe`/`fetchDirect` grew deezer branches that
  decrypt (the probe has to — chunk zero is encrypted, so the container sniff
  sees "fLaC" only after decryption).
- Strings: 13 new keys translated into all 19 locale catalogues plus English
  (values-hi and the parallel zh-Hant trio included). LocaleStringsTest
  enforces the parity.
- Tests (DeezerSourceTest): key derivation determinism and track-binding,
  short-salt fallback, chunk schedule, in-place decrypt round trip, URI round
  trip incl. custom salt and rejection cases, user-data/session/search/get_url/
  anonymous-resolver payload decodes, resolver ordering, the tier ladder, the
  request->format mapping, the FLAC stream shape, the decrypting head, and an
  end-to-end decrypting fetch over a striped MockWebServer body.
- Self-review fixes before pushing: a val-shadowing compile error in
  resolveWithAccount's failure path, the CookieManager third-party-cookie call
  binding to the wrong receiver in the login WebView, and unused imports.

Stage Summary:
- Pushed to dev for CI (Check 1271+13 unit tests + assembleDevDebug + Go; TV on
  the code-touching sha; the signed arm64-v8a nightly pipeline). On-device
  sign-in, FLAC playback and download verification pending user testing. Next
  in the roadmap: the Qobuz source.

---

Task ID: 7
Agent: Super Z (main agent, session web-60bd54af)

Task: Close the Deezer port's CI and port the Qobuz source — task 3's roadmap
item (b) — onto the same account-first machinery.

Work Log:
- The Deezer push failed on a real compile error my pre-push review missed:
  the two no-payload gateway calls against a parameter with no default
  (43ec8fa1 gives `payload` its default). Check/TV/Release all failed on that
  one line; pushed the repair and re-ran.
- Researched ArchiveTune's qobuz package: the official API path (token
  backends) and the instance path (self-hosted `/api/get-music` +
  `/api/download-music` mirrors with health probing, cooldowns and a backend
  race). The instance/pool half is its own settings surface in ArchiveTune and
  is left for a follow-up; this port is the account path.
- `data/qobuz/QobuzService.kt`: track/search, track/getFileUrl with the MD5
  request signature over the sorted parameter string (the exact protocol
  string, byte for byte), user/get subscription standing (the lossless/hi-res
  marker regex), the payload parsers (both the tracks.items and flat item
  shapes, preview detection, mime->codec), and a test-only apiBase seam.
- `data/qobuz/QobuzAccountStore.kt` + `auth/QobuzLoginScreen.kt`: the
  encrypted account store and the sign-in — the WebView loads play.qobuz.com,
  a JS hook reads the X-User-Auth-Token/X-App-Id off the player's own
  requests, and a bundle scraper recovers the 32-hex app secret; when
  scraping comes up empty the credentials pair with a manual paste field.
  Every path verifies through user/get before anything is stored.
- `data/sources/QobuzSource.kt` + `SourceKind.QOBUZ` (rank 2) + registry
  seeding with the MAX/Hi-Res/FLAC/MP3-320 quality options. Lossless asks at
  the picked tier defaulting to 16-bit FLAC; Best/Capped ask at MP3 320;
  `sample: true` answers are rejected as previews (the free-account case) and
  failure-cached.
- Strings: 13 keys translated into all 19 catalogues plus English.
- Tests (QobuzSourceTest): the signature MD5 vector, search/file-url payload
  decodes incl. the preview flag, the account-standing markers, the tier
  mapping, and an end-to-end signed getFileUrl over a MockWebServer asserting
  the request shape (path, request_sig, format_id, track_id, both auth
  headers) and the preview rejection.

Stage Summary:
- Deezer repair pushed (43ec8fa1) and monitored; Qobuz follows on green. Next
  in the roadmap: the SoundCloud source, then Spotify direct streaming, then
  the settings-page features that ride existing machinery (lyrics
  romanisation + language packs, Android Auto).

---

Task ID: 8
Agent: Super Z (main agent, session web-60bd54af)

Task: Session close-out — verify the delivered state and hand the roadmap to
the next session.

Work Log:
- Verified the pipeline end to end at HEAD (571c8cf3): Check (1271+26 unit
  tests incl. DeezerSourceTest and QobuzSourceTest, assembleDevDebug), Backend
  Go, Android TV and the signed arm64-v8a nightly all green; the nightly
  artifact is `bitchord-prod-release-arm64-v8a-signed` alone.
- This session delivered: the nightly-signing/arm64 repair (3cf31dc5), the
  Deezer source (b88d6ea0 + repairs 43ec8fa1/39bd590f), and the Qobuz source
  (571c8cf3) — each CI-verified, each with its locale-parity strings and its
  contract tests.

Stage Summary:
- Porting pipeline continues next session in this order (task 3 roadmap,
  updated): (c) the SoundCloud source — client-id scraping from the sndcdn
  bundles, api-v2 search/resolve/transcodings, the AAC_160/AAC_96/MP3_128
  quality ladder, the auth token option, and the HLS-vs-progressive answer
  (check first whether Neiro's manifest sniff already routes a .m3u8 answer
  through its HLS machinery); (d) Spotify direct streaming behind the
  existing SPDC token; then the settings-page features that ride existing
  machinery: lyrics romanisation + the Japanese language pack, Android Auto,
  storage/cache limits per type, backup & restore, the Internet group (DoH,
  proxy, IP rotation), the privacy/behavior group, the Listen-Together
  settings rows, downloads extras (auto-download-on-like, external
  downloader, source order), the developer options + logcat viewer, and the
  appearance details (UI scale, blur intensity, custom font, backgrounds).
  After those: the Qobuz instance/mirror support, the account-pool, and the
  Telegram/AI-integration ports, which each need their own research pass.
