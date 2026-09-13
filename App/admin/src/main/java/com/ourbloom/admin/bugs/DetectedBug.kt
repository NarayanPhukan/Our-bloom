package com.ourbloom.admin.bugs

import android.os.Build
import com.ourbloom.admin.BuildConfig
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

enum class BugSeverity {
    CRITICAL,
    WARNING,
    NETWORK_ERROR,
    UI_ERROR
}

data class DetectedBug(
    val id: String = UUID.randomUUID().toString(),
    val tag: String,
    val message: String,
    val technicalDetails: String? = null,
    val severity: BugSeverity = BugSeverity.WARNING,
    val timestamp: Long = System.currentTimeMillis(),
    val isFatal: Boolean = false,
    val deviceModel: String = "${Build.MANUFACTURER} ${Build.MODEL}",
    val androidVersion: String = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
    val appVersion: String = "v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"
) {
    fun formatDiagnosticReport(): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        return buildString {
            appendLine("=== BLOOM ADMIN BUG REPORT ===")
            appendLine("Bug ID: $id")
            appendLine("Severity: ${severity.name}")
            appendLine("Tag/Component: $tag")
            appendLine("Time: ${dateFormat.format(Date(timestamp))}")
            appendLine("Message: $message")
            appendLine("Is Fatal Crash: $isFatal")
            appendLine("App Version: $appVersion")
            appendLine("Device: $deviceModel")
            appendLine("OS: $androidVersion")
            if (!technicalDetails.isNullOrBlank()) {
                appendLine("\n--- Stack Trace ---")
                appendLine(technicalDetails)
            }
            appendLine("===============================")
        }
    }

    companion object {
        fun extractStackTrace(throwable: Throwable?): String? {
            if (throwable == null) return null
            val sw = StringWriter()
            throwable.printStackTrace(PrintWriter(sw))
            return sw.toString()
        }
    }
}
