package com.wonderfood.app.ui.main

import android.os.Build
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.wonderfood.app.MainActivity
import org.junit.Assume.assumeTrue
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class MainScreenTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun mainScreenShowsFiveDestinationShell() {
        assumeTrue(Build.MODEL.contains("sdk", ignoreCase = true) || Build.FINGERPRINT.contains("generic"))

        assertTextPresent("Today")
        assertTextPresent("Kitchen")
        assertTextPresent("Plan")
        assertTextPresent("Recipes")
        assertTextPresent("Shop")
        composeTestRule.onAllNodesWithContentDescription("More").assertCountEquals(0)
        composeTestRule.onAllNodesWithContentDescription("AI").assertCountEquals(0)
        composeTestRule.onAllNodesWithText("More").assertCountEquals(0)
        composeTestRule.onAllNodesWithText("AI").assertCountEquals(0)

        composeTestRule.onNodeWithContentDescription("Search WonderFood").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Open AI capture").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Open settings").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Open AI capture").performClick()
        composeTestRule.onNodeWithText("Ask WonderFood").assertIsDisplayed()
        composeTestRule.onNodeWithText("Receipt").assertIsDisplayed()
        composeTestRule.onNodeWithText("Voice").assertIsDisplayed()
        val inputWidth = composeTestRule
            .onNodeWithContentDescription("AI capture text")
            .fetchSemanticsNode()
            .boundsInRoot
            .width
        assertTrue("AI capture input should not collapse", inputWidth > 500f)
        composeTestRule.onNodeWithContentDescription("Close AI capture").performClick()
        composeTestRule.onNodeWithContentDescription("Open settings").performClick()
        composeTestRule.onNodeWithText("Settings").assertIsDisplayed()
        composeTestRule.onNodeWithText("Food OS").assertIsDisplayed()
        composeTestRule.onNodeWithText("Taste profile").assertIsDisplayed()
    }

    @Test
    fun aiCaptureStaysOpenAfterSend() {
        assumeTrue(Build.MODEL.contains("sdk", ignoreCase = true) || Build.FINGERPRINT.contains("generic"))

        composeTestRule.onNodeWithContentDescription("Open AI capture").performClick()
        composeTestRule.onNodeWithText("Ask WonderFood").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("AI capture text").performTextInput("Need oats")
        composeTestRule.onNodeWithText("Send").performClick()
        composeTestRule.onNodeWithText("Ask WonderFood").assertIsDisplayed()
    }

    private fun assertTextPresent(text: String) {
        assertTrue(
            "$text should be present in the app shell",
            composeTestRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty(),
        )
    }
}
