package com.ourbloom.app.data

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import android.net.Uri
import android.content.Context
import java.io.File
import java.util.UUID
import com.ourbloom.app.data.models.ChatMessage
import com.ourbloom.app.data.models.Couple
import com.ourbloom.app.data.models.Milestone
import com.ourbloom.app.data.models.User
import com.ourbloom.app.data.models.SavingsWallet
import com.ourbloom.app.data.models.SavingsGoal
import com.ourbloom.app.data.models.SavingsTransaction
import com.ourbloom.app.data.models.WithdrawalRequest
import com.ourbloom.app.data.models.BankAccountDetails
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import com.google.firebase.firestore.MetadataChanges
import com.ourbloom.app.OurBloomApp
import com.ourbloom.app.fcm.DirectFcmSender
import kotlinx.coroutines.CoroutineScope
import com.ourbloom.app.util.ErrorReporter

class FirestoreRepository {
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val client = OkHttpClient.Builder()
        .connectTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
        .writeTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
        .build()
    private val baseUrl = "https://our-bloom.onrender.com"

    // In-memory cache for partner info & FCM token to avoid repeated lookups
    private var cachedPartnerUid: String? = null
    private var cachedPartnerFcmToken: String? = null
    private var lastTokenFetchTime: Long = 0L

    suspend fun getPartnerFcmToken(coupleId: String, currentUid: String): String? {
        val now = System.currentTimeMillis()
        if (!cachedPartnerFcmToken.isNullOrBlank() && (now - lastTokenFetchTime < 120_000L)) {
            return cachedPartnerFcmToken
        }

        return try {
            var partnerUid = cachedPartnerUid
            if (partnerUid.isNullOrBlank()) {
                val coupleDoc = db.collection("couples").document(coupleId).get().await()
                val u1 = coupleDoc.getString("user1") ?: ""
                val u2 = coupleDoc.getString("user2") ?: ""
                partnerUid = if (currentUid == u1) u2 else (if (currentUid == u2) u1 else u2)
                if (partnerUid.isNotBlank()) {
                    cachedPartnerUid = partnerUid
                }
            }

            if (!partnerUid.isNullOrBlank()) {
                // Try 1: Direct document lookup by partnerUid
                val userDoc = db.collection("users").document(partnerUid).get().await()
                var token = userDoc.getString("fcmToken")

                // Try 2: Query users where uid == partnerUid
                if (token.isNullOrBlank()) {
                    val qUid = db.collection("users").whereEqualTo("uid", partnerUid).get().await()
                    token = qUid.documents.firstOrNull()?.getString("fcmToken")
                }

                // Try 3: Query users where _id == partnerUid
                if (token.isNullOrBlank()) {
                    val qId = db.collection("users").whereEqualTo("_id", partnerUid).get().await()
                    token = qId.documents.firstOrNull()?.getString("fcmToken")
                }

                // Try 4: Query users by coupleId if partnerUid was missing or unmapped
                if (token.isNullOrBlank()) {
                    val qCouple = db.collection("users").whereEqualTo("coupleId", coupleId).get().await()
                    for (doc in qCouple.documents) {
                        if (doc.id != currentUid && doc.getString("uid") != currentUid) {
                            val candToken = doc.getString("fcmToken")
                            if (!candToken.isNullOrBlank()) {
                                token = candToken
                                break
                            }
                        }
                    }
                }

                if (!token.isNullOrBlank()) {
                    cachedPartnerFcmToken = token
                    lastTokenFetchTime = now
                    Log.d("FirestoreRepo", "Resolved partner FCM token for partner $partnerUid")
                    return token
                }
            }
            cachedPartnerFcmToken
        } catch (e: Exception) {
            Log.e("FirestoreRepo", "Error fetching partner FCM token: ${e.message}")
            cachedPartnerFcmToken
        }
    }
    
    // Get the current User document with multi-strategy couple resolution
    suspend fun getCurrentUser(): User? {
        val fbUser = auth.currentUser ?: return null
        return resolveUserAndCouple(fbUser.uid, fbUser.email)
    }

    suspend fun saveUserCoupleId(userId: String, coupleId: String) {
        try {
            db.collection("users").document(userId)
                .set(mapOf("coupleId" to coupleId), com.google.firebase.firestore.SetOptions.merge())
                .await()
        } catch (e: Exception) {
            Log.w("FirestoreRepo", "Error saving coupleId to user: ${e.message}")
        }
    }

    suspend fun resolveUserAndCouple(uid: String, email: String?): User? {
        try {
            // 1. Direct document lookup by uid
            var userDoc = getUser(uid)
            var coupleId = userDoc?.coupleId?.takeIf { it.isNotBlank() && it != "null" }

            // 2. If coupleId is empty or userDoc is null, look up by email in users collection
            var mongoDoc: User? = null
            var matchedDocId: String? = null
            if ((coupleId.isNullOrBlank() || userDoc == null) && !email.isNullOrBlank()) {
                val cleanEmail = email.trim().lowercase()
                try {
                    val query = db.collection("users")
                        .whereEqualTo("email", cleanEmail)
                        .get()
                        .await()
                    for (d in query.documents) {
                        val u = d.toObject(User::class.java)
                        if (u != null) {
                            if (!u.coupleId.isNullOrBlank() && u.coupleId != "null") {
                                mongoDoc = u
                                matchedDocId = d.id
                                coupleId = u.coupleId
                                break
                            } else if (mongoDoc == null) {
                                mongoDoc = u
                                matchedDocId = d.id
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e("FirestoreRepo", "Error searching user by email $cleanEmail: ${e.message}")
                }
            }

            // 3. If still no coupleId, query couples collection directly where user1 or user2 equals uid or matchedDocId
            if (coupleId.isNullOrBlank()) {
                val candidateIds = listOfNotNull(uid, matchedDocId, mongoDoc?.uid).distinct()
                for (candId in candidateIds) {
                    try {
                        val c1 = db.collection("couples").whereEqualTo("user1", candId).get().await()
                        if (!c1.isEmpty) {
                            coupleId = c1.documents[0].id
                            break
                        }
                        val c2 = db.collection("couples").whereEqualTo("user2", candId).get().await()
                        if (!c2.isEmpty) {
                            coupleId = c2.documents[0].id
                            break
                        }
                    } catch (e: Exception) {
                        Log.e("FirestoreRepo", "Error querying couples for candidate $candId: ${e.message}")
                    }
                }
            }

            // 4. If still no coupleId, query backend API /api/auth/me using ID token
            if (coupleId.isNullOrBlank()) {
                try {
                    val idToken = auth.currentUser?.getIdToken(false)?.await()?.token
                    if (!idToken.isNullOrBlank()) {
                        val req = Request.Builder()
                            .url("$baseUrl/api/auth/me")
                            .addHeader("Authorization", "Bearer $idToken")
                            .get()
                            .build()
                        val res = client.newCall(req).execute()
                        val body = res.body?.string()
                        if (res.isSuccessful && !body.isNullOrBlank()) {
                            val json = JSONObject(body)
                            val cId = json.optString("coupleId", "").takeIf { it.isNotBlank() && it != "null" }
                            if (!cId.isNullOrBlank()) {
                                coupleId = cId
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.w("FirestoreRepo", "Backend auth/me couple lookup fallback error: ${e.message}")
                }
            }

            // Synthesize the resolved user
            val baseUser = userDoc ?: mongoDoc ?: User(uid = uid, email = email ?: "", name = auth.currentUser?.displayName ?: "")
            val resolvedUser = baseUser.copy(
                uid = uid,
                email = email ?: baseUser.email,
                name = baseUser.name.ifEmpty { auth.currentUser?.displayName ?: "" },
                coupleId = coupleId ?: baseUser.coupleId
            )

            // Auto-heal Firestore if needed:
            // If the user's uid doc does not have coupleId or does not exist at all, write it to users/{uid}
            if (coupleId != null && (userDoc == null || userDoc.coupleId != coupleId)) {
                try {
                    val updateMap = hashMapOf<String, Any?>(
                        "uid" to uid,
                        "email" to (email ?: resolvedUser.email),
                        "name" to resolvedUser.name,
                        "coupleId" to coupleId,
                        "updatedAt" to java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US).format(java.util.Date())
                    )
                    db.collection("users").document(uid).set(updateMap, com.google.firebase.firestore.SetOptions.merge()).await()
                } catch (e: Exception) {
                    Log.w("FirestoreRepo", "Failed to auto-heal users/$uid: ${e.message}")
                }
            }

            return resolvedUser
        } catch (e: Exception) {
            Log.e("FirestoreRepo", "Error resolving user and couple", e)
            return getUser(uid)
        }
    }

    suspend fun updateFcmToken(token: String) {
        val fbUser = auth.currentUser ?: return
        try {
            // 1. Primary write to users/{uid}
            db.collection("users").document(fbUser.uid).set(
                mapOf(
                    "fcmToken" to token,
                    "fcmUpdatedAt" to System.currentTimeMillis()
                ),
                com.google.firebase.firestore.SetOptions.merge()
            ).await()
            Log.d("FirestoreRepo", "Updated FCM token for ${fbUser.uid}")

            // 2. Also sync to any doc matching email to bridge MongoDB legacy ID records
            val email = fbUser.email?.trim()?.lowercase()
            if (!email.isNullOrBlank()) {
                val emailMatches = db.collection("users")
                    .whereEqualTo("email", email)
                    .get()
                    .await()
                for (doc in emailMatches.documents) {
                    if (doc.id != fbUser.uid) {
                        doc.reference.set(
                            mapOf(
                                "fcmToken" to token,
                                "fcmUpdatedAt" to System.currentTimeMillis()
                            ),
                            com.google.firebase.firestore.SetOptions.merge()
                        ).await()
                        Log.d("FirestoreRepo", "Synced FCM token to matching email doc ${doc.id}")
                    }
                }
            }
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

    suspend fun updateConnectedGoogleEmail(email: String): Boolean {
        val fbUser = auth.currentUser ?: return false
        return try {
            db.collection("users").document(fbUser.uid).update("connectedGoogleEmail", email).await()
            true
        } catch (e: Exception) {
            Log.e("FirestoreRepo", "Error updating connected Google email", e)
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

            // Asynchronously dispatch high-priority FCM push directly to partner (Zero Render dependency)
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val partnerToken = getPartnerFcmToken(coupleId, uid)
                    if (!partnerToken.isNullOrBlank()) {
                        DirectFcmSender.sendPush(
                            context = OurBloomApp.instance,
                            token = partnerToken,
                            title = "$senderName sent you a Heartbeat ❤️",
                            body = "Thinking of you right now... tap to send one back!",
                            data = mapOf(
                                "type" to "heartbeat",
                                "coupleId" to coupleId,
                                "senderId" to uid,
                                "senderName" to senderName
                            )
                        )
                    }
                } catch (e: Exception) {
                    Log.e("FirestoreRepo", "Direct FCM heartbeat error: ${e.message}")
                }
            }

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
            val user = doc.toObject(User::class.java)
            // Fallback: if uid field was not stored in the document, use the document ID
            if (user != null && user.uid.isBlank()) user.copy(uid = doc.id) else user
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
            val couple = doc.toObject(Couple::class.java)
            // Fallback: if id field was not stored in the document, use the document ID
            if (couple != null && couple.id.isBlank()) couple.copy(id = doc.id) else couple
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

    // Fetch daily love note with multi-layer fallback & on-demand generation
    suspend fun getDailyLoveNote(coupleId: String): com.ourbloom.app.data.models.LoveNote? = withContext(Dispatchers.IO) {
        try {
            val todayStr = java.text.SimpleDateFormat("MMMM d, yyyy", java.util.Locale.US).format(java.util.Date())
            val snapshot = db.collection("loveNotes")
                .whereEqualTo("coupleId", coupleId)
                .whereEqualTo("isDailyAi", true)
                .get()
                .await()
            val notes = snapshot.toObjects(com.ourbloom.app.data.models.LoveNote::class.java)

            // 1. Check if today's note already exists in Firestore
            val todayNote = notes.find { it.dateStr.equals(todayStr, ignoreCase = true) }
            if (todayNote != null) {
                return@withContext todayNote
            }

            // 2. If not yet generated for today, request backend to generate via Gemini
            try {
                val currentUid = auth.currentUser?.uid ?: ""
                val url = "$baseUrl/api/couples/$coupleId/daily-love-note?userId=$currentUid"
                val request = Request.Builder().url(url).get().build()
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string()
                        if (!body.isNullOrBlank()) {
                            val json = JSONObject(body)
                            val content = json.optString("content")
                            if (content.isNotBlank()) {
                                return@withContext com.ourbloom.app.data.models.LoveNote(
                                    coupleId = coupleId,
                                    content = content,
                                    author = json.optString("author", "Kuchupuchu ✨"),
                                    dateStr = json.optString("dateStr", todayStr),
                                    isDailyAi = true,
                                    createdAt = json.optString("createdAt")
                                )
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w("FirestoreRepo", "Backend daily note on-demand generation fallback: ${e.message}")
            }

            // 3. Fallback to latest available daily note so the card is never blank
            return@withContext notes.maxByOrNull { it.createdAt ?: it.dateStr }
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

    // Delete a love note
    suspend fun deleteLoveNote(noteId: String): Boolean {
        return try {
            db.collection("loveNotes").document(noteId).delete().await()
            true
        } catch (e: Exception) {
            Log.e("FirestoreRepo", "Error deleting love note", e)
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
            // Read 100% untouched original file bytes directly from Uri without ANY enhancement or recompression
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: return@withContext null

            val mimeType = context.contentResolver.getType(uri) ?: "image/jpeg"
            val extension = when (mimeType) {
                "image/png" -> "png"
                "image/webp" -> "webp"
                "image/gif" -> "gif"
                "image/heic" -> "heic"
                else -> "jpg"
            }
            val filename = "${UUID.randomUUID()}.$extension"

            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart(
                    "file",
                    filename,
                    bytes.toRequestBody(mimeType.toMediaTypeOrNull())
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

    suspend fun uploadAudioFile(file: File): String? = withContext(Dispatchers.IO) {
        try {
            if (!file.exists() || file.length() == 0L) return@withContext null
            val bytes = file.readBytes()
            val filename = "voice_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(8)}.m4a"

            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart(
                    "file",
                    filename,
                    bytes.toRequestBody("audio/mp4".toMediaTypeOrNull())
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
            Log.e("FirestoreRepo", "Error uploading audio file to server", e)
            null
        }
    }

    suspend fun uploadAudio(context: Context, uri: Uri): String? = withContext(Dispatchers.IO) {
        try {
            val bytes = if (uri.scheme == "file" && uri.path != null) {
                File(uri.path!!).readBytes()
            } else {
                val inputStream = context.contentResolver.openInputStream(uri)
                val read = inputStream?.readBytes()
                inputStream?.close()
                read ?: return@withContext null
            }

            val isM4a = uri.path?.endsWith(".m4a") == true || uri.toString().contains(".m4a")
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

    suspend fun getDreamLocations(coupleId: String): List<com.ourbloom.app.data.models.DreamLocation> {
        return try {
            val snapshot = db.collection("dreamLocations")
                .whereEqualTo("coupleId", coupleId)
                .get()
                .await()
            snapshot.toObjects(com.ourbloom.app.data.models.DreamLocation::class.java)
        } catch (e: Exception) {
            Log.e("FirestoreRepo", "Error fetching dream locations", e)
            emptyList()
        }
    }

    suspend fun createDreamLocation(location: com.ourbloom.app.data.models.DreamLocation): Boolean {
        return try {
            db.collection("dreamLocations").add(location).await()
            true
        } catch (e: Exception) {
            Log.e("FirestoreRepo", "Error creating dream location", e)
            false
        }
    }

    suspend fun updateDreamLocation(locationId: String, title: String, description: String, status: String): Boolean {
        return try {
            db.collection("dreamLocations").document(locationId).update(
                mapOf(
                    "title" to title,
                    "description" to description,
                    "status" to status,
                    "updatedAt" to java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US).apply {
                        timeZone = java.util.TimeZone.getTimeZone("UTC")
                    }.format(java.util.Date())
                )
            ).await()
            true
        } catch (e: Exception) {
            Log.e("FirestoreRepo", "Error updating dream location", e)
            false
        }
    }

    suspend fun deleteDreamLocation(locationId: String): Boolean {
        return try {
            db.collection("dreamLocations").document(locationId).delete().await()
            true
        } catch (e: Exception) {
            Log.e("FirestoreRepo", "Error deleting dream location", e)
            false
        }
    }

    suspend fun sendChatMessage(
        coupleId: String,
        text: String,
        imageUrl: String? = null,
        audioUrl: String? = null,
        audioDurationMs: Long? = null,
        senderName: String,
        replyToId: String? = null,
        replyToText: String? = null,
        replyToSenderName: String? = null,
        replyToImageUrl: String? = null,
        timestamp: Long? = null,
        isSticker: Boolean = false
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
                "audioDurationMs" to (audioDurationMs ?: 0L),
                "timestamp" to (timestamp ?: System.currentTimeMillis()),
                "isRead" to false,
                "read" to false,
                "isDelivered" to false,
                "delivered" to false,
                "replyToId" to (replyToId ?: ""),
                "replyToText" to (replyToText ?: ""),
                "replyToSenderName" to (replyToSenderName ?: ""),
                "replyToImageUrl" to (replyToImageUrl ?: ""),
                "isSticker" to isSticker,
                "deletedFor" to emptyList<String>(),
                "pushSent" to true
            )
            val docRef = db.collection("chat_messages").add(messageData).await()

            // Asynchronously dispatch high-priority FCM v1 push directly to partner
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val partnerToken = getPartnerFcmToken(coupleId, uid)
                    if (!partnerToken.isNullOrBlank()) {
                        val bodyText = when {
                            isSticker -> "🎭 Sticker"
                            text.isNotBlank() -> text
                            !imageUrl.isNullOrBlank() -> "📷 Photo"
                            !audioUrl.isNullOrBlank() -> "🎙️ Voice message"
                            else -> "New message"
                        }
                        val directResult = DirectFcmSender.sendPush(
                            context = OurBloomApp.instance,
                            token = partnerToken,
                            title = senderName,
                            body = bodyText,
                            data = mapOf(
                                "type" to "chat",
                                "coupleId" to coupleId,
                                "senderId" to uid,
                                "senderName" to senderName,
                                "messageId" to docRef.id,
                                "messageText" to (if (isSticker) "🎭 Sticker" else text),
                                "imageUrl" to (imageUrl ?: ""),
                                "audioUrl" to (audioUrl ?: ""),
                                "isSticker" to isSticker.toString()
                            )
                        )
                        Log.d("FirestoreRepo", "Direct FCM sendPush: $directResult for msg ${docRef.id}")
                    } else {
                        Log.w("FirestoreRepo", "Partner FCM token not available for couple $coupleId")
                    }
                } catch (e: Exception) {
                    Log.e("FirestoreRepo", "Error dispatching direct FCM push", e)
                }
            }

            true
        } catch (e: Exception) {
            Log.e("FirestoreRepo", "Error sending chat message", e)
            false
        }
    }

    suspend fun deleteChatMessageForEveryone(messageId: String): Boolean {
        if (messageId.isBlank()) return false
        return try {
            db.collection("chat_messages").document(messageId).delete().await()
            true
        } catch (e: Exception) {
            Log.e("FirestoreRepo", "Error deleting chat message for everyone", e)
            false
        }
    }

    suspend fun deleteChatMessageForMe(messageId: String, userId: String): Boolean {
        if (messageId.isBlank() || userId.isBlank()) return false
        return try {
            db.collection("chat_messages").document(messageId)
                .update("deletedFor", com.google.firebase.firestore.FieldValue.arrayUnion(userId))
                .await()
            true
        } catch (e: Exception) {
            Log.e("FirestoreRepo", "Error deleting chat message for me", e)
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
                val now = System.currentTimeMillis()
                toUpdate.forEach { doc ->
                    batch.update(doc.reference, mapOf(
                        "isDelivered" to true,
                        "delivered" to true,
                        "deliveredAt" to now
                    ))
                }
                batch.commit().await()
                Log.d("FirestoreRepo", "Marked ${toUpdate.size} messages as delivered")
            }
        } catch (e: Exception) {
            Log.e("FirestoreRepo", "Error marking messages delivered", e)
        }
    }

    suspend fun markSingleMessageDelivered(messageId: String, coupleId: String = "") {
        if (messageId.isBlank()) return
        try {
            db.collection("chat_messages").document(messageId).update(mapOf(
                "isDelivered" to true,
                "delivered" to true,
                "deliveredAt" to System.currentTimeMillis()
            )).await()
            Log.d("FirestoreRepo", "Marked single message $messageId as delivered via Firestore")
        } catch (e: Exception) {
            Log.e("FirestoreRepo", "Firestore direct mark delivered failed ($messageId), triggering server fallback", e)
            try {
                val json = JSONObject().apply {
                    put("messageId", messageId)
                    if (coupleId.isNotBlank()) put("coupleId", coupleId)
                }
                val body = json.toString().toRequestBody("application/json".toMediaTypeOrNull())
                val request = Request.Builder()
                    .url("$baseUrl/api/chat/delivered")
                    .post(body)
                    .build()
                client.newCall(request).enqueue(object : Callback {
                    override fun onFailure(call: Call, e: java.io.IOException) {
                        Log.d("FirestoreRepo", "Backend delivery ping failed: ${e.message}")
                    }
                    override fun onResponse(call: Call, response: Response) {
                        response.close()
                    }
                })
            } catch (ex: Exception) {
                Log.d("FirestoreRepo", "Backend delivery ping setup failed: ${ex.message}")
            }
        }
    }

    suspend fun markMessagesDeliveredByIds(ids: List<String>) {
        if (ids.isEmpty()) return
        try {
            val batch = db.batch()
            val now = System.currentTimeMillis()
            ids.take(500).forEach { id ->
                if (id.isNotBlank()) {
                    val ref = db.collection("chat_messages").document(id)
                    batch.update(ref, mapOf(
                        "isDelivered" to true,
                        "delivered" to true,
                        "deliveredAt" to now
                    ))
                }
            }
            batch.commit().await()
            Log.d("FirestoreRepo", "Marked ${ids.size} messages delivered by ID")
        } catch (e: Exception) {
            Log.e("FirestoreRepo", "Error marking messages delivered by ID", e)
        }
    }

    suspend fun markMessagesReadByIds(ids: List<String>) {
        if (ids.isEmpty()) return
        try {
            val batch = db.batch()
            val now = System.currentTimeMillis()
            ids.take(500).forEach { id ->
                if (id.isNotBlank()) {
                    val ref = db.collection("chat_messages").document(id)
                    batch.update(ref, mapOf(
                        "isRead" to true,
                        "read" to true,
                        "isDelivered" to true,
                        "delivered" to true,
                        "readAt" to now
                    ))
                }
            }
            batch.commit().await()
            Log.d("FirestoreRepo", "Marked ${ids.size} messages read by ID")
        } catch (e: Exception) {
            Log.e("FirestoreRepo", "Error marking messages read by ID", e)
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
                val now = System.currentTimeMillis()
                toUpdate.forEach { doc ->
                    batch.update(doc.reference, mapOf(
                        "isDelivered" to true,
                        "delivered" to true,
                        "deliveredAt" to now
                    ))
                }
                batch.commit().await()
                Log.d("FirestoreRepo", "Marked ${toUpdate.size} messages from sender $senderId as delivered")
            }
        } catch (e: Exception) {
            Log.e("FirestoreRepo", "Error marking messages from sender delivered", e)
        }
    }

    suspend fun markRecentMessagesDelivered(coupleId: String, senderId: String) {
        if (coupleId.isBlank() || senderId.isBlank()) return
        try {
            val snapshot = db.collection("chat_messages")
                .whereEqualTo("coupleId", coupleId)
                .whereEqualTo("senderId", senderId)
                .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .limit(10)
                .get()
                .await()

            val toUpdate = snapshot.documents.filter { doc ->
                val isDelivered = (doc.getBoolean("isDelivered") == true) || (doc.getBoolean("delivered") == true)
                !isDelivered
            }

            if (toUpdate.isNotEmpty()) {
                val batch = db.batch()
                val now = System.currentTimeMillis()
                toUpdate.forEach { doc ->
                    batch.update(doc.reference, mapOf(
                        "isDelivered" to true,
                        "delivered" to true,
                        "deliveredAt" to now
                    ))
                }
                batch.commit().await()
                Log.d("FirestoreRepo", "Marked ${toUpdate.size} recent messages delivered")
            }
        } catch (e: Exception) {
            Log.e("FirestoreRepo", "Error marking recent messages delivered", e)
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
                val now = System.currentTimeMillis()
                toUpdate.forEach { doc ->
                    batch.update(
                        doc.reference,
                        mapOf(
                            "isRead" to true,
                            "read" to true,
                            "isDelivered" to true,
                            "delivered" to true,
                            "readAt" to now
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

    data class PartnerPresence(
        val status: String = "offline", // "typing", "recording", "online", "offline"
        val lastSeen: Long = 0L
    )

    suspend fun setUserPresence(coupleId: String, userId: String, status: String, lastSeen: Long = System.currentTimeMillis()) {
        if (coupleId.isBlank() || userId.isBlank()) return
        try {
            val statusData = hashMapOf<String, Any>(
                userId to hashMapOf(
                    "status" to status,
                    "timestamp" to System.currentTimeMillis(),
                    "lastSeen" to lastSeen
                )
            )
            db.collection("typing_status").document(coupleId)
                .set(statusData, com.google.firebase.firestore.SetOptions.merge())
                .await()
        } catch (e: Exception) {
            Log.e("FirestoreRepo", "Error setting user presence", e)
        }
    }

    suspend fun setTypingStatus(coupleId: String, userId: String, status: String) {
        setUserPresence(coupleId, userId, status, System.currentTimeMillis())
    }

    fun listenPresenceAndTyping(
        coupleId: String,
        partnerId: String,
        onUpdate: (PartnerPresence) -> Unit
    ): ListenerRegistration {
        return db.collection("typing_status").document(coupleId)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null || !snapshot.exists()) {
                    onUpdate(PartnerPresence("offline", 0L))
                    return@addSnapshotListener
                }
                val data = snapshot.data
                @Suppress("UNCHECKED_CAST")
                val partnerMap = data?.get(partnerId) as? Map<String, Any>
                val rawStatus = partnerMap?.get("status") as? String ?: "offline"
                val timestamp = (partnerMap?.get("timestamp") as? Number)?.toLong() ?: 0L
                val lastSeen = (partnerMap?.get("lastSeen") as? Number)?.toLong() ?: timestamp
                val timeDiff = System.currentTimeMillis() - timestamp

                val resolvedStatus = when {
                    rawStatus == "typing" && timeDiff < 6000L -> "typing"
                    rawStatus == "recording" && timeDiff < 15000L -> "recording"
                    rawStatus == "online" && timeDiff < 40000L -> "online"
                    else -> "offline"
                }
                onUpdate(PartnerPresence(resolvedStatus, lastSeen))
            }
    }

    fun listenTypingStatus(
        coupleId: String,
        partnerId: String,
        onStatusChange: (status: String) -> Unit
    ): ListenerRegistration {
        return listenPresenceAndTyping(coupleId, partnerId) { presence ->
            onStatusChange(presence.status)
        }
    }

    fun getChatMessagesListener(
        coupleId: String,
        onMessages: (List<ChatMessage>) -> Unit
    ): ListenerRegistration {
        val currentUid = auth.currentUser?.uid ?: ""
        return db.collection("chat_messages")
            .whereEqualTo("coupleId", coupleId)
            .addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
                if (error != null || snapshot == null) {
                    Log.e("FirestoreRepo", "Chat listener error", error)
                    return@addSnapshotListener
                }
                val messages = snapshot.documents.mapNotNull { doc ->
                    val msg = doc.toObject(ChatMessage::class.java) ?: return@mapNotNull null
                    if (currentUid.isNotEmpty() && msg.deletedFor.contains(currentUid)) {
                        return@mapNotNull null
                    }
                    msg.isPending = doc.metadata.hasPendingWrites()
                    val isReadDirect = (doc.getBoolean("isRead") == true) || (doc.getBoolean("read") == true)
                    val isDeliveredDirect = (doc.getBoolean("isDelivered") == true) || (doc.getBoolean("delivered") == true)
                    if (isReadDirect) {
                        msg.isRead = true
                    }
                    if (isDeliveredDirect) {
                        msg.isDelivered = true
                    }
                    val rawImageUrl = doc.getString("imageUrl")
                    if (!rawImageUrl.isNullOrBlank()) {
                        msg.imageUrl = rawImageUrl
                    }
                    val rawAudioUrl = doc.getString("audioUrl")
                    if (!rawAudioUrl.isNullOrBlank()) {
                        msg.audioUrl = rawAudioUrl
                    }
                    val rawDuration = doc.getLong("audioDurationMs")
                    if (rawDuration != null && rawDuration > 0L) {
                        msg.audioDurationMs = rawDuration
                    }
                    val rawReplyToImageUrl = doc.getString("replyToImageUrl")
                    if (!rawReplyToImageUrl.isNullOrBlank()) {
                        msg.replyToImageUrl = rawReplyToImageUrl
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

    suspend fun updateChatBackground(coupleId: String, url: String?): Boolean {
        if (coupleId.isBlank()) return false
        return try {
            db.collection("couples").document(coupleId)
                .update("chatBackgroundUrl", url ?: "")
                .await()
            true
        } catch (e: Exception) {
            Log.e("FirestoreRepo", "Error updating chat background", e)
            false
        }
    }

    fun getCoupleListener(coupleId: String, onCouple: (Couple?) -> Unit): ListenerRegistration {
        return db.collection("couples").document(coupleId)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null || !snapshot.exists()) {
                    return@addSnapshotListener
                }
                val couple = snapshot.toObject(Couple::class.java)
                onCouple(couple)
            }
    }

    suspend fun loginWithServer(email: String, password: String): ServerAuthResult = withContext(Dispatchers.IO) {
        try {
            val url = "$baseUrl/api/auth/login"
            val json = JSONObject().apply {
                put("email", email.trim())
                put("password", password)
            }
            val body = json.toString().toRequestBody("application/json".toMediaTypeOrNull())
            val request = Request.Builder().url(url).post(body).build()
            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()
            if (response.isSuccessful && responseBody != null) {
                val resJson = JSONObject(responseBody)
                val customToken = resJson.optString("firebaseCustomToken", "").takeIf { it.isNotEmpty() }
                val userObj = resJson.optJSONObject("user")
                val coupleId = userObj?.optString("coupleId", "")?.takeIf { it.isNotEmpty() && it != "null" }
                ServerAuthResult(success = true, firebaseCustomToken = customToken, coupleId = coupleId)
            } else {
                val errorMsg = try {
                    JSONObject(responseBody ?: "").optString("error", "Login failed")
                } catch (_: Exception) {
                    "Login failed"
                }
                ServerAuthResult(success = false, error = errorMsg)
            }
        } catch (e: Exception) {
            Log.e("FirestoreRepo", "Server login error: ${e.message}")
            ServerAuthResult(success = false, error = e.message ?: "Network error")
        }
    }

    suspend fun registerWithServer(name: String, email: String, password: String): ServerAuthResult = withContext(Dispatchers.IO) {
        try {
            val url = "$baseUrl/api/auth/register"
            val json = JSONObject().apply {
                put("name", name.trim())
                put("email", email.trim())
                put("password", password)
            }
            val body = json.toString().toRequestBody("application/json".toMediaTypeOrNull())
            val request = Request.Builder().url(url).post(body).build()
            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()
            if (response.isSuccessful && responseBody != null) {
                val resJson = JSONObject(responseBody)
                val customToken = resJson.optString("firebaseCustomToken", "").takeIf { it.isNotEmpty() }
                val userObj = resJson.optJSONObject("user")
                val coupleId = userObj?.optString("coupleId", "")?.takeIf { it.isNotEmpty() && it != "null" }
                ServerAuthResult(success = true, firebaseCustomToken = customToken, coupleId = coupleId)
            } else {
                val errorMsg = try {
                    JSONObject(responseBody ?: "").optString("error", "Registration failed")
                } catch (_: Exception) {
                    "Registration failed"
                }
                ServerAuthResult(success = false, error = errorMsg)
            }
        } catch (e: Exception) {
            Log.e("FirestoreRepo", "Server register error: ${e.message}")
            ServerAuthResult(success = false, error = e.message ?: "Network error")
        }
    }

    suspend fun createCoupleViaServer(
        startDate: String,
        startTime: String = "00:00",
        specialPhrase: String = ""
    ): ServerCoupleResult = withContext(Dispatchers.IO) {
        val user = auth.currentUser ?: return@withContext ServerCoupleResult(false, error = "Not authenticated")

        // 1. First attempt creation via backend API
        try {
            val idToken = user.getIdToken(false).await()?.token
            if (!idToken.isNullOrBlank()) {
                val url = "$baseUrl/api/couples"
                val json = JSONObject().apply {
                    put("startDate", startDate)
                    put("startTime", startTime)
                    put("specialPhrase", specialPhrase)
                }
                val body = json.toString().toRequestBody("application/json".toMediaTypeOrNull())
                val request = Request.Builder()
                    .url(url)
                    .addHeader("Authorization", "Bearer $idToken")
                    .post(body)
                    .build()

                val response = client.newCall(request).execute()
                val responseBody = response.body?.string()
                if (response.isSuccessful && responseBody != null) {
                    val resJson = JSONObject(responseBody)
                    val coupleId = resJson.optString("_id", "")
                    val inviteCode = resJson.optString("inviteCode", "")
                    val slug = resJson.optString("slug", "")

                    if (coupleId.isNotEmpty()) {
                        try {
                            db.collection("users").document(user.uid).update("coupleId", coupleId).await()
                        } catch (_: Exception) {}
                    }

                    return@withContext ServerCoupleResult(success = true, coupleId = coupleId, inviteCode = inviteCode, slug = slug)
                } else {
                    Log.w("FirestoreRepo", "Backend API returned ${response.code}: $responseBody. Falling back to direct Firestore...")
                }
            }
        } catch (e: Exception) {
            Log.w("FirestoreRepo", "Server couple creation attempt failed, falling back to direct Firestore: ${e.message}")
        }

        // 2. Direct Cloud Firestore fallback (ensures immediate garden creation with 100% reliability)
        try {
            val coupleRef = db.collection("couples").document()
            val cId = coupleRef.id
            val codeChars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
            val randomCode = (1..4).map { codeChars.random() }.joinToString("")
            val inviteCode = "BLOOM-$randomCode"
            val slug = "garden-${(100..999).random()}"

            val coupleData = hashMapOf(
                "id" to cId,
                "slug" to slug,
                "user1" to user.uid,
                "user2" to "",
                "inviteCode" to inviteCode,
                "startDate" to startDate,
                "startTime" to startTime,
                "specialPhrase" to specialPhrase,
                "spotifyTrackId" to "4O2N861eOnF9q8EtpH8IJu",
                "heroImageUrl" to "/images/journey-bg.jpg",
                "chatBackgroundUrl" to "",
                "createdAt" to com.google.firebase.Timestamp.now()
            )
            coupleRef.set(coupleData).await()
            db.collection("users").document(user.uid).update("coupleId", cId).await()

            // Seed default milestones
            val milestones = listOf(
                hashMapOf(
                    "day" to 1,
                    "label" to "Day 01 — The Beginning",
                    "title" to "When It All Started",
                    "body" to "The very first day of our story. A moment we will treasure forever.",
                    "icon" to "local_florist",
                    "iconFill" to false,
                    "colorScheme" to "primary",
                    "aspectRatio" to "video",
                    "coupleId" to cId
                ),
                hashMapOf(
                    "day" to 7,
                    "label" to "Day 07 — One Week",
                    "title" to "Seven Days of Us",
                    "body" to "A week of getting to know each other, of sweet messages and stolen glances.",
                    "icon" to "water_drop",
                    "iconFill" to false,
                    "colorScheme" to "secondary",
                    "aspectRatio" to "4/5",
                    "coupleId" to cId
                ),
                hashMapOf(
                    "day" to 30,
                    "label" to "Day 30 — One Month",
                    "title" to "Our First Month",
                    "body" to "Thirty days of choosing each other, every single day. This is only the beginning.",
                    "icon" to "favorite",
                    "iconFill" to true,
                    "colorScheme" to "primary",
                    "aspectRatio" to "square",
                    "coupleId" to cId
                )
            )
            for (m in milestones) {
                db.collection("milestones").add(m).await()
            }

            ServerCoupleResult(success = true, coupleId = cId, inviteCode = inviteCode, slug = slug)
        } catch (fsEx: Exception) {
            Log.e("FirestoreRepo", "Firestore direct create couple failed", fsEx)
            ErrorReporter.notifyError("Garden Creation Failed", fsEx.message ?: "Failed to create garden", fsEx, "SetupCoupleFragment")
            ServerCoupleResult(success = false, error = fsEx.message ?: "Failed to create garden")
        }
    }

    suspend fun joinCoupleViaServer(inviteCode: String): ServerCoupleResult = withContext(Dispatchers.IO) {
        val user = auth.currentUser ?: return@withContext ServerCoupleResult(false, error = "Not authenticated")
        val cleanCode = inviteCode.trim().uppercase()

        // 1. Try server join API
        try {
            val idToken = user.getIdToken(false).await()?.token
            if (!idToken.isNullOrBlank()) {
                val url = "$baseUrl/api/couples/join"
                val json = JSONObject().apply {
                    put("inviteCode", cleanCode)
                }
                val body = json.toString().toRequestBody("application/json".toMediaTypeOrNull())
                val request = Request.Builder()
                    .url(url)
                    .addHeader("Authorization", "Bearer $idToken")
                    .post(body)
                    .build()

                val response = client.newCall(request).execute()
                val responseBody = response.body?.string()
                if (response.isSuccessful && responseBody != null) {
                    val resJson = JSONObject(responseBody)
                    val coupleId = resJson.optString("_id", "")
                    val slug = resJson.optString("slug", "")

                    if (coupleId.isNotEmpty()) {
                        try {
                            db.collection("users").document(user.uid).update("coupleId", coupleId).await()
                        } catch (_: Exception) {}
                    }

                    return@withContext ServerCoupleResult(success = true, coupleId = coupleId, slug = slug)
                } else {
                    Log.w("FirestoreRepo", "Backend join API returned ${response.code}: $responseBody. Falling back to direct Firestore...")
                }
            }
        } catch (e: Exception) {
            Log.w("FirestoreRepo", "Server join couple attempt failed, trying direct Firestore: ${e.message}")
        }

        // 2. Direct Cloud Firestore fallback
        try {
            val querySnap = db.collection("couples")
                .whereEqualTo("inviteCode", cleanCode)
                .limit(1)
                .get()
                .await()

            if (!querySnap.isEmpty) {
                val coupleDoc = querySnap.documents[0]
                val coupleId = coupleDoc.id
                val slug = coupleDoc.getString("slug") ?: ""

                db.collection("couples").document(coupleId).update(
                    "user2", user.uid,
                    "updatedAt", com.google.firebase.Timestamp.now()
                ).await()

                db.collection("users").document(user.uid).update("coupleId", coupleId).await()

                ServerCoupleResult(success = true, coupleId = coupleId, slug = slug)
            } else {
                ErrorReporter.notifyError("Join Garden Failed", "Garden with invite code $cleanCode not found", null, "SetupCoupleFragment")
                ServerCoupleResult(success = false, error = "Garden with invite code $cleanCode not found")
            }
        } catch (fsEx: Exception) {
            Log.e("FirestoreRepo", "Firestore direct join couple failed", fsEx)
            ErrorReporter.notifyError("Join Garden Failed", fsEx.message ?: "Failed to join garden", fsEx, "SetupCoupleFragment")
            ServerCoupleResult(success = false, error = fsEx.message ?: "Failed to join garden")
        }
    }

    // ==========================================
    // SAVINGS VAULT / WALLET METHODS
    // ==========================================

    fun observeSavingsWallet(coupleId: String, onUpdate: (SavingsWallet?) -> Unit): ListenerRegistration {
        return db.collection("savings_wallets").document(coupleId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("FirestoreRepo", "Error listening to savings wallet", error)
                    return@addSnapshotListener
                }
                if (snapshot != null && snapshot.exists()) {
                    val wallet = snapshot.toObject(SavingsWallet::class.java)
                    onUpdate(wallet)
                } else {
                    onUpdate(null)
                }
            }
    }

    suspend fun getOrCreateSavingsWallet(coupleId: String): SavingsWallet? {
        return try {
            val docRef = db.collection("savings_wallets").document(coupleId)
            val snap = docRef.get().await()
            if (snap.exists()) {
                snap.toObject(SavingsWallet::class.java)
            } else {
                val coupleDoc = db.collection("couples").document(coupleId).get().await()
                val u1Id = coupleDoc.getString("user1") ?: ""
                val u2Id = coupleDoc.getString("user2") ?: ""
                var u1Name = "Partner 1"
                var u2Name = "Partner 2"
                if (u1Id.isNotBlank()) {
                    val u1Doc = db.collection("users").document(u1Id).get().await()
                    u1Name = u1Doc.getString("name") ?: "Partner 1"
                }
                if (u2Id.isNotBlank()) {
                    val u2Doc = db.collection("users").document(u2Id).get().await()
                    u2Name = u2Doc.getString("name") ?: "Partner 2"
                }

                val newWallet = SavingsWallet(
                    id = coupleId,
                    coupleId = coupleId,
                    totalBalance = 0.0,
                    currency = "₹",
                    user1Id = u1Id,
                    user1Name = u1Name,
                    user1Total = 0.0,
                    user2Id = u2Id,
                    user2Name = u2Name,
                    user2Total = 0.0,
                    lockUntilDate = 0L,
                    lastUpdated = System.currentTimeMillis()
                )
                docRef.set(newWallet).await()
                newWallet
            }
        } catch (e: Exception) {
            Log.e("FirestoreRepo", "Error getting or creating savings wallet", e)
            null
        }
    }

    suspend fun setLockUntilDate(coupleId: String, dateMillis: Long): Boolean {
        return try {
            val walletRef = db.collection("savings_wallets").document(coupleId)
            val snap = walletRef.get().await()
            if (!snap.exists()) {
                getOrCreateSavingsWallet(coupleId)
            }
            walletRef.update(
                mapOf(
                    "lockUntilDate" to dateMillis,
                    "lastUpdated" to System.currentTimeMillis()
                )
            ).await()
            true
        } catch (e: Exception) {
            Log.e("FirestoreRepo", "Error setting lock date", e)
            false
        }
    }

    fun observeSavingsGoals(coupleId: String, onUpdate: (List<SavingsGoal>) -> Unit): ListenerRegistration {
        return db.collection("savings_goals")
            .whereEqualTo("coupleId", coupleId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("FirestoreRepo", "Error listening to savings goals", error)
                    return@addSnapshotListener
                }
                val list = snapshot?.documents?.mapNotNull { it.toObject(SavingsGoal::class.java) } ?: emptyList()
                onUpdate(list.sortedByDescending { it.createdAt })
            }
    }

    fun observeSavingsTransactions(coupleId: String, onUpdate: (List<SavingsTransaction>) -> Unit): ListenerRegistration {
        return db.collection("savings_transactions")
            .whereEqualTo("coupleId", coupleId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("FirestoreRepo", "Error listening to savings transactions", error)
                    return@addSnapshotListener
                }
                val list = snapshot?.documents?.mapNotNull { it.toObject(SavingsTransaction::class.java) } ?: emptyList()
                onUpdate(list.sortedByDescending { it.timestamp })
            }
    }

    fun observeWithdrawalRequests(coupleId: String, onUpdate: (List<WithdrawalRequest>) -> Unit): ListenerRegistration {
        return db.collection("withdrawal_requests")
            .whereEqualTo("coupleId", coupleId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("FirestoreRepo", "Error listening to withdrawal requests", error)
                    return@addSnapshotListener
                }
                val list = snapshot?.documents?.mapNotNull { it.toObject(WithdrawalRequest::class.java) } ?: emptyList()
                onUpdate(list.sortedByDescending { it.requestedAt })
            }
    }

    suspend fun recordDeposit(
        context: Context,
        coupleId: String,
        amount: Double,
        utrNumber: String,
        note: String,
        category: String = "Savings",
        paymentMethod: String = "UPI",
        goalId: String? = null,
        goalTitle: String? = null
    ): Boolean {
        return try {
            val user = getCurrentUser() ?: return false
            val uid = user.uid
            val userName = user.name.ifBlank { "Your Partner" }

            val walletRef = db.collection("savings_wallets").document(coupleId)
            val walletDoc = walletRef.get().await()
            val wallet = if (walletDoc.exists()) {
                walletDoc.toObject(SavingsWallet::class.java)
            } else {
                getOrCreateSavingsWallet(coupleId)
            } ?: return false

            val isUser1 = uid == wallet.user1Id || wallet.user1Id.isBlank()
            val newTotal = wallet.totalBalance + amount
            val newUser1Total = if (isUser1) wallet.user1Total + amount else wallet.user1Total
            val newUser2Total = if (!isUser1) wallet.user2Total + amount else wallet.user2Total

            val updateMap = mutableMapOf<String, Any>(
                "totalBalance" to newTotal,
                "user1Total" to newUser1Total,
                "user2Total" to newUser2Total,
                "lastUpdated" to System.currentTimeMillis()
            )
            if (wallet.user1Id.isBlank()) {
                updateMap["user1Id"] = uid
                updateMap["user1Name"] = userName
            } else if (!isUser1 && wallet.user2Id.isBlank()) {
                updateMap["user2Id"] = uid
                updateMap["user2Name"] = userName
            }

            walletRef.update(updateMap).await()

            if (!goalId.isNullOrBlank()) {
                try {
                    val goalRef = db.collection("savings_goals").document(goalId)
                    val goalDoc = goalRef.get().await()
                    if (goalDoc.exists()) {
                        val currentGAmount = goalDoc.getDouble("currentAmount") ?: 0.0
                        val targetGAmount = goalDoc.getDouble("targetAmount") ?: 0.0
                        val newGAmount = currentGAmount + amount
                        goalRef.update(
                            mapOf(
                                "currentAmount" to newGAmount,
                                "isCompleted" to (newGAmount >= targetGAmount && targetGAmount > 0)
                            )
                        ).await()
                    }
                } catch (e: Exception) {
                    Log.w("FirestoreRepo", "Failed to update goal amount: ${e.message}")
                }
            }

            val txn = SavingsTransaction(
                id = "",
                coupleId = coupleId,
                userId = uid,
                userName = userName,
                type = "deposit",
                amount = amount,
                utrNumber = utrNumber.trim(),
                goalId = goalId ?: "",
                goalTitle = goalTitle ?: "",
                note = note.trim(),
                category = category,
                paymentMethod = paymentMethod,
                timestamp = System.currentTimeMillis()
            )
            db.collection("savings_transactions").add(txn).await()

            val adminAlert = mapOf(
                "type" to "deposit",
                "coupleId" to coupleId,
                "userId" to uid,
                "userName" to userName,
                "amount" to amount,
                "utrNumber" to utrNumber.trim(),
                "note" to note.trim(),
                "timestamp" to System.currentTimeMillis(),
                "status" to "VERIFIED"
            )
            try {
                db.collection("admin_alerts").add(adminAlert)
            } catch (_: Exception) {}

            val partnerToken = getPartnerFcmToken(coupleId, uid)
            if (!partnerToken.isNullOrBlank()) {
                val cleanAmount = if (amount % 1.0 == 0.0) amount.toInt().toString() else String.format(java.util.Locale.US, "%.2f", amount)
                val cleanGoal = if (!goalTitle.isNullOrBlank()) " for $goalTitle" else ""
                val pushTitle = "$userName added ₹$cleanAmount to Vault 🌸"
                val pushBody = if (note.isNotBlank()) "\"$note\"" else "New contribution recorded$cleanGoal!"
                DirectFcmSender.sendPush(
                    context,
                    partnerToken,
                    pushTitle,
                    pushBody,
                    mapOf("type" to "savings", "action" to "open_vault")
                )
            }

            true
        } catch (e: Exception) {
            Log.e("FirestoreRepo", "Error recording deposit", e)
            false
        }
    }

    suspend fun createSavingsGoal(
        coupleId: String,
        title: String,
        targetAmount: Double,
        icon: String = "savings",
        category: String = "General",
        targetDate: String = ""
    ): Boolean {
        return try {
            val goal = SavingsGoal(
                id = "",
                coupleId = coupleId,
                title = title.trim(),
                targetAmount = targetAmount,
                currentAmount = 0.0,
                icon = icon,
                category = category,
                targetDate = targetDate,
                createdAt = System.currentTimeMillis(),
                isCompleted = false
            )
            db.collection("savings_goals").add(goal).await()
            true
        } catch (e: Exception) {
            Log.e("FirestoreRepo", "Error creating savings goal", e)
            false
        }
    }

    suspend fun deleteSavingsGoal(goalId: String): Boolean {
        return try {
            db.collection("savings_goals").document(goalId).delete().await()
            true
        } catch (e: Exception) {
            Log.e("FirestoreRepo", "Error deleting savings goal", e)
            false
        }
    }

    suspend fun submitWithdrawalRequest(
        context: Context,
        coupleId: String,
        amount: Double,
        reason: String,
        payoutMode: String,
        jointAccount: BankAccountDetails? = null,
        p1Account: BankAccountDetails? = null,
        p1Share: Double = 0.0,
        p2Account: BankAccountDetails? = null,
        p2Share: Double = 0.0
    ): Boolean {
        return try {
            val user = getCurrentUser() ?: return false
            val uid = user.uid
            val userName = user.name.ifBlank { "Your Partner" }

            val walletDoc = db.collection("savings_wallets").document(coupleId).get().await()
            val lockDate = walletDoc.getLong("lockUntilDate") ?: 0L
            val now = System.currentTimeMillis()
            val isEmergency = lockDate > 0L && now < lockDate

            val req = WithdrawalRequest(
                id = "",
                coupleId = coupleId,
                requestedByUid = uid,
                requestedByName = userName,
                amount = amount,
                reason = reason.trim(),
                payoutMode = payoutMode,
                jointAccount = jointAccount,
                partner1Account = p1Account,
                partner1ShareAmount = p1Share,
                partner2Account = p2Account,
                partner2ShareAmount = p2Share,
                isEmergency = isEmergency,
                status = "PENDING_APPROVAL",
                requestedAt = now
            )

            db.collection("withdrawal_requests").add(req).await()

            val partnerToken = getPartnerFcmToken(coupleId, uid)
            if (!partnerToken.isNullOrBlank()) {
                val cleanAmount = if (amount % 1.0 == 0.0) amount.toInt().toString() else String.format(java.util.Locale.US, "%.2f", amount)
                val emTag = if (isEmergency) " [Emergency]" else ""
                DirectFcmSender.sendPush(
                    context,
                    partnerToken,
                    "Withdrawal Request: ₹$cleanAmount$emTag ⚠️",
                    "$userName requested a withdrawal for \"$reason\". Tap to review & approve.",
                    mapOf("type" to "savings", "action" to "open_vault")
                )
            }

            true
        } catch (e: Exception) {
            Log.e("FirestoreRepo", "Error submitting withdrawal request", e)
            false
        }
    }

    suspend fun approveWithdrawalRequest(
        context: Context,
        requestId: String,
        request: WithdrawalRequest
    ): Boolean {
        return try {
            val now = System.currentTimeMillis()
            val isEmergency = request.isEmergency
            val newStatus = if (isEmergency) "WAITING_PERIOD" else "PROCESSING_PAYOUT"
            val waitingEnds = if (isEmergency) now + (4 * 24 * 60 * 60 * 1000L) else null
            val payoutExpected = if (!isEmergency) now + (48 * 60 * 60 * 1000L) else null

            val updateData = mutableMapOf<String, Any>(
                "status" to newStatus,
                "partnerApprovedAt" to now
            )
            if (waitingEnds != null) updateData["waitingPeriodEndsAt"] = waitingEnds
            if (payoutExpected != null) updateData["payoutExpectedBy"] = payoutExpected

            db.collection("withdrawal_requests").document(requestId).update(updateData).await()

            if (!isEmergency) {
                deductBalanceForWithdrawal(request)
            }

            val partnerToken = getPartnerFcmToken(request.coupleId, auth.currentUser?.uid ?: "")
            if (!partnerToken.isNullOrBlank()) {
                val cleanAmount = if (request.amount % 1.0 == 0.0) request.amount.toInt().toString() else String.format(java.util.Locale.US, "%.2f", request.amount)
                val bodyText = if (isEmergency) {
                    "Withdrawal approved. 4-day emergency cooldown started. Amount will be credited within 48h after."
                } else {
                    "Withdrawal approved! Processing payout — amount will be credited within 48 hours."
                }
                DirectFcmSender.sendPush(
                    context,
                    partnerToken,
                    "Withdrawal Request Approved ✓",
                    bodyText,
                    mapOf("type" to "savings", "action" to "open_vault")
                )
            }

            true
        } catch (e: Exception) {
            Log.e("FirestoreRepo", "Error approving withdrawal request", e)
            false
        }
    }

    suspend fun cancelOrRejectWithdrawalRequest(
        context: Context,
        requestId: String,
        isReject: Boolean,
        request: WithdrawalRequest
    ): Boolean {
        return try {
            val newStatus = if (isReject) "REJECTED" else "CANCELLED"
            db.collection("withdrawal_requests").document(requestId).update("status", newStatus).await()

            val otherUid = if (isReject) request.requestedByUid else ""
            if (otherUid.isNotBlank()) {
                val token = getPartnerFcmToken(request.coupleId, auth.currentUser?.uid ?: "")
                if (!token.isNullOrBlank()) {
                    val cleanAmount = if (request.amount % 1.0 == 0.0) request.amount.toInt().toString() else String.format(java.util.Locale.US, "%.2f", request.amount)
                    DirectFcmSender.sendPush(
                        context,
                        token,
                        "Withdrawal Request $newStatus",
                        "Your withdrawal request of ₹$cleanAmount was $newStatus.",
                        mapOf("type" to "savings", "action" to "open_vault")
                    )
                }
            }

            true
        } catch (e: Exception) {
            Log.e("FirestoreRepo", "Error rejecting or cancelling withdrawal request", e)
            false
        }
    }

    suspend fun advanceCooldownToProcessing(
        requestId: String,
        request: WithdrawalRequest
    ): Boolean {
        return try {
            val now = System.currentTimeMillis()
            db.collection("withdrawal_requests").document(requestId).update(
                mapOf(
                    "status" to "PROCESSING_PAYOUT",
                    "payoutExpectedBy" to now + (48 * 60 * 60 * 1000L)
                )
            ).await()

            deductBalanceForWithdrawal(request)
            true
        } catch (e: Exception) {
            Log.e("FirestoreRepo", "Error advancing cooldown to payout", e)
            false
        }
    }

    private suspend fun deductBalanceForWithdrawal(request: WithdrawalRequest) {
        try {
            val walletRef = db.collection("savings_wallets").document(request.coupleId)
            val walletDoc = walletRef.get().await()
            if (walletDoc.exists()) {
                val curBalance = walletDoc.getDouble("totalBalance") ?: 0.0
                val newBalance = (curBalance - request.amount).coerceAtLeast(0.0)

                val u1Total = walletDoc.getDouble("user1Total") ?: 0.0
                val u2Total = walletDoc.getDouble("user2Total") ?: 0.0
                val ratio = if (curBalance > 0) request.amount / curBalance else 0.0
                val newU1 = (u1Total - (u1Total * ratio)).coerceAtLeast(0.0)
                val newU2 = (u2Total - (u2Total * ratio)).coerceAtLeast(0.0)

                walletRef.update(
                    mapOf(
                        "totalBalance" to newBalance,
                        "user1Total" to newU1,
                        "user2Total" to newU2,
                        "lastUpdated" to System.currentTimeMillis()
                    )
                ).await()
            }

            val txn = SavingsTransaction(
                id = "",
                coupleId = request.coupleId,
                userId = request.requestedByUid,
                userName = request.requestedByName,
                type = "withdrawal",
                amount = request.amount,
                note = "Withdrawal: ${request.reason}",
                category = if (request.isEmergency) "Emergency Payout" else "Maturity Payout",
                paymentMethod = if (request.payoutMode == "JOINT") "Joint Bank Transfer" else "Separated Bank Transfer",
                timestamp = System.currentTimeMillis()
            )
            db.collection("savings_transactions").add(txn).await()

            val adminPayoutAlert = mapOf(
                "type" to "payout_needed",
                "coupleId" to request.coupleId,
                "amount" to request.amount,
                "payoutMode" to request.payoutMode,
                "reason" to request.reason,
                "jointAccount" to request.jointAccount,
                "partner1Account" to request.partner1Account,
                "partner1ShareAmount" to request.partner1ShareAmount,
                "partner2Account" to request.partner2Account,
                "partner2ShareAmount" to request.partner2ShareAmount,
                "dueWithinHours" to 48,
                "timestamp" to System.currentTimeMillis(),
                "status" to "PENDING_DISBURSEMENT"
            )
            db.collection("admin_alerts").add(adminPayoutAlert).await()
        } catch (e: Exception) {
            Log.e("FirestoreRepo", "Error deducting balance for withdrawal", e)
        }
    }

    // =========================================================================
    // COUPLE GAMES & ARCADE (TIC-TAC-TOE, QUIZ, TRUTH OR DARE)
    // =========================================================================

    fun observeTicTacToeState(coupleId: String, onUpdate: (com.ourbloom.app.games.models.TicTacToeState) -> Unit): ListenerRegistration {
        return db.collection("couples")
            .document(coupleId)
            .collection("games")
            .document("tictactoe")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("FirestoreRepo", "Error listening to TicTacToe state", error)
                    return@addSnapshotListener
                }
                val state = snapshot?.toObject(com.ourbloom.app.games.models.TicTacToeState::class.java)
                    ?: com.ourbloom.app.games.models.TicTacToeState()
                onUpdate(state)
            }
    }

    suspend fun updateTicTacToeState(coupleId: String, state: com.ourbloom.app.games.models.TicTacToeState): Boolean {
        return try {
            db.collection("couples")
                .document(coupleId)
                .collection("games")
                .document("tictactoe")
                .set(state)
                .await()
            true
        } catch (e: Exception) {
            Log.e("FirestoreRepo", "Error updating TicTacToe state", e)
            false
        }
    }

    suspend fun resetTicTacToeGame(
        coupleId: String,
        playerXUid: String,
        playerXName: String,
        playerOUid: String,
        playerOName: String,
        wager: String
    ): Boolean {
        val newState = com.ourbloom.app.games.models.TicTacToeState(
            id = "tictactoe",
            board = List(9) { "" },
            playerXUid = playerXUid,
            playerXName = playerXName,
            playerOUid = playerOUid,
            playerOName = playerOName,
            turnUid = playerXUid,
            wager = wager,
            winnerUid = null,
            winningLine = emptyList(),
            status = "PLAYING",
            lastMoveTimestamp = System.currentTimeMillis(),
            moveCount = 0
        )
        return updateTicTacToeState(coupleId, newState)
    }
}

data class ServerAuthResult(
    val success: Boolean,
    val firebaseCustomToken: String? = null,
    val coupleId: String? = null,
    val error: String? = null
)

data class ServerCoupleResult(
    val success: Boolean,
    val coupleId: String? = null,
    val inviteCode: String? = null,
    val slug: String? = null,
    val error: String? = null
)
