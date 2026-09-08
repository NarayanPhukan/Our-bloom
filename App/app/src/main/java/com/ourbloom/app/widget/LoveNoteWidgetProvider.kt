package com.ourbloom.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import android.widget.Toast
import com.ourbloom.app.MainActivity
import com.ourbloom.app.R
import com.ourbloom.app.data.FirestoreRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class LoveNoteWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_SEND_HEARTBEAT) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val repo = FirestoreRepository()
                    val user = repo.getCurrentUser()
                    if (user != null && !user.coupleId.isNullOrBlank()) {
                        val senderName = user.name.ifBlank { "Your Love" }
                        repo.sendHeartbeat(user.coupleId!!, senderName)
                        CoroutineScope(Dispatchers.Main).launch {
                            Toast.makeText(context, "Heartbeat sent to your partner ❤️", Toast.LENGTH_SHORT).show()
                        }
                    }
                } catch (_: Exception) {}
            }
        } else if (intent.action == ACTION_REFRESH_LOVE_NOTE_WIDGET) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val thisWidget = ComponentName(context, LoveNoteWidgetProvider::class.java)
            val allWidgetIds = appWidgetManager.getAppWidgetIds(thisWidget)
            onUpdate(context, appWidgetManager, allWidgetIds)
        }
    }

    companion object {
        const val PREFS_NAME = "our_bloom_love_note_widget_prefs"
        const val KEY_NOTE_CONTENT = "key_note_content"
        const val KEY_NOTE_AUTHOR = "key_note_author"
        const val KEY_NOTE_DATE = "key_note_date"
        const val KEY_PARTNER_NAME = "key_partner_name"

        const val ACTION_SEND_HEARTBEAT = "com.ourbloom.app.widget.ACTION_SEND_HEARTBEAT"
        const val ACTION_REFRESH_LOVE_NOTE_WIDGET = "com.ourbloom.app.widget.ACTION_REFRESH_LOVE_NOTE_WIDGET"

        fun updateAppWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val content = prefs.getString(KEY_NOTE_CONTENT, "You make every single day feel like a garden in full bloom... 🌸")
            val author = prefs.getString(KEY_NOTE_AUTHOR, "With love ✨")
            val partnerName = prefs.getString(KEY_PARTNER_NAME, "My Love")

            val views = RemoteViews(context.packageName, R.layout.widget_love_note)
            views.setTextViewText(R.id.tv_widget_note_title, "Daily Note • $partnerName")
            views.setTextViewText(R.id.tv_widget_note_content, "\"$content\"")
            views.setTextViewText(R.id.tv_widget_note_author, "— $author")

            // Tap root opens MainActivity directly to Love Notes
            val openIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("action", "open_love_notes")
                putExtra("type", "daily_note")
            }
            val openPendingIntent = PendingIntent.getActivity(
                context,
                2021,
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_love_note_root, openPendingIntent)

            // Tap "Send ❤️" sends heartbeat
            val heartbeatIntent = Intent(context, LoveNoteWidgetProvider::class.java).apply {
                action = ACTION_SEND_HEARTBEAT
            }
            val heartbeatPendingIntent = PendingIntent.getBroadcast(
                context,
                2022,
                heartbeatIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.btn_widget_heartbeat, heartbeatPendingIntent)

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }

        fun saveWidgetData(
            context: Context,
            content: String?,
            author: String?,
            partnerName: String? = null
        ) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().apply {
                if (!content.isNullOrBlank()) putString(KEY_NOTE_CONTENT, content)
                if (!author.isNullOrBlank()) putString(KEY_NOTE_AUTHOR, author)
                if (!partnerName.isNullOrBlank()) putString(KEY_PARTNER_NAME, partnerName)
                apply()
            }
            updateAllWidgets(context)
        }

        fun updateAllWidgets(context: Context) {
            try {
                val appWidgetManager = AppWidgetManager.getInstance(context)
                val thisWidget = ComponentName(context, LoveNoteWidgetProvider::class.java)
                val allWidgetIds = appWidgetManager.getAppWidgetIds(thisWidget)
                if (allWidgetIds.isNotEmpty()) {
                    for (widgetId in allWidgetIds) {
                        updateAppWidget(context, appWidgetManager, widgetId)
                    }
                }
            } catch (_: Exception) {}
        }
    }
}
