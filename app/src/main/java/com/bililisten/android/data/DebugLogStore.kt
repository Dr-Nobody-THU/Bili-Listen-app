package com.bililisten.android.data

import android.content.Context
import android.content.SharedPreferences
import java.io.File
import java.io.FileOutputStream
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

data class DebugSnapshot(
    val latestStatus: String?,
    val latestError: String?,
    val recentLines: List<String>,
    val logFilePath: String,
)

class DebugLogStore(context: Context) {
    private val appContext = context.applicationContext
    private val prefs: SharedPreferences =
        appContext.getSharedPreferences("debug_log_store", Context.MODE_PRIVATE)
    private val logFile: File = File(appContext.filesDir, "logs/playback.log").apply {
        parentFile?.mkdirs()
        if (!exists()) {
            createNewFile()
        }
    }
    private val formatter = DateTimeFormatter.ofPattern("MM-dd HH:mm:ss.SSS", Locale.US)
        .withZone(ZoneId.systemDefault())
    private val lock = Any()

    fun info(source: String, message: String) {
        write(level = "INFO", source = source, message = message, throwable = null)
    }

    fun error(source: String, message: String, throwable: Throwable? = null) {
        write(level = "ERROR", source = source, message = message, throwable = throwable)
    }

    fun snapshot(maxLines: Int = 60): DebugSnapshot {
        val latestStatus = prefs.getString(KEY_LATEST_STATUS, null)
        val latestError = prefs.getString(KEY_LATEST_ERROR, null)
        val recentLines = synchronized(lock) {
            if (!logFile.exists()) {
                emptyList()
            } else {
                logFile.readLines(Charsets.UTF_8).takeLast(maxLines)
            }
        }
        return DebugSnapshot(
            latestStatus = latestStatus,
            latestError = latestError,
            recentLines = recentLines,
            logFilePath = logFile.absolutePath,
        )
    }

    private fun write(source: String, level: String, message: String, throwable: Throwable?) {
        val now = System.currentTimeMillis()
        val timestamp = formatter.format(Instant.ofEpochMilli(now))
        val errorSuffix = throwable?.let {
            " | ${it::class.java.simpleName}: ${it.message ?: "no message"}"
        }.orEmpty()
        val line = "$timestamp [$level] $source - $message$errorSuffix"

        synchronized(lock) {
            FileOutputStream(logFile, true).bufferedWriter(Charsets.UTF_8).use { writer ->
                writer.appendLine(line)
                throwable?.stackTrace?.take(12)?.forEach { element ->
                    writer.appendLine("    at $element")
                }
            }
        }

        prefs.edit()
            .putString(KEY_LATEST_STATUS, line)
            .putLong(KEY_LAST_UPDATED_AT, now)
            .apply()

        if (level == "ERROR") {
            prefs.edit()
                .putString(KEY_LATEST_ERROR, line)
                .putLong(KEY_LAST_UPDATED_AT, now)
                .apply()
        }
    }

    companion object {
        private const val KEY_LATEST_STATUS = "latest_status"
        private const val KEY_LATEST_ERROR = "latest_error"
        private const val KEY_LAST_UPDATED_AT = "last_updated_at"
    }
}
