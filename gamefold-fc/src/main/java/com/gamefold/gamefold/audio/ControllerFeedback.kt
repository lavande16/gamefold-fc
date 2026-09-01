package com.gamefold.gamefold.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import com.gamefold.gamefold.R
import com.gamefold.gamefold.emulation.NesButton
import java.util.concurrent.ConcurrentHashMap

/** Short tactile-style sounds for the FC controller surface. */
class ControllerFeedback(context: Context) {
    private val loadedIds = ConcurrentHashMap.newKeySet<Int>()
    private val soundPool = SoundPool.Builder()
        .setMaxStreams(6)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
        )
        .build()
    private val dpadId: Int
    private val auxiliaryId: Int

    init {
        soundPool.setOnLoadCompleteListener { _, sampleId, status ->
            if (status == 0) loadedIds += sampleId
        }
        dpadId = soundPool.load(context, R.raw.controller_dpad_tick, 1)
        auxiliaryId = soundPool.load(context, R.raw.controller_aux_click, 1)
    }

    fun play(button: NesButton) {
        if (button == NesButton.Select || button == NesButton.Start) {
            play(auxiliaryId, 0.06f)
        } else {
            play(dpadId, 0.08f)
        }
    }

    fun auxiliary() = play(auxiliaryId, 0.06f)

    fun release() {
        soundPool.release()
        loadedIds.clear()
    }

    private fun play(sampleId: Int, volume: Float) {
        if (sampleId !in loadedIds) return
        val amplified = (volume * 3.2f).coerceAtMost(1f)
        soundPool.play(sampleId, amplified, amplified, 1, 0, 1f)
    }
}
