package com.ourbloom.app.touch

import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.View
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.google.android.material.button.MaterialButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import com.ourbloom.app.R
import com.ourbloom.app.data.FirestoreRepository
import com.ourbloom.app.data.models.User
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ThumbKissActivity : AppCompatActivity() {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val repository = FirestoreRepository()

    private var coupleId: String = ""
    private var myUid: String = ""
    private var partnerUid: String = ""
    private var partnerUser: User? = null

    private lateinit var touchCanvas: ThumbKissCanvasView
    private lateinit var tvPartnerStatus: TextView
    private lateinit var tvConnectionStatus: TextView
    private lateinit var layoutCenterHint: View
    private lateinit var ivPartnerAvatar: ImageView
    private lateinit var viewPartnerPulseRing: View
    private lateinit var btnNudgePartner: MaterialButton

    private var partnerListener: ListenerRegistration? = null
    private var vibrator: Vibrator? = null
    private var isVibratingHeartbeat = false
    private var lastWriteTime = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Keep screen on during live touch session
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setContentView(R.layout.activity_thumb_kiss)

        initVibrator()
        bindViews()
        loadCoupleAndPartner()
    }

    private fun initVibrator() {
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vibratorManager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    private fun bindViews() {
        touchCanvas = findViewById(R.id.touch_canvas)
        tvPartnerStatus = findViewById(R.id.tv_partner_status)
        tvConnectionStatus = findViewById(R.id.tv_connection_status)
        layoutCenterHint = findViewById(R.id.layout_center_hint)
        ivPartnerAvatar = findViewById(R.id.iv_partner_avatar)
        viewPartnerPulseRing = findViewById(R.id.view_partner_pulse_ring)
        btnNudgePartner = findViewById(R.id.btn_nudge_partner)

        findViewById<ImageButton>(R.id.btn_close).setOnClickListener {
            finish()
        }

        btnNudgePartner.setOnClickListener {
            sendNudge()
        }

        touchCanvas.onLocalTouchChanged = { xRatio, yRatio, isTouching ->
            if (isTouching) {
                layoutCenterHint.animate().alpha(0f).setDuration(200).start()
            } else if (!touchCanvas.isPartnerTouching) {
                layoutCenterHint.animate().alpha(1f).setDuration(200).start()
            }
            streamLocalTouch(xRatio, yRatio, isTouching)
        }

        touchCanvas.onKissContactStateChanged = { isInContact ->
            if (isInContact) {
                startHeartbeatVibration()
                tvPartnerStatus.text = "Connected in Love! 💓 Feeling each other's touch"
                tvConnectionStatus.text = "💓 Heartbeat Synced Across Distance 💓"
                viewPartnerPulseRing.visibility = View.VISIBLE
                viewPartnerPulseRing.animate().scaleX(1.5f).scaleY(1.5f).alpha(0f).setDuration(800).withEndAction {
                    viewPartnerPulseRing.scaleX = 1f
                    viewPartnerPulseRing.scaleY = 1f
                    viewPartnerPulseRing.alpha = 1f
                }.start()
            } else {
                stopHeartbeatVibration()
                viewPartnerPulseRing.visibility = View.GONE
                if (touchCanvas.isPartnerTouching) {
                    tvPartnerStatus.text = "Partner is touching the screen! Place your thumb to connect 💓"
                    tvConnectionStatus.text = "Partner Active"
                } else {
                    tvPartnerStatus.text = "Waiting for partner to touch..."
                    tvConnectionStatus.text = "Synchronous Touch Active"
                }
            }
        }
    }

    private fun loadCoupleAndPartner() {
        myUid = auth.currentUser?.uid ?: ""
        if (myUid.isBlank()) {
            finish()
            return
        }

        lifecycleScope.launch {
            val user = repository.getCurrentUser()
            coupleId = user?.coupleId ?: ""
            if (coupleId.isBlank()) {
                Toast.makeText(this@ThumbKissActivity, "No linked partner found", Toast.LENGTH_SHORT).show()
                finish()
                return@launch
            }

            val couple = repository.getCouple(coupleId)
            if (couple != null) {
                partnerUid = if (couple.user1 == myUid) couple.user2 else couple.user1
                if (partnerUid.isNotBlank()) {
                    partnerUser = repository.getUser(partnerUid)
                    val partnerName = partnerUser?.name?.ifBlank { "Your Love" } ?: "Your Love"
                    val avatarUrl = partnerUser?.avatarUrl ?: ""

                    if (avatarUrl.isNotBlank()) {
                        Glide.with(this@ThumbKissActivity)
                            .load(avatarUrl)
                            .circleCrop()
                            .into(ivPartnerAvatar)
                    }

                    tvPartnerStatus.text = "Waiting for $partnerName to touch..."
                    listenToPartnerTouch()
                }
            }
        }
    }

    private fun listenToPartnerTouch() {
        if (coupleId.isBlank() || partnerUid.isBlank()) return

        partnerListener?.remove()
        val docRef = db.collection("couples").document(coupleId)
            .collection("live").document("touch_$partnerUid")

        partnerListener = docRef.addSnapshotListener { snapshot, error ->
            if (error != null || snapshot == null || !snapshot.exists()) {
                touchCanvas.updatePartnerTouch(0f, 0f, false)
                return@addSnapshotListener
            }

            val isTouching = snapshot.getBoolean("touching") ?: false
            val updatedAt = snapshot.getLong("updatedAt") ?: 0L
            val x = (snapshot.getDouble("x") ?: 0.5).toFloat()
            val y = (snapshot.getDouble("y") ?: 0.5).toFloat()

            val isFresh = (System.currentTimeMillis() - updatedAt) < 6000L
            val activeTouching = isTouching && isFresh

            touchCanvas.updatePartnerTouch(x, y, activeTouching)
            if (activeTouching) {
                val partnerName = partnerUser?.name ?: "Partner"
                if (!touchCanvas.isLocalTouching) {
                    tvPartnerStatus.text = "$partnerName is touching the screen! 💓"
                }
            }
        }
    }

    private fun streamLocalTouch(xRatio: Float, yRatio: Float, isTouching: Boolean) {
        if (coupleId.isBlank() || myUid.isBlank()) return

        val now = System.currentTimeMillis()
        if (isTouching && (now - lastWriteTime < 60)) {
            // Throttle rapid moves to ~16 updates/sec to conserve Firestore writes
            return
        }
        lastWriteTime = now

        val data = hashMapOf<String, Any>(
            "x" to xRatio.toDouble(),
            "y" to yRatio.toDouble(),
            "touching" to isTouching,
            "updatedAt" to now
        )

        db.collection("couples").document(coupleId)
            .collection("live").document("touch_$myUid")
            .set(data, SetOptions.merge())
    }

    private var heartbeatJob: Job? = null

    private fun startHeartbeatVibration() {
        if (isVibratingHeartbeat) return
        isVibratingHeartbeat = true

        heartbeatJob?.cancel()
        heartbeatJob = lifecycleScope.launch {
            while (isVibratingHeartbeat) {
                triggerLubDubHaptic()
                delay(950) // Lub-dub cadence ~63 bpm
            }
        }
    }

    private fun triggerLubDubHaptic() {
        val vib = vibrator ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val timings = longArrayOf(0, 75, 110, 110)
            val amplitudes = intArrayOf(0, 190, 0, 255)
            vib.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
        } else {
            @Suppress("DEPRECATION")
            vib.vibrate(120)
        }
    }

    private fun stopHeartbeatVibration() {
        isVibratingHeartbeat = false
        heartbeatJob?.cancel()
        heartbeatJob = null
        vibrator?.cancel()
    }

    private fun sendNudge() {
        btnNudgePartner.isEnabled = false
        lifecycleScope.launch {
            val senderName = repository.getCurrentUser()?.name ?: "Your Love"
            val success = repository.sendHeartbeat(coupleId, "$senderName wants to ThumbKiss 💓", null)
            if (success) {
                Toast.makeText(this@ThumbKissActivity, "Nudged your partner! 💌", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this@ThumbKissActivity, "Sent nudge", Toast.LENGTH_SHORT).show()
            }
            delay(10000)
            btnNudgePartner.isEnabled = true
        }
    }

    override fun onPause() {
        super.onPause()
        stopHeartbeatVibration()
        streamLocalTouch(0f, 0f, false)
    }

    override fun onDestroy() {
        super.onDestroy()
        stopHeartbeatVibration()
        partnerListener?.remove()
        streamLocalTouch(0f, 0f, false)
    }
}
