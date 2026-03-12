package com.prasad.smsarchiver.ui.viewmodel

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.ChildEventListener
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.prasad.smsarchiver.data.model.ClipboardItem
import com.prasad.smsarchiver.service.ClipboardAccessibilityService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * ViewModel for managing clipboard history and operations
 */
class ClipboardViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "ClipboardViewModel"
    }

    private val _uiState = MutableStateFlow(ClipboardUiState())
    val uiState: StateFlow<ClipboardUiState> = _uiState.asStateFlow()

    private var clipboardListener: ChildEventListener? = null
    private var isListening: Boolean = false
    private val clipboardManager = application.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

    init {
        viewModelScope.launch {
            ensureAuthenticated()
            checkAccessibilityStatus()
            refreshClipboard()
            attachClipboardListener()
        }
    }

    /**
     * Check and update accessibility service status in UI state.
     */
    fun checkAccessibilityStatus() {
        val enabled = ClipboardAccessibilityService.isEnabled(getApplication())
        _uiState.value = _uiState.value.copy(
            isAccessibilityEnabled = enabled,
            isMonitoring = enabled
        )
        Log.d(TAG, "Accessibility service enabled: $enabled")
    }

    /**
     * Open Android Accessibility Settings so user can enable the service.
     */
    fun openAccessibilitySettings(context: Context) {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }

    /**
     * Ensure user is authenticated before accessing Firebase
     */
    private suspend fun ensureAuthenticated() {
        try {
            val auth = FirebaseAuth.getInstance()
            if (auth.currentUser == null) {
                Log.d(TAG, "No user found, signing in anonymously...")
                auth.signInAnonymously().await()
                Log.d(TAG, "Signed in anonymously: ${auth.currentUser?.uid}")
            } else {
                Log.d(TAG, "Already authenticated: ${auth.currentUser?.uid}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Authentication failed: ${e.message}", e)
        }
    }

    /**
     * Explicit refresh to pull the latest 100 clipboard entries from Firebase.
     */
    fun refreshClipboard() {
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(isLoading = true)

                val userId = FirebaseAuth.getInstance().currentUser?.uid
                if (userId == null) {
                    Log.w(TAG, "User not authenticated, cannot refresh clipboard")
                    _uiState.value = _uiState.value.copy(isLoading = false)
                    return@launch
                }

                val snapshot = FirebaseDatabase.getInstance().reference
                    .child("users").child(userId).child("clipboard")
                    .limitToLast(100)
                    .get()
                    .await()

                val items = snapshot.children
                    .mapNotNull { parseClipboardItem(it) }
                    .sortedByDescending { it.timestamp }

                _uiState.value = _uiState.value.copy(
                    items = items,
                    totalCount = items.size,
                    isLoading = false
                )
                Log.d(TAG, "Refreshed clipboard: ${items.size} items")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to refresh clipboard: ${e.message}", e)
                _uiState.value = _uiState.value.copy(isLoading = false)
            }
        }
    }

    /**
     * Attach realtime listener once per process to stream new clipboard entries.
     */
    private suspend fun attachClipboardListener() {
        if (isListening) return

        try {
            val userId = FirebaseAuth.getInstance().currentUser?.uid
            if (userId == null) {
                Log.w(TAG, "User not authenticated, cannot listen to clipboard")
                return
            }

            val clipboardRef = FirebaseDatabase.getInstance().reference
                .child("users").child(userId).child("clipboard")

            clipboardListener = object : ChildEventListener {
                override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                    parseClipboardItem(snapshot)?.let { addClipboardItem(it) }
                }

                override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {
                    parseClipboardItem(snapshot)?.let { updateClipboardItem(it) }
                }

                override fun onChildRemoved(snapshot: DataSnapshot) {
                    snapshot.key?.let { removeClipboardItem(it) }
                }

                override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) {}

                override fun onCancelled(error: DatabaseError) {
                    Log.e(TAG, "Firebase listener cancelled: ${error.message}")
                }
            }

            clipboardRef.addChildEventListener(clipboardListener!!)
            isListening = true
            Log.d(TAG, "Started realtime clipboard listener")
        } catch (e: Exception) {
            Log.e(TAG, "Error attaching clipboard listener: ${e.message}", e)
        }
    }

    private fun parseClipboardItem(snapshot: DataSnapshot): ClipboardItem? {
        return try {
            val id = snapshot.key ?: return null
            val contentType = snapshot.child("contentType").getValue(String::class.java) ?: "text"
            val content = snapshot.child("content").getValue(String::class.java) ?: ""
            val timestamp = snapshot.child("timestamp").getValue(Long::class.java) ?: 0L
            val contentHash = snapshot.child("contentHash").getValue(String::class.java) ?: ""
            val source = snapshot.child("source").getValue(String::class.java)
            val imageUrl = snapshot.child("imageUrl").getValue(String::class.java)

            ClipboardItem(
                id = id,
                content = content,
                timestamp = timestamp,
                source = source,
                contentHash = contentHash,
                isSynced = true,
                contentType = contentType,
                imageUrl = imageUrl
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing clipboard item: ${e.message}")
            null
        }
    }

    private fun addClipboardItem(item: ClipboardItem) {
        val current = _uiState.value.items.toMutableList()
        if (current.any { it.contentHash == item.contentHash }) return
        current.add(0, item)
        _uiState.value = _uiState.value.copy(items = current, totalCount = current.size)
        Log.d(TAG, "Added clipboard item: ${item.id}")
    }

    private fun updateClipboardItem(item: ClipboardItem) {
        val current = _uiState.value.items.toMutableList()
        val idx = current.indexOfFirst { it.id == item.id }
        if (idx != -1) {
            current[idx] = item
            _uiState.value = _uiState.value.copy(items = current)
        }
    }

    private fun removeClipboardItem(id: String) {
        val current = _uiState.value.items.toMutableList()
        current.removeAll { it.id == id }
        _uiState.value = _uiState.value.copy(items = current, totalCount = current.size)
    }

    /**
     * Copy a text clipboard item back to the system clipboard.
     */
    fun copyToClipboard(item: ClipboardItem) {
        if (item.contentType == "image") {
            Toast.makeText(getApplication(), "Image copied — open the URL to view", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            clipboardManager.setPrimaryClip(ClipData.newPlainText("clipboard", item.content))
            Toast.makeText(getApplication(), "Copied to clipboard", Toast.LENGTH_SHORT).show()
            Log.d(TAG, "Copied to clipboard: ${item.content.take(50)}")
        } catch (e: Exception) {
            Log.e(TAG, "Error copying to clipboard: ${e.message}", e)
            Toast.makeText(getApplication(), "Failed to copy", Toast.LENGTH_SHORT).show()
        }
    }

    // Kept for backward-compat UI callers; accessibility service is user-managed via settings.
    fun startMonitoring() {
        checkAccessibilityStatus()
    }

    fun stopMonitoring() {
        checkAccessibilityStatus()
    }

    override fun onCleared() {
        super.onCleared()
        clipboardListener?.let { listener ->
            try {
                val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return@let
                FirebaseDatabase.getInstance().reference
                    .child("users").child(userId).child("clipboard")
                    .removeEventListener(listener)
            } catch (e: Exception) {
                Log.e(TAG, "Error removing listener: ${e.message}")
            }
        }
    }
}

/**
 * UI state for clipboard screen
 */
data class ClipboardUiState(
    val items: List<ClipboardItem> = emptyList(),
    val totalCount: Int = 0,
    val isMonitoring: Boolean = false,
    val isLoading: Boolean = false,
    val error: String? = null,
    val isAccessibilityEnabled: Boolean = false
)
