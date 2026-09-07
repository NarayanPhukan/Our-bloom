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
        val isVideoCall = type == "video_call"
        val isUpdate = type == "app_update" || type == "update" ||
            remoteMessage.data.containsKey("versionCode")
        val isChat = type == "chat" || 
            remoteMessage.data.containsKey("messageText") || 
            remoteMessage.data.containsKey("audioUrl") || 
            remoteMessage.data.containsKey("imageUrl")

        if (isUpdate) {
            val payloadCode = remoteMessage.data["versionCode"]?.toIntOrNull()
            val currentCode = com.ourbloom.app.updates.AppUpdateHelper.getCurrentVersionCode(this@MyFirebaseMessagingService)

            if (payloadCode != null) {
                if (payloadCode > currentCode) {
                    val versionName = remoteMessage.data["versionName"] ?: "Latest"
                    val title = remoteMessage.data["title"]
                        ?: remoteMessage.notification?.title
                        ?: "New Bloom Update Available! 🌸"
                    val changelog = remoteMessage.data["changelog"]
                        ?: "• Performance improvements and bug fixes"
                    val apkUrl = remoteMessage.data["apkUrl"]
                        ?: "https://raw.githubusercontent.com/NarayanPhukan/Our-bloom/main/client/public/OurBloom.apk"
                    val forceUpdate = remoteMessage.data["forceUpdate"]?.toBoolean() ?: false

                    val updateInfo = com.ourbloom.app.updates.AppUpdateHelper.UpdateInfo(
                        versionCode = payloadCode,
                        versionName = versionName,
                        title = title,
                        changelog = changelog,
                        apkUrl = apkUrl,
                        forceUpdate = forceUpdate
                    )
                    Log.d(TAG, "Instant FCM update notification for v$versionName (code $payloadCode > current $currentCode)")
                    com.ourbloom.app.updates.AppUpdateHelper.showUpdateNotification(this@MyFirebaseMessagingService, updateInfo)
                } else {
                    Log.d(TAG, "Ignoring FCM update push: payloadCode $payloadCode <= currentCode $currentCode")
                }
                return
            }

            // Fallback to network manifest fetch if payload did not include versionCode
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val client = okhttp3.OkHttpClient.Builder()
                        .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                        .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                        .build()
                    val updateInfo = com.ourbloom.app.updates.AppUpdateHelper.fetchUpdateManifestSync(client)
                    if (updateInfo != null) {
                        if (updateInfo.versionCode > currentCode) {
                            com.ourbloom.app.updates.AppUpdateHelper.showUpdateNotification(this@MyFirebaseMessagingService, updateInfo)
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error handling FCM update message", e)
                }
            }
            return
        }

        val title = if (isHeartbeat) {
            val sender = remoteMessage.data["senderName"] ?: "Your Love"
            "$sender sent you a Heartbeat ❤️"
        } else if (isVideoCall) {
            remoteMessage.data["callerName"] ?: "Your Love"
        } else if (isChat) {
            // WhatsApp style: Partner's name is the title
            remoteMessage.data["senderName"] ?: remoteMessage.data["title"] ?: remoteMessage.notification?.title ?: "Your Love"
        } else {
            remoteMessage.data["title"] ?: remoteMessage.notification?.title ?: "OurBloom"
        }

        val body = if (isHeartbeat) {
            "Thinking of you right now... tap to send one back!"
        } else if (isVideoCall) {
            "Incoming Video Call 📹"
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
            // 1. ALWAYS mark delivered FIRST!
            // When FCM reaches this device, the message has officially reached the partner's physical phone.
            // Hold the FCM wakelock with runBlocking + timeout to guarantee Firestore commits the status.
            if (messageId.isNotBlank()) {
                kotlinx.coroutines.runBlocking {
                    kotlinx.coroutines.withTimeoutOrNull(4000L) {
                        try {
                            FirestoreRepository().markSingleMessageDelivered(messageId)
                        } catch (e: Exception) {
                            Log.e(TAG, "Error marking single message $messageId delivered from FCM", e)
                        }
                    }
                }
            } else if (coupleId.isNotBlank() && senderId.isNotBlank()) {
                kotlinx.coroutines.runBlocking {
                    kotlinx.coroutines.withTimeoutOrNull(4000L) {
                        try {
                            FirestoreRepository().markRecentMessagesDelivered(coupleId, senderId)
                        } catch (e: Exception) {
                            Log.e(TAG, "Error marking recent messages delivered from FCM", e)
                        }
                    }
                }
            }

            // 2. Check if notifications for this couple are muted
            if (coupleId.isNotBlank()) {
                val prefs = getSharedPreferences("ourbloom_notif_prefs", Context.MODE_PRIVATE)
                val muteUntil = prefs.getLong("mute_until_${coupleId}", 0L)
                if (System.currentTimeMillis() < muteUntil) {
                    Log.d(TAG, "Chat notifications are muted for couple $coupleId")
                    return
                }
            }

            // 3. If user is actively reading or typing in ChatFragment, skip floating banner to avoid interruption
            if (com.ourbloom.app.chat.ChatFragment.isChatVisible) {
                Log.d(TAG, "User currently in ChatFragment; skipping pop-up notification")
                return
            }
        }

        if (isVideoCall) {
            val callerAvatar = remoteMessage.data["callerAvatar"] ?: ""
            val callerId = remoteMessage.data["callerId"] ?: ""
            sendCallNotification(
                callerName = title,
                coupleId = coupleId,
                callerId = callerId,
                callerAvatar = callerAvatar
            )
            try {
                val incomingCallIntent = Intent(this, com.ourbloom.app.call.IncomingCallActivity::class.java).apply {
                    putExtra(com.ourbloom.app.call.IncomingCallActivity.EXTRA_COUPLE_ID, coupleId)
                    putExtra(com.ourbloom.app.call.IncomingCallActivity.EXTRA_CALLER_NAME, title)
                    putExtra(com.ourbloom.app.call.IncomingCallActivity.EXTRA_CALLER_AVATAR, callerAvatar)
                    putExtra(com.ourbloom.app.call.IncomingCallActivity.EXTRA_CALLER_ID, callerId)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
                startActivity(incomingCallIntent)
            } catch (e: Exception) {
                Log.d(TAG, "Direct launch from background restricted; fullScreenIntent will handle: ${e.message}")
            }
            return
        }

        val msgTimestamp = remoteMessage.sentTime.takeIf { it > 0 } ?: System.currentTimeMillis()
        sendNotification(
            title = title,
            messageBody = body,
            isHeartbeat = isHeartbeat,
            isChat = isChat,
            coupleId = coupleId,
            senderId = senderId,
            messageId = messageId,
            timestamp = msgTimestamp
        )
    }

    private fun sendCallNotification(
        callerName: String,
        coupleId: String,
        callerId: String,
        callerAvatar: String
    ) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "ourbloom_call_channel"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ringtoneUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            val audioAttributes = AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                .build()

            val channel = NotificationChannel(
                channelId,
                "Video Calls",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Incoming video call alerts"
                enableLights(true)
                lightColor = Color.parseColor("#FF4D6D")
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 1000, 1000, 1000)
                setSound(ringtoneUri, audioAttributes)
            }
            notificationManager.createNotificationChannel(channel)
        }

        // Full-screen intent → IncomingCallActivity (ringing screen with ringtone + vibration)
        val incomingCallIntent = Intent(this, com.ourbloom.app.call.IncomingCallActivity::class.java).apply {
            putExtra(com.ourbloom.app.call.IncomingCallActivity.EXTRA_COUPLE_ID, coupleId)
            putExtra(com.ourbloom.app.call.IncomingCallActivity.EXTRA_CALLER_NAME, callerName)
            putExtra(com.ourbloom.app.call.IncomingCallActivity.EXTRA_CALLER_AVATAR, callerAvatar)
            putExtra(com.ourbloom.app.call.IncomingCallActivity.EXTRA_CALLER_ID, callerId)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val fullScreenPendingIntent = PendingIntent.getActivity(
            this,
            9999,
            incomingCallIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val acceptAction = NotificationCompat.Action.Builder(
            R.drawable.ic_videocam,
            "Accept",
            fullScreenPendingIntent
        ).build()

        // Decline action: writes "declined" to Firestore via a broadcast receiver
        val declineIntent = Intent(this, com.ourbloom.app.fcm.CallActionReceiver::class.java).apply {
            action = "com.ourbloom.app.ACTION_DECLINE_CALL"
            putExtra("coupleId", coupleId)
        }
        val declinePendingIntent = PendingIntent.getBroadcast(
            this,
            9998,
            declineIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val declineAction = NotificationCompat.Action.Builder(
            R.drawable.ic_call_end,
            "Decline",
            declinePendingIntent
        ).build()

        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_videocam)
            .setContentTitle(callerName)
            .setContentText("Incoming Video Call 📹")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setAutoCancel(true)
            .setContentIntent(fullScreenPendingIntent)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .addAction(acceptAction)
            .addAction(declineAction)
            .setColor(Color.parseColor("#FF4D6D"))
            .setOngoing(true)
            .setTimeoutAfter(45000) // Auto-dismiss after 45 seconds
            .build()

        notificationManager.notify(7777, notification)
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
        messageId: String = "",
        timestamp: Long = System.currentTimeMillis()
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

        val notifId = when {
            isHeartbeat -> 8888
            isChat -> if (coupleId.isNotBlank()) kotlin.math.abs(coupleId.hashCode()) % 50000 + 10000 else 4042
            else -> (System.currentTimeMillis() % 100000).toInt() + 1000
        }

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
            val currentUid = FirebaseAuth.getInstance().currentUser?.uid ?: ""
            val avatarBitmap = createCircularAvatar(title)
            notificationBuilder.setLargeIcon(avatarBitmap)

            val avatarIcon = IconCompat.createWithBitmap(avatarBitmap)
            val mePerson = Person.Builder()
                .setName("Me")
                .setKey(currentUid.ifBlank { "me" })
                .build()

            val history = appendMessageToHistory(
                context = this,
                coupleId = coupleId,
                text = messageBody,
                timestamp = timestamp,
                senderName = title,
                senderId = senderId
            )

            val messagingStyle = NotificationCompat.MessagingStyle(mePerson)
                .setConversationTitle(null)

            for (msg in history) {
                val senderPerson = Person.Builder()
                    .setName(msg.senderName)
                    .setIcon(avatarIcon)
                    .setKey(msg.senderId.ifBlank { msg.senderName })
                    .build()
                messagingStyle.addMessage(msg.text, msg.timestamp, senderPerson)
            }

            notificationBuilder.setStyle(messagingStyle)
            if (coupleId.isNotBlank()) {
                notificationBuilder.setGroup("ourbloom_chat_group_${coupleId}")
            }

            // 1. WhatsApp Action: Reply (with RemoteInput for inline quick reply)
            val remoteInput = RemoteInput.Builder(NotificationActionReceiver.KEY_TEXT_REPLY)
                .setLabel("Reply")
                .build()

            val replyIntent = Intent(this, NotificationActionReceiver::class.java).apply {
                action = NotificationActionReceiver.ACTION_REPLY
                putExtra("notificationId", notifId)
                putExtra("notificationTag", TAG_CHAT)
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
                putExtra("notificationTag", TAG_CHAT)
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
                putExtra("notificationTag", TAG_CHAT)
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

            val deleteIntent = Intent(this, NotificationActionReceiver::class.java).apply {
                action = NotificationActionReceiver.ACTION_DISMISS
                putExtra("coupleId", coupleId)
                putExtra("notificationId", notifId)
            }
            val deletePendingIntent = PendingIntent.getBroadcast(
                this,
                notifId * 10 + 4,
                deleteIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            notificationBuilder.setDeleteIntent(deletePendingIntent)

            notificationBuilder.addAction(replyAction)
            notificationBuilder.addAction(markReadAction)
            notificationBuilder.addAction(muteAction)
        }

        if (isChat) {
            recordChatNotificationId(this, notifId)
            notificationManager.notify(TAG_CHAT, notifId, notificationBuilder.build())
        } else {
            notificationManager.notify(notifId, notificationBuilder.build())
        }
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
        const val TAG_CHAT = "chat_message"
        const val CHAT_CHANNEL_ID = "ourbloom_chat_heads_up_v3"
        private const val PREFS_NOTIFS = "ourbloom_active_chat_notifs"
        private const val KEY_ACTIVE_CHAT_IDS = "active_chat_notif_ids"
        private const val PREFS_CONV_HISTORY = "ourbloom_conv_history"
        private const val MAX_HISTORY_MESSAGES = 15

        data class StoredNotifMessage(
            val text: String,
            val timestamp: Long,
            val senderName: String,
            val senderId: String
        )

        fun appendMessageToHistory(
            context: Context,
            coupleId: String,
            text: String,
            timestamp: Long,
            senderName: String,
            senderId: String
        ): List<StoredNotifMessage> {
            if (coupleId.isBlank()) {
                return listOf(StoredNotifMessage(text, timestamp, senderName, senderId))
            }
            val prefs = context.getSharedPreferences(PREFS_CONV_HISTORY, Context.MODE_PRIVATE)
            val key = "history_$coupleId"
            val raw = prefs.getString(key, null)
            val list = mutableListOf<StoredNotifMessage>()
            if (!raw.isNullOrBlank()) {
                try {
                    val array = org.json.JSONArray(raw)
                    for (i in 0 until array.length()) {
                        val obj = array.getJSONObject(i)
                        list.add(
                            StoredNotifMessage(
                                text = obj.optString("text"),
                                timestamp = obj.optLong("time", System.currentTimeMillis()),
                                senderName = obj.optString("sender"),
                                senderId = obj.optString("senderId")
                            )
                        )
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed parsing conv history", e)
                }
            }

            list.add(StoredNotifMessage(text, timestamp, senderName, senderId))

            val trimmed = if (list.size > MAX_HISTORY_MESSAGES) {
                list.subList(list.size - MAX_HISTORY_MESSAGES, list.size)
            } else {
                list
            }

            try {
                val array = org.json.JSONArray()
                for (item in trimmed) {
                    val obj = org.json.JSONObject().apply {
                        put("text", item.text)
                        put("time", item.timestamp)
                        put("sender", item.senderName)
                        put("senderId", item.senderId)
                    }
                    array.put(obj)
                }
                prefs.edit().putString(key, array.toString()).apply()
            } catch (e: Exception) {
                Log.e(TAG, "Failed saving conv history", e)
            }

            return trimmed
        }

        fun clearConversationHistory(context: Context, coupleId: String? = null) {
            try {
                val prefs = context.getSharedPreferences(PREFS_CONV_HISTORY, Context.MODE_PRIVATE)
                if (!coupleId.isNullOrBlank()) {
                    prefs.edit().remove("history_$coupleId").apply()
                } else {
                    prefs.edit().clear().apply()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed clearing conv history", e)
            }
        }

        fun recordChatNotificationId(context: Context, notifId: Int) {
            try {
                val prefs = context.getSharedPreferences(PREFS_NOTIFS, Context.MODE_PRIVATE)
                val existing = prefs.getStringSet(KEY_ACTIVE_CHAT_IDS, emptySet())?.toMutableSet() ?: mutableSetOf()
                existing.add(notifId.toString())
                prefs.edit().putStringSet(KEY_ACTIVE_CHAT_IDS, existing).apply()
            } catch (e: Exception) {
                Log.e(TAG, "Error recording chat notification ID", e)
            }
        }

        fun dismissChatNotifications(context: Context) {
            try {
                clearConversationHistory(context)
                val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

                // 1. API 23+: Query active notifications and cancel any matching TAG_CHAT or CHAT_CHANNEL_ID
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    val activeList = notificationManager.activeNotifications
                    for (sbn in activeList) {
                        val isTagMatch = sbn.tag == TAG_CHAT
                        val isChannelMatch = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            sbn.notification.channelId == CHAT_CHANNEL_ID
                        } else false

                        if (isTagMatch || isChannelMatch) {
                            if (sbn.tag != null) {
                                notificationManager.cancel(sbn.tag, sbn.id)
                            } else {
                                notificationManager.cancel(sbn.id)
                            }
                        }
                    }
                }

                // 2. Cancel all IDs tracked in SharedPreferences (for API < 23 or as fail-safe)
                val prefs = context.getSharedPreferences(PREFS_NOTIFS, Context.MODE_PRIVATE)
                val ids = prefs.getStringSet(KEY_ACTIVE_CHAT_IDS, null)
                if (!ids.isNullOrEmpty()) {
                    for (idStr in ids) {
                        val id = idStr.toIntOrNull() ?: continue
                        notificationManager.cancel(TAG_CHAT, id)
                        notificationManager.cancel(id)
                    }
                    prefs.edit().remove(KEY_ACTIVE_CHAT_IDS).apply()
                }

                Log.d(TAG, "Chat notifications successfully dismissed")
            } catch (e: Exception) {
                Log.e(TAG, "Error dismissing chat notifications", e)
            }
        }
    }
}
