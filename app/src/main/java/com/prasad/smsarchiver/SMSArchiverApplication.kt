package com.prasad.smsarchiver

import android.app.Application
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf

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
            // Sign in anonymously (no test write)
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val auth = FirebaseAuth.getInstance()
                    if (auth.currentUser == null) {
                        auth.signInAnonymously().await()
                        Log.d(TAG, "Signed in anonymously: uid=${auth.currentUser?.uid}")
                    } else {
                        Log.d(TAG, "Already signed in: uid=${auth.currentUser?.uid}")
                    }

                    // Auto-upload on app launch is disabled to prevent
                    // unintended bulk uploads. Users can trigger uploads
                    // manually from the UI.
                } catch (ae: Exception) {
                    Log.e(TAG, "Anonymous auth failed: ${ae.message}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing Firebase: ${e.message}", e)
        }
    }
}
