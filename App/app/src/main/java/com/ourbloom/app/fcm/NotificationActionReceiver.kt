package com.ourbloom.app.fcm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput
import com.google.firebase.auth.FirebaseAuth
import com.ourbloom.app.data.FirestoreRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class NotificationActionReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_REPLY = "com.ourbloom.app.fcm.ACTION_REPLY"
        const val ACTION_MARK_AS_READ = "com.ourbloom.app.fcm.ACTION_MARK_AS_READ"
        const val ACTION_MUTE = "com.ourbloom.app.fcm.ACTION_MUTE"
        const val KEY_TEXT_REPLY = "key_text_reply"
        private const val TAG = "NotifActionReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val notificationId = intent.getIntExtra("notificationId", -1)
        val coupleId = intent.getStringExtra("coupleId") ?: ""
        val currentUid = intent.getStringExtra("currentUid")?.takeIf { it.isNotBlank() }
            ?: FirebaseAuth.getInstance().currentUser?.uid
            ?: ""

        when (action) {
            ACTION_REPLY -> {
                val remoteInput = RemoteInput.getResultsFromIntent(intent)
                val replyText = remoteInput?.getCharSequence(KEY_TEXT_REPLY)?.toString()?.trim()

                if (!replyText.isNullOrBlank() && coupleId.isNotBlank()) {
                    val pendingResult = goAsync()
                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            val repo = FirestoreRepository()
                            val user = repo.getCurrentUser()
                            val senderName = user?.name?.ifBlank { "Me" } ?: "Me"

                            repo.sendChatMessage(
                                coupleId = coupleId,
                                text = replyText,
                                imageUrl = null,
                                audioUrl = null,
                                senderName = senderName
                            )

                            if (currentUid.isNotBlank()) {
                                repo.markMessagesAsRead(coupleId, currentUid)
                            }

                            // Dismiss the notification once replied
                            if (notificationId != -1) {
                                NotificationManagerCompat.from(context).cancel(notificationId)
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to send inline notification reply", e)
                        } finally {
                            pendingResult.finish()
                        }
                    }
                }
            }

            ACTION_MARK_AS_READ -> {
                if (notificationId != -1) {
                    NotificationManagerCompat.from(context).cancel(notificationId)
                }
                if (coupleId.isNotBlank() && currentUid.isNotBlank()) {
                    val pendingResult = goAsync()
                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            val repo = FirestoreRepository()
                            repo.markMessagesAsRead(coupleId, currentUid)
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to mark as read from notification", e)
                        } finally {
                            pendingResult.finish()
                        }
                    }
                }
            }

            ACTION_MUTE -> {
                if (notificationId != -1) {
                    NotificationManagerCompat.from(context).cancel(notificationId)
                }
                if (coupleId.isNotBlank()) {
                    val prefs = context.getSharedPreferences("ourbloom_notif_prefs", Context.MODE_PRIVATE)
                    // Mute chat popups for 8 hours (WhatsApp standard)
                    prefs.edit().putLong("mute_until_${coupleId}", System.currentTimeMillis() + (8 * 60 * 60 * 1000L)).apply()
                }
            }
        }
    }
}
