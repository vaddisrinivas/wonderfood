package com.wonderfood.app.ui.main

import android.os.Build
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
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
        composeTestRule.onAllNodesWithContentDescription("Open AI capture").assertCountEquals(1)
        composeTestRule.onNodeWithContentDescription("Open AI capture").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Open settings").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Open AI capture").performClick()
        composeTestRule.onNodeWithText("WonderFood AI").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Attach receipt photo").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Record voice note").assertIsDisplayed()
        val inputWidth = composeTestRule
            .onNodeWithContentDescription("AI capture text")
            .fetchSemanticsNode()
            .boundsInRoot
            .width
        val screenWidth = composeTestRule.activity.resources.displayMetrics.widthPixels.toFloat()
        assertTrue("AI capture input should not collapse", inputWidth > screenWidth * 0.55f)
        composeTestRule.onNodeWithContentDescription("Close AI capture").performClick()
        composeTestRule.onNodeWithContentDescription("Open settings").performClick()
        composeTestRule.onNodeWithText("Settings").assertIsDisplayed()
        composeTestRule.onNodeWithText("Food profile").assertIsDisplayed()
        composeTestRule.onNodeWithText("Goals & health").assertIsDisplayed()
    }

    @Test
    fun aiCaptureStaysOpenAfterSend() {
        assumeTrue(Build.MODEL.contains("sdk", ignoreCase = true) || Build.FINGERPRINT.contains("generic"))

        composeTestRule.onNodeWithContentDescription("Open AI capture").performClick()
        composeTestRule.onNodeWithText("WonderFood AI").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("AI capture text").performTextInput("Need oats")
        composeTestRule.onNodeWithContentDescription("Send AI capture").performClick()
        composeTestRule.onNodeWithText("WonderFood AI").assertIsDisplayed()
        composeTestRule.waitUntil(timeoutMillis = 10_000) {
            composeTestRule.onAllNodesWithText("Update grocery list").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("WonderFood AI").assertIsDisplayed()
        composeTestRule.onNodeWithText("Handled locally. AI not needed.").assertIsDisplayed()
        composeTestRule.onAllNodesWithText("Update grocery list").assertCountEquals(1)
        composeTestRule.onNodeWithText("Edit proposal").performClick()
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            runCatching {
                composeTestRule.onNodeWithText("Edit before saving").assertIsDisplayed()
                true
            }.getOrDefault(false)
        }
        composeTestRule.onNodeWithText("Edit before saving").assertIsDisplayed()
        composeTestRule.onNodeWithText("Name").assertIsDisplayed()
    }

    @Test
    fun newChatKeepsPreviousConversationReadableFromHistory() {
        assumeTrue(Build.MODEL.contains("sdk", ignoreCase = true) || Build.FINGERPRINT.contains("generic"))
        val previousMessage = "Need tamarind for the history test"

        composeTestRule.onNodeWithContentDescription("Open AI capture").performClick()
        composeTestRule.onNodeWithContentDescription("AI capture text").performTextInput(previousMessage)
        composeTestRule.onNodeWithContentDescription("Send AI capture").performClick()
        composeTestRule.waitUntil(timeoutMillis = 10_000) {
            composeTestRule.onAllNodesWithText("Update grocery list").fetchSemanticsNodes().isNotEmpty()
        }

        composeTestRule.onNodeWithText("New chat").performClick()
        composeTestRule.waitUntil(timeoutMillis = 10_000) {
            composeTestRule.onAllNodesWithText("New chat started. Your kitchen, recipes, groceries, plans, and settings are still saved.")
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        composeTestRule.onNodeWithText("History").performClick()

        composeTestRule.onNodeWithText("Chat history").assertIsDisplayed()
        composeTestRule.onNodeWithText(previousMessage).assertIsDisplayed()
    }

    @Test
    fun kitchenShowsFoodFirstControlsAndSafeSelection() {
        assumeTrue(Build.MODEL.contains("sdk", ignoreCase = true) || Build.FINGERPRINT.contains("generic"))

        composeTestRule.onAllNodesWithText("Kitchen").onFirst().performClick()
        if (composeTestRule.onAllNodesWithText("No kitchen items yet.").fetchSemanticsNodes().isNotEmpty()) {
            composeTestRule.onNodeWithText("No kitchen items yet.").assertIsDisplayed()
            composeTestRule.onNodeWithText("Add food directly or scan a receipt.").assertIsDisplayed()
            composeTestRule.onAllNodesWithText("Remove").assertCountEquals(0)
            return
        }

        composeTestRule.onNodeWithText("Add food").assertIsDisplayed()
        composeTestRule.onNodeWithText("Use first").assertIsDisplayed()
        composeTestRule.onNodeWithText("Search food, category, notes").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Gallery view").assertIsSelected()
        composeTestRule.onNodeWithText("Select").performClick()
        composeTestRule.onNodeWithText("0 selected").assertIsDisplayed()
        composeTestRule.onAllNodesWithText("Remove").assertCountEquals(0)
    }

    @Test
    fun manualCreateIsAvailableWithoutAi() {
        assumeTrue(Build.MODEL.contains("sdk", ignoreCase = true) || Build.FINGERPRINT.contains("generic"))

        composeTestRule.onAllNodesWithText("Kitchen").onFirst().performClick()
        composeTestRule.onNodeWithText("Add food").performClick()
        composeTestRule.onNodeWithText("Add kitchen food").assertIsDisplayed()
        composeTestRule.onNodeWithText("Cancel").performClick()

        composeTestRule.onAllNodesWithText("Shop").onFirst().performClick()
        composeTestRule.onNodeWithText("Add item").assertIsDisplayed()

        composeTestRule.onAllNodesWithText("Recipes").onFirst().performClick()
        composeTestRule.onNodeWithText("New recipe").assertIsDisplayed()

        composeTestRule.onAllNodesWithText("Today").onFirst().performClick()
        composeTestRule.onNodeWithText("Log meal").performScrollTo().assertIsDisplayed()
    }

    private fun assertTextPresent(text: String) {
        assertTrue(
            "$text should be present in the app shell",
            composeTestRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty(),
        )
    }
}
