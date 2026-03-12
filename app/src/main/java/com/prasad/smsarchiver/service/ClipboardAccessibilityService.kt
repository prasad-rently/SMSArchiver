package com.prasad.smsarchiver.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.prasad.smsarchiver.data.worker.ClipboardUploadWorker
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * AccessibilityService-based clipboard monitor.
 *
 * Android 10+ (API 29) blocks background apps from reading clipboard content via
 * ClipboardManager.getPrimaryClip(). The only reliable workaround (without root or
 * being the default IME) is to use an AccessibilityService, which runs with
 * system-level trust and retains clipboard read access in the background.
 *
 * The user must enable this service once via:
 *   Settings → Accessibility → Downloaded apps → SMS Archiver → toggle on
 */
class ClipboardAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "ClipboardA11yService"
        const val CONTENT_TYPE_TEXT = "text"
        const val CONTENT_TYPE_IMAGE = "image"

        /** Check whether this service is currently enabled by the user. */
        fun isEnabled(context: Context): Boolean {
            val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE)
                as android.view.accessibility.AccessibilityManager
            val enabledServices = am.getEnabledAccessibilityServiceList(
                AccessibilityServiceInfo.FEEDBACK_ALL_MASK
            )
            val packageName = context.packageName
            return enabledServices.any { it.resolveInfo.serviceInfo.packageName == packageName
                    && it.resolveInfo.serviceInfo.name == ClipboardAccessibilityService::class.java.name }
        }
    }

    private var clipboardManager: ClipboardManager? = null
    private var lastContentHash = ""

    private val clipboardListener = ClipboardManager.OnPrimaryClipChangedListener {
        Log.d(TAG, "Clipboard change detected via AccessibilityService")
        handleClipboardChange()
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "Accessibility service connected — registering clipboard listener")
        clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboardManager?.addPrimaryClipChangedListener(clipboardListener)
    }

    override fun onUnbind(intent: Intent?): Boolean {
        Log.d(TAG, "Accessibility service unbound — removing clipboard listener")
        clipboardManager?.removePrimaryClipChangedListener(clipboardListener)
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        clipboardManager?.removePrimaryClipChangedListener(clipboardListener)
        Log.d(TAG, "Accessibility service destroyed")
    }

    // Not used — we only care about clipboard, not UI events.
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    private fun handleClipboardChange() {
        try {
            // This call SUCCEEDS here because AccessibilityService has system-level trust.
            // The same call returns null from a regular foreground service on Android 10+.
            val primaryClip = clipboardManager?.primaryClip
            if (primaryClip == null || primaryClip.itemCount == 0) {
                Log.d(TAG, "Clipboard empty or null — skipping")
                return
            }

            val item = primaryClip.getItemAt(0)

            // --- Image clipboard ---
            val imageUri = item.uri
            if (imageUri != null) {
                handleImageClip(imageUri)
                return
            }

            // --- Text clipboard ---
            val text = item.coerceToText(this)?.toString() ?: return
            if (text.isBlank() || text.length < 2) {
                Log.d(TAG, "Ignoring empty/short text")
                return
            }
            handleTextClip(text)

        } catch (e: Exception) {
            Log.e(TAG, "Error handling clipboard change: ${e.message}", e)
        }
    }

    private fun handleTextClip(text: String) {
        val hash = generateHash(text)
        if (hash == lastContentHash) {
            Log.d(TAG, "Duplicate text — skipping")
            return
        }
        lastContentHash = hash

        Log.d(TAG, "New text clip: ${text.take(80)}${if (text.length > 80) "..." else ""} [hash=$hash]")

        val timestamp = System.currentTimeMillis()
        scheduleUpload(
            workDataOf(
                "contentType" to CONTENT_TYPE_TEXT,
                "content" to text,
                "contentHash" to hash,
                "timestamp" to timestamp
            )
        )
    }

    private fun handleImageClip(imageUri: Uri) {
        val uriString = imageUri.toString()
        val hash = generateHash(uriString)
        if (hash == lastContentHash) {
            Log.d(TAG, "Duplicate image URI — skipping")
            return
        }
        lastContentHash = hash

        Log.d(TAG, "New image clip: $uriString [hash=$hash]")

        val timestamp = System.currentTimeMillis()
        scheduleUpload(
            workDataOf(
                "contentType" to CONTENT_TYPE_IMAGE,
                "imageUri" to uriString,
                "contentHash" to hash,
                "timestamp" to timestamp
            )
        )
    }

    private fun scheduleUpload(inputData: androidx.work.Data) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val request = OneTimeWorkRequestBuilder<ClipboardUploadWorker>()
            .setInputData(inputData)
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()

        WorkManager.getInstance(this).enqueue(request)
        Log.d(TAG, "Scheduled ClipboardUploadWorker")
    }

    private fun generateHash(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }.take(16)
    }
}
