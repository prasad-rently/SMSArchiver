package com.prasad.smsarchiver.data.model

/**
 * Data model for clipboard item
 */
data class ClipboardItem(
    val id: String,                    // Unique ID (hash of content + timestamp)
    val content: String,               // Clipboard text content
    val timestamp: Long,               // Unix timestamp when copied
    val source: String? = null,        // Source app (if available)
    val contentHash: String,           // Hash to detect duplicates
    val isSynced: Boolean = false      // Upload status
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
            "uploadedAt" to System.currentTimeMillis()
        )
    }
}
