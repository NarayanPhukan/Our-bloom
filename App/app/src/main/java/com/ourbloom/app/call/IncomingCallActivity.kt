package com.ourbloom.app.call

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.app.KeyguardManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.ourbloom.app.R

class IncomingCallActivity : AppCompatActivity() {

    companion object {
        const val TAG = "IncomingCallActivity"
        const val EXTRA_COUPLE_ID = "extra_couple_id"
        const val EXTRA_CALLER_NAME = "extra_caller_name"
        const val EXTRA_CALLER_AVATAR = "extra_caller_avatar"
        const val EXTRA_CALLER_ID = "extra_caller_id"
        const val EXTRA_OFFER_SDP = "extra_offer_sdp"
        const val CALL_NOTIFICATION_ID = 7777
        private const val RING_TIMEOUT_MS = 45000L // 45 seconds
    }

    private var coupleId: String = ""
    private var callerName: String = "My Love"
    private var callerAvatar: String = ""
    private var callerId: String = ""
    private var offerSdp: String? = null

    private var ringtone: Ringtone? = null
    private var vibrator: Vibrator? = null
    private var callDocListener: ListenerRegistration? = null
    private var isAnswered = false
    private var isFinishing = false

    private val timeoutHandler = Handler(Looper.getMainLooper())
    private val timeoutRunnable = Runnable {
        Log.d(TAG, "Ring timeout reached, dismissing")
        stopRinging()
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Show over lock screen and turn screen on
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
            keyguardManager.requestDismissKeyguard(this, null)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Draw under system bars for full-screen immersive
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        )

        setContentView(R.layout.activity_incoming_call)

        // Extract intent extras
        coupleId = intent.getStringExtra(EXTRA_COUPLE_ID) ?: ""
        callerName = intent.getStringExtra(EXTRA_CALLER_NAME) ?: "My Love"
        callerAvatar = intent.getStringExtra(EXTRA_CALLER_AVATAR) ?: ""
        callerId = intent.getStringExtra(EXTRA_CALLER_ID) ?: ""
        offerSdp = intent.getStringExtra(EXTRA_OFFER_SDP)

        setupUI()
        startPulseAnimation()
        startRinging()
        listenCallStatus()

        // Auto-timeout
        timeoutHandler.postDelayed(timeoutRunnable, RING_TIMEOUT_MS)
    }

    private fun setupUI() {
        val tvName = findViewById<TextView>(R.id.tv_incoming_name)
        val ivAvatar = findViewById<ImageView>(R.id.iv_incoming_avatar)
        val btnAccept = findViewById<ImageButton>(R.id.btn_accept_incoming)
        val btnDecline = findViewById<ImageButton>(R.id.btn_decline_incoming)

        tvName.text = callerName

        if (callerAvatar.isNotBlank()) {
            Glide.with(this)
                .load(callerAvatar)
                .placeholder(R.drawable.ic_favorite)
                .error(R.drawable.ic_favorite)
                .circleCrop()
                .into(ivAvatar)
        }

        btnAccept.setOnClickListener {
            acceptCall()
        }

        btnDecline.setOnClickListener {
            declineCall()
        }
    }

    private fun startPulseAnimation() {
        val pulseView = findViewById<View>(R.id.view_pulse_ring)

        val scaleX = ObjectAnimator.ofFloat(pulseView, "scaleX", 1f, 1.3f, 1f)
        val scaleY = ObjectAnimator.ofFloat(pulseView, "scaleY", 1f, 1.3f, 1f)
        val alpha = ObjectAnimator.ofFloat(pulseView, "alpha", 0.6f, 0.15f, 0.6f)

        val animatorSet = AnimatorSet().apply {
            playTogether(scaleX, scaleY, alpha)
            duration = 2000
            interpolator = AccelerateDecelerateInterpolator()
        }

        // Loop the animation
        animatorSet.addListener(object : android.animation.AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: android.animation.Animator) {
                if (!isFinishing && !isFinishing()) {
                    animatorSet.start()
                }
            }
        })
        animatorSet.start()
    }

    private fun startRinging() {
        // Play ringtone
        try {
            val ringtoneUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            ringtone = RingtoneManager.getRingtone(applicationContext, ringtoneUri)
            ringtone?.let { rt ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    rt.isLooping = true
                }
                val audioAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
                rt.audioAttributes = audioAttributes

                // Set ringer volume to ensure audibility
                val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
                val currentVolume = am.getStreamVolume(AudioManager.STREAM_RING)
                if (currentVolume == 0) {
                    // Device is on silent, respect it
                    Log.d(TAG, "Device ring volume is 0, respecting silent mode")
                } else {
                    rt.play()
                    Log.d(TAG, "Ringtone playing")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error playing ringtone: ${e.message}")
        }

        // Vibrate in a ringing pattern
        try {
            vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vm.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }

            // Phone-style ringing pattern: 0ms wait, 1000ms buzz, 1000ms pause, repeat
            val pattern = longArrayOf(0, 1000, 1000)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(
                    VibrationEffect.createWaveform(pattern, 0) // 0 = repeat from index 0
                )
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(pattern, 0)
            }
            Log.d(TAG, "Vibration started")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting vibration: ${e.message}")
        }
    }

    private fun stopRinging() {
        try {
            ringtone?.stop()
            ringtone = null
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping ringtone: ${e.message}")
        }

        try {
            vibrator?.cancel()
            vibrator = null
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping vibration: ${e.message}")
        }
    }

    private fun listenCallStatus() {
        if (coupleId.isBlank()) return

        callDocListener = FirebaseFirestore.getInstance()
            .collection("video_calls")
            .document(coupleId)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null || !snapshot.exists()) return@addSnapshotListener
                val status = snapshot.getString("status") ?: ""

                when (status) {
                    "ended", "declined" -> {
                        // Caller cancelled or call was declined elsewhere
                        if (!isAnswered && !isFinishing) {
                            Log.d(TAG, "Call $status by remote, dismissing ringing screen")
                            isFinishing = true
                            stopRinging()
                            dismissCallNotification()
                            finish()
                        }
                    }
                    "connected" -> {
                        // Already answered (e.g. from notification action)
                        if (!isAnswered && !isFinishing) {
                            isFinishing = true
                            stopRinging()
                            dismissCallNotification()
                            finish()
                        }
                    }
                }
            }
    }

    private fun acceptCall() {
        if (isAnswered) return
        isAnswered = true
        isFinishing = true

        stopRinging()
        dismissCallNotification()
        timeoutHandler.removeCallbacks(timeoutRunnable)
        callDocListener?.remove()
        callDocListener = null

        // Fetch the latest offer SDP if we don't already have it
        if (offerSdp.isNullOrBlank()) {
            FirebaseFirestore.getInstance()
                .collection("video_calls")
                .document(coupleId)
                .get()
                .addOnSuccessListener { doc ->
                    val offer = doc?.getString("offer") ?: ""
                    launchVideoCall(offer)
                }
                .addOnFailureListener {
                    launchVideoCall("")
                }
        } else {
            launchVideoCall(offerSdp ?: "")
        }
    }

    private fun launchVideoCall(offer: String) {
        val intent = Intent(this, VideoCallActivity::class.java).apply {
            putExtra(VideoCallActivity.EXTRA_COUPLE_ID, coupleId)
            putExtra(VideoCallActivity.EXTRA_PARTNER_NAME, callerName)
            putExtra(VideoCallActivity.EXTRA_PARTNER_AVATAR, callerAvatar)
            putExtra(VideoCallActivity.EXTRA_PARTNER_ID, callerId)
            putExtra(VideoCallActivity.EXTRA_IS_CALLER, false)
            putExtra(VideoCallActivity.EXTRA_OFFER_SDP, offer)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        startActivity(intent)
        finish()
    }

    private fun declineCall() {
        if (isFinishing) return
        isFinishing = true

        stopRinging()
        dismissCallNotification()
        timeoutHandler.removeCallbacks(timeoutRunnable)
        callDocListener?.remove()
        callDocListener = null

        // Write declined status to Firestore
        if (coupleId.isNotBlank()) {
            FirebaseFirestore.getInstance()
                .collection("video_calls")
                .document(coupleId)
                .update("status", "declined", "endedAt", System.currentTimeMillis())
                .addOnCompleteListener { finish() }
        } else {
            finish()
        }
    }

    private fun dismissCallNotification() {
        try {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.cancel(CALL_NOTIFICATION_ID)
        } catch (_: Exception) {}
    }

    override fun onDestroy() {
        super.onDestroy()
        timeoutHandler.removeCallbacks(timeoutRunnable)
        stopRinging()
        callDocListener?.remove()
        callDocListener = null
    }

    override fun onBackPressed() {
        // Prevent accidental back press dismissal — user must tap Decline
    }
}
