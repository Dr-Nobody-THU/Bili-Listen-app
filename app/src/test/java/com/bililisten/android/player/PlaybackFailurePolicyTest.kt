package com.bililisten.android.player

import androidx.media3.common.PlaybackException
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlaybackException
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.EOFException
import java.io.IOException

@UnstableApi
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class PlaybackFailurePolicyTest {
    @Test fun backgroundProcessorFailureCannotDeleteValidCachedAudio() {
        val error = ExoPlaybackException.createForUnexpected(
            IllegalArgumentException("The source buffer is this buffer"),
            PlaybackException.ERROR_CODE_FAILED_RUNTIME_CHECK,
        )
        assertFalse(PlaybackFailurePolicy.invalidatesLocalCache(error))
        assertTrue(PlaybackFailurePolicy.message(error, local = true).contains("缓存已保留"))
        assertFalse(PlaybackFailurePolicy.message(error, local = true).contains("Wi-Fi"))
    }

    @Test fun decoderDeviceAndSeekErrorsKeepTheFile() {
        for (code in listOf(
            PlaybackException.ERROR_CODE_DECODING_FAILED,
            PlaybackException.ERROR_CODE_AUDIO_TRACK_INIT_FAILED,
            PlaybackException.ERROR_CODE_AUDIO_TRACK_WRITE_FAILED,
            PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE,
            PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
            PlaybackException.ERROR_CODE_IO_UNSPECIFIED,
        )) {
            assertFalse("Preserve cache for $code", PlaybackFailurePolicy.invalidatesLocalCache(PlaybackException("error", null, code)))
        }
    }

    @Test fun confirmedMalformedMissingOrTruncatedSourcesCanBeDownloadedAgain() {
        for (code in listOf(PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED, PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND)) {
            assertTrue(PlaybackFailurePolicy.invalidatesLocalCache(ExoPlaybackException.createForSource(IOException("file"), code)))
        }
        assertTrue(PlaybackFailurePolicy.invalidatesLocalCache(ExoPlaybackException.createForSource(
            IOException("truncated", EOFException()), PlaybackException.ERROR_CODE_IO_UNSPECIFIED,
        )))
    }
}
