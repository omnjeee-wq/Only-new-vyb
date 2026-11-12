package com.example.player.visualizer

import android.media.audiofx.Visualizer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Reads FFT data from VYBE's own ExoPlayer audio session.
 * This is visualization-only capture; it does not record or store audio.
 */
class AudioVisualizerController {
    private val _spectrum = MutableStateFlow(FloatArray(64))
    val spectrum: StateFlow<FloatArray> = _spectrum.asStateFlow()
    private var visualizer: Visualizer? = null

    @Synchronized
    fun start(audioSessionId: Int) {
        if (audioSessionId <= 0) return
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
                        if (fft == null || fft.size < 4) return
                        val bins = FloatArray(64)
                        val usable = fft.size / 2
                        for (i in bins.indices) {
                            val start = (1 + i * (usable - 1) / bins.size).coerceAtLeast(1)
                            val end = (1 + (i + 1) * (usable - 1) / bins.size).coerceAtLeast(start + 1)
                            var sum = 0f
                            var count = 0
                            for (bin in start until end.coerceAtMost(usable)) {
                                val real = fft[2 * bin].toInt()
                                val imag = fft[2 * bin + 1].toInt()
                                val magnitude = kotlin.math.sqrt((real * real + imag * imag).toFloat())
                                sum += magnitude
                                count++
                            }
                            bins[i] = if (count > 0) (sum / count / 48f).coerceIn(0f, 1f) else 0f
                        }
                        _spectrum.value = bins
                    }
                },
                (Visualizer.getMaxCaptureRate() / 2).coerceAtLeast(8000),
                false,
                true
            )
            capture.enabled = true
            visualizer = capture
        } catch (_: Throwable) {
            stop()
        }
    }

    @Synchronized
    fun stop() {
        try {
            visualizer?.enabled = false
            visualizer?.release()
        } catch (_: Throwable) {
        }
        visualizer = null
        _spectrum.value = FloatArray(64)
    }

    fun release() = stop()
}
