package com.prasad.smsarchiver.data.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.prasad.smsarchiver.data.repository.SmsRepository
import com.prasad.smsarchiver.data.local.DatabaseProvider
import com.prasad.smsarchiver.data.local.QueuedSmsEntity
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * WorkManager worker for uploading SMS messages to Firebase Realtime Database
 * Enhanced with comprehensive logging and dual SIM support
 */
class SmsUploadWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "SmsUploadWorker"
    }

    private val smsRepository = SmsRepository(applicationContext)

    override suspend fun doWork(): Result {
        return try {
            logSection("STARTING SMS UPLOAD WORK")
            
                // Log work request details
                Log.d(TAG, "📋 Work Request ID: ${this.id}")
                Log.d(TAG, "🔢 Run attempt: ${this.runAttemptCount}")
                Log.d(TAG, "📱 Device: ${android.os.Build.MODEL} (Android ${android.os.Build.VERSION.SDK_INT})")

            val afterTimestamp = inputData.getLong("timestamp", 0L)
                if (afterTimestamp == 0L) {
                    Log.d(TAG, "📌 Timestamp filter: 0 (UPLOADING ALL SMS FROM DEVICE)")
                } else {
                    Log.d(TAG, "📌 Timestamp filter: $afterTimestamp (${formatTimestamp(afterTimestamp)})")
                    Log.d(TAG, "   └─ Uploading SMS newer than or equal to this time")
                }

            // Give the platform a brief moment to persist the SMS to the provider
            // (avoids a race where the broadcast arrives before the row is visible)
            kotlinx.coroutines.delay(1200)

            // Read SMS messages
            val messages = if (afterTimestamp > 0) {
                smsRepository.getMessagesAfter(afterTimestamp)
            } else {
                smsRepository.getAllMessages()
            }

            if (messages.isEmpty()) {
                Log.d(TAG, "✅ No messages to upload")
                return Result.success()
            }

            Log.d(TAG, "📨 Found ${messages.size} SMS messages to process")
            
            // Log message details for debugging
            messages.forEachIndexed { index, msg ->
                Log.d(TAG, "  SMS [${index + 1}]: ID=${msg.id}, From=${msg.address}, Time=${formatTimestamp(msg.timestamp)}")
            }
            
            // Log SIM distribution
            val simCounts = messages.groupingBy { it.subscriptionId }.eachCount()
            simCounts.forEach { (subId, count) ->
                val simLabel = when (subId) {
                    -1 -> "Unknown SIM"
                    else -> "SIM $subId"
                }
                Log.d(TAG, "  └─ $simLabel: $count messages")
            }

            // Upload to Realtime Database
            uploadToRealtimeWithQueue(messages)

            logSection("SMS UPLOAD WORK COMPLETED")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "❌ FATAL: Worker failed: ${e.message}", e)
            Result.retry()
        }
    }

    private suspend fun uploadToRealtimeWithQueue(messages: List<com.prasad.smsarchiver.data.model.SmsMessage>) {
        logSection("REALTIME DB UPLOAD PROCESS")

        val auth = FirebaseAuth.getInstance()
        val database = FirebaseDatabase.getInstance().reference
        val dao = DatabaseProvider.get(applicationContext).queuedSmsDao()

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
                Log.d(TAG, "  📤 Found ${queued.size} queued messages")
                var flushed = 0
                var failed = 0

                for (q in queued) {
                    try {
                        val documentId = "${q.smsId}_${q.timestamp}"
                        val smsData = mapOf(
                            "address" to q.address,
                            "body" to q.body,
                            "timestamp" to q.timestamp,
                            "type" to q.type,
                            "read" to q.read,
                            "threadId" to q.threadId,
                            "subscriptionId" to q.subscriptionId,
                            "userId" to userId,
                            "uploadedAt" to System.currentTimeMillis(),
                            "source" to "queue"
                        )

                        Log.d(TAG, "    🔄 Flushing SMS ID=${q.smsId} (SIM ${q.subscriptionId})")

                        val smsRef = database
                            .child("users")
                            .child(userId)
                            .child("sms")
                            .child(documentId)

                        smsRef.setValue(smsData).await()
                        
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

        // Step 3: Upload new messages
        logSection("STEP 3: UPLOAD NEW MESSAGES")
        var successCount = 0
        var errorCount = 0
        var queuedCount = 0

        for ((index, message) in messages.withIndex()) {
            try {
                // Create unique document ID: SMS_ID + timestamp
                // This prevents duplicates - same SMS will always have same documentId
                // If uploaded multiple times, setValue() will overwrite with identical data
                val documentId = "${message.id}_${message.timestamp}"
                val simLabel = if (message.subscriptionId >= 0) "SIM ${message.subscriptionId}" else "Unknown SIM"
                
                Log.d(TAG, "  📨 [${index + 1}/${messages.size}] Uploading SMS ID=${message.id} ($simLabel)")
                Log.d(TAG, "      Document ID: $documentId")
                Log.d(TAG, "      From: ${message.address}")
                Log.d(TAG, "      Preview: ${message.body.take(50)}...")
                Log.d(TAG, "      Time: ${formatTimestamp(message.timestamp)}")
                
                val smsData = message.toMap().toMutableMap()
                smsData["userId"] = userId
                smsData["uploadedAt"] = System.currentTimeMillis()
                smsData["source"] = "direct"

                val smsRef = database
                    .child("users")
                    .child(userId)
                    .child("sms")
                    .child(documentId)

                smsRef.setValue(smsData).await()

                successCount++
                Log.d(TAG, "      ✅ Upload successful")
                
            } catch (e: Exception) {
                errorCount++
                val errorMsg = e.message ?: "No error message"

                Log.e(TAG, "      ❌ Upload failed: $errorMsg")
                
                // Queue for retry
                try {
                    dao.insert(
                        QueuedSmsEntity(
                            smsId = message.id,
                            threadId = message.threadId,
                            address = message.address,
                            body = message.body,
                            timestamp = message.timestamp,
                            type = message.type,
                            read = message.read,
                            subscriptionId = message.subscriptionId
                        )
                    )
                    queuedCount++
                    Log.w(TAG, "      🔄 Queued locally for retry")
                } catch (qe: Exception) {
                    Log.e(TAG, "      ⚠️ Failed to queue: ${qe.message}", qe)
                }
            }
        }

        // Final summary
        logSection("UPLOAD SUMMARY")
        Log.d(TAG, "  ✅ Successful uploads: $successCount")
        Log.d(TAG, "  ❌ Failed uploads: $errorCount")
        Log.d(TAG, "  🔄 Queued for retry: $queuedCount")
        Log.d(TAG, "  📊 Total processed: ${messages.size}")
        
        if (errorCount > 0) {
            Log.w(TAG, "  ⚠️ Some messages failed to upload. Check Realtime Database configuration:")
            Log.w(TAG, "     - Is Realtime Database enabled in Firebase Console?")
            Log.w(TAG, "     - Are security rules configured correctly?")
            Log.w(TAG, "     - Is anonymous authentication enabled?")
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
