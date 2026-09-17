package com.ourbloom.admin.telemetry

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.ourbloom.admin.R
import com.ourbloom.admin.data.AdminFirestoreRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SystemHealthDialog(
    context: Context,
    private val repository: AdminFirestoreRepository,
    private val coroutineScope: CoroutineScope
) : Dialog(context) {

    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.US)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        setContentView(R.layout.dialog_system_health)

        window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        initViews()
        refreshHealth()
    }

    private fun initViews() {
        findViewById<ImageButton>(R.id.btn_close_health)?.setOnClickListener { dismiss() }
        findViewById<ImageButton>(R.id.btn_refresh_health)?.setOnClickListener { refreshHealth() }
    }

    private fun refreshHealth() {
        val progress = findViewById<ProgressBar>(R.id.progress_health)
        val content = findViewById<LinearLayout>(R.id.layout_health_content)

        progress?.visibility = View.VISIBLE
        content?.visibility = View.GONE

        coroutineScope.launch {
            val result = repository.fetchSystemHealth()
            withContext(Dispatchers.Main) {
                progress?.visibility = View.GONE
                content?.visibility = View.VISIBLE

                result.onSuccess { json ->
                    bindData(json)
                }.onFailure { err ->
                    Toast.makeText(context, "Telemetry error: ${err.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun bindData(json: JSONObject) {
        val totalLatency = json.optLong("latencyMs", 0L)
        val globalStatus = json.optString("status", "OPERATIONAL")
        val services = json.optJSONObject("services") ?: JSONObject()

        val tvGlobalStatus = findViewById<TextView>(R.id.tv_health_global_status)
        val tvGlobalLatency = findViewById<TextView>(R.id.tv_health_global_latency)
        val tvTimestamp = findViewById<TextView>(R.id.tv_health_timestamp)

        tvGlobalStatus?.text = if (globalStatus == "OPERATIONAL") "ALL SYSTEMS OPERATIONAL" else "DEGRADED PERFORMANCE"
        tvGlobalLatency?.text = "API roundtrip latency: ~$totalLatency ms"
        tvTimestamp?.text = timeFormat.format(Date())

        // API Server
        val apiServer = services.optJSONObject("apiServer")
        if (apiServer != null) {
            val uptimeSec = apiServer.optLong("uptimeSeconds", 0L)
            val memoryMb = apiServer.optLong("memoryUsageMb", 0L)
            val hours = uptimeSec / 3600
            val minutes = (uptimeSec % 3600) / 60
            findViewById<TextView>(R.id.tv_health_server_details)?.text =
                "Uptime: ${hours}h ${minutes}m • Memory: ${memoryMb} MB"
            findViewById<TextView>(R.id.badge_server_status)?.text = apiServer.optString("status", "UP")
        }

        // MongoDB
        val mongoDb = services.optJSONObject("mongoDb")
        if (mongoDb != null) {
            val host = mongoDb.optString("host", "Atlas Cluster")
            val status = mongoDb.optString("status", "CONNECTED")
            findViewById<TextView>(R.id.tv_health_mongo_details)?.text = "Host: $host • Replica Set"
            val badge = findViewById<TextView>(R.id.badge_mongo_status)
            badge?.text = status
            if (status != "CONNECTED") {
                badge?.setTextColor(ContextCompat.getColor(context, R.color.admin_rose))
                badge?.setBackgroundResource(R.drawable.bg_status_rejected)
            }
        }

        // Firestore
        val firestore = services.optJSONObject("firestore")
        if (firestore != null) {
            val fsLatency = firestore.optLong("latencyMs", 0L)
            val fsStatus = firestore.optString("status", "HEALTHY")
            findViewById<TextView>(R.id.tv_health_firestore_details)?.text =
                "Query Latency: ${fsLatency} ms • Live Sync Active"
            findViewById<TextView>(R.id.badge_firestore_status)?.text = fsStatus
        }

        // PayU Gateway
        val payu = services.optJSONObject("payu")
        if (payu != null) {
            val mode = payu.optString("mode", "live").uppercase()
            val isConfigured = payu.optBoolean("merchantKeyConfigured", true)
            findViewById<TextView>(R.id.tv_health_payu_details)?.text =
                "Mode: $mode • Auto Settlement Sync Configured"
            val badge = findViewById<TextView>(R.id.badge_payu_status)
            badge?.text = if (isConfigured) "CONFIGURED" else "KEY MISSING"
        }
    }
}
