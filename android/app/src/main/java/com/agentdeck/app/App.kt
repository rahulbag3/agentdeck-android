package com.agentdeck.app

import android.app.Application
import android.system.Os
import com.google.firebase.FirebaseApp

class AgentDeckApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Needle enables anonymous telemetry by default. Set the documented
        // opt-outs before the engine library is loaded; needle_jni.cpp sets
        // them again as a guard for direct JNI use.
        runCatching {
            Os.setenv("NEEDLE_TELEMETRY", "0", true)
            Os.setenv("DO_NOT_TRACK", "1", true)
        }
        // No-op when google-services.json is absent (debug builds, fresh clones).
        runCatching { FirebaseApp.initializeApp(this) }
    }
}
