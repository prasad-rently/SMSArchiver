package com.prasad.smsarchiver.data.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.storage.FirebaseStorage
import com.google.gson.Gson
import com.prasad.smsarchiver.data.repository.SmsRepository
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * WorkManager worker for uploading SMS messages to Firebase Cloud Storage
 */
class SmsUploadWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "SmsUploadWorker"
    }

    private val smsRepository = SmsRepository(applicationContext)
    private val gson = Gson()

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

            // Upload to Firebase
            uploadToFirebase(messages)

            Log.d(TAG, "SMS upload completed successfully")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Error uploading SMS: ${e.message}", e)
            Result.retry()
        }
    }

    private suspend fun uploadToFirebase(messages: List<com.prasad.smsarchiver.data.model.SmsMessage>) {
        val auth = FirebaseAuth.getInstance()
        val storage = FirebaseStorage.getInstance()

        // Get current user (or use anonymous auth)
        val userId = auth.currentUser?.uid ?: run {
            // Sign in anonymously if no user
            auth.signInAnonymously().await()
            auth.currentUser?.uid
        } ?: "anonymous"

        // Create filename with timestamp
        val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
        val timestamp = dateFormat.format(Date())
        val filename = "sms_backup_${timestamp}_${messages.size}.json"

        // Convert messages to JSON
        val messagesMap = messages.map { it.toMap() }
        val jsonData = gson.toJson(messagesMap)

        // Upload to Firebase Storage
        val storageRef = storage.reference
        val smsRef = storageRef.child("users/$userId/sms/$filename")

        smsRef.putBytes(jsonData.toByteArray()).await()
        Log.d(TAG, "Uploaded ${messages.size} messages to Firebase: $filename")
    }
}
