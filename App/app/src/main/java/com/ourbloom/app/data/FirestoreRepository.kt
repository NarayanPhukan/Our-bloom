package com.ourbloom.app.data

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import android.net.Uri
import android.content.Context
import java.util.UUID
import com.ourbloom.app.data.models.ChatMessage
import com.ourbloom.app.data.models.Couple
import com.ourbloom.app.data.models.Milestone
import com.ourbloom.app.data.models.User
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

class FirestoreRepository {
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val client = OkHttpClient()
    private val baseUrl = "https://our-bloom.onrender.com"
    
    // Get the current User document
    suspend fun getCurrentUser(): User? {
        val uid = auth.currentUser?.uid ?: return null
        return getUser(uid)
    }

    suspend fun updateFcmToken(token: String) {
        val fbUser = auth.currentUser ?: return
        try {
            db.collection("users").document(fbUser.uid).update("fcmToken", token).await()
        } catch (e: Exception) {
            Log.e("FirestoreRepo", "Error updating FCM token", e)
        }
    }

    suspend fun updateNicknameForPartner(nickname: String): Boolean {
        val fbUser = auth.currentUser ?: return false
        return try {
            db.collection("users").document(fbUser.uid).update("nicknameForPartner", nickname).await()
            true
        } catch (e: Exception) {
            Log.e("FirestoreRepo", "Error updating nickname", e)
            false
        }
    }

    suspend fun updateAvatarUrl(avatarUrl: String): Boolean {
        val fbUser = auth.currentUser ?: return false
        return try {
            db.collection("users").document(fbUser.uid).update("avatarUrl", avatarUrl).await()
            true
        } catch (e: Exception) {
            Log.e("FirestoreRepo", "Error updating avatar URL", e)
            false
        }
    }

    suspend fun sendHeartbeat(coupleId: String, senderName: String, coupleSlug: String? = null): Boolean {
        val uid = auth.currentUser?.uid ?: return false
        return try {
            val heartbeatData = hashMapOf(
                "coupleId" to coupleId,
                "senderId" to uid,
                "senderName" to senderName,
                "createdAt" to System.currentTimeMillis()
            )
            db.collection("heartbeats").add(heartbeatData).await()

            if (!coupleSlug.isNullOrBlank()) {
                withContext(Dispatchers.IO) {
                    try {
                        val url = "$baseUrl/api/couples/$coupleSlug/heartbeat"
                        val json = JSONObject().apply {
                            put("senderName", senderName)
                        }
                        val body = json.toString().toRequestBody("application/json".toMediaTypeOrNull())
                        val request = Request.Builder()
                            .url(url)
                            .post(body)
                            .build()
                        client.newCall(request).execute().close()
                    } catch (e: Exception) {
                        Log.e("FirestoreRepo", "Heartbeat API call error: ${e.message}")
                    }
                }
            }

            true
        } catch (e: Exception) {
            Log.e("FirestoreRepo", "Error sending heartbeat", e)
            false
        }
    }

    suspend fun getUser(userId: String): User? {
        return try {
            val doc = db.collection("users").document(userId).get().await()
            doc.toObject(User::class.java)
        } catch (e: Exception) {
            Log.e("FirestoreRepo", "Error fetching user $userId", e)
            null
        }
    }
    
    // Fetch milestones for a specific couple, ordered by day
    suspend fun getMilestones(coupleId: String): List<Milestone> {
        return try {
            val snapshot = db.collection("milestones")
                .whereEqualTo("coupleId", coupleId)
                .orderBy("day", Query.Direction.ASCENDING)
                .get()
                .await()
            snapshot.toObjects(Milestone::class.java)
        } catch (e: Exception) {
            Log.e("FirestoreRepo", "Error fetching milestones", e)
            emptyList()
        }
    }
    
    // Add a new milestone
    suspend fun addMilestone(milestone: Milestone): Boolean {
        return try {
            db.collection("milestones").add(milestone).await()
            true
        } catch (e: Exception) {
            Log.e("FirestoreRepo", "Error adding milestone", e)
            false
        }
    }

    // Get a couple by ID
    suspend fun getCouple(coupleId: String): Couple? {
        return try {
            val doc = db.collection("couples").document(coupleId).get().await()
            doc.toObject(Couple::class.java)
        } catch (e: Exception) {
            Log.e("FirestoreRepo", "Error fetching couple", e)
            null
        }
    }

    // Update couple's Spotify track ID
    suspend fun updateCoupleSpotifyId(coupleId: String, trackId: String): Boolean {
        return try {
            db.collection("couples").document(coupleId)
                .update("spotifyTrackId", trackId)
                .await()
            true
        } catch (e: Exception) {
            Log.e("FirestoreRepo", "Error updating Spotify track ID", e)
            false
        }
    }

    // Fetch the very first milestone
    suspend fun getFirstMilestone(coupleId: String): Milestone? {
        return try {
            val snapshot = db.collection("milestones")
                .whereEqualTo("coupleId", coupleId)
                .orderBy("day", Query.Direction.ASCENDING)
                .limit(1)
                .get()
                .await()
            snapshot.documents.firstOrNull()?.toObject(Milestone::class.java)
        } catch (e: Exception) {
            Log.e("FirestoreRepo", "Error fetching first milestone", e)
            null
        }
    }

    // Fetch daily love note
    suspend fun getDailyLoveNote(coupleId: String): com.ourbloom.app.data.models.LoveNote? {
        return try {
            val snapshot = db.collection("loveNotes")
                .whereEqualTo("coupleId", coupleId)
                .whereEqualTo("isDailyAi", true)
                .get()
                .await()
            val notes = snapshot.toObjects(com.ourbloom.app.data.models.LoveNote::class.java)
            return notes.maxByOrNull { it.dateStr }
        } catch (e: Exception) {
            Log.e("FirestoreRepo", "Error fetching daily love note", e)
            null
        }
    }

    // Fetch all love notes for the couple
    suspend fun getAllLoveNotes(coupleId: String): List<com.ourbloom.app.data.models.LoveNote> {
        return try {
            val snapshot = db.collection("loveNotes")
                .whereEqualTo("coupleId", coupleId)
                .get()
                .await()
            val notes = snapshot.toObjects(com.ourbloom.app.data.models.LoveNote::class.java)
            // Sort by createdAt descending (newest first). Since it's a string, sorting by string works for ISO dates
            notes.sortedByDescending { it.createdAt ?: "" }
        } catch (e: Exception) {
            Log.e("FirestoreRepo", "Error fetching love notes", e)
            emptyList()
        }
    }

    // Add a new love note
    suspend fun createLoveNote(note: com.ourbloom.app.data.models.LoveNote): Boolean {
        return try {
            db.collection("loveNotes").add(note).await()
            true
        } catch (e: Exception) {
            Log.e("FirestoreRepo", "Error creating love note", e)
            false
        }
    }

    // Fetch recent memories for the gallery
    suspend fun getRecentMemories(coupleId: String): List<com.ourbloom.app.data.models.Memory> {
        return try {
            val snapshot = db.collection("memories")
                .whereEqualTo("coupleId", coupleId)
                .get()
                .await()
            val memories = snapshot.toObjects(com.ourbloom.app.data.models.Memory::class.java)
            memories.sortedByDescending { memory ->
                parseDateRobustly(memory.dateStr)
            }.take(5)
        } catch (e: Exception) {
            Log.e("FirestoreRepo", "Error fetching memories", e)
            emptyList()
        }
    }

    // Fetch all memories for the gallery
    suspend fun getAllMemories(coupleId: String): List<com.ourbloom.app.data.models.Memory> {
        return try {
            val snapshot = db.collection("memories")
                .whereEqualTo("coupleId", coupleId)
                .get()
                .await()
            val memories = snapshot.toObjects(com.ourbloom.app.data.models.Memory::class.java)
            memories.sortedByDescending { memory ->
                parseDateRobustly(memory.dateStr)
            }
        } catch (e: Exception) {
            Log.e("FirestoreRepo", "Error fetching all memories", e)
            emptyList()
        }
    }

    private fun parseDateRobustly(dateStr: String): Long {
        if (dateStr.isBlank()) return 0L
        val normalizedDate = dateStr.lowercase(java.util.Locale.US).replaceFirstChar { it.titlecase(java.util.Locale.US) }
        val formats = listOf(
            "MMMM d, yyyy",
            "d MMMM, yyyy",
            "d MMM yyyy",
            "d MMMM yyyy",
            "MMM d, yyyy",
            "MMMM d yyyy"
        )
        for (pattern in formats) {
            try {
                val format = java.text.SimpleDateFormat(pattern, java.util.Locale.US)
                val date = format.parse(normalizedDate)
                if (date != null) return date.time
            } catch (e: Exception) {
                // Ignore and try next format
            }
        }
        return 0L
    }

    suspend fun uploadImage(context: Context, uri: Uri): String? = withContext(Dispatchers.IO) {
        try {
            val inputStream = context.contentResolver.openInputStream(uri)
            val bytes = inputStream?.readBytes() ?: return@withContext null
            inputStream.close()

            val ext = context.contentResolver.getType(uri)?.split("/")?.lastOrNull() ?: "jpg"
            val filename = "${UUID.randomUUID()}.$ext"

            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart(
                    "file",
                    filename,
                    bytes.toRequestBody(context.contentResolver.getType(uri)?.toMediaTypeOrNull())
                )
                .build()

            val request = Request.Builder()
                .url("$baseUrl/api/upload")
                .post(requestBody)
                .build()

            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val responseBody = response.body?.string()
                if (responseBody != null) {
                    val json = JSONObject(responseBody)
                    val urlPath = json.optString("url", "")
                    if (urlPath.isNotEmpty()) {
                        return@withContext if (urlPath.startsWith("http")) urlPath else baseUrl + urlPath
                    }
                }
            }
            null
        } catch (e: Exception) {
            Log.e("FirestoreRepo", "Error uploading image to server", e)
            null
        }
    }

    suspend fun uploadImageBytes(bytes: ByteArray, filename: String = "${UUID.randomUUID()}.jpg"): String? = withContext(Dispatchers.IO) {
        try {
            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart(
                    "file",
                    filename,
                    bytes.toRequestBody("image/jpeg".toMediaTypeOrNull())
                )
                .build()

            val request = Request.Builder()
                .url("$baseUrl/api/upload")
                .post(requestBody)
                .build()

            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val responseBody = response.body?.string()
                if (responseBody != null) {
                    val json = JSONObject(responseBody)
                    val urlPath = json.optString("url", "")
                    if (urlPath.isNotEmpty()) {
                        return@withContext if (urlPath.startsWith("http")) urlPath else baseUrl + urlPath
                    }
                }
            }
            null
        } catch (e: Exception) {
            Log.e("FirestoreRepo", "Error uploading image bytes to server", e)
            null
        }
    }

    suspend fun uploadAudio(context: Context, uri: Uri): String? = withContext(Dispatchers.IO) {
        try {
            val inputStream = context.contentResolver.openInputStream(uri)
            val bytes = inputStream?.readBytes() ?: return@withContext null
            inputStream.close()

            val isM4a = uri.path?.endsWith(".m4a") == true
            val ext = if (isM4a) "m4a" else "3gp"
            val mime = if (isM4a) "audio/mp4" else "audio/3gpp"
            val filename = "${UUID.randomUUID()}.$ext"

            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart(
                    "file",
                    filename,
                    bytes.toRequestBody(mime.toMediaTypeOrNull())
                )
                .build()

            val request = Request.Builder()
                .url("$baseUrl/api/upload")
                .post(requestBody)
                .build()

            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val responseBody = response.body?.string()
                if (responseBody != null) {
                    val json = JSONObject(responseBody)
                    val urlPath = json.optString("url", "")
                    if (urlPath.isNotEmpty()) {
                        return@withContext if (urlPath.startsWith("http")) urlPath else baseUrl + urlPath
                    }
                }
            }
            null
        } catch (e: Exception) {
            Log.e("FirestoreRepo", "Error uploading audio to server", e)
            null
        }
    }

    suspend fun createMemory(memory: com.ourbloom.app.data.models.Memory): Boolean {
        return try {
            db.collection("memories").add(memory).await()
            true
        } catch (e: Exception) {
            Log.e("FirestoreRepo", "Error creating memory", e)
            false
        }
    }

    suspend fun deleteMemory(memoryId: String): Boolean {
        return try {
            // Since we are no longer using Firebase Storage, we skip file deletion for now.
            // (A full implementation would call a DELETE /api/upload endpoint on the server)
            db.collection("memories").document(memoryId).delete().await()
            true
        } catch (e: Exception) {
            Log.e("FirestoreRepo", "Error deleting memory", e)
            false
        }
    }

    suspend fun sendChatMessage(
        coupleId: String,
        text: String,
        imageUrl: String? = null,
        audioUrl: String? = null,
        senderName: String
    ): Boolean {
        val uid = auth.currentUser?.uid ?: return false
        return try {
            val messageData = hashMapOf(
                "coupleId" to coupleId,
                "senderId" to uid,
                "senderName" to senderName,
                "text" to text,
                "imageUrl" to (imageUrl ?: ""),
                "audioUrl" to (audioUrl ?: ""),
                "timestamp" to System.currentTimeMillis(),
                "isRead" to false,
                "read" to false,
                "isDelivered" to false,
                "delivered" to false
            )
            db.collection("chat_messages").add(messageData).await()
            true
        } catch (e: Exception) {
            Log.e("FirestoreRepo", "Error sending chat message", e)
            false
        }
    }

    suspend fun markMessagesDelivered(coupleId: String, currentUserId: String) {
        if (coupleId.isBlank() || currentUserId.isBlank()) return
        try {
            val snapshot = db.collection("chat_messages")
                .whereEqualTo("coupleId", coupleId)
                .get()
                .await()

            val toUpdate = snapshot.documents.filter { doc ->
                val senderId = doc.getString("senderId") ?: ""
                val isDelivered = (doc.getBoolean("isDelivered") == true) || (doc.getBoolean("delivered") == true)
                senderId.isNotBlank() && senderId != currentUserId && !isDelivered
            }

            if (toUpdate.isNotEmpty()) {
                val batch = db.batch()
                toUpdate.forEach { doc ->
                    batch.update(doc.reference, mapOf(
                        "isDelivered" to true,
                        "delivered" to true
                    ))
                }
                batch.commit().await()
                Log.d("FirestoreRepo", "Marked ${toUpdate.size} messages as delivered")
            }
        } catch (e: Exception) {
            Log.e("FirestoreRepo", "Error marking messages delivered", e)
        }
    }

    suspend fun markSingleMessageDelivered(messageId: String) {
        if (messageId.isBlank()) return
        try {
            db.collection("chat_messages").document(messageId).update(mapOf(
                "isDelivered" to true,
                "delivered" to true
            )).await()
            Log.d("FirestoreRepo", "Marked single message $messageId as delivered")
        } catch (e: Exception) {
            Log.e("FirestoreRepo", "Error marking single message $messageId delivered", e)
        }
    }

    suspend fun markMessagesFromSenderDelivered(coupleId: String, senderId: String) {
        if (coupleId.isBlank() || senderId.isBlank()) return
        try {
            val snapshot = db.collection("chat_messages")
                .whereEqualTo("coupleId", coupleId)
                .whereEqualTo("senderId", senderId)
                .get()
                .await()

            val toUpdate = snapshot.documents.filter { doc ->
                val isDelivered = (doc.getBoolean("isDelivered") == true) || (doc.getBoolean("delivered") == true)
                !isDelivered
            }

            if (toUpdate.isNotEmpty()) {
                val batch = db.batch()
                toUpdate.forEach { doc ->
                    batch.update(doc.reference, mapOf(
                        "isDelivered" to true,
                        "delivered" to true
                    ))
                }
                batch.commit().await()
                Log.d("FirestoreRepo", "Marked ${toUpdate.size} messages from sender $senderId as delivered")
            }
        } catch (e: Exception) {
            Log.e("FirestoreRepo", "Error marking messages from sender delivered", e)
        }
    }

    suspend fun markMessagesAsRead(coupleId: String, currentUserId: String) {
        if (coupleId.isBlank() || currentUserId.isBlank()) return
        try {
            val snapshot = db.collection("chat_messages")
                .whereEqualTo("coupleId", coupleId)
                .get()
                .await()

            val toUpdate = snapshot.documents.filter { doc ->
                val senderId = doc.getString("senderId") ?: ""
                val isRead = (doc.getBoolean("isRead") == true) || (doc.getBoolean("read") == true)
                val isDelivered = (doc.getBoolean("isDelivered") == true) || (doc.getBoolean("delivered") == true)
                senderId.isNotBlank() && senderId != currentUserId && (!isRead || !isDelivered)
            }

            if (toUpdate.isNotEmpty()) {
                val batch = db.batch()
                toUpdate.forEach { doc ->
                    batch.update(
                        doc.reference,
                        mapOf(
                            "isRead" to true,
                            "read" to true,
                            "isDelivered" to true,
                            "delivered" to true
                        )
                    )
                }
                batch.commit().await()
                Log.d("FirestoreRepo", "Marked ${toUpdate.size} messages as read & delivered")
            }
        } catch (e: Exception) {
            Log.e("FirestoreRepo", "Error marking messages as read", e)
        }
    }

    suspend fun setTypingStatus(coupleId: String, userId: String, status: String) {
        if (coupleId.isBlank() || userId.isBlank()) return
        try {
            val statusData = hashMapOf<String, Any>(
                userId to hashMapOf(
                    "status" to status,
                    "timestamp" to System.currentTimeMillis()
                )
            )
            db.collection("typing_status").document(coupleId)
                .set(statusData, com.google.firebase.firestore.SetOptions.merge())
                .await()
        } catch (e: Exception) {
            Log.e("FirestoreRepo", "Error setting typing status", e)
        }
    }

    fun listenTypingStatus(
        coupleId: String,
        partnerId: String,
        onStatusChange: (status: String) -> Unit
    ): ListenerRegistration {
        return db.collection("typing_status").document(coupleId)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null || !snapshot.exists()) {
                    onStatusChange("idle")
                    return@addSnapshotListener
                }
                val data = snapshot.data
                @Suppress("UNCHECKED_CAST")
                val partnerMap = data?.get(partnerId) as? Map<String, Any>
                val status = partnerMap?.get("status") as? String ?: "idle"
                val timestamp = (partnerMap?.get("timestamp") as? Number)?.toLong() ?: 0L
                val timeDiff = System.currentTimeMillis() - timestamp

                // Consider active if within last 6 seconds (or 15s for recording)
                val maxDiff = if (status == "recording") 15000L else 6000L
                if (timeDiff < maxDiff && (status == "typing" || status == "recording")) {
                    onStatusChange(status)
                } else {
                    onStatusChange("idle")
                }
            }
    }

    fun getChatMessagesListener(
        coupleId: String,
        onMessages: (List<ChatMessage>) -> Unit
    ): ListenerRegistration {
        return db.collection("chat_messages")
            .whereEqualTo("coupleId", coupleId)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) {
                    Log.e("FirestoreRepo", "Chat listener error", error)
                    return@addSnapshotListener
                }
                val messages = snapshot.documents.mapNotNull { doc ->
                    val msg = doc.toObject(ChatMessage::class.java) ?: return@mapNotNull null
                    val isReadDirect = (doc.getBoolean("isRead") == true) || (doc.getBoolean("read") == true)
                    val isDeliveredDirect = (doc.getBoolean("isDelivered") == true) || (doc.getBoolean("delivered") == true)
                    if (isReadDirect) {
                        msg.isRead = true
                    }
                    if (isDeliveredDirect) {
                        msg.isDelivered = true
                    }
                    msg
                }.sortedBy { it.timestamp }
                onMessages(messages)
            }
    }

    suspend fun exportChatBackupJson(coupleId: String): String {
        return try {
            val snapshot = db.collection("chat_messages")
                .whereEqualTo("coupleId", coupleId)
                .get()
                .await()

            val messages = snapshot.documents.mapNotNull { it.toObject(ChatMessage::class.java) }
                .sortedBy { it.timestamp }

            val jsonArray = JSONArray()
            for (msg in messages) {
                val obj = JSONObject().apply {
                    put("id", msg.id)
                    put("coupleId", msg.coupleId)
                    put("senderId", msg.senderId)
                    put("senderName", msg.senderName)
                    put("text", msg.text)
                    put("imageUrl", msg.imageUrl ?: "")
                    put("timestamp", msg.timestamp)
                    put("isRead", msg.isRead)
                }
                jsonArray.put(obj)
            }

            val backupObj = JSONObject().apply {
                put("appName", "OurBloom")
                put("version", 1)
                put("coupleId", coupleId)
                put("exportedAt", System.currentTimeMillis())
                put("totalMessages", messages.size)
                put("messages", jsonArray)
            }

            backupObj.toString(2)
        } catch (e: Exception) {
            Log.e("FirestoreRepo", "Error exporting chat backup", e)
            throw e
        }
    }

    suspend fun importChatBackupJson(coupleId: String, jsonString: String): Int {
        return try {
            val backupObj = JSONObject(jsonString)
            val appName = backupObj.optString("appName")
            if (appName != "OurBloom") {
                throw IllegalArgumentException("Invalid backup file: Not an OurBloom backup")
            }

            val messagesArray = backupObj.optJSONArray("messages") ?: JSONArray()
            var importedCount = 0

            val existingSnapshot = db.collection("chat_messages")
                .whereEqualTo("coupleId", coupleId)
                .get()
                .await()
            val existingSignatures = existingSnapshot.documents.map { 
                "${it.getString("senderId")}_${it.getLong("timestamp")}" 
            }.toSet()

            val batch = db.batch()
            for (i in 0 until messagesArray.length()) {
                val obj = messagesArray.getJSONObject(i)
                val senderId = obj.optString("senderId")
                val timestamp = obj.optLong("timestamp")
                val sig = "${senderId}_${timestamp}"

                if (!existingSignatures.contains(sig)) {
                    val docRef = db.collection("chat_messages").document()
                    val data = hashMapOf(
                        "coupleId" to coupleId,
                        "senderId" to senderId,
                        "senderName" to obj.optString("senderName", "Partner"),
                        "text" to obj.optString("text", ""),
                        "imageUrl" to obj.optString("imageUrl", ""),
                        "timestamp" to timestamp,
                        "isRead" to obj.optBoolean("isRead", true)
                    )
                    batch.set(docRef, data)
                    importedCount++
                }
            }

            if (importedCount > 0) {
                batch.commit().await()
            }
            importedCount
        } catch (e: Exception) {
            Log.e("FirestoreRepo", "Error importing chat backup", e)
            throw e
        }
    }
}
