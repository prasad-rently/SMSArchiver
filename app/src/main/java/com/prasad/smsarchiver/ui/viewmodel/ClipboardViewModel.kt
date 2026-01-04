package com.prasad.smsarchiver.ui.viewmodel

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
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
import com.prasad.smsarchiver.service.ClipboardMonitorService
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
        // Authenticate, load initial snapshot, and attach listener once
        viewModelScope.launch {
            ensureAuthenticated()
            refreshClipboard()
            attachClipboardListener()
        }
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
     * Useful when the listener was not attached yet or after app relaunch.
     */
    fun refreshClipboard() {
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(isLoading = true)

                val auth = FirebaseAuth.getInstance()
                val userId = auth.currentUser?.uid

                if (userId == null) {
                    Log.w(TAG, "User not authenticated, cannot refresh clipboard")
                    _uiState.value = _uiState.value.copy(isLoading = false)
                    return@launch
                }

                val database = FirebaseDatabase.getInstance().reference
                val clipboardRef = database.child("users").child(userId).child("clipboard")

                val snapshot = clipboardRef
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

                Log.d(TAG, "Refreshed clipboard: loaded ${items.size} items")
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
            val auth = FirebaseAuth.getInstance()
            val userId = auth.currentUser?.uid

            if (userId == null) {
                Log.w(TAG, "User not authenticated, cannot listen to clipboard")
                return
            }

            val database = FirebaseDatabase.getInstance().reference
            val clipboardRef = database.child("users").child(userId).child("clipboard")

            clipboardListener = object : ChildEventListener {
                override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                    val item = parseClipboardItem(snapshot)
                    if (item != null) {
                        addClipboardItem(item)
                    }
                }

                override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {
                    val item = parseClipboardItem(snapshot)
                    if (item != null) {
                        updateClipboardItem(item)
                    }
                }

                override fun onChildRemoved(snapshot: DataSnapshot) {
                    val id = snapshot.key
                    if (id != null) {
                        removeClipboardItem(id)
                    }
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
            val content = snapshot.child("content").getValue(String::class.java) ?: return null
            val timestamp = snapshot.child("timestamp").getValue(Long::class.java) ?: 0L
            val contentHash = snapshot.child("contentHash").getValue(String::class.java) ?: ""
            val source = snapshot.child("source").getValue(String::class.java)

            ClipboardItem(
                id = id,
                content = content,
                timestamp = timestamp,
                source = source,
                contentHash = contentHash,
                isSynced = true
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing clipboard item: ${e.message}")
            null
        }
    }

    private fun addClipboardItem(item: ClipboardItem) {
        val currentItems = _uiState.value.items.toMutableList()
        
        // Check for duplicate by content hash
        if (currentItems.any { it.contentHash == item.contentHash }) {
            Log.d(TAG, "Duplicate clipboard item, skipping")
            return
        }
        
        currentItems.add(0, item) // Add to top
        _uiState.value = _uiState.value.copy(
            items = currentItems,
            totalCount = currentItems.size
        )
        Log.d(TAG, "Added clipboard item: ${item.id}")
    }

    private fun updateClipboardItem(item: ClipboardItem) {
        val currentItems = _uiState.value.items.toMutableList()
        val index = currentItems.indexOfFirst { it.id == item.id }
        if (index != -1) {
            currentItems[index] = item
            _uiState.value = _uiState.value.copy(items = currentItems)
            Log.d(TAG, "Updated clipboard item: ${item.id}")
        }
    }

    private fun removeClipboardItem(id: String) {
        val currentItems = _uiState.value.items.toMutableList()
        currentItems.removeAll { it.id == id }
        _uiState.value = _uiState.value.copy(
            items = currentItems,
            totalCount = currentItems.size
        )
        Log.d(TAG, "Removed clipboard item: $id")
    }

    /**
     * Copy clipboard item to system clipboard
     */
    fun copyToClipboard(item: ClipboardItem) {
        try {
            val clip = ClipData.newPlainText("clipboard", item.content)
            clipboardManager.setPrimaryClip(clip)
            
            Toast.makeText(
                getApplication(),
                "Copied to clipboard",
                Toast.LENGTH_SHORT
            ).show()
            
            Log.d(TAG, "Copied to clipboard: ${item.content.take(50)}")
        } catch (e: Exception) {
            Log.e(TAG, "Error copying to clipboard: ${e.message}", e)
            Toast.makeText(
                getApplication(),
                "Failed to copy",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    /**
     * Start clipboard monitoring service
     */
    fun startMonitoring() {
        try {
            ClipboardMonitorService.startService(getApplication())
            _uiState.value = _uiState.value.copy(isMonitoring = true)
            Log.d(TAG, "Started clipboard monitoring service")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting monitoring: ${e.message}", e)
        }
    }

    /**
     * Stop clipboard monitoring service
     */
    fun stopMonitoring() {
        try {
            ClipboardMonitorService.stopService(getApplication())
            _uiState.value = _uiState.value.copy(isMonitoring = false)
            Log.d(TAG, "Stopped clipboard monitoring service")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping monitoring: ${e.message}", e)
        }
    }

    override fun onCleared() {
        super.onCleared()
        clipboardListener?.let { listener ->
            try {
                val auth = FirebaseAuth.getInstance()
                val userId = auth.currentUser?.uid
                if (userId != null) {
                    val database = FirebaseDatabase.getInstance().reference
                    database.child("users").child(userId).child("clipboard")
                        .removeEventListener(listener)
                } else {
                    Log.w(TAG, "Cannot remove listener: user not authenticated")
                }
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
    val error: String? = null
)
