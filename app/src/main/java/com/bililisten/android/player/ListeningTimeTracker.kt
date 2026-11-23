package com.bililisten.android.player

/** Uses monotonic wall time while audio is actually playing, independent of media position/speed. */
internal class ListeningTimeTracker(
    private val clock: () -> Long,
    private val record: (collectionId: String, elapsedMs: Long) -> Unit,
) {
    private var activeCollection: String? = null
    private var checkpointAt = clock()

    fun update(collectionId: String?, isPlaying: Boolean) {
        val now = clock()
        val elapsed = (now - checkpointAt).coerceAtLeast(0L)
        activeCollection?.let { if (elapsed > 0L) record(it, elapsed) }
        checkpointAt = now
        activeCollection = collectionId.takeIf { isPlaying }
    }

    fun checkpoint() = update(activeCollection, activeCollection != null)
    fun stop() = update(null, false)
}
