package com.gamefold.gamefold.emulation

class NativeNesEmulator {
    private val frameBuffer = IntArray(MaxFramePixels)
    private val audioBuffer = ShortArray(MaxAudioSamples)
    private var frameSequence = 0L

    fun initialize(systemDir: String, saveDir: String) {
        nativeInitialize(systemDir, saveDir)
    }

    fun loadRom(path: String): Boolean = nativeLoadRom(path)

    fun setInputMask(mask: Int) {
        nativeSetInputMask(mask)
    }

    fun runFrame(): NesFrame? {
        val packedSize = nativeRunFrame(frameBuffer)
        if (packedSize == 0) return null

        val width = packedSize ushr 16
        val height = packedSize and 0xFFFF
        if (width <= 0 || height <= 0 || width * height > frameBuffer.size) return null

        return NesFrame(
            pixels = frameBuffer.copyOf(width * height),
            width = width,
            height = height,
            sequence = frameSequence++,
        )
    }

    fun drainAudio(): ShortArray {
        val sampleCount = nativeDrainAudio(audioBuffer)
        if (sampleCount <= 0) return EmptyAudio
        return audioBuffer.copyOf(sampleCount)
    }

    fun getSampleRate(): Int = nativeGetSampleRate().coerceAtLeast(8000)

    fun getFrameRate(): Double = nativeGetFrameRate().coerceIn(1.0, 240.0)

    fun unload() {
        nativeUnload()
    }

    fun saveState(path: String): Boolean = nativeSaveState(path)

    fun loadState(path: String): Boolean = nativeLoadState(path)

    private external fun nativeInitialize(systemDir: String, saveDir: String)
    private external fun nativeLoadRom(romPath: String): Boolean
    private external fun nativeRunFrame(outPixels: IntArray): Int
    private external fun nativeDrainAudio(outSamples: ShortArray): Int
    private external fun nativeGetSampleRate(): Int
    private external fun nativeGetFrameRate(): Double
    private external fun nativeSetInputMask(mask: Int)
    private external fun nativeUnload()
    private external fun nativeSaveState(statePath: String): Boolean
    private external fun nativeLoadState(statePath: String): Boolean

    private companion object {
        const val MaxFramePixels = 512 * 512
        const val MaxAudioSamples = 8192
        val EmptyAudio = ShortArray(0)

        init {
            System.loadLibrary("gamefold_emu")
        }
    }
}
