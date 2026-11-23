package com.bililisten.android.data

import com.bililisten.android.TestFixtures
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException

class AudioCacheFilesTest {
    @get:Rule val folder = TemporaryFolder()

    @Test fun incompleteDownloadCannotBeUsedAsCache() {
        val files = AudioCacheFiles(folder.root)
        val item = TestFixtures.item(0)
        val destination = files.file(item).apply { parentFile!!.mkdirs() }
        val temp = File(destination.path + ".part").apply { writeBytes(ByteArray(7)) }
        assertNull(files.cached(item))
        assertThrows(IOException::class.java) { files.commit(temp, destination, 12) }
        assertNull(files.cached(item))
        assertFalse(destination.exists())
    }

    @Test fun completeDownloadBecomesPlayableAtomicallyIncludingSingleVideos() {
        val files = AudioCacheFiles(folder.root)
        val item = TestFixtures.item(0, collection = null)
        val destination = files.file(item).apply { parentFile!!.mkdirs() }
        val temp = File(destination.path + ".part").apply { writeBytes(ByteArray(12)) }
        files.commit(temp, destination, 12)
        assertEquals(destination, files.cached(item))
        assertFalse(temp.exists())
    }

    @Test fun emptyResponsesAreNotCachedEvenWithoutContentLength() {
        val files = AudioCacheFiles(folder.root)
        val destination = files.file(TestFixtures.item(0)).apply { parentFile!!.mkdirs() }
        val temp = File(destination.path + ".part").apply { writeBytes(byteArrayOf()) }
        assertThrows(IOException::class.java) { files.commit(temp, destination, -1) }
    }

    @Test fun evictionKeepsActiveWindowAndRecentlyUsedFiles() {
        val files = AudioCacheFiles(folder.root)
        val audio = (0..3).map { index -> files.file(TestFixtures.item(index)).apply {
            parentFile!!.mkdirs(); writeBytes(ByteArray(10)); setLastModified(1000L + index)
        } }
        assertEquals(2, files.trim(setOf(audio[0]), 20))
        assertTrue(audio[0].exists())
        assertTrue(audio[3].exists())
        assertFalse(audio[1].exists())
        assertFalse(audio[2].exists())
    }
}
