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
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
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
     * Manually trigger upload of all SMS from device to Firebase
     * This uses timestamp=0 to upload all messages, not just new ones
     */
    fun uploadAllSms() {
        viewModelScope.launch {
            try {
                Log.d(TAG, "=== MANUAL UPLOAD ALL SMS TRIGGERED ===")
                _uiState.value = _uiState.value.copy(
                    firebaseStatus = "📤 Uploading all SMS to Firebase..."
                )
                
                // Schedule WorkManager job with timestamp=0 to upload ALL SMS
                val workRequest = OneTimeWorkRequestBuilder<SmsUploadWorker>()
                    .setInputData(
                        androidx.work.workDataOf(
                            "timestamp" to 0L  // 0 means upload ALL SMS
                        )
                    )
                    .build()
                
                WorkManager.getInstance(getApplication())
                    .enqueue(workRequest)
                
                Log.d(TAG, "WorkManager job scheduled for uploading all SMS")
                Log.d(TAG, "Work Request ID: ${workRequest.id}")
                
                _uiState.value = _uiState.value.copy(
                    firebaseStatus = "⏳ Upload in progress... Check logs for details"
                )
                
                // Refresh queue count after a delay to see results
                kotlinx.coroutines.delay(3000)
                refreshQueueCount()
                
            } catch (e: Exception) {
                Log.e(TAG, "Error triggering upload: ${e.message}", e)
                _uiState.value = _uiState.value.copy(
                    error = e.message,
                    firebaseStatus = "❌ Upload failed: ${e.message}"
                )
            }
        }
    }


    /**
     * Test Realtime Database write and read to verify connectivity
     * Enhanced with comprehensive debugging information
     */
    fun testRealtimeDbWrite() {
        viewModelScope.launch {
            try {
                logDebugSection("REALTIME DB DIAGNOSTIC TEST START")
                _uiState.value = _uiState.value.copy(firebaseStatus = "🔍 Testing Realtime Database...")

                val auth = FirebaseAuth.getInstance()
                val database = FirebaseDatabase.getInstance().reference

                // Step 1: Check Firebase SDK initialization
                Log.d(TAG, "📱 Step 1: Firebase SDK Check")
                Log.d(TAG, "  └─ Auth instance: ${auth.app.name}")
                Log.d(TAG, "  └─ Database root: ${database.key ?: "/"}")

                // Step 2: Authentication
                Log.d(TAG, "🔐 Step 2: Authentication")
                val currentUser = auth.currentUser
                if (currentUser != null) {
                    Log.d(TAG, "  ✅ Already authenticated")
                    Log.d(TAG, "  └─ User ID: ${currentUser.uid}")
                    Log.d(TAG, "  └─ Provider: ${currentUser.providerId}")
                    Log.d(TAG, "  └─ Anonymous: ${currentUser.isAnonymous}")
                } else {
                    Log.d(TAG, "  🔄 No user, signing in anonymously...")
                    _uiState.value = _uiState.value.copy(firebaseStatus = "🔐 Authenticating...")
                    
                    val result = auth.signInAnonymously().await()
                    Log.d(TAG, "  ✅ Anonymous sign-in successful")
                    Log.d(TAG, "  └─ New User ID: ${result.user?.uid}")
                }

                val userId = auth.currentUser?.uid ?: throw Exception("❌ No user ID after authentication")

                // Step 3: Prepare test data
                Log.d(TAG, "📝 Step 3: Prepare Test Data")
                val testTimestamp = System.currentTimeMillis()
                val testData = mapOf(
                    "message" to "Hello from SMSArchiver",
                    "timestamp" to testTimestamp,
                    "testId" to "test_$testTimestamp",
                    "deviceInfo" to android.os.Build.MODEL,
                    "androidVersion" to android.os.Build.VERSION.SDK_INT
                )
                Log.d(TAG, "  └─ Test data: $testData")

                // Step 4: Define node path
                val nodeRef = database
                    .child("users")
                    .child(userId)
                    .child("diagnostics")
                    .child("smokeTest")

                val fullPath = "users/$userId/diagnostics/smokeTest"
                Log.d(TAG, "📍 Step 4: Node Path")
                Log.d(TAG, "  └─ Full path: $fullPath")

                // Step 5: Write to Realtime Database
                Log.d(TAG, "✍️ Step 5: Write to Realtime Database")
                _uiState.value = _uiState.value.copy(firebaseStatus = "✍️ Writing test data...")

                try {
                    nodeRef.setValue(testData).await()
                    Log.d(TAG, "  ✅ Write operation completed successfully!")
                } catch (writeError: Exception) {
                    Log.e(TAG, "  ❌ Write operation failed!", writeError)
                    Log.e(TAG, "  └─ Error type: ${writeError.javaClass.simpleName}")
                    Log.e(TAG, "  └─ Error message: ${writeError.message}")
                    throw writeError
                }

                // Step 6: Read back from Realtime Database
                Log.d(TAG, "📖 Step 6: Read from Realtime Database")
                _uiState.value = _uiState.value.copy(firebaseStatus = "📖 Reading test data...")

                val snapshot = nodeRef.get().await()

                if (snapshot.exists()) {
                    Log.d(TAG, "  ✅ Node exists!")
                    Log.d(TAG, "  └─ Key: ${snapshot.key}")
                    Log.d(TAG, "  └─ Data: ${snapshot.value}")

                    val readMessage = snapshot.child("message").getValue(String::class.java)
                    val readTimestamp = snapshot.child("timestamp").getValue(Long::class.java)

                    Log.d(TAG, "  └─ Message: $readMessage")
                    Log.d(TAG, "  └─ Timestamp: $readTimestamp")

                    logDebugSection("TEST RESULT: SUCCESS ✅")
                    _uiState.value = _uiState.value.copy(
                        firebaseStatus = "✅ SUCCESS! Data written & read from Realtime Database\n" +
                                "Path: $fullPath\n" +
                                "Message: $readMessage"
                    )
                } else {
                    Log.w(TAG, "  ⚠️ Node does not exist after write!")
                    Log.w(TAG, "  └─ This means write succeeded but read failed")
                    Log.w(TAG, "  └─ Check Realtime Database security rules")

                    logDebugSection("TEST RESULT: PARTIAL SUCCESS ⚠️")
                    _uiState.value = _uiState.value.copy(
                        firebaseStatus = "⚠️ Node written but not readable\n" +
                                "Check Realtime Database security rules"
                    )
                }

            } catch (e: Exception) {
                Log.e(TAG, "❌ REALTIME DB TEST FAILED!", e)
                Log.e(TAG, "  └─ Error type: ${e.javaClass.simpleName}")
                Log.e(TAG, "  └─ Error message: ${e.message}")
                Log.e(TAG, "  └─ Stack trace:")
                e.printStackTrace()
                
                logDebugSection("TEST RESULT: FAILED ❌")
                
                val errorDetails = when {
                    e.message?.contains("PERMISSION_DENIED") == true -> {
                        "Permission Denied - Check Realtime Database rules"
                    }
                    e.message?.contains("NOT_FOUND") == true -> {
                        "Database Not Found - Create Realtime Database in console"
                    }
                    e.message?.contains("network") == true -> {
                        "Network Error - Check internet connection"
                    }
                    else -> e.message ?: "Unknown error"
                }
                
                _uiState.value = _uiState.value.copy(
                    firebaseStatus = "❌ FAILED: $errorDetails"
                )
            }
        }
    }

    private fun logDebugSection(title: String) {
        Log.d(TAG, "")
        Log.d(TAG, "═══════════════════════════════════════════════════")
        Log.d(TAG, " $title")
        Log.d(TAG, "═══════════════════════════════════════════════════")
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
    val queuedCount: Int = 0,
    val firebaseStatus: String? = null
)
