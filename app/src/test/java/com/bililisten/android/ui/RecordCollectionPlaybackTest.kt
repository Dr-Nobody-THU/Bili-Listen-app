package com.bililisten.android.ui

import androidx.lifecycle.ViewModelStore
import com.bililisten.android.TestFixtures
import com.bililisten.android.data.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class RecordCollectionPlaybackTest {
    @Test fun confirmationStartsTheSavedPositionOnceAndNeverRestartsTheCurrentCollection() {
        val application = RuntimeEnvironment.getApplication()
        val repository = AppGraph.repository(application)
        val target = TestFixtures.playback(index = 2)
        val id = repository.favoriteIdOf(target)
        repository.saveFavorite(target)
        repository.saveProgress(target, 42_000)
        val model = ListenViewModel(application)
        val store = ViewModelStore().apply { put("listen", model) }
        try {
            model.switchFavoriteFromRecord(id)
            val requested = playbackRequests().single()
            assertEquals(target.plan.item.key, requested.plan.item.key)
            assertEquals(42_000L, requested.plan.startPositionMs)
            assertEquals(42_000L, model.uiState.playbackControl.positionMs)
            assertEquals(id, model.uiState.currentFavoriteId)
            repository.saveProgress(target, 65_000)
            model.switchFavoriteFromRecord(id)
            assertTrue(playbackRequests().isEmpty())
            assertEquals(65_000L, repository.loadFavorite(id)!!.playback.plan.startPositionMs)
            model.switchFavoriteFromRecord("removed-favorite")
            assertTrue(playbackRequests().isEmpty())
            assertEquals(id, model.uiState.currentFavoriteId)
        } finally {
            store.clear()
        }
    }

    @Test fun optingInAndOutPersistsWithoutChangingOtherSettingsOrStartingPlayback() {
        val application = RuntimeEnvironment.getApplication()
        val settings = PlayerSettings(speed = 1.5f, playMode = PlayMode.SINGLE_LOOP, skin = AppSkin.MIDNIGHT_BLUE,
            backgroundAudio = BackgroundAudioSettings(true, BackgroundSound.RAIN, .3f))
        PlayerSettingsStore(application).save(settings)
        val model = ListenViewModel(application)
        val store = ViewModelStore().apply { put("listen", model) }
        try {
            model.updateRecordCollectionSwitch(true)
            assertEquals(settings.copy(recordCollectionSwitch = true), PlayerSettingsStore(application).load())
            model.updateRecordCollectionSwitch(false)
            assertEquals(settings, PlayerSettingsStore(application).load())
            assertTrue(playbackRequests().isEmpty())
        } finally {
            store.clear()
        }
    }

    private fun playbackRequests(): List<PlannedPlayback> =
        generateSequence { shadowOf(RuntimeEnvironment.getApplication()).nextStartedService }
            .mapNotNull { it.getStringExtra("playback_json")?.let(PlaybackJsonCodec::decode) }.toList()
}
