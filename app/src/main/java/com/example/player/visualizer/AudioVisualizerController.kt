package com.example.player.visualizer

import android.media.audiofx.Visualizer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Reads FFT data from VYBE's own ExoPlayer audio session.
 * Visualization only: no microphone recording, encoding, or audio storage.
 */
class AudioVisualizerController {
    private val _spectrum = MutableStateFlow(FloatArray(BAND_COUNT))
    val spectrum: StateFlow<FloatArray> = _spectrum.asStateFlow()

    private var visualizer: Visualizer? = null
    private var activeSessionId: Int = 0
    private val smoothedBands = FloatArray(BAND_COUNT)

    @Synchronized
    fun start(audioSessionId: Int) {
        if (audioSessionId <= 0) return
        if (activeSessionId == audioSessionId && visualizer != null) return

        stop()
        try {
            val capture = Visualizer(audioSessionId)
            val range = Visualizer.getCaptureSizeRange()
            capture.captureSize = range[1].coerceAtMost(1024)
            capture.setDataCaptureListener(
                object : Visualizer.OnDataCaptureListener {
                    override fun onWaveFormDataCapture(
                        visualizer: Visualizer?, waveform: ByteArray?, samplingRate: Int
                    ) = Unit

                    override fun onFftDataCapture(
                        visualizer: Visualizer?, fft: ByteArray?, samplingRate: Int
                    ) {
                        if (fft == null || fft.size < 8) return
                        val targets = extractLogSpectrum(fft)
                        val output = synchronized(smoothedBands) {
                            for (i in targets.indices) {
                                val old = smoothedBands[i]
                                val target = targets[i]
                                // Snappy attack, naturally decaying release (callback is ~20 Hz).
                                val coefficient = if (target > old) 0.62f else 0.24f
                                smoothedBands[i] = old + (target - old) * coefficient
                            }
                            smoothedBands.copyOf()
                        }
                        _spectrum.value = output
                    }
                },
                Visualizer.getMaxCaptureRate().coerceAtLeast(8000),
                false,
                true
            )
            capture.enabled = true
            visualizer = capture
            activeSessionId = audioSessionId
        } catch (_: Throwable) {
            stop()
        }
    }

    private fun extractLogSpectrum(fft: ByteArray): FloatArray {
        val result = FloatArray(BAND_COUNT)
        val complexBinCount = fft.size / 2
        val maxBin = (complexBinCount - 1).coerceAtLeast(2)
        val logMax = ln(maxBin.toFloat())

        for (band in 0 until BAND_COUNT) {
            val startFraction = band.toFloat() / BAND_COUNT
            val endFraction = (band + 1).toFloat() / BAND_COUNT
            val startBin = floor(exp(logMax * startFraction)).toInt().coerceIn(1, (maxBin - 1).coerceAtLeast(1))
            val endBin = floor(exp(logMax * endFraction)).toInt().coerceAtLeast(startBin + 1).coerceAtMost(maxBin)

            var sumSquares = 0f
            var peak = 0f
            var count = 0
            for (bin in startBin..endBin) {
                val real = fft[bin * 2].toInt().toFloat()
                val imaginary = fft[bin * 2 + 1].toInt().toFloat()
                val magnitude = sqrt(real * real + imaginary * imaginary)
                sumSquares += magnitude * magnitude
                peak = max(peak, magnitude)
                count++
            }

            val rms = if (count > 0) sqrt(sumSquares / count) else 0f
            val magnitude = max(peak * 0.72f, rms * 0.96f)
            if (magnitude < 2.5f) {
                result[band] = 0f
            } else {
                // Logarithmic loudness mapping avoids weak, flat-looking high bins.
                val decibels = 20f * kotlin.math.log10((magnitude / 128f).coerceAtLeast(0.0001f))
                val normalized = ((decibels + 48f) / 48f).coerceIn(0f, 1f)
                result[band] = normalized.pow(1.55f)
            }
        }
        return result
    }

    @Synchronized
    fun stop() {
        try {
            visualizer?.enabled = false
            visualizer?.release()
        } catch (_: Throwable) {
        }
        visualizer = null
        activeSessionId = 0
        synchronized(smoothedBands) {
            smoothedBands.fill(0f)
        }
        _spectrum.value = FloatArray(BAND_COUNT)
    }

    fun release() = stop()

    private companion object {
        const val BAND_COUNT = 64
    }
}
