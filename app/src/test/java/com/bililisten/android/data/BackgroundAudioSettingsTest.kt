package com.bililisten.android.data

import android.content.Context
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class BackgroundAudioSettingsTest {
    private val context get() = RuntimeEnvironment.getApplication()

    @Test fun defaultsAreOptInAndBackgroundEditsPreserveExistingPlaybackSettings() {
        val store = PlayerSettingsStore(context)
        assertFalse(store.load().backgroundAudio.enabled)
        store.save(PlayerSettings(speed = 1.5f, playMode = PlayMode.SINGLE_LOOP, allowMobileStreaming = true, fadePlayback = false))
        store.saveBackgroundAudio(BackgroundAudioSettings(true, BackgroundSound.BROWN, .37f))
        val restored = PlayerSettingsStore(context).load()
        assertEquals(BackgroundAudioSettings(true, BackgroundSound.BROWN, .37f), restored.backgroundAudio)
        assertEquals(1.5f, restored.speed)
        assertEquals(PlayMode.SINGLE_LOOP, restored.playMode)
        assertTrue(restored.allowMobileStreaming)
        assertFalse(restored.fadePlayback)
    }

    @Test fun unknownSoundsAndInvalidVolumesRecoverSafely() {
        context.getSharedPreferences("player_settings", Context.MODE_PRIVATE).edit()
            .putString("background_sound", "removed-sound").putFloat("background_volume", Float.NaN).apply()
        val store = PlayerSettingsStore(context)
        assertEquals(BackgroundSound.RAIN, store.load().backgroundAudio.sound)
        assertEquals(.2f, store.load().backgroundAudio.volume)
        assertEquals(1f, store.saveBackgroundAudio(BackgroundAudioSettings(volume = 6f)).backgroundAudio.volume)
        assertEquals(0f, store.saveBackgroundAudio(BackgroundAudioSettings(volume = -1f)).backgroundAudio.volume)
    }
}
