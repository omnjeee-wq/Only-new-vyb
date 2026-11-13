package com.example.player.visualizer

import androidx.media3.common.C

/**
 * Shared, thread-safe bridge between the audio thread (which produces spectrum
 * frames from the *actual decoded PCM*) and the UI (which draws them).
 *
 * Every frame is stamped with the media time at which its audio is HEARD, and
 * the UI asks for "the frame that is audible right now" using the playback
 * position reported by the audio sink. That is what keeps the visuals locked to
 * the beat even though ExoPlayer buffers a few hundred ms of audio ahead.
 *
 * No microphone and no android.media.audiofx.Visualizer are involved, so no
 * RECORD_AUDIO permission is required.
 */
object AudioSpectrumHub {
    const val BANDS = 32

    private const val RING = 256
    private const val STALE_US = 250_000L
    private const val MAX_EXTRAPOLATION_US = 120_000L

    /** One snapshot of the music: 0..1 values. */
    class Frame {
        val bands = FloatArray(BANDS)
        var bass = 0f
        var beat = 0f
    }

    private val lock = Any()
    private val times = LongArray(RING)
    private val bandData = Array(RING) { FloatArray(BANDS) }
    private val bassData = FloatArray(RING)
    private val beatData = FloatArray(RING)
    private var head = 0 // next write slot
    private var count = 0

    /** Media time (us) of the first sample of the buffer currently entering the sink. */
    @Volatile
    internal var bufferStartUs: Long = 0L

    @Volatile
    private var sinkPositionUs: Long = C.TIME_UNSET

    @Volatile
    private var sinkPositionNanos: Long = 0L

    internal fun pushFrame(timeUs: Long, bands: FloatArray, bass: Float, beat: Float) {
        synchronized(lock) {
            System.arraycopy(bands, 0, bandData[head], 0, BANDS)
            times[head] = timeUs
            bassData[head] = bass
            beatData[head] = beat
            head = (head + 1) % RING
            if (count < RING) count++
        }
    }

    /** Called on seek / track change / release: old frames belong to a different timeline. */
    internal fun clear() {
        synchronized(lock) {
            count = 0
            head = 0
        }
    }

    internal fun reportSinkPosition(positionUs: Long) {
        sinkPositionNanos = System.nanoTime()
        sinkPositionUs = positionUs
    }

    /**
     * Fills [out] with the frame that is audible right now.
     * Returns false when nothing is playing / no fresh data (treat as silence).
     */
    fun sample(out: Frame): Boolean {
        val position = sinkPositionUs
        if (position == C.TIME_UNSET) return false
        val sinceReportUs = (System.nanoTime() - sinkPositionNanos) / 1000L
        val nowUs = position + sinceReportUs.coerceIn(0L, MAX_EXTRAPOLATION_US)

        synchronized(lock) {
            var found = -1
            for (k in 0 until count) {
                val i = (head - 1 - k + RING * 2) % RING
                if (times[i] <= nowUs) {
                    found = i
                    break
                }
            }
            if (found < 0 || nowUs - times[found] > STALE_US) return false
            System.arraycopy(bandData[found], 0, out.bands, 0, BANDS)
            out.bass = bassData[found]
            out.beat = beatData[found]
        }
        return true
    }
}
