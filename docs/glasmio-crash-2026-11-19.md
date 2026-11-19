# Glasmio sample: RenderThread crashes on button taps and scrolling (2026-11-19)

Source: the user's logcat, `attachments/19_11-19-24-36_030-83e566.txt` (realme
RMX3563, board k6895v1_64 = MediaTek Dimensity 8100 / MT6895, Android 16 / API 36,
Mali GPU, 120 Hz panel). The app is the Glasmio demo, `io.github.neoruaa.glasmio.sample`,
a minified release build installed at 19:24:36. The newest fork tag at that time was
v0.2.4 (0681a13, 19:02).

## What the log shows

Four process deaths in 22 s, every one a native SIGSEGV on the RenderThread, with no
Java exception before any of them:

| Line | Time | pid | Fault address |
|---|---|---|---|
| 294 | 19:24:39.775 | 795 | `0x38` |
| 737 | 19:24:49.016 | 1149 | `0x3f800038` |
| 1061 | 19:24:56.286 | 1202 | `0x420535fe42181737` |
| 1476 | 19:25:01.578 | 1247 | `0x2e64696f72646e99` |

```
294:  F libc : Fatal signal 11 (SIGSEGV), code 1 (SEGV_MAPERR), fault addr 0x38 in tid 860 (RenderThread), pid 795 (.glasmio.sample)
737:  F libc : Fatal signal 11 (SIGSEGV), code 1 (SEGV_MAPERR), fault addr 0x3f800038 in tid 1163 (RenderThread), pid 1149 (.glasmio.sample)
1061: F libc : Fatal signal 11 (SIGSEGV), code 1 (SEGV_MAPERR), fault addr 0x420535fe42181737 in tid 1216 (RenderThread), pid 1202 (.glasmio.sample)
1476: F libc : Fatal signal 11 (SIGSEGV), code 1 (SEGV_MAPERR), fault addr 0x2e64696f72646e99 in tid 1261 (RenderThread), pid 1247 (.glasmio.sample)
```

### Decoding the fault addresses

- `0x38`: a field at offset 0x38 read through a null pointer.
- `0x3f800038`: `0x3f800000` is the float `1.0f`, plus the same offset `0x38`. A pointer
  slot that now holds a float, dereferenced at the same field.
- `0x420535fe42181737`: two floats, `38.02` and `33.30`, which look like coordinates or sizes.
- `0x2e64696f72646e99`: the bytes `99 'n' 'd' 'r' 'o' 'i' 'd' '.'`, the text "android." with its first byte changed.

Each one is a pointer slot that now holds something other than a pointer: null, a float,
geometry, or string bytes. That is what a **use-after-free** looks like: the RenderThread
follows a pointer in a native object that has been freed and reused. The same offset
`0x38` in two different processes points at one code path and one structure.

### Why there is no backtrace

- `crash_dump64: ... Failed to initialize unwinder object: Failed to parse maps`
  (lines 315, 753, 1075, 1494). SELinux denies crash_dump writing its `/memfd:mf:/proc/<pid>/maps`
  copy (avc denials at 298/300), so no tombstone backtrace is written.
- LSPosed/Vector loads `jp.miruku.unitydisplaytweaker` into the sample (lines 529–548): it
  injects `libudt-native.so` and fails to hook (`Didn't find class com.unity3d.player.UnityPlayer`).
  It cannot be ruled out from this log and is worth excluding once, but it is loaded in every
  app on the phone, while these crashes follow the glass.

## Why the cache path runs on this phone and not on an emulator

- The sample never calls `GlasmioPerformance.configure`, so `DrawBackdropModifier` runs
  `ensureDetected`. `classify("MediaTek MT6895 …", 8–12 GB)` matches `mt689\d` in
  `PreviousFlagshipSoc` → **Balanced**, `frameRateCap = 60`.
- `GlassFrameCache.applies` holds when `cap < refresh - 1`: 60 < 119, so **every glass
  surface renders through GlassFrameCache**. That is a second `HardwareRenderer` that draws
  the glass layer into an `ImageReader` at 60 Hz, while the window composites the last image
  at 120 Hz.
- On a 60 Hz emulator the same tier gives 60 < 59 = false, so the cache never runs. That is why
  the harness forces `frameRateCap = 30`.

`GlassFrameCache` first shipped in 0.2.2. The fork's CHANGELOG already records two crashes
and an ANR fixed in this code within that afternoon (0.2.3, 0.2.4). Their cause was the
glass layer's render node losing its last parent while one renderer still drew it. The
user's crashes came after the latest of those fixes.

## Suspects (in order)

1. **Two renderers, one render node.** While the window shows the cached image, the
   off-screen renderer is the only parent of the glass layer's node. HWUI is not built for one
   node to be synced by two `CanvasContext`s. Display lists, the offscreen layer and the
   damage/layer-update queues can be freed by one context while the other still holds raw
   pointers. 0.2.3/0.2.4 patched individual cases (resize, hand-back). The class remains.
2. **Layer released on detach while the renderer can still reach it.** `onDetach` destroyed
   the renderer only on the next window frame commit, but handed the `GraphicsLayer` back to
   Compose immediately, which discards its display lists.
3. **ImageReader closed under the image thread.** Retired readers were closed on the main
   thread, while `GlasmioGlassFrames` could still be acquiring from them.

Neiro pins Glasmio **v0.2.1**, which has no `GlassFrameCache`, so Neiro does not ship this
path today. Neiro's own 2026-11-19 SIGTRAP was a different bug: glass sampling the layer
that records it. That is fixed on `feat/glasmio-fork`.

## The fix (third_party/glasmio-patches/fix, against UPSTREAM_SHA 0681a13)

- `0001`: **the off-screen renderer is opt-in.** `GlasmioPerformanceConfig(cachedGlass = true)`
  / `GlasmioPerformance.cachedGlass`, off by default. Without it, `frameRateCap` gates the glass
  clocks only, as in 0.2.1, so tier detection alone never puts glass in a second renderer.
  This removes suspect 1 for every host that does not opt in. It also removes 2 and 3, which
  only exist in the cache. Tests: `GlassFrameCacheOptInTest`, `GlasmioPerformanceTest.cachedGlassIsOptIn`.
- `0002`: **defence for hosts that opt in.** `GlassFrameCache.release(onDestroyed)`: the glass
  layer goes back to Compose only after the cache's renderer is destroyed, which addresses suspect 2.
  Retired readers are closed on `GlasmioGlassFrames`, behind any callback already queued there,
  which addresses suspect 3.

This is the conservative path. It is chosen without a symbolized trace, because the phone
writes none. The CI harness below is what confirms the cause and the fix.

## Reproducing in CI

`.github/workflows/glasmio-sample-smoke.yml` clones the fork at `UPSTREAM_SHA` (or
`upstream_ref`) and applies `harness/` (GWP-ASan always on, a `glasmio_cap` /
`glasmio_route` launch extra). Unless `apply_fix` is false, it also applies `fix/` and
`harness-fixed/` (`glasmio_cached`). It builds the debug sample and, on API 34/35/36, opens
every route at cap 0 and cap 30. On each route it taps every control and scrolls every
scrollable 6× each way, then runs three seeded monkey passes. On any death it prints the
tombstone (with symbols and the GWP-ASan report, since the sample is not minified) and
exit-info, and fails.

Runs:

| Run | Build | Result |
|---|---|---|
| _pending_ | 0681a13, `apply_fix=false` | expected to fail on the cap-30 pass |
| _pending_ | v0.2.1 (`upstream_ref=v0.2.1`, `apply_fix=false`) | baseline: no GlassFrameCache |
| _pending_ | 0681a13 + fix | expected to pass |

If SwiftShader does not reproduce a Mali-only fault, the table says so, and the fix rests on
the opt-in removal of the second renderer.
