package com.ourbloom.admin.bugs

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.util.Collections

object AdminBugRadar {

    private const val TAG = "BloomBugRadar"
    private const val PREFS_NAME = "bloom_bug_radar_prefs"
    private const val KEY_BUGS_JSON = "saved_bugs_json"
    private const val MAX_BUGS = 50

    private val bugList = Collections.synchronizedList(mutableListOf<DetectedBug>())
    private val _bugsState = MutableStateFlow<List<DetectedBug>>(emptyList())
    val bugsState = _bugsState.asStateFlow()

    private val _newBugAlert = MutableSharedFlow<DetectedBug>(extraBufferCapacity = 10)
    val newBugAlert = _newBugAlert.asSharedFlow()

    private var appContext: Context? = null

    /**
     * Initializes the ultra-sensitive bug detector:
     * 1. Restores previously detected bugs from disk.
     * 2. Registers default uncaught exception handler to intercept fatal crashes.
     */
    fun init(application: Application) {
        appContext = application.applicationContext
        loadSavedBugs(application)

        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                Log.e(TAG, "Fatal uncaught crash intercepted on thread: ${thread.name}", throwable)
                record(
                    tag = "FatalCrash/${thread.name}",
                    message = throwable.message ?: throwable.javaClass.simpleName,
                    throwable = throwable,
                    severity = BugSeverity.CRITICAL,
                    isFatal = true
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error logging crash in handler", e)
            } finally {
                defaultHandler?.uncaughtException(thread, throwable)
            }
        }

        Log.i(TAG, "Bloom Bug Radar initialized. Sensitive error interception ACTIVE.")
    }

    /**
     * Records any error, exception, or failure — detecting even a single small error.
     */
    fun record(
        tag: String,
        message: String,
        throwable: Throwable? = null,
        severity: BugSeverity = BugSeverity.WARNING,
        isFatal: Boolean = false
    ) {
        val stackTrace = DetectedBug.extractStackTrace(throwable)
        val bug = DetectedBug(
            tag = tag,
            message = message,
            technicalDetails = stackTrace,
            severity = severity,
            timestamp = System.currentTimeMillis(),
            isFatal = isFatal
        )

        Log.e(TAG, "[$severity] [$tag] $message", throwable)

        synchronized(bugList) {
            bugList.add(0, bug)
            if (bugList.size > MAX_BUGS) {
                bugList.removeAt(bugList.size - 1)
            }
            _bugsState.value = bugList.toList()
        }

        _newBugAlert.tryEmit(bug)

        // Persist locally & sync to Firestore
        appContext?.let { ctx ->
            saveBugsToDisk(ctx)
            syncBugToFirestore(bug)
        }
    }

    fun bindToActivity(activity: AppCompatActivity) {
        activity.lifecycleScope.launch {
            newBugAlert.collect { bug ->
                try {
                    val rootView = activity.findViewById<android.view.View>(android.R.id.content)
                    if (rootView != null) {
                        val icon = if (bug.severity == BugSeverity.CRITICAL) "🚨" else "⚠️"
                        val snackbar = Snackbar.make(
                            rootView,
                            "$icon Bug Radar: [${bug.tag}] ${bug.message.take(45)}...",
                            Snackbar.LENGTH_LONG
                        )
                        snackbar.setAction("INSPECT") {
                            BugRadarDialog(activity).show()
                        }
                        snackbar.show()
                    }
                } catch (_: Exception) {}
            }
        }
    }

    fun clearAllBugs() {
        synchronized(bugList) {
            bugList.clear()
            _bugsState.value = emptyList()
        }
        appContext?.let { ctx ->
            ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .remove(KEY_BUGS_JSON)
                .apply()
        }
    }

    fun simulateTestError(context: Context) {
        try {
            throw IllegalStateException("Test exception triggered manually by admin to verify Bug Radar detection.")
        } catch (e: Exception) {
            record(
                tag = "Diagnostics/TestRadar",
                message = "Simulated test error verified successfully 🌸",
                throwable = e,
                severity = BugSeverity.WARNING
            )
        }
    }

    private fun saveBugsToDisk(context: Context) {
        try {
            val array = JSONArray()
            synchronized(bugList) {
                bugList.take(MAX_BUGS).forEach { bug ->
                    val obj = JSONObject().apply {
                        put("id", bug.id)
                        put("tag", bug.tag)
                        put("message", bug.message)
                        put("technicalDetails", bug.technicalDetails ?: "")
                        put("severity", bug.severity.name)
                        put("timestamp", bug.timestamp)
                        put("isFatal", bug.isFatal)
                        put("deviceModel", bug.deviceModel)
                        put("androidVersion", bug.androidVersion)
                        put("appVersion", bug.appVersion)
                    }
                    array.put(obj)
                }
            }
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_BUGS_JSON, array.toString())
                .apply()
        } catch (e: Exception) {
            Log.w(TAG, "Error saving bugs to disk", e)
        }
    }

    private fun loadSavedBugs(context: Context) {
        try {
            val raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(KEY_BUGS_JSON, null) ?: return

            val array = JSONArray(raw)
            val loaded = mutableListOf<DetectedBug>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val severityName = obj.optString("severity", BugSeverity.WARNING.name)
                val severity = try { BugSeverity.valueOf(severityName) } catch (_: Exception) { BugSeverity.WARNING }

                val bug = DetectedBug(
                    id = obj.optString("id"),
                    tag = obj.optString("tag"),
                    message = obj.optString("message"),
                    technicalDetails = obj.optString("technicalDetails").ifBlank { null },
                    severity = severity,
                    timestamp = obj.optLong("timestamp"),
                    isFatal = obj.optBoolean("isFatal"),
                    deviceModel = obj.optString("deviceModel"),
                    androidVersion = obj.optString("androidVersion"),
                    appVersion = obj.optString("appVersion")
                )
                loaded.add(bug)
            }

            synchronized(bugList) {
                bugList.clear()
                bugList.addAll(loaded)
                _bugsState.value = bugList.toList()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error loading saved bugs from disk", e)
        }
    }

    private fun syncBugToFirestore(bug: DetectedBug) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = FirebaseFirestore.getInstance()
                val data = mapOf(
                    "bugId" to bug.id,
                    "tag" to bug.tag,
                    "message" to bug.message,
                    "technicalDetails" to (bug.technicalDetails ?: ""),
                    "severity" to bug.severity.name,
                    "timestamp" to bug.timestamp,
                    "isFatal" to bug.isFatal,
                    "deviceModel" to bug.deviceModel,
                    "androidVersion" to bug.androidVersion,
                    "appVersion" to bug.appVersion,
                    "status" to "OPEN"
                )
                db.collection("admin_bug_reports").document(bug.id).set(data, SetOptions.merge())
            } catch (e: Exception) {
                Log.w(TAG, "Error uploading bug report to Firestore: ${e.message}")
            }
        }
    }
}
