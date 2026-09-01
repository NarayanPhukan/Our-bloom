package com.ourbloom.app.dashboard

import android.net.Uri
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ourbloom.app.data.FirestoreRepository
import com.ourbloom.app.data.models.Memory
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MemoriesViewModel : ViewModel() {

    private val repository = FirestoreRepository()
    private var coupleId: String? = null

    private val _memories = MutableLiveData<List<Memory>>()
    val memories: LiveData<List<Memory>> = _memories

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    private val _uploadStatus = MutableLiveData<String?>()
    val uploadStatus: LiveData<String?> = _uploadStatus

    init {
        loadMemories()
    }

    fun loadMemories() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val user = repository.getCurrentUser()
                if (user != null && user.coupleId != null) {
                    coupleId = user.coupleId
                    val allMemories = repository.getAllMemories(user.coupleId)
                    _memories.value = allMemories
                } else {
                    _error.value = "User not logged in."
                }
            } catch (e: Exception) {
                _error.value = "Failed to load memories: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun addMemory(uri: Uri, title: String, dateStr: String, audioUri: Uri? = null) {
        val cid = coupleId
        if (cid == null) {
            _error.value = "Cannot upload: coupleId is null"
            return
        }

        viewModelScope.launch {
            _isLoading.value = true
            _uploadStatus.value = "Uploading image..."
            try {
                val imageUrl = repository.uploadImage(uri)
                if (imageUrl != null) {
                    var audioUrl = ""
                    if (audioUri != null) {
                        _uploadStatus.value = "Uploading audio..."
                        audioUrl = repository.uploadAudio(audioUri) ?: ""
                    }
                    _uploadStatus.value = "Saving memory..."
                    val newMemory = Memory(
                        coupleId = cid,
                        title = title,
                        dateStr = dateStr,
                        imageUrl = imageUrl,
                        audioUrl = audioUrl,
                        createdAt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).format(Date())
                    )
                    
                    val success = repository.createMemory(newMemory)
                    if (success) {
                        _uploadStatus.value = "Memory saved!"
                        loadMemories() // Refresh the list
                    } else {
                        _error.value = "Failed to save memory to database"
                    }
                } else {
                    _error.value = "Failed to upload image"
                }
            } catch (e: Exception) {
                _error.value = "Error: ${e.message}"
            } finally {
                _isLoading.value = false
                _uploadStatus.value = null
            }
        }
    }
}
