package com.wonderfood.app.ui.main

import android.os.Build
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.test.platform.app.InstrumentationRegistry
import com.wonderfood.app.MainActivity
import org.junit.Assume.assumeTrue
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.BeforeClass
import org.junit.FixMethodOrder
import org.junit.Rule
import org.junit.Test
import org.junit.runners.MethodSorters

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class MainScreenTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    companion object {
        @JvmStatic
        @BeforeClass
        fun seedLocalBackendChoice() {
            InstrumentationRegistry.getInstrumentation()
                .targetContext
                .getSharedPreferences("wonderfood_backend_configuration", android.content.Context.MODE_PRIVATE)
                .edit()
                .putString("type", "LOCAL_SQLITE")
                .putInt("schema_version", 1)
                .putBoolean("onboarding_dismissed", true)
                .commit()
        }
    }

    @After
    fun returnToShellAfterTest() {
        if (!isEmulator()) return
        repeat(6) {
            if (isShellVisible()) return
            runCatching {
                composeTestRule.activityRule.scenario.onActivity { activity ->
                    activity.onBackPressedDispatcher.onBackPressed()
                }
            }
            composeTestRule.waitForIdle()
        }
    }

    @Test
    fun aMainScreenShowsFiveDestinationShell() {
        assumeEmulatorAndWaitForShell()

        assertTextPresent("Now")
        assertTextPresent("Food")
        assertTextPresent("Week")
        assertTextPresent("Saved")
        assertTextPresent("Cart")
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
        pressActivityBack()
    }

    @Test
    fun eAiCaptureStaysOpenAfterSend() {
        assumeEmulatorAndWaitForShell()

        composeTestRule.onNodeWithContentDescription("Open AI capture").performClick()
        composeTestRule.onNodeWithText("WonderFood AI").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("AI capture text").performTextInput("Need oats")
        composeTestRule.onNodeWithContentDescription("Send AI capture").performClick()
        composeTestRule.onNodeWithText("WonderFood AI").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Close AI capture").performClick()
    }

    @Test
    fun fNewChatKeepsPreviousConversationReadableFromHistory() {
        assumeEmulatorAndWaitForShell()
        val previousMessage = "Need tamarind for the history test"

        composeTestRule.onNodeWithContentDescription("Open AI capture").performClick()
        composeTestRule.onNodeWithText("New chat").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithContentDescription("AI capture text").performTextInput(previousMessage)
        composeTestRule.onNodeWithContentDescription("Send AI capture").performClick()
        composeTestRule.waitUntil(timeoutMillis = 20_000) {
            composeTestRule.onAllNodesWithText("Edit proposal").fetchSemanticsNodes().isNotEmpty()
        }

        composeTestRule.onNodeWithText("New chat").performClick()
        composeTestRule.waitUntil(timeoutMillis = 10_000) {
            composeTestRule.onAllNodesWithText("New chat started. Your kitchen, recipes, groceries, plans, and settings are still saved.")
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        composeTestRule.onNodeWithText("History").performClick()

        composeTestRule.onNodeWithText("Chat history").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Chat history list").performScrollToNode(hasText(previousMessage))
        composeTestRule.onNodeWithText(previousMessage).assertIsDisplayed()
        pressActivityBack()
    }

    @Test
    fun zCoreAiSkillCanBeOpenedWithoutCrashing() {
        assumeEmulatorAndWaitForShell()

        composeTestRule.onNodeWithContentDescription("Open settings").performClick()
        composeTestRule.onNodeWithText("AI assistant").performClick()
        composeTestRule.onNodeWithText("Provider routes").assertIsDisplayed()
        composeTestRule.onNodeWithText("View or edit core skill").performClick()

        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithText("Core AI skill").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("Core AI skill").assertExists()
        composeTestRule.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(hasText("Reset to bundled skill"))
        composeTestRule.onNodeWithText("Reset to bundled skill").assertIsDisplayed()
        pressActivityBack()
        pressActivityBack()
    }

    @Test
    fun bKitchenShowsFoodFirstControlsAndSafeSelection() {
        assumeEmulatorAndWaitForShell()

        composeTestRule.onNodeWithContentDescription("Navigate to Food").performClick()
        if (composeTestRule.onAllNodesWithText("No kitchen items yet.").fetchSemanticsNodes().isNotEmpty()) {
            composeTestRule.onNodeWithText("No kitchen items yet.").assertIsDisplayed()
            composeTestRule.onNodeWithText("Add food directly or scan a receipt when you're ready.").assertIsDisplayed()
            composeTestRule.onNodeWithContentDescription("Add kitchen food").assertIsDisplayed()
            composeTestRule.onNodeWithContentDescription("Open AI capture").assertIsDisplayed()
            composeTestRule.onAllNodesWithText("Remove").assertCountEquals(0)
            return
        }

        composeTestRule.onNodeWithContentDescription("Add kitchen food").assertIsDisplayed()
        composeTestRule.onAllNodesWithText("Remove").assertCountEquals(0)
    }

    @Test
    fun cManualCreateIsAvailableWithoutAi() {
        assumeEmulatorAndWaitForShell()

        composeTestRule.onNodeWithContentDescription("Navigate to Food").performClick()
        composeTestRule.onNodeWithContentDescription("Add kitchen food").performClick()
        composeTestRule.onNodeWithText("Add kitchen food").assertIsDisplayed()
        composeTestRule.onNodeWithText("Cancel").performClick()

        composeTestRule.onNodeWithContentDescription("Navigate to Cart").performClick()
        composeTestRule.onNodeWithContentDescription("Add shopping item").assertIsDisplayed()

        composeTestRule.onNodeWithContentDescription("Navigate to Saved").performClick()
        composeTestRule.onNodeWithContentDescription("Create recipe").assertIsDisplayed()

        composeTestRule.onNodeWithContentDescription("Navigate to Now").performClick()
        composeTestRule.onNodeWithContentDescription("Log meal").performClick()
        composeTestRule.onNodeWithText("Date").assertIsDisplayed()
        pressActivityBack()
    }

    @Test
    fun dDestinationsExposeV3WorkflowContexts() {
        assumeEmulatorAndWaitForShell()

        composeTestRule.onNodeWithContentDescription("Navigate to Now").performClick()
        composeTestRule.onNodeWithText("Meal timeline").assertIsDisplayed()

        composeTestRule.onNodeWithContentDescription("Navigate to Week").performClick()
        composeTestRule.onNodeWithText("This week").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Plan today").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Open AI capture").assertIsDisplayed()

        composeTestRule.onNodeWithContentDescription("Navigate to Cart").performClick()
        composeTestRule.onNodeWithContentDescription("Shop mode To buy").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Shop mode Receipts").performClick()
        composeTestRule.onNodeWithText("No receipts yet.").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Shop mode Put away").performClick()
        composeTestRule.onNodeWithText("Put-away queue is clear.").assertIsDisplayed()
    }

    private fun assertTextPresent(text: String) {
        assertTrue(
            "$text should be present in the app shell",
            composeTestRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty(),
        )
    }

    private fun assumeEmulatorAndWaitForShell() {
        assumeTrue(isEmulator())
        composeTestRule.activity
        composeTestRule.waitForIdle()
        dismissFoodHomeChooserIfPresent()
        composeTestRule.waitUntil(timeoutMillis = 10_000) {
            isShellVisible()
        }
    }

    private fun isEmulator(): Boolean =
        Build.MODEL.contains("sdk", ignoreCase = true) || Build.FINGERPRINT.contains("generic")

    private fun isShellVisible(): Boolean =
        runCatching {
            composeTestRule.onAllNodesWithContentDescription("Navigate to Now").fetchSemanticsNodes().isNotEmpty() ||
                composeTestRule.onAllNodesWithText("Today").fetchSemanticsNodes().isNotEmpty()
        }.getOrDefault(false)

    private fun pressActivityBack() {
        runCatching {
            composeTestRule.activityRule.scenario.onActivity { activity ->
                activity.onBackPressedDispatcher.onBackPressed()
            }
        }
        composeTestRule.waitForIdle()
    }

    private fun dismissFoodHomeChooserIfPresent() {
        if (runCatching { composeTestRule.onAllNodesWithText("Start local now").fetchSemanticsNodes().isNotEmpty() }.getOrDefault(false)) {
            composeTestRule.onNodeWithText("Start local now").performClick()
            composeTestRule.waitForIdle()
            composeTestRule.waitUntil(timeoutMillis = 5_000) {
                runCatching { composeTestRule.onAllNodesWithText("Start local now").fetchSemanticsNodes().isEmpty() }.getOrDefault(false)
            }
        }
    }
}
