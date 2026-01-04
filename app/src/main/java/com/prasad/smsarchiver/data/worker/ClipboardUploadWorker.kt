package com.prasad.smsarchiver.data.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.prasad.smsarchiver.data.local.DatabaseProvider
import com.prasad.smsarchiver.data.local.QueuedClipboardEntity
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * WorkManager worker for uploading clipboard content to Firebase Realtime Database
 */
class ClipboardUploadWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "ClipboardUploadWorker"
    }

    override suspend fun doWork(): Result {
        return try {
            logSection("STARTING CLIPBOARD UPLOAD WORK")

            Log.d(TAG, "📋 Work Request ID: ${this.id}")
            Log.d(TAG, "🔢 Run attempt: ${this.runAttemptCount}")

            val content = inputData.getString("content") ?: return Result.failure()
            val contentHash = inputData.getString("contentHash") ?: return Result.failure()
            val timestamp = inputData.getLong("timestamp", System.currentTimeMillis())

            Log.d(TAG, "📝 Content length: ${content.length}")
            Log.d(TAG, "🔐 Content hash: $contentHash")
            Log.d(TAG, "⏰ Timestamp: ${formatTimestamp(timestamp)}")

            // Upload to Firebase
            uploadToRealtime(content, contentHash, timestamp)

            logSection("CLIPBOARD UPLOAD WORK COMPLETED")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "❌ FATAL: Worker failed: ${e.message}", e)
            Result.retry()
        }
    }

    private suspend fun uploadToRealtime(content: String, contentHash: String, timestamp: Long) {
        logSection("REALTIME DB UPLOAD PROCESS")

        val auth = FirebaseAuth.getInstance()
        val database = FirebaseDatabase.getInstance().reference
        val dao = DatabaseProvider.get(applicationContext).queuedClipboardDao()

        // Step 1: Authentication
        Log.d(TAG, "🔐 Step 1: Authentication")
        val userId = try {
            val currentUser = auth.currentUser
            if (currentUser != null) {
                Log.d(TAG, "  ✅ Already authenticated as: ${currentUser.uid}")
                currentUser.uid
            } else {
                Log.d(TAG, "  🔄 No user found, signing in anonymously...")
                val result = auth.signInAnonymously().await()
                val uid = result.user?.uid
                Log.d(TAG, "  ✅ Anonymous sign-in successful: $uid")
                uid
            }
        } catch (e: Exception) {
            Log.e(TAG, "  ❌ Authentication failed: ${e.message}", e)
            throw e
        } ?: throw Exception("Failed to get user ID after authentication")

        // Step 2: Flush local queue
        logSection("STEP 2: FLUSH LOCAL QUEUE")
        try {
            val queued = dao.getAll()
            if (queued.isEmpty()) {
                Log.d(TAG, "  ✅ Queue is empty, nothing to flush")
            } else {
                Log.d(TAG, "  📤 Found ${queued.size} queued items")
                var flushed = 0
                var failed = 0

                for (q in queued) {
                    try {
                        val documentId = "${q.contentHash}_${q.timestamp}"
                        val clipData = mapOf(
                            "content" to q.content,
                            "timestamp" to q.timestamp,
                            "source" to q.source,
                            "contentHash" to q.contentHash,
                            "userId" to userId,
                            "uploadedAt" to System.currentTimeMillis()
                        )

                        Log.d(TAG, "    🔄 Flushing clipboard ID=${q.clipboardId}")

                        val clipRef = database
                            .child("users")
                            .child(userId)
                            .child("clipboard")
                            .child(documentId)

                        clipRef.setValue(clipData).await()
                        
                        dao.deleteById(q.id)
                        flushed++
                        Log.d(TAG, "      ✅ Flushed successfully")
                    } catch (e: Exception) {
                        failed++
                        Log.e(TAG, "      ❌ Flush failed: ${e.message}")
                    }
                }
                
                Log.d(TAG, "  📊 Flush complete: $flushed succeeded, $failed failed")
            }
        } catch (e: Exception) {
            Log.e(TAG, "  ❌ Error accessing queue: ${e.message}", e)
        }

        // Step 3: Upload new clipboard item
        logSection("STEP 3: UPLOAD NEW CLIPBOARD")
        try {
            val documentId = "${contentHash}_$timestamp"
            
            Log.d(TAG, "  📨 Uploading clipboard item")
            Log.d(TAG, "      Document ID: $documentId")
            Log.d(TAG, "      Content preview: ${content.take(100)}${if (content.length > 100) "..." else ""}")
            Log.d(TAG, "      Time: ${formatTimestamp(timestamp)}")
            
            val clipData = mapOf(
                "content" to content,
                "timestamp" to timestamp,
                "contentHash" to contentHash,
                "userId" to userId,
                "uploadedAt" to System.currentTimeMillis()
            )

            val clipRef = database
                .child("users")
                .child(userId)
                .child("clipboard")
                .child(documentId)

            clipRef.setValue(clipData).await()

            Log.d(TAG, "      ✅ Upload successful")
            
        } catch (e: Exception) {
            val errorMsg = e.message ?: "No error message"
            Log.e(TAG, "      ❌ Upload failed: $errorMsg")
            
            // Queue for retry
            try {
                dao.insert(
                    QueuedClipboardEntity(
                        clipboardId = contentHash,
                        content = content,
                        timestamp = timestamp,
                        source = null,
                        contentHash = contentHash
                    )
                )
                Log.w(TAG, "      🔄 Queued locally for retry")
            } catch (qe: Exception) {
                Log.e(TAG, "      ⚠️ Failed to queue: ${qe.message}", qe)
            }
            throw e
        }
    }

    private fun logSection(title: String) {
        Log.d(TAG, "")
        Log.d(TAG, "═══════════════════════════════════════")
        Log.d(TAG, " $title")
        Log.d(TAG, "═══════════════════════════════════════")
    }

    private fun formatTimestamp(timestamp: Long): String {
        if (timestamp == 0L) return "N/A"
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        return dateFormat.format(Date(timestamp))
    }
}
