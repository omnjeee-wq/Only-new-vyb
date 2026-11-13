# VYBE nav-bar visualizer (v2)

## What changed
- **No microphone permission anymore.** `RECORD_AUDIO` removed from the manifest and the runtime permission prompt removed from `MainAppScaffold`.
  The old `android.media.audiofx.Visualizer` API (which needs that permission and often returns near-flat data) is gone;
  `AudioVisualizerController.kt` is deleted.
- **New real-audio backend** (`player/visualizer/`):
  - `SpectrumAudioProcessor` – ExoPlayer `AudioProcessor` that passes audio through untouched, reads the PCM that is actually
    being played, runs a 2048-pt Hann-windowed FFT 60x/second and outputs 32 log-spaced bands (35 Hz–16 kHz, treble tilt-compensated,
    auto-gain), a bass level and a **beat/onset pulse**.
  - `SpectrumTapAudioSink` – wraps `DefaultAudioSink`; reports media time of every buffer and the sink's playback position.
  - `AudioSpectrumHub` – thread-safe ring buffer. The UI asks for "the frame audible right now", so visuals stay locked to the
    beat even though ExoPlayer buffers audio ahead (also correct after pause / seek / track change).
  - `VybePlaybackService` builds ExoPlayer with this audio sink (`buildAudioSink` override).
- **New frontend** (`ui/visualizer/PremiumNavVisualizer.kt`): dark glass lens with glowing rim, 4 layered translucent
  "liquid membranes" (blue → violet → magenta sweep gradients) that ripple per frequency band, swell + glow on every beat,
  and 7 equalizer bars (bass in the centre). Stops redrawing when paused and fully calm (saves battery).
- `FloatingBottomBar` no longer takes `audioSpectrum`; the orb reads the hub itself.

## Tuning knobs
- Sensitivity: `RANGE_DB` (bigger = more sensitive to quiet bands), `REF_DECAY_DB` in `SpectrumAudioProcessor`.
- Beat strength: the `3.6f` multiplier in `computeFrame()`.
- Wave size / look: `LAYERS`, `BAR_BASE`, `BAR_GAIN` in `PremiumNavVisualizer`.
- Smoothing: attack `0.030f` / release `0.150f` in `OrbState.step`.

## Notes
- `VybeVisualizers.kt` (CIRCULAR / FLUID_WAVE / SPECTRUM_BARS / PARTICLE_BLOB) is untouched and still takes a `FloatArray`;
  feed it `AudioSpectrumHub.sample(frame)` -> `frame.bands` if you ever want those styles.
- Visualizer is driven by pre-effect PCM, so the Equalizer setting and volume do not change what it shows.
