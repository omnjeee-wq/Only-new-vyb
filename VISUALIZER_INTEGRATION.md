# VYBE visualizer integration

## What changed
- Added `player/visualizer/AudioVisualizerController.kt`, a visualization-only FFT reader attached to VYBE's active ExoPlayer audio session.
- Added `ui/visualizer/VybeVisualizers.kt` with four independent Compose Canvas renderers:
  - `CIRCULAR` — A, circular frequency spectrum
  - `FLUID_WAVE` — B, flowing waveform
  - `SPECTRUM_BARS` — C, frequency bars
  - `PARTICLE_BLOB` — D, audio-reactive particle/blob
- Added `VybeVisualizerRegistry` as the central style registry. `defaultStyle` is `PARTICLE_BLOB`.
- Replaced the center navigation button's decorative equalizer animation with the live particle/blob renderer.
- Added the Android `RECORD_AUDIO` permission because Android's `Visualizer` API requires it for a player-specific audio session. The app requests it only when playback starts and the visualizer is about to be used.

## Use another style later
Call `VybeVisualizer(style = VybeVisualizerStyle.CIRCULAR, spectrum = audioSpectrum, modifier = ...)`.
The other options are `FLUID_WAVE`, `SPECTRUM_BARS`, and `PARTICLE_BLOB`.

## Architecture boundaries
No API service, repository, Supabase, database, recommendation engine, queue, Media3 playback commands, or playback service implementation was changed. The playback service is only read for its existing ExoPlayer audio session ID.

## Permission and device notes
- This does not use the microphone as an input source and does not save audio. Android nevertheless gates the platform Visualizer API behind `RECORD_AUDIO`; the runtime prompt is therefore required on supported Android versions.
- If permission is denied or the device does not support the Visualizer effect, the blob remains in its idle shape and playback continues normally.
- Android's Visualizer API exposes partial, low-quality FFT data intended for visualization, not recording.
- Build verification could not be run from this archive because it does not include the Gradle wrapper script (`gradlew`). Open the project in Android Studio and sync/build with the configured Gradle version.
