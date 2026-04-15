package com.volya.vpn.util

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Simple file-based logger for crash diagnostics.
 * Writes to the app's external files directory so logs can be read without root.
 */
object FileLogger {
    private const val LOG_FILE = "volya_vpn.log"
    private const val MAX_LOG_SIZE = 500 * 1024 // 500KB max, then truncate

    private var logFile: File? = null

    fun init(context: Context) {
        try {
            val dir = context.getExternalFilesDir(null) ?: context.filesDir
            logFile = File(dir, LOG_FILE)
            // Rotate: if file is too large, keep last half
            if (logFile!!.exists() && logFile!!.length() > MAX_LOG_SIZE) {
                val content = logFile!!.readText()
                val truncated = content.takeLast(content.length / 2)
                logFile!!.writeText(truncated)
            }
            info("FileLogger initialized at ${logFile!!.absolutePath}")
        } catch (e: Exception) {
            android.util.Log.e("VolyaVPN", "FileLogger init failed", e)
        }
    }

    fun info(message: String) = log("INFO", message, null)
    fun warn(message: String) = log("WARN", message, null)
    fun error(message: String, e: Throwable? = null) = log("ERROR", message, e)

    fun log(level: String, message: String, e: Throwable?) {
        val file = logFile ?: return
        try {
            val timestamp = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date())
            val line = buildString {
                append("[$timestamp] $level: $message")
                if (e != null) {
                    append("\n  EXCEPTION: ${e::class.simpleName}: ${e.message}")
                    e.stackTrace.take(10).forEach {
                        append("\n    at $it")
                    }
                    e.cause?.let { cause ->
                        append("\n  CAUSED BY: ${cause::class.simpleName}: ${cause.message}")
                    }
                }
                append("\n")
            }
            file.appendText(line)
        } catch (ignored: Exception) {
            // Don't let logging crash the app
        }
    }
}
