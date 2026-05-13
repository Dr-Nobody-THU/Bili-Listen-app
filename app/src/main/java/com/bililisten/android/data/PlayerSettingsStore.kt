package com.bililisten.android.data

import android.content.Context
import android.content.SharedPreferences

enum class PlayMode {
    AUTO_NEXT,
    SINGLE_LOOP,
}

data class PlayerSettings(
    val speed: Float = 1.0f,
    val playMode: PlayMode = PlayMode.AUTO_NEXT,
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
    }
}
