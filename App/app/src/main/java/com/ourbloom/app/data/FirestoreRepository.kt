package com.ourbloom.app.data

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import android.net.Uri
import android.content.Context
import java.util.UUID
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
                        return@withContext baseUrl + urlPath
                    }
                }
            }
            null
        } catch (e: Exception) {
            Log.e("FirestoreRepo", "Error uploading image to server", e)
            null
        }
    }

    suspend fun uploadAudio(context: Context, uri: Uri): String? = withContext(Dispatchers.IO) {
        try {
            val inputStream = context.contentResolver.openInputStream(uri)
            val bytes = inputStream?.readBytes() ?: return@withContext null
            inputStream.close()

            val filename = "${UUID.randomUUID()}.3gp"

            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart(
                    "file",
                    filename,
                    bytes.toRequestBody("audio/3gpp".toMediaTypeOrNull())
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
                        return@withContext baseUrl + urlPath
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
}
