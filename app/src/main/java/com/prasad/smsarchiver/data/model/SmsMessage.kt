package com.prasad.smsarchiver.data.model

/**
 * Represents an SMS message from the device
 */
data class SmsMessage(
    val id: Long,
    val threadId: Long,
    val address: String,          // Phone number
    val body: String,             // Message content
    val timestamp: Long,          // Unix timestamp in milliseconds
    val type: Int,                // MESSAGE_TYPE_INBOX = 1, MESSAGE_TYPE_SENT = 2
    val read: Boolean,
    val seen: Boolean,
    val protocol: Int? = null,
    val serviceCenter: String? = null,
    val subscriptionId: Int = -1  // SIM card identifier for dual SIM phones (-1 = unknown)
) {
    companion object {
        const val MESSAGE_TYPE_INBOX = 1
        const val MESSAGE_TYPE_SENT = 2
        const val MESSAGE_TYPE_DRAFT = 3
        const val MESSAGE_TYPE_OUTBOX = 4
        const val MESSAGE_TYPE_FAILED = 5
        const val MESSAGE_TYPE_QUEUED = 6
    }

    /**
     * Convert to JSON-friendly map for Firebase upload
     */
    fun toMap(): Map<String, Any?> = mapOf(
        "id" to id,
        "threadId" to threadId,
        "address" to address,
        "body" to body,
        "timestamp" to timestamp,
        "type" to type,
        "read" to read,
        "seen" to seen,
        "protocol" to protocol,
        "serviceCenter" to serviceCenter,
        "subscriptionId" to subscriptionId
    )
}
