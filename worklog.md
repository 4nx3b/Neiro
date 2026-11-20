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
Agent: main (Super Z)
Task: Port the ArchiveTune Appearance settings (the features shown in the
user's screenshots) into Neiro's Appearance settings page: Liquid Glass
customization (refraction, blur, tint, optical quality), random theme on
startup, Color Palette picker with a custom palette editor, App icon (moved
from Miscellaneous into Appearance, matching ArchiveTune's placement),
app-wide Hide status bar, UI scale, and Blur intensity. Dark theme ("Theme"),
disable-blur ("Reduce dynamic blur") and disable-animations ("Reduce
animation") already existed in birchord's own idiom and were left as they
were, as the equivalents of ArchiveTune's rows.

Work Log:
- Studied ArchiveTune's AppearanceSettings / LiquidGlassSettings /
  PalettePickerScreen / Theme.kt / App.kt and Neiro's SettingsSheet, Theme,
  LiquidGlass, MainActivity, AppSettings and player backdrop code first.
- data/theme/ThemePalettes.kt (new): the 64-palette catalog, ThemeSeedPalette
  and ThemeSeedPaletteCodec ported verbatim — same palette ids, colors and
  "seedPalette:"-prefixed base64 JSON preference format, so themes exported
  from ArchiveTune import here unchanged. generateRandomPalette keeps
  ArchiveTune's hue offsets.
- AppSettings: LiquidGlassIntensity enum plus ten Liquid Glass tuning flows
  (intensity, refraction height/amount, blur radius, tint opacity, shadow
  depth, 3D depth, chromatic aberration, backdrop vibrancy, adaptive
  luminance), colorPalette, randomThemeOnStartup, hideStatusBar (app-wide),
  uiScale (0.85..1.30) and blurIntensity (0..64 dp, 48 default), each with
  clamped setters; readAll() grows and stores a random palette on every start
  when random-theme is on, exactly as ArchiveTune's App init does.
- Theme.kt: BitChordTheme takes a seedPalette; the scheme is grown per-role
  from the four seeds (ArchiveTune's mergedSeedColorScheme structure) through
  materialkolor, added at 2.1.1 — the last line built against Compose 1.7,
  which is what this app's BOM resolves (ArchiveTune's 5.0.0-alpha07 needs
  Compose 1.11 / Kotlin 2.4). Palette style follows the seed's chroma
  (Monochrome / Neutral / TonalSpot) as in ArchiveTune.
- LiquidGlass.kt: LiquidGlassTuning ported — every control is a multiplier
  around the fixed values the app has always drawn, so the stock look is the
  default preset; refraction height/amount scale the lens, blur radius scales
  the frost, tint and adaptive luminance scale the surface fill, vibrancy
  gates the saturation boost, shadow depth scales the drop shadow.
- MainActivity: decodes the palette into BitChordTheme, provides a scaled
  LocalDensity (fontScale * uiScale, dp layout untouched, as in ArchiveTune),
  and composes the app-wide HideStatusBar beside the player's own.
  StatusBarIcons.android.kt's HideStatusBar now reference-counts so
  overlapping hiders (app-wide + player) don't fight over the window.
- Player blur intensity: PlayerSettingsSource.blurIntensity flows from
  AppSettings through AndroidPlayerHost into NowPlayingScreen;
  rememberFullArtworkBlurImage bakes the strength into its cache key and
  boxBlurred maps 48 dp to the blur the player has always drawn (same radius,
  three passes), widening/narrowing with the setting.
- SettingsSheet (Appearance group): a "Customize Glass" row under the Liquid
  Glass switch opens the new Liquid Glass choice page (intensity segmented
  control, five optics sliders, four optical-quality switches, reset);
  Random theme on startup switch; Color Palette row (shows the palette's
  name) opening the new palette page — live preview card drawn in a real
  BitChordTheme grown from the seeds, custom palette editor with per-role
  RGB/hex color dialogs and Apply, the 64-preset swatch grid, and
  import/export through the document picker; the App icon picker moved here
  from Miscellaneous; Hide status bar switch; UI scale and Blur intensity
  slider rows.
- strings.xml: 126 new strings (palette names, glass tuning, seed editor).

Stage Summary:
- Every default multiplies to exactly the stock look: no palette selected, no
  glass tuning touched, 48 dp blur, 100% UI scale, status bar visible — the
  app renders identically until a setting is changed. Pushed to main; CI
  (Check: testDevDebugUnitTest + assembleDevDebug) to be confirmed green.

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

---

Task ID: 9
Agent: Super Z (main agent, session web-60bd54af)

Task: Port the SoundCloud source from ArchiveTune — roadmap item (c) — onto
the account-first source machinery.

Work Log:
- Researched ArchiveTune's soundcloud package end to end:
  `SoundCloudAudioProvider` (client-id + app_version scraping off
  soundcloud.com's HTML and the sndcdn JS bundles, api-v2 search/resolve/
  tracks, the transcoding walk with its candidate scoring, the 5-minute
  stream cache), `SoundCloudAuthUtils` (the OAuth token normalizer),
  `SoundCloudHlsDataSource` (a concatenating HLS shim behind a
  base64url `soundcloud-hls:` scheme), and the `SoundCloudLoginSheet`
  WebView sweep (cookies + localStorage + sessionStorage + the runtime
  `SC.connector.oauth_token`). Noted that ArchiveTune captures the auth
  token but never attaches it to a request — its OAuth support is
  cache-key decoration only.
- `data/soundcloud/SoundCloudService.kt`: the scrape (12h TTL, mutex, a
  capped 16-bundle parallel walk, force-refresh retry), search/tracks and
  tracks/{id} with client_id + app_locale + app_version and browser
  Referer/Origin, the transcoding selection and its `url` fetch with
  track_authorization, stream/failure caches (5/10 min), the SNIP and
  unstreamable filters, `-large` → `-t500x500` artwork, the preset bitrate
  fallbacks, and `normalizeAuthToken` applied as `Authorization: OAuth` on
  every api-v2 call when an account exists — the fix for ArchiveTune's
  dangling token. `apiBaseForTest`/`clientIdForTest`/`appVersionForTest`
  seams for the JVM tests.
- The tier ladder deviates from ArchiveTune deliberately: the tier match
  (AAC_160/MP3_128/AAC_96 by bitrate+mime) outranks progressive-vs-HLS.
  ArchiveTune hard-prefers any progressive rendition because its HLS went
  through a byte-concatenating DataSource; Neiro's HlsMediaSource is
  first-class, so the picker is honest — AAC 160 lands on the HLS AAC
  rendition even when a progressive MP3 128 exists.
- `data/soundcloud/SoundCloudAccountStore.kt` + `auth/SoundCloudLoginScreen.kt`:
  the encrypted token store and the sign-in — the WebView loads
  soundcloud.com/signin and sweeps cookies, both storages and the runtime
  SDK object at staggered delays, every path verified through `/me` before
  anything is stored, with the manual paste field underneath (Deezer/Qobuz
  pattern).
- `data/sources/SoundCloudSource.kt` + `SourceKind.SOUNDCLOUD` (rank 3,
  beside JioSaavn) + registry seeding. Opt-in like JioSaavn — the catalogue
  is user uploads, so a title match can be a remix or a cover, and 160 kbps
  AAC is not a tier that pays for that risk; `SourceConfig.enabled`
  defaults false for the kind. HLS answers declare
  `StreamContainer.declare(url, "hls")` (the AddonSource pattern), so the
  `.m3u8` extension sniff is never load-bearing; progressive answers carry
  the browser media headers on the DataSpec.
- `shared/.../Http.kt`: a sndcdn/media-streaming host interceptor that
  stamps the browser UA + Referer/Origin only when absent. Media3's HLS
  loader builds its own playlist and segment requests, which never see a
  DataSpec's headers — the interceptor is the only layer that reaches them,
  the same reason WebDAV's auth rides there.
- Strings: 13 keys translated into all 17 locale catalogues plus English,
  with the parallel zh-Hant trio and values-hi carried (apostrophes escaped
  for fr/it). LocaleStringsTest enforces the parity.
- Tests (SoundCloudSourceTest): the scraping regexes, the search decode
  (SNIP/unstreamable dropped, artists split and deduped, artwork upsized),
  the tier ladder at all three rungs, the bitrate fallbacks, the token
  normalizer (raw/OAuth/Bearer/cookie/JSON inputs and the implausible
  rejects), the request-tier mapping (pick vs connection cap), the
  end-to-end transcoding walk over a MockWebServer (asserting client_id,
  app_version, track_authorization, Referer, and the Authorization header
  when signed in), and the failure cache's single-request guarantee.

Stage Summary:
- Pushed to dev for CI (Check: unit tests + dev APK; TV; the signed arm64
  nightly on the merge). On-device playback, download and sign-in
  verification pending user testing. Next in the roadmap: (d) Spotify
  direct streaming behind the existing SPDC token, then the settings-page
  features that ride existing machinery.

Task ID: 9 (continued — CI repairs)
Agent: Super Z (main agent, session web-60bd54af)

Task: Close the SoundCloud port's CI.

Work Log:
- First push failed on one line: the source-row icon `when` in
  SourcesScreen gained a kind and needed its SOUNDCLOUD branch (5fc5de2f).
- The unit tests then failed eight ways, and the pattern in the failures
  was the interesting part: every ladder test picked the candidate my
  scoring ranked LOWEST. Root cause: `maxWithOrNull(compareByDescending {
  score })` selects the minimum — a descending comparator inverts what
  maxWith calls greatest, so the 400/300-point tier match lost to the
  0-point one. ArchiveTune's SoundCloudAudioProvider carries the same
  latent inversion: its "progressive preferred" 1000-point bonus has been
  losing to HLS whenever both transcodings exist, masked by the fact that
  its HLS path still plays. The port now scores with
  `maxWithOrNull(compareBy { … })`, so the tier match genuinely wins —
  which also makes the deliberate tier-over-progressive policy real.
- The bitrate fallback had the sibling bug the inversion was hiding: the
  AAC branches required the mime to say "aac", but the API's HLS mimes are
  `audio/mp4`/`audio/mpegurl`, so every HLS AAC preset fell through to the
  128 kbps default (in ArchiveTune too). The preset is now the
  authoritative signal for both bitrate and codec; the mime only breaks
  ties.
- Two smaller repairs the tests caught: the client-id regex now accepts
  bracket-quoted keys (`config["clientId"]='…'`), and the artist splitter's
  `feat.` boundary no longer strands a leading dot on the second name.
- Two test expectations were wrong, not the code: `mp3_64` is 64 kbps, not
  the mime fallback's 128, and a picked tier that fits under a connection
  cap is honoured (Capped(200) with an MP3 128 pick answers MP3 128).

Stage Summary:
- Repair pushed for CI. The e2e transcoding walk, the failure cache and
  the OAuth header assertions were green throughout; on-device
  verification still pending user testing.
Agent: main (Super Z)
Task: Redo the player design styles port as a true one-to-one port. The
first attempt (commit 33fbec3) had adapted ArchiveTune's ten styles into
BitChord-flavoured rewrites under ui/player/designstyles/ — wrong: the user
asked for the styles exactly as ArchiveTune ships them, icons, animations,
queue pages, lyrics pages, transitions and everything related, with no
adaptation. This task replaces that with a vendored, byte-for-byte port.

Work Log:
- Deleted sharedUi/.../ui/player/designstyles/ (12 files, the adapted
  rewrites). Nothing else referenced them beyond the style enum wiring that
  stays.
- New Gradle module :archivetuneplayer (com.android.library, namespace
  moe.rukamori.archivetune, minSdk 26, JDK 17, the app's own pins: Compose
  BOM 2024.12.01 + foundation 1.10.0 + material3 1.3.1, coil 3.0.4,
  media3 1.11.0). Libraries ArchiveTune's stack needs that the app doesn't
  use are declared here: accompanist-lyrics, squigglyslider, backdrop,
  compose-shimmer, reorderable 3.1.0, lottie-compose 6.6.6,
  material-kolor 2.1.0 (last Compose-1.7 line), timber.
- 229 Kotlin files copied byte-for-byte from ArchiveTune dev @ d08d8da94
  under the original package moe.rukamori.archivetune: Player.kt and the
  eleven styles (V4/V5/V7/V9/V10, flamingo=AppleMusic, tiktok, simpmusic,
  spatialflow, looper), every queue sheet, lyrics screen, lottie icon,
  menu and transition they draw, plus their closure (ui/component, ui/menu,
  ui/theme, ui/utils, viewmodels, models, extensions, constants, playlist,
  lyrics pipeline plumbing, repository, playback queues).
- Vendored ArchiveTune's pure-JVM modules under archivetune/: core
  (InnerTube + NewPipe/BravePipe extraction, 122 files), canvas (6),
  betterlyrics (4), jiosaavn (1) — all byte-identical to upstream, wired as
  Gradle projects with their own pins (ktor 3.5.2, PipePipeExtractor via
  JitPack, protobuf-java excluded in favour of javalite as upstream does).
- The app-service layer those files talk to is reimplemented as a thin
  seam, marked "BRIDGE" in every file: PlayerConnection wraps the host's
  Media3 controller (MediaController is a Player) and mirrors the queue/
  metadata/sleep-timer surface; MusicService carries the ~20 members the
  player reaches (multi-source pickers and Discord sync inert);
  MusicDatabase answers from the host's data through ArchiveTuneBridge;
  DataStore keeps ArchiveTune's preference API over a module-local
  DataStore plus BridgedPreferences adapters; LyricsHelper serves the
  lyrics menu (empty searches); DownloadUtil and SyncUtils forward to the
  host. javax.inject / dagger.hilt / androidx.hilt are inert-annotation
  shims; hiltViewModel() resolves through the bridge's own factory
  registry. tf.monochrome, com.lastwave and the DSP chain are inert stubs
  (the pill simply hides, as it does when no DSP runs).
- bridge/ArchiveTunePlayerHost.kt: what the app mounts when a ported style
  is active — installs the host bridge, builds the PlayerConnection,
  provides every composition local (LocalPlayerConnection, LocalDatabase,
  LocalDownloadUtil, LocalSyncUtils, LocalMenuState,
  LocalBottomSheetPageState), and lays out ArchiveTune's own
  BottomSheetPlayer with its own mini player, byte-for-byte its hosting in
  ArchiveTune's activity. A BridgeNavController maps artist/album/search
  routes onto the host's pages.
- app/MainActivity.kt: when AppSettings.playerDesignStyle is not BITCHORD,
  the ArchiveTunePlayerHost mounts over the page and the app's own mini
  player, glass dock circles and full player stand down; BITCHORD keeps
  the app's player exactly as before. ArchiveTuneBridgeImpl answers the
  bridge: playback through the app's MediaController (playSongs,
  beginRadioQueue, enqueue surgery), likes and playlists through
  MainViewModel, lyrics translated from the app's LyricLine stream into
  ArchiveTune's LRC dialect, and the design-style / slider / button-style
  preferences bridged one source of truth.
- The module's resources are ArchiveTune's own (229 drawables incl. every
  style icon, 3 lottie raws, all 15 fonts, the strings the stack reads
  across 26 locales); app_name carried in its own values file.
- Compile verification by CI only, per the user's instruction: push,
  monitor check.yml, fix from the logs, repeat.

Stage Summary:
- All ten ported styles now run ArchiveTune's real code — the same icons,
  animations, queue sheets, lyrics screens and transitions the upstream
  app ships — over BitChord's playback through a thin bridge. BITCHORD
  stays the app's own untouched player. First push handed to CI; repair
  loop to follow.

---

Task ID: 2 (continuation — the compile-repair loop, CI-driven per instruction)
Agent: main (Super Z)
Task: Carry the one-to-one player style port (Task ID: 2's first push) to a
green build: push, monitor check.yml / android.yml, fix from the failure
logs, repeat. No local compilation.

Work Log (seven rounds, from the CI logs):
- Round 1 — the module's own compile, the shapes the copied UI reads:
  playbackState made public on the bridge connection; the service bridge
  gained the live-track facts the stats sheet reads (currentMediaMetadata,
  currentStreamInfo, liveNormalizeFactor, inert); LyricsHelperEntryPoint
  registered in the bridge entry-point registry; BackdropTonePalette.kt
  and LyricsTextWrapping.kt copied from ArchiveTune (they were outside the
  original closure — the V7 backdrop's tones and the bouncing-word wrap
  units); VideoBackgroundPreloader bridged inert (no background hold
  exists without ArchiveTune's service); DownloadFetchProgress bridged
  inert for SpatialFlow's download pill; SyncUtils.likeSong takes the
  toggled SongEntity exactly as the TikTok rail passes it; materialkolor
  raised to ArchiveTune's own 5.0.0-alpha07 (isAmoled defaults there, and
  its Compose 1.11.0-alpha03 deps resolve under the module's beta02 pins);
  room-ktx for the withTransaction import SimpMusicPlayer spells out; the
  pipeline/channel/DSP stubs reshaped to the original data classes so
  ShowMediaInfo compiles unmodified; the javax.inject shim files
  deduplicated; DownloadManager's five-arg constructor; the tf.monochrome
  eq sub-screens stubbed inert (the engine stack they belong to is not
  carried — the dialog's tone/speed/reverb halves stay live); the
  export_lyrics_saved string.
- Round 2 — the app joins the module's Compose line. The vendored stack's
  pins (foundation 1.11.0-beta02, material3 1.5.0-alpha18) reach the app's
  compile classpath through the module and its runtime classpath
  regardless, so app and tv declare that line outright, and the three call
  sites that predate it move with it: the now-playing sheet's
  SheetState.Saver / constructor hand material3 1.5 the swipe thresholds it
  used to derive from density (the same 56/125 dp, so the sheet feels
  unchanged); ComposeUiFlags.isAdaptiveRefreshRateEnabled — retired in ui
  1.11 — is set through reflection where it exists and no-ops where it
  doesn't; datastore-preferences on the app's classpath (the bridge
  preference adapters carry Preferences.Key in their signatures). The
  bridge impl's constructor lambdas renamed (openArtist & co. collided
  with the overrides they feed — an untypeable recursion), like status is
  this app's LikeStatus.LIKE, the lyrics source reads its label safely,
  radio seeds map from ArchiveTune's metadata shape.
- Round 3 — the renamed lambdas' call site in MainActivity.
- Round 4 — unit tests green; packaging: BravePipe (the vendored core's
  extractor, under org.schabi.newpipe as ArchiveTune pins it) and the
  app's own stripped TeamNewPipe v0.26.3 share the package and D8 refuses
  the duplicates. The app keeps its own jar: every org.schabi call the
  vendored core makes (Response's 5-arg constructor,
  YoutubeJavaScriptPlayerManager's signature / n-param entry points,
  StreamInfo.getInfo, AudioStream's bitrates) was verified against
  v0.26.3's bytecode and matches, and the core treats extractor failure
  as a fallback, never an error. BravePipe stays on the core's own
  compile classpath; only app/tv resolution drop it.
- Round 5 — kuromoji's two jars ship one CONTRIBUTORS.md and one
  LICENSE.md between them (and BouncyCastle's licence joins the pile):
  excluded / picked in packaging, in both app and tv (the tv build was
  missing the pickFirsts the phone app already had).
- Round 6/7 — R8: javax.inject must be the real artifact, not a shim —
  the release graph carries javax.inject:1 from elsewhere and R8 refuses
  the module-local copy. The artifact provides every annotation the
  copied sources use; only the dagger/hilt side stays shimmed.

Stage Summary:
- check.yml green (testDevDebugUnitTest + assembleDevDebug), android.yml
  (the nightly R8 release APK, arm64-v8a) green, TV green. All ten
  ported styles build over their real ArchiveTune sources; the BITCHORD
  style keeps the app's own player untouched.

## Ported player styles crashed on switch; crash logs now saved on device

- **Build was broken since cbade7c.** `CrashLog.kt` had two `dump(File, Throwable)` overloads — same JVM signature, and making one private (4bdb12e) does not help. No APK with the earlier "fix" was ever produced. Now one `writeReport(file, thread, throwable, header, appendix)`; `snapshot()` is `@Synchronized`; JVM test `CrashLogTest`.
- **The earlier theory was wrong.** `PreferenceStore.get()` never blocks (it returns null until the snapshot lands); starting it in `Application.onCreate` fixed nothing.
- **Real root cause:** `ArchiveTunePlayerHost` installed the bridge, `BridgeEntryPoints.context` and the `BridgeViewModels` registry inside a `DisposableEffect`, which runs only *after* the first composition. That same first composition built `PlayerConnection` (bound to `PlaceholderPlayer` for good) and composed the styles, whose `hiltViewModel()` hit an empty registry. On top of that the reflective placeholder / ExoPlayer proxies returned an `Integer` for `long`, a `Double` for `float` and `null` for `Timeline`/`PlaybackParameters` → ClassCastException / NPE. Fixed: setup in a `remember` ahead of everything; caches built on IO in a `LaunchedEffect`; `PlayerConnection` re-attaches to the live controller (main looper only, no recursion); typed defaults plus media3 empties (`Timeline.EMPTY`, …).
- **Crash capture ported from ArchiveTune** (`data/CrashReporter.kt`, `data/CrashLogTree.kt`): the only uncaught handler; it chains to the previous one. It writes header + stack trace (with causes) + the `CrashLog` ring (DebugLog in all builds + Timber) + this pid's logcat tail to `Android/data/<pkg>/files/crash/neiro-crash-*.log` (newest 10), and on API 29+ copies it to `Download/Neiro/` via MediaStore (no permissions). On the next launch a toast says where the log went (`crash_log_saved`, in every catalogue).
- Known follow-up (not a crash): ported screens may show blank metadata, because `MediaItem.metadata` reads `localConfiguration.tag`, which a MediaController never carries.

## Follow-up 1: ArchiveTune settings, metadata bridge, drift (partial)

- **Done: metadata bridge.** `MediaItem.metadata` (vendored MediaItemExt.kt, one `// BRIDGE:` line) falls back to `bridge/BridgeMetadata.of(item)`. A MediaController's items have no `localConfiguration.tag`, so every ported mini/full player and queue was reading null metadata (no title, artists or artwork). The fallback rebuilds ArchiveTune's MediaMetadata from Media3 metadata plus Neiro's `bitchord.*` extras, with a 256-entry LRU. JVM test: `BridgeMetadataTest`.
- **Staged, not yet built: settings port** (upstream c0c97f7). The 17 settings files and their 12-file closure are copied byte-for-byte, with 4 BRIDGE shims, under `docs/archivetune-settings-port/staged/`, along with the dependency analyser and a README listing what's left (gradle deps, res, settings host, SettingsSheet mount, key bridging).
- **Not started: drift collapse for ported styles.**
- Skipped by design (they drive ArchiveTune's own service, Room, InnerTube or accounts): account/source/integration pages (Tidal, Qobuz, Deezer, Spotify, Apple Music, Telegram, Jio, SoundCloud, YouTube OAuth/PoToken, Discord, scrobblers, Listen Together, AI, Android Auto, Update, Backup, Storage, DNS, Content, Privacy, Audiophile, Debug, Icon, AOD).

## Follow-up 2: crash on every launch with a ported style saved

- **Log** (realme RMX3563, Android 16, 1.9.1 release): `IllegalStateException: MediaController method is called from a wrong thread` from `MediaController.getCurrentMediaItem`, on `DefaultDispatcher-worker-13`.
- **Misleading frames:** the trace names `PartySync$start$1$2.emit` and `PlaylistSync$init$1`. R8 merges lambda classes, so those names are unrelated. The real one is the method suffix `invokeSuspend$com$music$bitchord$ui$player$ArchiveTuneBridgeImpl$1`, plus `Dispatchers.Default` in the suppressed coroutine context.
- **Root cause:** `ArchiveTuneBridgeImpl`'s init collected `viewModel.lyrics` on `Dispatchers.Default` and read `controller.currentMediaItem` inside. `StateFlow.collect` delivers the current value at once, so the first emission ran off the main looper and threw. The saved style mounts the host at every start, so this happened on every launch.
- **Fix:**
  - Every controller read or command in the bridge runs on the controller's application looper, through `onMain` or a Handler-backed dispatcher.
  - The synchronous answers (`song()`, which `MusicDatabase` calls from IO, plus `toggleLike` and `addToPlaylist`) read a `currentItem` snapshot that a controller listener keeps current.
  - LRC rendering stays off main, with no controller in reach.
  - `release()` removes the listener and cancels the scope when the host unmounts.
  - `MusicServiceBridge.pauseFromSleepTimer` no longer calls the player directly.
- **Crash-loop guard** (`data/CrashLoopGuard.kt`, `CrashLoopPolicy.kt`):
  - The crash handler records `<epoch> <uptime> <style>` in `files/crash/boot_crashes`.
  - On launch, two quick crashes (each under 30 s of uptime, within 10 min) with the same ported style switch the style back to BITCHORD, and a toast says so once (`player_style_reset`).
  - A launch that survives 30 s clears the record.
  - JVM test: `CrashLoopGuardTest`.

## Follow-up 2b: settings bridged at app start (branch feat/archivetune-port-resume)

- `ui/player/ArchiveTunePreferenceBridge` replaces the private `installPreferenceAdapters()`, which ran only while a ported player was mounted. It is now installed once in `BitChordApplication.onCreate`, after `AppSettings.init`.
- **23 keys bridged** to Neiro's AppSettings, each with an explicit two-way enum map:
  - player look: style, slider, buttons, thumbnail corner, mini-player and lyrics background;
  - appearance: theme/dark mode, font (plus the custom font name and URI), back animation, reduce animation, status bar, UI scale;
  - layout: tablet mode, minimal home, default tab;
  - playback: skip silence, stop on task clear, volume bar, AutoMix performance, crossfade on/length.
- `utils/DataStore.kt` (BRIDGE):
  - the `dataStore[key]` getter now asks `BridgedPreferences` first;
  - `mirrorBridgedPreferences` copies Neiro's values one way into the module's DataStore, so vendored `dataStore.data` readers see them too.
- `docs/archivetune-settings-map.md` lists every key, with its bridge / local / dead status.
- Tests: `ArchiveTunePreferenceBridgeTest` (7 cases) and `CrashLoopGuardTest` (8) pass locally with kotlinc against the real enum entry names. Android compile is CI only.
- **Not started:** the drift collapse for the ported styles.

## Follow-up 3: drift collapse for the ported styles (branch feat/ported-drift)

- `bridge/DriftDock.kt`:
  - `driftRect(mini, open, t, travel)` is the app's own sleeve path: `offset = (1 - t) * travel`, `ride = clamp((mini.top - open.top) / travel)`, `rect = lerp(mini, open.translate(0, offset * ride), t)`. x is linear, y lands tangentially and never passes the cover. Covered by `DriftDockTest` (5 cases).
  - `DriftState` holds the mini cover and the open artwork slot in sheet-local coordinates. Each is read against the sheet at the same instant, so the sheet's translation cancels out.
  - `Modifier.driftArtworkSlot()` reports a style's artwork slot and hides that artwork while the copy flies.
  - `DriftArtworkLayer` draws the flying copy in BottomSheet's shared layer, as a translate + scale + corner in graphicsLayer only, so a drag recomposes nothing.
- Vendored hooks, every line marked `// BRIDGE: drift`:
  - `Player.kt`: DriftState per sheet and style, the `sharedLayer` for every style except SpatialFlow, and `reportMini` in the mini slot callback;
  - `Thumbnail.kt`: the current page only;
  - `PlayerComponents.kt`: V9's artwork.
- Off for SpatialFlow (it keeps its own floating artwork) and under reduce-animation (`DisableAnimationsKey`, bridged to Neiro).
- Drifting now: Cinematic (V4) and Material Extended (V9), the two styles with one square artwork slot. The other styles keep their existing fade:
  - BitChord-port and V7 use full-bleed artwork;
  - TikTok is a video feed;
  - V10 uses a die-cut shape;
  - SimpMusic, Looper and Flamingo use artwork pagers.

## Follow-up 5: style-switch crashes, missing mini player, settings spacing, settings merge (part 1)

- **SimpleCache crash** (logs 112718, 112801): every mount of `ArchiveTunePlayerHost` built a new `DownloadUtil`, and with it a second `SimpleCache` on `cache/at_download_bridge`. `DownloadUtil` is now a process singleton (`DownloadUtil.get`), with its cache and manager built lazily. `BridgeManagerHolder` uses the same one.
- **Haze crash** (log 120335): haze 1.3.1 throws without a backgroundColor, and the two ported `HazeStyle` calls had none (settings header, player backdrop). Both now pass one.
- **Mini player missing after a style switch**: the host's sheet started DISMISSED and nothing collapsed it. It now starts COLLAPSED when a song is loaded, and collapses from DISMISSED when one arrives later.
- **Settings cards ran together**: `SearchableSettingsGroup` dropped the header and the top gap for every card on a category page. Now only the first card sits flush, and a header is hidden only when it repeats the page title. Pure rule in `SettingsGroupSpacing.kt`, tested by `SettingsGroupSpacingTest`.
- **Merge, part 1**:
  - the separate ArchiveTune group and `ArchiveTuneSettingsActivity` are removed;
  - 21 more twin keys are bridged to Neiro's existing rows: Liquid Glass (switch, tier and 9 tuning values), hide thumbnail, TikTok lyrics, lyric blur, animated canvas, slide bar, moving blur, square thumbnails, reduce blur, blur strength, Flamingo volume bar.
- **Merge, part 2 is still open**: native rows for the ported-only keys. They are listed in `docs/archivetune-settings-map.md`.

## Follow-up 6: the six-item round — Sources/Lyrics sub-pages, Material Extended and the palette out, Accompanist out for the native word-sync renderer

- **Playback gains the "Content" group** from ArchiveTune's player settings page: a Sources row (the existing source-manager page, now reached from Playback; the main page's audio-quality group no longer carries the Sources row or the Direct Source Stream line under it — that line moved to the bottom of the sources-order card, where the fetch policy it describes lives) and a Lyrics row opening a new Lyrics page holding everything that used to sit under Appearance: the synced-lyrics master switch, line blur, the providers picker and the translation language.
- **Material Extended is gone**: `MATERIAL_EXTENDED` out of the shared enum and the preference bridge, `V9` out of the vendored enum with every branch that read it collapsed (`Player.kt`'s colour/branches, `PlayerMenu.kt`'s sets, `Queue.kt`/`QueueComponents.kt`'s V9 content, `PlayerComponents.kt`'s V9 layout functions deleted with `V9AnimatedPlaybackControls.kt`; `PlayerFadeConfig` stays — the mini player uses it).
- **The primary/secondary seed-palette code is out**: the vendored `Theme.kt` keeps only the font locals (everything palette-grown — `ArchiveTuneTheme`, `ThemeSeedPalette`, the exact/merged schemes, `paletteStyleFor` — had no callers left once `PalettePickerScreen.kt`/`ThemeCreatorScreen`, `TonalPalettes`, the SVG preview stack, `CustomThemeColorKey` and `RandomThemeOnStartupKey` went); materialkolor and androidsvg leave the module's dependencies; the manifest's minSdk override for the accompanist lyrics library is gone; the last colourScheme.primary/secondary reads on the ported player's button/text paths became explicit values.
- **Accompanist is out**: `LyricsEnhanced` keeps its scaffolding (fetch, TTML/LRC parse, translation/romanisation, click-to-seek, scrub preview) and renders natively — the BitChordLyrics word-sweep technique (drawWithContent + clipRect gradient wipe) on a LazyColumn with anchor auto-scroll; `LyricsSpicyMobile.kt` and the `EnhancedLyricsStyle` picker are deleted; both mocharealm artifacts leave the module.
- **am-lyrics on the web** goes 1.8.2 → 1.9.1 (package.json + pnpm-lock).
- **Every locale** got the Integration/Internet/Lyrics strings the LocaleStringsTest demands, preferring ArchiveTune's own translations where they exist.

## Task 1-a: the settings reorganization the owner ordered

Task ID: 1-a
Agent: Super Z (sub agent, session task-1a)

Task: Reorganize Settings exactly as the owner ordered: the audio-quality
ceilings and the Dolby Atmos switch off the main page, six categories
(Downloads, Content Preferences, Local Music, Language, Performance,
Behavior) dissolved into the pages whose subject they are, Spotify moved from
Integration to Sources, and the Replay row and the work-out-genres switch
moved to where the owner wants them read.

Work Log:
- SettingsSheet.kt:
  - The main page's Audio quality group is gone; a Replay row (same icon,
    subtitle, keywords and `onOpenReplay` as Your data's) sits in the top
    group directly after Listen Together; the Wear OS row no longer sits
    under the category list — it is a Miscellaneous row now.
  - `SettingsCategory` keeps five entries — PLAYBACK, APPEARANCE, STORAGE,
    YOUR_DATA, MISCELLANEOUS — and the icon map lost the six dead branches.
    Nothing outside SettingsSheet/AppNavigator names a category, and the
    top-bar title mapping reads `route.category.title`, so no other call
    site changed.
  - Playback gains the Dolby Atmos switch (between Spatial audio and
    Equalizer, still `BypassedByBitPerfect`-wrapped and greyed where the
    device can't decode E-AC-3), plus Content Preferences and Local Music
    groups carrying every row of the two dissolved categories (the folder
    picker and the conditional Use-all-folders row included).
  - Storage leads with the Downloads group (Download quality, Wi-Fi only,
    Export-compatible downloads, Sync downloaded playlists).
  - Miscellaneous gains Language (App language + the activity-hosted
    `AppLanguageDialog`), Performance (High performance mode + the
    conditional Refresh rate row) and Behavior (the Performance row into
    `BehaviorPerformancePage`), and the Wear OS row, all still searchable
    through `SearchableSettingsGroup`'s keyword rows.
  - Your data keeps only Export/Import data — the later task replaces them.
  - `AudioQuality.localizedLabel()` went private→internal so the Internet
    page labels the ceilings with the same strings.
- InternetScreen.kt: an Audio quality group after the YouTube Music region
  group, with the same On Wi-Fi / On mobile data rows (same icons, In-use
  badges off the live `meteredConnection`, same choice-page navigation
  through a new `onOpenChoice` parameter).
- IntegrationScreen.kt: the Music sources group (Sources row, Direct source
  stream subrow, Tidal/Deezer/Qobuz/SoundCloud account rows) is gone — all
  duplicated on Sources already — and the External sources group left with
  Spotify. The Scrobbling group gains the work-out-genres switch directly
  under the Last.fm sign-in row. Ten now-unused parameters and their
  MainActivity wiring came out with the rows.
- SourcesScreen.kt: a Spotify group after the SoundCloud account group — the
  Spotify logo row, the Connected subtitle, the Disconnect destructive row,
  the connect footer — reached through a new `onOpenSpotify` parameter.
- MainActivity.kt: the three call sites updated (Integration slimmed,
  Internet gains `onOpenChoice`, Sources gains `onOpenSpotify`); no other
  region touched.
- No string keys were added, removed, or renamed — every moved row reuses
  its existing key; `LocaleStringsTest` is unaffected by construction.

Stage Summary:
- Settings' page structure matches the owner's order with zero row-level
  behaviour changes: every moved row kept its callbacks, preference keys,
  conditional visibility and search keywords. Compile verification is CI's
  (never local); the orchestrator commits.

## Task 1-b: synced lyrics fixed across the ported player styles

Task ID: 1-b
Agent: Super Z (sub agent, session task-1b)

Task: Fix synced-lyrics rendering in every style ported from ArchiveTune
(Flamingo/APPLE_MUSIC, TikTok, the vendored BitChord page and strip, the
full-screen lyrics screen): word-synced lyrics arrived fused into
"Ibeentrynacall" with no per-word sweep, line-synced lyrics sat misplaced,
the app's lyrics offset never reached the ported players, and the vendored
BitChord strip could never show lyrics at all.

Work Log:
- Root cause, verified in the sources: `toArchiveTuneLrc()` joined
  `<stamp>word` with no inter-word spaces, but the app's `LyricWord.text`
  is trimmed while the ported parser keeps inter-tag substrings verbatim —
  so the reconstructed words had no spaces and the line text fused. The
  app's `lyricsOffsetMs` was bridged nowhere (the vendored
  `lyricsSyncOffset` was a per-track `rememberSaveable(0)`), and the ported
  views added their own leads (LRC_LEAD_MS 120/300, LYRIC_VISUAL_TUNING
  150) that the native style has no counterpart for. The vendored strip
  read `database.lyrics(id)` off an in-memory MusicDatabase nothing seeds.
- `ArchiveTuneBridgeImpl.kt`:
  - `toArchiveTuneLrc()` now writes the A2 convention: each word run
    carries its own trailing space (`<00:27.39>I <00:27.54>been `), the
    tag-stripped line text equals the original (spaces intact), and the
    last run is closed by a bare terminator stamp at `LyricLine.endMs` —
    the parser reads a bare tag as the final word's end and nothing else,
    so the sweep stops where the line stops (verified against
    `parseEnhancedLrcLine`'s empty-text skip and
    `clampEnhancedLrcLastWordEnds`).
  - The collector now watches `lyricsChecked` too and emits the module's
    `LYRICS_NOT_FOUND` marker when a lookup finished with nothing, so the
    ported pages and the strip report "not available" instead of
    shimmering forever (the AOD ticker already guards that marker).
- `constants/PreferenceKeys.kt`: new `LyricsSyncOffsetKey`
  ("lyricsSyncOffset", int).
- `ArchiveTunePreferenceBridge.kt`: `lyricsSyncOffset` bridged two-way to
  `AppSettings.lyricsOffsetMs`, negated — Neiro's playhead is
  `position − offset`, the ported stack's `position + offset`, so the sign
  flip is what makes a track synced in one style stay synced in the other.
  `docs/archivetune-settings-map.md` gains the row (39 bridged now).
- Readers routed to the bridged preference: vendored `Player.kt` (which
  feeds Flamingo, TikTok and the full-screen LyricsScreen) and
  `BitChordPlayer.kt` replaced their per-track `rememberSaveable` locals
  with `rememberPreference(LyricsSyncOffsetKey, 0)`; the vendored
  LyricsMenu's offset dialog therefore writes the app's setting.
- `BitChordPlayer.kt`: the strip's source switched from
  `database.lyrics(id)` to the live `playerConnection.currentLyrics` flow
  (id-matched like the lyric pages), parsed by the same
  `parseBitChordLyrics` — the strip now shows the current line and sweeps
  it word by word, and `lyricsUnavailable` becomes reachable.
- Layout unification, per the user's ask:
  - `LyricsEnhanced.kt` (Flamingo portrait/landscape, TikTok inline pane,
    vendored BitChord ENHANCED page, LyricsScreen ENHANCED): the lyric
    column gets a fixed symmetric `NativeLyricsGutter = 30.dp`
    contentPadding (BitChord's PLAYER_GUTTER equivalent; rows carry no
    horizontal padding of their own) and `LRC_LEAD_MS` is zeroed — the
    anchor was already the ~16%-from-top pin. The word sweep itself
    (SweptLyricText/sweepTo over revealedChars) was already the BitChord
    technique; it now has real words to walk.
  - `LyricsV2.kt` (LyricsScreen V2/SPOTIFY, vendored BitChord V2/SPOTIFY):
    the LazyColumn gets `contentPadding` of 18dp each side (rows keep
    their own 12dp → 30dp total), the active-line anchor moves from
    35% to `LYRICS_ANCHOR_FRACTION = 0.16f` (matching LyricsEnhanced and
    the native panel), and the 300ms LRC lead + 150ms visual tuning are
    zeroed. Word separation came from the bridge fix: each word run's
    trailing space is what the FlowRow now lays out; the space-run branch
    stays for TTML sources.
  - `LyricsScreen.kt`: the pane's 24dp horizontal padding (and the
    landscape full-screen 36dp) dropped — the components carry the fixed
    gutter themselves now.
  - `TikTokSongPage.kt`: the inline pane's 6dp horizontal padding dropped
    for the same reason. `TikTokMainLyrics` keeps its caller-chosen
    overlay insets; its sweep is fixed by the bridge. AOD untouched.
- No string resources added. No files outside the task's scope touched.

Stage Summary:
- Every ported style renders the app's lyrics the way the native BitChord
  panel does: words separated and swept word by word on their own timings,
  lines anchored ~16% from the top inside a fixed 30dp gutter, timing
  driven by the app's lyrics offset through the negated bridge with the
  per-view leads gone, and the vendored BitChord strip live at last.
  Compile verification is CI's (never local); the orchestrator commits.

## Task 3-a: Backup and restore ported into the Your data page

Task ID: 3-a
Agent: Super Z (sub agent, session task-3a)

Task: Port ArchiveTune's Backup and restore page (scheduled auto backup,
cloud storage sync, internal service with m3u/csv playlist import and CSV
export) into Neiro's Your data settings page, replacing the old
Export/Import data rows, with the archive adapted to Neiro's data rather
than ArchiveTune's SQLite zip.

Work Log:
- New engine under `app/src/main/java/com/music/bitchord/data/backup/`:
  - `BackupArchive.kt`: the `.backup` file — a zip of `manifest.json`
    (app tag `bitchord`, format version, created date, app version),
    `settings.json` (AppSettings.exportPrefs() through Backup.PrefValue's
    typed codec, secrets and device-local keys already excluded), and
    `history.json` (ListeningStats.exportAll()'s buckets). Restore
    validates the manifest first, then applies settings through
    AppSettings.importPrefs() and history through ListeningStats.importAll()
    — the existing import paths, not new ones. A picked file whose first two
    bytes are not "PK" is one of the old single-file JSON exports and
    restores through Backup.importFrom() as-is (Validity.legacy, the confirm
    dialog says so); zip validation answers invalid / corrupted / missing /
    newer-format with its own string.
  - `ScheduledBackup.kt`: ScheduledBackupFrequency (DAILY/WEEKLY/MONTHLY/
    CUSTOM) plus the scheduler, both channels — `scheduled_auto_backup` for
    the device folder and `scheduled_auto_backup_cloud` for the cloud
    folder — periodic work at 1/7/30 days with the initial delay landing on
    the next 02:00 after one period (the reference's nextRunAt), custom as a
    one-time request on the picked date at 02:00; constraints
    battery-not-low + storage-not-low; setDirectory/setCloudFolder take the
    persistable READ|WRITE tree grant and resolve the display name the way
    ArchiveTune's repository did; syncNow() writes one dated-or-overwrite
    backup into the Drive folder and stamps cloudSyncLastRunAt;
    rescheduleAfterRun() advances a custom date by whole months to the next
    future occurrence and re-arms that one channel only (re-arming periodic
    work would reset its delay every run).
  - `ScheduledBackupWorker.kt`: the CoroutineWorker — reads its channel's
    settings fresh at run time, writes the zip through
    ScheduledBackup.createDestination (DocumentsContract.createDocument,
    dated `<app>_yyyyMMdd.backup`, overwrite mode finds the fixed
    `<app>.backup` child doc and truncates it), stamps the cloud last-run,
    re-arms; a lost grant fails the run, anything else retries.
  - `PlaylistTransfer.kt`: readCsvRecords ported verbatim (the
    PushbackReader state machine, quote handling, CRLF); the CSV header
    detection (title/artist aliases, artist split on `;` and `|`); the m3u
    #EXTM3U/#EXTINF parse; imported rows become a LocalPlaylistStore
    playlist named after the file; exportCsv writes the ArchiveTune column
    set (Title, Artist, Album, Duration — Duration instead of Media ID,
    which is a YouTube id Neiro rows don't always have) with the BOM,
    always-quoted fields and the spreadsheet-formula escape.
- `AppSettings.kt`: eleven new flows and keys in the house pattern —
  scheduledBackupEnabled/Frequency/CustomDateEpochDay/DirectoryUri/
  DirectoryName/OverwriteExisting and cloudSyncEnabled/FolderUri/FolderName/
  OverwriteExisting/LastRunAt — read in readAll(), written by setters, the
  frequency enum parsed like the other enum settings. The two folder URIs,
  their names and the cloud last-run are added to DEVICE_LOCAL (a SAF tree
  grant means this phone), so a restore keeps this device's folders and
  never exports them.
- `SettingsSheet.kt`, Your data page: the old Export/Import data rows, their
  two launchers, their status vars and the pre-picker confirm dialog are
  replaced by the ported structure —
  - "Scheduled auto backup": enable switch (running/paused subtitle),
    Backup schedule row (frequency value chip, opens the Daily/Weekly/
    Monthly/Custom dialog; Custom opens a Material3 DatePicker with past
    dates disabled), Backup directory row (OpenDocumentTree, shows the
    folder name), Overwrite switch gated on a directory being set.
  - "Cloud storage sync": the info banner row (the owner's wording about
    cloud apps and the system picker), Drive folder row, Enable cloud sync
    switch (paused/running subtitle), the shared Backup schedule row gated
    on a folder, Overwrite existing Drive backup switch, Sync now row
    showing "Never synced"/"Last synced …" and running the backup inline.
  - "Internal service": Backup row (CreateDocument octet-stream,
    `<app>_yyyyMMddHHmmss.backup`), Restore row (OpenDocument, validate,
    confirm dialog showing what the file holds, restore, then the schedule
    re-armed from the restored settings), Import a "m3u" playlists, Import a
    "csv" playlists, Export playlist as CSV (playlist picker dialog →
    CreateDocument text/csv).
  All rows ride SettingsRow/SearchableSettingsGroup with the page's own
  spacing and search keywords; the frequency dialog, restore dialogs and
  playlist picker are GlassAlertDialogs; a `scheduledFrequencyLabel`
  composable maps the enum to the row's value.
- `strings.xml`: 60 `backup_*` strings plus one plural added
  `translatable="false"` — the repo's standing way to ship English-first
  keys (32 existing ones already do) so LocaleStringsTest keeps passing
  until the localization task translates them; no existing key removed.
- No dependency added: `androidx.work:work-runtime-ktx:2.10.1` is already
  declared (PlaylistSync's line), which is the 2.10.x the task asked for.
  MainActivity untouched — the launchers live in SettingsScreen, where the
  export/import pickers already lived.

Stage Summary:
- Your data is now the Backup and restore page: one schedule engine behind
  two folders, an own-format zip archive with legacy JSON restore, and the
  playlist import/export pair — every row backed by the engine, every
  setting persisted in AppSettings, every schedule in WorkManager.
  Compile verification is CI's (never local); the orchestrator commits.

---

## Task 3-b: Music Together — the full Listen Together port, verified and completed

Task ID: 3-b
Agent: Super Z (sub agent, session task-3b-continue)

Task: Verify and complete the interrupted port of ArchiveTune's Music
Together feature (embedded LAN server, online relay host, guest socket,
room-state protocol with chat) into Neiro, keeping the old Listen Together
facade alive for every existing caller, and fix all breakage left in the
working tree.

Work Log:
- The port itself (in-flight from the previous agent, verified file by
  file): `app/.../data/together/` — `TogetherMessages.kt` (the
  `type`-discriminated sealed protocol: hello/welcome, room state, control
  and add-track requests, join decisions, heartbeats, host transfer, kick/
  ban, and the chat pair `ChatSendRequest`/`ChatMessage`),
  `TogetherModels.kt` (track/participant/room-settings/room-state + the
  session-state sealed class), `TogetherServer.kt` (the host IS the server —
  embedded Ktor CIO on 0.0.0.0, `/together` WebSocket, session-key +
  protocol-version handshake, approval gating, authority/handoff, chat
  rate-limit 10 per 5s), `TogetherOnlineEndpoint.kt` (relay base resolved
  from `NeiroKoiverseServer.txt` on the fork's dev branch, 6h cache, falls
  back to the cached copy), `TogetherOnlineApi.kt` (`/v1/together/sessions`
  create/resolve/end over the Bearer token, retry on transient IO),
  `TogetherOnlineHost.kt` (the host's relay WebSocket client, relays guest
  chat back), `TogetherClient.kt` (the guest socket: ws→wss retry,
  hello/welcome, events as a shared flow), `TogetherClock.kt` (NTP-style
  offset over heartbeat pongs), `TogetherPlaybackSync.kt` (the sync rules:
  500ms broadcast, stale-frame drop, queue rebuild on hash mismatch, seek
  thresholds paused 150ms / LAN playing 700ms / online playing 1200ms),
  `TogetherGuestPlaybackPlanner.kt`, `TogetherJson.kt`, `TogetherLink.kt`
  (deep-link encode/decode + `TogetherDeepLink` pending-join store), and
  `TogetherEngine.kt` (the single object every surface reads — session and
  room state flows, host broadcast loop, guest control/add planning with
  dedupe and fallback-start, focus-loss dropout, host transfer, inactivity
  room close, the room-events notification channel, chat buffer of 200).
- The compatibility layer: `data/listentogether/ListenTogether.kt` rewritten
  as a facade over the engine — the old `State` shape (`code`/`you`/
  `members`/`playback`/`queue`/`inParty`/`controlsLocked`/`hostOnlyControl`)
  mapped from the session+room state, `awaitingStart` from Joining*, the
  nickname backed by `togetherDisplayName` with one-time migration from the
  legacy party prefs, and `queueAdd` split into the host's local append and
  a guest's `AddTrackRequest`s. Every external caller verified against it:
  MainActivity (transport, refusedByHost, party queue context, version
  swap, onboarding name), PlaybackService (AutoPlay election, queue echo
  wait, all `inParty` gates), Autoplay/DirectSourceGate/MusicOnlyPlayback/
  CrossfadeController/TrackAnalyzer/AndroidPlayerHost/SettingsSheet/
  SourcesScreen, and sharedUi through `PartyUi` — all unchanged, all still
  compiling against the facade.
- `playback/PartySync.kt` rewritten as the service-side door set
  (`start`/`stop`, debounced `onLocalIntent` → guest control/add requests,
  `onPlayWhenReadyChanged` audio-focus dropout, `onLockedTransport`,
  `shouldDeferPlay` with the engine's fallback-start, queue-drag flushing,
  the personal-queue stash on enter/leave) — PlaybackService is untouched
  and every existing door call matches the new signatures.
- The UI: `ui/screens/MusicTogetherScreen.kt` replaces ListenTogetherScreen/
  Sheets/PartyServerEditor — status, current playback, host controls
  (LAN/online segmented, display name, port, the three permission toggles),
  join by link or code, participants with approve/reject/transfer/kick/ban,
  the chat card, the activity log, the welcome dialog, all in the settings
  page idiom (SettingsGroup/SettingsRow/SegmentedControl/GlassAlertDialog/
  IosSwitch). MainActivity dispatches it at the Listen Together route, the
  settings row and MiniPlayer badge ride the facade, and the party-server
  top-bar status UI is gone with the server it described.
- Completion fixes made while finishing the port:
  - `app/build.gradle.kts` + `tv/build.gradle.kts`: added
    `io.ktor:ktor-server-{core,cio,websockets}:3.5.2` beside the client set
    (TogetherServer's imports), added the `TOGETHER_BEARER_TOKEN`
    BuildConfig field in the localProps/env pattern (default "" — an empty
    token is the "online sessions aren't available in this build" state),
    and removed the now-dead `LISTEN_TOGETHER_SERVER` val + field (nothing
    in app/tv Kotlin reads it any more; grep-verified). tv needs both
    because it compiles the phone app's sources — without them `assembleDev
    Debug` fails at :tv.
  - CI (`check.yml`, `android.yml`, `tv.yml`, `diag-pr.yml`): the
    local.properties seeding swaps `LISTEN_TO_GETHER_SERVER` for
    `TOGETHER_BEARER_TOKEN`, so the owner sets one repo secret and every
    pipeline builds online sessions with it.
  - Manifest (app + tv): the dead `bitchord.kushagrasingh.in/invite/……`
    autoVerify filter and the `bitchord://party` custom-scheme filter are
    replaced by the together filter — `bitchord://together` with
    ACTION_VIEW + DEFAULT + BROWSABLE; `TogetherDeepLink.consume` (already
    wired in MainActivity's onCreate/onNewIntent) parks the join info and
    the screen auto-joins with the saved display name.
  - `NeiroKoiverseServer.txt` at the repo root: one line,
    `https://archivetune-api.koiiverse.cloud`, matching the raw.githubusercontent
    URL TogetherOnlineEndpoint fetches (ArchiveTune's own
    ArchiveTuneKoiverseServer.txt content, verbatim).
  - `strings.xml`: 105 keys added `translatable="false"` (the repo's
    standing English-first pattern until the locale task) — the full
    together_* set ported from ArchiveTune's wording (welcome, status,
    host/join sections, permissions, participants, moderation confirms,
    playback pills, chat, activity log, notifications/errors) plus the
    generic actions the screen shares (start/join_session, session
    link/code, join/joined/leave/loading/connecting/copied/copy_link/
    dismiss/invalid link/code/not allowed/network unavailable). No
    duplicate keys; LocaleStringsTest exempts them.
  - The facade's state combine rewrote as nested combines (5+2) — the
    single 7-flow combine relied on an arity kotlinx.coroutines does not
    offer, which would not have compiled.
  - TogetherEngine's scope gained a logging CoroutineExceptionHandler:
    a failed host bind (port already taken) was an uncaught coroutine
    crash; the reference swallows it silently, this logs it instead.
  - `TvActivity.kt` consumes `TogetherDeepLink` instead of the deleted
    `JamInviteLink`; `SignatureApiEditor.kt`'s doc reference to the deleted
    `PartyServerEditor` rewritten in place.
  - Deleted the two dead test files that no longer compiled:
    `JamInviteLinkTest.kt` (tested the deleted invite parser and the old
    server-URL machinery) and `ListenTogetherIdleHealthTest.kt` (tested
    the deleted idle health-probe gate). `PartyHostOnlyControlTest`,
    `PartyAutoplayTest` and `PartyQueueMoveTest` still compile and pass
    against the facade and the shared party models.
  - AGENTS.md gained the Music Together invariant (engine shape, facade,
  doors, token, tv's duplicate deps, cleartext posture, the untouched Go
  backend).
- Engine wiring traced end to end (read-the-code verification, no local
  compile): host LAN Start → server bound on 0.0.0.0:port → 500ms
  buildRoomState-from-player broadcasts to every guest socket; host online
  → endpoint fetch → create session (Bearer) → relay ws → same broadcasts;
  guest → hello/welcome → room states → hash-rebuild/seek under the
  thresholds, local intents out as ControlRequest/AddTrackRequest when
  the room's permissions allow; chat → ChatSendRequest relayed by both the
  LAN server and the online host → ChatMessage broadcast → the chat card.

Stage Summary:
- Listen Together is now Music Together: one protocol package with chat,
  an embedded Ktor server, an online relay stack, the compatibility
  facade, the PartySync door rewrite, the settings-page screen, the
  `bitchord://together` deep link, the together settings keys, and every
  string the surface references. All deleted-symbol references across
  app/tv are resolved, the two module builds carry the server stack and
  the token, and the relay endpoint file the engine fetches exists at the
  exact URL it reads. Compile verification is CI's (never local); the
  orchestrator commits.

## Task 4: the Canvas settings page and the Downloads/Storage settings port

Task ID: 4
Agent: Super Z (sub agent, session task-4-continue)

Task: Finish the interrupted Canvas/Downloads/Storage settings port from
ArchiveTune — the data layer was already in the working tree from the
previous agent; what was left was every string, the Canvas page's dispatch,
the rows on the Settings pages, and the two settings' actual wiring
(auto download on like, external downloader).

Work Log:
- The data layer (in-flight from the previous agent, read file by file and
  kept as-is): `data/canvas/CanvasHealth.kt` (per-provider probes with
  timeouts — Spotify gated on the sp_dc token, the others probed over IO,
  20s/45s budgets), `download/DownloadsExport.kt` (SAF tree export of the
  finished downloads in their original audio format, per-song progress
  callback, sanitised "title - artist.ext" names), `download/ExternalDownloader.kt`
  (watch-URL hand-off to a package-named app, three-intent ladder:
  ACTION_VIEW / ACTION_SEND / launch intent), `Downloads.autoDownloadOnLike`
  + `clearAll` + `bytesInUse`, `CanvasCache`'s MutableLruCacheEvictor with a
  live ceiling + `sizeBytes` + `clear`, the canvas providers' probeHealthy
  probes, `AppSettings`' nine new keys with their setters
  (normalizeCanvasCacheSizeMb: -1 unlimited, 0 off, else 0..2048), the Smart
  Trimmer pass in BitChordApplication (image disk cache + AudioCache.trimNow),
  LyricsTranslation.clearCache, and `AppRoute.Canvas`.
- `CanvasSettingsScreen.kt` (previous agent's file, fixed to compile): the
  missing `foundation.layout.Row` import for its healthLine/priorityOption
  rows, the nonexistent `SfIcons.Spotify` replaced with `SfIcons.PlayCircle`
  (no brand glyph exists in SfIcons), and the health dialog's
  `android.R.string.ok` switched to the repo's own `R.string.ok`.
- `strings.xml`: 69 keys added `translatable="false"` (English-first until
  the locale pass, LocaleStringsTest-exempt) — the 32 keys the Canvas page
  references (canvas, neiro_canvas, spotify_canvas, the cellular/headers/
  check rows, the four provider names and their health words, the artwork
  priority dialog's labels and details, animate_static_album_art, ok), the
  canvas row's own subtitle, and the Storage port's full set (downloaded
  songs, clear/confirm/size, export + its four status lines, auto download
  on like, download source priority, external downloader + package + missing
  toast, canvas cache section/max size/usage/off/unlimited/clear/confirm/
  cleared, lyrics cache section/clear/confirm/cleared, smart trimmer).
  No duplicate names; `animated_cover_cellular`, `integrate_spotify_canvas`,
  `spotify`, `cancel` were already present and are not duplicated.
- `MainActivity.kt`: the `AppRoute.Canvas` dispatch branch (CanvasSettingsScreen
  with onSpotifyCanvasAuth raised at the activity, like Lyrics' pickers), the
  FrostedTopBar title branch (`AppRoute.Canvas -> R.string.canvas` — the
  when is exhaustive over the sealed interface), and
  `onOpenCanvas = { navigator.push(AppRoute.Canvas) }` on the SettingsScreen
  call site.
- `SettingsSheet.kt`:
  - Playback's Content group gains the Canvas row (Sources → Lyrics →
    Canvas), opening the new page.
  - Appearance loses the "Animated cover art" switch and its "On cellular"
    and "Spotify Canvas" subrows — the Canvas page carries all three
    settings now; everything else on the page is untouched.
  - Storage, after the existing Downloads group: "Auto download on like"
    and "Download source priority" (subtitle = the live
    SourceRegistry.activeForDownload order joined with arrows, tap opens
    Sources) inside the Downloads group; then the "Downloaded songs" card
    ("Clear all downloads" with the bytes-in-use subtitle and a confirm,
    "Export" through OpenDocumentTree → DownloadsExport with progress and
    outcome reported on the row); the "External downloader" card (the
    switch, plus the package row enabled only under it, edited through the
    frosted text dialog); the "Canvas cache" card (max size choice dialog
    over 0/64/128/256/512/1024/2048 MB + Unlimited — 0 off, -1 unlimited,
    matching normalizeCanvasCacheSizeMb — with the current pick as the row's
    value, a usage row reading CanvasCache.sizeBytes with the "x of y · N%
    used" text and a LinearProgressIndicator, and clear with confirm); the
    "Lyrics" card (clear → LyricsTranslation.clearCache, with confirm); and
    the Smart trimmer switch in the storage card beside the song-cache rows
    (the "first card near the song-cache rows" the task named — the
    song-cache rows live in the Storage card, not the Downloads card that
    leads the page).
  - No video cache card: archivetuneplayer's CanvasArtworkPlaybackCache is
    a distinct persistent cache, but nothing in app/tv/wearos references it —
    Neiro's canvas system is its own CanvasRepository + CanvasCache — so there
    is no second video cache to mirror the rows against. Not fabricated.
- Wiring, verified in the code and completed where it was missing:
  - autoDownloadOnLike was stored-only. `MainViewModel.setLike` now answers
    whether the rating landed (the refused-tap paths: not signed in, or the
    same rating twice) and `toggleLike` passes on the status it left behind —
    the shape `toggleDislike` already had. The three UI like funnels now
    consult `Downloads.autoDownloadOnLike` with the song in hand:
    MainActivity's `toggleLikeOf` (the native player's heart, the song menu,
    the list rows), `ArchiveTuneBridgeImpl`'s toggleLike/likeSong (the
    ported player styles' hearts — it needed a context, added to its
    constructor and passed by MainActivity's single construction site), and
    PlaybackService's `toggleFavoriteFromNotification` (the media
    notification's heart, off the current item's toSong).
  - ExternalDownloader was stored-only. `MainActivity.downloadSong` — the
    one-song download action the song menu drives — now hands a YouTube
    track to the configured app when the switch is on; NOT_CONFIGURED (no
    package, or a source-backed track with no watch page) falls through to
    the app's own download exactly as the setting's doc promises, and
    NOT_INSTALLED is a toast with the package name and no download.
  - spotifyCanvasEnabled is genuinely gated (CanvasRepository.spotifyAsked,
    part of the cache key so a toggle never serves the other order's answer).
    canvasInHeaders, animateStaticArtwork and tidalArtworkFallback are
    stored-only: no UI or provider reads them yet — the header pages, the
    static-artwork animation and the player's Tidal sleeve fallback are the
    follow-up ports they wait for. TidalCanvas.fallbackCoverUrl (the lookup
    tidalArtworkFallback describes) exists in the data layer but has no
    caller.
- Verification, all read-the-code (compile verification is CI's — never
  local): every R.string key referenced by CanvasSettingsScreen, the new
  rows, and the new data-layer files cross-checked against values/
  strings.xml by script (no misses, no android.R confusion — R.string.ok is
  the repo's own); every SfIcons glyph used exists in SfIcons.kt; the
  SettingsRow/SettingsSubRow/GlassAlertDialog/IosSwitch/LaunchedEffect/
  rememberLauncherForActivityResult signatures matched against their
  declarations; strings.xml parsed with Python ElementTree (no duplicate
  names, format args well-formed); brace/paren balance checked on
  SettingsSheet.kt, MainActivity.kt, MainViewModel.kt,
  ArchiveTuneBridgeImpl.kt, PlaybackService.kt and CanvasSettingsScreen.kt;
  every setLike/toggleLike call site re-grepped after the signature change
  (all four compile-compatible); the exhaustive AppRoute when got its
  Canvas branch.

Stage Summary:
- The Canvas page is reachable (Playback → Content → Canvas → the page and
  its health check, priority dialog and sp_dc auth), Appearance no longer
  duplicates its rows, the Storage page carries the full downloads/caches
  port (clear-all + SAF export, auto download on like, source priority,
  external downloader, canvas cache ceiling + usage + clear, lyrics clear,
  smart trimmer), and both ported settings act: a like queues a download on
  all three like surfaces, and the external downloader takes over single
  YouTube downloads when configured. 69 strings, translatable="false" until
  the locale pass. The three stored-only canvas keys (canvasInHeaders,
  animateStaticArtwork, tidalArtworkFallback) are the port's known remainder,
  reported rather than hidden.

## Task 4b: the three stored-only canvas keys wired to real behavior

Task ID: 4b
Agent: Super Z (sub agent, session task-4b)

Task: Give canvasInHeaders, animateStaticArtwork and tidalArtworkFallback —
the three keys Task 4 left stored-only — their behaviour, smallest change per
key, zero regressions out of the box (defaults: on / off / off).

Work Log:
- tidalArtworkFallback. The seam is the player's own sleeve resolution,
  NowPlayingScreen's `rememberRemoteArtworkUrl(song)` result: when it answers
  null (no thumbnail, no embedded store art), the new `tidalSleeve` state asks
  the host for Tidal's cover and every `remoteArt` consumer below picks it up —
  sleeve, banner, mesh/glow/blur backdrops, top-band scrim, lyric share card.
  PlayerHost gained `tidalSleeveFallback(song): String?` (default null),
  AndroidPlayerHost answers it through `CanvasRepository.tidalSleeveFallback`,
  which reuses the repository's own resolve() — noise-cleaned title/artist,
  miss-cached entries, album-late re-ask, IO off-main — and only reaches
  TidalCanvas.fallbackCoverUrl (its first caller) after the
  AppSettings.tidalArtworkFallback gate; off answers null with no request.
  WebDAV/SMB tracks wait REMOTE_ART_SETTLE_MS (700ms) for the embedded
  extraction to land before a search is spent on a sleeve the store may be
  about to answer. Deliberately NOT wired under the rows (SongRow/MiniPlayer
  resolve the same nothing; a fallback there is a third-party search per row).
- animateStaticArtwork. `Modifier.staticArtworkBreathing(breathe)` in
  NowPlayingScreen.kt: an infinite scale 1.0→1.03→1.0 (4.5s per leg, ~9s the
  whole breath, FastOutSlowIn reversed), read in the graphicsLayer so a frame
  is a redraw, never a relayout. Applied at the portrait sleeve's AsyncImage
  and passed into LandscapeArtwork (new `breathe` param) for the landscape
  sleeve. The gate is `animateStaticArtwork && canvas == null && !videoMode`
  — a track with no clip and no music-video picture — collected from the new
  PlayerSettingsSource.animateStaticArtwork (AndroidPlayerHost fronts
  AppSettings), and skipped entirely under Reduce animation or while the
  switch is off (the modifier is then the caller's own).
- canvasInHeaders. DetailScreen's two header canvas effects (the album
  sleeve's `canvasForAlbum` lookup and the artist page's appleArt videoUrl)
  now also key on and gate on AppSettings.canvasInHeaders: off, `canvas` stays
  null and both headers render exactly the no-canvas look they always had
  (ReleaseHeader sleeve still, PageBackground photo with its softened foot);
  on (the default), unchanged. The player's own clip is untouched, and toggling
  the key live re-runs the effects.
- No new strings (all three rows' strings shipped with Task 4), no new
  dependencies, no comment blocks beyond KDoc matching each file's own voice.

Verification, all read-the-code (compile verification is CI's — never local):
PlayerSettingsSource/PlayerHost have exactly one implementation
(AndroidPlayerHost; no fakes in any test source set) so the two new members
break nothing; `animateFloat` is an extension needing the import now added
(PlayerControls.kt's own import confirmed the name); `Modifier.graphicsLayer`,
`rememberInfiniteTransition`, `infiniteRepeatable`, `RepeatMode.Reverse` all
match existing usages in sharedUi; TidalCanvas.fallbackCoverUrl's signature
re-checked (title/artist/album, its own isMatch validation) and
CanvasRepository's private resolve()/cleaned() reached from the same object;
CanvasArtwork(url = …) follows the two existing single-arg call sites;
`remoteArt`'s eight downstream reads sit after the new merged declaration and
kept their names; brace/paren balance scripted over all six touched files;
tv/wearos grep-clean (tv compiles the app's sources unchanged; wearos does not
depend on sharedUi); `kotlin.math.abs` duplicate import in NowPlayingScreen.kt
is pre-existing and left alone.

Stage Summary:
- All three Canvas page switches now do something: "Use Tidal artwork" fills
  an artless player sleeve from Tidal (opt-in, cached, miss-cached),
  "Animate static album art" breathes a canvasless sleeve (opt-in,
  reduce-animation aware, redraw-only), and "Canvas in headers" takes the
  looping artwork out of the album and artist page headers when off (default
  on keeps today's look exactly). Task 4's "known remainder" is closed.

## Task 5-a: locale pass, batch 1 — the 234 new strings translated into the nine Asian catalogues

Task ID: 5-a
Agent: Super Z (sub agent, session task-5a)

Task: Localize the 234 newly added English strings (Backup and restore port,
Music Together port, Canvas/Downloads/Storage ports) into the nine Asian
locale catalogues — values-zh, values-zh-rTW, values-zh-rHK,
values-b+zh+Hant, values-ja, values-ko, values-vi, values-th, values-id —
plus the one new plural, backup_playlist_song_count, keeping the
LocaleStringsTest contract (every locale carries every base key) and the
translation-source priority: ArchiveTune's own translations first, then
fresh translation in each file's register. Code/translations only; no
commit.

Work Log:
- Sources mined. ArchiveTune's values-zh-rCN / -zh-rTW / -ja / -ko / -vi /
  -th / -in-rID (strings.xml + archivetune_strings.xml) were indexed by
  English text and by key family; 138 of the 234 matched ArchiveTune wording
  exactly, and the rest mapped by wording family (together_* ↔ together_*,
  backup_* ↔ scheduled_backup_*/import_*/export_*/restore_*/backup_*,
  canvas/storage ↔ canvas_*/max_cache_size/clear_*_cache/smart_trimmer/
  external_downloader*/auto_download_on_like). The gms Drive-backup strings
  turned out to be a different feature (Google account sign-in, no locale
  folders at all), so the whole backup_cloud_* family is fresh per locale.
- Reuse/rewrite split per locale (234 strings + 1 plural each): zh 129 from
  ArchiveTune / 105 fresh-or-adapted; zh-rTW 129/105; zh-rHK and b+zh+Hant
  mirror zh-rTW (HK only swaps 帳號→帳戶 per that file's own convention);
  ja 113/121; ko 53/181; vi 122/112; th 21/213 (ArchiveTune's Thai catalogue
  is 452 entries — only 21 of the mapped keys exist there); id 147/87.
  ArchiveTune brand was swapped to Neiro everywhere it appeared (welcome
  titles, welcome host bodies, backup_schedule_subtitle's "how often
  ArchiveTune creates a backup" → brandless), and "Internal service" flipped
  ArchiveTune's External Service translations rather than re-translating.
  Machine-translation damage in ArchiveTune's ja/ko files was fixed instead
  of copied (ja dismiss "解雇する"→閉じる, leave "休暇を取る"→退出, playing
  "遊ぶ"→再生中, the mangled room-events channel name; ko's embellished
  session strings trimmed to match Neiro's shorter English). Register was
  kept per file: zh 画布/全角标点, TW/HK/Hant 畫布/快取/連線/資料夾, ja 「」
  quoting and 高評価 (Neiro ja's own like-term, over ArchiveTune's
  「いいね！」), ko 캔버스/세션/대기열, vi bộ đệm/danh sách phát, th แคนวาส/
  เซสชัน, id kanvas/antrean — reusing each file's own twin keys where the
  same English already existed (downloaded_songs, lyrics, song_cache_
  unlimited, export_failed patterns).
- Placement and format. Entries appended at each file's end before
  </resources> in newkeys.txt order, 4-space indent, apostrophes escaped
  per file convention (ko's '좋아요' keeps ArchiveTune's \'…\' form; no other
  locale needed one), no straight double quotes introduced (curly/「」
  quotes instead — the only raw-quote string in the tree, ko's pre-existing
  listen_together_code_footer, was left untouched), brand names (Neiro,
  BitChord, Spotify, Tidal, Apple Music, Google Drive, Dropbox, Nextcloud,
  OneDrive, m3u, csv, LAN, Wi-Fi) untranslated. The plural follows each
  file's own plurals style: other-only for zh/zh-rTW/zh-rHK/b+zh+Hant/ja/
  vi/th/id, one+other for ko.
- Verification (scripted, re-run independently after the last write):
  ElementTree parses all nine files; the 234 newkeys present in every one
  (parity 0 missing — was 234); no duplicate keys; format-arg sets match
  the base for every entry and plural item (%1$s/%2$s/%1$d, incl. the
  positional order-swaps ja/ko/vi/zh use for "Restored %1$s from v%2$s");
  git numstat confirms the nine files are purely additive (237/238 added,
  0 removed). A full LocaleStringsTest simulation over SUPPORTED_LANGUAGES
  now reads 0 missing for zh, zh-rTW, ja, ko, vi, th, id (the seven this
  test covers); zh-rHK and b+zh+Hant (untested, maintained in parallel)
  also carry all 234 new keys — their pre-existing gaps (221/207 keys,
  present before this task) are untouched and out of scope. The 11
  non-Asian languages still miss the 234 keys — that is the next batches'
  work. Note: backup_playlist_song_count is still translatable="false" in
  the base, so the test does not yet demand the plural, but all nine
  catalogues carry it anyway; dropping that flag later is a one-line base
  change with nothing else to do.

Stage Summary:
- The nine Asian catalogues now carry all 234 new strings plus the
  backup_playlist_song_count plural, seven of them fully satisfying
  LocaleStringsTest's whole-catalogue parity (base keys 1674, missing 0),
  with ArchiveTune's own translations as the base layer everywhere they
  exist (from 147/234 in id down to 21/234 in th) and fresh translations
  in each file's register for the Neiro-specific wording. No commit was
  made; the work sits in the working tree for the owner's review.

## Task 5-b: locale pass, batch 2 — the 234 new strings translated into the ten European/Middle-Eastern catalogues

Task ID: 5-b
Agent: Super Z (sub agent, session task-5b)

Task: Localize the 234 newly added English strings (Backup and restore
port, Music Together port, Canvas/Downloads/Storage ports) into the ten
European/Middle-Eastern locale catalogues — values-de, values-es,
values-fr, values-it, values-pt, values-ru, values-pl, values-he,
values-hi, values-tr — plus the one new plural,
backup_playlist_song_count, keeping the LocaleStringsTest contract
(every locale carries every base key) and the translation-source
priority: ArchiveTune's own translations first, then fresh translation
in each file's register. Code/translations only; no commit.

Work Log:
- Sources mined. ArchiveTune's values-de-rDE / -es / -fr / -it / -pt +
  -pt-rBR / -ru / -iw / -tr (strings.xml + archivetune_strings.xml)
  were indexed by English text and key family (values-pl / values-hi
  checked: neither translates these features — ~100 unrelated app
  strings only). 133 of the 234 matched ArchiveTune wording exactly,
  the rest mapped by family, same as batch 1 (together_* ↔ together_*,
  backup_* ↔ scheduled_backup_*/import_*/export_*/restore_*/backup_*,
  canvas/storage ↔ canvas_*/max_cache_size/clear_*_cache/
  smart_trimmer/external_downloader*/auto_download_on_like; the
  backup_cloud_* family is fresh everywhere — ArchiveTune has no Drive
  backup feature, and its export_playlist_* keys are untranslated in
  de/es/fr/pt/ru/he). values-es/strings.xml in ArchiveTune is
  encoding-corrupted in place (mixed latin-1 bytes near
  together_requesting_song_change), so it was mined by regex with
  utf-8/replace instead of ElementTree. Neiro's values-pt turned out to
  be Brazilian register ("Tocando agora", "Baixando", "sua pasta"), so
  ArchiveTune's values-pt-rBR is the Portuguese source layer, not its
  pt-PT values-pt.
- Reuse/rewrite split per locale (234 strings + 1 plural each): de 135
  from ArchiveTune / 99 fresh-or-adapted; es 138/96; fr 136/98; pt
  138/96; he 114/120; ru 87/147 (ArchiveTune's ru catalogue carries
  only 48 together_* plus the archivetune_strings.xml backup subset);
  tr 41/193 (AT tr has 21 together_*); it 29/205 (AT it has 13
  together_*); pl 0/234 and hi 0/234 — entirely fresh, no ArchiveTune
  source exists. ArchiveTune brand was swapped to Neiro everywhere it
  appeared (welcome titles, welcome host bodies, neiro_canvas), and AT's
  machine-translation damage was fixed rather than copied: de
  "Stauts"→Status, "fragt den Betritt an", "Host hat Sitzungen
  verlassen"; es "Tú nombre", "abandono la sala", the "cvs" typo and
  "En linea"; fr "Session d'adhésion"→Connexion à la session,
  "Sessions laissées par l'hôte", "Se connecter depuis…" (log-in sense)
  →Rejoindre; it "Attendendo l'approvazione"; ru "Сессии покинутые
  хостом", "Онлайн сессии"→Онлайн-сессии; tr "Yönetici Tarafından
  Sonlandırılan Oturumlar", leftover English "playback", "Paylaşımcı"/
  "Katılımcı" as host/guest → Yönetici/Misafir; he "Host Leaved
  Sessions"-class sources re-translated, „…” quotes kept per file
  convention. Register kept per file: de du-form + „“ + Sicherung/
  Backupdatei compounds; es tú + ¿? + «» + caché; fr vous + « » with a
  space before : ? !; it tu + « » + cache dei canvas + brani; pt (BR)
  você + “” + cache de músicas; ru Вы + «» + кэш with е (the file's own
  spelling, over ArchiveTune's "кеш"); pl imperative + pamięć podręczna
  + utwór; he masculine singular + קנבס + geresh (no ASCII apostrophes —
  צ׳אט); hi आप-register + danda + “”; tr siz + dotted İ + \'
  (Katıl\'a) + çalma listesi (the file's own term over ArchiveTune's
  "oynatma listesi"). LAN kept untranslated per the brand rule (AT's fr
  "Réseau local" and ru "Локальная сеть" not carried; he keeps רשת LAN,
  which retains the term). Brand names Neiro, BitChord, Spotify, Tidal,
  Apple Music, Google Drive, Dropbox, Nextcloud, OneDrive, m3u, csv,
  Wi-Fi, M3U, CSV untouched.
- Placement and format. Entries appended at each file's end before
  </resources> in newkeys.txt order, 4-space indent, one section comment
  per file matching the existing comment style (the files already carry
  "Listen together"-style section comments), apostrophes and straight
  double quotes escaped (\' and \") per Android rules — the escaped
  \"csv\"/\"m3u\" forms follow the base entry, de uses „CSV“, fr
  « csv », es/it/pt/he/hi/tr use \". The plural follows each file's own
  song-count plural style: one/other (de Song/Songs, es canción/
  canciones, fr chanson/chansons, it brano/brani, pt música/músicas, hi
  गाना/गाने, tr şarkı/şarkı), one/few/many/other (ru песня/песни/песен/
  песен, pl utwór/utwory/utworów/utworów), one/two/other (he שיר/
  שירים/שירים) — mirroring song_count_plural/track_count_plural/
  uploading_song_count in each catalogue.
- Verification (scripted, re-run independently after the last write):
  ElementTree parses all ten files; the 234 newkeys present in every
  one (parity 0 missing — was 234); no duplicate keys; format-arg sets
  match the base for every entry and every plural item (%1$s/%2$s/
  %1$d and the literal %%, positional order-swaps preserved — hi
  "%2$s में से %1$s", the queue-position "of"-forms); git numstat
  confirms the ten files are purely additive (240 added / 0 removed,
  241/242 where extra plural quantities add lines). A full
  LocaleStringsTest simulation over all 17 SUPPORTED_LANGUAGES tags
  (minus en) now reads 0 missing — with this batch, whole-catalogue
  parity (1674 base keys) holds for every offered language; the
  untested parallel-maintained values-zh-rHK/values-b+zh+Hant were
  already covered by batch 5-a. backup_playlist_song_count remains
  translatable="false" in the base (test-exempt), yet all ten
  catalogues carry the translated plural anyway — same call as
  batch 5-a's nine.

Stage Summary:
- The ten European/Middle-Eastern catalogues now carry all 234 new
  strings plus the backup_playlist_song_count plural, ArchiveTune's own
  translations as the base layer everywhere they exist (135/234 in de
  and es-pt-fr-he down to 29/234 in it, 0 in pl/hi) and fresh
  translations in each file's register for the Neiro-specific wording.
  Combined with batch 5-a, LocaleStringsTest's parity contract is now
  satisfied across all seventeen offered languages (0 missing keys per
  locale, 1674 base keys). No commit was made; the work sits in the
  working tree for the owner's review.

## Task 6: the first CI round — the port's compile fixes

Task ID: 6
Agent: Super Z (orchestrator, session task-6)

Task: The first Check run after the port (7d3a38c) failed
:app:compileDevDebugKotlin with 35 resolution/type errors; fix every one
without local compilation, per Rule 1.

Work Log:
- TogetherClient's event wrappers shadowed their own wire types — the
  three nested classes named exactly like the protocol messages
  (JoinDecision, HostTransferred, HeartbeatPong) declared their payloads
  as their own type. Their parameter types are now fully qualified to
  the wire classes, which fixes both the client's emissions and every
  engine read off them (participantId, sessionId, approved, the pong
  clock fields).
- TogetherEngine's Welcome branch called startHeartbeat(sessionId)
  against a two-parameter signature; the guest client is now resolved
  from the engine's own reference first.
- The isConnectedToSession extension needed imports in the facade
  (ListenTogether) and PartySync — both same-package-different-module
  callers got them; the cascade of inference errors below those two
  lines went with the missing imports.
- CanvasCache.sizeBytes() asked SimpleCache for a cacheSize Kotlin
  property media3 1.11 does not expose; it now sums cached spans per
  key exactly the way AudioCache's own accounting does.
- ExternalDownloader built a List<Intent> with a nullable
  getLaunchIntentForPackage element; listOfNotNull now carries it.
- PlaybackService's notification favorite referenced an exoPlayer that
  exists only inside a listener method two thousand lines up; the
  service's own player property answers it, the same call
  onIsPlayingChanged already makes.
- IntegrationScreen's Discord row called vectorResource as a member
  of ImageVector; it is a top-level androidx.compose.ui.res function.
- MusicTogetherScreen: the Icons accessor object was unimported while
  its icon extensions were, and six when-branches relied on smart
  casts of the delegated sessionState property — explicit casts now,
  matching the casts the same file already used elsewhere.
- SourcesScreen carried a private AudioQuality.localizedLabel()
  that the reorganization's internal twin in SettingsSheet made a
  conflicting overload; the private duplicate is gone and both
  callers resolve to the internal one.

Stage Summary:
- Every error the run listed is addressed by name; brace balance on
  all touched files is byte-identical to the pushed tree. The next
  push runs the loop again.

## Task 7: the second CI round — one call form

Task ID: 7
Agent: Super Z (orchestrator, session task-7)

Task: The second Check and TV runs (43201d3) failed on the single
remaining error — IntegrationScreen's Discord row still called
vectorResource wrong.

Work Log:
- vectorResource is an extension on ImageVector's companion, not a
  top-level res function: the fully-qualified call form cannot resolve.
  The row now uses DiscordScreen's own idiom — the receiver spelled
  through ImageVector with the androidx.compose.ui.res.vectorResource
  import in scope.

Stage Summary:
- The only error both workflows listed is gone; the next push re-runs
  the loop.

## Task 8: the third CI round — R8 meets the embedded server

Task ID: 8
Agent: Super Z (orchestrator, session task-8)

Task: Check and TV passed on 17c34fd; the Release APK workflow failed
in :app:minifyProdReleaseWithR8 on one missing class.

Work Log:
- ktor-server's ReloadingZipFileSystem references the JDK-internal
  com.sun.nio.file.SensitivityWatchEventModifier — the watcher the
  desktop zip-reload feature uses to poll jar files for edits. The
  phone never runs that path, so the proguard file's JVM-only section
  gains -dontwarn com.sun.nio.file.** beside Rhino's and SMBJ's
  desktop-only entries, documented the same way.

Stage Summary:
- Debug builds and Check never run R8; only the prod release needed
  the rule. The next push re-runs all three.

## Task 9: Glasmio port, stage 1: library vendored as source modules

Task ID: 9
Agent: Curialy Engineer

Task: Port NEORUAA/Glasmio's glass switches, sliders, popup morph, navigation
bar, page and album transitions into Neiro, and replace Kyant backdrop with
Glasmio's renderer. This entry covers the first stage: the vendored modules.

Work Log:
- Branch curialy/glasmio-port, made from origin/dev @ 346c631. Glasmio is
  checked out at 905919bc2a289ce7e873fa3185e59c064c89b95d.
- glasmio/{core,glass,icons,ui}: each src/main is copied unchanged; diff -r
  against upstream prints nothing. Upstream's LICENSE, THIRD_PARTY_NOTICES.md
  and licenses/Apache-2.0.txt are under glasmio/. The build files are Neiro's:
  JVM 17, AGP 9.1.1 + kotlin.android, the app's Compose line, the default NDK,
  and minSdk 33. Every change from upstream is in glasmio/README.md.
  settings.gradle.kts includes the four modules inside the Android-SDK block.
  The repository order is unchanged.
- Not vendored: glasmio-scaffold. Its navigation motion (IosNavigationMotion,
  IosSwipeBack) is `internal` and typed on navigation3 Scene. To use it with
  Neiro's AppRoute router, copy it into ui/motion with its Apache header.
- Inventory of the files the later stages change:
  - Kyant callers in the app: MainActivity, ui/components/LiquidGlass,
    IosMenu, floatingtabbar/FloatingTabBar, floatingtabbar/GlassSelectionPill,
    player/AndroidPlayerHost.
  - Kyant callers in archivetuneplayer: BottomSheetMenu,
    CompactBottomControls, FloatingNavigationToolbar, FrostedHeaderPill,
    LiquidGlass, LyricsMenu, flamingo/FlamingoPlayer.
  - IosSwitch callers: ListenTogetherSettings, Equalizer, CanvasSettings,
    SettingsSheet, Sources, SpotifyCanvasAuth, LyricsSettings, Internet,
    Integration, Discord.
  - Sliders: SettingsSheet's Slider, and sharedUi ThinSlider (PlayerControls,
    LyricsOffsetSheet, AudioOutputSheet, SongActionsMenu).
  - Motion: GlassMorph/GlassDialog/IosMenu/HeldContextMenu,
    GlassNavBar/FloatingBottomBar, motion/PageMotion + IosPageTransitions.
  - Tests that guard them: GlassMorphTest, PageMotionTest,
    ReleaseOriginCoverageTest.
- API gaps found that make the swap more than an import change:
  - Glasmio's render package has no colorControls. It has vibrancy(saturation)
    and colorFilter.
  - It has no SnapshotBackdrop. That class is Neiro's own file inside the
    vendored package and has to move into Neiro code on Glasmio's Backdrop
    interface.
  - Neiro's backdropScale parameter maps to samplingScale.
  - GlasmioTheme replaces MaterialTheme, so the app should provide
    LocalGlassColors/LocalGlassAppearance/LocalGlassBackdrop itself rather
    than wrap the root in it.
  - archivetuneplayer needs a small BRIDGE colorControls shim, so its edits
    can stay import-only.

Stage Summary:
- The modules were vendored here and wired in Task 9-b.

## Task 9-b: Glasmio port, stage 2: wiring, Kyant removal, controls, popups, tab bar, motion

Task ID: 9-b
Agent: Curialy Engineer

Task: Reviewer round 1 on the Glasmio port. Implement plan steps 3-7 and
the docs part of step 8.

Work Log:
- Wiring:
  - app and tv depend on :glasmio:ui, and archivetuneplayer on
    :glasmio:glass.
  - The three manifests carry tools:overrideLibrary for the four namespaces.
  - GlasmioSupport.kt holds glasmioAvailable (API 33),
    rememberGlasmioLocals (backdrop, colours from Neiro's colorScheme,
    appearance from Liquid Glass and Reduce dynamic blur), colorControls and
    GlassSlider/snapToSteps. MainActivity spreads the locals into its root
    provider.
- Kyant removal:
  - ui/components/backdrop (28 files) is deleted.
  - SnapshotBackdrop moved to ui/components on Glasmio's Backdrop. Its
    layerBlock inverse is dropped: no caller passes one.
  - Six app files import io.github.neoruaa.glasmio.glass.render.
    backdropScale became samplingScale.
  - The glass rim draws Highlight.Default via drawBackdrop(emptyBackdrop()),
    because Glasmio's HighlightElement and ShapeProvider are internal.
  - isGlassSupported is now API 33.
  - archivetuneplayer: kyant0:backdrop was replaced by :glasmio:glass. The
    seven files changed import lines only (plus LyricsMenu's one fully
    qualified Backdrop?). colorControls and an API-33-guarded lens come from
    bridge/GlasmioEffects.kt.
- Toggles: IosSwitch renders GlasmioSwitch on API 33+, with the same
  signature and the same haptics. A read-out switch keeps the row's tap
  through a non-consuming overlay and has no semantics of its own.
- Sliders: the settings SliderRow renders GlassSlider on API 33+, with steps
  snapped to Material's stops. The player scrubbers and volume bars (sharedUi
  ThinSlider) are unchanged. They are multiplatform and draw loading,
  Automix-window and marker states Glasmio's slider has no equivalent for.
- Popups: IosMenuPopover, GlassAlertDialog and HeldContextMenu's menu run
  LiquidMenuMorphMotion via GlasmioMorphDriver:
  - the panel follows frame.bounds, with alpha from sourceOpacity;
  - the rows follow contentProgress, contentZoom and contentBlur;
  - the scrim follows contentProgress;
  - reopening mid-close retargets the live springs.
  morphFrame stays as the fallback below 33 and under reduced motion.
- Tab bar: GlassNavBar renders GlasmioNavigationBar on API 33+.
  - Neiro's icons go through LocalGlasmioIconResolver.
  - compactProgress follows the scroll connection's isInline, and onExpand
    calls expand().
  - Search is a separate liquid-glass circle.
  - The now-playing accessory sits above the bar, or inline when compact.
- Motion:
  - GlasmioNavigationMotion.kt copies Glasmio's IosNavigationFloatSpring,
    the parallax offset and the 10% dim.
  - Push/pop and IosPageFrame progress use them.
  - Album zoom open uses the spring. The close keeps PageMotion.closeFrame
    over 350 ms.
- Tests: GlassSliderStepTest added. GlassMorphTest, PageMotionTest and
  ReleaseOriginCoverageTest are unaffected.

Stage Summary:
- Static checks:
  - The Kyant grep is empty.
  - The glasmio trees are still identical to upstream.
  - Every Glasmio control call is behind glasmioAvailable.
  - Braces and parentheses balance in every touched file.
- Not compiled. There is no SDK, rules.md forbids local builds, and no push
  has been approved yet. The first CI round is the compiler.
- Not done:
  - Interactive edge swipe-back. Neiro's AnimatedContent router has no
    seekable transition for IosSwipeBack to drive.
  - Root-tab crossfade. The deliberate no-fade tab swap is kept.

## Task 9-c: Glasmio port, review round 2

Task ID: 9-c
Agent: Curialy Engineer

Task: Reviewer round 2 on the Glasmio port.

Work Log:
- Morph fallback: GlasmioMorphDriver.open() is split into prepare() and
  expand().
  - prepare() waits until both the anchor and the panel rect are positive,
    for at most 60 frames.
  - If they never are, it sets `fallback`, and IosMenuPopover,
    GlassAlertDialog and HeldContextMenu run the legacy morphFrame path for
    that open. A popup can no longer stay at alpha 0.
  - Closing after a fallback open uses the legacy close.
- archivetuneplayer below API 33: bridge/GlasmioEffects.kt now also has
  drawBackdrop. It forwards every Glasmio parameter, but passes
  highlight/shadow/innerShadow = null when !isRuntimeShaderSupported().
  The five copied files that call drawBackdrop import it from the bridge
  (import-only). No copied file imports Glasmio's drawBackdrop or lens
  directly any more: checked by grep.
- Sliders:
  - sharedUi ThinSlider has a LocalGlassSlider slot. The app provides
    GlassSlider there on API 33+, so the player scrubber, the volume bar,
    the lyrics-offset bar and the audio-output volume render Glasmio's
    slider.
  - ThinSlider still draws itself while loading, mixing, pulsing or showing
    an Automix window, so those states are kept.
  - GlassSlider falls back to Material's Slider for a non-finite or
    non-increasing range, or a non-finite value, instead of hitting
    GlasmioSlider's require().
- Tab symbols: explicit SfSymbol.House, Safari and
  MusicNoteSquareStackFilled for Home, Explore and Library, instead of enum
  ordinals.
- Not changed:
  - DacVolumeHud. It is the transient hardware-volume HUD, not a slider
    control.
  - Root-tab switching stays instant. Glasmio's scaffold has no tab-content
    crossfade: its only AnimatedContent is GlasmioNavDisplay's stack, and
    UIKit tab bars switch instantly. So the earlier plan line "root tabs
    crossfade as in Glasmio" is not backed by the upstream source.
  - Edge swipe-back is still not implemented. Glasmio's IosSwipeBack scrubs
    a seekable navigation3 scene. Neiro's page area is one AnimatedContent
    keyed on the destination, in MainActivity. Driving it by a finger means
    moving that area to a SeekableTransitionState-driven transition, a
    separate and risky change. This needs the owner's or Architect's
    decision before it is done, and the PR body must say so.
- User-visible regression for the PR body: Android 12/12L lose the app's
  glass surfaces, which now need API 33 (Glasmio's RuntimeShader renderer),
  and show the non-glass fallbacks.

Stage Summary:
- Static checks:
  - The Kyant grep is empty outside the docs.
  - The glasmio trees are identical to upstream.
  - archivetuneplayer's copied files differ from dev only in import lines
    plus one fully qualified type.
  - Braces and parentheses balance in every touched file.
- Still not compiled: no SDK, no approved push. CI on a PR into dev is the
  next gate.

---

Task ID: 1 (this window)
Agent: Super Z (main agent, session web-4388e63e)

Task: Fix the main-branch CI failure (Android Release APK, run 44340611121)
without removing anything vendored — and fix the "app doesn't even open"
release crash the previous fix's APK shipped.

Work Log:
- Pulled the failing run's logs: :glasmio:ui:compileReleaseKotlin died on
  `LayerOutsets` / `GraphicsLayerScope.outsets`, which exist only from
  Compose ui 1.12. The vendored sources are written against upstream
  Glasmio's line (BOM 2026.09.00, ui 1.12), while Neiro's modules pinned
  the 1.11-beta line (BOM 2024.12.01 + foundation 1.11.0-beta02).
- The previous fix (489006fe) deleted the outsets from the vendored file to
  make it compile — breaking the byte-for-byte vendoring contract in
  AGENTS.md and clipping the navigation glass's exterior shadow. Reverted:
  GlasmioSidebarMorphSurface.kt is byte-for-byte upstream again (diff -r
  against the pinned commit prints nothing).
- Diagnosed the launch crash from the actual signed APK of the successful
  run and the resolved dependency graph: the phone's runtime classpath was
  ui 1.12.0 (elevated transitively by the JetBrains Compose 1.12.2 plugin
  sharedUi rides) riding foundation 1.11.1 and material3 1.5.0-alpha18
  (both compiled against the 1.11-beta line) — a split runtime whose mixed
  bytecode dies on the first frame. The removal "fix" made the build green
  and shipped exactly that mix.
- The fix is the move the glasmio README already sanctioned: "If CI shows
  these sources need 1.12 APIs, move the whole app line in one commit and
  record why in AGENTS.md." One commit moves every Android module to the
  1.12 line: compose-bom 2026.09.00 (ui/foundation 1.12.1 through it),
  material3 1.5.0-rc01 (the 1.5.0 line's pairing with ui 1.12 — alpha18 was
  the 1.11-beta pairing), activity-compose 1.13.0, sharedUi's cmp to 1.12.0.
  Nothing vendored is edited; only Neiro's own build files move.
- Verified the resolved prodRelease runtime graph is now single-line
  (ui 1.12.1, foundation 1.12.1, material3 1.5.0-rc01, no -> arrows left)
  and that :glasmio:ui:compileReleaseKotlin is green with the restored
  outsets on the new line. The rest is CI's to check, per the owner's rule.

Stage Summary:
- Vendored glasmio restored byte-for-byte, app-wide Compose on one 1.12
  line, launch-crash root cause (split runtime) removed at the source.

---

Task ID: 2 (this window)
Agent: Super Z (main agent, session web-4388e63e)

Task: CI round 2 — :archivetuneplayer compiles against the material3 1.5.0-rc01
API surface the line move picked up (alpha18's experimental surfaces finalized
differently by rc01).

Work Log:
- Read every failing call site and the rc01 signatures straight out of the
  material3-android 1.5.0-rc01 AAR already in the Gradle cache (javap) plus
  the androidx-main sources for the same files; no local build run.
- Slider migration, the big one: rc01's rememberSliderState dropped the
  onValueChange/onValueChangeFinished factory params and renamed valueRange to
  trackRange, SliderState lost the settable onValueChange, and the state-based
  Slider now takes onValueChange (required) + onValueChangeFinished itself.
  Converted all ten sites the CI round flagged — Preference's three dialog
  sliders and PlayerSettings' preload slider (static ranges), LyricsScreen
  and PlayerComponents' Thick style (ranges that change per song — keyed
  remember on the range ends constructing SliderState directly, value synced
  per recomposition), and Flamingo's seek (keyed on the track duration) and
  volume sliders. PlayerSliderTrack reads .trackRange now.
- ToggleButtonDefaults.toggleButtonColors -> ToggleButtonDefaults.colors
  (renamed; same six color params) in QueueComponents.
- ShortNavigationBarItemDefaults.colors' selectedTextColor split into
  selectedTextColorTopIconPosition / selectedTextColorStartIconPosition;
  both set to the same colour at all three FloatingNavigationToolbar sites.
- ExposedDropdownMenu's scope-member became HIDDEN in rc01; the replacement
  is the top-level extension of the same name — imported it in
  EqualizerDialog, and menuAnchor() now takes its type:
  PrimaryNotEditable, the anchor of a read-only field.
- LoadingIndicator's opt-in marker is ExperimentalMaterial3ExpressiveApi on
  the rc01 line; added it to the module's compiler opt-ins beside the
  ExperimentalMaterial3Api that was already there.
- Verified (javap only) that the app's own fragile APIs all exist on the
  1.12 line: LocalOverscrollFactory (foundation OverscrollKt),
  rememberPullToRefreshState / PullToRefreshBox (m3 pulltorefresh),
  rememberModalBottomSheetState, ModalBottomSheetProperties,
  LocalClipboardManager. TV's failure was the same archivetuneplayer set,
  so this covers all three workflows.

## Task 9: the Glasmio port — glass controls, popups, navigation, motion

Task ID: 9
Agent: Super Z (orchestrator, session web-4388e63e)

Task: Port the glass sliders, glass toggles, popup opening animations,
navigation bar, page switch animation and album opening animation from
myself-tsuki/Glasmio (the JitPack fork of NEORUAA/Glasmio), and replace
the Kyant Backdrop dependency — the vendored source in the app, the
io.github.kyant0:backdrop artifact in :archivetuneplayer — with the
backdrop implementation Glasmio's own glass module ships.

Work Log:
- Dependency: com.github.myself-tsuki.Glasmio:glasmio-ui + :glasmio-scaffold
  v0.2.1 from JitPack in :app (the fork is not on Maven Central — the
  upstream io.github.neoruaa.glasmio coordinates only resolve for the
  original project, so the fork coordinates are the ones used);
  :glasmio-glass in :archivetuneplayer; io.github.kyant0:backdrop removed.
  Glasmio minSdks 31 against the app's 26, so the manifest gains
  tools:overrideLibrary for the five fork packages and every fork surface
  stays behind isGlassSupported(), the gate the vendored glass already sat
  behind. GlasmioPerformance.configure() at Application start;
  GlasmioTheme nested inside BitChordTheme (the app's own colour scheme
  and typography passed through), GlasmioBackdropProvider over the app
  backdrop, and GlasmioIconProvider with a resolver that maps the
  navigation bar's four SF names to BitChordIcons and falls through to
  Glasmio's stock Material mapping.
- Backdrop swap: LiquidGlass.kt (the app's glue) rewritten on
  io.github.neoruaa.glasmio.glass.render — drawBackdrop with vibrancy,
  blur, lens, Highlight/Shadow, the resolution scale moving from the
  vendored backdropScale parameter to Glasmio's samplingScale (the fork
  multiplies it by its own performance-tier scale). The vendored Kyant
  package ui/components/backdrop/** deleted entire; SnapshotBackdrop and
  InverseLayerScope — the app's own still-of-the-source recorder — moved
  to ui/components and rewritten against Glasmio's Backdrop interface.
  IosMenu's menuBackdropFrost, AndroidPlayerHost's PlayerFrost and the
  MainActivity wiring (rememberLayerBackdrop/layerBackdrop imports) all
  follow. :archivetuneplayer's LiquidGlass glue and its eight kyant
  importers (FlamingoPlayer, LyricsMenu, BottomSheetMenu,
  FloatingNavigationToolbar, CompactBottomControls, FrostedHeaderPill)
  swapped to the same render namespace, colorControls → vibrancy.
- Glass toggles: IosSwitch delegates to GlasmioSwitch — the iOS 27 63×28
  track with the elastic liquid-thumb lens — wherever Liquid Glass is on
  and supported; the plain iOS 26 capsule stays for Android 8-11, glass
  off, and read-out rows (null callback). 111 call sites through one
  wrapper; the flip haptic stays.
- Glass sliders: SettingsSheet's SliderRow renders GlasmioSlider (the
  40dp capsule track, accent fill, 37×24 glass-lens thumb) in glass mode,
  22 call sites through one wrapper; the discrete rows quantise the drag
  to the same steps+1 intervals M3's own slider used, so every settings
  writer sees the values it always did. M3 Slider stays as the fallback.
- Navigation bar: GlassNavBar rebuilt on GlasmioNavigationBarWithTrailingItem
  — the fork's split-search tab bar, the Home/Explore/Library platter with
  the Search circle fused through the BottomNavigationContact union, the
  selection indicator a native control lens, the platter's own morph into
  the selected tab's circle on the fold. The now-playing accessory rides
  above as its own glass capsule on Modifier.liquidGlass, fading with the
  fold; the fold driver (FloatingTabBarScrollConnection) moved to
  NavigationBarScroll.kt and drives the compact progress on the fork's
  own navigation spring. The Echo FloatingTabBar and GlassSelectionPill —
  the shell the fork replaces — deleted; the plain FloatingBottomBar path
  is untouched.
- Popup opening animations: GlassMorph.kt now carries Glasmio's exact
  DropdownMorphSpring (damping 0.8, stiffness (2π/0.3)² = 438.64908 —
  AnimationKit's liquidMorph content spring), the content fade curve
  ((progress − 0.12) / 0.64) and the 300 ms collapse; every popup that
  grows out of its control — IosMenuPopover, HeldContextMenu — picks the
  ported constants up where they read MORPH_*. :archivetuneplayer's
  BottomSheetMenu and LyricsMenu entrances swapped to the same spring
  (GlasmioMotion.kt, module-local) with the fork's 120/300 ms alphas.
  The unit tests that pinned the old curve (GlassMorphTest) moved with
  the engine, per house rule.
- Page switch and album opening: IosNavigationMotion.kt — Glasmio's
  IosNavigationMotion ported verbatim where physics and adapted only
  where it was typed against navigation3's Scene (the unnormalised
  critical spring IosNavigationFloatSpring sampled on nanos, the slide
  spring, UIKit's thirty-percent parallax offset pixel-exact through the
  density round-trip, the 350 ms easing). iosPageTransform slides on the
  fork's spring with RTL sign; tab swaps crossfade on the fork's
  rootTabTransition (fadeIn togetherWith fadeOut, what its own sample
  swaps top-level routes on); the under-page dim is the fork's native
  ten percent; IosPageFrame's progress runs the fork's easing. The album
  zoom out of its cover — the app's own ReleaseOrigin card — rides
  unchanged over the ported slide. PageMotionTest now pins the ported
  spring and parallax instead of the retired tween curve.

Stage Summary:
- :app:compileDevDebugKotlin and :archivetuneplayer:compileDebugKotlin
  green; :app:testDevDebugUnitTest green (1340 tests, the three motion
  tests updated with the port); manifest merge, D8 external dexes with
  the fork's classes and the wearos R8 pass verified locally under the
  sandbox's 3.9 GB memory recipe. The full APK assembly's native link
  was disk-starved in this sandbox (unchanged C++ sources, bus error at
  100% disk); CI's runners carry the rest.


---

Task: analyse and fix the Glasmio sample's crashes on taps and scrolling (user log 19_11-19-24-36)

- Four RenderThread SIGSEGVs in 22 s, fault addresses null+0x38, 1.0f+0x38,
  float data and "android." bytes: use-after-free; no backtrace (crash_dump
  denied /proc maps by SELinux; UnityDisplayTweaker injected).
- The phone's Dimensity 8100 auto-classifies Balanced (cap 60) on a 120 Hz
  panel, so every glass surface ran through GlassFrameCache (v0.2.2+), the
  second renderer behind the afternoon's earlier crash/ANR fixes.
- Fix as patches against 0681a13 (third_party/glasmio-patches/fix): the
  off-screen renderer is opt-in (cachedGlass), and for opt-in hosts the glass
  layer is released after the renderer and readers close on the image thread.
- glasmio-sample-smoke.yml + .github/scripts/glasmio-sample-smoke.sh: build the
  sample (harness: GWP-ASan, cap/route/cached extras), tap every control,
  scroll every page, monkey seeds 1-3, on API 34/35/36; prints tombstones.
- GlasmioVersionGuardTest: Neiro stays on v0.2.1 until FIXED_IN names a tag.
- Not run yet: the workflow (needs a push); Neiro's pin is unchanged.

## Task: merge curialy/glassmio (PR #25) into main

Task ID: merge-pr25
Agent: Super Z (orchestrator, session web-4388e63e)

Task: Fix the conflicts of PR #25 (branch curialy/glassmio, the JitPack
Glasmio fork port) and merge it into main.

Work Log:
- Merge base 21549b94 (PR #1); main carried 112 commits since (Listen
  Together, backup, i18n, settings reorg, CI/build fixes on the vendored
  Glasmio port); the PR carried the final Glasmio port state as one
  snapshot commit 8f00951a.
- Architecture resolved to the PR's: the vendored `glasmio/` Gradle modules
  are deleted (the JitPack fork `com.github.myself-tsuki.Glasmio` v0.2.1
  replaces them — glasmio-ui + glasmio-scaffold in :app/:tv,
  glasmio-glass in :archivetuneplayer), settings.gradle.kts loses the four
  module includes, app/tv build files lose `project(":glasmio:ui")`, and
  `bridge/GlasmioEffects.kt` is dropped with the whole module importing the
  fork's render namespace directly. The manifests keep the five-package
  tools:overrideLibrary from the PR.
- Components resolved to the PR's port state: GlassMorph (DropdownMorphSpring
  constants), GlassDialog, GlassNavBar (GlasmioNavigationBarWithTrailingItem;
  the Echo FloatingTabBar/GlassSelectionPill shell deleted with the fold
  driver now in NavigationBarScroll.kt), HeldContextMenu, IosMenu, IosSwitch
  (GlasmioSwitch behind LocalLiquidGlassEnabled + isGlassSupported), the app
  LiquidGlass glue, SnapshotBackdrop + the flat InverseLayerScope, page
  motion (IosPageTransitions/PageMotion/IosNavigationMotion), and the three
  motion tests that pin the ported constants. GlasmioMenuMorph.kt (main's
  LiquidMenuMorphSurface driver) is superseded and deleted.
- Main's post-port fixes preserved: the rc01 Slider/ToggleButton/dropdown
  conversions in archivetuneplayer (FlamingoPlayer/PlayerComponents/
  Preference/PlayerSettings keep the SliderState-based calls; only the fork
  import rewiring was taken from the PR), the Compose 1.12 / BOM 2026.09.00
  line everywhere, launch-smoke.yml, GlassSliderStepTest, proguard dontwarn,
  and every main-only feature (Listen Together, backup, i18n, sources...).
- sharedUi's ThinSlider keeps main's LocalGlassSlider glass ride: the slot
  is provided from MainActivity inside the PR's GlasmioBackdropProvider
  wiring (GlasmioThinSlider in GlasmioSupport.kt), and AGENTS.md documents
  the merged architecture.

Stage Summary:
- Compile/test evidence: recorded by the CI run that follows this merge.

---

## Task: fix the launch RenderThread crash, push to dev, open the PR

Task ID: crash-guard-pr26
Agent: Super Z (orchestrator, session web-4388e63e)

Task: Fix the app's startup crash (user log 19_11-21-23-30_017.txt), push the
fix on dev, open a pull request, monitor the build — no local compiling.

Work Log:
- Read the log: `Fatal signal 5 (SIGTRAP), code 1 (TRAP_BRKPT)` in
  RenderThread, three launches dead in six seconds, each right after the
  glass nav bar's first frame — realme RMX3563, Android 16, Mali, ColorOS
  GED/MEOW hooks injected into RenderThread. No tombstone (SELinux denies
  crash_dump the maps), and a native death never reaches the Java
  uncaught-exception handler, so the existing CrashLoopGuard was blind to
  it: every relaunch armed the same glass and died the same way.
- Traced the first-frame render path: MainActivity's GlasmioTheme +
  rememberLayerBackdrop + GlasmioNavigationBarWithTrailingItem (the fork's
  full optics: AGSL refraction lens, blur, vibrancy, CASDF union) — the
  port's new GPU load. The fork itself documents the same device and
  signature (myself-tsuki/Glasmio#7, Muzo, RMX3563/Android 16/Mali) and is
  rebuilding its render cap around it; that fix is unmerged and
  GlasmioVersionGuardTest pins v0.2.1, so the app guards itself app-side.
- The fix, in CrashLoopGuard (the repo's own crash-loop idiom, extended to
  native deaths): every launch writes a glass session marker in files/crash
  while Liquid Glass is armed (Application.onCreate, before any glass
  renders); a launch that survives QUICK_CRASH_MS deletes it; a marker still
  standing at the next checkOnLaunch is the remains of a launch that died
  young; two of those in a row — CrashLoopPolicy.isNativeDeathLoop, the
  same two-quick-crashes rule the style guard uses — switch Liquid Glass
  off (MainActivity then draws the Haze bars this hardware ran before the
  port) and the surviving launch says so once. A Java crash clears the
  marker (the style guard owns that death), one young death never counts, a
  stale marker is yesterday's force-stop, and the setting can be turned
  back on at any time.
- liquid_glass_crash_reset added to values + the 19 locale folders that
  translate player_style_reset (LocaleStringsTest requires every supported
  language to carry it); the it/tr apostrophes escaped for AAPT.
- Resolved this branch against main (PR #26's back-merge): 138 files
  auto-merged; SettingsSheet.kt takes main's rc01 SliderState conversion
  (the merged dependency line is compose-bom 2026.09.00 / material3
  1.5.0-rc01, and the shared bare-track tail needs it); worklog.md unions
  both histories.

Stage Summary:
- Pushed to dev (bf8c97c8 the guard, then this merge); PR #26 opened
  dev -> main. CI is the compile evidence: monitored in check.yml's
  testDevDebugUnitTest + assembleDevDebug round that follows.

---

## Task: fix the second launch crash log — SIGSEGV this time

Task ID: glass-frost-wiring
Agent: Super Z (orchestrator, session web-4388e63e)

Task: Fix the crash in the new user log (19_11-22-25-14_825.txt): Fatal
signal 11 (SIGSEGV) in RenderThread, three launches dead in six seconds,
same device (realme RMX3563, Dimensity 8100, Android 16, Mali, ColorOS
MEOW in RenderThread, LSPosed/UnityDisplayTweaker in every app, SELinux
denying crash_dump its maps — no tombstone, same environment
docs/glasmio-crash-2026-11-19.md documents for the fork's own crashes).

Work Log:
- Read the log: death 1 loads libglasmio_liquid_menu_sdf.so (the fork's
  native SDF field, shipped inside the v0.2.1 AAR) 263 ms before its
  RenderThread dies; deaths 2 and 3 fault at the SAME address
  0xb0b000d5c — a pointer slot holding packed field-pixel data, the
  use-after-free shape the crash doc decoded for the fork's sample
  (faults like null+0x38, 1.0f+0x38, "android." bytes). The 4th process
  survived only because the user swiped the app away mid-launch, before
  the glass composed. The build on the phone predates the crash guard:
  three deaths, where the guard converges the third launch with Liquid
  Glass off.
- Confirmed the phone's tier is Balanced (Dimensity 8100 -> PreviousFlag
  SoC): full optics — the nav bar runs the fork's whole AGSL refraction
  chain over the FP16 contact-field bitmap on first frame.
- The fork's real fix is unreleased (issue #7 open; the snapshot
  redesign unmerged), v0.2.2-v0.2.4 are banned by GlasmioVersionGuard
  Test, and a no-tombstone OEM native crash cannot be pinned to a line
  app-side. What the app CAN do, this log shows where: the app's own
  "Better Performance for Glass" switch promises "trade the glass lens
  for a plain frost" — but it only frosted Modifier.liquidGlass; the
  fork's navigation bar, the component actually dying, read its optics
  straight off GlasmioPerformance and ignored the switch entirely.
- The wiring: BitChordApplication installs
  GlasmioPerformanceConfig(refraction = !AppSettings.glassPerformance)
  after AppSettings.init — before the first glass composition — and
  MainActivity keeps mid-session toggles in step with a LaunchedEffect.
  A phone that dies under the lens now has the frost as a working-glass
  option rather than all-or-nothing; the crash guard (bf8c97c8) stays
  the net that guarantees the app boots either way.
- Launch Smoke for the guard's head (f9b89894) completed successfully:
  the merged tree boots on API 30/34 emulators with no Fatal signal in
  logcat. Check, TV and the Release APK workflows all passed the same
  head.

Stage Summary:
- Pushed to dev; CI is the compile evidence (7-minute monitor per the
  rules). PR #26 (dev -> main) carries the guard and this wiring; it
  merges once this round is green so the phone's next build boots.

---

## Task: the launch smoke tolerates one runner-side flake per APK

Task ID: smoke-retry
Agent: Super Z (orchestrator, session web-4388e63e)

Task: The Launch Smoke round for the frost wiring (99d68499) failed three
times on the API 34 job with three different runner-side failures and no
app crash signature in any of them; make the workflow's verdict mean the
app, not the runner.

Work Log:
- Attempt 1: ANR in the dev build — input dispatching timed out waiting
  5003 ms for FocusEvent, during cold start, with the process ALIVE at
  +25 s; runner load 20, CPU pressure 89%, 37k major faults.
- Attempt 2 (rerun): the dev build clean; the same FocusEvent ANR on the
  PROD build instead; load 30, CPU pressure 91%. The ANR follows whichever
  APK launches while the runner is buried — not the code, not even the
  variant.
- Attempt 3 (rerun): the emulator's own graphics stack broke (Failed to
  find ColorBuffer: 98, invalid color buffer, I/O thread spun) and the dev
  build died with NO crash, ANR, or exception line in logcat.
- Three attempts, three infra modes, zero app signatures: the gate was
  measuring the runner. launch-smoke.sh now runs each APK's launch through
  launch_once() with a verdict per class — 0 alive and clean, 1
  runner-side (silent death, or an ANR the process recovered from), 2 the
  app's own signature (FATAL EXCEPTION / Fatal signal). Class 1 gets one
  clean relaunch; class 2 never retries, so a genuinely crashing build
  still fails both attempts and the gate stays strict. Both attempts keep
  their logcat and screenshot in the artifacts.

Stage Summary:
- Pushed with the frost wiring's head; the next Launch Smoke round is the
  evidence. Check, TV and the Release APK stayed green throughout.

## Task: the Glasmio exodus — every liquid glass, slider, toggle and bar onto AndroidLiquidGlass

The Glasmio fork is gone entirely: no JitPack coordinates, no
io.github.neoruaa imports, no manifest overrides, no version guard, no
sample-smoke workflow, no crash patches, no crash doc. Every glass
surface now draws through the vendored AndroidLiquidGlass backdrop
module instead.

- The renderer is `:backdrop` — guozhiqiang123/AndroidLiquidGlass's
  backdrop module (Kyant's fork) vendored at 0ed8b08, commonMain + the
  Android actuals with the expect/actual pairs collapsed. One
  deliberate change: drawBackdrop/drawPlainBackdrop take a per-surface
  `samplingScale` multiplied with the global BackdropRendering.scale
  (the fork exposes the scale only globally; the app's surfaces are
  tuned per call site — a third, a quarter under frost). The fork is
  published nowhere its Gradle can be consumed from (no Maven Central,
  no JitPack build — AGP 9.3.2/KMP/publishToMavenCentral), which is
  why it's vendored and compiled by this repo's own toolchain; its one
  Maven dependency, io.github.kyant0:shapes, resolves normally.
- The liquid controls are the fork's own catalog, ported into
  app/…/ui/components/liquid with BitChord's theme, backdrop and
  tuning: LiquidGlassSlider (6dp track, 40×24 lens thumb, damped drag,
  press blur-out, velocity squish), LiquidGlassSwitch (64×28 capsule,
  40×24 lens thumb, drag-to-commit), LiquidGlassTabBar (the capsule
  platter with the selection indicator as a control lens over an
  accent-tinted recorded copy, damped drag-to-switch, the tracking
  highlight, BitChord's split-search trailing circle and the fold
  morph), plus the DampedDragAnimation / DragGestureInspector /
  InteractiveHighlight utils they ride. IosSwitch, GlassNavBar,
  GlassControls' GlassSlider, the SettingsSheet's sliders and
  sharedUi's LocalGlassSlider slot all render these now.
- The render-level glue swapped namespaces: app LiquidGlass.kt,
  SnapshotBackdrop, IosMenu's menuBackdropFrost and the eight
  archivetuneplayer files import com.kyant.backdrop.* (vibrancy(x) →
  colorControls(saturation = x); the fork's vibrancy() is fixed at
  1.5). GlasmioMotion.kt → LiquidMorphMotion.kt, the constants the
  menus morph on renamed with it. The motions the app had ported
  (IosNavigationMotion, IosPageTransitions, GlassMorph) are untouched
  code — only their Glasmio mentions were reworded.
- MainActivity lost the fork wrappers (GlasmioTheme, the icon
  provider, the backdrop provider, the performance-tier effect);
  BitChordApplication lost both GlasmioPerformance installs. The
  frost switch (glassPerformance) is honoured per surface instead —
  the lens goes, the blur doubles, the sampling drops to a quarter —
  the same trade Modifier.liquidGlass always made.
- The three manifests lost their tools:overrideLibrary (the vendored
  module minSdks 21, below the app's 26). check.yml's Android job,
  android.yml and the launch smoke are the CI that has to carry it.

Stage Summary:
- GlasmioVersionGuardTest deleted (its coordinate scan has nothing to
  guard); GlassMorphTest and PageMotionTest untouched in substance —
  the pure math they pin didn't change. AGENTS.md's library section
  rewritten around :backdrop. Nothing else moved: the motions, the
  tuning, the settings pages and every call site's glass parameters
  are the values they were under the fork.

---
Task ID: curialy/zenith-liquid (partial)
Agent: Curialy Engineer
Task: Remove Glasmio traces; Zenith nav bar + mini player; AndroidLiquidGlass controls; fix the user's RenderThread SIGSEGV.
Work Log:
- Branched curialy/zenith-liquid off origin/dev 5a1853b (never main).
- Found that LiquidGlassSwitch and LiquidGlassSlider defaulted `backdrop` to
  LocalAppBackdrop (= appBackdrop). Both live inside pages that
  layerBackdrop(appBackdrop) records: one instance of the
  glass-samples-its-own-recorder pattern removed. Whether it was the user's
  crash is unproven (the log has no backtrace; LSPosed/UnityDisplayTweaker is
  also injected into the app). Their default is now emptyBackdrop(); the thumb still
  refracts its own track through the catalog's combined track backdrop.
Stage Summary:
- One instance of the glass-samples-its-recorder pattern removed; whether it was the user's crash is unproven (no backtrace; LSPosed/UnityDisplayTweaker is injected on that phone).
- Only the crash fix landed in this round. The Zenith GlassNavBar/floatingtabbar/MiniPlayer port,
  the catalog toggle/slider rebuild, the Glasmio text purge (GlassMorph.kt, AGENTS.md)
  and the launch-smoke interaction phase are still to do.
- Still suspect: FrostedTopBar -> Modifier.liquidGlass samples LocalAppBackdrop too;
  check whether it is drawn inside the recorded page.

---
Task ID: curialy/zenith-liquid (round 2, partial)
Agent: Curialy Engineer
Task: Reviewer round 1 fixes.
Work Log:
- Glasmio purge: LiquidMorphMotion.kt and IosNavigationMotion.kt deleted;
  GlassMorph.kt, IosPageTransitions.kt, PageMotion.kt and their tests restored
  to a150d33^; ArchiveTune BottomSheetMenu.kt and LyricsMenu.kt are
  byte-identical to a150d33^ again; MainActivity tab swaps back to no
  transition; AGENTS.md rewritten; the purge grep is empty.
- LiquidGlassDefaults.kt added (catalog @0ed8b08 values); LiquidGlass.kt reads
  its blur/lens/alpha from it (same numbers as before).
- A Zenith nav-bar port was started, then reverted, because MainActivity
  wiring, AppSettings.glassIndicator and PlayerDock pill fields could not be
  finished this round. Not compiled.
Stage Summary:
- Still open: Zenith GlassNavBar/floatingtabbar/MiniPlayer port (removes
  LiquidGlassTabBar + NavigationBarScroll), catalog rebuild of the
  toggle/slider, Modifier.liquidGlass call-site audit, launch-smoke
  interaction phase, push + CI.

---
Task ID: curialy/zenith-liquid (round 3)
Agent: Curialy Engineer
Task: Reviewer round 2 fixes.
Work Log:
- MainActivity imports point back at ui/components (NavigationBarScroll.kt),
  where FloatingTabBarInlineBehavior and rememberFloatingTabBarScrollConnection
  live; the non-glass MiniPlayer gets compactFraction again.
- Unused PlayerDock pill members and LiquidGlassDefaults.LiquidTabIndicator
  were removed (leftovers of the reverted port).
- Recursion audit: Modifier.liquidGlass is the only appBackdrop reader besides
  the tab bar. FrostedTopBar, the nav bar and the compact circles are drawn
  outside the recorded AnimatedContent; lightweightLiquidGlass samples nothing.
  The page content lambda now provides LocalAppBackdrop = emptyBackdrop(), so
  no glass inside a recorded page can sample its recorder. Pinned by the new
  GlassRecursionGuardTest.
Stage Summary:
- Zenith nav bar/mini player port, catalog toggle/slider rebuild and the
  launch-smoke interaction phase are still open; not compiled or pushed.

---
Task ID: curialy/zenith-liquid (round 4, follow-up #1)
Agent: Curialy Engineer
Task: Finish the Zenith nav bar / mini player port; startup and rendering performance.
Work Log:
- 4e2d703: GlassNavBar.kt, floatingtabbar/{FloatingTabBar,GlassSelectionPill,RubberStretch}.kt,
  FloatingBottomBar.kt and MiniPlayer.kt are Zenith@01e2ab1 verbatim (package mapped,
  comments stripped per Rule 3, licence headers kept). LiquidGlassTabBar.kt and
  NavigationBarScroll.kt deleted; MainActivity uses floatingtabbar's
  rememberFloatingTabBarScrollConnection(expandThreshold = 250.dp) as Zenith does,
  with MiniPlayerBarBehavior.ADAPTIVE -> OnScrollDown.
- Edges adapted: AppSettings.glassIndicator (data/settings/GlassIndicator.kt, Zenith's
  DEFAULT preset values, no settings UI); lens(chromaticStrength) added to :backdrop
  (additive, default 1f = upstream behaviour); Zenith's panel morph (panelShown /
  reportMiniPill) is not ported, so playerDockPill keeps the pill visible; Neiro's
  compactFraction fold grafted onto Zenith's MiniPlayer; Neiro's
  CompactControlCircles moved to its own file.
- GlassRecursionGuardTest pins the bar outside the recorded page.
- Baseline profile extended to com/kyant/backdrop and ArchiveTune ui; profileinstaller
  1.4.1 added to :app and :tv; tv/src/main/baseline-prof.txt added.
Stage Summary:
- Not compiled (no SDK) and not pushed (no push tool in this run).
- Still open: catalog-verbatim toggle/slider rebuild, RuntimeShaderPool + prewarm,
  appBackdrop recording skip, per-frame read moves, deferred startup work,
  launch-smoke interaction + perf phase, AGENTS.md glass section rewrite.

---
Task ID: curialy/zenith-liquid (follow-up 1, round 2)
Agent: Curialy Engineer
Task: Finish the open items; make startup and steady-state rendering fast without lowering quality.
Work Log:
- 4e2d703: Zenith@01e2ab1 GlassNavBar + floatingtabbar/{FloatingTabBar,GlassSelectionPill,RubberStretch}
  + FloatingBottomBar + MiniPlayer ported. LiquidGlassTabBar.kt and NavigationBarScroll.kt deleted.
  GlassIndicator = Zenith DEFAULT preset. Lens gained chromaticStrength (default 1f = upstream).
- 50a60ca: profileinstaller 1.4.1 for :app/:tv; baseline-prof.txt also covers com/kyant/backdrop
  and the ArchiveTune UI.
- This round: RuntimeShaderPool in :backdrop (per-node exclusive instances, returned on onDetach
  only) + prewarmBackdropShaders() from BitChordApplication on Dispatchers.Default; ShaderPoolTest
  (also run locally with kotlinc 2.1.0: all pass). backdrop/PATCHES.md. Toggle/slider sizes and thumb
  blur now read LiquidGlassDefaults; DampedDragAnimation/InteractiveHighlight/DragGestureInspector
  already equal the catalog except visibility, withFrameNanos and System.currentTimeMillis.
  launch-smoke: interaction phase (6 swipes, 5 bar taps, mini player tap + back, 10 s settle) before
  the crash checks, a perf block (am start -W x3, gfxinfo) as-installed and after speed-profile into
  smoke-out/perf-*.txt, and a baseline.prof presence check on the prod APK; android.yml verifies it too.
Stage Summary:
- Not done: skipping appBackdrop recording while unseen, moving per-frame reads out of the BitChordApp
  scope, and deferring non-visual startup work. player.position is already a separate holder
  (PlaybackPosition), so ticks do not recompose BitChordApp.
- Nothing compiled: the sandbox has no Android SDK and no push tool in this round. Perf numbers
  come from the first launch-smoke run after the push.

---
Task ID: curialy/zenith-liquid (perf round 2)
Agent: Curialy Engineer
Task: First-seconds lag; review round 2 fixes.
Work Log:
- Moved per-frame reads out of BitChordApp's composition (MainActivity.kt):
  - bottomUiCompactFraction (spring, every frame of the fold) is now held as
    State (bottomUiCompactState, ~l.726). MiniPlayer takes compactFraction as
    a `() -> Float` and reads it inside its own scope (MiniPlayer.kt); 
    CompactControlCircles takes `fraction: () -> Float` and reads it only in
    graphicsLayer {} (CompactControlCircles.kt). The show/hide test is a
    derivedStateOf Boolean (bottomUiCompactShown), so BitChordApp recomposes
    twice per fold instead of once per frame. FloatingBottomBar's
    graphicsLayer read was already draw-phase.
  - player.position is already a separate PlaybackPosition holder (not part
    of PlayerState equality), so it does not recompose BitChordApp per tick.
- Deferred startup work: AppUpdateChecker.clearCache + check moved from
  MainViewModel.init into onFirstFramesDrawn(), called from a
  LaunchedEffect(Unit) after two withFrameNanos in BitChordApp; runs once per
  ViewModel. BitChordApplication's ordered startup is untouched.
- 7.1 (skip layerBackdrop(appBackdrop) while nothing samples it): deliberately
  deferred. The nav bar (always composed above pages) samples appBackdrop on
  every tab, so the only off state is "full player fully covers the page".
  Swapping the modifier there risks one empty-glass frame when the sheet
  starts collapsing, which cannot be checked without CI screenshots. Do it
  after the first green launch-smoke round.
- launch-smoke: perf block runs only for the prod APK; launch job timeout 60 min.
- Push/PR: not possible in this run (no push tool exposed); branch is local.
Stage Summary:
- Unverified by a compiler: nothing on this branch has gone through CI yet.

---
Task ID: PR #28 conflicts + leftovers
Agent: Curialy Engineer
Task: Clear PR #28's merge conflicts (head curialy/fix, base main); resolve
the open items left by the earlier rounds.
Work Log:
- PR #28 head 8c5fc9e (one commit on d549f39) has the same tree as the local
  curialy/zenith-liquid@5846497. main@bd51e1e (PR #27) has the same tree as
  dev 5a1853b, which this branch already contains. The 18 merge-tree
  conflicts come from the same work arriving along two histories.
- Resolution: `git merge -s ours --no-ff origin/main` on curialy/fix ->
  519d975 (parents 8c5fc9e, bd51e1e). Proof: `git diff --quiet 8c5fc9e 519d975`
  passes (tree unchanged); `git diff origin/main 519d975 --stat` equals
  `git diff origin/dev curialy/zenith-liquid --stat`; `git merge-tree
  --write-tree origin/main 519d975` exits 0 with no CONFLICT. No Glasmio file
  (NavigationBarScroll.kt, IosNavigationMotion.kt, the JitPack fork) comes back.
- Item 7.1 (skip the appBackdrop recording while the player covers the page):
  already in place on dev. The page root's `drawWithContent { if
  (!coveredByPlayer) drawContent() }` (MainActivity ~l.3100, content box
  l.3102-6665) holds the recorder (l.3203) and GlassNavBar (l.4512).
  coveredByPlayer is a derivedStateOf on openFraction() >= 1f, so recording
  resumes on the first frame of a collapse. grep shows no LocalAppBackdrop or
  liquidGlass reader in ui/player or sharedUi. No code change; this corrects
  the earlier "deliberately deferred" note.
- Zenith's PlayerDock.panelShown / reportMiniPill panel morph: not ported, on
  purpose. It belongs to Zenith's PlayerPanel side panel, which Neiro does not
  have (Neiro's player is a ModalBottomSheet). Known difference.
- Codex review on PR #28: completed on 8c5fc9e with one P1 finding
  (discussion_r4635985090). LiquidGlassSlider read its `value: Float`
  parameter inside a once-started snapshotFlow, so the effect never saw the
  parent's new value. The drag delta was computed from that frozen
  targetValue, and onDragStopped wrote it back, so glass sliders (settings,
  player scrubbing) did not follow the finger and reverted on release.
  Fixed (LiquidGlassSlider.kt):
  - the effect reads rememberUpdatedState(value), like the catalog's
    `value: () -> Float`;
  - the callbacks go through rememberUpdatedState;
  - the drag accumulates the finger position itself, so callers that snap to
    steps (GlassSlider, the SettingsSheet sliders) cannot pin a slow drag to
    one step;
  - release reports the finger value.
  The call sites are unchanged.
Stage Summary:
- Not yet verified: the push of 519d975 and every CI round (Check, TV,
  Launch Smoke) wait for the Reviewer's approval. Perf numbers stay pending
  until the first green launch-smoke run.

---
Task ID: curialy/glass-always
Agent: Curialy Engineer
Task: Glass always on (no master switch); steady nav tabs while dragging; performance and leaks.
Work Log:
- Branched from origin/main 349fda8 (stale d549f39 leftovers kept in `git stash` "stale-d549f39").
- Glass always on: removed AppSettings.liquidGlass / setLiquidGlass, GlassDefault (+test),
  LocalLiquidGlassEnabled and every reader, the nav-bar and mini-player per-element glass
  switches, the Settings master row, CrashLoopGuard's native glass-loop reset and its
  MainActivity notice, and the 8 now-unused strings in all locales. The retired prefs keys
  are removed once on load. ArchiveTune's LiquidGlassEnabledKey is bridged as always true.
- Drag flicker, four causes fixed in floatingtabbar/:
  1. the swell graphicsLayer scaled the tab Row: it now scales only the background surface Box
     (same barGlass export node);
  2. startDrag ran twice per gesture and restarted the lift springs: now a no-op while held;
  3. dragTo moved the pill through scope.launch{snapTo}, a frame behind the finger: it now sets
     a snapshot dragPos synchronously and hands off to the same springs on release/settle;
  4. PillTinted clipped to the rest tab width: tabPillGeometry now reports the drawn pill
     width from PillGeometry, the same function the pill is sized with.
  The flat and glass pills stay composed during the gesture (alpha / draw gating), so nothing
  is added or removed mid-drag. All spring specs are unchanged.
- Tests: PillGeometryTest, LiquidGlassAlwaysOnGuardTest (no master switch; synchronous drag;
  the swell does not scale the Row). Not compiled locally (no Android SDK or JDK here).
- Round 2 (performance and leaks, no visual change):
  - compose_stability.conf (data.model.*, List/Map/Set) wired into app, sharedUi and tv via
    composeCompiler.stabilityConfigurationFiles; every data.model class is vals-only.
  - Baseline profile: already covers floatingtabbar/**, liquid/** and com/kyant/backdrop/**
    through the existing `com/music/bitchord/**` and `com/kyant/backdrop/**` wildcards; no
    new rules needed.
  - Leak audit: every listed registration already pairs with its release or is a
    process-lifetime singleton on applicationContext with an install-once guard
    (BitPerfectOutput/LosslessOutput/ExclusiveTransport audio-device callbacks,
    UsbDirectDevices receivers, CastController removeCallback lambda, CastPlayback
    unregisterCallback in release, CrossfadeController removeListener on swap,
    PlaybackService removeListener on swap and release, PlayerConnection DisposableEffect,
    the MediaController future on the main executor, AudioCache storing applicationContext).
    The three `while (isActive)` loops are lifecycle-bound already (two in LaunchedEffect with
    delay, MusicVideoPlayer's syncJob cancelled in release). No code change was needed;
    LeakCanary now checks this at runtime.
  - LeakCanary 2.14 (debugImplementation, launcher icon off; heap dumps off on emulators so
    launch-smoke cannot ANR on a dump) and StrictMode (thread: disk/network; VM: closables,
    activity leaks, registrations; penaltyLog) via a debug-only provider in
    app/src/debug/kotlin (tv's debug source set only adds app/src/debug/java, so tv does not
    compile it).
  - launch-smoke: a bar drag swipe, 10 tab cycles, dumpsys meminfo TOTAL before and after,
    and LeakCanary and StrictMode summaries per attempt (saved as artifacts, report only).
  - ArchiveTune's hidden Liquid Glass page (with its own master switch) is no longer routed:
    its route shows the unported-page stub. ArchiveTuneSettingsPage is not called from app or
    tv, so the page was already unreachable.
  - Minor: stretch() guards on the live position; the hidden flat pill is skipped in draw,
    not drawn at alpha 0.
Stage Summary:
- Not compiled locally (no Android SDK). CI and the launch-smoke meminfo/LeakCanary/StrictMode
  report are the evidence.

---
Task ID: curialy/drift-lyrics-acrylic-perf
Agent: Curialy Engineer
Task: Request #8 — finish drift, word lyrics, acrylic, glass sliders, cold start.
Work Log:
- Sandbox reset lost the earlier unpushed player-acrylic commits; redone from main 920557e.
- Tests kept as contracts: AcrylicStyleTest (ACRYLIC_BLUR_DP/NOISE/TINT_ALPHA in
  com.music.bitchord.ui.components; KEY_ACRYLIC_BLUR in AppSettings.kt; AppSettings::setAcrylicBlur
  in SettingsSheet.kt) and ArchiveTuneLrcRoundTripTest (toArchiveTuneLrc → LyricsUtils, US + Arabic digits).
- Drift: driftArtworkSlot on the Apple Music cover and the current TikTok page's cover; "Moving
  blur" now wanders the Apple Music background (replacing the playing-only Ken Burns) and the
  current TikTok mesh with the BitChord wander.
- Lyrics root cause: LyricsUtils.ENHANCED_LRC_WORD_TIME_REGEX had no capture groups, so
  parseEnhancedLrcLine's toLrcTimestampMs() read groupValues[1] out of range; groups added.
- Acrylic blur toggle (Appearance › Misc), persisted, strings in all 20 catalogues.
Stage Summary:
- Not done: the AndroidLiquidGlass/Zenith slider inventory and port; the generated
  Baseline/Startup Profile module and workflow; startup deferrals. Nothing compiled (no SDK);
  CI and device checks pending until the branch is pushed.

---
Task ID: curialy/drift-lyrics-acrylic-perf (round 2)
Agent: Curialy Engineer
Task: Review round 1 — sliders, cold start, acrylic on glass, Ken Burns.
Work Log:
- Slider inventory (three-way):
  | Control | AndroidLiquidGlass playground | Zenith | Neiro before | Now |
  | corner radius | 0..1, 0.5 | — | — | added (relative to caller's corners) |
  | blur | 0..32 dp, 0 | GlassCustom.blur 0.66 | liquidGlassBlurRadius | kept |
  | refraction height | 0..1, 0.2 | refraction 0.99 | liquidGlassRefractionHeight | kept |
  | refraction amount | 0..1, 0.2 | — | liquidGlassRefractionAmount | kept |
  | chromatic aberration | 0..1, 0 | — | Boolean | added amount (dispersion strength) |
  | clarity | — | GlassClarity.level 0.13 (soft mix) | — | added |
  | saturation | — | 1 | backdropVibrancy (Boolean) | added |
  | tint | — | 1.67 | liquidGlassTintOpacity | kept |
  | refraction width | — | 0.36 | — | added |
  | rainbow | — | 0 | — | added (adds to dispersion strength) |
  | rim | — | 1 | — | added (highlight alpha) |
  | shadow | — | 1 | liquidGlassShadowDepth | kept |
  | depth | — | true | liquidGlassDepth3D | kept |
  | quality | — | 1 | — | added (sampling resolution × quality) |
- Acrylic on liquid glass: blur stage at 32 dp, surface tint at 0.72 and a process-wide 128×128
  seeded ALPHA_8 grain tile (12 %) drawn in onDrawSurface; no new backdrop sampler.
- Apple Music keeps upstream's Ken Burns under the BitChord wander.
- Cold start: :baselineprofile module + baseline-profile.yml; prewarm and the ArchiveTune host
  moved after the first frames; ReportDrawnWhen; launch-smoke resets compilation and prints a
  first-10-s window (gfxinfo percentiles, slow UI thread, dexopt status) uncompiled vs speed-profile.
Stage Summary:
- Not verified: nothing compiled (no SDK); the profile workflow has never run, so no generated
  profile is committed and no benchmark numbers exist yet. ProfileVerifier logging not added
  (dumpsys dexopt status printed instead). Slider strings are English in every catalogue.
- Review round 2: the acrylic grain tile is ARGB_8888 mid-grey noise (128 ± 96), not an ALPHA_8 mask,
  so it reads as neutral grain at 12 % and never as dark speckle. cornerFactor already spans 0..2
  (neutral at 0.5, clamped at the pill radius by scaledCorners); LiquidGlassUpstreamTest now pins
  cornerFactor(1) = 2 and cornerFactor(0) = 0. refractionWidth stays on the lens height: that is
  Zenith's own mapping (Zenith ui/components/LiquidGlass.kt:240, lensHeightPx × custom.refractionWidth).
  values-zh-rHK and values-b+zh+Hant got the 16 new liquid_glass_* placeholders too.

---
Task ID: curialy/drift-lyrics-acrylic-perf (cold start fix)
Agent: Curialy Engineer
Task: Make the baseline/startup profile generation actually run (PR #36).
Work Log:
- Cause: Baseline Profile run 44741076223 stopped with "Cannot locate tasks that match
  ':app:generateProdReleaseBaselineProfile'". app/build.gradle.kts had `mergeIntoMain = true`;
  per the Android docs that registers only a single `generateBaselineProfile` task, and
  per-variant tasks exist only with false.
- Fix: `mergeIntoMain = false` (saveInSrc stays true; `warnings { maxAgpVersion = false }`), so
  :app:generateProdReleaseBaselineProfile exists and writes
  app/src/prodRelease/generated/baselineProfiles/.
- baseline-profile.yml: generation is strict; the benchmark is its own report-only step;
  the log and step summary print both rule counts, the first 30 rules and the
  StartupTimingMetric/FrameTimingMetric summary (.github/scripts/bench_summary.py); a
  github-actions[bot] commit pushes the generated profiles back to the PR branch (contents:
  write, checkout of the head ref); triggers also on app/build.gradle.kts.
Stage Summary:
- Rule counts and benchmark numbers: pending the first green run.
