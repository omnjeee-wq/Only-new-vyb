package com.example.player.visualizer

import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.ForwardingAudioSink
import java.nio.ByteBuffer

/**
 * Thin wrapper around DefaultAudioSink. It does not touch the audio; it only
 *  1) tells the hub the media time of each buffer entering the sink, and
 *  2) forwards the sink's playback position (what is audible right now),
 * so spectrum frames can be shown exactly when their sound is heard.
 */
@OptIn(UnstableApi::class)
class SpectrumTapAudioSink(delegate: AudioSink) : ForwardingAudioSink(delegate) {

    override fun handleBuffer(
        buffer: ByteBuffer,
        presentationTimeUs: Long,
        encodedAccessUnitCount: Int
    ): Boolean {
        AudioSpectrumHub.bufferStartUs = presentationTimeUs
        return super.handleBuffer(buffer, presentationTimeUs, encodedAccessUnitCount)
    }

    override fun getCurrentPositionUs(sourceEnded: Boolean): Long {
        val position = super.getCurrentPositionUs(sourceEnded)
        if (position != AudioSink.CURRENT_POSITION_NOT_SET) {
            AudioSpectrumHub.reportSinkPosition(position)
        }
        return position
    }

    override fun flush() {
        AudioSpectrumHub.clear()
        super.flush()
    }

    override fun reset() {
        AudioSpectrumHub.clear()
        super.reset()
    }
}
