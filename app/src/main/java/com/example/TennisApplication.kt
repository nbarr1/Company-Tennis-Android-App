package com.example

import android.app.Application
import com.example.firebase.FirebaseInitializer

class TennisApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Initialize Firebase SDK using google-services configuration
        FirebaseInitializer.init(this)
    }
}
