package com.wonderfood.app

import android.content.Intent
import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WonderFoodDeepLinkTest {
    @Test
    fun waterCommandParsesAmountAndExplicitIdempotencyKey() {
        val command = WonderFoodDeepLink.from(
            Intent(Intent.ACTION_VIEW, Uri.parse("wonderfood://voice/water?ml=375&requestId=water-1")),
        )

        assertEquals(WonderFoodVoiceAction.LOG_WATER, command?.action)
        assertEquals(375.0, command?.amount)
        assertEquals("ml", command?.unit)
        assertEquals("water-1", command?.idempotencyKey)
    }

    @Test
    fun unsupportedVoiceUtteranceRoutesToAiReview() {
        val command = WonderFoodDeepLink.from(
            Intent(Intent.ACTION_VIEW, Uri.parse("wonderfood://quick?text=need%20oats%20and%20bananas&requestId=note-1")),
        )

        assertEquals(WonderFoodVoiceAction.AI_REVIEW, command?.action)
        assertEquals("need oats and bananas", command?.text)
        assertEquals("note-1", command?.idempotencyKey)
    }

    @Test
    fun openNumbersMapsToNumbersAction() {
        val command = WonderFoodDeepLink.from(
            Intent(Intent.ACTION_VIEW, Uri.parse("wonderfood://open/numbers")),
        )

        assertEquals(WonderFoodVoiceAction.SHOW_NUMBERS, command?.action)
        assertEquals("numbers", command?.section)
    }

    @Test
    fun rejectsNonViewActionAndUnknownScheme() {
        assertNull(
            WonderFoodDeepLink.from(
                Intent(Intent.ACTION_SEND, Uri.parse("wonderfood://voice/water?ml=250")),
            ),
        )
        assertNull(
            WonderFoodDeepLink.from(
                Intent(Intent.ACTION_VIEW, Uri.parse("https://voice/water?ml=250")),
            ),
        )
    }

    @Test
    fun trimsOversizedAssistantText() {
        val longText = "o".repeat(240)
        val command = WonderFoodDeepLink.from(
            Intent(Intent.ACTION_VIEW, Uri.parse("wonderfood://quick?text=$longText")),
        )

        assertEquals(160, command?.text?.length)
    }
}
