package com.agentdeck.app.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.agentdeck.app.MainActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** End-to-end smoke test: launch, wait for hydration, and navigate to Settings. */
@RunWith(AndroidJUnit4::class)
class AppSmokeTest {

    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun homeRendersAndSettingsOpens() {
        compose.waitUntil(15_000) {
            compose.onAllNodesWithText("Local workspace").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Local workspace").assertIsDisplayed()
        compose.onNodeWithText("New task").assertIsDisplayed()

        compose.onNodeWithText("Settings").performClick()
        compose.waitUntil(5_000) {
            compose.onAllNodesWithText("EXECUTION HOST").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("EXECUTION HOST").assertIsDisplayed()
        compose.onNodeWithText("CLOUD MODELS").assertIsDisplayed()
        compose.onNodeWithText("REPOSITORY CONTEXT").assertIsDisplayed()
    }
}
