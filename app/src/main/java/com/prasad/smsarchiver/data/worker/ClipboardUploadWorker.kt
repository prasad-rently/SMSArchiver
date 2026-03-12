package com.prasad.smsarchiver.data.worker

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.storage.FirebaseStorage
import com.prasad.smsarchiver.data.local.DatabaseProvider
import com.prasad.smsarchiver.data.local.QueuedClipboardEntity
import com.prasad.smsarchiver.service.ClipboardAccessibilityService
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * WorkManager worker for uploading clipboard content (text or image) to Firebase.
 *
 * Text  → Firebase Realtime Database directly
 * Image → Firebase Storage (bytes) → Realtime Database (download URL)
 */
class ClipboardUploadWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "ClipboardUploadWorker"
        private const val MAX_IMAGE_BYTES = 10 * 1024 * 1024 // 10 MB guard
    }

    override suspend fun doWork(): Result {
        return try {
            logSection("STARTING CLIPBOARD UPLOAD WORK")
            Log.d(TAG, "📋 Work Request ID: ${this.id}")
            Log.d(TAG, "🔢 Run attempt: ${this.runAttemptCount}")

            val contentType = inputData.getString("contentType")
                ?: ClipboardAccessibilityService.CONTENT_TYPE_TEXT
            val contentHash = inputData.getString("contentHash") ?: return Result.failure()
            val timestamp = inputData.getLong("timestamp", System.currentTimeMillis())

            Log.d(TAG, "📌 Content type: $contentType")
            Log.d(TAG, "🔐 Content hash: $contentHash")
            Log.d(TAG, "⏰ Timestamp: ${formatTimestamp(timestamp)}")

            uploadToFirebase(contentType, contentHash, timestamp)

            logSection("CLIPBOARD UPLOAD WORK COMPLETED")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "❌ FATAL: Worker failed: ${e.message}", e)
            Result.retry()
        }
    }

    private suspend fun uploadToFirebase(contentType: String, contentHash: String, timestamp: Long) {
        logSection("FIREBASE UPLOAD PROCESS")

        val auth = FirebaseAuth.getInstance()
        val database = FirebaseDatabase.getInstance().reference
        val dao = DatabaseProvider.get(applicationContext).queuedClipboardDao()

        // --- Step 1: Authentication ---
        Log.d(TAG, "🔐 Step 1: Authentication")
        val userId = try {
            val current = auth.currentUser
            if (current != null) {
                Log.d(TAG, "  ✅ Authenticated: ${current.uid}")
                current.uid
            } else {
                Log.d(TAG, "  🔄 Signing in anonymously...")
                val result = auth.signInAnonymously().await()
                Log.d(TAG, "  ✅ Anonymous sign-in: ${result.user?.uid}")
                result.user?.uid
            }
        } catch (e: Exception) {
            Log.e(TAG, "  ❌ Auth failed: ${e.message}", e)
            throw e
        } ?: throw Exception("Failed to get user ID")

        // --- Step 2: Flush local queue ---
        logSection("STEP 2: FLUSH LOCAL QUEUE")
        flushQueue(dao, database, userId)

        // --- Step 3: Upload new item ---
        logSection("STEP 3: UPLOAD NEW CLIPBOARD ITEM")
        val documentId = "${contentHash}_$timestamp"

        when (contentType) {
            ClipboardAccessibilityService.CONTENT_TYPE_IMAGE -> {
                uploadImage(dao, database, userId, documentId, contentHash, timestamp)
            }
            else -> {
                val content = inputData.getString("content") ?: run {
                    Log.e(TAG, "  ❌ No content for text upload")
                    return
                }
                uploadText(dao, database, userId, documentId, content, contentHash, timestamp)
            }
        }
    }

    private suspend fun flushQueue(
        dao: com.prasad.smsarchiver.data.local.QueuedClipboardDao,
        database: com.google.firebase.database.DatabaseReference,
        userId: String
    ) {
        val queued = dao.getAll()
        if (queued.isEmpty()) {
            Log.d(TAG, "  ✅ Queue empty")
            return
        }
        Log.d(TAG, "  📤 Flushing ${queued.size} queued items")
        var flushed = 0
        var failed = 0

        for (q in queued) {
            try {
                val documentId = "${q.contentHash}_${q.timestamp}"
                val clipRef = database.child("users").child(userId).child("clipboard").child(documentId)

                if (q.contentType == ClipboardAccessibilityService.CONTENT_TYPE_IMAGE && q.imageUri != null) {
                    // Re-upload image from stored URI
                    val imageUrl = uploadImageToStorage(userId, q.contentHash, Uri.parse(q.imageUri))
                    if (imageUrl != null) {
                        val data = mapOf(
                            "content" to "",
                            "imageUrl" to imageUrl,
                            "contentType" to "image",
                            "timestamp" to q.timestamp,
                            "contentHash" to q.contentHash,
                            "userId" to userId,
                            "uploadedAt" to System.currentTimeMillis()
                        )
                        clipRef.setValue(data).await()
                        dao.deleteById(q.id)
                        flushed++
                    } else {
                        failed++
                    }
                } else {
                    // Text item
                    val data = mapOf(
                        "content" to q.content,
                        "contentType" to "text",
                        "timestamp" to q.timestamp,
                        "source" to q.source,
                        "contentHash" to q.contentHash,
                        "userId" to userId,
                        "uploadedAt" to System.currentTimeMillis()
                    )
                    clipRef.setValue(data).await()
                    dao.deleteById(q.id)
                    flushed++
                }
            } catch (e: Exception) {
                failed++
                Log.e(TAG, "    ❌ Flush failed for ${q.clipboardId}: ${e.message}")
            }
        }
        Log.d(TAG, "  📊 Flush done: $flushed ok, $failed failed")
    }

    private suspend fun uploadText(
        dao: com.prasad.smsarchiver.data.local.QueuedClipboardDao,
        database: com.google.firebase.database.DatabaseReference,
        userId: String,
        documentId: String,
        content: String,
        contentHash: String,
        timestamp: Long
    ) {
        try {
            Log.d(TAG, "  📝 Uploading text (${content.length} chars)")
            val data = mapOf(
                "content" to content,
                "contentType" to "text",
                "timestamp" to timestamp,
                "contentHash" to contentHash,
                "userId" to userId,
                "uploadedAt" to System.currentTimeMillis()
            )
            database.child("users").child(userId).child("clipboard").child(documentId)
                .setValue(data).await()
            Log.d(TAG, "  ✅ Text upload successful")
        } catch (e: Exception) {
            Log.e(TAG, "  ❌ Text upload failed: ${e.message}")
            queueForRetry(dao, contentHash, content, null, "text", null, timestamp)
            throw e
        }
    }

    private suspend fun uploadImage(
        dao: com.prasad.smsarchiver.data.local.QueuedClipboardDao,
        database: com.google.firebase.database.DatabaseReference,
        userId: String,
        documentId: String,
        contentHash: String,
        timestamp: Long
    ) {
        val imageUriString = inputData.getString("imageUri") ?: run {
            Log.e(TAG, "  ❌ No imageUri in input data")
            return
        }
        val imageUri = Uri.parse(imageUriString)
        Log.d(TAG, "  🖼️ Uploading image: $imageUriString")

        try {
            val downloadUrl = uploadImageToStorage(userId, contentHash, imageUri)
            if (downloadUrl == null) {
                Log.e(TAG, "  ❌ Image Storage upload failed — queuing for retry")
                queueForRetry(dao, contentHash, "", imageUriString, "image", imageUriString, timestamp)
                return
            }

            val data = mapOf(
                "content" to "",
                "imageUrl" to downloadUrl,
                "contentType" to "image",
                "timestamp" to timestamp,
                "contentHash" to contentHash,
                "userId" to userId,
                "uploadedAt" to System.currentTimeMillis()
            )
            database.child("users").child(userId).child("clipboard").child(documentId)
                .setValue(data).await()
            Log.d(TAG, "  ✅ Image upload successful → $downloadUrl")
        } catch (e: Exception) {
            Log.e(TAG, "  ❌ Image upload failed: ${e.message}")
            queueForRetry(dao, contentHash, "", imageUriString, "image", imageUriString, timestamp)
            throw e
        }
    }

    /**
     * Upload image bytes to Firebase Storage and return the download URL, or null on failure.
     */
    private suspend fun uploadImageToStorage(userId: String, contentHash: String, imageUri: Uri): String? {
        return try {
            val inputStream = applicationContext.contentResolver.openInputStream(imageUri)
                ?: return null
            val bytes = inputStream.readBytes()
            inputStream.close()

            if (bytes.size > MAX_IMAGE_BYTES) {
                Log.w(TAG, "  ⚠️ Image too large (${bytes.size} bytes), skipping")
                return null
            }

            Log.d(TAG, "  📤 Uploading ${bytes.size} bytes to Firebase Storage")
            val storageRef = FirebaseStorage.getInstance().reference
                .child("clipboard-images")
                .child(userId)
                .child(contentHash)

            storageRef.putBytes(bytes).await()
            val downloadUrl = storageRef.downloadUrl.await().toString()
            Log.d(TAG, "  ✅ Storage upload done: $downloadUrl")
            downloadUrl
        } catch (e: Exception) {
            Log.e(TAG, "  ❌ Storage upload error: ${e.message}", e)
            null
        }
    }

    private suspend fun queueForRetry(
        dao: com.prasad.smsarchiver.data.local.QueuedClipboardDao,
        contentHash: String,
        content: String,
        clipboardId: String?,
        contentType: String,
        imageUri: String?,
        timestamp: Long
    ) {
        try {
            dao.insert(
                QueuedClipboardEntity(
                    clipboardId = clipboardId ?: contentHash,
                    content = content,
                    timestamp = timestamp,
                    source = null,
                    contentHash = contentHash,
                    contentType = contentType,
                    imageUri = imageUri
                )
            )
            Log.w(TAG, "  🔄 Queued locally for retry")
        } catch (qe: Exception) {
            Log.e(TAG, "  ⚠️ Failed to queue: ${qe.message}", qe)
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
        return SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(timestamp))
    }
}
