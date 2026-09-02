package com.ourbloom.app.data

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.storage.FirebaseStorage
import android.net.Uri
import java.util.UUID
import com.ourbloom.app.data.models.Couple
import com.ourbloom.app.data.models.Milestone
import com.ourbloom.app.data.models.User
import kotlinx.coroutines.tasks.await

class FirestoreRepository {
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val storage = FirebaseStorage.getInstance("gs://our-bloom.firebasestorage.app")
    
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

    suspend fun uploadImage(uri: Uri): String? {
        return try {
            val filename = UUID.randomUUID().toString()
            val ref = storage.reference.child("uploads/$filename")
            val uploadTask = ref.putFile(uri).await()
            ref.downloadUrl.await().toString()
        } catch (e: Exception) {
            Log.e("FirestoreRepo", "Error uploading image", e)
            null
        }
    }

    suspend fun uploadAudio(uri: Uri): String? {
        return try {
            val filename = UUID.randomUUID().toString() + ".3gp"
            val ref = storage.reference.child("uploads/$filename")
            val uploadTask = ref.putFile(uri).await()
            ref.downloadUrl.await().toString()
        } catch (e: Exception) {
            Log.e("FirestoreRepo", "Error uploading audio", e)
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
}
