package com.ourbloom.app.dashboard

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ourbloom.app.data.FirestoreRepository
import com.ourbloom.app.data.models.Couple
import com.ourbloom.app.data.models.LoveNote
import com.ourbloom.app.data.models.Memory
import com.ourbloom.app.data.models.Milestone
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class DashboardViewModel : ViewModel() {

    private val repository = FirestoreRepository()

    private val _couple = MutableLiveData<Couple?>()
    val couple: LiveData<Couple?> = _couple

    private val _firstMilestone = MutableLiveData<Milestone?>()
    val firstMilestone: LiveData<Milestone?> = _firstMilestone

    private val _dailyLoveNote = MutableLiveData<LoveNote?>()
    val dailyLoveNote: LiveData<LoveNote?> = _dailyLoveNote

    private val _memories = MutableLiveData<List<Memory>>()
    val memories: LiveData<List<Memory>> = _memories

    private val _currentUser = MutableLiveData<com.ourbloom.app.data.models.User?>()
    val currentUser: LiveData<com.ourbloom.app.data.models.User?> = _currentUser

    private val _partnerUser = MutableLiveData<com.ourbloom.app.data.models.User?>()
    val partnerUser: LiveData<com.ourbloom.app.data.models.User?> = _partnerUser

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    data class TimeElapsed(val days: Long, val hours: Long, val minutes: Long, val seconds: Long, val totalHours: Long)

    private val _timeElapsed = MutableLiveData<TimeElapsed>()
    val timeElapsed: LiveData<TimeElapsed> = _timeElapsed

    private var timerJob: Job? = null

    fun loadDashboardData() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            
            val user = repository.getCurrentUser()
            if (user == null || user.coupleId.isNullOrEmpty()) {
                _error.value = "User not found or not linked to a partner"
                _isLoading.value = false
                return@launch
            }

            _currentUser.value = user
            val cId = user.coupleId

            // Fetch all required data
            val fetchedCouple = repository.getCouple(cId)
            _couple.value = fetchedCouple
            
            if (fetchedCouple != null) {
                val partnerId = if (fetchedCouple.user1 == user.uid) fetchedCouple.user2 else fetchedCouple.user1
                if (partnerId.isNotEmpty()) {
                    _partnerUser.value = repository.getUser(partnerId)
                }
            }

            _firstMilestone.value = repository.getFirstMilestone(cId)
            _dailyLoveNote.value = repository.getDailyLoveNote(cId)
            _memories.value = repository.getRecentMemories(cId)
            
            startTimer()

            _isLoading.value = false
        }
    }

    private fun startTimer() {
        val startDateStr = _couple.value?.startDate?.take(10) ?: return
        val startTimeStr = _couple.value?.startTime ?: "00:00"
        
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            try {
                val format = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                val startDate = format.parse("$startDateStr $startTimeStr") ?: Date()
                
                while (true) {
                    val diff = Math.max(0, Date().time - startDate.time)
                    val days = diff / (1000 * 60 * 60 * 24)
                    val hours = (diff / (1000 * 60 * 60)) % 24
                    val minutes = (diff / (1000 * 60)) % 60
                    val seconds = (diff / 1000) % 60
                    val totalHours = diff / (1000 * 60 * 60)
                    
                    _timeElapsed.postValue(TimeElapsed(days, hours, minutes, seconds, totalHours))
                    delay(1000)
                }
            } catch (e: Exception) {
                // Parse error, ignore
            }
        }
    }

    fun getMonthsTogether(): Int {
        val startDateStr = _couple.value?.startDate?.take(10) ?: return 0
        try {
            val format = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val startDate = format.parse(startDateStr) ?: return 0
            val calendarStart = Calendar.getInstance().apply { time = startDate }
            val calendarNow = Calendar.getInstance()
            
            var months = (calendarNow.get(Calendar.YEAR) - calendarStart.get(Calendar.YEAR)) * 12
            months += calendarNow.get(Calendar.MONTH) - calendarStart.get(Calendar.MONTH)
            
            if (calendarNow.get(Calendar.DAY_OF_MONTH) < calendarStart.get(Calendar.DAY_OF_MONTH)) {
                months--
            }
            
            return if (months < 0) 0 else months
        } catch (e: Exception) {
            return 0
        }
    }

    fun updateSpotifyTrackId(trackId: String, onComplete: (Boolean) -> Unit) {
        val cId = _currentUser.value?.coupleId ?: return onComplete(false)
        viewModelScope.launch {
            val success = repository.updateCoupleSpotifyId(cId, trackId)
            if (success) {
                // Update local model
                val current = _couple.value
                if (current != null) {
                    _couple.value = current.copy(spotifyTrackId = trackId)
                }
            }
            onComplete(success)
        }
    }

    override fun onCleared() {
        super.onCleared()
        timerJob?.cancel()
    }
    
    fun updateNicknameForPartner(nickname: String) {
        viewModelScope.launch {
            val success = repository.updateNicknameForPartner(nickname)
            if (success) {
                // Refresh current user to update the UI
                val updatedUser = repository.getCurrentUser()
                if (updatedUser != null) {
                    _currentUser.postValue(updatedUser)
                }
            } else {
                _error.postValue("Failed to update nickname")
            }
        }
    }

    fun sendHeartbeat(onResult: (Boolean) -> Unit) {
        val cId = _couple.value?.id ?: return
        val senderName = _partnerUser.value?.nicknameForPartner?.takeIf { it.isNotBlank() }
            ?: _currentUser.value?.name?.takeIf { it.isNotBlank() }
            ?: "Your Love"
        val slug = _couple.value?.slug
        viewModelScope.launch {
            val success = repository.sendHeartbeat(cId, senderName, slug)
            onResult(success)
        }
    }
}
