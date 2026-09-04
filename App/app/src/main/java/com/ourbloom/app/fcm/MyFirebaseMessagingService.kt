package com.ourbloom.app.fcm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.Person
import androidx.core.app.RemoteInput
import androidx.core.graphics.drawable.IconCompat
import com.google.firebase.auth.FirebaseAuth
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
        val isChat = type == "chat" || 
            remoteMessage.data.containsKey("messageText") || 
            remoteMessage.data.containsKey("audioUrl") || 
            remoteMessage.data.containsKey("imageUrl")

        val title = if (isHeartbeat) {
            val sender = remoteMessage.data["senderName"] ?: "Your Love"
            "$sender sent you a Heartbeat ❤️"
        } else if (isChat) {
            // WhatsApp style: Partner's name is the title
            remoteMessage.data["senderName"] ?: remoteMessage.data["title"] ?: remoteMessage.notification?.title ?: "Your Love"
        } else {
            remoteMessage.data["title"] ?: remoteMessage.notification?.title ?: "OurBloom"
        }

        val body = if (isHeartbeat) {
            "Thinking of you right now... tap to send one back!"
        } else if (isChat) {
            // WhatsApp style: exact message preview
            val messageText = remoteMessage.data["messageText"]
            val imageUrl = remoteMessage.data["imageUrl"]
            val audioUrl = remoteMessage.data["audioUrl"]
            when {
                !messageText.isNullOrBlank() -> messageText
                !imageUrl.isNullOrBlank() -> "📷 Photo"
                !audioUrl.isNullOrBlank() -> "🎙️ Voice message"
                !remoteMessage.data["body"].isNullOrBlank() -> remoteMessage.data["body"]!!
                !remoteMessage.notification?.body.isNullOrBlank() -> remoteMessage.notification?.body!!
                else -> "New message"
            }
        } else {
            remoteMessage.data["body"] ?: remoteMessage.notification?.body ?: "You have a new message!"
        }

        if (isHeartbeat) {
            triggerHeartbeatHaptic()
        }

        val coupleId = remoteMessage.data["coupleId"] ?: ""
        val senderId = remoteMessage.data["senderId"] ?: ""
        val messageId = remoteMessage.data["messageId"] ?: ""

        if (isChat) {
            // Check if notifications for this couple are muted
            if (coupleId.isNotBlank()) {
                val prefs = getSharedPreferences("ourbloom_notif_prefs", Context.MODE_PRIVATE)
                val muteUntil = prefs.getLong("mute_until_${coupleId}", 0L)
                if (System.currentTimeMillis() < muteUntil) {
                    Log.d(TAG, "Chat notifications are muted for couple $coupleId")
                    return
                }
            }

            // If user is actively reading or typing in ChatFragment, skip floating banner to avoid interruption
            if (com.ourbloom.app.chat.ChatFragment.isChatVisible) {
                Log.d(TAG, "User currently in ChatFragment; skipping pop-up notification")
                return
            }

            if (coupleId.isNotBlank()) {
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val repository = FirestoreRepository()
                        if (messageId.isNotBlank()) {
                            repository.markSingleMessageDelivered(messageId)
                        }
                        if (senderId.isNotBlank()) {
                            repository.markMessagesFromSenderDelivered(coupleId, senderId)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error marking messages delivered from FCM", e)
                    }
                }
            }
        }

        sendNotification(
            title = title,
            messageBody = body,
            isHeartbeat = isHeartbeat,
            isChat = isChat,
            coupleId = coupleId,
            senderId = senderId,
            messageId = messageId
        )
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

    private fun sendNotification(
        title: String,
        messageBody: String,
        isHeartbeat: Boolean,
        isChat: Boolean,
        coupleId: String = "",
        senderId: String = "",
        messageId: String = ""
    ) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            if (isHeartbeat) {
                putExtra("action", "heartbeat_received")
            } else if (isChat) {
                putExtra("action", "open_chat")
                putExtra("coupleId", coupleId)
                putExtra("senderId", senderId)
            }
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 
            if (isHeartbeat) 4041 else (if (isChat) 4042 else 0), 
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // WhatsApp-style heads-up channel ID
        val chatChannelId = "ourbloom_chat_heads_up_v3"
        val channelId = when {
            isHeartbeat -> "ourbloom_heartbeat_channel"
            isChat -> chatChannelId
            else -> "ourbloom_fcm_channel"
        }
        val channelName = when {
            isHeartbeat -> "Heartbeat & Thinking of You"
            isChat -> "Couple Chat Messages"
            else -> "Our Bloom Notifications"
        }

        val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val audioAttributes = AudioAttributes.Builder()
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .setUsage(if (isChat) AudioAttributes.USAGE_NOTIFICATION_COMMUNICATION_INSTANT else AudioAttributes.USAGE_NOTIFICATION)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                channelName,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                enableVibration(true)
                vibrationPattern = if (isHeartbeat) longArrayOf(0, 120, 80, 240) else longArrayOf(0, 250, 250, 250)
                setSound(soundUri, audioAttributes)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                setShowBadge(true)
                description = if (isHeartbeat) "Instant tactile heartbeat notifications from your partner" else "WhatsApp-style floating pop-up messages"
            }
            notificationManager.createNotificationChannel(channel)
        }

        val notifId = if (isHeartbeat) 8888 else (System.currentTimeMillis() % 100000).toInt() + 1000

        val notificationBuilder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(messageBody)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setSound(soundUri)
            .setVibrate(if (isHeartbeat) longArrayOf(0, 120, 80, 240) else longArrayOf(0, 250, 250, 250))
            .setContentIntent(pendingIntent)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setCategory(if (isChat) NotificationCompat.CATEGORY_MESSAGE else (if (isHeartbeat) NotificationCompat.CATEGORY_EVENT else NotificationCompat.CATEGORY_STATUS))
            .setWhen(System.currentTimeMillis())
            .setShowWhen(true)
            .setOnlyAlertOnce(false)

        if (isChat) {
            val avatarBitmap = createCircularAvatar(title)
            notificationBuilder.setLargeIcon(avatarBitmap)

            val avatarIcon = IconCompat.createWithBitmap(avatarBitmap)
            val senderPerson = Person.Builder()
                .setName(title)
                .setIcon(avatarIcon)
                .setKey(senderId.ifBlank { title })
                .build()

            val mePerson = Person.Builder()
                .setName("Me")
                .build()

            val messagingStyle = NotificationCompat.MessagingStyle(mePerson)
                .setConversationTitle(null)
                .addMessage(messageBody, System.currentTimeMillis(), senderPerson)
            notificationBuilder.setStyle(messagingStyle)

            val currentUid = FirebaseAuth.getInstance().currentUser?.uid ?: ""

            // 1. WhatsApp Action: Reply (with RemoteInput for inline quick reply)
            val remoteInput = RemoteInput.Builder(NotificationActionReceiver.KEY_TEXT_REPLY)
                .setLabel("Reply")
                .build()

            val replyIntent = Intent(this, NotificationActionReceiver::class.java).apply {
                action = NotificationActionReceiver.ACTION_REPLY
                putExtra("notificationId", notifId)
                putExtra("coupleId", coupleId)
                putExtra("senderId", senderId)
                putExtra("currentUid", currentUid)
            }

            val replyPendingIntent = PendingIntent.getBroadcast(
                this,
                notifId * 10 + 1,
                replyIntent,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                } else {
                    PendingIntent.FLAG_UPDATE_CURRENT
                }
            )

            val replyAction = NotificationCompat.Action.Builder(
                R.drawable.ic_reply,
                "Reply",
                replyPendingIntent
            )
                .addRemoteInput(remoteInput)
                .setAllowGeneratedReplies(true)
                .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_REPLY)
                .setShowsUserInterface(false)
                .build()

            // 2. WhatsApp Action: Mark as read
            val markReadIntent = Intent(this, NotificationActionReceiver::class.java).apply {
                action = NotificationActionReceiver.ACTION_MARK_AS_READ
                putExtra("notificationId", notifId)
                putExtra("coupleId", coupleId)
                putExtra("senderId", senderId)
                putExtra("currentUid", currentUid)
            }

            val markReadPendingIntent = PendingIntent.getBroadcast(
                this,
                notifId * 10 + 2,
                markReadIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            val markReadAction = NotificationCompat.Action.Builder(
                0,
                "Mark as read",
                markReadPendingIntent
            )
                .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_MARK_AS_READ)
                .setShowsUserInterface(false)
                .build()

            // 3. WhatsApp Action: Mute
            val muteIntent = Intent(this, NotificationActionReceiver::class.java).apply {
                action = NotificationActionReceiver.ACTION_MUTE
                putExtra("notificationId", notifId)
                putExtra("coupleId", coupleId)
            }

            val mutePendingIntent = PendingIntent.getBroadcast(
                this,
                notifId * 10 + 3,
                muteIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            val muteAction = NotificationCompat.Action.Builder(
                0,
                "Mute",
                mutePendingIntent
            )
                .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_MUTE)
                .setShowsUserInterface(false)
                .build()

            notificationBuilder.addAction(replyAction)
            notificationBuilder.addAction(markReadAction)
            notificationBuilder.addAction(muteAction)
        }

        notificationManager.notify(notifId, notificationBuilder.build())
    }

    private fun createCircularAvatar(name: String): Bitmap {
        val size = 160
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val colors = intArrayOf(
            Color.parseColor("#25D366"), // WhatsApp Green
            Color.parseColor("#128C7E"), // Teal Green
            Color.parseColor("#FF5983"), // Bloom Rose
            Color.parseColor("#7C4DFF"), // Purple
            Color.parseColor("#00B0FF")  // Light Blue
        )
        val colorIndex = kotlin.math.abs(name.hashCode()) % colors.size
        val bgColor = colors[colorIndex]

        val circlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = bgColor
            style = Paint.Style.FILL
        }
        val radius = size / 2f
        canvas.drawCircle(radius, radius, radius, circlePaint)

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 68f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }

        val initial = name.trim().take(1).uppercase().ifEmpty { "❤" }
        val yPos = (radius - ((textPaint.descent() + textPaint.ascent()) / 2f))
        canvas.drawText(initial, radius, yPos, textPaint)

        return bitmap
    }

    companion object {
        private const val TAG = "FCMService"
    }
}
