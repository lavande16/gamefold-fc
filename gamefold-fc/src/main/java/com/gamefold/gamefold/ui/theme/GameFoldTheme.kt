package com.gamefold.gamefold.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val ShellBlack = Color(0xFF202125)
val PanelGraphite = Color(0xFF565861)
val ControlBlack = Color(0xFF242529)
val ControlRaised = Color(0xFF34363C)
val ControlPressed = Color(0xFF1A1B1E)
val ScreenBlue = Color(0xFF58A9F4)
val ScreenCyan = Color(0xFF91DBF5)
val GameOrange = Color(0xFFF0671A)
val TextMuted = Color(0xFFC7C8CC)
val SoftWhite = Color(0xFFE9E9E7)

@Composable
fun GameFoldTheme(content: @Composable () -> Unit) {
    content()
}
