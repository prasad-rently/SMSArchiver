package com.prasad.smsarchiver.service

import android.app.Service
import android.content.ClipboardManager
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.BackoffPolicy
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.prasad.smsarchiver.data.worker.ClipboardUploadWorker
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * Foreground service to monitor clipboard changes
 */
class ClipboardMonitorService : Service() {

    companion object {
        private const val TAG = "ClipboardMonitor"
        private const val NOTIFICATION_ID = 1002
        
        fun startService(context: Context) {
            val intent = Intent(context, ClipboardMonitorService::class.java)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            val intent = Intent(context, ClipboardMonitorService::class.java)
            context.stopService(intent)
        }
    }

    private var clipboardManager: ClipboardManager? = null
    private var lastClipContent: String = ""
    private var lastContentHash: String = ""

    private val clipboardListener = ClipboardManager.OnPrimaryClipChangedListener {
        Log.d(TAG, "Clipboard change detected!")
        handleClipboardChange()
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service created")
        
        clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        clipboardManager?.addPrimaryClipChangedListener(clipboardListener)
        Log.d(TAG, "Clipboard listener registered")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Service started")
        
        val notification = createNotification()
        startForeground(NOTIFICATION_ID, notification)

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        clipboardManager?.removePrimaryClipChangedListener(clipboardListener)
        Log.d(TAG, "Service destroyed")
    }

    private fun handleClipboardChange() {
        try {
            val primaryClip = clipboardManager?.primaryClip
            if (primaryClip == null || primaryClip.itemCount == 0) {
                Log.d(TAG, "No clipboard content")
                return
            }

            val item: ClipData.Item = primaryClip.getItemAt(0)
            val text = item.text?.toString() ?: return
            
            // Ignore empty or very short content
            if (text.isBlank() || text.length < 2) {
                Log.d(TAG, "Ignoring empty/short content")
                return
            }

            val contentHash = generateHash(text)
            
            // Check for duplicate
            if (contentHash == lastContentHash) {
                Log.d(TAG, "Duplicate clipboard content, ignoring")
                return
            }

            lastClipContent = text
            lastContentHash = contentHash

            Log.d(TAG, "========== NEW CLIPBOARD ==========")
            Log.d(TAG, "Content: ${text.take(100)}${if (text.length > 100) "..." else ""}")
            Log.d(TAG, "Length: ${text.length}")
            Log.d(TAG, "Hash: $contentHash")
            Log.d(TAG, "==================================")

            // Schedule upload work
            scheduleUploadWork(text, contentHash)

        } catch (e: Exception) {
            Log.e(TAG, "Error handling clipboard change: ${e.message}", e)
        }
    }

    private fun scheduleUploadWork(content: String, contentHash: String) {
        val timestamp = System.currentTimeMillis()
        
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(true)
            .build()
        
        val uploadWorkRequest = OneTimeWorkRequestBuilder<ClipboardUploadWorker>()
            .setInputData(
                workDataOf(
                    "content" to content,
                    "contentHash" to contentHash,
                    "timestamp" to timestamp
                )
            )
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()

        WorkManager.getInstance(this).enqueue(uploadWorkRequest)
        Log.d(TAG, "📤 Scheduled clipboard upload work")
    }

    private fun generateHash(content: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(content.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }.take(16)
    }

    private fun createNotification(): android.app.Notification {
        val notificationIntent = Intent(this, com.prasad.smsarchiver.ui.MainActivity::class.java)
        val pendingIntent = android.app.PendingIntent.getActivity(
            this,
            0,
            notificationIntent,
            android.app.PendingIntent.FLAG_IMMUTABLE
        )

        val channelId = "clipboard_monitor_channel"
        
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val channel = android.app.NotificationChannel(
                channelId,
                "Clipboard Monitor",
                android.app.NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Monitors clipboard for automatic sync"
            }

            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            notificationManager.createNotificationChannel(channel)
        }

        return androidx.core.app.NotificationCompat.Builder(this, channelId)
            .setContentTitle("Clipboard Monitor Active")
            .setContentText("Monitoring clipboard for sync")
            .setSmallIcon(android.R.drawable.ic_menu_save)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }
}
