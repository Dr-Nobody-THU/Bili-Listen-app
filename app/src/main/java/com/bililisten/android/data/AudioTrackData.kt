package com.bililisten.android.data

import org.json.JSONObject

/** AU audio is its own content, even when its metadata also links to a BV video. */
internal object AudioTrackData {
    fun queue(id: Long, data: JSONObject): ResolvedQueue {
        check(data.optLong("id") == id) { "B 站返回的音频编号不匹配" }
        val title = data.optString("title").trim().ifEmpty { "音频 AU$id" }
        val collection = "audio:$id"
        val item = QueueItem(
            key = ContentKey("au$id", id), title = title,
            durationMs = data.optLong("duration").coerceAtLeast(0) * 1000L,
            pageNumber = 1, orderInCollection = 0, collectionId = collection, collectionTitle = title,
            coverUrl = data.optString("cover").takeIf { it.isNotBlank() }?.let(::https),
            authorMid = data.optLong("uid").takeIf { it > 0 },
            authorName = data.optString("uname").ifBlank { data.optString("author") }.takeIf { it.isNotBlank() },
            authorAvatarUrl = null,
            viewCount = data.optJSONObject("statistic")?.optLong("play")?.coerceAtLeast(0) ?: 0L,
            publishedAtMs = data.optLong("passtime").takeIf { it > 0 }?.times(1000L)
                ?: data.optLong("ctime").coerceAtLeast(0),
            description = data.optString("intro").takeIf { it.isNotBlank() },
        )
        return ResolvedQueue(collection, title, listOf(item), 0)
    }

    fun audio(key: ContentKey, title: String, durationMs: Long, data: JSONObject): ResolvedAudio {
        check(data.optLong("sid") == key.audioId) { "B 站返回的音频播放编号不匹配" }
        val cdns = data.optJSONArray("cdns")
        val urls = (0 until (cdns?.length() ?: 0)).map { cdns!!.optString(it).trim() }
            .map(::https).filter { it.startsWith("https://") }.distinct()
        check(urls.isNotEmpty()) { "该音频暂无可播放地址，可能已下架或需要访问权限" }
        val source = AudioStreamSource(data.optInt("type").coerceAtLeast(0), urls.first(), urls.drop(1),
            0L, null, null, AudioStreamKind.AUDIO_TRACK)
        return ResolvedAudio(key, title, durationMs, source, listOf(source))
    }

    private fun https(value: String): String = when {
        value.startsWith("//") -> "https:$value"
        value.startsWith("http://") -> "https:" + value.removePrefix("http:")
        else -> value
    }
}
