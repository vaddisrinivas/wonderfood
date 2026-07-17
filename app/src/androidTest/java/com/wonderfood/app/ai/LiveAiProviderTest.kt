package com.wonderfood.app.ai

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.wonderfood.app.data.FoodMemory
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LiveAiProviderTest {
    @Test
    fun configuredPrimaryProviderCompletesStructuredTurnWhenExplicitlyEnabled() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        if (InstrumentationRegistry.getArguments().getString(ARG_LIVE_AI) != "true") return

        val config = LiteLlmSettings(instrumentation.targetContext).read()
        assertTrue("Primary AI provider is not configured.", config.isUsable)

        val result = LiteLlmFoodInterpreter().interpretWithDiagnostics(
            text = "Reply with a short provider health message. Do not create a draft.",
            memory = FoodMemory(),
            config = config,
        )
        Log.i(LOG_TAG, result.diagnostic)
        assertTrue(result.diagnostic, result is LiteLlmInterpretation.Success)
    }

    private companion object {
        const val ARG_LIVE_AI = "liveAi"
        const val LOG_TAG = "WonderFoodAILiveTest"
    }
}
