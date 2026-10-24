package com.agentdeck.app

import android.app.Application
import com.google.firebase.FirebaseApp

class AgentDeckApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // No-op when google-services.json is absent (debug builds, fresh clones).
        runCatching { FirebaseApp.initializeApp(this) }
    }
}
