package com.ourbloom.app.util

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.ourbloom.app.BuildConfig
import com.ourbloom.app.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

data class DetectedError(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val message: String,
    val technicalDetails: String? = null,
    val screenName: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val isCrash: Boolean = false
) {
    fun formatDiagnosticReport(userNote: String = ""): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        return buildString {
            appendLine("=== OUR BLOOM ERROR REPORT ===")
            appendLine("Error ID: $id")
            appendLine("Time: ${dateFormat.format(Date(timestamp))}")
            appendLine("Title: $title")
            appendLine("Message: $message")
            if (!screenName.isNullOrBlank()) {
                appendLine("Screen: $screenName")
            }
            appendLine("App Version: v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL} (Brand: ${Build.BRAND})")
            appendLine("Android OS: Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            val user = FirebaseAuth.getInstance().currentUser
            appendLine("User: ${user?.email ?: "Not signed in"} (UID: ${user?.uid ?: "none"})")
            if (userNote.isNotBlank()) {
                appendLine("\n--- User Note ---")
                appendLine(userNote.trim())
            }
            if (!technicalDetails.isNullOrBlank()) {
                appendLine("\n--- Technical Stacktrace / Details ---")
                appendLine(technicalDetails)
            }
            appendLine("==============================")
        }
    }
}

object ErrorReporter {

    private const val TAG = "OurBloomErrorReporter"
    private const val PREFS_NAME = "ourbloom_crash_prefs"
    private const val KEY_PENDING_CRASH = "pending_crash_data"

    private val _errorEvents = MutableSharedFlow<DetectedError>(extraBufferCapacity = 10)
    val errorEvents = _errorEvents.asSharedFlow()

    fun notifyError(
        title: String,
        message: String,
        throwable: Throwable? = null,
        screenName: String? = null,
        autoPrompt: Boolean = true
    ) {
        val stackTrace = throwable?.let {
            val sw = StringWriter()
            it.printStackTrace(PrintWriter(sw))
            sw.toString()
        }

        val error = DetectedError(
            title = title,
            message = message,
            technicalDetails = stackTrace,
            screenName = screenName,
            timestamp = System.currentTimeMillis(),
            isCrash = false
        )

        Log.e(TAG, "Auto-detected error: [$title] $message (Screen: $screenName)", throwable)

        if (autoPrompt) {
            _errorEvents.tryEmit(error)
        }
    }

    fun bindToActivity(activity: FragmentActivity) {
        activity.lifecycleScope.launch {
            errorEvents.collect { error ->
                showAutoDetectedPill(activity, error)
            }
        }
    }

    private fun showAutoDetectedPill(activity: FragmentActivity, error: DetectedError) {
        try {
            val rootView = activity.findViewById<View>(android.R.id.content) ?: return
            val snackbar = Snackbar.make(
                rootView,
                "⚠️ ${error.title}: ${error.message.take(45)}${if (error.message.length > 45) "..." else ""}",
                Snackbar.LENGTH_LONG
            )
            snackbar.setAction("REPORT") {
                showReportSheet(activity, error)
            }
            snackbar.setActionTextColor(activity.getColor(R.color.chat_action_pink))
            snackbar.show()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to show auto error pill", e)
        }
    }

    fun showReportSheet(activity: FragmentActivity, error: DetectedError) {
        try {
            val existing = activity.supportFragmentManager.findFragmentByTag("ReportErrorBottomSheet")
            if (existing == null) {
                val sheet = ReportErrorBottomSheetDialogFragment.newInstance(error)
                sheet.show(activity.supportFragmentManager, "ReportErrorBottomSheet")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to show report bottom sheet", e)
        }
    }

    fun recordCrash(context: Context, throwable: Throwable) {
        try {
            val sw = StringWriter()
            throwable.printStackTrace(PrintWriter(sw))
            val trace = sw.toString()

            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val json = org.json.JSONObject().apply {
                put("id", UUID.randomUUID().toString())
                put("title", "App Crash (Unhandled Exception)")
                put("message", throwable.message ?: throwable.javaClass.simpleName)
                put("technicalDetails", trace)
                put("screenName", "Crash Handler")
                put("timestamp", System.currentTimeMillis())
                put("isCrash", true)
            }
            prefs.edit().putString(KEY_PENDING_CRASH, json.toString()).commit()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to record crash", e)
        }
    }

    fun checkAndPromptPendingCrash(activity: FragmentActivity) {
        try {
            val prefs = activity.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val raw = prefs.getString(KEY_PENDING_CRASH, null) ?: return
            prefs.edit().remove(KEY_PENDING_CRASH).apply()

            val json = org.json.JSONObject(raw)
            val error = DetectedError(
                id = json.optString("id", UUID.randomUUID().toString()),
                title = json.optString("title", "Previous App Crash"),
                message = json.optString("message", "The app stopped unexpectedly."),
                technicalDetails = json.optString("technicalDetails", ""),
                screenName = json.optString("screenName", "Crash Recovery"),
                timestamp = json.optLong("timestamp", System.currentTimeMillis()),
                isCrash = true
            )

            // Show report dialog to user
            showReportSheet(activity, error)
        } catch (e: Exception) {
            Log.e(TAG, "Error checking pending crash", e)
        }
    }

    suspend fun submitReport(error: DetectedError, userNote: String): Boolean {
        return try {
            val db = FirebaseFirestore.getInstance()
            val user = FirebaseAuth.getInstance().currentUser
            val data = hashMapOf(
                "errorId" to error.id,
                "title" to error.title,
                "message" to error.message,
                "technicalDetails" to (error.technicalDetails ?: ""),
                "screenName" to (error.screenName ?: "Unknown"),
                "userNote" to userNote.trim(),
                "timestamp" to FieldValue.serverTimestamp(),
                "clientTimestamp" to error.timestamp,
                "isCrash" to error.isCrash,
                "appVersion" to BuildConfig.VERSION_NAME,
                "appVersionCode" to BuildConfig.VERSION_CODE,
                "deviceManufacturer" to Build.MANUFACTURER,
                "deviceModel" to Build.MODEL,
                "androidVersion" to Build.VERSION.RELEASE,
                "androidSdk" to Build.VERSION.SDK_INT,
                "userId" to (user?.uid ?: "anonymous"),
                "userEmail" to (user?.email ?: "anonymous"),
                "status" to "OPEN"
            )

            db.collection("error_reports").document(error.id).set(data).await()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to submit error report to Firestore", e)
            false
        }
    }
}

class ReportErrorBottomSheetDialogFragment : BottomSheetDialogFragment() {

    private var detectedError: DetectedError? = null
    private var isDetailsExpanded = false

    companion object {
        private const val ARG_ERROR_ID = "error_id"
        private const val ARG_TITLE = "error_title"
        private const val ARG_MESSAGE = "error_message"
        private const val ARG_DETAILS = "error_details"
        private const val ARG_SCREEN = "error_screen"
        private const val ARG_TIMESTAMP = "error_timestamp"
        private const val ARG_IS_CRASH = "error_is_crash"

        fun newInstance(error: DetectedError): ReportErrorBottomSheetDialogFragment {
            return ReportErrorBottomSheetDialogFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_ERROR_ID, error.id)
                    putString(ARG_TITLE, error.title)
                    putString(ARG_MESSAGE, error.message)
                    putString(ARG_DETAILS, error.technicalDetails)
                    putString(ARG_SCREEN, error.screenName)
                    putLong(ARG_TIMESTAMP, error.timestamp)
                    putBoolean(ARG_IS_CRASH, error.isCrash)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val args = arguments
        if (args != null) {
            detectedError = DetectedError(
                id = args.getString(ARG_ERROR_ID, UUID.randomUUID().toString()),
                title = args.getString(ARG_TITLE, "Error"),
                message = args.getString(ARG_MESSAGE, ""),
                technicalDetails = args.getString(ARG_DETAILS),
                screenName = args.getString(ARG_SCREEN),
                timestamp = args.getLong(ARG_TIMESTAMP, System.currentTimeMillis()),
                isCrash = args.getBoolean(ARG_IS_CRASH, false)
            )
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.bottom_sheet_report_error, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val error = detectedError ?: return

        val btnClose = view.findViewById<ImageView>(R.id.btn_close_report)
        val tvTitle = view.findViewById<TextView>(R.id.tv_error_title)
        val tvMessage = view.findViewById<TextView>(R.id.tv_error_message)
        val tvContext = view.findViewById<TextView>(R.id.tv_error_context)
        val headerToggleDetails = view.findViewById<LinearLayout>(R.id.header_toggle_details)
        val tvToggleIcon = view.findViewById<TextView>(R.id.tv_toggle_icon)
        val layoutExpandedDetails = view.findViewById<LinearLayout>(R.id.layout_expanded_details)
        val tvDeviceInfo = view.findViewById<TextView>(R.id.tv_device_info)
        val tvStacktrace = view.findViewById<TextView>(R.id.tv_stacktrace)
        val etUserNote = view.findViewById<EditText>(R.id.et_user_note)
        val btnSendReport = view.findViewById<MaterialButton>(R.id.btn_send_report)
        val btnCopyDetails = view.findViewById<MaterialButton>(R.id.btn_copy_details)
        val btnShareDetails = view.findViewById<MaterialButton>(R.id.btn_share_details)
        val pbSending = view.findViewById<ProgressBar>(R.id.pb_sending_report)

        btnClose.setOnClickListener { dismiss() }

        tvTitle.text = error.title
        tvMessage.text = error.message

        val dateFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
        val screenInfo = error.screenName?.let { "Screen: $it • " } ?: ""
        tvContext.text = "$screenInfo${dateFormat.format(Date(error.timestamp))}"

        tvDeviceInfo.text = "App: v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) • Android ${Build.VERSION.RELEASE} • ${Build.MANUFACTURER} ${Build.MODEL}"
        tvStacktrace.text = error.technicalDetails?.ifBlank { "No stacktrace recorded." } ?: "No stacktrace recorded."

        // Collapsible technical diagnostics
        headerToggleDetails.setOnClickListener {
            isDetailsExpanded = !isDetailsExpanded
            layoutExpandedDetails.visibility = if (isDetailsExpanded) View.VISIBLE else View.GONE
            tvToggleIcon.text = if (isDetailsExpanded) "▲ Hide" else "▼ Show"
        }

        // Copy Details Button
        btnCopyDetails.setOnClickListener {
            val fullReport = error.formatDiagnosticReport(etUserNote.text.toString())
            val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("OurBloom Error Report", fullReport)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(requireContext(), "Diagnostic report copied to clipboard! 📋", Toast.LENGTH_SHORT).show()
        }

        // Share Details Button
        btnShareDetails.setOnClickListener {
            val fullReport = error.formatDiagnosticReport(etUserNote.text.toString())
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, "Our Bloom Error Report: ${error.title}")
                putExtra(Intent.EXTRA_TEXT, fullReport)
            }
            startActivity(Intent.createChooser(intent, "Share Error Report"))
        }

        // Send Report to Developers Button
        btnSendReport.setOnClickListener {
            val note = etUserNote.text.toString()
            btnSendReport.isEnabled = false
            pbSending.visibility = View.VISIBLE

            CoroutineScope(Dispatchers.Main).launch {
                val success = ErrorReporter.submitReport(error, note)
                pbSending.visibility = View.GONE
                if (success) {
                    Toast.makeText(requireContext(), "Thank you! Error report sent to developers 🌸", Toast.LENGTH_LONG).show()
                    dismiss()
                } else {
                    btnSendReport.isEnabled = true
                    Toast.makeText(requireContext(), "Report submission saved locally. You can also Copy or Share details.", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}
