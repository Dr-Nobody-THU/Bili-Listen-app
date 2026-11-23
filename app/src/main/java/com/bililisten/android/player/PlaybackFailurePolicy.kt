package com.bililisten.android.player

import androidx.media3.common.PlaybackException
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlaybackException
import java.io.EOFException

@UnstableApi
internal object PlaybackFailurePolicy {
    fun invalidatesLocalCache(error: PlaybackException): Boolean {
        // Decoder, output device and audio processor errors cannot establish file corruption.
        if (error is ExoPlaybackException && error.type != ExoPlaybackException.TYPE_SOURCE) return false
        return error.errorCode == PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED ||
            error.errorCode == PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND ||
            (error.errorCode == PlaybackException.ERROR_CODE_IO_UNSPECIFIED &&
                generateSequence<Throwable>(error) { it.cause }.take(8).any { it is EOFException })
    }

    fun message(error: PlaybackException, local: Boolean): String {
        if (invalidatesLocalCache(error) && local) return "缓存文件不完整，请连接 Wi-Fi 后重试缓存。"
        if (error is ExoPlaybackException && error.type != ExoPlaybackException.TYPE_SOURCE ||
            error.errorCode in 4000..5999) {
            return "音频输出异常，请重试。" + if (local) "本地缓存已保留。" else ""
        }
        return if (local) "暂时无法播放这条音频，本地缓存已保留，请重试。"
            else "音频连接失败，请重试或连接 Wi-Fi 后缓存。"
    }
}
