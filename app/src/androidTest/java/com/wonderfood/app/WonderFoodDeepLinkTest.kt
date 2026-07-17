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
    fun sharedTextRoutesToAiReviewWithoutTruncatingNormalProposalText() {
        val shared = "Need oats and bananas from ChatGPT"
        val command = WonderFoodDeepLink.from(
            Intent(Intent.ACTION_SEND)
                .setType("text/plain")
                .putExtra(Intent.EXTRA_TEXT, shared),
        )

        assertEquals(WonderFoodVoiceAction.AI_REVIEW, command?.action)
        assertEquals(shared, command?.text)
    }

    @Test
    fun packedGroceryTextParsesItemAndQuantity() {
        val command = WonderFoodDeepLink.from(
            Intent(Intent.ACTION_VIEW, Uri.parse("wonderfood://voice/grocery/add?text=2%20bags%20oats&requestId=grocery-1")),
        )

        assertEquals(WonderFoodVoiceAction.ADD_GROCERY, command?.action)
        assertEquals("oats", command?.itemName)
        assertEquals("2 bags", command?.quantity)
        assertEquals("2 bags oats", command?.text)
    }

    @Test
    fun packedKitchenTextKeepsZone() {
        val command = WonderFoodDeepLink.from(
            Intent(Intent.ACTION_VIEW, Uri.parse("wonderfood://voice/kitchen/add?text=a%20dozen%20eggs&zone=fridge")),
        )

        assertEquals(WonderFoodVoiceAction.ADD_INVENTORY, command?.action)
        assertEquals("eggs", command?.itemName)
        assertEquals("a dozen", command?.quantity)
        assertEquals("fridge", command?.zone)
    }

    @Test
    fun httpsAddPantryLinkPrefillsReviewableInventoryCommand() {
        val command = WonderFoodDeepLink.from(
            Intent(Intent.ACTION_VIEW, Uri.parse("https://wonderfood.app/add?kind=pantry&name=Eggs&quantity=12&zone=fridge&requestId=gpt-eggs-1")),
        )

        assertEquals(WonderFoodVoiceAction.ADD_INVENTORY, command?.action)
        assertEquals("Eggs", command?.itemName)
        assertEquals("12", command?.quantity)
        assertEquals("fridge", command?.zone)
        assertEquals("gpt-eggs-1", command?.idempotencyKey)
    }

    @Test
    fun httpsAddShoppingLinkCanCarryPlainTextList() {
        val command = WonderFoodDeepLink.from(
            Intent(Intent.ACTION_VIEW, Uri.parse("https://wonderfood.app/add/shopping?text=milk%202%20gallons%2C%20rice%2C%20lentils")),
        )

        assertEquals(WonderFoodVoiceAction.ADD_GROCERY, command?.action)
        assertEquals("milk 2 gallons, rice, lentils", command?.text)
    }

    @Test
    fun httpsAddLinkRejectsUntrustedHost() {
        assertNull(
            WonderFoodDeepLink.from(
                Intent(Intent.ACTION_VIEW, Uri.parse("https://evil.example/add?kind=pantry&name=Eggs")),
            ),
        )
    }

    @Test
    fun httpsActionLinkStagesReviewableInventoryEdit() {
        val command = WonderFoodDeepLink.from(
            Intent(Intent.ACTION_VIEW, Uri.parse("https://wonderfood.app/action?type=inventory.edit&id=42&quantity=6&zone=fridge&requestId=gpt-edit-1")),
        )

        assertEquals(WonderFoodVoiceAction.LINK_ACTION, command?.action)
        assertEquals("gpt-edit-1", command?.idempotencyKey)
        assertEquals(1, command?.linkActions?.size)
        val action = command?.linkActions?.single()
        assertEquals("inventory.edit", action?.type)
        assertEquals("inventory", action?.targetKind)
        assertEquals("42", action?.targetRef)
        assertEquals("6", action?.fields?.get("quantity"))
        assertEquals("fridge", action?.fields?.get("zone"))
    }

    @Test
    fun httpsActionLinkParsesBulkJsonActions() {
        val json = Uri.encode(
            """
            [
              {"type":"inventory.edit","id":"7","fields":{"quantity":"12"}},
              {"type":"grocery.delete","id":"9","name":"old milk"}
            ]
            """.trimIndent(),
        )
        val command = WonderFoodDeepLink.from(
            Intent(Intent.ACTION_VIEW, Uri.parse("https://wonderfood.app/action?actions=$json")),
        )

        assertEquals(WonderFoodVoiceAction.LINK_ACTION, command?.action)
        assertEquals(2, command?.linkActions?.size)
        assertEquals("inventory.edit", command?.linkActions?.first()?.type)
        assertEquals("12", command?.linkActions?.first()?.fields?.get("quantity"))
        assertEquals("grocery.delete", command?.linkActions?.last()?.type)
        assertEquals(true, command?.linkActions?.last()?.destructive)
    }

    @Test
    fun httpsActionLinkRejectsUntrustedHost() {
        assertNull(
            WonderFoodDeepLink.from(
                Intent(Intent.ACTION_VIEW, Uri.parse("https://evil.example/action?type=inventory.delete&id=42")),
            ),
        )
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
    fun obsoleteProposalBrokerLinksAreRejected() {
        assertNull(
            WonderFoodDeepLink.from(
                Intent(Intent.ACTION_VIEW, Uri.parse("https://wonderfood.app/proposal/obsolete-token")),
            ),
        )
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
