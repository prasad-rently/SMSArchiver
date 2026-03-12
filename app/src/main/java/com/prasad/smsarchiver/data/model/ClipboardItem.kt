package com.prasad.smsarchiver.data.model

/**
 * Data model for clipboard item
 */
data class ClipboardItem(
    val id: String,                         // Unique ID (hash of content + timestamp)
    val content: String,                    // Clipboard text content (empty string for images)
    val timestamp: Long,                    // Unix timestamp when copied
    val source: String? = null,             // Source app (if available)
    val contentHash: String,                // Hash to detect duplicates
    val isSynced: Boolean = false,          // Upload status
    val contentType: String = "text",       // "text" or "image"
    val imageUrl: String? = null            // Firebase Storage download URL (images only)
) {
    /**
     * Convert to map for Firebase upload
     */
    fun toMap(): Map<String, Any?> {
        return mapOf(
            "id" to id,
            "content" to content,
            "timestamp" to timestamp,
            "source" to source,
            "contentHash" to contentHash,
            "contentType" to contentType,
            "imageUrl" to imageUrl,
            "uploadedAt" to System.currentTimeMillis()
        )
    }
}
