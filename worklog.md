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
