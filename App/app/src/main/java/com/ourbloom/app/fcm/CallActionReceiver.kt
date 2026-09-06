package com.ourbloom.app.fcm

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore

class CallActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val coupleId = intent.getStringExtra("coupleId") ?: return

        Log.d("CallActionReceiver", "Received action: $action for couple: $coupleId")

        if (action == "com.ourbloom.app.ACTION_DECLINE_CALL") {
            // Write declined status to Firestore
            FirebaseFirestore.getInstance()
                .collection("video_calls")
                .document(coupleId)
                .update("status", "declined", "endedAt", System.currentTimeMillis())
                .addOnCompleteListener {
                    Log.d("CallActionReceiver", "Call declined via notification action")
                }

            // Dismiss the call notification
            try {
                val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                nm.cancel(7777)
            } catch (_: Exception) {}
        }
    }
}
