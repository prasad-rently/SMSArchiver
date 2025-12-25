package com.prasad.smsarchiver

import android.app.Application
import android.util.Log
import com.google.firebase.FirebaseApp

/**
 * Application class for initializing Firebase and other app-wide components
 */
class SMSArchiverApplication : Application() {

    companion object {
        private const val TAG = "SMSArchiverApp"
    }

    override fun onCreate() {
        super.onCreate()
        
        // Initialize Firebase
        try {
            FirebaseApp.initializeApp(this)
            Log.d(TAG, "Firebase initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing Firebase: ${e.message}", e)
        }
    }
}
