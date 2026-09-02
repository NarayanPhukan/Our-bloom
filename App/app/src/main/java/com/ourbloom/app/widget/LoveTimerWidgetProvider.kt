package com.ourbloom.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.RemoteViews
import com.ourbloom.app.MainActivity
import com.ourbloom.app.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class LoveTimerWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
        WidgetLiveUpdateService.start(context)
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        WidgetLiveUpdateService.start(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        WidgetLiveUpdateService.stop(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_REFRESH_WIDGET) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val thisWidget = ComponentName(context, LoveTimerWidgetProvider::class.java)
            val allWidgetIds = appWidgetManager.getAppWidgetIds(thisWidget)
            onUpdate(context, appWidgetManager, allWidgetIds)
        }
    }

    companion object {
        const val PREFS_NAME = "our_bloom_widget_prefs"
        const val KEY_START_DATE = "start_date"
        const val KEY_START_TIME = "start_time"
        const val KEY_MY_NICKNAME = "my_nickname"
        const val KEY_PARTNER_NICKNAME = "partner_nickname"
        const val KEY_MY_NAME = "my_name"
        const val KEY_PARTNER_NAME = "partner_name"
        const val ACTION_REFRESH_WIDGET = "com.ourbloom.app.widget.ACTION_REFRESH_WIDGET"

        fun updateAppWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val startDateStr = prefs.getString(KEY_START_DATE, null)?.take(10)
            val startTimeStr = prefs.getString(KEY_START_TIME, "00:00") ?: "00:00"
            val myNickname = prefs.getString(KEY_MY_NICKNAME, null)?.takeIf { it.isNotBlank() }
            val partnerNickname = prefs.getString(KEY_PARTNER_NICKNAME, null)?.takeIf { it.isNotBlank() }
            val myName = prefs.getString(KEY_MY_NAME, null)?.takeIf { it.isNotBlank() }
            val partnerName = prefs.getString(KEY_PARTNER_NAME, null)?.takeIf { it.isNotBlank() }

            val views = RemoteViews(context.packageName, R.layout.widget_love_timer)

            // Header Title: "[Nickname] & [Nickname] ❤️" (Below "TOGETHER AS" label)
            val leftName = myNickname ?: myName ?: "You"
            val rightName = partnerNickname ?: partnerName ?: "Partner"
            val singleLine = "$leftName & $rightName ❤️"
            val title = if (singleLine.length > 15) "$leftName &\n$rightName ❤️" else singleLine
            views.setTextViewText(R.id.tv_widget_title, title)

            if (!startDateStr.isNullOrEmpty()) {
                try {
                    val format = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                    val startDate = format.parse("$startDateStr $startTimeStr") ?: Date()
                    val diff = Math.max(0L, Date().time - startDate.time)

                    val days = diff / (1000 * 60 * 60 * 24)
                    val hours = (diff / (1000 * 60 * 60)) % 24
                    val minutes = (diff / (1000 * 60)) % 60
                    val seconds = (diff / 1000) % 60
                    val totalHours = diff / (1000 * 60 * 60)

                    views.setTextViewText(R.id.tv_widget_days, days.toString())
                    views.setTextViewText(R.id.tv_widget_hours, String.format(Locale.getDefault(), "%02d", hours))
                    views.setTextViewText(R.id.tv_widget_mins, String.format(Locale.getDefault(), "%02d", minutes))
                    views.setTextViewText(R.id.tv_widget_secs, String.format(Locale.getDefault(), "%02d", seconds))

                    val subtitle = "✨ ${String.format(Locale.getDefault(), "%,d", totalHours)} hours of love"
                    views.setTextViewText(R.id.tv_widget_subtitle, subtitle)
                } catch (e: Exception) {
                    views.setTextViewText(R.id.tv_widget_days, "0")
                    views.setTextViewText(R.id.tv_widget_hours, "00")
                    views.setTextViewText(R.id.tv_widget_mins, "00")
                    views.setTextViewText(R.id.tv_widget_secs, "00")
                    views.setTextViewText(R.id.tv_widget_subtitle, "Loving you every second ✨")
                }
            } else {
                views.setTextViewText(R.id.tv_widget_days, "0")
                views.setTextViewText(R.id.tv_widget_hours, "00")
                views.setTextViewText(R.id.tv_widget_mins, "00")
                views.setTextViewText(R.id.tv_widget_secs, "00")
                views.setTextViewText(R.id.tv_widget_subtitle, "Open OurBloom to sync love counter ✨")
            }

            // Tap anywhere on widget to open MainActivity / Dashboard
            val launchIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                0,
                launchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_root, pendingIntent)

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }

        fun saveWidgetData(
            context: Context,
            startDate: String?,
            startTime: String?,
            partnerNickname: String?,
            myName: String?,
            partnerName: String?,
            myNickname: String? = null
        ) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().apply {
                putString(KEY_START_DATE, startDate)
                putString(KEY_START_TIME, startTime)
                putString(KEY_PARTNER_NICKNAME, partnerNickname)
                putString(KEY_MY_NAME, myName)
                putString(KEY_PARTNER_NAME, partnerName)
                if (myNickname != null) {
                    putString(KEY_MY_NICKNAME, myNickname)
                }
                apply()
            }
            updateAllWidgets(context)
            WidgetLiveUpdateService.start(context)
        }

        fun updateAllWidgets(context: Context) {
            try {
                val appWidgetManager = AppWidgetManager.getInstance(context)
                val thisWidget = ComponentName(context, LoveTimerWidgetProvider::class.java)
                val allWidgetIds = appWidgetManager.getAppWidgetIds(thisWidget)
                if (allWidgetIds.isNotEmpty()) {
                    for (widgetId in allWidgetIds) {
                        updateAppWidget(context, appWidgetManager, widgetId)
                    }
                }
            } catch (e: Exception) {
                // Ignore any widget refresh exceptions
            }
        }

        fun requestPinWidget(context: Context): Boolean {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val appWidgetManager = context.getSystemService(AppWidgetManager::class.java)
                val myProvider = ComponentName(context, LoveTimerWidgetProvider::class.java)
                if (appWidgetManager != null && appWidgetManager.isRequestPinAppWidgetSupported) {
                    appWidgetManager.requestPinAppWidget(myProvider, null, null)
                    return true
                }
            }
            return false
        }
    }
}
