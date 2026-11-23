package com.bililisten.android.data

import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.io.InterruptedIOException
import java.net.SocketTimeoutException
import java.util.concurrent.CancellationException

class AudioCacheTransferTest {
    @Test fun cancellingATransferIsNotReportedAsWifiLoss() {
        Thread.currentThread().interrupt()
        try {
            val error = assertThrows(CancellationException::class.java) {
                checkCacheTransferAllowed { throw AssertionError("Cancellation must be checked before the network") }
            }
            assertTrue(isCacheTransferCancelled(error))
            assertFalse(error.message.orEmpty().contains("Wi-Fi"))
        } finally {
            Thread.interrupted()
        }
    }

    @Test fun ioCancellationIsRecognizedEvenWhenIoClearsTheThreadFlag() {
        assertFalse(Thread.currentThread().isInterrupted)
        assertTrue(isCacheTransferCancelled(InterruptedIOException("interrupted")))
        assertFalse(isCacheTransferCancelled(SocketTimeoutException("timed out")))
        assertFalse(isCacheTransferCancelled(IOException("disk full")))
    }

    @Test fun actualWifiLossStillPausesTheTransfer() {
        checkCacheTransferAllowed { true }
        val error = assertThrows(IOException::class.java) { checkCacheTransferAllowed { false } }
        assertTrue(error.message.orEmpty().contains("Wi-Fi"))
        assertFalse(isCacheTransferCancelled(error))
    }
}
