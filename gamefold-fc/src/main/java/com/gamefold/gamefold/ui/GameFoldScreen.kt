package com.gamefold.gamefold.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.BatteryManager
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.width
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.gamefold.gamefold.GameLibraryItem
import com.gamefold.gamefold.R
import com.gamefold.gamefold.RomSelection
import com.gamefold.gamefold.audio.ControllerFeedback
import com.gamefold.gamefold.audio.MenuMoveSound
import com.gamefold.gamefold.audio.NesAudioSink
import com.gamefold.gamefold.emulation.NativeNesEmulator
import com.gamefold.gamefold.emulation.NesButton
import com.gamefold.gamefold.emulation.NesFrame
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.min
import kotlin.math.roundToInt

private const val DesignWidth = 2504f
private const val DesignHeight = 2312f
private const val SupportedMinWidthDp = 700
private const val SupportedMinHeightDp = 560
private const val MaxCatchUpFrames = 3L
private const val GameLibraryColumns = 4
private const val GameLibraryRows = 2
private const val GameLibraryPageSize = GameLibraryColumns * GameLibraryRows
private const val UpperShellX = 90f
private const val UpperShellY = 90f
private const val UpperShellWidth = 2324f
private const val UpperShellHeight = 916f
private const val ViewportX = 542f
private const val ViewportY = 148f
private const val ViewportWidth = 1418f
private const val ViewportHeight = 801f
private const val LowerControlsStartY = 1218f
private const val HingeSourceWidth = 2504f
private const val HingeSourceHeight = 192f
private const val TopDisplayOffsetAfterCenterPx = 20f
private const val BatteryLightX = 1978f
private const val BatteryLightY = 79f
private const val BatteryLightSize = 38f
private const val LogoHitX = 34f
private const val LogoHitY = 785f
private const val LogoHitWidth = 400f
private const val LogoHitHeight = 90f

internal enum class ActionButtonSlot { Top, Left, Right, Bottom }

internal data class ActionButtonPlacement(
    val slot: ActionButtonSlot,
    val label: String,
    val x: Float,
    val y: Float,
)

internal fun defaultActionButtonPlacements(): List<ActionButtonPlacement> = listOf(
    ActionButtonPlacement(ActionButtonSlot.Top, "X", x = 1911f, y = 1342f),
    ActionButtonPlacement(ActionButtonSlot.Left, "Y", x = 1728f, y = 1524f),
    ActionButtonPlacement(ActionButtonSlot.Right, "A", x = 2090f, y = 1524f),
    ActionButtonPlacement(ActionButtonSlot.Bottom, "B", x = 1911f, y = 1707f),
)

private val OuterBackground = Color(0xFF252529)

private data class AbsoluteRect(
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
)

private data class ControlLayout(
    val originX: Float,
    val originY: Float,
    val scale: Float,
    val baseY: Float = LowerControlsStartY,
)

private data class AdaptiveLayout(
    val upperShell: AbsoluteRect,
    val viewport: AbsoluteRect,
    val topScale: Float,
    val hinge: AbsoluteRect,
    val controls: ControlLayout,
)

@Composable
private fun rememberVirtualKeyHaptic(): () -> Unit {
    val view = LocalView.current
    return remember(view) {
        {
            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        }
    }
}

@Composable
private fun rememberHighBatteryIndicator(): Boolean {
    val context = LocalContext.current
    var isHighBattery by remember { mutableStateOf(readHighBatteryIndicator(context)) }

    DisposableEffect(context) {
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                isHighBattery = readHighBatteryIndicator(intent)
            }
        }
        context.registerReceiver(receiver, filter)?.let {
            isHighBattery = readHighBatteryIndicator(it)
        }
        onDispose {
            runCatching { context.unregisterReceiver(receiver) }
        }
    }

    return isHighBattery
}

private fun readHighBatteryIndicator(context: Context): Boolean {
    val batteryStatus = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    return readHighBatteryIndicator(batteryStatus)
}

private fun readHighBatteryIndicator(intent: Intent?): Boolean {
    val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
    val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
    if (level < 0 || scale <= 0) return true
    return level * 100f / scale >= 50f
}

@Composable
fun GameFoldScreen(
    isSupportedUsageMode: Boolean,
    rom: RomSelection?,
    gameLibrary: List<GameLibraryItem>,
    onSelectGame: (GameLibraryItem) -> Boolean,
    onImportGame: () -> Unit,
    onDeleteGame: (GameLibraryItem) -> Unit,
) {
    val context = LocalContext.current
    val emulator = remember { NativeNesEmulator() }
    val emulatorLock = remember { Any() }
    val audioSink = remember { NesAudioSink() }
    val controllerFeedback = remember { ControllerFeedback(context) }
    val menuMoveSound = remember { MenuMoveSound() }
    val coroutineScope = rememberCoroutineScope()
    val inputMaskRef = remember { AtomicInteger(0) }
    var inputMask by remember { mutableIntStateOf(0) }
    var currentFrame by remember { mutableStateOf<NesFrame?>(null) }
    var romLoadFailed by remember { mutableStateOf(false) }
    var statusText by remember { mutableStateOf<String?>(null) }
    var showGameLibrary by remember { mutableStateOf(true) }
    var showManual by remember { mutableStateOf(false) }
    var gameLibraryMessage by remember { mutableStateOf<String?>(null) }
    var selectedGameIndex by remember { mutableIntStateOf(0) }
    var reloadNonce by remember { mutableIntStateOf(0) }
    var pendingDeletion by remember { mutableStateOf<GameLibraryItem?>(null) }
    val statePath = remember(context, rom?.gameId) {
        File(context.filesDir, "nes-saves/${rom?.gameId ?: "current"}.state").absolutePath
    }

    DisposableEffect(emulator, context, controllerFeedback, menuMoveSound) {
        val systemDir = File(context.filesDir, "nes-system").apply { mkdirs() }
        val saveDir = File(context.filesDir, "nes-saves").apply { mkdirs() }
        emulator.initialize(systemDir.absolutePath, saveDir.absolutePath)
        onDispose {
            audioSink.stop()
            controllerFeedback.release()
            menuMoveSound.release()
            synchronized(emulatorLock) {
                emulator.unload()
            }
        }
    }

    LaunchedEffect(rom?.path, rom?.loadToken, reloadNonce) {
        inputMaskRef.set(0)
        inputMask = 0
        emulator.setInputMask(0)
        currentFrame = null
        romLoadFailed = false
        statusText = null
        audioSink.stop()

        val selectedRom = rom?.path ?: return@LaunchedEffect
        val loaded = withContext(Dispatchers.Default) {
            synchronized(emulatorLock) {
                emulator.loadRom(selectedRom)
            }
        }
        romLoadFailed = !loaded
        if (!loaded) return@LaunchedEffect
        audioSink.start(emulator.getSampleRate())
        var audioRunning = true

        val frameIntervalNanos = (1_000_000_000.0 / emulator.getFrameRate()).toLong()
        var lastFrameTimeNanos = 0L
        var accumulatedNanos = frameIntervalNanos

        while (isActive) {
            val frameTimeNanos = withFrameNanos { it }
            if (showGameLibrary || showManual) {
                if (audioRunning) {
                    audioSink.stop()
                    audioRunning = false
                }
                lastFrameTimeNanos = 0L
                accumulatedNanos = frameIntervalNanos
                continue
            }
            if (!audioRunning) {
                audioSink.start(emulator.getSampleRate())
                audioRunning = true
                lastFrameTimeNanos = 0L
                accumulatedNanos = frameIntervalNanos
            }
            if (lastFrameTimeNanos != 0L) {
                val elapsedNanos = (frameTimeNanos - lastFrameTimeNanos)
                    .coerceIn(0L, frameIntervalNanos * MaxCatchUpFrames)
                accumulatedNanos += elapsedNanos
            }
            lastFrameTimeNanos = frameTimeNanos

            var latestFrame: NesFrame? = null
            var catchUpFrames = 0L
            while (accumulatedNanos >= frameIntervalNanos && catchUpFrames < MaxCatchUpFrames) {
                val frameAudio = withContext(Dispatchers.Default) {
                    val result = synchronized(emulatorLock) {
                        emulator.runFrame() to emulator.drainAudio()
                    }
                    audioSink.write(result.second)
                    result
                }
                latestFrame = frameAudio.first
                accumulatedNanos -= frameIntervalNanos
                catchUpFrames++
            }

            if (catchUpFrames == MaxCatchUpFrames) {
                accumulatedNanos = 0L
            }
            if (latestFrame != null) currentFrame = latestFrame
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        val hasSupportedCanvas = isSupportedUsageMode &&
            maxWidth >= SupportedMinWidthDp.dp &&
            maxHeight >= SupportedMinHeightDp.dp &&
            maxWidth > maxHeight
        val backgroundColor = if (hasSupportedCanvas) OuterBackground else Color.Black

        Box(
            modifier = Modifier
                .matchParentSize()
                .background(backgroundColor),
        )

        if (!hasSupportedCanvas) {
            UnsupportedUsageScreen(maxWidth = maxWidth, maxHeight = maxHeight)
            return@BoxWithConstraints
        }

        val density = LocalDensity.current.density
        val layout = remember(maxWidth, maxHeight, density) {
            calculateAdaptiveLayout(maxWidth.value, maxHeight.value, density)
        }

        Box(
            modifier = Modifier
                .fillMaxSize(),
        ) {
            val onButtonChange: (NesButton, Boolean) -> Unit = { button, pressed ->
                if (pressed) controllerFeedback.play(button)
                val currentMask = inputMaskRef.get()
                val nextMask = if (pressed) {
                    currentMask or button.mask
                } else {
                    currentMask and button.mask.inv()
                }
                inputMaskRef.set(nextMask)
                inputMask = nextMask
                emulator.setInputMask(nextMask)
            }
            val reloadCurrentGame: () -> Unit = {
                reloadNonce++
            }
            val onOpenManual: () -> Unit = {
                inputMaskRef.set(0)
                inputMask = 0
                emulator.setInputMask(0)
                showManual = true
            }
            val onCloseManual: () -> Unit = {
                inputMaskRef.set(0)
                inputMask = 0
                emulator.setInputMask(0)
                showManual = false
            }
            val saveCurrentState: suspend () -> Boolean = {
                if (rom == null || romLoadFailed) {
                    false
                } else {
                    withContext(Dispatchers.Default) {
                        synchronized(emulatorLock) {
                            emulator.saveState(statePath)
                        }
                    }
                }
            }
            val onSaveState: () -> Unit = {
                coroutineScope.launch {
                    controllerFeedback.auxiliary()
                    val saved = saveCurrentState()
                    statusText = if (saved) "SAVED" else "SAVE FAILED"
                    delay(900L)
                    statusText = null
                }
            }
            val onBackAction: () -> Unit = {
                coroutineScope.launch {
                    controllerFeedback.auxiliary()
                    if (showManual) {
                        onCloseManual()
                        return@launch
                    }
                    if (showGameLibrary) {
                        showGameLibrary = false
                        gameLibraryMessage = null
                        return@launch
                    }

                    val stateFile = File(statePath)
                    if (!stateFile.exists()) {
                        reloadCurrentGame()
                        return@launch
                    }

                    val loaded = withContext(Dispatchers.Default) {
                        synchronized(emulatorLock) {
                            emulator.loadState(statePath)
                        }
                    }
                    if (loaded) {
                        statusText = "LOADED"
                    } else {
                        statusText = "LOAD FAILED"
                    }
                    delay(900L)
                    statusText = null
                }
            }
            val onOpenGameLibrary: () -> Unit = {
                coroutineScope.launch {
                    controllerFeedback.auxiliary()
                    val saved = saveCurrentState()
                    inputMaskRef.set(0)
                    inputMask = 0
                    emulator.setInputMask(0)
                    showManual = false
                    selectedGameIndex = gameLibrary.indexOfFirst { it.id == rom?.gameId }
                        .takeIf { it >= 0 }
                        ?: 0
                    gameLibraryMessage = if (saved) "PROGRESS SAVED" else null
                    showGameLibrary = true
                }
            }
            val moveGameLibrarySelection: (Int) -> Unit = { delta ->
                if (showGameLibrary && gameLibrary.isNotEmpty()) {
                    val nextGameIndex = (selectedGameIndex + delta).coerceIn(0, gameLibrary.size)
                    if (nextGameIndex != selectedGameIndex) {
                        selectedGameIndex = nextGameIndex
                        gameLibraryMessage = null
                        menuMoveSound.play()
                    }
                }
            }
            val launchGame: (GameLibraryItem?) -> Unit = { game ->
                controllerFeedback.auxiliary()
                if (showManual) {
                    onCloseManual()
                } else if (showGameLibrary) {
                    if (game == null) {
                        onImportGame()
                    } else if (game.id == rom?.gameId) {
                        showGameLibrary = false
                        gameLibraryMessage = null
                    } else if (onSelectGame(game)) {
                        showGameLibrary = false
                        gameLibraryMessage = null
                    } else {
                        gameLibraryMessage = "COMING SOON"
                    }
                }
            }
            val launchSelectedGame: () -> Unit = {
                launchGame(gameLibrary.getOrNull(selectedGameIndex))
            }

            UpperDisplayShell(rect = layout.upperShell, scale = layout.topScale, onOpenManual = onOpenManual)
            NesGameViewport(
                frame = currentFrame,
                statusText = statusText,
                hasRom = rom != null,
                loadFailed = romLoadFailed,
                rect = layout.viewport,
                scale = layout.topScale,
                showGameLibrary = showGameLibrary,
                showManual = showManual,
                games = gameLibrary,
                currentGameId = rom?.gameId,
                selectedGameIndex = selectedGameIndex,
                libraryMessage = gameLibraryMessage,
                onGameSelected = { game ->
                    selectedGameIndex = gameLibrary.indexOf(game).takeIf { it >= 0 } ?: selectedGameIndex
                    launchGame(game)
                },
                onImportGame = onImportGame,
                onGameLongPressed = { game -> pendingDeletion = game },
            )
            DisplayHinge(rect = layout.hinge)
            RenderedControls(
                layout = layout.controls,
                onButtonChange = onButtonChange,
                onOpenGameLibrary = onOpenGameLibrary,
                onSaveState = onSaveState,
                onBackAction = onBackAction,
                isGameLibraryOpen = showGameLibrary,
                isManualOpen = showManual,
                onMoveGameLibrarySelection = moveGameLibrarySelection,
                onLaunchSelectedGame = launchSelectedGame,
                onCloseManual = onCloseManual,
            )

            pendingDeletion?.let { game ->
                Dialog(
                    onDismissRequest = { pendingDeletion = null },
                    properties = DialogProperties(usePlatformDefaultWidth = false),
                ) {
                    PixelDeleteDialog(
                        gameTitle = game.title,
                        scale = layout.topScale,
                        onDelete = {
                            onDeleteGame(game)
                            pendingDeletion = null
                            selectedGameIndex = selectedGameIndex.coerceAtMost(
                                (gameLibrary.size - 1).coerceAtLeast(0),
                            )
                        },
                        onCancel = { pendingDeletion = null },
                    )
                }
            }
        }
    }
}

@Composable
private fun PixelDeleteDialog(
    gameTitle: String,
    scale: Float,
    onDelete: () -> Unit,
    onCancel: () -> Unit,
) {
    val panelShape = RoundedCornerShape(dp(4f, scale))
    Column(
        modifier = Modifier
            .width(dp(1300f, scale))
            .shadow(dp(30f, scale), panelShape)
            .clip(panelShape)
            .background(Color(0xFF11151D))
            .border(dp(10f, scale), Color(0xFFFF7B22), panelShape)
            .padding(horizontal = dp(68f, scale), vertical = dp(52f, scale)),
    ) {
        Text(
            text = "DELETE GAME",
            color = Color(0xFFFF7B22),
            fontSize = with(LocalDensity.current) { dp(52f, scale).toSp() },
            fontWeight = FontWeight.Black,
            letterSpacing = 0.sp,
            maxLines = 1,
        )
        Text(
            text = "确定删除“$gameTitle”？",
            modifier = Modifier.padding(top = dp(38f, scale)),
            color = Color.White,
            fontSize = with(LocalDensity.current) { dp(44f, scale).toSp() },
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.sp,
            maxLines = 2,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = dp(52f, scale)),
            horizontalArrangement = Arrangement.spacedBy(dp(30f, scale), Alignment.End),
        ) {
            PixelDialogButton(text = "取消", scale = scale, onClick = onCancel)
            PixelDialogButton(text = "删除", scale = scale, emphasized = true, onClick = onDelete)
        }
    }
}

@Composable
private fun PixelDialogButton(
    text: String,
    scale: Float,
    emphasized: Boolean = false,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .width(dp(240f, scale))
            .height(dp(96f, scale))
            .clip(RoundedCornerShape(dp(2f, scale)))
            .background(if (emphasized) Color(0xFFFF7B22) else Color(0xFF2A303B))
            .border(dp(3f, scale), if (emphasized) Color(0xFFFFC07C) else Color(0xFF69717E))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = if (emphasized) Color(0xFF11151D) else Color.White,
            fontSize = with(LocalDensity.current) { dp(38f, scale).toSp() },
            fontWeight = FontWeight.Black,
            letterSpacing = 0.sp,
            maxLines = 1,
        )
    }
}

private fun calculateAdaptiveLayout(width: Float, height: Float, density: Float): AdaptiveLayout {
    val hingeHeight = width * HingeSourceHeight / HingeSourceWidth
    val hingeY = (height - hingeHeight) / 2f
    val hingeBottom = hingeY + hingeHeight

    val topRegionHeight = hingeY
    val lowerRegionHeight = (height - hingeBottom).coerceAtLeast(1f)
    val topDisplayOffset = TopDisplayOffsetAfterCenterPx / density.coerceAtLeast(0.1f)

    val topScaleByWidth = width / DesignWidth
    val topScaleByHeight = ((topRegionHeight - topDisplayOffset).coerceAtLeast(1f)) / UpperShellHeight
    val topScale = min(topScaleByWidth, topScaleByHeight)
    val upperShellWidth = UpperShellWidth * topScale
    val upperShellHeight = UpperShellHeight * topScale
    val upperShellX = (width - upperShellWidth) / 2f
    val upperShellY = ((topRegionHeight - upperShellHeight) / 2f + topDisplayOffset)
        .coerceAtLeast(0f)

    val controlsScale = lowerRegionHeight / (DesignHeight - LowerControlsStartY)
    val controlsWidth = DesignWidth * controlsScale
    val controlsOriginX = (width - controlsWidth) / 2f

    return AdaptiveLayout(
        upperShell = AbsoluteRect(
            x = upperShellX,
            y = upperShellY,
            width = upperShellWidth,
            height = upperShellHeight,
        ),
        viewport = AbsoluteRect(
            x = upperShellX + (ViewportX - UpperShellX) * topScale,
            y = upperShellY + (ViewportY - UpperShellY) * topScale,
            width = ViewportWidth * topScale,
            height = ViewportHeight * topScale,
        ),
        topScale = topScale,
        hinge = AbsoluteRect(
            x = 0f,
            y = hingeY,
            width = width,
            height = hingeHeight,
        ),
        controls = ControlLayout(
            originX = controlsOriginX,
            originY = hingeBottom,
            scale = controlsScale,
        ),
    )
}

@Composable
private fun UnsupportedUsageScreen(maxWidth: Dp, maxHeight: Dp) {
    val iconSize = min(min(maxWidth.value * 0.38f, maxHeight.value * 0.22f), 180f).dp

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Image(
            painter = painterResource(R.drawable.gamefold_fc_door_icon),
            contentDescription = "GameFold FC",
            modifier = Modifier
                .size(iconSize)
                .clip(RoundedCornerShape(18.dp)),
            contentScale = ContentScale.Fit,
        )
        Text(
            text = "GameFold FC",
            modifier = Modifier.padding(top = 14.dp),
            color = Color(0xFFF0E6D8),
            fontSize = 28.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 0.sp,
            maxLines = 1,
        )
        Text(
            text = "不支持当前使用形态，请展开手机至全屏使用",
            modifier = Modifier.padding(top = 10.dp, start = 20.dp, end = 20.dp),
            color = Color(0xFFE5D5BD),
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            letterSpacing = 0.sp,
        )
    }
}

@Composable
private fun UpperDisplayShell(rect: AbsoluteRect, scale: Float, onOpenManual: () -> Unit) {
    val batteryLight = if (rememberHighBatteryIndicator()) {
        R.drawable.battery_light_high
    } else {
        R.drawable.battery_light_low
    }
    val hapticFeedback = rememberVirtualKeyHaptic()

    AbsoluteBox(rect = rect) {
        Image(
            painter = painterResource(R.drawable.upper_display_shell),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.FillBounds,
        )
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset(dp(LogoHitX, scale), dp(LogoHitY, scale))
                .size(dp(LogoHitWidth, scale), dp(LogoHitHeight, scale))
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val down = awaitPointerEvent().changes.firstOrNull { it.pressed }
                            if (down != null) {
                                hapticFeedback()
                                do {
                                    val event = awaitPointerEvent()
                                    event.changes.forEach { it.consume() }
                                } while (event.changes.any { it.pressed })
                                onOpenManual()
                            }
                        }
                    }
                },
        )
        Image(
            painter = painterResource(batteryLight),
            contentDescription = null,
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset(dp(BatteryLightX, scale), dp(BatteryLightY, scale))
                .size(dp(BatteryLightSize, scale)),
            contentScale = ContentScale.FillBounds,
        )
    }
}

@Composable
private fun DisplayHinge(rect: AbsoluteRect) {
    AbsoluteBox(rect = rect) {
        Image(
            painter = painterResource(R.drawable.display_hinge),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.FillBounds,
        )
    }
}

@Composable
private fun NesGameViewport(
    frame: NesFrame?,
    statusText: String?,
    hasRom: Boolean,
    loadFailed: Boolean,
    rect: AbsoluteRect,
    scale: Float,
    showGameLibrary: Boolean,
    showManual: Boolean,
    games: List<GameLibraryItem>,
    currentGameId: String?,
    selectedGameIndex: Int,
    libraryMessage: String?,
    onGameSelected: (GameLibraryItem) -> Unit,
    onImportGame: () -> Unit,
    onGameLongPressed: (GameLibraryItem) -> Unit,
) {
    val bitmap = remember(frame?.sequence) {
        frame?.let {
            Bitmap.createBitmap(
                it.pixels,
                0,
                it.width,
                it.width,
                it.height,
                Bitmap.Config.ARGB_8888,
            )
        }
    }

    AbsoluteBox(rect = rect) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(dp(14f, scale)))
                .background(Color(0xFF56A8EF)),
            contentAlignment = Alignment.Center,
        ) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.FillBounds,
                )
            } else {
                Text(
                    text = if (loadFailed) "ROM LOAD FAILED" else if (hasRom) "LOADING" else "NO ROM",
                    color = Color(0xCC102235),
                    fontSize = with(LocalDensity.current) { dp(54f, scale).toSp() },
                    fontWeight = FontWeight.Black,
                    letterSpacing = 0.sp,
                    textAlign = TextAlign.Center,
                )
            }
            if (statusText != null) {
                Text(
                    text = statusText,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .offset(dp(28f, scale), dp(22f, scale)),
                    color = Color.White.copy(alpha = 0.92f),
                    fontSize = with(LocalDensity.current) { dp(32f, scale).toSp() },
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.sp,
                    maxLines = 1,
                )
            }
            if (showManual) {
                GameManualOverlay(scale = scale)
            } else if (showGameLibrary) {
                GameLibraryOverlay(
                    games = games,
                    currentGameId = currentGameId,
                    selectedGameIndex = selectedGameIndex,
                    message = libraryMessage,
                    scale = scale,
                    onGameSelected = onGameSelected,
                    onImportGame = onImportGame,
                    onGameLongPressed = onGameLongPressed,
                )
            }
        }
    }
}

@Composable
private fun GameManualOverlay(scale: Float) {
    val manualScale = 1.28f
    fun manualDp(value: Float): Dp = dp(value * manualScale, scale)
    val density = LocalDensity.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xF6F2F1EC))
            .padding(horizontal = dp(76f, scale), vertical = dp(48f, scale)),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(manualDp(24f)),
        ) {
            Image(
                painter = painterResource(R.drawable.ic_launcher),
                contentDescription = null,
                modifier = Modifier
                    .size(manualDp(94f))
                    .clip(RoundedCornerShape(manualDp(21f))),
                contentScale = ContentScale.Fit,
            )
            Column {
                Text(
                    text = "GameFold FC",
                    color = Color(0xFF202125),
                    fontSize = with(density) { manualDp(48f).toSp() },
                    fontWeight = FontWeight.Black,
                    letterSpacing = 0.sp,
                    maxLines = 1,
                )
                Text(
                    text = "Have Fun!",
                    color = Color(0xFFEA5B00),
                    fontSize = with(density) { manualDp(22f).toSp() },
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.sp,
                    maxLines = 1,
                )
            }
        }

        Text(
            text = "操作说明",
            modifier = Modifier.padding(top = manualDp(34f)),
            color = Color(0xFF202125),
            fontSize = with(density) { manualDp(30f).toSp() },
            fontWeight = FontWeight.Black,
            letterSpacing = 0.sp,
            maxLines = 1,
        )
        Text(
            text = "- MENU 键：快速存档\n- BACK 键：读取存档\n- HOME 键：返回游戏库并自动存档",
            modifier = Modifier.padding(top = manualDp(16f)),
            color = Color(0xE6202125),
            fontSize = with(density) { manualDp(25f).toSp() },
            fontWeight = FontWeight.Medium,
            lineHeight = with(density) { manualDp(38f).toSp() },
            letterSpacing = 0.sp,
        )
        Text(
            text = "仅供个人学习与研究使用",
            modifier = Modifier.padding(top = manualDp(36f)),
            color = Color(0xFF202125),
            fontSize = with(density) { manualDp(24f).toSp() },
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.sp,
            maxLines = 1,
        )
        Text(
            text = "Developer\nlavande.yongwei@gmail.com\n© 2026 GameFold FC. All rights reserved.",
            modifier = Modifier.padding(top = manualDp(46f)),
            color = Color(0xB5202125),
            fontSize = with(density) { manualDp(21f).toSp() },
            fontWeight = FontWeight.Medium,
            lineHeight = with(density) { manualDp(31f).toSp() },
            letterSpacing = 0.sp,
        )
    }
}

@Composable
private fun GameLibraryOverlay(
    games: List<GameLibraryItem>,
    currentGameId: String?,
    selectedGameIndex: Int,
    message: String?,
    scale: Float,
    onGameSelected: (GameLibraryItem) -> Unit,
    onImportGame: () -> Unit,
    onGameLongPressed: (GameLibraryItem) -> Unit,
) {
    val pageStart = (selectedGameIndex / GameLibraryPageSize) * GameLibraryPageSize
    val pageGames = (games.map { it as GameLibraryItem? } + null)
        .drop(pageStart)
        .take(GameLibraryPageSize)
    val pageRows = pageGames.chunked(GameLibraryColumns)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xF20B1016))
            .padding(horizontal = dp(42f, scale), vertical = dp(28f, scale)),
        verticalArrangement = Arrangement.spacedBy(dp(18f, scale)),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PixelWordmark(
                text = "GAMEFOLD FC",
                color = Color.White.copy(alpha = 0.94f),
                textSize = dp(32f, scale),
                bold = true,
            )
            if (message != null) {
                Text(
                    text = message,
                    color = Color(0xFFFF7B22),
                    fontSize = with(LocalDensity.current) { dp(20f, scale).toSp() },
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.sp,
                    maxLines = 1,
                )
            }
        }

        pageRows.forEachIndexed { rowIndex, rowGames ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(dp(316f, scale)),
                horizontalArrangement = Arrangement.spacedBy(dp(22f, scale)),
            ) {
                rowGames.forEachIndexed { columnIndex, game ->
                    val absoluteIndex = pageStart + rowIndex * GameLibraryColumns + columnIndex
                    if (game == null) {
                        GameImportCell(
                            isSelected = absoluteIndex == selectedGameIndex,
                            scale = scale,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxSize(),
                            onClick = onImportGame,
                        )
                    } else {
                        GameLibraryCell(
                            game = game,
                            isCurrent = game.id == currentGameId,
                            isSelected = absoluteIndex == selectedGameIndex,
                            scale = scale,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxSize(),
                            onClick = { onGameSelected(game) },
                            onLongClick = { onGameLongPressed(game) },
                        )
                    }
                }
                repeat(GameLibraryColumns - rowGames.size) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxSize(),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GameLibraryCell(
    game: GameLibraryItem,
    isCurrent: Boolean,
    isSelected: Boolean,
    scale: Float,
    modifier: Modifier,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val cellShape = RoundedCornerShape(dp(18f, scale))
    Box(
        modifier = modifier
            .shadow(
                elevation = if (isSelected) dp(10f, scale) else dp(4f, scale),
                shape = cellShape,
                clip = false,
            )
            .clip(cellShape)
            .background(Color.Black)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        GameCoverImage(game = game)
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(dp(82f, scale))
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color.Transparent,
                            Color(0xE600050B),
                            Color(0xFA000205),
                        ),
                    ),
                ),
        )
        Text(
            text = game.title,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = dp(14f, scale), vertical = dp(18f, scale)),
            color = if (game.isAvailable) Color.White.copy(alpha = 0.96f) else Color.White.copy(alpha = 0.42f),
            fontSize = with(LocalDensity.current) { dp(28f, scale).toSp() },
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center,
            letterSpacing = 0.sp,
            maxLines = 1,
        )
        if (isCurrent) {
            Text(
                text = "CURRENT",
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(dp(10f, scale))
                    .background(Color(0xDD00050B), RoundedCornerShape(dp(9f, scale)))
                    .padding(horizontal = dp(8f, scale), vertical = dp(4f, scale)),
                color = Color(0xFFFF7B22),
                fontSize = with(LocalDensity.current) { dp(12f, scale).toSp() },
                fontWeight = FontWeight.Black,
                letterSpacing = 0.sp,
                maxLines = 1,
            )
        }
        if (isSelected) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .border(
                        width = dp(4.2f, scale),
                        color = Color(0xFFFF7B22),
                        shape = cellShape,
                    ),
            )
        }
    }
}

@Composable
private fun GameImportCell(
    isSelected: Boolean,
    scale: Float,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val cellShape = RoundedCornerShape(dp(18f, scale))
    Box(
        modifier = modifier
            .shadow(
                elevation = if (isSelected) dp(10f, scale) else dp(4f, scale),
                shape = cellShape,
                clip = false,
            )
            .clip(cellShape)
            .background(Color(0xFF111821))
            .clickable(onClick = onClick)
            .then(
                if (isSelected) {
                    Modifier.border(dp(4.2f, scale), Color(0xFFFF7B22), cellShape)
                } else {
                    Modifier
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "+",
                color = Color(0xFFFF7B22),
                fontSize = with(LocalDensity.current) { dp(92f, scale).toSp() },
                fontWeight = FontWeight.Light,
                lineHeight = with(LocalDensity.current) { dp(86f, scale).toSp() },
                letterSpacing = 0.sp,
            )
            Text(
                text = "导入游戏",
                modifier = Modifier.padding(top = dp(8f, scale)),
                color = Color.White.copy(alpha = 0.9f),
                fontSize = with(LocalDensity.current) { dp(22f, scale).toSp() },
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.sp,
            )
        }
    }
}

@Composable
private fun GameCoverImage(game: GameLibraryItem) {
    val context = LocalContext.current
    val coverBitmap = remember(game.coverAssetPath) {
        game.coverAssetPath?.let { path ->
            runCatching {
                context.assets.open(path).use { BitmapFactory.decodeStream(it) }
            }.getOrNull()
        }
    }
    Box(
        modifier = Modifier
            .fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        if (coverBitmap != null) {
            Image(
                bitmap = coverBitmap.asImageBitmap(),
                contentDescription = game.title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
            )
        } else {
            val fallbackId = remember(game.coverResourceName) {
                game.coverResourceName
                    ?.let { context.resources.getIdentifier(it, "drawable", context.packageName) }
                    ?.takeIf { it != 0 }
                    ?: R.drawable.gamefold_fc_door_icon
            }
            Image(
                painter = painterResource(fallbackId),
                contentDescription = game.title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
            )
        }
    }
}

@Composable
private fun LeftStatusIndicators(scale: Float) {
    BoardBox(x = 98f, y = 232f, width = 70f, height = 78f, scale = scale) {
        Canvas(Modifier.fillMaxSize()) {
            fun indicator(top: Float) {
                val pillSize = Size(size.width * 0.82f, size.height * 0.2f)
                val left = size.width * 0.02f
                drawRoundRect(
                    color = Color.Black.copy(alpha = 0.12f),
                    topLeft = Offset(left, top + size.height * 0.03f),
                    size = pillSize,
                    cornerRadius = CornerRadius(pillSize.height * 0.16f),
                )
                drawRoundRect(
                    brush = Brush.verticalGradient(
                        listOf(
                            Color(0xFFC9CBCA).copy(alpha = 0.92f),
                            Color(0xFFA4A7A7).copy(alpha = 0.92f),
                        ),
                    ),
                    topLeft = Offset(left, top),
                    size = pillSize,
                    cornerRadius = CornerRadius(pillSize.height * 0.16f),
                )
                drawRoundRect(
                    color = Color.White.copy(alpha = 0.12f),
                    topLeft = Offset(left + pillSize.width * 0.1f, top + pillSize.height * 0.16f),
                    size = Size(pillSize.width * 0.72f, pillSize.height * 0.18f),
                    cornerRadius = CornerRadius(pillSize.height * 0.08f),
                )
            }

            indicator(size.height * 0.02f)
            indicator(size.height * 0.43f)
        }
    }
}

@Composable
private fun BatteryStatus(scale: Float) {
    BoardBox(x = 2070f, y = 136f, width = 330f, height = 120f, scale = scale) {
        Canvas(Modifier.fillMaxSize()) {
            drawRect(Color(0xFF555962))

            val lightCenter = Offset(size.width * 0.2f, size.height * 0.42f)
            val lightRadius = size.height * 0.12f
            drawCircle(
                color = Color.Black.copy(alpha = 0.18f),
                radius = lightRadius * 1.12f,
                center = lightCenter + Offset(0f, lightRadius * 0.1f),
            )
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(
                        Color(0xFFFF7B22),
                        Color(0xFFFF5C10),
                        Color(0xFFD8480B),
                    ),
                    center = lightCenter - Offset(lightRadius * 0.2f, lightRadius * 0.22f),
                    radius = lightRadius * 1.18f,
                ),
                radius = lightRadius * 0.9f,
                center = lightCenter,
            )
            drawCircle(
                color = Color.White.copy(alpha = 0.14f),
                radius = lightRadius * 0.24f,
                center = lightCenter - Offset(lightRadius * 0.22f, lightRadius * 0.24f),
            )
            drawCircle(
                color = Color.Black.copy(alpha = 0.18f),
                radius = lightRadius * 0.9f,
                center = lightCenter,
                style = Stroke(width = lightRadius * 0.06f),
            )
        }

        Text(
            text = "BATTERY",
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset(dp(101f, scale), dp(43f, scale))
                .size(dp(210f, scale), dp(50f, scale)),
            color = Color(0xFFC9CBCE).copy(alpha = 0.72f),
            fontSize = with(LocalDensity.current) { dp(37f, scale).toSp() },
            fontStyle = FontStyle.Italic,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Start,
            maxLines = 1,
            softWrap = false,
        )
    }
}

@Composable
private fun CleanControllerDeck(scale: Float) {
    BoardBox(x = 0f, y = 1218f, width = DesignWidth, height = DesignHeight - 1218f, scale = scale) {
        Canvas(Modifier.fillMaxSize()) {
            drawRect(Color(0xFF202125))
            drawLine(
                color = Color.Black.copy(alpha = 0.12f),
                start = Offset(0f, 1f),
                end = Offset(size.width, 1f),
                strokeWidth = size.height * 0.0025f,
            )
        }
    }
}

@Composable
private fun RenderedControls(
    layout: ControlLayout,
    onButtonChange: (NesButton, Boolean) -> Unit,
    onOpenGameLibrary: () -> Unit,
    onSaveState: () -> Unit,
    onBackAction: () -> Unit,
    isGameLibraryOpen: Boolean,
    isManualOpen: Boolean,
    onMoveGameLibrarySelection: (Int) -> Unit,
    onLaunchSelectedGame: () -> Unit,
    onCloseManual: () -> Unit,
) {
    DPad(layout, onButtonChange, isGameLibraryOpen, isManualOpen, onMoveGameLibrarySelection, onCloseManual)
    SystemControls(layout, onOpenGameLibrary, onSaveState, onBackAction, isManualOpen, onCloseManual)
    StartSelectControls(layout, onButtonChange, isGameLibraryOpen, isManualOpen, onLaunchSelectedGame, onCloseManual)
    AnalogStick(
        x = 700f,
        y = 1855f,
        size = 300f,
        layout = layout,
        isGameLibraryOpen = isGameLibraryOpen,
        isManualOpen = isManualOpen,
        onMoveGameLibrarySelection = onMoveGameLibrarySelection,
        onButtonChange = onButtonChange,
        onCloseManual = onCloseManual,
    )
    AnalogStick(x = 1498f, y = 1855f, size = 300f, layout = layout, isManualOpen = isManualOpen, onCloseManual = onCloseManual)
    defaultActionButtonPlacements().forEach { placement ->
        ActionButton(placement.label, x = placement.x, y = placement.y, size = 246f, layout = layout, isGameLibraryOpen = isGameLibraryOpen, isManualOpen = isManualOpen, onButtonChange = onButtonChange, onLaunchSelectedGame = onLaunchSelectedGame, onCloseManual = onCloseManual)
    }
}

@Composable
private fun DPad(
    layout: ControlLayout,
    onButtonChange: (NesButton, Boolean) -> Unit,
    isGameLibraryOpen: Boolean,
    isManualOpen: Boolean,
    onMoveGameLibrarySelection: (Int) -> Unit,
    onCloseManual: () -> Unit,
) {
    var activeDirection by remember { mutableStateOf<DPadDirection?>(null) }
    val dpadImage = when (activeDirection) {
        DPadDirection.Up -> R.drawable.rc_motionpad_5way_pressed_up
        DPadDirection.Down -> R.drawable.rc_motionpad_5way_pressed_down
        DPadDirection.Left -> R.drawable.rc_motionpad_5way_pressed_left
        DPadDirection.Right -> R.drawable.rc_motionpad_5way_pressed_right
        DPadDirection.UpLeft -> R.drawable.rc_motionpad_5way_pressed_up
        DPadDirection.UpRight -> R.drawable.rc_motionpad_5way_pressed_up
        DPadDirection.DownLeft -> R.drawable.rc_motionpad_5way_pressed_down
        DPadDirection.DownRight -> R.drawable.rc_motionpad_5way_pressed_down
        null -> R.drawable.rc_motionpad_5way_normal
    }

    BoardBox(x = 142f, y = 1342f, width = 610f, height = 610f, layout = layout) {
        Image(
            painter = painterResource(dpadImage),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.FillBounds,
        )
    }
    DPadHit(x = 328f, y = 1352f, width = 238f, height = 250f, direction = DPadDirection.Up, layout = layout, isGameLibraryOpen = isGameLibraryOpen, isManualOpen = isManualOpen, onActiveChange = { activeDirection = it }, onButtonChange = onButtonChange, onMoveGameLibrarySelection = onMoveGameLibrarySelection, onCloseManual = onCloseManual)
    DPadHit(x = 328f, y = 1692f, width = 238f, height = 250f, direction = DPadDirection.Down, layout = layout, isGameLibraryOpen = isGameLibraryOpen, isManualOpen = isManualOpen, onActiveChange = { activeDirection = it }, onButtonChange = onButtonChange, onMoveGameLibrarySelection = onMoveGameLibrarySelection, onCloseManual = onCloseManual)
    DPadHit(x = 152f, y = 1528f, width = 260f, height = 240f, direction = DPadDirection.Left, layout = layout, isGameLibraryOpen = isGameLibraryOpen, isManualOpen = isManualOpen, onActiveChange = { activeDirection = it }, onButtonChange = onButtonChange, onMoveGameLibrarySelection = onMoveGameLibrarySelection, onCloseManual = onCloseManual)
    DPadHit(x = 482f, y = 1528f, width = 260f, height = 240f, direction = DPadDirection.Right, layout = layout, isGameLibraryOpen = isGameLibraryOpen, isManualOpen = isManualOpen, onActiveChange = { activeDirection = it }, onButtonChange = onButtonChange, onMoveGameLibrarySelection = onMoveGameLibrarySelection, onCloseManual = onCloseManual)
    DPadHit(x = 152f, y = 1352f, width = 176f, height = 176f, direction = DPadDirection.UpLeft, layout = layout, isGameLibraryOpen = isGameLibraryOpen, isManualOpen = isManualOpen, onActiveChange = { activeDirection = it }, onButtonChange = onButtonChange, onMoveGameLibrarySelection = onMoveGameLibrarySelection, onCloseManual = onCloseManual)
    DPadHit(x = 566f, y = 1352f, width = 176f, height = 176f, direction = DPadDirection.UpRight, layout = layout, isGameLibraryOpen = isGameLibraryOpen, isManualOpen = isManualOpen, onActiveChange = { activeDirection = it }, onButtonChange = onButtonChange, onMoveGameLibrarySelection = onMoveGameLibrarySelection, onCloseManual = onCloseManual)
    DPadHit(x = 152f, y = 1768f, width = 176f, height = 174f, direction = DPadDirection.DownLeft, layout = layout, isGameLibraryOpen = isGameLibraryOpen, isManualOpen = isManualOpen, onActiveChange = { activeDirection = it }, onButtonChange = onButtonChange, onMoveGameLibrarySelection = onMoveGameLibrarySelection, onCloseManual = onCloseManual)
    DPadHit(x = 566f, y = 1768f, width = 176f, height = 174f, direction = DPadDirection.DownRight, layout = layout, isGameLibraryOpen = isGameLibraryOpen, isManualOpen = isManualOpen, onActiveChange = { activeDirection = it }, onButtonChange = onButtonChange, onMoveGameLibrarySelection = onMoveGameLibrarySelection, onCloseManual = onCloseManual)
}

private enum class DPadDirection { Up, Down, Left, Right, UpLeft, UpRight, DownLeft, DownRight }

private fun DPadDirection.toNesButtons(): List<NesButton> = when (this) {
    DPadDirection.Up -> listOf(NesButton.Up)
    DPadDirection.Down -> listOf(NesButton.Down)
    DPadDirection.Left -> listOf(NesButton.Left)
    DPadDirection.Right -> listOf(NesButton.Right)
    DPadDirection.UpLeft -> listOf(NesButton.Up, NesButton.Left)
    DPadDirection.UpRight -> listOf(NesButton.Up, NesButton.Right)
    DPadDirection.DownLeft -> listOf(NesButton.Down, NesButton.Left)
    DPadDirection.DownRight -> listOf(NesButton.Down, NesButton.Right)
}

private fun DPadDirection.toLibraryMoveDelta(): Int = when (this) {
    DPadDirection.Up -> -GameLibraryColumns
    DPadDirection.Down -> GameLibraryColumns
    DPadDirection.Left -> -1
    DPadDirection.Right -> 1
    DPadDirection.UpLeft -> -GameLibraryColumns - 1
    DPadDirection.UpRight -> -GameLibraryColumns + 1
    DPadDirection.DownLeft -> GameLibraryColumns - 1
    DPadDirection.DownRight -> GameLibraryColumns + 1
}

@Composable
private fun DPadHit(
    x: Float,
    y: Float,
    width: Float,
    height: Float,
    direction: DPadDirection,
    layout: ControlLayout,
    isGameLibraryOpen: Boolean,
    isManualOpen: Boolean,
    onActiveChange: (DPadDirection?) -> Unit,
    onButtonChange: (NesButton, Boolean) -> Unit,
    onMoveGameLibrarySelection: (Int) -> Unit,
    onCloseManual: () -> Unit,
) {
    val hapticFeedback = rememberVirtualKeyHaptic()
    val buttons = direction.toNesButtons()
    val currentOnActiveChange by rememberUpdatedState(onActiveChange)
    val currentOnButtonChange by rememberUpdatedState(onButtonChange)
    val currentOnMoveGameLibrarySelection by rememberUpdatedState(onMoveGameLibrarySelection)
    val currentOnCloseManual by rememberUpdatedState(onCloseManual)
    BoardBox(x = x, y = y, width = width, height = height, layout = layout) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(direction, isGameLibraryOpen, isManualOpen) {
                    awaitPointerEventScope {
                        while (true) {
                            val down = awaitPointerEvent().changes.firstOrNull { it.pressed }
                            if (down != null) {
                                currentOnActiveChange(direction)
                                if (isManualOpen) {
                                    currentOnCloseManual()
                                } else if (isGameLibraryOpen) {
                                    currentOnMoveGameLibrarySelection(direction.toLibraryMoveDelta())
                                } else {
                                    buttons.forEach { currentOnButtonChange(it, true) }
                                }
                                hapticFeedback()
                                do {
                                    val event = awaitPointerEvent()
                                    event.changes.forEach { it.consume() }
                                } while (event.changes.any { it.pressed })
                                if (!isGameLibraryOpen && !isManualOpen) {
                                    buttons.forEach { currentOnButtonChange(it, false) }
                                }
                                currentOnActiveChange(null)
                            }
                        }
                    }
                },
        )
    }
}

@Composable
private fun SystemControls(
    layout: ControlLayout,
    onOpenGameLibrary: () -> Unit,
    onSaveState: () -> Unit,
    onBackAction: () -> Unit,
    isManualOpen: Boolean,
    onCloseManual: () -> Unit,
) {
    SystemButton("home", 1045f, 1325f, layout, onClick = { if (isManualOpen) onCloseManual() else onOpenGameLibrary() })
    SystemButton("back", 1187f, 1325f, layout, onClick = { if (isManualOpen) onCloseManual() else onBackAction() })
    SystemButton("menu", 1329f, 1325f, layout, onClick = { if (isManualOpen) onCloseManual() else onSaveState() })
}

@Composable
private fun SystemButton(kind: String, x: Float, y: Float, layout: ControlLayout, onClick: () -> Unit = {}) {
    var pressed by remember { mutableStateOf(false) }
    val depression by animateFloatAsState(if (pressed) 6f else 0f, label = "system-depression")
    val icon = when (kind) {
        "home" -> R.drawable.ic_rc_key_home
        "back" -> R.drawable.ic_rc_key_back
        else -> R.drawable.ic_rc_key_menu
    }

    PressableBoardBox(
        x = x,
        y = y + depression,
        width = 132f,
        height = 132f,
        layout = layout,
        pressed = pressed,
        onPressedChange = { pressed = it },
        onClick = onClick,
        shape = CircleShape,
        shadow = 14f,
    ) {
        Image(
            painter = painterResource(if (pressed) R.drawable.ic_rc_keybg_1_pressed else R.drawable.ic_rc_keybg_1_normal),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.FillBounds,
        )
        Image(
            painter = painterResource(icon),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit,
        )
    }
}

@Composable
private fun StartSelectControls(
    layout: ControlLayout,
    onButtonChange: (NesButton, Boolean) -> Unit,
    isGameLibraryOpen: Boolean,
    isManualOpen: Boolean,
    onLaunchSelectedGame: () -> Unit,
    onCloseManual: () -> Unit,
) {
    MiniButton("SELECT", x = 1085f, y = 1626f, layout = layout, isGameLibraryOpen = isGameLibraryOpen, isManualOpen = isManualOpen, onButtonChange = onButtonChange, onLaunchSelectedGame = onLaunchSelectedGame, onCloseManual = onCloseManual)
    MiniButton("START", x = 1276f, y = 1626f, layout = layout, isGameLibraryOpen = isGameLibraryOpen, isManualOpen = isManualOpen, onButtonChange = onButtonChange, onLaunchSelectedGame = onLaunchSelectedGame, onCloseManual = onCloseManual)
}

@Composable
private fun MiniButton(
    label: String,
    x: Float,
    y: Float,
    layout: ControlLayout,
    isGameLibraryOpen: Boolean,
    isManualOpen: Boolean,
    onButtonChange: (NesButton, Boolean) -> Unit,
    onLaunchSelectedGame: () -> Unit,
    onCloseManual: () -> Unit,
) {
    var pressed by remember { mutableStateOf(false) }
    val normalImage = if (label == "SELECT") R.drawable.ic_rc_key_select_normal else R.drawable.ic_rc_key_start_normal
    val pressedImage = if (label == "SELECT") R.drawable.ic_rc_key_select_pressed else R.drawable.ic_rc_key_start_pressed
    val button = if (label == "SELECT") NesButton.Select else NesButton.Start

    PressableBoardBox(
        x = x,
        y = y,
        width = 154f,
        height = 154f,
        layout = layout,
        pressed = pressed,
        onPressedChange = {
            pressed = it
            if (!isGameLibraryOpen && !isManualOpen) {
                onButtonChange(button, it)
            }
        },
        onClick = {
            if (isManualOpen) {
                onCloseManual()
            } else if (isGameLibraryOpen) {
                onLaunchSelectedGame()
            }
        },
        shape = RoundedCornerShape(dp(18f, layout.scale)),
        shadow = 0f,
    ) {
        Image(
            painter = painterResource(if (pressed) pressedImage else normalImage),
            contentDescription = label,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.FillBounds,
        )
    }
}

@Composable
private fun ActionButton(
    label: String,
    x: Float,
    y: Float,
    size: Float,
    layout: ControlLayout,
    isGameLibraryOpen: Boolean,
    isManualOpen: Boolean,
    onButtonChange: (NesButton, Boolean) -> Unit,
    onLaunchSelectedGame: () -> Unit,
    onCloseManual: () -> Unit,
) {
    var pressed by remember { mutableStateOf(false) }
    val depression by animateFloatAsState(if (pressed) 8f else 0f, label = "action-depression")
    val letter = when (label) {
        "A" -> R.drawable.ic_rc_key_a2
        "B" -> R.drawable.ic_rc_key_b2
        "X" -> R.drawable.ic_rc_key_x2
        else -> R.drawable.ic_rc_key_y2
    }
    val nesButton = when (label) {
        "A" -> NesButton.A
        "B" -> NesButton.B
        "X" -> NesButton.A
        else -> NesButton.B
    }

    PressableBoardBox(
        x = x,
        y = y + depression,
        width = size,
        height = size,
        layout = layout,
        pressed = pressed,
        onPressedChange = {
            pressed = it
            if (!isGameLibraryOpen && !isManualOpen) {
                onButtonChange(nesButton, it)
            }
        },
        onClick = {
            if (isManualOpen) {
                onCloseManual()
            } else if (isGameLibraryOpen && (label == "A" || label == "X")) {
                onLaunchSelectedGame()
            }
        },
        shape = CircleShape,
        shadow = if (pressed) 12f else 24f,
    ) {
        Image(
            painter = painterResource(if (pressed) R.drawable.ic_rc_keybg_3_pressed else R.drawable.ic_rc_keybg_3_normal),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.FillBounds,
        )
        Image(
            painter = painterResource(letter),
            contentDescription = label,
            modifier = Modifier
                .size(dp(size * 1.34f, layout.scale), dp(size * 0.92f, layout.scale))
                .graphicsLayer {
                    translationY = if (pressed) 4f * layout.scale else 0f
                    alpha = if (pressed) 0.82f else 1f
                },
            contentScale = ContentScale.Fit,
        )
    }
}

@Composable
private fun AnalogStick(
    x: Float,
    y: Float,
    size: Float,
    layout: ControlLayout,
    isGameLibraryOpen: Boolean = false,
    isManualOpen: Boolean = false,
    onMoveGameLibrarySelection: (Int) -> Unit = {},
    onButtonChange: (NesButton, Boolean) -> Unit = { _, _ -> },
    onCloseManual: () -> Unit = {},
) {
    var pressed by remember { mutableStateOf(false) }
    var knobOffset by remember { mutableStateOf(Offset.Zero) }
    val hapticFeedback = rememberVirtualKeyHaptic()
    val maxTravel = size * layout.scale * 0.29f
    val knobScale by animateFloatAsState(if (pressed) 0.975f else 1f, label = "stick-knob-scale")
    val currentOnMoveGameLibrarySelection by rememberUpdatedState(onMoveGameLibrarySelection)
    val currentOnButtonChange by rememberUpdatedState(onButtonChange)
    val currentOnCloseManual by rememberUpdatedState(onCloseManual)

    BoardBox(
        x = x,
        y = y,
        width = size,
        height = size,
        layout = layout,
    ) {
        Image(
            painter = painterResource(R.drawable.rc_stick_bg),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.FillBounds,
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(isGameLibraryOpen, isManualOpen) {
                    val hitWidth = this.size.width.toFloat()
                    val hitHeight = this.size.height.toFloat()
                    awaitPointerEventScope {
                        while (true) {
                            val down = awaitPointerEvent().changes.firstOrNull { it.pressed }
                            if (down != null) {
                                hapticFeedback()
                                pressed = true
                                knobOffset = Offset.Zero
                                var navigationTriggered = 0
                                var activeButtons = emptySet<NesButton>()
                                if (isManualOpen) {
                                    currentOnCloseManual()
                                } else if (isGameLibraryOpen) {
                                    navigationTriggered = triggerStickNavigation(
                                        positionX = down.position.x,
                                        positionY = down.position.y,
                                        width = hitWidth,
                                        height = hitHeight,
                                        currentTrigger = navigationTriggered,
                                        onMoveGameLibrarySelection = currentOnMoveGameLibrarySelection,
                                    )
                                } else {
                                    val nextButtons = stickButtonsForPosition(
                                        positionX = down.position.x,
                                        positionY = down.position.y,
                                        width = hitWidth,
                                        height = hitHeight,
                                    )
                                    updateDirectionalButtons(activeButtons, nextButtons, currentOnButtonChange)
                                    activeButtons = nextButtons
                                }
                                do {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.first()
                                    val delta = change.positionChange()
                                    knobOffset = Offset(
                                        x = (knobOffset.x + delta.x).coerceIn(-maxTravel, maxTravel),
                                        y = (knobOffset.y + delta.y).coerceIn(-maxTravel, maxTravel),
                                    )
                                    if (isManualOpen) {
                                        // Manual is already closed on touch-down; consume the gesture without game input.
                                    } else if (isGameLibraryOpen) {
                                        navigationTriggered = triggerStickNavigation(
                                            positionX = change.position.x,
                                            positionY = change.position.y,
                                            width = hitWidth,
                                            height = hitHeight,
                                            currentTrigger = navigationTriggered,
                                            onMoveGameLibrarySelection = currentOnMoveGameLibrarySelection,
                                        )
                                    } else {
                                        val nextButtons = stickButtonsForPosition(
                                            positionX = change.position.x,
                                            positionY = change.position.y,
                                            width = hitWidth,
                                            height = hitHeight,
                                        )
                                        updateDirectionalButtons(activeButtons, nextButtons, currentOnButtonChange)
                                        activeButtons = nextButtons
                                    }
                                    event.changes.forEach { it.consume() }
                                } while (event.changes.any { it.pressed })
                                if (!isManualOpen) {
                                    updateDirectionalButtons(activeButtons, emptySet(), currentOnButtonChange)
                                }
                                knobOffset = Offset.Zero
                                pressed = false
                            }
                        }
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(R.drawable.rc_stick_wheel),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .offset {
                        IntOffset(
                            x = knobOffset.x.roundToInt(),
                            y = knobOffset.y.roundToInt(),
                        )
                    }
                    .graphicsLayer {
                        scaleX = knobScale
                        scaleY = knobScale
                    },
                contentScale = ContentScale.FillBounds,
            )
        }
    }
}

@Composable
private fun PressableBoardBox(
    x: Float,
    y: Float,
    width: Float,
    height: Float,
    layout: ControlLayout,
    pressed: Boolean,
    onPressedChange: (Boolean) -> Unit,
    onClick: () -> Unit = {},
    shape: Shape,
    shadow: Float,
    rotationZ: Float = 0f,
    hitPaddingLeft: Float = 0f,
    hitPaddingTop: Float = 0f,
    hitPaddingRight: Float = 0f,
    hitPaddingBottom: Float = 0f,
    content: @Composable BoxScope.() -> Unit,
) {
    val visualScale by animateFloatAsState(if (pressed) 0.985f else 1f, label = "pressable-visual-scale")
    val hapticFeedback = rememberVirtualKeyHaptic()
    val currentOnPressedChange by rememberUpdatedState(onPressedChange)
    val currentOnClick by rememberUpdatedState(onClick)

    BoardBox(
        x = x - hitPaddingLeft,
        y = y - hitPaddingTop,
        width = width + hitPaddingLeft + hitPaddingRight,
        height = height + hitPaddingTop + hitPaddingBottom,
        layout = layout,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val down = awaitPointerEvent().changes.firstOrNull { it.pressed }
                            if (down != null) {
                                currentOnPressedChange(true)
                                hapticFeedback()
                                do {
                                    val event = awaitPointerEvent()
                                    event.changes.forEach { it.consume() }
                                } while (event.changes.any { it.pressed })
                                currentOnPressedChange(false)
                                currentOnClick()
                            }
                        }
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(dp(width, layout.scale), dp(height, layout.scale))
                .graphicsLayer {
                    scaleX = visualScale
                    scaleY = visualScale
                    this.rotationZ = rotationZ
                }
                .shadow(dp(shadow, layout.scale), shape, ambientColor = Color.Black, spotColor = Color.Black)
                    .clip(shape),
                contentAlignment = Alignment.Center,
                content = content,
            )
        }
    }
}

@Composable
private fun AbsoluteBox(
    rect: AbsoluteRect,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = Modifier
            .offset(rect.x.dp, rect.y.dp)
            .size(rect.width.dp, rect.height.dp),
        contentAlignment = Alignment.Center,
        content = content,
    )
}

@Composable
private fun BoardBox(
    x: Float,
    y: Float,
    width: Float,
    height: Float,
    layout: ControlLayout,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = Modifier
            .offset(
                (layout.originX + x * layout.scale).dp,
                (layout.originY + (y - layout.baseY) * layout.scale).dp,
            )
            .size(dp(width, layout.scale), dp(height, layout.scale)),
        contentAlignment = Alignment.Center,
        content = content,
    )
}

@Composable
private fun BoardBox(
    x: Float,
    y: Float,
    width: Float,
    height: Float,
    scale: Float,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = Modifier
            .offset(dp(x, scale), dp(y, scale))
            .size(dp(width, scale), dp(height, scale)),
        contentAlignment = Alignment.Center,
        content = content,
    )
}

private fun dp(value: Float, scale: Float): Dp = (value * scale).dp

private fun triggerStickNavigation(
    positionX: Float,
    positionY: Float,
    width: Float,
    height: Float,
    currentTrigger: Int,
    onMoveGameLibrarySelection: (Int) -> Unit,
): Int {
    val nextTrigger = gridDeltaForPosition(positionX, positionY, width, height)
    if (nextTrigger != 0 && nextTrigger != currentTrigger) {
        onMoveGameLibrarySelection(nextTrigger)
    }
    return nextTrigger
}

private fun gridDeltaForPosition(positionX: Float, positionY: Float, width: Float, height: Float): Int {
    val horizontal = when {
        positionX < width * 0.35f -> -1
        positionX > width * 0.65f -> 1
        else -> 0
    }
    val vertical = when {
        positionY < height * 0.35f -> -GameLibraryColumns
        positionY > height * 0.65f -> GameLibraryColumns
        else -> 0
    }
    return horizontal + vertical
}

private fun stickButtonsForPosition(
    positionX: Float,
    positionY: Float,
    width: Float,
    height: Float,
): Set<NesButton> {
    val buttons = mutableSetOf<NesButton>()
    if (positionX < width * 0.35f) buttons += NesButton.Left
    if (positionX > width * 0.65f) buttons += NesButton.Right
    if (positionY < height * 0.35f) buttons += NesButton.Up
    if (positionY > height * 0.65f) buttons += NesButton.Down
    return buttons
}

private fun updateDirectionalButtons(
    previousButtons: Set<NesButton>,
    nextButtons: Set<NesButton>,
    onButtonChange: (NesButton, Boolean) -> Unit,
) {
    (previousButtons - nextButtons).forEach { onButtonChange(it, false) }
    (nextButtons - previousButtons).forEach { onButtonChange(it, true) }
}
