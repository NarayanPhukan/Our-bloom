package com.ourbloom.app.dashboard

import android.net.Uri
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ourbloom.app.data.FirestoreRepository
import com.ourbloom.app.data.models.LoveNote
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class LoveNotesViewModel : ViewModel() {

    private val repository = FirestoreRepository()

    private val _notes = MutableLiveData<List<LoveNote>>()
    val notes: LiveData<List<LoveNote>> = _notes

    private val _isLoading = MutableLiveData<Boolean>(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    fun loadNotes() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                val user = repository.getCurrentUser()
                if (user != null && !user.coupleId.isNullOrEmpty()) {
                    val coupleNotes = repository.getAllLoveNotes(user.coupleId!!)
                    _notes.value = coupleNotes
                } else {
                    _error.value = "Couple info not found."
                }
            } catch (e: Exception) {
                _error.value = "Failed to load love notes."
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun addLoveNote(
        context: android.content.Context,
        content: String,
        imageUri: Uri?,
        audioFile: java.io.File? = null,
        audioDuration: Int = 0,
        isScratchSecret: Boolean = false
    ) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val user = repository.getCurrentUser()
                if (user != null && !user.coupleId.isNullOrEmpty()) {
                    var uploadedImageUrl = ""
                    if (imageUri != null) {
                        val result = repository.uploadImage(context, imageUri)
                        if (result != null) {
                            uploadedImageUrl = result
                        } else {
                            _error.value = "Failed to upload image."
                            _isLoading.value = false
                            return@launch
                        }
                    }

                    var uploadedAudioUrl = ""
                    if (audioFile != null && audioFile.exists()) {
                        val audioResult = repository.uploadAudioFile(audioFile)
                        if (audioResult != null) {
                            uploadedAudioUrl = audioResult
                        } else {
                            _error.value = "Failed to upload voice note."
                            _isLoading.value = false
                            return@launch
                        }
                    }

                    val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
                        timeZone = TimeZone.getTimeZone("UTC")
                    }
                    val now = Date()
                    val isoString = isoFormat.format(now)

                    val newNote = LoveNote(
                        id = "", // Let Firestore assign it
                        coupleId = user.coupleId!!,
                        content = content.ifBlank { if (uploadedAudioUrl.isNotBlank()) "🎙️ Voice Love Letter" else "A sweet love note" },
                        author = user.name?.ifEmpty { "With love" } ?: "With love",
                        isDailyAi = false,
                        dateStr = isoString,
                        imageUrl = uploadedImageUrl,
                        audioUrl = uploadedAudioUrl,
                        audioDuration = audioDuration,
                        createdAt = isoString,
                        isScratchSecret = isScratchSecret,
                        isRevealed = false
                    )

                    val success = repository.createLoveNote(newNote)
                    if (success) {
                        loadNotes() // Refresh
                    } else {
                        _error.value = "Failed to save love note."
                    }
                }
            } catch (e: Exception) {
                _error.value = "Error creating love note."
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun markNoteRevealed(note: LoveNote) {
        viewModelScope.launch {
            try {
                val user = repository.getCurrentUser()
                val cId = user?.coupleId
                if (!cId.isNullOrBlank() && note.id.isNotBlank()) {
                    com.google.firebase.firestore.FirebaseFirestore.getInstance()
                        .collection("couples").document(cId)
                        .collection("love_notes").document(note.id)
                        .update("isRevealed", true)
                }
            } catch (_: Exception) {}
        }
    }
}
