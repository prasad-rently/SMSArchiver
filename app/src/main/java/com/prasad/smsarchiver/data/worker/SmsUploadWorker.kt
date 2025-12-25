package com.prasad.smsarchiver.data.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.prasad.smsarchiver.data.repository.SmsRepository
import com.prasad.smsarchiver.data.local.DatabaseProvider
import com.prasad.smsarchiver.data.local.QueuedSmsEntity
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * WorkManager worker for uploading SMS messages to Firebase Firestore
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
            Log.d(TAG, "Starting SMS upload work")

            // Get the timestamp from input data (0 means get all messages)
            val afterTimestamp = inputData.getLong("timestamp", 0L)

            // Read SMS messages
            val messages = if (afterTimestamp > 0) {
                smsRepository.getMessagesAfter(afterTimestamp)
            } else {
                smsRepository.getAllMessages()
            }

            if (messages.isEmpty()) {
                Log.d(TAG, "No messages to upload")
                return Result.success()
            }

            // Upload to Firestore and handle local queue fallback
            uploadToFirestoreWithQueue(messages)

            Log.d(TAG, "SMS upload completed successfully")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Error uploading SMS: ${e.message}", e)
            Result.retry()
        }
    }

    private suspend fun uploadToFirestoreWithQueue(messages: List<com.prasad.smsarchiver.data.model.SmsMessage>) {
        val auth = FirebaseAuth.getInstance()
        val firestore = FirebaseFirestore.getInstance()
        val dao = DatabaseProvider.get(applicationContext).queuedSmsDao()

        // Get current user (or use anonymous auth)
        val userId = auth.currentUser?.uid ?: run {
            // Sign in anonymously if no user
            auth.signInAnonymously().await()
            auth.currentUser?.uid
        } ?: "anonymous"

        Log.d(TAG, "Starting Firestore upload for user: $userId")

        // First flush any queued items
        try {
            val queued = dao.getAll()
            if (queued.isNotEmpty()) {
                Log.d(TAG, "Found ${queued.size} queued SMS to flush")
                var flushed = 0
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
                            "userId" to userId,
                            "uploadedAt" to System.currentTimeMillis()
                        )
                        firestore.collection("users")
                            .document(userId)
                            .collection("sms")
                            .document(documentId)
                            .set(smsData, SetOptions.merge())
                            .await()
                        dao.deleteById(q.id)
                        flushed++
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to flush queued SMS id=${q.id}: ${e.message}")
                    }
                }
                Log.d(TAG, "Flushed $flushed/${queued.size} queued SMS")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error flushing queue: ${e.message}")
        }

        // Upload each message to Firestore
        var successCount = 0
        var errorCount = 0

        for (message in messages) {
            try {
                // Create document with SMS ID as document ID (prevents duplicates)
                val documentId = "${message.id}_${message.timestamp}"
                
                val smsData = message.toMap().toMutableMap()
                smsData["userId"] = userId
                smsData["uploadedAt"] = System.currentTimeMillis()

                // Upload to: users/{userId}/sms/{documentId}
                firestore.collection("users")
                    .document(userId)
                    .collection("sms")
                    .document(documentId)
                    .set(smsData, SetOptions.merge())
                    .await()

                successCount++
                Log.d(TAG, "Uploaded SMS $successCount/${messages.size}: ID=$documentId")
            } catch (e: Exception) {
                errorCount++
                Log.e(TAG, "Error uploading message ${message.id}: ${e.message}")
                // Fallback: enqueue locally
                try {
                    dao.insert(
                        QueuedSmsEntity(
                            smsId = message.id,
                            threadId = message.threadId,
                            address = message.address,
                            body = message.body,
                            timestamp = message.timestamp,
                            type = message.type,
                            read = message.read
                        )
                    )
                    Log.w(TAG, "Enqueued SMS locally for later upload: id=${message.id}")
                } catch (qe: Exception) {
                    Log.e(TAG, "Failed to enqueue SMS locally: ${qe.message}")
                }
            }
        }

        Log.d(TAG, "Firestore upload completed: $successCount success, $errorCount errors")
    }
}
