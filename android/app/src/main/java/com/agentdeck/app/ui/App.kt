package com.agentdeck.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewModelScope
import com.agentdeck.app.data.AgentDeckState
import com.agentdeck.app.data.RootTab
import com.agentdeck.app.data.Screen
import com.agentdeck.app.ui.theme.AmberInk
import com.agentdeck.app.ui.theme.AmberSoft
import com.agentdeck.app.ui.theme.Canvas
import com.agentdeck.app.ui.theme.Ink
import com.agentdeck.app.ui.theme.Muted
import com.agentdeck.app.ui.theme.Surface2

class AgentDeckViewModel(application: Application) : AndroidViewModel(application) {
    val state = AgentDeckState(viewModelScope, application.applicationContext)

    override fun onCleared() {
        state.shutdown()
        super.onCleared()
    }
}

@Composable
fun AgentDeckApp(viewModel: AgentDeckViewModel = viewModel()) {
    val state = viewModel.state
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.toastEvent) {
        val event = state.toastEvent ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(event.text)
        state.consumeToast()
    }

    if (!state.hydrated) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Canvas),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "AgentDeck",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Ink
                )
                Text(
                    text = "Restoring workspace…",
                    fontSize = 13.sp,
                    color = Muted,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
        return
    }

    val currentScreen = state.screen
    BackHandler(enabled = state.sheet == null && currentScreen is Screen.TaskScreen && state.diffFileId != null) {
        state.selectDiffFile(null)
    }
    BackHandler(enabled = state.sheet == null && state.diffFileId == null && currentScreen !is Screen.SignedOut && state.canGoBack()) {
        state.back()
    }

    Box(Modifier.fillMaxSize().background(Canvas)) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val appWidth = if (maxWidth > 430.dp) 430.dp else maxWidth
            Surface(
                modifier = Modifier
                    .width(appWidth)
                    .fillMaxHeight()
                    .align(Alignment.TopCenter),
                color = Surface2,
                shadowElevation = if (maxWidth > 430.dp) 18.dp else 0.dp
            ) {
                when (currentScreen) {
                    is Screen.Root -> {
                        Column(Modifier.fillMaxSize()) {
                            Box(Modifier.weight(1f)) {
                                when (currentScreen.tab) {
                                    RootTab.HOME -> HomeScreen(state)
                                    RootTab.TASKS -> TasksScreen(state)
                                    RootTab.PROJECTS -> ProjectsScreen(state)
                                    RootTab.SETTINGS -> SettingsScreen(state)
                                }
                            }
                            if (!state.online) {
                                OfflinePill(
                                    modifier = Modifier
                                        .align(Alignment.CenterHorizontally)
                                        .padding(bottom = 8.dp)
                                )
                            }
                            AdBottomBar(active = currentScreen.tab, onSelect = state::selectRoot)
                        }
                    }
                    is Screen.TaskScreen -> TaskScreen(state, currentScreen.taskId)
                    Screen.NewTask -> NewTaskScreen(state)
                    Screen.SignedOut -> SignedOutScreen(state)
                }
            }
        }
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 16.dp, vertical = 88.dp)
        )
    }

    if (state.sheet != null) {
        AgentDeckSheetHost(state)
    }
}

@Composable
private fun OfflinePill(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .background(AmberSoft, shape = androidx.compose.foundation.shape.RoundedCornerShape(999.dp))
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Offline",
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = AmberInk
        )
        Text(
            text = "· cloud requests will fail until you reconnect",
            fontSize = 11.sp,
            color = AmberInk,
            modifier = Modifier.padding(start = 4.dp)
        )
    }
}

@Composable
private fun SignedOutScreen(state: AgentDeckState) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Surface2)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(28.dp),
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("AgentDeck", style = MaterialTheme.typography.headlineLarge, color = Ink)
        androidx.compose.foundation.layout.Spacer(Modifier.height(20.dp))
        Text("You are signed out", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Ink)
        androidx.compose.foundation.layout.Spacer(Modifier.height(8.dp))
        Text(
            "This is a local demo. Signing back in only restores the sample workspace in app memory.",
            fontSize = 12.sp,
            lineHeight = 18.sp,
            color = Muted,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        androidx.compose.foundation.layout.Spacer(Modifier.height(20.dp))
        AdButton(
            "Continue in demo",
            onClick = state::signIn,
            modifier = Modifier.fillMaxWidth(),
            fillWidth = true
        )
    }
}
