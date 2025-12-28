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
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
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
     * Test Firestore write and read to verify database connectivity
     * Enhanced with comprehensive debugging information
     */
    fun testFirestoreWrite() {
        viewModelScope.launch {
            try {
                logDebugSection("FIRESTORE DIAGNOSTIC TEST START")
                _uiState.value = _uiState.value.copy(firebaseStatus = "🔍 Testing Firestore...")

                val auth = FirebaseAuth.getInstance()
                val firestore = FirebaseFirestore.getInstance()

                // Step 1: Check Firebase SDK initialization
                Log.d(TAG, "📱 Step 1: Firebase SDK Check")
                Log.d(TAG, "  └─ Auth instance: ${auth.app.name}")
                Log.d(TAG, "  └─ Firestore instance: ${firestore.app.name}")
                Log.d(TAG, "  └─ Project ID: ${firestore.app.options.projectId}")
                Log.d(TAG, "  └─ App ID: ${firestore.app.options.applicationId}")

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

                // Step 4: Define document path
                val docPath = firestore.collection("users")
                    .document(userId)
                    .collection("diagnostics")
                    .document("smokeTest")
                
                val fullPath = "users/$userId/diagnostics/smokeTest"
                Log.d(TAG, "📍 Step 4: Document Path")
                Log.d(TAG, "  └─ Full path: $fullPath")

                // Step 5: Write to Firestore
                Log.d(TAG, "✍️ Step 5: Write to Firestore")
                _uiState.value = _uiState.value.copy(firebaseStatus = "✍️ Writing test data...")
                
                try {
                    docPath.set(testData, SetOptions.merge()).await()
                    Log.d(TAG, "  ✅ Write operation completed successfully!")
                } catch (writeError: Exception) {
                    Log.e(TAG, "  ❌ Write operation failed!", writeError)
                    Log.e(TAG, "  └─ Error type: ${writeError.javaClass.simpleName}")
                    Log.e(TAG, "  └─ Error message: ${writeError.message}")
                    if (writeError is com.google.firebase.firestore.FirebaseFirestoreException) {
                        Log.e(TAG, "  └─ Firestore error code: ${writeError.code}")
                        Log.e(TAG, "  └─ Firestore error name: ${writeError.code.name}")
                    }
                    throw writeError
                }

                // Step 6: Read back from Firestore
                Log.d(TAG, "📖 Step 6: Read from Firestore")
                _uiState.value = _uiState.value.copy(firebaseStatus = "📖 Reading test data...")
                
                val snapshot = docPath.get().await()
                
                if (snapshot.exists()) {
                    Log.d(TAG, "  ✅ Document exists!")
                    Log.d(TAG, "  └─ Document ID: ${snapshot.id}")
                    Log.d(TAG, "  └─ Data: ${snapshot.data}")
                    
                    val readMessage = snapshot.getString("message")
                    val readTimestamp = snapshot.getLong("timestamp")
                    
                    Log.d(TAG, "  └─ Message: $readMessage")
                    Log.d(TAG, "  └─ Timestamp: $readTimestamp")
                    
                    logDebugSection("TEST RESULT: SUCCESS ✅")
                    _uiState.value = _uiState.value.copy(
                        firebaseStatus = "✅ SUCCESS! Data written & read from Firestore\n" +
                                "Path: $fullPath\n" +
                                "Message: $readMessage"
                    )
                } else {
                    Log.w(TAG, "  ⚠️ Document does not exist after write!")
                    Log.w(TAG, "  └─ This means write succeeded but read failed")
                    Log.w(TAG, "  └─ Check Firestore security rules")
                    
                    logDebugSection("TEST RESULT: PARTIAL SUCCESS ⚠️")
                    _uiState.value = _uiState.value.copy(
                        firebaseStatus = "⚠️ Document written but not readable\n" +
                                "Check Firestore security rules"
                    )
                }

            } catch (e: Exception) {
                Log.e(TAG, "❌ FIRESTORE TEST FAILED!", e)
                Log.e(TAG, "  └─ Error type: ${e.javaClass.simpleName}")
                Log.e(TAG, "  └─ Error message: ${e.message}")
                Log.e(TAG, "  └─ Stack trace:")
                e.printStackTrace()
                
                logDebugSection("TEST RESULT: FAILED ❌")
                
                val errorDetails = when {
                    e is com.google.firebase.firestore.FirebaseFirestoreException -> {
                        "Firestore Error [${e.code.name}]: ${e.message}"
                    }
                    e.message?.contains("PERMISSION_DENIED") == true -> {
                        "Permission Denied - Check Firestore rules"
                    }
                    e.message?.contains("NOT_FOUND") == true -> {
                        "Database Not Found - Create Firestore DB in console"
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
