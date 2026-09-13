package com.ourbloom.admin.fcm

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.media.RingtoneManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.ourbloom.admin.R
import com.ourbloom.admin.main.AdminMainActivity

class AdminFirebaseMessagingService : FirebaseMessagingService() {

    companion object {
        private const val TAG = "AdminFCMService"
        const val CHANNEL_ID = "bloom_admin_financial_alerts"
        const val CHANNEL_NAME = "Financial & Vault Alerts"
        const val ADMIN_TOPIC = "admin_financial_alerts"
        const val ADMIN_PHONE = "8822361549"

        fun registerDeviceToken(token: String) {
            if (token.isBlank()) return
            try {
                val db = FirebaseFirestore.getInstance()
                val data = mapOf(
                    "token" to token,
                    "adminPhone" to ADMIN_PHONE,
                    "platform" to "android",
                    "app" to "com.ourbloom.admin",
                    "updatedAt" to System.currentTimeMillis()
                )
                db.collection("admin_devices").document(token).set(data, SetOptions.merge())
                Log.d(TAG, "Admin FCM token registered in Firestore admin_devices")
            } catch (e: Exception) {
                Log.w(TAG, "Error saving admin token: ${e.message}")
            }
        }

        fun ensureTopicSubscription() {
            FirebaseMessaging.getInstance().subscribeToTopic(ADMIN_TOPIC)
                .addOnSuccessListener {
                    Log.d(TAG, "Subscribed successfully to topic: $ADMIN_TOPIC")
                }
                .addOnFailureListener {
                    Log.w(TAG, "Failed to subscribe to topic: ${it.message}")
                }
        }
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "New Admin FCM Token generated: $token")
        registerDeviceToken(token)
        ensureTopicSubscription()
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        Log.d(TAG, "Admin Push Received: data=${remoteMessage.data}, notif=${remoteMessage.notification?.body}")

        val title = remoteMessage.notification?.title
            ?: remoteMessage.data["title"]
            ?: "Bloom Financial Alert 🌸💰"

        val body = remoteMessage.notification?.body
            ?: remoteMessage.data["body"]
            ?: "New financial update in Couple's Vault."

        showAdminNotification(title, body, remoteMessage.data)
    }

    private fun showAdminNotification(title: String, body: String, data: Map<String, String>) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Create high-importance notification channel
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Urgent alerts for deposits, withdrawal requests, and couple vault activity"
                enableLights(true)
                lightColor = Color.GREEN
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 300, 200, 300)
            }
            notificationManager.createNotificationChannel(channel)
        }

        val intent = Intent(this, AdminMainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            data.forEach { (k, v) -> putExtra(k, v) }
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            System.currentTimeMillis().toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_lock)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setColor(0xFF10B981.toInt()) // Admin Emerald
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setSound(soundUri)
            .setVibrate(longArrayOf(0, 300, 200, 300))
            .setContentIntent(pendingIntent)

        val notifId = (System.currentTimeMillis() % 100000).toInt()
        notificationManager.notify(notifId, builder.build())
    }
}
