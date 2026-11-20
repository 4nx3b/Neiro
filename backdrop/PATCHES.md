# Local patches on AndroidLiquidGlass @ 0ed8b08

`:backdrop` is guozhiqiang123/AndroidLiquidGlass's backdrop library at the
commit in `UPSTREAM_SHA`. Package `com.kyant.backdrop`. Every local change
is listed here. None of them changes a glass parameter, a shader source or
the order of effects.

1. **samplingScale** — `drawBackdrop` / `drawPlainBackdrop` take
   `samplingScale: (Int, Int) -> Float` (see the header of `build.gradle.kts`).
2. **Lens `chromaticStrength`** — `effects/Lens.kt` takes
   `chromaticStrength: Float = 1f` and multiplies the dispersion uniform by it,
   as in Zenith's vendored copy (rezapranata089-bit/Zenith@01e2ab1), which
   Zenith's selection pill (`ui/components/floatingtabbar/GlassSelectionPill.kt`)
   calls. With the default `1f` the output is identical to upstream.
3. **RuntimeShaderPool** — `RuntimeShaderPool.kt` keeps a process-wide pool of
   idle `RuntimeShader` instances per AGSL source. `RuntimeShaderCacheImpl`
   (owned by each `BackdropEffectScope` and `HighlightModifier`) takes its
   instance from the pool on the first use of a key, and returns it in
   `clear()`, which runs only from the node's `onDetach` after its graphics
   layer is released. Every node still owns its instance exclusively while
   attached, so uniforms are never shared between surfaces.
   `prewarmBackdropShaders()` builds the lens, dispersion and highlight
   shaders off the main thread at app start (no-op below API 33). This moves
   the SkSL parse/compile off the first frame of each glass surface; it does
   not change what is drawn.
4. **LayerBackdrop `recordedLayerCoordinates`** — the selection pill's rim
   (`drawBarRim`) reads the bar layer's own coordinates to place its halo,
   which upstream keeps internal; `LayerBackdrop` gains a read-only
   `recordedLayerCoordinates` returning the same value. Nothing can write it
   from outside the module.
