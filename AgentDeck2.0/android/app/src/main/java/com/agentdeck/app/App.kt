package com.agentdeck.app

import android.app.Application
import com.agentdeck.app.data.AgentDeckState
import com.agentdeck.app.ui.App
import androidx.lifecycle.viewmodel.compose.viewModel
import org.jetbrains.compose.ui.tooling.preview.Preview
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import com.agentdeck.app.ui.theme.AgentDeckTheme
import com.google.firebase.FirebaseApp

/**
 * Application entry point for AgentDeck native Android app.
 * Initializes Firebase for crash reporting and manages app lifecycle.
 */
class AgentDeckApplication : Application() {
    
    override fun onCreate() {
        super.onCreate()
        
        // Initialize Firebase (for production builds with google-services.json)
        try {
            FirebaseApp.initializeApp(this)
        } catch (e: Exception) {
            // Firebase might not be initialized in debug or if google-services.json missing
            // This is expected during development
        }
    }
}

// ViewModel provider function for Compose
@Composable
fun AppViewModel(
    agentDeckState: AgentDeckState = viewModel()
): AgentDeckState = agentDeckState
