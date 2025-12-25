package com.prasad.smsarchiver.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.prasad.smsarchiver.data.worker.SmsUploadWorker

/**
 * BroadcastReceiver that listens for incoming SMS messages
 */
class SmsReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "SmsReceiver"
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        Log.d(TAG, "onReceive called with action: ${intent?.action}")
        
        if (context == null || intent == null) {
            Log.e(TAG, "Context or Intent is null")
            return
        }

        if (intent.action == Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            Log.d(TAG, "SMS_RECEIVED_ACTION matched!")
            
            try {
                val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
                Log.d(TAG, "Received ${messages.size} SMS messages")
                
                for (smsMessage in messages) {
                    val sender = smsMessage.displayOriginatingAddress
                    val messageBody = smsMessage.messageBody
                    val timestamp = smsMessage.timestampMillis

                    Log.d(TAG, "========== NEW SMS ==========")
                    Log.d(TAG, "From: $sender")
                    Log.d(TAG, "Message: $messageBody")
                    Log.d(TAG, "Timestamp: $timestamp")
                    Log.d(TAG, "=============================")

                    // Notify UI that new SMS arrived
                    notifyUiOfNewSms(context)

                    // Trigger background work to read and upload SMS
                    scheduleUploadWork(context, timestamp)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error processing SMS: ${e.message}", e)
            }
        } else {
            Log.w(TAG, "Received unknown action: ${intent.action}")
        }
    }

    /**
     * Send local broadcast to notify UI of new SMS
     */
    private fun notifyUiOfNewSms(context: Context) {
        val intent = Intent("com.prasad.smsarchiver.NEW_SMS")
        context.sendBroadcast(intent)
        Log.d(TAG, "📢 Sent NEW_SMS broadcast to UI")
        Log.d(TAG, "📢 Broadcast action: com.prasad.smsarchiver.NEW_SMS")
    }

    /**
     * Schedule WorkManager task to read and upload SMS messages
     */
    private fun scheduleUploadWork(context: Context, timestamp: Long) {
        val uploadWorkRequest = OneTimeWorkRequestBuilder<SmsUploadWorker>()
            .setInputData(
                workDataOf(
                    "timestamp" to timestamp
                )
            )
            .build()

        WorkManager.getInstance(context).enqueue(uploadWorkRequest)
        Log.d(TAG, "Scheduled SMS upload work")
    }
}
