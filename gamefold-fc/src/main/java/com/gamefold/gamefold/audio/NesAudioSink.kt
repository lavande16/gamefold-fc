package com.gamefold.gamefold.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack

class NesAudioSink {
    private var audioTrack: AudioTrack? = null
    private var sampleRate: Int = 0

    fun start(requestedSampleRate: Int) {
        val nextSampleRate = requestedSampleRate.coerceAtLeast(8000)
        if (audioTrack != null && sampleRate == nextSampleRate) {
            return
        }

        stop()
        sampleRate = nextSampleRate

        val minBufferSize = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_STEREO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        val bufferSize = maxOf(minBufferSize, sampleRate / 10 * 4)

        audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .build(),
            )
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setBufferSizeInBytes(bufferSize)
            .build()
            .also { it.play() }
    }

    fun write(samples: ShortArray) {
        if (samples.isEmpty()) return
        val track = audioTrack ?: return
        var offset = 0
        while (offset < samples.size) {
            val written = track.write(
                samples,
                offset,
                samples.size - offset,
                AudioTrack.WRITE_BLOCKING,
            )
            if (written <= 0) return
            offset += written
        }
    }

    fun stop() {
        audioTrack?.run {
            pause()
            flush()
            release()
        }
        audioTrack = null
        sampleRate = 0
    }
}
