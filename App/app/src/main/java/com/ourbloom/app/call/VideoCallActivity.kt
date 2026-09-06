package com.ourbloom.app.call

import android.Manifest
import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaActionSound
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.MediaStore
import android.util.Base64
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.webkit.JavascriptInterface
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.google.android.material.card.MaterialCardView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import com.ourbloom.app.R
import com.ourbloom.app.data.FirestoreRepository
import com.ourbloom.app.data.models.Milestone
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.tasks.await
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class VideoCallActivity : AppCompatActivity() {

    companion object {
        const val TAG = "VideoCallActivity"
        const val EXTRA_COUPLE_ID = "extra_couple_id"
        const val EXTRA_PARTNER_NAME = "extra_partner_name"
        const val EXTRA_PARTNER_AVATAR = "extra_partner_avatar"
        const val EXTRA_PARTNER_ID = "extra_partner_id"
        const val EXTRA_IS_CALLER = "extra_is_caller"
        const val EXTRA_OFFER_SDP = "extra_offer_sdp"
    }

    private var coupleId: String = ""
    private var partnerName: String = "My Love"
    private var partnerAvatar: String = ""
    private var partnerId: String = ""
    private var isCaller: Boolean = true
    private var initialOfferSdp: String? = null

    private lateinit var webView: WebView
    private lateinit var layoutCallingOverlay: LinearLayout
    private lateinit var ivCallingAvatar: ImageView
    private lateinit var tvCallingPartnerName: TextView
    private lateinit var tvCallingStatus: TextView
    private lateinit var pbCalling: ProgressBar
    private lateinit var viewShutterFlash: View
    private lateinit var tvCallPartnerName: TextView
    private lateinit var tvCallTimer: TextView
    private lateinit var btnTopBack: ImageButton
    private lateinit var btnCallSpeaker: ImageButton
    private lateinit var btnCallMuteMic: ImageButton
    private lateinit var btnCallSwitchCam: ImageButton
    private lateinit var btnCaptureMoment: ImageButton
    private lateinit var btnCallEnd: ImageButton
    private lateinit var cardMomentSaved: MaterialCardView
    private lateinit var tvMomentBannerText: TextView

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val repository = FirestoreRepository()
    private var callDocListener: ListenerRegistration? = null

    private var audioManager: AudioManager? = null
    private var isSpeakerOn: Boolean = true
    private var isMicMuted: Boolean = false
    private var isCameraReady: Boolean = false
    private var isCallConnected: Boolean = false

    private var callDurationSeconds: Long = 0
    private val timerHandler = Handler(Looper.getMainLooper())
    private var timerRunnable: Runnable? = null

    private var mediaActionSound: MediaActionSound? = null
    private var lastCapturedBytes: ByteArray? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Keep screen on and draw under system bars
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        )

        setContentView(R.layout.activity_video_call)

        coupleId = intent.getStringExtra(EXTRA_COUPLE_ID) ?: ""
        partnerName = intent.getStringExtra(EXTRA_PARTNER_NAME) ?: "My Love"
        partnerAvatar = intent.getStringExtra(EXTRA_PARTNER_AVATAR) ?: ""
        partnerId = intent.getStringExtra(EXTRA_PARTNER_ID) ?: ""
        isCaller = intent.getBooleanExtra(EXTRA_IS_CALLER, true)
        initialOfferSdp = intent.getStringExtra(EXTRA_OFFER_SDP)

        initViews()
        setupAudio()
        setupMediaSound()
        setupWebView()

        if (coupleId.isBlank()) {
            Toast.makeText(this, "Couple connection not found", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        listenCallSignaling()
    }

    private fun initViews() {
        webView = findViewById(R.id.web_view_call)
        layoutCallingOverlay = findViewById(R.id.layout_calling_overlay)
        ivCallingAvatar = findViewById(R.id.iv_calling_avatar)
        tvCallingPartnerName = findViewById(R.id.tv_calling_partner_name)
        tvCallingStatus = findViewById(R.id.tv_calling_status)
        pbCalling = findViewById(R.id.pb_calling)
        viewShutterFlash = findViewById(R.id.view_shutter_flash)
        tvCallPartnerName = findViewById(R.id.tv_call_partner_name)
        tvCallTimer = findViewById(R.id.tv_call_timer)
        btnTopBack = findViewById(R.id.btn_top_back)
        btnCallSpeaker = findViewById(R.id.btn_call_speaker)
        btnCallMuteMic = findViewById(R.id.btn_call_mute_mic)
        btnCallSwitchCam = findViewById(R.id.btn_call_switch_cam)
        btnCaptureMoment = findViewById(R.id.btn_capture_moment)
        btnCallEnd = findViewById(R.id.btn_call_end)
        cardMomentSaved = findViewById(R.id.card_moment_saved)
        tvMomentBannerText = findViewById(R.id.tv_moment_banner_text)

        tvCallingPartnerName.text = partnerName
        tvCallPartnerName.text = partnerName
        tvCallingStatus.text = if (isCaller) "Calling $partnerName..." else "Connecting..."
        tvCallTimer.text = if (isCaller) "Calling..." else "Connecting..."

        if (partnerAvatar.isNotBlank()) {
            Glide.with(this)
                .load(partnerAvatar)
                .placeholder(R.drawable.ic_favorite)
                .error(R.drawable.ic_favorite)
                .circleCrop()
                .into(ivCallingAvatar)
        }

        btnTopBack.setOnClickListener {
            // Minimize or back
            onBackPressedDispatcher.onBackPressed()
        }

        btnCallSpeaker.setOnClickListener {
            toggleSpeaker()
        }

        btnCallMuteMic.setOnClickListener {
            toggleMic()
        }

        btnCallSwitchCam.setOnClickListener {
            switchCamera()
        }

        btnCaptureMoment.setOnClickListener {
            triggerCaptureMoment()
        }

        btnCallEnd.setOnClickListener {
            endCallAndFinish("User ended call")
        }

        cardMomentSaved.setOnClickListener {
            promptSaveToMemories()
        }
    }

    private fun setupAudio() {
        try {
            audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            audioManager?.mode = AudioManager.MODE_IN_COMMUNICATION
            audioManager?.isSpeakerphoneOn = true
            isSpeakerOn = true
            btnCallSpeaker.alpha = 1.0f
        } catch (e: Exception) {
            Log.e(TAG, "Error setting up audio manager: ${e.message}")
        }
    }

    private fun toggleSpeaker() {
        val am = audioManager ?: return
        isSpeakerOn = !isSpeakerOn
        am.isSpeakerphoneOn = isSpeakerOn
        btnCallSpeaker.alpha = if (isSpeakerOn) 1.0f else 0.5f
        Toast.makeText(this, if (isSpeakerOn) "Speaker On" else "Earpiece Audio", Toast.LENGTH_SHORT).show()
    }

    private fun toggleMic() {
        isMicMuted = !isMicMuted
        val script = "setMicMuted($isMicMuted)"
        webView.evaluateJavascript(script, null)
        btnCallMuteMic.setImageResource(if (isMicMuted) R.drawable.ic_mic_off else R.drawable.ic_mic_whatsapp)
        btnCallMuteMic.alpha = if (isMicMuted) 0.6f else 1.0f
        Toast.makeText(this, if (isMicMuted) "Microphone Muted" else "Microphone Unmuted", Toast.LENGTH_SHORT).show()
    }

    private fun switchCamera() {
        webView.evaluateJavascript("switchCamera()", null)
        triggerHaptic(30)
    }

    private fun setupMediaSound() {
        try {
            mediaActionSound = MediaActionSound()
            mediaActionSound?.load(MediaActionSound.SHUTTER_CLICK)
        } catch (e: Exception) {
            Log.w(TAG, "MediaActionSound not available: ${e.message}")
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null)
        val settings = webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.mediaPlaybackRequiresUserGesture = false
        settings.allowFileAccess = true
        settings.useWideViewPort = true
        settings.loadWithOverviewMode = true
        settings.cacheMode = WebSettings.LOAD_NO_CACHE

        webView.webChromeClient = object : WebChromeClient() {
            override fun onPermissionRequest(request: PermissionRequest) {
                // Grant camera and microphone access to the WebRTC WebView
                runOnUiThread {
                    request.grant(request.resources)
                }
            }
        }

        webView.addJavascriptInterface(CallBridge(), "AndroidCallBridge")
        webView.loadUrl("file:///android_asset/webrtc_call.html")
    }

    // ==========================================
    // CAPTURE MOMENT 📸
    // ==========================================
    private fun triggerCaptureMoment() {
        // Visual shutter flash effect
        viewShutterFlash.alpha = 0.85f
        viewShutterFlash.animate()
            .alpha(0f)
            .setDuration(220)
            .start()

        // Haptic feedback
        triggerHaptic(50)

        // Shutter click sound
        try {
            mediaActionSound?.play(MediaActionSound.SHUTTER_CLICK)
        } catch (e: Exception) {
            Log.w(TAG, "Sound play error: ${e.message}")
        }

        // Request frame snapshot from WebView canvas
        webView.evaluateJavascript("captureMoment()", null)
    }

    private fun handleCapturedMoment(base64Jpeg: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val imageBytes = Base64.decode(base64Jpeg, Base64.DEFAULT)
                lastCapturedBytes = imageBytes

                // Save to device public Pictures/OurBloom/Moments gallery
                val filename = "OurBloom_Moment_${System.currentTimeMillis()}.jpg"
                val resolver = contentResolver
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                    put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        put(
                            MediaStore.MediaColumns.RELATIVE_PATH,
                            Environment.DIRECTORY_PICTURES + File.separator + "OurBloom" + File.separator + "Moments"
                        )
                        put(MediaStore.MediaColumns.IS_PENDING, 1)
                    }
                }

                val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                if (uri != null) {
                    resolver.openOutputStream(uri)?.use { out ->
                        out.write(imageBytes)
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        contentValues.clear()
                        contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                        resolver.update(uri, contentValues, null, null)
                    }
                }

                withContext(Dispatchers.Main) {
                    showMomentBanner()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error saving captured moment: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@VideoCallActivity, "Failed to save moment: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun showMomentBanner() {
        cardMomentSaved.alpha = 0f
        cardMomentSaved.visibility = View.VISIBLE
        tvMomentBannerText.text = "Moment saved to Gallery! 📸 Tap to add to Memories"
        cardMomentSaved.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(250)
            .start()

        // Auto hide after 5 seconds
        Handler(Looper.getMainLooper()).postDelayed({
            if (!isFinishing && !isDestroyed) {
                cardMomentSaved.animate()
                    .alpha(0f)
                    .setDuration(300)
                    .withEndAction { cardMomentSaved.visibility = View.GONE }
                    .start()
            }
        }, 5000)
    }

    private fun promptSaveToMemories() {
        val bytes = lastCapturedBytes ?: return
        cardMomentSaved.visibility = View.GONE
        Toast.makeText(this, "Saving moment to OurBloom Memories... 🌸", Toast.LENGTH_SHORT).show()

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val uploadedUrl = repository.uploadImageBytes(bytes, "moment_${System.currentTimeMillis()}.jpg")
                if (!uploadedUrl.isNullOrBlank()) {
                    val dateFormatted = SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date())
                    val milestone = Milestone(
                        coupleId = coupleId,
                        day = 0,
                        label = dateFormatted,
                        title = "Video Call Moment 📸",
                        body = "A sweet moment captured during our video call ❤️",
                        imageUrl = uploadedUrl,
                        icon = "videocam",
                        iconFill = true,
                        colorScheme = "tertiary"
                    )
                    repository.addMilestone(milestone)
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@VideoCallActivity, "Added to Memories! ✨", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed uploading milestone moment: ${e.message}")
            }
        }
    }

    // ==========================================
    // FIRESTORE SIGNALING
    // ==========================================
    private fun listenCallSignaling() {
        val callDoc = db.collection("video_calls").document(coupleId)
        val currentUid = auth.currentUser?.uid ?: ""

        callDocListener = callDoc.addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.e(TAG, "Firestore call listener error: ${error.message}")
                return@addSnapshotListener
            }
            if (snapshot == null || !snapshot.exists()) return@addSnapshotListener

            val status = snapshot.getString("status") ?: ""
            Log.d(TAG, "Call doc update: status=$status, isCaller=$isCaller")

            if (status == "declined") {
                runOnUiThread {
                    Toast.makeText(this, "$partnerName declined the call", Toast.LENGTH_LONG).show()
                    endCallAndFinish("Call declined")
                }
                return@addSnapshotListener
            }

            if (status == "ended") {
                runOnUiThread {
                    Toast.makeText(this, "Call ended", Toast.LENGTH_SHORT).show()
                    endCallAndFinish("Call ended by remote")
                }
                return@addSnapshotListener
            }

            // Caller side: wait for answer
            if (isCaller) {
                val answerJson = snapshot.getString("answer")
                if (!answerJson.isNullOrBlank() && !isCallConnected) {
                    val encoded = Uri.encode(answerJson)
                    webView.evaluateJavascript("handleAnswer('$encoded')", null)
                }

                // Process ICE candidates from receiver
                val receiverCandidates = snapshot.get("receiverCandidates") as? List<*>
                receiverCandidates?.forEach { cand ->
                    if (cand is String) {
                        val encoded = Uri.encode(cand)
                        webView.evaluateJavascript("handleCandidate('$encoded')", null)
                    } else if (cand is Map<*, *>) {
                        val json = JSONObject(cand as Map<String, Any?>).toString()
                        val encoded = Uri.encode(json)
                        webView.evaluateJavascript("handleCandidate('$encoded')", null)
                    }
                }
            } else {
                // Receiver side: process ICE candidates from caller
                val callerCandidates = snapshot.get("callerCandidates") as? List<*>
                callerCandidates?.forEach { cand ->
                    if (cand is String) {
                        val encoded = Uri.encode(cand)
                        webView.evaluateJavascript("handleCandidate('$encoded')", null)
                    } else if (cand is Map<*, *>) {
                        val json = JSONObject(cand as Map<String, Any?>).toString()
                        val encoded = Uri.encode(json)
                        webView.evaluateJavascript("handleCandidate('$encoded')", null)
                    }
                }
            }
        }
    }

    private fun startCallOfferFlow() {
        Log.d(TAG, "Starting call offer flow...")
        webView.evaluateJavascript("startCall()", null)
    }

    private fun handleReceiverOfferFlow() {
        Log.d(TAG, "Starting receiver offer flow...")
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val snapshot = db.collection("video_calls").document(coupleId).get().await()
                val offerJson = initialOfferSdp ?: snapshot?.getString("offer")
                if (!offerJson.isNullOrBlank()) {
                    withContext(Dispatchers.Main) {
                        val encoded = Uri.encode(offerJson)
                        webView.evaluateJavascript("handleOffer('$encoded')", null)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching offer for receiver: ${e.message}")
            }
        }
    }

    private fun startTimer() {
        if (timerRunnable != null) return
        callDurationSeconds = 0
        timerRunnable = object : Runnable {
            override fun run() {
                callDurationSeconds++
                val minutes = callDurationSeconds / 60
                val seconds = callDurationSeconds % 60
                tvCallTimer.text = String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
                timerHandler.postDelayed(this, 1000)
            }
        }
        timerHandler.postDelayed(timerRunnable!!, 1000)
    }

    private fun stopTimer() {
        timerRunnable?.let { timerHandler.removeCallbacks(it) }
        timerRunnable = null
    }

    private fun triggerHaptic(durationMs: Long) {
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vm.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(durationMs)
            }
        } catch (_: Exception) {}
    }

    private fun endCallAndFinish(reason: String) {
        Log.d(TAG, "Ending call: $reason")
        stopTimer()

        // Post chat record if call was connected
        if (isCallConnected && callDurationSeconds > 0) {
            val mins = callDurationSeconds / 60
            val secs = callDurationSeconds % 60
            val durationText = if (mins > 0) "${mins}m ${secs}s" else "${secs}s"
            val text = "📹 Video call • $durationText"
            val currentUid = auth.currentUser?.uid ?: ""
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    repository.sendChatMessage(
                        coupleId = coupleId,
                        text = text,
                        senderName = if (isCaller) "You" else partnerName
                    )
                } catch (e: Exception) {
                    Log.w(TAG, "Error posting call duration message: ${e.message}")
                }
            }
        }

        // Clean up Firestore state
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                db.collection("video_calls").document(coupleId)
                    .update("status", "ended", "endedAt", System.currentTimeMillis())
            } catch (_: Exception) {}
        }

        webView.evaluateJavascript("endCall()", null)
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopTimer()
        callDocListener?.remove()
        callDocListener = null

        audioManager?.mode = AudioManager.MODE_NORMAL
        audioManager?.isSpeakerphoneOn = false

        mediaActionSound?.release()
        mediaActionSound = null

        try {
            webView.evaluateJavascript("endCall()", null)
            webView.destroy()
        } catch (_: Exception) {}
    }

    // ==========================================
    // JAVASCRIPT BRIDGE
    // ==========================================
    inner class CallBridge {

        @JavascriptInterface
        fun onCameraReady() {
            runOnUiThread {
                isCameraReady = true
                if (isCaller) {
                    startCallOfferFlow()
                } else {
                    handleReceiverOfferFlow()
                }
            }
        }

        @JavascriptInterface
        fun onLocalDescription(type: String, sdpJson: String) {
            val currentUid = auth.currentUser?.uid ?: ""
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    if (type == "offer") {
                        val callData = hashMapOf(
                            "callerId" to currentUid,
                            "callerName" to (auth.currentUser?.displayName ?: "Your Partner"),
                            "receiverId" to partnerId,
                            "offer" to sdpJson,
                            "status" to "calling",
                            "timestamp" to System.currentTimeMillis(),
                            "callerCandidates" to emptyList<String>(),
                            "receiverCandidates" to emptyList<String>()
                        )
                        db.collection("video_calls").document(coupleId)
                            .set(callData, SetOptions.merge())
                    } else if (type == "answer") {
                        db.collection("video_calls").document(coupleId)
                            .update(
                                "answer", sdpJson,
                                "status", "connected",
                                "connectedAt", System.currentTimeMillis()
                            )
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error saving local description ($type): ${e.message}")
                }
            }
        }

        @JavascriptInterface
        fun onIceCandidate(candidateJson: String) {
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    val field = if (isCaller) "callerCandidates" else "receiverCandidates"
                    db.collection("video_calls").document(coupleId)
                        .update(field, FieldValue.arrayUnion(candidateJson))
                } catch (e: Exception) {
                    Log.w(TAG, "Error writing ICE candidate: ${e.message}")
                }
            }
        }

        @JavascriptInterface
        fun onCallConnected() {
            runOnUiThread {
                if (!isCallConnected) {
                    isCallConnected = true
                    layoutCallingOverlay.animate()
                        .alpha(0f)
                        .setDuration(400)
                        .withEndAction { layoutCallingOverlay.visibility = View.GONE }
                        .start()

                    startTimer()
                    triggerHaptic(60)
                    Toast.makeText(this@VideoCallActivity, "Video call connected 💕", Toast.LENGTH_SHORT).show()
                }
            }
        }

        @JavascriptInterface
        fun onCallDisconnected(reason: String) {
            runOnUiThread {
                Log.d(TAG, "onCallDisconnected: $reason")
                if (isCallConnected) {
                    Toast.makeText(this@VideoCallActivity, "Call disconnected ($reason)", Toast.LENGTH_SHORT).show()
                    endCallAndFinish(reason)
                }
            }
        }

        @JavascriptInterface
        fun onMomentCaptured(base64Data: String) {
            handleCapturedMoment(base64Data)
        }

        @JavascriptInterface
        fun onError(msg: String) {
            runOnUiThread {
                Log.e(TAG, "WebRTC JS Error: $msg")
                Toast.makeText(this@VideoCallActivity, msg, Toast.LENGTH_SHORT).show()
            }
        }
    }
}
