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

    /** Debug switch: false = normal ExoPlayer audio path, orb only idles. Use to A/B test lag. */
    const val AUDIO_TAP_ENABLED = true

    private const val RING = 256
    private const val STALE_US = 250_000L
    private const val SAFETY_SLOTS = 8
    private const val MAX_EXTRAPOLATION_US = 120_000L

    /** One snapshot of the music: 0..1 values. */
    class Frame {
        val bands = FloatArray(BANDS)
        var bass = 0f
        var beat = 0f
    }

    // Lock-free single-writer ring (writer = audio thread, reader = UI thread).
    // The audio thread must NEVER block on a lock the UI thread might hold: under CPU
    // load the UI thread can be descheduled while holding it, which would starve the
    // audio buffer and make the music stutter.
    private val times = LongArray(RING)
    private val bandData = Array(RING) { FloatArray(BANDS) }
    private val bassData = FloatArray(RING)
    private val beatData = FloatArray(RING)

    @Volatile
    private var writeIndex = 0L // total frames ever written (publishes slots to the reader)

    @Volatile
    private var validFrom = 0L // frames before this index belong to an old timeline

    /** Media time (us) of the first sample of the buffer currently entering the sink. */
    @Volatile
    internal var bufferStartUs: Long = 0L

    @Volatile
    private var sinkPositionUs: Long = C.TIME_UNSET

    @Volatile
    private var sinkPositionNanos: Long = 0L

    internal fun pushFrame(timeUs: Long, bands: FloatArray, bass: Float, beat: Float) {
        val index = writeIndex
        val slot = (index % RING).toInt()
        System.arraycopy(bands, 0, bandData[slot], 0, BANDS)
        times[slot] = timeUs
        bassData[slot] = bass
        beatData[slot] = beat
        writeIndex = index + 1 // volatile write publishes the slot
    }

    /** Called on seek / track change / release: old frames belong to a different timeline. */
    internal fun clear() {
        validFrom = writeIndex
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

        val end = writeIndex // volatile read: everything below `end` is fully written
        val oldest = maxOf(validFrom, end - RING + SAFETY_SLOTS)
        var i = end - 1
        while (i >= oldest) {
            val slot = (i % RING).toInt()
            if (times[slot] <= nowUs) {
                if (nowUs - times[slot] > STALE_US) return false
                System.arraycopy(bandData[slot], 0, out.bands, 0, BANDS)
                out.bass = bassData[slot]
                out.beat = beatData[slot]
                // Make sure the writer did not lap this slot while we were copying.
                return writeIndex - i < RING - 2
            }
            i--
        }
        return false
    }
}
