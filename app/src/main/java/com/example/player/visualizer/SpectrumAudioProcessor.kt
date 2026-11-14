package com.example.player.visualizer

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Pass-through ExoPlayer AudioProcessor. The audio is not modified at all; we
 * only read the PCM that is about to be played, run a windowed FFT ~60 times a
 * second and publish 32 log-spaced bands + bass level + beat (onset) pulse to
 * [AudioSpectrumHub].
 */
@OptIn(UnstableApi::class)
class SpectrumAudioProcessor : BaseAudioProcessor() {

    private var sampleRate = 44100
    private var channels = 2
    private var isFloat = false
    private var hop = 735

    private val ring = FloatArray(N)
    private var writePos = 0
    private var filled = 0
    private var sinceHop = 0

    private val re = FloatArray(N)
    private val im = FloatArray(N)
    private val mag = FloatArray(N / 2 + 1)
    private val window = FloatArray(N) { (0.5 - 0.5 * cos(2.0 * PI * it / N)).toFloat() }
    private val cosT = FloatArray(N / 2) { cos(2.0 * PI * it / N).toFloat() }
    private val sinT = FloatArray(N / 2) { sin(2.0 * PI * it / N).toFloat() }
    private val bitRev = IntArray(N).also { table ->
        val bits = Integer.numberOfTrailingZeros(N)
        for (i in 0 until N) table[i] = Integer.reverse(i) ushr (32 - bits)
    }

    private val bandLoBin = FloatArray(AudioSpectrumHub.BANDS)
    private val bandHiBin = FloatArray(AudioSpectrumHub.BANDS)
    private val bandTiltDb = FloatArray(AudioSpectrumHub.BANDS)
    private val bandDb = FloatArray(AudioSpectrumHub.BANDS)
    private val bands = FloatArray(AudioSpectrumHub.BANDS)

    private var refDb = MIN_REF_DB
    private var bassSlow = 0f

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        val encoding = inputAudioFormat.encoding
        if ((encoding != C.ENCODING_PCM_16BIT && encoding != C.ENCODING_PCM_FLOAT) ||
            inputAudioFormat.channelCount <= 0 || inputAudioFormat.sampleRate <= 0
        ) {
            // Inactive: audio simply bypasses this processor.
            return AudioProcessor.AudioFormat.NOT_SET
        }
        sampleRate = inputAudioFormat.sampleRate
        channels = inputAudioFormat.channelCount
        isFloat = encoding == C.ENCODING_PCM_FLOAT
        hop = (sampleRate / HOPS_PER_SECOND).coerceAtLeast(256)
        configureBands()
        ring.fill(0f)
        writePos = 0
        filled = 0
        sinceHop = 0
        refDb = MIN_REF_DB
        bassSlow = 0f
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return

        analyze(inputBuffer.duplicate().order(ByteOrder.nativeOrder()))

        val out = replaceOutputBuffer(remaining)
        out.put(inputBuffer)
        out.flip()
    }

    private val shortChunk = ShortArray(CHUNK_SAMPLES)
    private val floatChunk = FloatArray(CHUNK_SAMPLES)

    private fun analyze(buf: ByteBuffer) {
        val bytesPerSample = if (isFloat) 4 else 2
        var remainingFrames = buf.remaining() / (bytesPerSample * channels)
        val startUs = AudioSpectrumHub.bufferStartUs
        val rate = sampleRate.toLong()
        val inv = 1f / channels
        val chunkFrames = (CHUNK_SAMPLES / channels).coerceAtLeast(1)
        val shorts = if (isFloat) null else buf.asShortBuffer()
        val floats = if (isFloat) buf.asFloatBuffer() else null
        var frameIndex = 0

        // Bulk-read PCM in chunks (much cheaper than one ByteBuffer call per sample).
        while (remainingFrames > 0) {
            val n = min(chunkFrames, remainingFrames)
            val count = n * channels
            if (isFloat) floats!!.get(floatChunk, 0, count) else shorts!!.get(shortChunk, 0, count)

            var idx = 0
            for (f in 0 until n) {
                var sum = 0f
                for (c in 0 until channels) {
                    sum += if (isFloat) floatChunk[idx] else shortChunk[idx] * INV_32768
                    idx++
                }
                ring[writePos] = sum * inv
                writePos = (writePos + 1) and (N - 1)
                if (filled < N) filled++
                frameIndex++
                sinceHop++

                if (sinceHop >= hop && filled >= N) {
                    sinceHop = 0
                    computeFrame()
                    // Stamp with the moment this window's *centre* is heard.
                    val endUs = startUs + frameIndex * 1_000_000L / rate
                    val timeUs = endUs - (N / 2) * 1_000_000L / rate
                    AudioSpectrumHub.pushFrame(timeUs, bands, lastBass, lastBeat)
                }
            }
            remainingFrames -= n
        }
    }

    private var lastBass = 0f
    private var lastBeat = 0f

    private fun computeFrame() {
        // Oldest sample sits at writePos in the circular buffer.
        for (i in 0 until N) {
            re[i] = ring[(writePos + i) and (N - 1)] * window[i]
            im[i] = 0f
        }
        fft()

        // 1.0 == full-scale sine.
        val norm = 4f / N
        for (k in 0..N / 2) mag[k] = sqrt(re[k] * re[k] + im[k] * im[k]) * norm

        var peakDb = -140f
        val maxBin = N / 2
        for (b in 0 until AudioSpectrumHub.BANDS) {
            val lo = bandLoBin[b]
            val hi = bandHiBin[b]
            val centre = sqrt(lo * hi)
            val a = min(centre.toInt(), maxBin - 1)
            val frac = centre - a
            var m = mag[a] * (1f - frac) + mag[a + 1] * frac
            var s = ceil(lo).toInt()
            val e = min(floor(hi).toInt(), maxBin)
            while (s <= e) {
                if (mag[s] > m) m = mag[s]
                s++
            }
            val db = 20f * log10(m + 1e-7f) + bandTiltDb[b]
            bandDb[b] = db
            if (db > peakDb) peakDb = db
        }

        // Auto gain: slowly-decaying reference so quiet and loud songs both
        // use the full visual range.
        refDb = max(max(refDb - REF_DECAY_DB, peakDb), MIN_REF_DB)
        val floorDb = refDb - RANGE_DB
        var bassSum = 0f
        for (b in 0 until AudioSpectrumHub.BANDS) {
            val v = ((bandDb[b] - floorDb) / RANGE_DB).coerceIn(0f, 1f).pow(1.35f)
            bands[b] = v
            if (b < BASS_BANDS) bassSum += v
        }
        val bass = bassSum / BASS_BANDS

        // Beat = positive jump of bass energy over its slow average.
        val onset = (bass - bassSlow).coerceAtLeast(0f)
        bassSlow += (bass - bassSlow) * 0.10f
        lastBass = bass
        lastBeat = (onset * 3.6f).coerceIn(0f, 1f)
    }

    private fun configureBands() {
        val binHz = sampleRate.toFloat() / N
        val nyquist = sampleRate / 2f
        val loF = 35f
        val hiF = min(16000f, nyquist * 0.92f)
        val ratio = hiF / loF
        val count = AudioSpectrumHub.BANDS
        for (b in 0 until count) {
            val f0 = loF * ratio.pow(b.toFloat() / count)
            val f1 = loF * ratio.pow((b + 1).toFloat() / count)
            bandLoBin[b] = f0 / binHz
            bandHiBin[b] = f1 / binHz
            val centreF = sqrt(f0 * f1)
            // Music spectra fall ~3 dB/octave; compensate so highs still move.
            bandTiltDb[b] = 3f * (ln(max(centreF / 250f, 1f)) / ln(2f))
        }
    }

    private fun fft() {
        for (i in 0 until N) {
            val j = bitRev[i]
            if (j > i) {
                val tr = re[i]; re[i] = re[j]; re[j] = tr
                val ti = im[i]; im[i] = im[j]; im[j] = ti
            }
        }
        var len = 2
        while (len <= N) {
            val half = len / 2
            val step = N / len
            var i = 0
            while (i < N) {
                var k = 0
                for (j in 0 until half) {
                    val wr = cosT[k]
                    val wi = -sinT[k]
                    val a = i + j
                    val b = a + half
                    val tr = re[b] * wr - im[b] * wi
                    val ti = re[b] * wi + im[b] * wr
                    re[b] = re[a] - tr
                    im[b] = im[a] - ti
                    re[a] += tr
                    im[a] += ti
                    k += step
                }
                i += len
            }
            len = len shl 1
        }
    }

    private companion object {
        const val N = 2048 // FFT size (power of two)
        const val HOPS_PER_SECOND = 50
        const val CHUNK_SAMPLES = 4096
        const val INV_32768 = 1f / 32768f
        const val BASS_BANDS = 6
        const val RANGE_DB = 46f
        const val MIN_REF_DB = -45f
        const val REF_DECAY_DB = 0.03f
    }
}
