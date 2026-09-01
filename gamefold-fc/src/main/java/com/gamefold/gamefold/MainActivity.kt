package com.gamefold.gamefold

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.util.Base64
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.gamefold.gamefold.ui.GameFoldScreen
import com.gamefold.gamefold.ui.theme.GameFoldTheme
import java.io.File

class MainActivity : ComponentActivity() {
    private var isSupportedUsageMode by mutableStateOf(false)
    private var selectedRom by mutableStateOf<RomSelection?>(null)
    private val builtInGameLibrary = listOf(
        builtInGame(DefaultGameId, "超级马里奥兄弟", "超级马里奥兄弟"),
        builtInGame("super_mario_2", "超级马里奥兄弟2", "超级马里奥兄弟2"),
        builtInGame("super_mario_3", "超级马里奥兄弟3", "超级马里奥兄弟3"),
        builtInGame("fc_genshi", "FC原人", "FC原人"),
        builtInGame("contra", "魂斗罗", "魂斗罗"),
        builtInGame("super_contra", "超级魂斗罗", "超级魂斗罗"),
        builtInGame("battle_city", "坦克大战", "坦克大战"),
        builtInGame("adventure_island", "冒险岛", "冒险岛"),
        builtInGame("adventure_island_2", "冒险岛2", "冒险岛2"),
        builtInGame("adventure_island_3", "冒险岛3", "冒险岛3"),
        builtInGame("double_dragon", "双截龙", "双截龙"),
        builtInGame("double_dragon_2", "双截龙2", "双截龙2"),
        builtInGame("double_dragon_3", "双截龙3", "双截龙3"),
        builtInGame("double_dragon_4", "双截龙4", "双截龙4"),
        builtInGame("ninja_gaiden", "忍者龙剑传", "忍者龙剑传"),
        builtInGame("ninja_gaiden_2", "忍者龙剑传2 - 暗黑邪神剑", "忍者龙剑传2 - 暗黑邪神剑"),
        builtInGame("ninja_gaiden_3", "忍者龙剑传3 - 黄泉方舟", "忍者龙剑传3 - 黄泉方舟"),
        builtInGame("tmnt", "激龟忍者传", "激龟忍者传"),
        builtInGame("tmnt_tournament", "激龟快打", "激龟快打"),
        builtInGame("tmnt_2", "忍者神龟2", "忍者神龟2"),
        builtInGame("tmnt_3", "忍者神龟3", "忍者神龟3"),
        builtInGame("chip_dale", "松鼠大作战", "松鼠大作战"),
        builtInGame("chip_dale_2", "松鼠大作战2", "松鼠大作战2"),
        builtInGame("happy_cat", "快乐猫", "快乐猫"),
        builtInGame("rockman", "洛克人", "洛克人"),
        builtInGame("batman", "蝙蝠侠", "蝙蝠侠"),
        builtInGame("shadow_of_ninja", "赤影战士", "赤影战士"),
        builtInGame("final_fight", "快打旋风", "快打旋风"),
        builtInGame("kung_fu", "功夫", "功夫"),
        builtInGame("mitsume", "三目童子", "三目童子"),
        builtInGame("circus", "马戏团", "马戏团"),
        builtInGame("green_beret", "绿色兵团", "绿色兵团"),
        builtInGame("wild_west", "荒野大镖客", "荒野大镖客"),
        builtInGame("jail_break", "脱狱", "脱狱"),
        builtInGame("human_weapon", "人间兵器", "人间兵器"),
        builtInGame("river_city_ransom", "热血物语", "热血物语"),
        builtInGame("nikketsu_soccer", "热血足球联盟", "热血足球联盟"),
        builtInGame("nikketsu_basketball", "热血篮球", "热血篮球"),
        builtInGame("nikketsu_fighting", "热血格斗传说", "热血格斗传说"),
    )
    private var importedGameLibrary by mutableStateOf<List<GameLibraryItem>>(emptyList())
    private var removedBuiltInGameIds by mutableStateOf<Set<String>>(emptySet())
    private val gameLibrary: List<GameLibraryItem>
        get() = builtInGameLibrary.filterNot { it.id in removedBuiltInGameIds } + importedGameLibrary

    private val romPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            importRom(uri)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )

        updateUsageMode()
        removedBuiltInGameIds = loadRemovedBuiltInGames()
        importedGameLibrary = loadImportedGames()

        setContent {
            GameFoldTheme {
                GameFoldScreen(
                    isSupportedUsageMode = isSupportedUsageMode,
                    rom = selectedRom,
                    gameLibrary = gameLibrary,
                    onSelectGame = { game ->
                        if (game.isAvailable) {
                            selectedRom = installGame(game).also { rememberRom(it) }
                            true
                        } else {
                            false
                        }
                    },
                    onImportGame = { romPicker.launch(arrayOf("application/octet-stream", "application/x-nes-rom", "*/*")) },
                    onDeleteGame = { game -> deleteGame(game) },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        updateUsageMode()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        updateUsageMode()
    }

    override fun onMultiWindowModeChanged(isInMultiWindowMode: Boolean, newConfig: Configuration) {
        super.onMultiWindowModeChanged(isInMultiWindowMode, newConfig)
        updateUsageMode()
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration,
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        updateUsageMode()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) updateUsageMode()
    }

    private fun updateUsageMode() {
        isSupportedUsageMode = isFullInnerLandscapeMode()
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        WindowCompat.setDecorFitsSystemWindows(window, false)
        hideSystemBars()
    }

    private fun isFullInnerLandscapeMode(): Boolean {
        val config = resources.configuration
        return !isInMultiWindowMode &&
            !isInPictureInPictureMode &&
            config.orientation == Configuration.ORIENTATION_LANDSCAPE &&
            config.smallestScreenWidthDp >= InnerDisplaySmallestWidthDp &&
            config.screenWidthDp >= InnerDisplayMinWidthDp &&
            config.screenHeightDp >= InnerDisplayMinHeightDp
    }

    private fun hideSystemBars() {
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    private fun copyRomToPrivateStorage(uri: Uri): RomSelection {
        val displayName = readDisplayName(uri) ?: "selected.nes"
        val safeName = displayName
            .replace(Regex("[^A-Za-z0-9._-]"), "_")
            .ifBlank { "selected.nes" }
        val romDir = File(filesDir, "roms").apply { mkdirs() }
        val target = File(romDir, safeName)

        contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "Unable to open selected ROM." }
            target.outputStream().use { output ->
                input.copyTo(output)
            }
        }

        return RomSelection(
            path = target.absolutePath,
            name = displayName,
            gameId = storageKeyFor(safeName),
            loadToken = System.nanoTime(),
        )
    }

    private fun readDisplayName(uri: Uri): String? {
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null).use { cursor ->
            if (cursor != null && cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0) return cursor.getString(index)
            }
        }
        return uri.lastPathSegment?.substringAfterLast('/')
    }

    private fun rememberRom(rom: RomSelection): RomSelection {
        val prefs = getSharedPreferences(PrefsName, MODE_PRIVATE)
        val recent = prefs.getString(RecentRomsKey, null)
            ?.split(RecentRomsSeparator)
            ?.filter { it.isNotBlank() }
            ?.toMutableList()
            ?: mutableListOf()
        val entry = "${rom.gameId}|${rom.name}|${rom.path}"
        recent.remove(entry)
        recent.add(0, entry)

        prefs.edit()
            .putString(LastRomPathKey, rom.path)
            .putString(LastRomNameKey, rom.name)
            .putString(LastRomGameIdKey, rom.gameId)
            .putString(RecentRomsKey, recent.take(8).joinToString(RecentRomsSeparator))
            .apply()

        return rom
    }

    private fun restoreLastRom(): RomSelection? {
        val prefs = getSharedPreferences(PrefsName, MODE_PRIVATE)
        val path = prefs.getString(LastRomPathKey, null) ?: return null
        val file = File(path)
        if (!file.exists()) return null
        val name = prefs.getString(LastRomNameKey, file.name) ?: file.name
        val gameId = prefs.getString(LastRomGameIdKey, null) ?: storageKeyFor(name)
        return RomSelection(path = path, name = name, gameId = gameId, loadToken = System.nanoTime())
    }

    private fun installBuiltInGame(game: GameLibraryItem): RomSelection {
        val assetPath = requireNotNull(game.assetPath) { "Game ${game.id} has no ROM asset." }
        val romDir = File(filesDir, "roms").apply { mkdirs() }
        val target = File(romDir, game.fileName)

        if (!target.exists() || target.length() == 0L) {
            assets.open(assetPath).use { input ->
                target.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
        }

        return RomSelection(
            path = target.absolutePath,
            name = game.displayName,
            gameId = game.id,
            loadToken = System.nanoTime(),
        )
    }

    private fun installGame(game: GameLibraryItem): RomSelection =
        game.importedPath?.let { path ->
            RomSelection(
                path = path,
                name = game.displayName,
                gameId = game.id,
                loadToken = System.nanoTime(),
            )
        } ?: installBuiltInGame(game)

    private fun importRom(uri: Uri) {
        val displayName = readDisplayName(uri) ?: "imported.nes"
        if (!displayName.lowercase().endsWith(".nes")) {
            Toast.makeText(this, "请选择 .nes 格式的 FC ROM", Toast.LENGTH_SHORT).show()
            return
        }
        val safeName = displayName
            .replace(Regex("[^A-Za-z0-9._-]"), "_")
            .ifBlank { "imported.nes" }
            .let { if (it.lowercase().endsWith(".nes")) it else "$it.nes" }
        val romDir = File(filesDir, "imported-roms").apply { mkdirs() }
        val initialTarget = File(romDir, safeName)
        val target = if (!initialTarget.exists()) {
            initialTarget
        } else {
            val baseName = safeName.removeSuffix(".nes")
            File(romDir, "${baseName}_${System.currentTimeMillis()}.nes")
        }

        contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "Unable to open imported ROM." }
            target.outputStream().use { output -> input.copyTo(output) }
        }

        val game = GameLibraryItem(
            id = "imported_${storageKeyFor(target.name)}",
            title = displayName.substringBeforeLast('.', displayName),
            subtitle = "FC / NES",
            assetPath = null,
            coverAssetPath = null,
            fileName = target.name,
            displayName = displayName,
            isAvailable = true,
            importedPath = target.absolutePath,
            coverResourceName = nextImportedCoverResourceName(),
        )
        importedGameLibrary = (importedGameLibrary.filterNot { it.id == game.id } + game)
        saveImportedGames(importedGameLibrary)
        selectedRom = RomSelection(
            path = target.absolutePath,
            name = game.displayName,
            gameId = game.id,
            loadToken = System.nanoTime(),
        ).also { rememberRom(it) }
    }

    private fun nextImportedCoverResourceName(): String {
        val prefs = getSharedPreferences(PrefsName, MODE_PRIVATE)
        val index = prefs.getInt(ImportedCoverIndexKey, 0)
        val cover = importedCoverResourceNames[index % importedCoverResourceNames.size]
        prefs.edit()
            .putInt(ImportedCoverIndexKey, (index + 1) % importedCoverResourceNames.size)
            .apply()
        return cover
    }

    private fun loadImportedGames(): List<GameLibraryItem> {
        val prefs = getSharedPreferences(PrefsName, MODE_PRIVATE)
        return prefs.getString(ImportedGamesKey, null)
            ?.split(RecentRomsSeparator)
            ?.mapNotNull { decodeImportedGame(it) }
            ?: emptyList()
    }

    private fun saveImportedGames(games: List<GameLibraryItem>) {
        val encoded = games.joinToString(RecentRomsSeparator) { game ->
            listOf(game.id, game.title, game.displayName, game.importedPath.orEmpty(), game.coverResourceName.orEmpty())
                .joinToString("|") { value ->
                    Base64.encodeToString(value.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
                }
        }
        getSharedPreferences(PrefsName, MODE_PRIVATE)
            .edit()
            .putString(ImportedGamesKey, encoded)
            .apply()
    }

    private fun decodeImportedGame(record: String): GameLibraryItem? {
        val values = record.split('|')
        if (values.size !in 4..5) return null
        val decoded = values.mapNotNull { value ->
            runCatching { String(Base64.decode(value, Base64.DEFAULT), Charsets.UTF_8) }.getOrNull()
        }
        if (decoded.size != values.size) return null
        val path = decoded[3]
        if (!File(path).exists()) return null
        return GameLibraryItem(
            id = decoded[0],
            title = decoded[1],
            subtitle = "FC / NES",
            assetPath = null,
            coverAssetPath = null,
            fileName = File(path).name,
            displayName = decoded[2],
            isAvailable = true,
            importedPath = path,
            coverResourceName = decoded.getOrNull(4)?.takeIf { it.isNotBlank() }
                ?: importedCoverResourceNames[Math.floorMod(decoded[0].hashCode(), importedCoverResourceNames.size)],
        )
    }

    private fun loadRemovedBuiltInGames(): Set<String> =
        getSharedPreferences(PrefsName, MODE_PRIVATE)
            .getStringSet(RemovedBuiltInGamesKey, emptySet())
            ?.toSet()
            ?: emptySet()

    private fun saveRemovedBuiltInGames(ids: Set<String>) {
        getSharedPreferences(PrefsName, MODE_PRIVATE)
            .edit()
            .putStringSet(RemovedBuiltInGamesKey, ids)
            .apply()
    }

    private fun deleteGame(game: GameLibraryItem) {
        game.importedPath?.let { path ->
            File(path).delete()
            importedGameLibrary = importedGameLibrary.filterNot { it.id == game.id }
            saveImportedGames(importedGameLibrary)
        } ?: run {
            removedBuiltInGameIds = removedBuiltInGameIds + game.id
            saveRemovedBuiltInGames(removedBuiltInGameIds)
        }

        if (selectedRom?.gameId == game.id) {
            selectedRom = null
        }
    }

    private fun storageKeyFor(value: String): String =
        value
            .lowercase()
            .replace(Regex("[^a-z0-9._-]"), "_")
            .ifBlank { "game" }

    private fun builtInGame(id: String, title: String, folder: String): GameLibraryItem =
        GameLibraryItem(
            id = id,
            title = title,
            subtitle = "FC / NES",
            assetPath = "$GameLibraryAssetRoot/$folder/game.nes",
            coverAssetPath = "$GameLibraryAssetRoot/$folder/cover.png",
            fileName = "$id.nes",
            displayName = "$title.nes",
            isAvailable = true,
        )

    private companion object {
        const val PrefsName = "gamefold"
        const val LastRomPathKey = "last_rom_path"
        const val LastRomNameKey = "last_rom_name"
        const val LastRomGameIdKey = "last_rom_game_id"
        const val RecentRomsKey = "recent_roms"
        const val ImportedGamesKey = "imported_games"
        const val RemovedBuiltInGamesKey = "removed_built_in_games"
        const val ImportedCoverIndexKey = "imported_cover_index"
        const val RecentRomsSeparator = "\n"
        const val DefaultGameId = "super_mario_bros"
        const val GameLibraryAssetRoot = "game_library/fc_rom_select"
        const val InnerDisplaySmallestWidthDp = 600
        const val InnerDisplayMinWidthDp = 700
        const val InnerDisplayMinHeightDp = 560
        val importedCoverResourceNames = listOf(
            "game_import_cover_01",
            "game_import_cover_02",
            "game_import_cover_03",
            "game_import_cover_04",
            "game_import_cover_05",
            "game_import_cover_06",
            "game_import_cover_07",
            "game_import_cover_08",
        )
    }
}

data class RomSelection(
    val path: String,
    val name: String,
    val gameId: String,
    val loadToken: Long = 0L,
)

data class GameLibraryItem(
    val id: String,
    val title: String,
    val subtitle: String,
    val assetPath: String?,
    val coverAssetPath: String?,
    val fileName: String,
    val displayName: String,
    val isAvailable: Boolean,
    val importedPath: String? = null,
    val coverResourceName: String? = null,
)
