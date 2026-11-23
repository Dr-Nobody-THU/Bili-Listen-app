package com.bililisten.android.player

import android.net.Uri
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import java.io.IOException

/** Recheck the policy when ExoPlayer reopens a stream or keeps buffering after a network change. */
@UnstableApi
internal class PlaybackDataSource(
    private val upstream: DataSource,
    private val allowNetwork: () -> Boolean,
) : DataSource {
    private var networkSource = false

    override fun open(dataSpec: DataSpec): Long {
        networkSource = dataSpec.uri.scheme in setOf("http", "https")
        checkPolicy()
        return upstream.open(dataSpec)
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        checkPolicy()
        return upstream.read(buffer, offset, length)
    }

    private fun checkPolicy() {
        if (networkSource && !allowNetwork()) throw IOException("已停止移动网络音频请求，请使用本地缓存或连接 Wi-Fi")
    }

    override fun getUri(): Uri? = upstream.uri
    override fun getResponseHeaders(): Map<String, List<String>> = upstream.responseHeaders
    override fun addTransferListener(transferListener: TransferListener) = upstream.addTransferListener(transferListener)
    override fun close() = upstream.close()

    class Factory(private val upstream: DataSource.Factory, private val allowNetwork: () -> Boolean) : DataSource.Factory {
        override fun createDataSource(): DataSource = PlaybackDataSource(upstream.createDataSource(), allowNetwork)
    }
}
