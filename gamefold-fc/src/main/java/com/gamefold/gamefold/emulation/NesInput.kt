package com.gamefold.gamefold.emulation

enum class NesButton(val mask: Int) {
    B(1 shl 0),
    Select(1 shl 2),
    Start(1 shl 3),
    Up(1 shl 4),
    Down(1 shl 5),
    Left(1 shl 6),
    Right(1 shl 7),
    A(1 shl 8),
}

data class NesFrame(
    val pixels: IntArray,
    val width: Int,
    val height: Int,
    val sequence: Long,
)
