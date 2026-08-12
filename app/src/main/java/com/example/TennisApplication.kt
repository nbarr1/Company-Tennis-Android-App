package com.example

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.example.firebase.FirebaseInitializer

class TennisApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Initialize Firebase SDK using google-services configuration
        FirebaseInitializer.init(this)
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Tennis League Updates",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Match invites, score updates, and league messages"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    companion object {
        const val NOTIFICATION_CHANNEL_ID = "tennis_league_updates"
    }
}
