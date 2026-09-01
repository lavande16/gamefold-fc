package com.gamefold.gamefold.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.PI
import kotlin.math.sin

class MenuMoveSound {
    private val lock = Any()
    private var audioTrack: AudioTrack? = createAudioTrack()

    fun play() {
        synchronized(lock) {
            val track = audioTrack ?: createAudioTrack().also { audioTrack = it }
            runCatching {
                track.pause()
                track.setPlaybackHeadPosition(0)
                track.play()
            }
        }
    }

    fun release() {
        synchronized(lock) {
            audioTrack?.release()
            audioTrack = null
        }
    }

    private fun createAudioTrack(): AudioTrack {
        val samples = createMoveSamples()
        return AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(SampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .build(),
            )
            .setTransferMode(AudioTrack.MODE_STATIC)
            .setBufferSizeInBytes(samples.size * ShortBytes)
            .build()
            .also { it.write(samples, 0, samples.size) }
    }

    private fun createMoveSamples(): ShortArray {
        val sampleCount = SampleRate * DurationMs / 1000
        return ShortArray(sampleCount) { index ->
            val t = index.toFloat() / SampleRate
            val envelope = 1f - index.toFloat() / sampleCount
            val pulse = if (sin(2.0 * PI * FrequencyHz * t) >= 0.0) 1f else -1f
            (pulse * MaxAmplitude * envelope).toInt().toShort()
        }
    }

    private companion object {
        const val SampleRate = 48_000
        const val DurationMs = 55
        const val FrequencyHz = 1_180
        const val MaxAmplitude = 12_000
        const val ShortBytes = 2
    }
}
