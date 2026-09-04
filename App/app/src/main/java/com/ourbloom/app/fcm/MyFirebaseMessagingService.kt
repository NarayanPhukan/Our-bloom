package com.ourbloom.app.fcm

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.ourbloom.app.MainActivity
import com.ourbloom.app.R
import com.ourbloom.app.data.FirestoreRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MyFirebaseMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "Refreshed token: $token")
        
        CoroutineScope(Dispatchers.IO).launch {
            val repository = FirestoreRepository()
            repository.updateFcmToken(token)
        }
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)

        val type = remoteMessage.data["type"]
        val isHeartbeat = type == "heartbeat"
        val isChat = type == "chat"

        val title = remoteMessage.notification?.title 
            ?: (if (isHeartbeat) {
                val sender = remoteMessage.data["senderName"] ?: "Your Love"
                "$sender sent you a Heartbeat ❤️"
            } else if (isChat) {
                val sender = remoteMessage.data["senderName"] ?: "Your Love"
                "$sender 💬"
            } else {
                "OurBloom"
            })

        val body = remoteMessage.notification?.body 
            ?: (if (isHeartbeat) {
                "Thinking of you right now... tap to send one back!"
            } else if (isChat) {
                "Sent you a new message"
            } else {
                "You have a new message!"
            })

        if (isHeartbeat) {
            triggerHeartbeatHaptic()
        }

        if (isChat) {
            val coupleId = remoteMessage.data["coupleId"]
            val senderId = remoteMessage.data["senderId"]
            val messageId = remoteMessage.data["messageId"]
            if (!coupleId.isNullOrBlank()) {
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val repository = FirestoreRepository()
                        if (!messageId.isNullOrBlank()) {
                            repository.markSingleMessageDelivered(messageId)
                        }
                        if (!senderId.isNullOrBlank()) {
                            repository.markMessagesFromSenderDelivered(coupleId, senderId)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error marking messages delivered from FCM", e)
                    }
                }
            }
        }

        sendNotification(title, body, isHeartbeat, isChat)
    }

    private fun triggerHeartbeatHaptic() {
        try {
            val pattern = longArrayOf(0, 120, 80, 240)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                val vibrator = vibratorManager?.defaultVibrator
                val effect = VibrationEffect.createWaveform(pattern, -1)
                vibrator?.vibrate(effect)
            } else {
                @Suppress("DEPRECATION")
                val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator?.vibrate(VibrationEffect.createWaveform(pattern, -1))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator?.vibrate(pattern, -1)
                }
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to trigger haptic: ${e.message}")
        }
    }

    private fun sendNotification(title: String, messageBody: String, isHeartbeat: Boolean, isChat: Boolean) {
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            if (isHeartbeat) {
                putExtra("action", "heartbeat_received")
            } else if (isChat) {
                putExtra("action", "open_chat")
            }
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 
            if (isHeartbeat) 4041 else (if (isChat) 4042 else 0), 
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val channelId = when {
            isHeartbeat -> "ourbloom_heartbeat_channel"
            isChat -> "ourbloom_chat_channel"
            else -> "ourbloom_fcm_channel"
        }
        val channelName = when {
            isHeartbeat -> "Heartbeat & Thinking of You"
            isChat -> "Couple Chat Messages"
            else -> "Our Bloom Notifications"
        }

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                channelName,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                enableVibration(true)
                vibrationPattern = if (isHeartbeat) longArrayOf(0, 120, 80, 240) else longArrayOf(0, 250, 250, 250)
                description = if (isHeartbeat) "Instant tactile heartbeat notifications from your partner" else "Standard updates"
            }
            notificationManager.createNotificationChannel(channel)
        }

        val notificationBuilder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(messageBody)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVibrate(if (isHeartbeat) longArrayOf(0, 120, 80, 240) else longArrayOf(0, 250, 250, 250))
            .setContentIntent(pendingIntent)

        val notifId = if (isHeartbeat) 8888 else System.currentTimeMillis().toInt()
        notificationManager.notify(notifId, notificationBuilder.build())
    }

    companion object {
        private const val TAG = "FCMService"
    }
}
