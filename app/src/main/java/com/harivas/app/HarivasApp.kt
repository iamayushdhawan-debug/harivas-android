package com.harivas.app

import android.app.Application
import com.google.firebase.FirebaseApp

/**
 * Application entry point.
 * Initialises Firebase and notification channels on startup.
 */
class HarivasApp : Application() {
    override fun onCreate() {
        super.onCreate()
        FirebaseApp.initializeApp(this)
        HvNotifications.createChannels(this)
    }
}
