package com.example.firebase

import android.content.Context
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.functions.FirebaseFunctions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Ensures robust Firebase SDK initialization and manages connection state across the application.
 */
object FirebaseInitializer {
    private const val TAG = "FirebaseInitializer"

    private val _isInitialized = MutableStateFlow(false)
    val isInitialized: StateFlow<Boolean> = _isInitialized.asStateFlow()

    private val _initializationError = MutableStateFlow<String?>(null)
    val initializationError: StateFlow<String?> = _initializationError.asStateFlow()

    fun init(context: Context) {
        try {
            val app = if (FirebaseApp.getApps(context).isEmpty()) {
                Log.d(TAG, "Initializing FirebaseApp with google-services context...")
                FirebaseApp.initializeApp(context)
            } else {
                Log.d(TAG, "FirebaseApp auto-initialized by Google Services ContentProvider.")
                FirebaseApp.getInstance()
            }

            if (app != null) {
                try {
                    val firestore = FirebaseFirestore.getInstance()
                    val settings = FirebaseFirestoreSettings.Builder()
                        .setLocalCacheSettings(com.google.firebase.firestore.PersistentCacheSettings.newBuilder().build())
                        .build()
                    firestore.firestoreSettings = settings
                    Log.d(TAG, "Firebase Firestore configured with persistent cache.")
                } catch (e: Exception) {
                    Log.w(TAG, "Firestore settings non-fatal setup warning: ${e.message}")
                }

                _isInitialized.value = true
                _initializationError.value = null
                Log.i(TAG, "Firebase successfully initialized for project: ${app.options.projectId}")
                initFcmTokenSafely()
            } else {
                _isInitialized.value = false
                _initializationError.value = "FirebaseApp instance returned null"
                Log.e(TAG, "Failed to initialize FirebaseApp.")
            }
        } catch (e: Exception) {
            _isInitialized.value = false
            _initializationError.value = e.localizedMessage ?: "Unknown initialization error"
            Log.e(TAG, "Error during Firebase initialization: ${e.message}", e)
        }
    }

    fun getAuth(): FirebaseAuth? {
        return if (_isInitialized.value) {
            try { FirebaseAuth.getInstance() } catch (e: Exception) { null }
        } else null
    }

    fun getFirestore(): FirebaseFirestore? {
        return if (_isInitialized.value) {
            try { FirebaseFirestore.getInstance() } catch (e: Exception) { null }
        } else null
    }

    fun getFunctions(): FirebaseFunctions? {
        return if (_isInitialized.value) {
            try { FirebaseFunctions.getInstance() } catch (e: Exception) { null }
        } else null
    }

    private fun initFcmTokenSafely() {
        try {
            val messaging = com.google.firebase.messaging.FirebaseMessaging.getInstance()
            messaging.isAutoInitEnabled = true
            Log.d(TAG, "FirebaseMessaging auto-init enabled (real google-services.json is present).")
        } catch (e: Exception) {
            Log.w(TAG, "FCM setup non-fatal notice: ${e.message}")
        }
    }
}
