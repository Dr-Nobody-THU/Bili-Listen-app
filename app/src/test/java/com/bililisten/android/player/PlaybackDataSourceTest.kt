package com.bililisten.android.player

import android.net.Uri
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException

@UnstableApi
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class PlaybackDataSourceTest {
    private class CountingSource : DataSource {
        var opens = 0
        var reads = 0
        override fun open(dataSpec: DataSpec): Long { opens++; return 100L }
        override fun read(buffer: ByteArray, offset: Int, length: Int): Int { reads++; return -1 }
        override fun getUri(): Uri? = null
        override fun addTransferListener(transferListener: TransferListener) = Unit
        override fun close() = Unit
    }

    @Test fun mobilePolicyBlocksNetworkBeforeOpeningConnection() {
        val upstream = CountingSource()
        val source = PlaybackDataSource(upstream) { false }
        assertThrows(IOException::class.java) { source.open(DataSpec(Uri.parse("https://audio.example/track"))) }
        assertEquals(0, upstream.opens)
    }

    @Test fun wifiLossStopsFurtherNetworkReads() {
        var wifi = true
        val upstream = CountingSource()
        val source = PlaybackDataSource(upstream) { wifi }
        source.open(DataSpec(Uri.parse("https://audio.example/track")))
        wifi = false
        assertThrows(IOException::class.java) { source.read(ByteArray(10), 0, 10) }
        assertEquals(0, upstream.reads)
    }

    @Test fun cachedFilesRemainPlayableWithoutNetworkPermission() {
        val upstream = CountingSource()
        val source = PlaybackDataSource(upstream) { false }
        source.open(DataSpec(Uri.parse("file:///audio_cache/track.m4a")))
        source.read(ByteArray(10), 0, 10)
        assertEquals(1, upstream.opens)
        assertEquals(1, upstream.reads)
    }
}
