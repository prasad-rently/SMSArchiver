package com.prasad.smsarchiver.data.repository

import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.provider.Telephony
import android.util.Log
import com.prasad.smsarchiver.data.model.SmsMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Repository for reading SMS messages from the device
 */
class SmsRepository(private val context: Context) {

    companion object {
        private const val TAG = "SmsRepository"
    }

    /**
     * Read all SMS messages from the device
     */
    suspend fun getAllMessages(): List<SmsMessage> = withContext(Dispatchers.IO) {
        val messages = mutableListOf<SmsMessage>()
        val contentResolver: ContentResolver = context.contentResolver

        val projection = arrayOf(
            Telephony.Sms._ID,
            Telephony.Sms.THREAD_ID,
            Telephony.Sms.ADDRESS,
            Telephony.Sms.BODY,
            Telephony.Sms.DATE,
            Telephony.Sms.TYPE,
            Telephony.Sms.READ,
            Telephony.Sms.SEEN,
            Telephony.Sms.PROTOCOL,
            Telephony.Sms.SERVICE_CENTER,
            Telephony.Sms.SUBSCRIPTION_ID
        )

        try {
            val cursor: Cursor? = contentResolver.query(
                Telephony.Sms.CONTENT_URI,
                projection,
                null,
                null,
                "${Telephony.Sms.DATE} DESC"
            )

            cursor?.use {
                val idIndex = it.getColumnIndexOrThrow(Telephony.Sms._ID)
                val threadIdIndex = it.getColumnIndexOrThrow(Telephony.Sms.THREAD_ID)
                val addressIndex = it.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
                val bodyIndex = it.getColumnIndexOrThrow(Telephony.Sms.BODY)
                val dateIndex = it.getColumnIndexOrThrow(Telephony.Sms.DATE)
                val typeIndex = it.getColumnIndexOrThrow(Telephony.Sms.TYPE)
                val readIndex = it.getColumnIndexOrThrow(Telephony.Sms.READ)
                val seenIndex = it.getColumnIndexOrThrow(Telephony.Sms.SEEN)
                val protocolIndex = it.getColumnIndexOrThrow(Telephony.Sms.PROTOCOL)
                val serviceCenterIndex = it.getColumnIndexOrThrow(Telephony.Sms.SERVICE_CENTER)
                val subscriptionIdIndex = it.getColumnIndex(Telephony.Sms.SUBSCRIPTION_ID)

                while (it.moveToNext()) {
                    messages.add(
                        SmsMessage(
                            id = it.getLong(idIndex),
                            threadId = it.getLong(threadIdIndex),
                            address = it.getString(addressIndex) ?: "",
                            body = it.getString(bodyIndex) ?: "",
                            timestamp = it.getLong(dateIndex),
                            type = it.getInt(typeIndex),
                            read = it.getInt(readIndex) == 1,
                            seen = it.getInt(seenIndex) == 1,
                            protocol = it.getInt(protocolIndex),
                            serviceCenter = it.getString(serviceCenterIndex),
                            subscriptionId = if (subscriptionIdIndex >= 0) it.getInt(subscriptionIdIndex) else -1
                        )
                    )
                }
            }
            Log.d(TAG, "Read ${messages.size} SMS messages")
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException reading SMS: ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "Error reading SMS: ${e.message}", e)
        }

        return@withContext messages
    }

    /**
     * Read SMS messages after a specific timestamp
     */
    suspend fun getMessagesAfter(timestamp: Long): List<SmsMessage> = withContext(Dispatchers.IO) {
        val messages = mutableListOf<SmsMessage>()
        val contentResolver: ContentResolver = context.contentResolver

        val projection = arrayOf(
            Telephony.Sms._ID,
            Telephony.Sms.THREAD_ID,
            Telephony.Sms.ADDRESS,
            Telephony.Sms.BODY,
            Telephony.Sms.DATE,
            Telephony.Sms.TYPE,
            Telephony.Sms.READ,
            Telephony.Sms.SEEN,
            Telephony.Sms.PROTOCOL,
            Telephony.Sms.SERVICE_CENTER,
            Telephony.Sms.SUBSCRIPTION_ID
        )

        val selection = "${Telephony.Sms.DATE} >= ?"
        val selectionArgs = arrayOf(timestamp.toString())

        Log.d(TAG, "Querying SMS with date >= $timestamp")

        try {
            val cursor: Cursor? = contentResolver.query(
                Telephony.Sms.CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                "${Telephony.Sms.DATE} DESC"
            )

            cursor?.use {
                val idIndex = it.getColumnIndexOrThrow(Telephony.Sms._ID)
                val threadIdIndex = it.getColumnIndexOrThrow(Telephony.Sms.THREAD_ID)
                val addressIndex = it.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
                val bodyIndex = it.getColumnIndexOrThrow(Telephony.Sms.BODY)
                val dateIndex = it.getColumnIndexOrThrow(Telephony.Sms.DATE)
                val typeIndex = it.getColumnIndexOrThrow(Telephony.Sms.TYPE)
                val readIndex = it.getColumnIndexOrThrow(Telephony.Sms.READ)
                val seenIndex = it.getColumnIndexOrThrow(Telephony.Sms.SEEN)
                val protocolIndex = it.getColumnIndexOrThrow(Telephony.Sms.PROTOCOL)
                val serviceCenterIndex = it.getColumnIndexOrThrow(Telephony.Sms.SERVICE_CENTER)
                val subscriptionIdIndex = it.getColumnIndex(Telephony.Sms.SUBSCRIPTION_ID)

                while (it.moveToNext()) {
                    messages.add(
                        SmsMessage(
                            id = it.getLong(idIndex),
                            threadId = it.getLong(threadIdIndex),
                            address = it.getString(addressIndex) ?: "",
                            body = it.getString(bodyIndex) ?: "",
                            timestamp = it.getLong(dateIndex),
                            type = it.getInt(typeIndex),
                            read = it.getInt(readIndex) == 1,
                            seen = it.getInt(seenIndex) == 1,
                            protocol = it.getInt(protocolIndex),
                            serviceCenter = it.getString(serviceCenterIndex),
                            subscriptionId = if (subscriptionIdIndex >= 0) it.getInt(subscriptionIdIndex) else -1
                        )
                    )
                }
            }
            Log.d(TAG, "Read ${messages.size} new SMS messages after timestamp $timestamp")
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException reading SMS: ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "Error reading SMS: ${e.message}", e)
        }

        return@withContext messages
    }
}
