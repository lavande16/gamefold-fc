package com.gamefold.gamefold.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.size

/** Compose port of the GAMEFOLD 5 x 7 bitmap wordmark used by the NS and PC home screens. */
@Composable
internal fun PixelWordmark(
    text: String,
    color: Color,
    textSize: Dp,
    modifier: Modifier = Modifier,
    bold: Boolean = false,
) {
    val wordmark = text.uppercase()
    val glyphs = PixelWordmarkSpec.glyphRowsFor(wordmark)
    val cellSize = textSize / 7f
    val glyphGap = cellSize
    val pixelWeight = if (bold) cellSize * 0.24f else 0.dp
    val contentWidth = cellSize * (wordmark.length * 5f) +
        glyphGap * (wordmark.length - 1).coerceAtLeast(0).toFloat() + pixelWeight

    Canvas(
        modifier = modifier
            .size(width = contentWidth, height = textSize)
            .semantics { contentDescription = wordmark },
    ) {
        val pixelSizePx = size.height / 7f
        val glyphGapPx = pixelSizePx
        val pixelWeightPx = if (bold) pixelSizePx * 0.24f else 0f
        glyphs.forEachIndexed { index, glyph ->
            val originX = index * (5f * pixelSizePx + glyphGapPx)
            glyph.forEachIndexed { row, bits ->
                repeat(5) { column ->
                    if (bits and (1 shl (4 - column)) != 0) {
                        drawRect(
                            color = color,
                            topLeft = Offset(originX + column * pixelSizePx, row * pixelSizePx),
                            size = Size(pixelSizePx + pixelWeightPx, pixelSizePx),
                        )
                    }
                }
            }
        }
    }
}

internal object PixelWordmarkSpec {
    fun glyphRowsFor(text: String): List<IntArray> = text.uppercase().map { character ->
        requireNotNull(glyphs[character]) { "Unsupported GAMEFOLD pixel glyph: $character" }
    }

    private val glyphs = mapOf(
        'G' to intArrayOf(0b01110, 0b10001, 0b10000, 0b10111, 0b10001, 0b10001, 0b01110),
        'A' to intArrayOf(0b01110, 0b10001, 0b10001, 0b11111, 0b10001, 0b10001, 0b10001),
        'M' to intArrayOf(0b10001, 0b11011, 0b10101, 0b10101, 0b10001, 0b10001, 0b10001),
        'E' to intArrayOf(0b11111, 0b10000, 0b10000, 0b11110, 0b10000, 0b10000, 0b11111),
        'F' to intArrayOf(0b11111, 0b10000, 0b10000, 0b11110, 0b10000, 0b10000, 0b10000),
        'O' to intArrayOf(0b01110, 0b10001, 0b10001, 0b10001, 0b10001, 0b10001, 0b01110),
        'L' to intArrayOf(0b10000, 0b10000, 0b10000, 0b10000, 0b10000, 0b10000, 0b11111),
        'D' to intArrayOf(0b11110, 0b10001, 0b10001, 0b10001, 0b10001, 0b10001, 0b11110),
        'C' to intArrayOf(0b01110, 0b10001, 0b10000, 0b10000, 0b10000, 0b10001, 0b01110),
        ' ' to intArrayOf(0, 0, 0, 0, 0, 0, 0),
    )
}
