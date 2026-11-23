package com.bililisten.android.data

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.net.Network
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import java.util.concurrent.atomic.AtomicReference
import java.util.zip.GZIPInputStream

data class VideoReference(
    val bvid: String,
    val page: Int = 1,
)

data class ContentKey(
    // Keep the persisted field name for existing records; pure audio uses a canonical au ID.
    val bvid: String,
    val cid: Long,
) {
    init {
        require(BilibiliIds.isValidBvid(bvid) || BilibiliIds.audioId(bvid) != null) { "Invalid content id: $bvid" }
        require(cid > 0L) { "cid must be > 0." }
    }

    fun asStoreKey(): String = "$bvid:$cid"
    val audioId: Long? get() = BilibiliIds.audioId(bvid)
}

data class QueueItem(
    val key: ContentKey,
    val title: String,
    val durationMs: Long,
    val pageNumber: Int,
    val orderInCollection: Int,
    val collectionId: String?,
    val collectionTitle: String?,
    val coverUrl: String?,
    val authorMid: Long?,
    val authorName: String?,
    val authorAvatarUrl: String?,
    val viewCount: Long,
    val publishedAtMs: Long,
    val description: String?,
) {
    init {
        require(title.isNotBlank()) { "title must not be blank." }
        require(durationMs >= 0L) { "durationMs must be >= 0." }
        require(pageNumber >= 1) { "pageNumber must be >= 1." }
        require(orderInCollection >= 0) { "orderInCollection must be >= 0." }
        require(viewCount >= 0L) { "viewCount must be >= 0." }
        require(publishedAtMs >= 0L) { "publishedAtMs must be >= 0." }
        require((collectionId == null) == (collectionTitle == null)) {
            "collectionId and collectionTitle must either both be null or both be present."
        }
    }
}

data class ResolvedQueue(
    val collectionId: String?,
    val collectionTitle: String?,
    val items: List<QueueItem>,
    val requestedIndex: Int,
) {
    init {
        require(items.isNotEmpty()) { "items must not be empty." }
        require(requestedIndex in items.indices) { "requestedIndex out of range: $requestedIndex" }
        require((collectionId == null) == (collectionTitle == null)) {
            "collectionId and collectionTitle must either both be null or both be present."
        }
    }
}

enum class CreatorCollectionType {
    SEASON,
    SERIES,
}

data class CreatorCollectionSummary(
    val id: String,
    val type: CreatorCollectionType,
    val collectionId: Long,
    val authorMid: Long,
    val authorName: String?,
    val authorAvatarUrl: String?,
    val title: String,
    val coverUrl: String?,
    val itemCount: Int,
    val leadBvid: String?,
) {
    init {
        require(id.isNotBlank()) { "id must not be blank." }
        require(collectionId > 0L) { "collectionId must be > 0." }
        require(authorMid > 0L) { "authorMid must be > 0." }
        require(title.isNotBlank()) { "title must not be blank." }
        require(itemCount >= 0) { "itemCount must be >= 0." }
    }
}

data class PlaybackProgress(
    val key: ContentKey,
    val positionMs: Long,
    val durationMs: Long,
    val finished: Boolean,
    val updatedAt: Long,
)

data class CollectionSession(
    val collectionId: String,
    val currentIndex: Int,
    val updatedAt: Long,
) {
    init {
        require(collectionId.isNotBlank()) { "collectionId must not be blank." }
        require(currentIndex >= 0) { "currentIndex must be >= 0." }
    }
}

data class StartPlan(
    val startIndex: Int,
    val item: QueueItem,
    val startPositionMs: Long,
    val reason: String,
) {
    init {
        require(startIndex >= 0) { "startIndex must be >= 0." }
        require(startPositionMs >= 0L) { "startPositionMs must be >= 0." }
        require(reason.isNotBlank()) { "reason must not be blank." }
    }
}

enum class ResumePolicy {
    PREFER_REQUESTED_ITEM,
    PREFER_SAVED_COLLECTION_POSITION,
}

enum class CollectionPlaybackOrder {
    COLLECTION_DEFAULT,
    PUBLISHED_ASC,
    PUBLISHED_DESC,
    FAVORITED_ASC,
    FAVORITED_DESC,
    MANUAL;

    val usesPublicationTime: Boolean get() = this == PUBLISHED_ASC || this == PUBLISHED_DESC

    fun forCollection(collectionId: String?): CollectionPlaybackOrder = if (collectionId == SpecialFavorites.ID) {
        when (this) {
            COLLECTION_DEFAULT, PUBLISHED_ASC -> FAVORITED_ASC
            PUBLISHED_DESC -> FAVORITED_DESC
            else -> this
        }
    } else {
        when (this) {
            FAVORITED_ASC, FAVORITED_DESC, MANUAL -> COLLECTION_DEFAULT
            else -> this
        }
    }
}

enum class AudioStreamKind {
    AUDIO_TRACK,
    DASH_AUDIO,
    DASH_FLAC,
    DASH_DOLBY,
    MUXED_FALLBACK,
}

data class AudioStreamSource(
    val id: Int,
    val url: String,
    val backupUrls: List<String>,
    val bandwidth: Long,
    val codec: String?,
    val mimeType: String?,
    val kind: AudioStreamKind,
) {
    init {
        require(id >= 0) { "id must be >= 0." }
        require(url.isNotBlank()) { "url must not be blank." }
        require(bandwidth >= 0L) { "bandwidth must be >= 0." }
    }
}

data class ResolvedAudio(
    val key: ContentKey,
    val title: String,
    val durationMs: Long,
    val selected: AudioStreamSource,
    val candidates: List<AudioStreamSource>,
)

data class PlannedPlayback(
    val queue: ResolvedQueue,
    val plan: StartPlan,
)

data class FavoriteCollection(
    val id: String,
    val playback: PlannedPlayback,
    val updatedAt: Long,
    val listeningTimeMs: Long = 0L,
) {
    val title: String
        get() = playback.queue.collectionTitle ?: playback.plan.item.collectionTitle ?: playback.plan.item.title

    val coverUrl: String?
        get() = playback.plan.item.coverUrl ?: playback.queue.items.firstOrNull()?.coverUrl

    val itemCount: Int
        get() = playback.queue.items.size
}

data class AdvanceOutcome(
    val completedItem: QueueItem,
    val nextPlayback: PlannedPlayback?,
    val reachedEnd: Boolean,
)

object BilibiliIds {
    private val bvidPattern = Regex("BV[0-9A-Za-z]{10}")
    private val audioIdPattern = Regex("au[1-9][0-9]*")

    fun isValidBvid(value: String?): Boolean = value != null && bvidPattern.matches(value)
    fun audioId(value: String): Long? = value.takeIf { audioIdPattern.matches(it) }
        ?.removePrefix("au")?.toLongOrNull()?.takeIf { it > 0 }
}

class BilibiliLinkParser {
    private val audioPattern = Regex("(?<![A-Za-z0-9])au([1-9][0-9]*)(?![A-Za-z0-9])", RegexOption.IGNORE_CASE)
    private val bvidPattern = Regex("(BV[0-9A-Za-z]{10})")
    private val pagePattern = Regex("(^|[?&#])p=(\\d+)")

    fun parse(input: String): VideoReference {
        val raw = input.trim()
        require(raw.isNotEmpty()) { "Input must not be blank." }

        audioPattern.find(raw)?.groupValues?.get(1)?.toLongOrNull()?.takeIf { it > 0 }?.let {
            return VideoReference(bvid = "au$it")
        }

        val bvid = bvidPattern.find(raw)?.groupValues?.get(1)
            ?: throw IllegalArgumentException("请输入 BV 号、AU 号或 B 站视频／音频链接")
        val page = pagePattern.find(raw)?.groupValues?.get(2)?.toIntOrNull()?.coerceAtLeast(1) ?: 1
        return VideoReference(bvid = bvid, page = page)
    }
}

class BilibiliShortLinkResolver {
    private val fullShortLinkPattern =
        Regex("""https?://(?:b23\.tv|bili22\.cn|bili23\.cn|bili2233\.cn)/[0-9A-Za-z]+""", RegexOption.IGNORE_CASE)
    private val nakedShortLinkPattern =
        Regex("""(?:b23\.tv|bili22\.cn|bili23\.cn|bili2233\.cn)/[0-9A-Za-z]+""", RegexOption.IGNORE_CASE)

    fun resolve(input: String): String {
        val shortUrl = extractShortUrl(input) ?: return input
        return expand(shortUrl) ?: input
    }

    private fun extractShortUrl(input: String): String? {
        fullShortLinkPattern.find(input)?.value?.let { return it }
        nakedShortLinkPattern.find(input)?.value?.let { return "https://$it" }
        return null
    }

    private fun expand(url: String): String? {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10_000
            readTimeout = 15_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "Mozilla/5.0")
        }
        return try {
            connection.responseCode
            connection.inputStream?.close()
            connection.url?.toString()?.trim()?.takeIf { it.isNotEmpty() }
        } catch (_: Exception) {
            null
        } finally {
            connection.disconnect()
        }
    }
}

class BilibiliApi(private val jsonTransport: ((String, Network?) -> JSONObject)? = null) {
    companion object {
        private const val VIEW_API = "https://api.bilibili.com/x/web-interface/view?bvid="
        private const val PLAY_URL_API =
            "https://api.bilibili.com/x/player/playurl?fnval=4048&fnver=0&fourk=1&bvid=%s&cid=%d"
        private const val ARCHIVE_DESC_API = "https://api.bilibili.com/x/web-interface/archive/desc?bvid="
        private const val CREATOR_COLLECTIONS_API =
            "https://api.bilibili.com/x/polymer/web-space/seasons_series_list?mid=%d&page_num=%d&page_size=%d&web_location=333.999"
    }

    fun fetchView(bvid: String): JSONObject {
        val root = getJson(VIEW_API + Uri.encode(bvid))
        val code = root.optLong("code", -1L)
        check(code == 0L) {
            "Bilibili view API failed: code=$code, message=${root.optString("message")}"
        }
        return root.getJSONObject("data")
    }

    fun fetchPlayUrl(bvid: String, cid: Long, network: Network? = null): JSONObject {
        val url = PLAY_URL_API.format(Uri.encode(bvid), cid)
        val root = getJson(url, network)
        val code = root.optLong("code", -1L)
        check(code == 0L) {
            "Bilibili playurl API failed: code=$code, message=${root.optString("message")}"
        }
        return root.getJSONObject("data")
    }

    fun fetchAudioInfo(id: Long): JSONObject = fetchAudioData("song/info?sid=$id")

    fun fetchAudioUrl(id: Long, network: Network? = null): JSONObject =
        fetchAudioData("url?sid=$id&privilege=2&quality=2", network)

    private fun fetchAudioData(path: String, network: Network? = null): JSONObject {
        val root = getJson("https://www.bilibili.com/audio/music-service-c/web/$path", network)
        check(root.optInt("code", -1) == 0) {
            "B 站音频暂不可用：${root.optString("message", root.optString("msg"))}（${root.optInt("code", -1)}）"
        }
        return root.optJSONObject("data") ?: error("B 站未返回音频信息")
    }

    fun fetchArchiveDescription(bvid: String): String? {
        return runCatching {
            val root = getJson(ARCHIVE_DESC_API + Uri.encode(bvid))
            val code = root.optLong("code", -1L)
            check(code == 0L) {
                "Bilibili archive desc API failed: code=$code, message=${root.optString("message")}"
            }
            val data = root.opt("data")
            when (data) {
                is String -> data.trim().takeIf { it.isNotEmpty() }
                is JSONObject -> data.optStringOrNull("desc") ?: data.optStringOrNull("description")
                else -> root.optStringOrNull("data")
            }
        }.getOrNull()
    }

    fun fetchCreatorCollectionsPage(
        authorMid: Long,
        pageNum: Int,
        pageSize: Int,
    ): JSONObject {
        require(authorMid > 0L) { "authorMid must be > 0." }
        require(pageNum >= 1) { "pageNum must be >= 1." }
        require(pageSize >= 1) { "pageSize must be >= 1." }

        val url = CREATOR_COLLECTIONS_API.format(authorMid, pageNum, pageSize)
        val root = getJson(url)
        val code = root.optLong("code", -1L)
        check(code == 0L) {
            "Bilibili creator collections API failed: code=$code, message=${root.optString("message")}"
        }
        return root.optJSONObject("data")
            ?.optJSONObject("items_lists")
            ?: JSONObject()
    }

    fun fetchSeasonPublicationTimes(authorMid: Long, seasonId: Long): Map<String, Long> {
        val dates = mutableMapOf<String, Long>()
        var page = 1
        do {
            val root = getJson("https://api.bilibili.com/x/polymer/web-space/seasons_archives_list?mid=$authorMid&season_id=$seasonId&sort_reverse=false&page_num=$page&page_size=100")
            check(root.optInt("code", -1) == 0) { "无法获取合集发布时间：${root.optString("message")}" }
            val data = root.getJSONObject("data")
            val archives = (data.optJSONArray("archives") ?: JSONArray()).objects()
            for (archive in archives) {
                val bvid = archive.optStringOrNull("bvid") ?: continue
                val time = archive.optLong("pubdate", 0L) * 1000L
                if (time > 0) dates[bvid] = time
            }
            val total = data.optJSONObject("page")?.optInt("total", 0) ?: 0
            if (archives.isEmpty() || page * 100 >= total) break
            page++
        } while (page <= 100)
        return dates
    }

    private fun getJson(url: String, network: Network? = null): JSONObject {
        jsonTransport?.let { return it(url, network) }
        val target = URL(url)
        val connection = ((network?.openConnection(target) ?: target.openConnection()) as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15_000
            readTimeout = 20_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "Mozilla/5.0")
            setRequestProperty("Referer", "https://www.bilibili.com/")
        }

        return try {
            val statusCode = connection.responseCode
            val stream = if (statusCode in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream ?: connection.inputStream
            }
            val decoded = if (connection.contentEncoding?.equals("gzip", ignoreCase = true) == true) GZIPInputStream(stream) else stream
            val body = BufferedReader(InputStreamReader(decoded, Charsets.UTF_8)).use { reader ->
                buildString {
                    var line = reader.readLine()
                    while (line != null) {
                        append(line)
                        line = reader.readLine()
                    }
                }
            }
            check(statusCode == 200) { "Bilibili API returned HTTP $statusCode: $body" }
            JSONObject(body)
        } finally {
            connection.disconnect()
        }
    }
}

class BilibiliQueueResolver(
    private val api: BilibiliApi,
) {
    fun resolve(reference: VideoReference): ResolvedQueue {
        BilibiliIds.audioId(reference.bvid)?.let { return AudioTrackData.queue(it, api.fetchAudioInfo(it)) }
        val data = api.fetchView(reference.bvid)
        val currentBvid = data.getString("bvid")
        val currentTitle = data.getString("title")
        val defaultCover = normalizeImageUrl(data.optStringOrNull("pic"))
        val defaultAuthor = parseAuthorInfo(data.optJSONObject("owner"))
        val currentDescription = resolveCurrentDescription(data, currentBvid)
        val defaultMetadata = parseVideoMetadata(data, explicitDescription = currentDescription)

        val season = data.optJSONObject("ugc_season")
        if (season != null) {
            return buildSeasonQueue(season, reference, defaultCover, defaultAuthor, currentDescription)
        }

        val pages = data.optJSONArray("pages") ?: JSONArray()
        if (pages.length() > 1) {
            val drafts = mutableListOf<ItemDraft>()
            for (page in pages.objects()) {
                drafts += ItemDraft(
                    bvid = currentBvid,
                    cid = page.getLong("cid"),
                    title = buildTitle(
                        currentTitle,
                        page.optStringOrNull("part"),
                        page.getInt("page"),
                        pages.length(),
                    ),
                    durationMs = page.optLong("duration", 0L) * 1000L,
                    pageNumber = page.getInt("page"),
                    coverUrl = defaultCover,
                    authorMid = defaultAuthor?.mid,
                    authorName = defaultAuthor?.name,
                    authorAvatarUrl = defaultAuthor?.avatarUrl,
                    viewCount = defaultMetadata.viewCount,
                    publishedAtMs = defaultMetadata.publishedAtMs,
                    description = defaultMetadata.description,
                )
            }
            val requestedIndex = findRequestedIndex(drafts, reference)
            val collectionId = "video:$currentBvid"
            return ResolvedQueue(
                collectionId = collectionId,
                collectionTitle = currentTitle,
                items = materializeQueue(drafts, collectionId, currentTitle),
                requestedIndex = requestedIndex,
            )
        }

        val singleItem = QueueItem(
            key = ContentKey(currentBvid, data.getLong("cid")),
            title = currentTitle,
            durationMs = data.optLong("duration", 0L) * 1000L,
            pageNumber = 1,
            orderInCollection = 0,
            collectionId = null,
            collectionTitle = null,
            coverUrl = defaultCover,
            authorMid = defaultAuthor?.mid,
            authorName = defaultAuthor?.name,
            authorAvatarUrl = defaultAuthor?.avatarUrl,
            viewCount = defaultMetadata.viewCount,
            publishedAtMs = defaultMetadata.publishedAtMs,
            description = defaultMetadata.description,
        )
        return ResolvedQueue(
            collectionId = null,
            collectionTitle = null,
            items = listOf(singleItem),
            requestedIndex = 0,
        )
    }

    private fun buildSeasonQueue(
        season: JSONObject,
        reference: VideoReference,
        defaultCover: String?,
        defaultAuthor: AuthorDraft?,
        currentDescription: String?,
    ): ResolvedQueue {
        val collectionId = "season:${season.getLong("id")}"
        val collectionTitle = season.getString("title")
        val drafts = mutableListOf<ItemDraft>()

        for (section in (season.optJSONArray("sections") ?: JSONArray()).objects()) {
            for (episode in (section.optJSONArray("episodes") ?: JSONArray()).objects()) {
                val arc = episode.optJSONObject("arc")
                val episodeBvid = episode.getString("bvid")
                val episodeTitle = arc?.optStringOrNull("title") ?: episode.getString("title")
                val coverUrl = normalizeImageUrl(firstNonBlank(arc?.optStringOrNull("pic"), defaultCover))
                val author = parseAuthorInfo(
                    episode.optJSONObject("author"),
                    episode.optJSONObject("owner"),
                    arc?.optJSONObject("author"),
                    arc?.optJSONObject("owner"),
                ) ?: defaultAuthor
                val metadata = parseVideoMetadata(
                    primary = arc ?: episode,
                    fallback = episode,
                    explicitDescription = if (episodeBvid == reference.bvid) currentDescription else null,
                )
                val pages = episode.optJSONArray("pages") ?: JSONArray()

                if (pages.length() > 0) {
                    val sortedPages = pages.objects().sortedBy { it.optInt("page", 1) }
                    sortedPages.forEach { page ->
                        drafts += ItemDraft(
                            bvid = episodeBvid,
                            cid = page.getLong("cid"),
                            title = buildTitle(
                                episodeTitle,
                                page.optStringOrNull("part"),
                                page.optInt("page", 1),
                                sortedPages.size,
                            ),
                            durationMs = page.optLong("duration", 0L) * 1000L,
                            pageNumber = page.optInt("page", 1),
                            coverUrl = coverUrl,
                            authorMid = author?.mid,
                            authorName = author?.name,
                            authorAvatarUrl = author?.avatarUrl,
                            viewCount = metadata.viewCount,
                            publishedAtMs = metadata.publishedAtMs,
                            description = metadata.description,
                        )
                    }
                    continue
                }

                val singlePage = episode.optJSONObject("page")
                val cid = singlePage?.optLong("cid", episode.optLong("cid")) ?: episode.optLong("cid")
                val pageNumber = singlePage?.optInt("page", 1) ?: 1
                val durationSeconds = singlePage?.optLong("duration", durationFromEpisode(episode, arc))
                    ?: durationFromEpisode(episode, arc)
                val part = singlePage?.optStringOrNull("part")

                drafts += ItemDraft(
                    bvid = episodeBvid,
                    cid = cid,
                    title = buildTitle(episodeTitle, part, pageNumber, 1),
                    durationMs = durationSeconds * 1000L,
                    pageNumber = pageNumber,
                    coverUrl = coverUrl,
                    authorMid = author?.mid,
                    authorName = author?.name,
                    authorAvatarUrl = author?.avatarUrl,
                    viewCount = metadata.viewCount,
                    publishedAtMs = metadata.publishedAtMs,
                    description = metadata.description,
                )
            }
        }

        require(drafts.isNotEmpty()) { "ugc_season does not contain any playable episodes." }
        val requestedIndex = findRequestedIndex(drafts, reference)
        return ResolvedQueue(
            collectionId = collectionId,
            collectionTitle = collectionTitle,
            items = materializeQueue(drafts, collectionId, collectionTitle),
            requestedIndex = requestedIndex,
        )
    }

    private fun materializeQueue(
        drafts: List<ItemDraft>,
        collectionId: String,
        collectionTitle: String,
    ): List<QueueItem> = drafts.mapIndexed { index, draft ->
        QueueItem(
            key = ContentKey(draft.bvid, draft.cid),
            title = draft.title,
            durationMs = draft.durationMs,
            pageNumber = draft.pageNumber,
            orderInCollection = index,
            collectionId = collectionId,
            collectionTitle = collectionTitle,
            coverUrl = draft.coverUrl,
            authorMid = draft.authorMid,
            authorName = draft.authorName,
            authorAvatarUrl = draft.authorAvatarUrl,
            viewCount = draft.viewCount,
            publishedAtMs = draft.publishedAtMs,
            description = draft.description,
        )
    }

    private fun findRequestedIndex(drafts: List<ItemDraft>, reference: VideoReference): Int {
        val exactIndex = drafts.indexOfFirst { it.bvid == reference.bvid && it.pageNumber == reference.page }
        if (exactIndex >= 0) {
            return exactIndex
        }

        val bvidMatches = drafts.withIndex().filter { (_, item) -> item.bvid == reference.bvid }
        if (bvidMatches.size == 1) {
            return bvidMatches.first().index
        }
        if (bvidMatches.isNotEmpty() && reference.page == 1) {
            return bvidMatches.first().index
        }

        throw IllegalArgumentException(
            "Requested video is not present in the resolved queue: ${reference.bvid} page ${reference.page}",
        )
    }

    private fun durationFromEpisode(episode: JSONObject, arc: JSONObject?): Long {
        if (arc != null && arc.has("duration") && !arc.isNull("duration")) {
            return arc.optLong("duration", 0L)
        }
        return episode.optLong("duration", 0L)
    }

    private fun buildTitle(baseTitle: String, part: String?, pageNumber: Int, totalPages: Int): String {
        val normalizedPart = normalizeBlank(part) ?: return baseTitle
        if (totalPages == 1 && normalizedPart == baseTitle) {
            return baseTitle
        }
        return "$baseTitle - P$pageNumber $normalizedPart"
    }

    private fun firstNonBlank(first: String?, second: String?): String? = normalizeBlank(first) ?: normalizeBlank(second)

    private fun parseAuthorInfo(vararg candidates: JSONObject?): AuthorDraft? {
        candidates.forEach { candidate ->
            if (candidate == null) {
                return@forEach
            }
            val name = firstNonBlank(
                candidate.optStringOrNull("name"),
                candidate.optStringOrNull("uname"),
            )
            val avatarUrl = firstNonBlank(
                candidate.optStringOrNull("face"),
                firstNonBlank(candidate.optStringOrNull("avatar"), candidate.optStringOrNull("pic")),
            )
            val mid = candidate.optLong("mid", candidate.optLong("uid", 0L))
                .takeIf { it > 0L }
            if (mid != null || name != null || avatarUrl != null) {
                return AuthorDraft(
                    mid = mid,
                    name = name,
                    avatarUrl = normalizeImageUrl(avatarUrl),
                )
            }
        }
        return null
    }

    private fun normalizeImageUrl(value: String?): String? {
        val normalized = normalizeBlank(value) ?: return null
        return when {
            normalized.startsWith("//") -> "https:$normalized"
            normalized.startsWith("http://") -> "https://${normalized.removePrefix("http://")}"
            else -> normalized
        }
    }

    private fun normalizeBlank(value: String?): String? {
        val trimmed = value?.trim()
        return if (trimmed.isNullOrEmpty()) null else trimmed
    }

    private fun parseVideoMetadata(
        primary: JSONObject,
        fallback: JSONObject? = null,
        explicitDescription: String? = null,
    ): VideoMetadataDraft {
        val stat = primary.optJSONObject("stat") ?: fallback?.optJSONObject("stat")
        val viewCount = stat?.optLong("view", stat.optLong("play", 0L)) ?: 0L
        val publishSeconds = listOf(
            primary.optLong("pubdate", 0L), fallback?.optLong("pubdate", 0L) ?: 0L,
            primary.optLong("ctime", 0L), fallback?.optLong("ctime", 0L) ?: 0L,
        ).firstOrNull { it > 0L } ?: 0L
        val description = explicitDescription
            ?: firstNonBlank(parseDescription(primary), parseDescription(fallback))
        return VideoMetadataDraft(
            viewCount = viewCount,
            publishedAtMs = publishSeconds * 1000L,
            description = description,
        )
    }

    private fun resolveCurrentDescription(viewData: JSONObject, bvid: String): String? {
        return firstNonBlank(
            parseDescription(viewData),
            api.fetchArchiveDescription(bvid),
        )
    }

    private fun parseDescription(source: JSONObject?): String? {
        if (source == null) {
            return null
        }
        source.optStringOrNull("desc")?.let { return it }
        val descV2 = source.optJSONArray("desc_v2") ?: return null
        val combined = buildString {
            descV2.objects().forEach { block ->
                val rawText = block.optStringOrNull("raw_text") ?: return@forEach
                append(rawText)
            }
        }.trim()
        return combined.takeIf { it.isNotEmpty() }
    }

    private data class ItemDraft(
        val bvid: String,
        val cid: Long,
        val title: String,
        val durationMs: Long,
        val pageNumber: Int,
        val coverUrl: String?,
        val authorMid: Long?,
        val authorName: String?,
        val authorAvatarUrl: String?,
        val viewCount: Long,
        val publishedAtMs: Long,
        val description: String?,
    )

    private data class AuthorDraft(
        val mid: Long?,
        val name: String?,
        val avatarUrl: String?,
    )

    private data class VideoMetadataDraft(
        val viewCount: Long,
        val publishedAtMs: Long,
        val description: String?,
    )
}

class BilibiliAudioResolver(
    private val api: BilibiliApi,
) {
    fun resolve(item: QueueItem, network: Network? = null): ResolvedAudio = resolve(item.key, item.title, item.durationMs, network)

    fun resolve(key: ContentKey, title: String, durationMs: Long, network: Network? = null): ResolvedAudio {
        key.audioId?.let { return AudioTrackData.audio(key, title, durationMs, api.fetchAudioUrl(it, network)) }
        val data = api.fetchPlayUrl(key.bvid, key.cid, network)
        val candidates = mutableListOf<AudioStreamSource>()
        val dash = data.optJSONObject("dash")
        if (dash != null) {
            candidates += parseRegularAudio(dash)
            candidates += parseFlacAudio(dash)
            candidates += parseDolbyAudio(dash)
        }
        if (candidates.isEmpty()) {
            candidates += parseDurlFallback(data)
        }
        require(candidates.isNotEmpty()) { "Playurl response does not contain any playable audio source." }
        val selected = candidates.maxWithOrNull(
            compareBy<AudioStreamSource> { preferenceRank(it.kind) }.thenBy { it.bandwidth },
        ) ?: throw IllegalStateException("No audio candidates were selected.")
        val resolvedDurationMs = if (durationMs > 0L) durationMs else data.optLong("timelength", 0L)
        return ResolvedAudio(
            key = key,
            title = title,
            durationMs = resolvedDurationMs,
            selected = selected,
            candidates = candidates.toList(),
        )
    }

    private fun parseRegularAudio(dash: JSONObject): List<AudioStreamSource> =
        (dash.optJSONArray("audio") ?: JSONArray()).objects().map {
            parseDashSource(it, AudioStreamKind.DASH_AUDIO)
        }

    private fun parseFlacAudio(dash: JSONObject): List<AudioStreamSource> {
        val audio = dash.optJSONObject("flac")?.optJSONObject("audio") ?: return emptyList()
        return listOf(parseDashSource(audio, AudioStreamKind.DASH_FLAC))
    }

    private fun parseDolbyAudio(dash: JSONObject): List<AudioStreamSource> {
        val dolby = dash.optJSONObject("dolby") ?: return emptyList()
        val rawAudio = dolby.opt("audio") ?: return emptyList()
        return when (rawAudio) {
            is JSONObject -> listOf(parseDashSource(rawAudio, AudioStreamKind.DASH_DOLBY))
            is JSONArray -> rawAudio.objects().map { parseDashSource(it, AudioStreamKind.DASH_DOLBY) }
            else -> emptyList()
        }
    }

    private fun parseDurlFallback(data: JSONObject): List<AudioStreamSource> {
        val durl = data.optJSONArray("durl") ?: return emptyList()
        return durl.objects().mapNotNull { entry ->
            val url = firstNonBlank(entry.optStringOrNull("url"), entry.optStringOrNull("backup_url")) ?: return@mapNotNull null
            AudioStreamSource(
                id = entry.optInt("order", 0),
                url = url,
                backupUrls = emptyList(),
                bandwidth = 0L,
                codec = null,
                mimeType = data.optStringOrNull("format"),
                kind = AudioStreamKind.MUXED_FALLBACK,
            )
        }
    }

    private fun parseDashSource(source: JSONObject, kind: AudioStreamKind): AudioStreamSource {
        val url = firstNonBlank(source.optStringOrNull("baseUrl"), source.optStringOrNull("base_url"))
            ?: throw IllegalArgumentException("Dash source is missing baseUrl/base_url.")
        return AudioStreamSource(
            id = source.optInt("id", 0),
            url = url,
            backupUrls = parseBackupUrls(source),
            bandwidth = source.optLong("bandwidth", 0L),
            codec = source.optStringOrNull("codecs"),
            mimeType = firstNonBlank(source.optStringOrNull("mimeType"), source.optStringOrNull("mime_type")),
            kind = kind,
        )
    }

    private fun parseBackupUrls(source: JSONObject): List<String> {
        val raw = source.opt("backupUrl") ?: source.opt("backup_url") ?: return emptyList()
        if (raw !is JSONArray) {
            return emptyList()
        }
        return raw.objectsAsStrings().filter { it.isNotBlank() }
    }

    private fun preferenceRank(kind: AudioStreamKind): Int = when (kind) {
        AudioStreamKind.AUDIO_TRACK -> 4
        AudioStreamKind.DASH_AUDIO -> 3
        AudioStreamKind.DASH_FLAC -> 2
        AudioStreamKind.DASH_DOLBY -> 1
        AudioStreamKind.MUXED_FALLBACK -> 0
    }

    private fun firstNonBlank(first: String?, second: String?): String? {
        if (!first.isNullOrBlank()) {
            return first
        }
        return second?.takeIf { it.isNotBlank() }
    }
}

class PlaybackPlanner {
    companion object {
        private const val FINISHED_THRESHOLD_MS = 3_000L
    }

    fun planStart(
        queue: ResolvedQueue,
        savedSession: CollectionSession?,
        stateStore: ListenStateStore,
        resumePolicy: ResumePolicy,
    ): StartPlan {
        var startIndex = queue.requestedIndex
        var reason = "requested item"

        if (
            resumePolicy == ResumePolicy.PREFER_SAVED_COLLECTION_POSITION &&
            queue.collectionId != null &&
            savedSession != null &&
            queue.collectionId == savedSession.collectionId
        ) {
            startIndex = savedSession.currentIndex.coerceIn(0, queue.items.lastIndex)
            reason = "saved collection position"
        }

        val normalizedIndex = advancePastFinished(queue.items, startIndex, stateStore)
        if (normalizedIndex != startIndex) {
            reason = "next unfinished item"
        }

        val item = queue.items[normalizedIndex]
        val startPositionMs = stateStore.loadProgress(item.key)
            ?.takeUnless { it.finished }
            ?.positionMs
            ?: 0L

        return StartPlan(
            startIndex = normalizedIndex,
            item = item,
            startPositionMs = startPositionMs,
            reason = reason,
        )
    }

    fun checkpoint(item: QueueItem, positionMs: Long, updatedAt: Long): PlaybackProgress {
        val safePosition = if (item.durationMs > 0L) {
            positionMs.coerceIn(0L, item.durationMs)
        } else {
            positionMs.coerceAtLeast(0L)
        }
        return PlaybackProgress(
            key = item.key,
            positionMs = safePosition,
            durationMs = item.durationMs,
            finished = isFinished(safePosition, item.durationMs),
            updatedAt = updatedAt,
        )
    }

    fun nextIndexAfterCompletion(queue: ResolvedQueue, currentIndex: Int): Int {
        val nextIndex = currentIndex + 1
        return if (nextIndex < queue.items.size) nextIndex else -1
    }

    private fun advancePastFinished(items: List<QueueItem>, startIndex: Int, stateStore: ListenStateStore): Int {
        var currentIndex = startIndex
        while (currentIndex < items.lastIndex) {
            val progress = stateStore.loadProgress(items[currentIndex].key)
            if (progress == null || !progress.finished) {
                break
            }
            currentIndex += 1
        }
        return currentIndex
    }

    private fun isFinished(positionMs: Long, durationMs: Long): Boolean {
        if (durationMs <= 0L) {
            return false
        }
        return positionMs >= (durationMs - FINISHED_THRESHOLD_MS).coerceAtLeast(0L)
    }
}

class ListenStateStore(context: Context) {
    private val progressPrefs: SharedPreferences =
        context.getSharedPreferences("playback_progress", Context.MODE_PRIVATE)
    private val sessionPrefs: SharedPreferences =
        context.getSharedPreferences("collection_sessions", Context.MODE_PRIVATE)
    private val appPrefs: SharedPreferences =
        context.getSharedPreferences("app_state", Context.MODE_PRIVATE)

    fun loadProgress(key: ContentKey): PlaybackProgress? {
        val raw = progressPrefs.getString(key.asStoreKey(), null) ?: return null
        val json = JSONObject(raw)
        return PlaybackProgress(
            key = key,
            positionMs = json.optLong("positionMs", 0L),
            durationMs = json.optLong("durationMs", 0L),
            finished = json.optBoolean("finished", false),
            updatedAt = json.optLong("updatedAt", 0L),
        )
    }

    fun saveProgress(progress: PlaybackProgress) {
        val json = JSONObject()
            .put("positionMs", progress.positionMs)
            .put("durationMs", progress.durationMs)
            .put("finished", progress.finished)
            .put("updatedAt", progress.updatedAt)
        progressPrefs.edit().putString(progress.key.asStoreKey(), json.toString()).apply()
    }

    fun clearProgress(key: ContentKey) {
        progressPrefs.edit().remove(key.asStoreKey()).apply()
    }

    fun clearCollectionProgress(queue: ResolvedQueue, keepKey: ContentKey? = null) {
        val editor = progressPrefs.edit()
        queue.items.forEach { item ->
            if (item.key != keepKey) {
                editor.remove(item.key.asStoreKey())
            }
        }
        editor.apply()
    }

    fun loadSession(collectionId: String): CollectionSession? {
        val raw = sessionPrefs.getString(collectionId, null) ?: return null
        val json = JSONObject(raw)
        return CollectionSession(
            collectionId = collectionId,
            currentIndex = json.optInt("currentIndex", 0),
            updatedAt = json.optLong("updatedAt", 0L),
        )
    }

    fun saveSession(session: CollectionSession) {
        val json = JSONObject()
            .put("currentIndex", session.currentIndex)
            .put("updatedAt", session.updatedAt)
        sessionPrefs.edit().putString(session.collectionId, json.toString()).apply()
    }

    fun removeSession(collectionId: String) {
        sessionPrefs.edit().remove(collectionId).apply()
    }

    fun loadLastPlaybackJson(): String? = appPrefs.getString(KEY_LAST_PLAYBACK, null)?.trim()?.takeIf { it.isNotEmpty() }

    fun saveLastPlaybackJson(raw: String) {
        val normalized = raw.trim()
        if (normalized.isEmpty()) {
            appPrefs.edit().remove(KEY_LAST_PLAYBACK).apply()
        } else {
            appPrefs.edit().putString(KEY_LAST_PLAYBACK, normalized).apply()
        }
    }

    fun loadFavoriteJson(id: String): String? = appPrefs.getString(favoriteKey(id), null)?.trim()?.takeIf { it.isNotEmpty() }

    fun saveFavoriteJson(id: String, raw: String) {
        val normalized = raw.trim()
        if (normalized.isEmpty()) {
            appPrefs.edit().remove(favoriteKey(id)).apply()
        } else {
            appPrefs.edit().putString(favoriteKey(id), normalized).apply()
        }
    }

    fun removeFavorite(id: String) {
        appPrefs.edit().remove(favoriteKey(id)).apply()
    }

    fun loadAllFavoriteJson(): List<String> {
        return appPrefs.all
            .filterKeys { it.startsWith(KEY_FAVORITE_PREFIX) }
            .values
            .mapNotNull { value -> (value as? String)?.trim()?.takeIf { it.isNotEmpty() } }
    }

    fun loadCollectionPlaybackOrder(collectionId: String): CollectionPlaybackOrder {
        val raw = appPrefs.getString(collectionOrderKey(collectionId), null)?.trim()
        return raw
            ?.takeIf { it.isNotEmpty() }
            ?.let { value -> runCatching { CollectionPlaybackOrder.valueOf(value) }.getOrNull() }
            ?: CollectionPlaybackOrder.COLLECTION_DEFAULT
    }

    fun saveCollectionPlaybackOrder(collectionId: String, order: CollectionPlaybackOrder) {
        appPrefs.edit().putString(collectionOrderKey(collectionId), order.name).apply()
    }

    private fun favoriteKey(id: String): String = "$KEY_FAVORITE_PREFIX$id"
    private fun collectionOrderKey(collectionId: String): String = "$KEY_COLLECTION_ORDER_PREFIX$collectionId"

    companion object {
        private const val KEY_LAST_PLAYBACK = "last_playback"
        private const val KEY_FAVORITE_PREFIX = "favorite:"
        private const val KEY_COLLECTION_ORDER_PREFIX = "collection_order:"
    }
}

class ListenRepository(context: Context, private val api: BilibiliApi = BilibiliApi()) {
    private val parser = BilibiliLinkParser()
    private val shortLinkResolver = BilibiliShortLinkResolver()
    private val queueResolver = BilibiliQueueResolver(api)
    private val audioResolver = BilibiliAudioResolver(api)
    private val audioCacheManager = AudioCacheManager(context.applicationContext, audioResolver)
    private val planner = PlaybackPlanner()
    private val stateStore = ListenStateStore(context.applicationContext)
    private val recentProgress = RecentPlaybackStore(context.applicationContext, stateStore)
    private val listeningTime = ListeningTimeStore(context.applicationContext)
    private val specialFavorites = SpecialFavoritesStore(context.applicationContext)
    private val resumeRetention = ResumeRetentionStore(context.applicationContext)

    fun cleanupExpiredResumeRecords(now: Long = System.currentTimeMillis()): Set<String> =
        resumeRetention.cleanup(now, specialFavorites.favorite())

    fun cleanupExpiredAudioCache(now: Long = System.currentTimeMillis()): Int = synchronized(favoritesLock) {
        audioCacheManager.updateFavorites(listFavorites(), now, cleanup = true)
    }

    private fun updateCacheFavorites(now: Long) { audioCacheManager.updateFavorites(listFavorites(), now) }

    @Volatile var playbackError: String? = null
    @Volatile var playbackLoading: Boolean = false
    @Volatile var activePlayback: PlannedPlayback? = null

    companion object {
        private const val CREATOR_COLLECTIONS_PAGE_SIZE = 20
        // Cleanup, membership changes and background progress writes must observe one ownership state.
        private val favoritesLock = Any()
    }

    fun loadCollectionPlaybackOrder(collectionId: String?): CollectionPlaybackOrder {
        val normalizedCollectionId = collectionId?.takeIf { it.isNotBlank() }
            ?: return CollectionPlaybackOrder.COLLECTION_DEFAULT
        return stateStore.loadCollectionPlaybackOrder(normalizedCollectionId).forCollection(normalizedCollectionId)
    }

    fun planPlayback(input: String, resumePolicy: ResumePolicy): PlannedPlayback {
        val reference = parseReference(input)
        val resolved = queueResolver.resolve(reference)
        val withDates = if (loadCollectionPlaybackOrder(resolved.collectionId).usesPublicationTime) {
            runCatching { fillMissingPublicationTimes(PlannedPlayback(resolved, StartPlan(resolved.requestedIndex, resolved.items[resolved.requestedIndex], 0, "metadata"))).queue }
                .getOrDefault(resolved)
        } else resolved
        val queue = applySavedCollectionOrder(
            queue = withDates,
            anchorKey = null,
            fallbackIndex = null,
        )
        val requestedItem = queue.items[queue.requestedIndex]
        val startPlan = StartPlan(
            startIndex = queue.requestedIndex,
            item = requestedItem,
            startPositionMs = recentProgress.load(queue).firstOrNull { it.key == requestedItem.key }
                ?.takeUnless { it.finished }?.positionMs ?: 0L,
            reason = when (resumePolicy) {
                ResumePolicy.PREFER_REQUESTED_ITEM -> "requested item"
                ResumePolicy.PREFER_SAVED_COLLECTION_POSITION -> "requested item"
            },
        )
        return PlannedPlayback(queue = queue, plan = startPlan)
    }

    private fun parseReference(input: String): VideoReference {
        return runCatching { parser.parse(input) }.getOrElse {
            val resolved = shortLinkResolver.resolve(input)
            if (resolved == input) {
                throw it
            }
            parser.parse(resolved)
        }
    }

    fun planPlaybackFromQueue(
        queue: ResolvedQueue,
        targetIndex: Int,
        reason: String = "manual selection",
        resumeSavedProgress: Boolean = true,
    ): PlannedPlayback {
        val requestedKey = queue.items.getOrNull(wrapIndex(targetIndex, queue.items.size))?.key
        val orderedQueue = applySavedCollectionOrder(
            queue = queue,
            anchorKey = requestedKey,
            fallbackIndex = targetIndex,
        )
        val normalizedIndex = requestedKey
            ?.let { key -> orderedQueue.items.indexOfFirst { it.key == key }.takeIf { it >= 0 } }
            ?: wrapIndex(targetIndex, orderedQueue.items.size)
        val item = orderedQueue.items[normalizedIndex]
        val startPositionMs = if (resumeSavedProgress) {
            recentProgress.load(orderedQueue).firstOrNull { it.key == item.key }
                ?.takeUnless { it.finished }
                ?.positionMs
                ?: 0L
        } else {
            0L
        }
        return PlannedPlayback(
            queue = orderedQueue,
            plan = StartPlan(
                startIndex = normalizedIndex,
                item = item,
                startPositionMs = startPositionMs,
                reason = reason,
            ),
        )
    }

    fun listCreatorCollections(
        authorMid: Long,
        authorName: String?,
        authorAvatarUrl: String?,
    ): List<CreatorCollectionSummary> {
        require(authorMid > 0L) { "authorMid must be > 0." }

        val results = mutableListOf<CreatorCollectionSummary>()
        val seenIds = LinkedHashSet<String>()
        var pageNum = 1
        var totalPages = 1

        while (pageNum <= totalPages) {
            val itemsLists = api.fetchCreatorCollectionsPage(
                authorMid = authorMid,
                pageNum = pageNum,
                pageSize = CREATOR_COLLECTIONS_PAGE_SIZE,
            )
            totalPages = itemsLists.optJSONObject("page")
                ?.optInt("total", pageNum)
                ?.coerceAtLeast(pageNum)
                ?: pageNum

            parseCreatorCollectionSummaries(
                itemsLists = itemsLists,
                authorMid = authorMid,
                authorName = authorName,
                authorAvatarUrl = authorAvatarUrl,
            ).forEach { summary ->
                if (seenIds.add(summary.id)) {
                    results += summary
                }
            }

            pageNum += 1
        }

        return results
    }

    fun planPlaybackFromCreatorCollection(summary: CreatorCollectionSummary): PlannedPlayback {
        val leadBvid = summary.leadBvid
            ?: throw IllegalArgumentException("This creator collection does not contain a playable BV id.")
        val queuePlayback = planPlayback(leadBvid, ResumePolicy.PREFER_REQUESTED_ITEM)
        return planPlaybackFromQueue(
            queue = queuePlayback.queue,
            targetIndex = 0,
            reason = "creator collection",
            resumeSavedProgress = true,
        )
    }

    fun refreshPlaybackSnapshot(
        playback: PlannedPlayback,
        preservePositionMs: Long = playback.plan.startPositionMs,
    ): PlannedPlayback {
        if (playback.queue.collectionId == SpecialFavorites.ID) {
            // This is a local playlist: resolving its BV as a collection would discard its membership.
            return normalizePlaybackOrder(playback.copy(plan = playback.plan.copy(startPositionMs = preservePositionMs)))
        }
        // Saved queues and local audio must remain usable without contacting Bilibili.
        if (!isWifiConnected()) return normalizePlaybackOrder(playback)
        val reference = VideoReference(
            bvid = playback.plan.item.key.bvid,
            page = playback.plan.item.pageNumber,
        )
        val knownDates = playback.queue.items.filter { it.publishedAtMs > 0L }.associate { it.key.bvid to it.publishedAtMs }
        val resolved = queueResolver.resolve(reference).let { queue ->
            queue.copy(items = queue.items.map { item ->
                if (item.publishedAtMs > 0) item else item.copy(publishedAtMs = knownDates[item.key.bvid] ?: 0L)
            })
        }
        val refreshedQueue = applySavedCollectionOrder(
            queue = resolved,
            anchorKey = playback.plan.item.key,
            fallbackIndex = null,
        )
        val refreshedIndex =
            refreshedQueue.items.indexOfFirst { it.key == playback.plan.item.key }
                .takeIf { it >= 0 }
                ?: refreshedQueue.items.indexOfFirst {
                    it.key.bvid == playback.plan.item.key.bvid && it.pageNumber == playback.plan.item.pageNumber
                }.takeIf { it >= 0 }
                ?: refreshedQueue.requestedIndex
        val refreshedItem = refreshedQueue.items[refreshedIndex]
        val cappedPosition = if (refreshedItem.durationMs > 0L) {
            preservePositionMs.coerceIn(0L, refreshedItem.durationMs)
        } else {
            preservePositionMs.coerceAtLeast(0L)
        }
        return PlannedPlayback(
            queue = refreshedQueue,
            plan = StartPlan(
                startIndex = refreshedIndex,
                item = refreshedItem,
                startPositionMs = cappedPosition,
                reason = "refreshed snapshot",
            ),
        )
    }

    fun updateCollectionPlaybackOrder(
        playback: PlannedPlayback,
        order: CollectionPlaybackOrder,
        preservePositionMs: Long = playback.plan.startPositionMs,
    ): PlannedPlayback {
        playback.queue.collectionId?.let { stateStore.saveCollectionPlaybackOrder(it, order.forCollection(it)) }
        val reorderedQueue = applySavedCollectionOrder(playback.queue, playback.plan.item.key, playback.plan.startIndex)
        val reorderedIndex = reorderedQueue.items.indexOfFirst { it.key == playback.plan.item.key }
            .takeIf { it >= 0 }
            ?: reorderedQueue.requestedIndex
        val reorderedItem = reorderedQueue.items[reorderedIndex]
        val cappedPosition = if (reorderedItem.durationMs > 0L) {
            preservePositionMs.coerceIn(0L, reorderedItem.durationMs)
        } else {
            preservePositionMs.coerceAtLeast(0L)
        }
        val updatedPlayback = PlannedPlayback(
            queue = reorderedQueue,
            plan = StartPlan(
                startIndex = reorderedIndex,
                item = reorderedItem,
                startPositionMs = cappedPosition,
                reason = playback.plan.reason,
            ),
        )
        if (isFavorited(updatedPlayback)) {
            saveFavorite(updatedPlayback)
        }
        saveLastPlayback(updatedPlayback, cappedPosition)
        rememberSession(updatedPlayback)
        return updatedPlayback
    }

    fun nextIndex(queue: ResolvedQueue, currentIndex: Int): Int = wrapIndex(currentIndex + 1, queue.items.size)

    fun previousIndex(queue: ResolvedQueue, currentIndex: Int): Int = wrapIndex(currentIndex - 1, queue.items.size)

    fun adjacentPlayback(playback: PlannedPlayback, delta: Int): PlannedPlayback? {
        val ordered = normalizePlaybackOrder(playback)
        if (ordered.queue.items.size < 2) return null
        if (ordered.queue.collectionId == SpecialFavorites.ID) {
            val saved = loadFavorite(SpecialFavorites.ID)?.playback ?: return null
            val keys = saved.queue.items.map { it.key }.toSet()
            // A removed current part may finish, but must never be reached again by next/previous.
            for (step in 1..ordered.queue.items.size) {
                val candidate = ordered.queue.items[wrapIndex(ordered.plan.startIndex + delta * step, ordered.queue.items.size)]
                if (candidate.key in keys) {
                    return planPlaybackFromQueue(saved.queue, saved.queue.items.indexOfFirst { it.key == candidate.key }, "special favorites skip")
                }
            }
            return null
        }
        return planPlaybackFromQueue(ordered.queue, wrapIndex(ordered.plan.startIndex + delta, ordered.queue.items.size), "media skip")
    }

    fun rememberSession(playback: PlannedPlayback, updatedAt: Long = System.currentTimeMillis()) {
        val collectionId = playback.queue.collectionId ?: return
        stateStore.saveSession(
            CollectionSession(
                collectionId = collectionId,
                currentIndex = playback.plan.startIndex,
                updatedAt = updatedAt,
            ),
        )
    }

    fun saveLastPlayback(playback: PlannedPlayback) {
        val normalizedPlayback = normalizePlaybackOrder(playback)
        if (playback.queue.collectionId == SpecialFavorites.ID && specialFavorites.favorite() == null) {
            stateStore.saveLastPlaybackJson("")
            return
        }
        stateStore.saveLastPlaybackJson(PlaybackJsonCodec.encode(normalizedPlayback))
        updateFavoriteIfPresent(normalizedPlayback)
    }

    fun saveLastPlayback(playback: PlannedPlayback, positionMs: Long) {
        val orderedPlayback = normalizePlaybackOrder(playback)
        val normalizedPlayback = orderedPlayback.copy(
            plan = orderedPlayback.plan.copy(
                startIndex = orderedPlayback.plan.startIndex,
                item = orderedPlayback.plan.item,
                startPositionMs = positionMs.coerceAtLeast(0L),
            ),
        )
        saveLastPlayback(normalizedPlayback)
    }

    fun loadLastPlayback(): PlannedPlayback? {
        val raw = stateStore.loadLastPlaybackJson() ?: return null
        val decoded = runCatching { PlaybackJsonCodec.decode(raw) }.getOrNull() ?: return null
        if (decoded.queue.collectionId == SpecialFavorites.ID) return loadFavorite(SpecialFavorites.ID)?.playback
        return normalizePlaybackOrder(decoded)
    }

    fun favoriteIdOf(playback: PlannedPlayback): String {
        return playback.queue.collectionId ?: "single:${playback.plan.item.key.asStoreKey()}"
    }

    fun listeningTimeMs(collectionId: String): Long = listeningTime.totalMs(collectionId)

    fun totalListeningTimeMs(): Long = listeningTime.totalMs()

    internal fun addListeningTime(collectionId: String, elapsedMs: Long) = listeningTime.add(collectionId, elapsedMs)

    fun isSpecialFavorited(key: ContentKey): Boolean = specialFavorites.contains(key)

    fun toggleSpecialFavorite(playback: PlannedPlayback, updatedAt: Long = System.currentTimeMillis()): Boolean = synchronized(favoritesLock) {
        specialFavorites.toggle(playback, updatedAt).also { updateCacheFavorites(updatedAt) }
    }

    fun specialFavoriteManualItems(): List<QueueItem> =
        specialFavorites.manualItems(loadCollectionPlaybackOrder(SpecialFavorites.ID))

    fun saveSpecialFavoriteOrder(keys: List<ContentKey>) {
        specialFavorites.saveManualOrder(keys)
        stateStore.saveCollectionPlaybackOrder(SpecialFavorites.ID, CollectionPlaybackOrder.MANUAL)
    }

    fun isFavorited(playback: PlannedPlayback?): Boolean {
        if (playback == null) {
            return false
        }
        if (playback.queue.collectionId == SpecialFavorites.ID) return specialFavorites.favorite() != null
        return stateStore.loadFavoriteJson(favoriteIdOf(playback)) != null
    }

    fun listFavorites(): List<FavoriteCollection> {
        val regular = stateStore.loadAllFavoriteJson()
            .mapNotNull { raw ->
                runCatching { FavoriteJsonCodec.decode(raw) }
                    .getOrNull()
                    ?.let { favorite -> favorite.copy(playback = normalizePlaybackOrder(favorite.playback),
                        listeningTimeMs = listeningTimeMs(favorite.id)) }
            }
            .sortedByDescending { it.updatedAt }
        return listOfNotNull(loadFavorite(SpecialFavorites.ID)) + regular
    }

    fun loadFavorite(id: String): FavoriteCollection? {
        if (id == SpecialFavorites.ID) return specialFavorites.favorite()?.let {
            it.copy(playback = normalizePlaybackOrder(it.playback), listeningTimeMs = listeningTimeMs(id))
        }
        val raw = stateStore.loadFavoriteJson(id) ?: return null
        return runCatching { FavoriteJsonCodec.decode(raw) }
            .getOrNull()
            ?.let { favorite -> favorite.copy(playback = normalizePlaybackOrder(favorite.playback),
                listeningTimeMs = listeningTimeMs(favorite.id)) }
    }

    fun toggleFavorite(playback: PlannedPlayback, updatedAt: Long = System.currentTimeMillis()): Boolean = synchronized(favoritesLock) {
        val id = favoriteIdOf(playback)
        if (id == SpecialFavorites.ID) return isFavorited(playback)
        if (stateStore.loadFavoriteJson(id) != null) {
            resumeRetention.removed(playback, updatedAt)
            stateStore.removeFavorite(id)
            updateCacheFavorites(updatedAt)
            return false
        }
        saveFavorite(playback, updatedAt)
        return true
    }

    fun saveFavorite(playback: PlannedPlayback, updatedAt: Long = System.currentTimeMillis()): Unit = synchronized(favoritesLock) {
        val normalizedPlayback = normalizePlaybackOrder(playback)
        if (playback.queue.collectionId == SpecialFavorites.ID) {
            specialFavorites.savePosition(normalizedPlayback)
            return
        }
        val favorite = FavoriteCollection(
            id = favoriteIdOf(normalizedPlayback),
            playback = normalizedPlayback,
            updatedAt = updatedAt,
        )
        val newlyFavorited = stateStore.loadFavoriteJson(favorite.id) == null
        stateStore.saveFavoriteJson(favorite.id, FavoriteJsonCodec.encode(favorite))
        resumeRetention.refavorited(favorite.id)
        if (newlyFavorited) updateCacheFavorites(updatedAt)
    }

    fun clearCollectionProgress(queue: ResolvedQueue, keepKey: ContentKey? = null) {
        stateStore.clearCollectionProgress(queue, keepKey)
        recentProgress.clear(queue, keepKey)
    }

    fun resolveAudio(playback: PlannedPlayback): ResolvedAudio = audioResolver.resolve(playback.plan.item)

    fun cachedAudioUri(item: QueueItem): Uri? = audioCacheManager.cachedAudioUri(item)

    fun cacheSnapshot(playback: PlannedPlayback): AudioCacheSnapshot =
        audioCacheManager.snapshot(playback, isFavorited(playback)) { canAutoCache(playback, it) }

    private fun canAutoCache(playback: PlannedPlayback, item: QueueItem): Boolean =
        isFavorited(playback) && (playback.queue.collectionId != SpecialFavorites.ID || specialFavorites.contains(item.key))

    fun invalidateCachedAudio(item: QueueItem) = audioCacheManager.invalidate(item)

    fun maintainAutoCache(playback: PlannedPlayback, onCached: (QueueItem) -> Unit = {}): AutoCacheMaintenanceResult {
        val previousKey = recentProgress.load(playback.queue).firstOrNull { it.key != playback.plan.item.key }?.key
        val previous = playback.queue.items.firstOrNull { it.key == previousKey }
        return audioCacheManager.maintainWindow(playback, previous, { canAutoCache(playback, it) }, onCached)
    }

    fun isWifiConnected(): Boolean = audioCacheManager.isWifiConnected()

    fun saveProgress(
        playback: PlannedPlayback,
        positionMs: Long,
        updatedAt: Long = System.currentTimeMillis(),
    ): PlaybackProgress {
        val progress = planner.checkpoint(playback.plan.item, positionMs, updatedAt)
        recentProgress.save(playback.queue, progress)
        rememberSession(playback, updatedAt)
        saveLastPlayback(playback, progress.positionMs)
        return progress
    }

    fun advance(
        playback: PlannedPlayback,
        updatedAt: Long = System.currentTimeMillis(),
    ): AdvanceOutcome {
        val completedItem = playback.plan.item
        val nextIndex = planner.nextIndexAfterCompletion(playback.queue, playback.plan.startIndex)
        if (nextIndex < 0) {
            rememberSession(playback, updatedAt)
            saveLastPlayback(playback, completedItem.durationMs)
            return AdvanceOutcome(
                completedItem = completedItem,
                nextPlayback = null,
                reachedEnd = true,
            )
        }

        val collectionId = playback.queue.collectionId
        if (collectionId != null) {
            stateStore.saveSession(CollectionSession(collectionId, nextIndex, updatedAt))
        }
        saveProgress(playback, completedItem.durationMs, updatedAt)
        val nextPlayback = planPlaybackFromQueue(playback.queue, nextIndex, "auto next")
        saveLastPlayback(nextPlayback)
        return AdvanceOutcome(
            completedItem = completedItem,
            nextPlayback = nextPlayback,
            reachedEnd = false,
        )
    }

    private fun updateFavoriteIfPresent(playback: PlannedPlayback, updatedAt: Long = System.currentTimeMillis()): Unit = synchronized(favoritesLock) {
        val id = favoriteIdOf(playback)
        if (id != SpecialFavorites.ID && stateStore.loadFavoriteJson(id) == null) {
            return
        }
        saveFavorite(playback, updatedAt)
    }

    private fun wrapIndex(index: Int, size: Int): Int {
        if (size <= 0) {
            return 0
        }
        val mod = index % size
        return if (mod >= 0) mod else mod + size
    }

    fun normalizePlaybackOrder(playback: PlannedPlayback): PlannedPlayback {
        val reorderedQueue = applySavedCollectionOrder(
            queue = playback.queue,
            anchorKey = playback.plan.item.key,
            fallbackIndex = playback.plan.startIndex,
        )
        val reorderedIndex = reorderedQueue.items.indexOfFirst { it.key == playback.plan.item.key }
            .takeIf { it >= 0 }
            ?: reorderedQueue.requestedIndex
        val reorderedItem = reorderedQueue.items[reorderedIndex]
        val cappedPosition = if (reorderedItem.durationMs > 0L) {
            playback.plan.startPositionMs.coerceIn(0L, reorderedItem.durationMs)
        } else {
            playback.plan.startPositionMs.coerceAtLeast(0L)
        }
        return PlannedPlayback(
            queue = reorderedQueue,
            plan = StartPlan(
                startIndex = reorderedIndex,
                item = reorderedItem,
                startPositionMs = cappedPosition,
                reason = playback.plan.reason,
            ),
        )
    }

    private fun applySavedCollectionOrder(
        queue: ResolvedQueue,
        anchorKey: ContentKey?,
        fallbackIndex: Int?,
    ): ResolvedQueue {
        val order = loadCollectionPlaybackOrder(queue.collectionId)
        val anchoredQueue = anchorKey?.let { key -> queue.items.indexOfFirst { it.key == key }.takeIf { it >= 0 } }
            ?.let { queue.copy(requestedIndex = it) } ?: queue
        val reorderedQueue = reorderQueue(anchoredQueue, order)
        if (anchorKey == null && fallbackIndex == null) {
            return reorderedQueue
        }
        val requestedIndex = anchorKey
            ?.let { key -> reorderedQueue.items.indexOfFirst { it.key == key }.takeIf { it >= 0 } }
            ?: fallbackIndex?.let { wrapIndex(it, reorderedQueue.items.size) }
            ?: reorderedQueue.requestedIndex
        return reorderedQueue.copy(requestedIndex = requestedIndex)
    }

    private fun reorderQueue(
        queue: ResolvedQueue,
        order: CollectionPlaybackOrder,
    ): ResolvedQueue {
        return if (queue.collectionId == SpecialFavorites.ID) specialFavorites.orderedQueue(queue, order)
        else QueueOrdering.reorder(queue, order)
    }

    fun fillMissingPublicationTimes(playback: PlannedPlayback): PlannedPlayback {
        if (playback.queue.items.all { it.publishedAtMs > 0 }) return playback
        val seasonId = playback.queue.collectionId?.removePrefix("season:")?.toLongOrNull() ?: return playback
        val authorMid = playback.queue.items.firstNotNullOfOrNull { it.authorMid } ?: return playback
        val dates = api.fetchSeasonPublicationTimes(authorMid, seasonId)
        val items = playback.queue.items.map { item ->
            if (item.publishedAtMs > 0) item else item.copy(publishedAtMs = dates[item.key.bvid] ?: 0L)
        }
        return playback.copy(queue = playback.queue.copy(items = items), plan = playback.plan.copy(item = items[playback.plan.startIndex]))
    }

    private fun parseCreatorCollectionSummaries(
        itemsLists: JSONObject,
        authorMid: Long,
        authorName: String?,
        authorAvatarUrl: String?,
    ): List<CreatorCollectionSummary> {
        val results = mutableListOf<CreatorCollectionSummary>()

        (itemsLists.optJSONArray("seasons_list") ?: JSONArray()).objects().forEach { entry ->
            parseCreatorCollectionSummary(
                entry = entry,
                type = CreatorCollectionType.SEASON,
                authorMid = authorMid,
                authorName = authorName,
                authorAvatarUrl = authorAvatarUrl,
            )?.let(results::add)
        }
        (itemsLists.optJSONArray("series_list") ?: JSONArray()).objects().forEach { entry ->
            parseCreatorCollectionSummary(
                entry = entry,
                type = CreatorCollectionType.SERIES,
                authorMid = authorMid,
                authorName = authorName,
                authorAvatarUrl = authorAvatarUrl,
            )?.let(results::add)
        }

        return results
    }

    private fun parseCreatorCollectionSummary(
        entry: JSONObject,
        type: CreatorCollectionType,
        authorMid: Long,
        authorName: String?,
        authorAvatarUrl: String?,
    ): CreatorCollectionSummary? {
        val meta = entry.optJSONObject("meta") ?: return null
        val collectionId = when (type) {
            CreatorCollectionType.SEASON -> meta.optLong("season_id", 0L)
            CreatorCollectionType.SERIES -> meta.optLong("series_id", 0L)
        }
        if (collectionId <= 0L) {
            return null
        }

        val title = meta.optStringOrNull("name") ?: return null
        val leadBvid = (entry.optJSONArray("archives") ?: JSONArray())
            .objects()
            .firstNotNullOfOrNull { archive -> archive.optStringOrNull("bvid") }
            ?: return null
        val itemCount = meta.optInt("total", entry.optJSONArray("archives")?.length() ?: 0)
            .coerceAtLeast(0)
        val normalizedAuthorMid = meta.optLong("mid", authorMid)
            .takeIf { it > 0L }
            ?: authorMid

        return CreatorCollectionSummary(
            id = "${type.name.lowercase(Locale.US)}:$collectionId",
            type = type,
            collectionId = collectionId,
            authorMid = normalizedAuthorMid,
            authorName = authorName,
            authorAvatarUrl = normalizeRepositoryImageUrl(authorAvatarUrl),
            title = title,
            coverUrl = normalizeRepositoryImageUrl(
                meta.optStringOrNull("cover")
                    ?: entry.optStringOrNull("cover"),
            ),
            itemCount = itemCount,
            leadBvid = leadBvid,
        )
    }
}

object PlaybackJsonCodec {
    fun encode(playback: PlannedPlayback): String {
        val queueJson = JSONObject()
            .putNullable("collectionId", playback.queue.collectionId)
            .putNullable("collectionTitle", playback.queue.collectionTitle)
            .put("requestedIndex", playback.queue.requestedIndex)
            .put("items", JSONArray().apply {
                playback.queue.items.forEach { item ->
                    put(
                        JSONObject()
                            .put("bvid", item.key.bvid)
                            .put("cid", item.key.cid)
                            .put("title", item.title)
                            .put("durationMs", item.durationMs)
                            .put("pageNumber", item.pageNumber)
                            .put("orderInCollection", item.orderInCollection)
                            .putNullable("collectionId", item.collectionId)
                            .putNullable("collectionTitle", item.collectionTitle)
                            .putNullable("coverUrl", item.coverUrl)
                            .putNullable("authorMid", item.authorMid)
                            .putNullable("authorName", item.authorName)
                            .putNullable("authorAvatarUrl", item.authorAvatarUrl)
                            .put("viewCount", item.viewCount)
                            .put("publishedAtMs", item.publishedAtMs)
                            .putNullable("description", item.description),
                    )
                }
            })

        return JSONObject()
            .put("queue", queueJson)
            .put("startIndex", playback.plan.startIndex)
            .put("startPositionMs", playback.plan.startPositionMs)
            .put("reason", playback.plan.reason)
            .toString()
    }

    fun decode(raw: String): PlannedPlayback {
        val root = JSONObject(raw)
        val queueJson = root.getJSONObject("queue")
        val items = queueJson.getJSONArray("items").objects().map { itemJson ->
            QueueItem(
                key = ContentKey(
                    bvid = itemJson.getString("bvid"),
                    cid = itemJson.getLong("cid"),
                ),
                title = itemJson.getString("title"),
                durationMs = itemJson.optLong("durationMs", 0L),
                pageNumber = itemJson.optInt("pageNumber", 1),
                orderInCollection = itemJson.optInt("orderInCollection", 0),
                collectionId = itemJson.optStringOrNull("collectionId"),
                collectionTitle = itemJson.optStringOrNull("collectionTitle"),
                coverUrl = itemJson.optStringOrNull("coverUrl"),
                authorMid = itemJson.optLongOrNull("authorMid"),
                authorName = itemJson.optStringOrNull("authorName"),
                authorAvatarUrl = itemJson.optStringOrNull("authorAvatarUrl"),
                viewCount = itemJson.optLong("viewCount", 0L),
                publishedAtMs = itemJson.optLong("publishedAtMs", 0L),
                description = itemJson.optStringOrNull("description"),
            )
        }
        val queue = ResolvedQueue(
            collectionId = queueJson.optStringOrNull("collectionId"),
            collectionTitle = queueJson.optStringOrNull("collectionTitle"),
            items = items,
            requestedIndex = queueJson.optInt("requestedIndex", 0),
        )
        val startIndex = root.getInt("startIndex")
        return PlannedPlayback(
            queue = queue,
            plan = StartPlan(
                startIndex = startIndex,
                item = queue.items[startIndex],
                startPositionMs = root.optLong("startPositionMs", 0L),
                reason = root.getString("reason"),
            ),
        )
    }
}

object FavoriteJsonCodec {
    fun encode(favorite: FavoriteCollection): String {
        return JSONObject()
            .put("id", favorite.id)
            .put("updatedAt", favorite.updatedAt)
            .put("playback", JSONObject(PlaybackJsonCodec.encode(favorite.playback)))
            .toString()
    }

    fun decode(raw: String): FavoriteCollection {
        val root = JSONObject(raw)
        return FavoriteCollection(
            id = root.getString("id"),
            playback = PlaybackJsonCodec.decode(root.getJSONObject("playback").toString()),
            updatedAt = root.optLong("updatedAt", 0L),
        )
    }
}

object AppGraph {
    private val repositoryRef = AtomicReference<ListenRepository?>()
    private val loggerRef = AtomicReference<DebugLogStore?>()
    private val playerSettingsRef = AtomicReference<PlayerSettingsStore?>()

    fun repository(context: Context): ListenRepository {
        repositoryRef.get()?.let { return it }
        return synchronized(repositoryRef) {
            repositoryRef.get() ?: ListenRepository(context.applicationContext).also {
                // Run once on cold startup, before the UI, service or a widget restores playback.
                it.cleanupExpiredResumeRecords()
                repositoryRef.set(it)
            }
        }
    }

    fun logger(context: Context): DebugLogStore {
        loggerRef.get()?.let { return it }
        val created = DebugLogStore(context.applicationContext)
        return if (loggerRef.compareAndSet(null, created)) {
            created
        } else {
            loggerRef.get()!!
        }
    }

    fun playerSettings(context: Context): PlayerSettingsStore {
        playerSettingsRef.get()?.let { return it }
        val created = PlayerSettingsStore(context.applicationContext)
        return if (playerSettingsRef.compareAndSet(null, created)) {
            created
        } else {
            playerSettingsRef.get()!!
        }
    }
}

private fun JSONObject.optStringOrNull(name: String): String? {
    if (!has(name) || isNull(name)) {
        return null
    }
    return optString(name).trim().takeIf { it.isNotEmpty() }
}

private fun JSONObject.optLongOrNull(name: String): Long? {
    if (!has(name) || isNull(name)) {
        return null
    }
    return optLong(name, 0L).takeIf { it != 0L }
}

private fun JSONObject.putNullable(name: String, value: String?): JSONObject {
    if (value != null) {
        put(name, value)
    }
    return this
}

private fun JSONObject.putNullable(name: String, value: Long?): JSONObject {
    if (value != null) {
        put(name, value)
    }
    return this
}

private fun JSONArray.objects(): List<JSONObject> {
    val result = ArrayList<JSONObject>(length())
    for (index in 0 until length()) {
        optJSONObject(index)?.let(result::add)
    }
    return result
}

private fun JSONArray.objectsAsStrings(): List<String> {
    val result = ArrayList<String>(length())
    for (index in 0 until length()) {
        val value = optString(index, "")
        if (value.isNotBlank()) {
            result += value
        }
    }
    return result
}

private fun normalizeRepositoryImageUrl(value: String?): String? {
    val normalized = value?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    return when {
        normalized.startsWith("//") -> "https:$normalized"
        normalized.startsWith("http://") -> "https://${normalized.removePrefix("http://")}"
        else -> normalized
    }
}
