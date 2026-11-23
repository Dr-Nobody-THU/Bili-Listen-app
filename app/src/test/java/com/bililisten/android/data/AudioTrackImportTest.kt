package com.bililisten.android.data

import android.net.Network
import com.bililisten.android.TestFixtures
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowNetwork
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class AudioTrackImportTest {
    private val id = 1486384L
    private val context get() = RuntimeEnvironment.getApplication()
    // Fields captured from the user-supplied AU, including a related BV that must not replace it.
    private fun metadata() = JSONObject("""{
        "code":0,"data":{"id":1486384,"uid":14934648,"uname":"阿泉君LIN",
        "title":"【SCP基金会】处处吻（填词/混剪pv）（赤羽x洛天依）",
        "author":"洛天依 · 赤羽 · 王牌p","duration":197,"passtime":1586605949,
        "cover":"http://i0.hdslb.com/bfs/music/25d6e3bae9c6b4ffbfea5b4589d653ffcac2cbdb.jpg",
        "bvid":"BV1tp4y1C7wf","cid":174967092,"statistic":{"play":244813}}}
        """)
    private fun urls() = JSONObject("""{"code":0,"data":{"sid":1486384,"type":1,
        "cdns":["https://audio.example/test.m4a","//backup.example/test.m4a","https://audio.example/test.m4a"]}}""")

    @Test fun acceptsMobileDesktopShareAndRawAuWhileKeepingBvPartParsing() {
        val parser = BilibiliLinkParser()
        listOf("https://m.bilibili.com/audio/au1486384", "https://www.bilibili.com/audio/au1486384?from=search",
            "AU1486384", "分享音频 https://m.bilibili.com/audio/au1486384?p=9 试听").forEach {
            assertEquals(VideoReference("au$id"), parser.parse(it))
        }
        assertEquals(VideoReference("BV18h411v7Lp", 3), parser.parse("https://www.bilibili.com/video/BV18h411v7Lp?p=3"))
        listOf("au0", "au-5", "au9999999999999999999999999", "au1486384x").forEach {
            assertThrows(IllegalArgumentException::class.java) { parser.parse(it) }
        }
    }

    @Test fun importsTheActualAuAndResolvesItsOwnAudioEndpointWithBackupUrls() {
        val requests = mutableListOf<String>()
        val api = BilibiliApi { url, _ ->
            requests += url
            when {
                url.contains("/song/info?sid=$id") -> metadata()
                url.contains("/web/url?sid=$id&privilege=2&quality=2") -> urls()
                else -> error("Unexpected video endpoint: $url")
            }
        }
        val repository = ListenRepository(context, api)
        val playback = repository.planPlayback("https://m.bilibili.com/audio/au1486384", ResumePolicy.PREFER_REQUESTED_ITEM)
        assertEquals("audio:$id", playback.queue.collectionId)
        assertEquals(1, playback.queue.items.size)
        val item = playback.plan.item
        assertEquals(ContentKey("au$id", id), item.key)
        assertEquals(197_000L, item.durationMs)
        assertEquals("阿泉君LIN", item.authorName)
        assertEquals(14934648L, item.authorMid)
        assertTrue(item.coverUrl!!.startsWith("https://"))
        assertEquals(1_586_605_949_000L, item.publishedAtMs)
        val audio = repository.resolveAudio(playback)
        assertEquals(item.key, audio.key)
        assertEquals(AudioStreamKind.AUDIO_TRACK, audio.selected.kind)
        assertEquals("https://audio.example/test.m4a", audio.selected.url)
        assertEquals(listOf("https://backup.example/test.m4a"), audio.selected.backupUrls)
        assertEquals(2, requests.size)
    }

    @Test fun favoritesProgressMixedSpecialQueueAndCacheSurviveReopening() {
        val repository = ListenRepository(context, BilibiliApi { _, _ -> metadata() })
        val playback = repository.planPlayback("au1486384", ResumePolicy.PREFER_REQUESTED_ITEM)
        repository.saveFavorite(playback)
        repository.saveProgress(playback, 42_000)
        repository.toggleSpecialFavorite(playback)
        val video = TestFixtures.playback(index = 2)
        repository.toggleSpecialFavorite(video)
        val cached = AudioCacheFiles(File(context.filesDir, "audio_cache")).file(playback.plan.item)
        cached.apply { parentFile!!.mkdirs(); writeBytes(ByteArray(20)) }
        val reopened = ListenRepository(context)
        val restored = reopened.loadFavorite("audio:$id")!!.playback
        assertEquals(playback.plan.item.key, restored.plan.item.key)
        assertEquals(42_000L, restored.plan.startPositionMs)
        assertNotNull(reopened.cachedAudioUri(restored.plan.item))
        assertEquals(CachePhase.CACHED, reopened.cacheSnapshot(restored).entries[restored.plan.item.key.asStoreKey()]!!.phase)
        val special = reopened.loadFavorite(SpecialFavorites.ID)!!.playback
        assertEquals(setOf(playback.plan.item.key, video.plan.item.key), special.queue.items.map { it.key }.toSet())
        val next = reopened.adjacentPlayback(special, 1)!!
        assertNotEquals(special.plan.item.key, next.plan.item.key)
        assertEquals(special.plan.item.key, reopened.adjacentPlayback(next, 1)!!.plan.item.key)
    }

    @Test fun cacheResolverUsesTheProvidedWifiNetworkForAuRequests() {
        val wifi = ShadowNetwork.newInstance(42)
        var requestedNetwork: Network? = null
        val api = BilibiliApi { _, network -> requestedNetwork = network; urls() }
        BilibiliAudioResolver(api).resolve(AudioTrackData.queue(id, metadata().getJSONObject("data")).items.single(), wifi)
        assertSame(wifi, requestedNetwork)
    }

    @Test fun unavailableMismatchedAndEmptyAudioResponsesFailClearly() {
        assertThrows(IllegalStateException::class.java) {
            BilibiliApi { _, _ -> JSONObject("""{"code":7201006,"msg":"音频不存在"}""") }.fetchAudioInfo(id)
        }
        assertThrows(IllegalStateException::class.java) {
            AudioTrackData.queue(id, metadata().getJSONObject("data").put("id", 3))
        }
        val key = ContentKey("au$id", id)
        assertThrows(IllegalStateException::class.java) {
            AudioTrackData.audio(key, "test", 197_000, JSONObject("""{"sid":1486384,"cdns":[]}"""))
        }
        assertThrows(IllegalStateException::class.java) {
            AudioTrackData.audio(key, "test", 197_000, urls().getJSONObject("data").put("sid", 3))
        }
    }
}
