package com.bililisten.android.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

enum class PlayMode {
    AUTO_NEXT,
    SINGLE_LOOP,
}

enum class AppSkin(val label: String) {
    MINT_NIGHT("薄荷夜色"),
    MIDNIGHT_BLUE("黑蓝夜色"),
}

data class PlayerSettings(
    val speed: Float = 1.0f,
    val playMode: PlayMode = PlayMode.AUTO_NEXT,
    val allowMobileStreaming: Boolean = false,
    val fadePlayback: Boolean = true,
    val backgroundAudio: BackgroundAudioSettings = BackgroundAudioSettings(),
    val musicLockScreen: Boolean = false,
    val skin: AppSkin = AppSkin.MINT_NIGHT,
    val recordCollectionSwitch: Boolean = false,
)

class PlayerSettingsStore(context: Context) {
    private val preferences: SharedPreferences =
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun load(): PlayerSettings {
        val storedSpeed = preferences.getFloat(KEY_SPEED, DEFAULT_SPEED)
        val storedMode = preferences.getString(KEY_PLAY_MODE, PlayMode.AUTO_NEXT.name)
        return PlayerSettings(
            speed = normalizeSpeed(storedSpeed),
            playMode = storedMode?.let(::parsePlayMode) ?: PlayMode.AUTO_NEXT,
            allowMobileStreaming = preferences.getBoolean(KEY_MOBILE_STREAMING, false),
            fadePlayback = preferences.getBoolean(KEY_FADE_PLAYBACK, true),
            musicLockScreen = preferences.getBoolean(KEY_MUSIC_LOCK_SCREEN, false),
            recordCollectionSwitch = preferences.getBoolean(KEY_RECORD_COLLECTION_SWITCH, false),
            skin = runCatching { AppSkin.valueOf(preferences.getString(KEY_SKIN, null).orEmpty()) }
                .getOrDefault(AppSkin.MINT_NIGHT),
            backgroundAudio = BackgroundAudioSettings(
                enabled = preferences.getBoolean(KEY_BACKGROUND_ENABLED, false),
                sound = runCatching { BackgroundSound.valueOf(preferences.getString(KEY_BACKGROUND_SOUND, null).orEmpty()) }
                    .getOrDefault(BackgroundSound.RAIN),
                volume = preferences.getFloat(KEY_BACKGROUND_VOLUME, BackgroundAudioSettings.DEFAULT_VOLUME),
            ).normalized(),
        )
    }

    fun saveSpeed(speed: Float): PlayerSettings {
        val normalizedSpeed = normalizeSpeed(speed)
        preferences.edit().putFloat(KEY_SPEED, normalizedSpeed).apply()
        return load()
    }

    fun savePlayMode(playMode: PlayMode): PlayerSettings {
        preferences.edit().putString(KEY_PLAY_MODE, playMode.name).apply()
        return load()
    }

    fun save(settings: PlayerSettings): PlayerSettings {
        val normalizedSpeed = normalizeSpeed(settings.speed)
        preferences.edit()
            .putFloat(KEY_SPEED, normalizedSpeed)
            .putString(KEY_PLAY_MODE, settings.playMode.name)
            .putBoolean(KEY_MOBILE_STREAMING, settings.allowMobileStreaming)
            .putBoolean(KEY_FADE_PLAYBACK, settings.fadePlayback)
            .putBoolean(KEY_MUSIC_LOCK_SCREEN, settings.musicLockScreen)
            .putBoolean(KEY_RECORD_COLLECTION_SWITCH, settings.recordCollectionSwitch)
            .putString(KEY_SKIN, settings.skin.name)
            .putBoolean(KEY_BACKGROUND_ENABLED, settings.backgroundAudio.enabled)
            .putString(KEY_BACKGROUND_SOUND, settings.backgroundAudio.sound.name)
            .putFloat(KEY_BACKGROUND_VOLUME, settings.backgroundAudio.normalized().volume)
            .apply()
        return load()
    }

    fun saveAllowMobileStreaming(allowed: Boolean): PlayerSettings {
        preferences.edit().putBoolean(KEY_MOBILE_STREAMING, allowed).apply()
        return load()
    }

    fun saveFadePlayback(enabled: Boolean): PlayerSettings {
        preferences.edit().putBoolean(KEY_FADE_PLAYBACK, enabled).apply()
        return load()
    }

    fun saveMusicLockScreen(enabled: Boolean): PlayerSettings {
        preferences.edit().putBoolean(KEY_MUSIC_LOCK_SCREEN, enabled).apply()
        return load()
    }

    fun saveSkin(skin: AppSkin): PlayerSettings {
        preferences.edit().putString(KEY_SKIN, skin.name).apply()
        return load()
    }

    fun saveRecordCollectionSwitch(enabled: Boolean): PlayerSettings {
        preferences.edit { putBoolean(KEY_RECORD_COLLECTION_SWITCH, enabled) }
        return load()
    }

    fun saveBackgroundAudio(settings: BackgroundAudioSettings): PlayerSettings {
        preferences.edit()
            .putBoolean(KEY_BACKGROUND_ENABLED, settings.enabled)
            .putString(KEY_BACKGROUND_SOUND, settings.sound.name)
            .putFloat(KEY_BACKGROUND_VOLUME, settings.normalized().volume)
            .apply()
        return load()
    }

    private fun parsePlayMode(raw: String): PlayMode = try {
        PlayMode.valueOf(raw)
    } catch (_: IllegalArgumentException) {
        PlayMode.AUTO_NEXT
    }

    private fun normalizeSpeed(speed: Float): Float {
        return SUPPORTED_SPEEDS.minByOrNull { candidate -> kotlin.math.abs(candidate - speed) } ?: DEFAULT_SPEED
    }

    companion object {
        const val DEFAULT_SPEED = 1.0f
        val SUPPORTED_SPEEDS = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)

        private const val PREFERENCES_NAME = "player_settings"
        private const val KEY_SPEED = "speed"
        private const val KEY_PLAY_MODE = "play_mode"
        private const val KEY_MOBILE_STREAMING = "allow_mobile_streaming"
        private const val KEY_FADE_PLAYBACK = "fade_playback"
        private const val KEY_MUSIC_LOCK_SCREEN = "music_lock_screen"
        private const val KEY_SKIN = "app_skin"
        private const val KEY_RECORD_COLLECTION_SWITCH = "record_collection_switch"
        private const val KEY_BACKGROUND_ENABLED = "background_enabled"
        private const val KEY_BACKGROUND_SOUND = "background_sound"
        private const val KEY_BACKGROUND_VOLUME = "background_volume"
    }
}
