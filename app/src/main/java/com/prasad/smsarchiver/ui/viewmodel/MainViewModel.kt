package com.prasad.smsarchiver.ui.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.prasad.smsarchiver.data.model.SmsMessage
import com.prasad.smsarchiver.data.repository.SmsRepository
import com.prasad.smsarchiver.data.local.DatabaseProvider
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.prasad.smsarchiver.data.worker.SmsUploadWorker
import com.prasad.smsarchiver.service.SmsMonitorService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * ViewModel for managing SMS archiving state and operations
 */
class MainViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "MainViewModel"
    }

    private val smsRepository = SmsRepository(application)
    private val db = DatabaseProvider.get(application)

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    /**
     * Load all SMS messages from device
     */
    fun loadMessages() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            
            try {
                val messages = smsRepository.getAllMessages()
                _uiState.value = _uiState.value.copy(
                    messages = messages,
                    messageCount = messages.size,
                    isLoading = false,
                    lastSyncTime = getCurrentTime()
                )
                refreshQueueCount()
                Log.d(TAG, "Loaded ${messages.size} messages")
            } catch (e: Exception) {
                Log.e(TAG, "Error loading messages: ${e.message}", e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message
                )
            }
        }
    }

    /**
     * Refresh local upload queue count
     */
    fun refreshQueueCount() {
        viewModelScope.launch {
            try {
                val count = db.queuedSmsDao().count()
                _uiState.value = _uiState.value.copy(queuedCount = count)
                Log.d(TAG, "Queue count: $count")
            } catch (e: Exception) {
                Log.e(TAG, "Error reading queue count: ${e.message}")
            }
        }
    }

    /**
     * Start the foreground monitoring service
     */
    fun startMonitoring() {
        try {
            SmsMonitorService.startService(getApplication())
            _uiState.value = _uiState.value.copy(isMonitoring = true)
            Log.d(TAG, "Started monitoring service")
            refreshQueueCount()
        } catch (e: Exception) {
            Log.e(TAG, "Error starting monitoring: ${e.message}", e)
            _uiState.value = _uiState.value.copy(error = e.message)
        }
    }

    /**
     * Stop the foreground monitoring service
     */
    fun stopMonitoring() {
        try {
            SmsMonitorService.stopService(getApplication())
            _uiState.value = _uiState.value.copy(isMonitoring = false)
            Log.d(TAG, "Stopped monitoring service")
            refreshQueueCount()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping monitoring: ${e.message}", e)
            _uiState.value = _uiState.value.copy(error = e.message)
        }
    }


    /**
     * Clear error message
     */
    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    private fun getCurrentTime(): String {
        val dateFormat = SimpleDateFormat("MMM dd, yyyy HH:mm:ss", Locale.getDefault())
        return dateFormat.format(Date())
    }
}

/**
 * UI state for the main screen
 */
data class MainUiState(
    val messages: List<SmsMessage> = emptyList(),
    val messageCount: Int = 0,
    val isMonitoring: Boolean = false,
    val isLoading: Boolean = false,
    val lastSyncTime: String? = null,
    val error: String? = null,
    val queuedCount: Int = 0
)
