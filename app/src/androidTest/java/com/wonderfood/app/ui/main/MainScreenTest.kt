package com.wonderfood.app.ui.main

import android.os.Build
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.wonderfood.app.MainActivity
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

class MainScreenTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun mainScreenShowsFoodChatShell() {
        assumeTrue(Build.MODEL.contains("sdk", ignoreCase = true) || Build.FINGERPRINT.contains("generic"))

        composeTestRule.onNodeWithText("Plan, log, sync").assertIsDisplayed()
        composeTestRule.onNodeWithText("Meal calendar").assertIsDisplayed()
        composeTestRule.onNodeWithText("Tell AI what changed...").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Attach receipt photo").assertIsDisplayed()
        composeTestRule.onNodeWithText("More").performClick()
        composeTestRule.onNodeWithText("Food OS").assertIsDisplayed()
        composeTestRule.onNodeWithText("Taste profile").assertIsDisplayed()
    }
}
