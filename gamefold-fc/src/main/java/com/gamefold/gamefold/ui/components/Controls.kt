package com.gamefold.gamefold.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gamefold.gamefold.ui.theme.ControlBlack
import com.gamefold.gamefold.ui.theme.ControlPressed
import com.gamefold.gamefold.ui.theme.GameOrange
import com.gamefold.gamefold.ui.theme.SoftWhite
import com.gamefold.gamefold.ui.theme.TextMuted
import kotlin.math.roundToInt

@Composable
fun PressableControl(
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = RoundedCornerShape(18.dp),
    pressedColor: Color = ControlPressed,
    releasedBrush: Brush = Brush.verticalGradient(listOf(Color(0xFF3A3C42), Color(0xFF24262B))),
    content: @Composable BoxScope.(pressed: Boolean) -> Unit = {},
) {
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (pressed) 0.94f else 1f, label = "control-scale")
    val yOffset by animateFloatAsState(if (pressed) 4f else 0f, label = "control-offset")

    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationY = yOffset
            }
            .shadow(14.dp, shape, ambientColor = Color.Black, spotColor = Color.Black)
            .clip(shape)
            .background(if (pressed) Brush.verticalGradient(listOf(pressedColor, Color(0xFF111215))) else releasedBrush)
            .border(1.dp, Color(0x443FFFFFF), shape)
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val down = awaitPointerEvent().changes.firstOrNull { it.pressed }
                        if (down != null) {
                            pressed = true
                            do {
                                val event = awaitPointerEvent()
                                event.changes.forEach { it.consume() }
                            } while (event.changes.any { it.pressed })
                            pressed = false
                        }
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        content(pressed)
    }
}

@Composable
fun DPad(modifier: Modifier = Modifier, size: Dp) {
    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSize()) {
            drawCircle(Color(0x55202125), radius = this.size.minDimension * 0.5f, center = center)
            drawCircle(Color(0x33202125), radius = this.size.minDimension * 0.39f, center = center)
        }
        val arm = size * 0.34f
        val longArm = size * 0.45f
        val corner = RoundedCornerShape(12.dp)
        PressableControl(
            modifier = Modifier.size(arm, longArm).align(Alignment.TopCenter),
            shape = corner,
        ) { DPadMark(vertical = true) }
        PressableControl(
            modifier = Modifier.size(arm, longArm).align(Alignment.BottomCenter),
            shape = corner,
        ) { DPadMark(vertical = true) }
        PressableControl(
            modifier = Modifier.size(longArm, arm).align(Alignment.CenterStart),
            shape = corner,
        ) { DPadMark(vertical = false) }
        PressableControl(
            modifier = Modifier.size(longArm, arm).align(Alignment.CenterEnd),
            shape = corner,
        ) { DPadMark(vertical = false) }
        Box(
            Modifier
                .size(arm * 0.92f)
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF202126))
        )
    }
}

@Composable
private fun DPadMark(vertical: Boolean) {
    Canvas(Modifier.size(22.dp)) {
        val stroke = Stroke(width = 6.dp.toPx())
        if (vertical) {
            drawLine(SoftWhite, Offset(center.x, 3.dp.toPx()), Offset(center.x, size.height - 3.dp.toPx()), stroke.width)
        } else {
            drawLine(SoftWhite, Offset(3.dp.toPx(), center.y), Offset(size.width - 3.dp.toPx(), center.y), stroke.width)
        }
    }
}

@Composable
fun ActionCluster(modifier: Modifier = Modifier, size: Dp) {
    Box(modifier = modifier.size(size)) {
        GameButton("Y", GameOrange, Modifier.align(Alignment.TopCenter))
        GameButton("X", Color(0xFF1272D5), Modifier.align(Alignment.CenterStart))
        GameButton("B", Color(0xFFD93A51), Modifier.align(Alignment.CenterEnd))
        GameButton("A", Color(0xFF04953A), Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun GameButton(label: String, color: Color, modifier: Modifier = Modifier) {
    PressableControl(
        modifier = modifier.size(88.dp),
        shape = RoundedCornerShape(50),
        releasedBrush = Brush.radialGradient(
            listOf(Color.White, Color(0xFFD8D8D4), Color(0xFFB8B8B5)),
        ),
        pressedColor = Color(0xFFBEBEBB),
    ) { pressed ->
        Text(
            text = label,
            color = if (pressed) color.copy(alpha = 0.72f) else color,
            fontSize = 28.sp,
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
fun AnalogStick(modifier: Modifier = Modifier, size: Dp) {
    var knobOffset by remember { mutableStateOf(Offset.Zero) }
    var pressed by remember { mutableStateOf(false) }
    val maxTravel = with(LocalDensity.current) { (size * 0.13f).toPx() }
    val scale by animateFloatAsState(if (pressed) 0.97f else 1f, label = "stick-scale")

    Box(
        modifier = modifier
            .size(size)
            .pointerInput(size) {
                detectDragGestures(
                    onDragStart = { pressed = true },
                    onDragEnd = {
                        pressed = false
                        knobOffset = Offset.Zero
                    },
                    onDragCancel = {
                        pressed = false
                        knobOffset = Offset.Zero
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        val next = knobOffset + dragAmount
                        knobOffset = Offset(
                            x = next.x.coerceIn(-maxTravel, maxTravel),
                            y = next.y.coerceIn(-maxTravel, maxTravel),
                        )
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.matchParentSize()) {
            drawCircle(Color(0xFF1A1B1F), radius = this.size.minDimension * 0.46f)
            drawCircle(Color(0xFF2A2C31), radius = this.size.minDimension * 0.42f, style = Stroke(2.dp.toPx()))
            drawCircle(
                Brush.radialGradient(listOf(Color(0xFF777A7D), Color(0xFF15161A))),
                radius = this.size.minDimension * 0.33f,
            )
        }
        Box(
            Modifier
                .offset { IntOffset(knobOffset.x.roundToInt(), knobOffset.y.roundToInt()) }
                .size(size * 0.48f)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .shadow(16.dp, CircleShape, ambientColor = Color.Black, spotColor = Color.Black)
                .clip(CircleShape)
                .background(Brush.radialGradient(listOf(Color.White, Color(0xFFC9C9C5), Color(0xFF8B8C8C))))
                .border(1.dp, Color(0xAAFFFFFF), CircleShape)
        ) {
            Canvas(Modifier.matchParentSize()) {
                drawCircle(Color(0x22808080), radius = 3.dp.toPx(), center = center + Offset(-10.dp.toPx(), -7.dp.toPx()))
                drawCircle(Color(0x22808080), radius = 3.dp.toPx(), center = center + Offset(10.dp.toPx(), -7.dp.toPx()))
                drawCircle(Color(0x22808080), radius = 3.dp.toPx(), center = center + Offset(0f, 12.dp.toPx()))
            }
        }
    }
}

@Composable
fun StartSelect(modifier: Modifier = Modifier) {
    Box(modifier = modifier.size(260.dp, 100.dp)) {
        SmallPill("SELECT", Modifier.align(Alignment.CenterStart))
        SmallPill("START", Modifier.align(Alignment.CenterEnd))
    }
}

@Composable
private fun SmallPill(label: String, modifier: Modifier = Modifier) {
    Box(modifier = modifier.size(96.dp, 84.dp), contentAlignment = Alignment.Center) {
        Text(
            text = label,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .graphicsLayer { rotationZ = -48f },
            color = TextMuted,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            fontStyle = FontStyle.Italic,
            letterSpacing = 0.sp,
        )
        PressableControl(
            modifier = Modifier
                .size(44.dp, 24.dp)
                .align(Alignment.BottomCenter)
                .graphicsLayer { rotationZ = -48f },
            shape = RoundedCornerShape(50),
        )
    }
}

@Composable
fun SystemButtons(modifier: Modifier = Modifier) {
    androidx.compose.foundation.layout.Row(modifier = modifier) {
        IconButtonGlyph("home")
        androidx.compose.foundation.layout.Spacer(Modifier.size(18.dp))
        IconButtonGlyph("back")
        androidx.compose.foundation.layout.Spacer(Modifier.size(18.dp))
        IconButtonGlyph("menu")
    }
}

@Composable
private fun IconButtonGlyph(kind: String) {
    PressableControl(
        modifier = Modifier.size(54.dp),
        shape = RoundedCornerShape(50),
        releasedBrush = Brush.verticalGradient(listOf(Color(0xFF4B4D53), Color(0xFF2A2C31))),
    ) {
        Canvas(Modifier.size(25.dp)) {
            val line = Stroke(width = 2.5.dp.toPx())
            when (kind) {
                "home" -> {
                    val roof = Path().apply {
                        moveTo(center.x, 3.dp.toPx())
                        lineTo(size.width - 3.dp.toPx(), center.y)
                        lineTo(size.width - 7.dp.toPx(), center.y)
                        lineTo(size.width - 7.dp.toPx(), size.height - 4.dp.toPx())
                        lineTo(7.dp.toPx(), size.height - 4.dp.toPx())
                        lineTo(7.dp.toPx(), center.y)
                        lineTo(3.dp.toPx(), center.y)
                        close()
                    }
                    drawPath(roof, TextMuted, style = line)
                }
                "back" -> {
                    drawLine(TextMuted, Offset(5.dp.toPx(), center.y), Offset(size.width - 4.dp.toPx(), center.y), line.width)
                    drawLine(TextMuted, Offset(5.dp.toPx(), center.y), Offset(12.dp.toPx(), 6.dp.toPx()), line.width)
                    drawLine(TextMuted, Offset(5.dp.toPx(), center.y), Offset(12.dp.toPx(), size.height - 6.dp.toPx()), line.width)
                }
                else -> {
                    drawLine(TextMuted, Offset(5.dp.toPx(), 7.dp.toPx()), Offset(size.width - 5.dp.toPx(), 7.dp.toPx()), line.width)
                    drawLine(TextMuted, Offset(5.dp.toPx(), center.y), Offset(size.width - 5.dp.toPx(), center.y), line.width)
                    drawLine(TextMuted, Offset(5.dp.toPx(), size.height - 7.dp.toPx()), Offset(size.width - 5.dp.toPx(), size.height - 7.dp.toPx()), line.width)
                }
            }
        }
    }
}
