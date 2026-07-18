package com.wonderfood.app

import android.content.Intent
import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class WonderFoodDeepLinkTest {
    @Test
    fun actionLinkAcceptsCanonicalInventoryTarget() {
        val command = WonderFoodDeepLink.from(
            Intent(
                Intent.ACTION_VIEW,
                Uri.parse("wonderfood://action?type=inventory.add&name=Eggs&quantity=12&zone=fridge&idempotencyKey=test-1"),
            ),
        )

        assertNotNull(command)
        requireNotNull(command)
        assertEquals(WonderFoodVoiceAction.LINK_ACTION, command.action)
        assertEquals("test-1", command.idempotencyKey)
        assertEquals(1, command.linkActions.size)
        val action = command.linkActions.single()
        assertEquals("inventory.add", action.type)
        assertEquals("inventory", action.targetKind)
        assertEquals("Eggs", action.displayName)
        assertEquals("12", action.fields["quantity"])
        assertEquals("fridge", action.fields["zone"])
    }

    @Test
    fun actionLinkAcceptsCanonicalGroceryTarget() {
        val command = WonderFoodDeepLink.from(
            Intent(
                Intent.ACTION_VIEW,
                Uri.parse("wonderfood://action?type=grocery.add&name=Bananas&quantity=6&idempotencyKey=test-2"),
            ),
        )

        assertNotNull(command)
        requireNotNull(command)
        assertEquals(WonderFoodVoiceAction.LINK_ACTION, command.action)
        val action = command.linkActions.single()
        assertEquals("grocery.add", action.type)
        assertEquals("grocery", action.targetKind)
        assertEquals("Bananas", action.displayName)
        assertEquals("6", action.fields["quantity"])
    }

    @Test
    fun commandIntentAcceptsStructuredExtras() {
        val command = WonderFoodDeepLink.from(
            Intent(WonderFoodCommandContract.ACTION_COMMAND)
                .putExtra(WonderFoodCommandContract.EXTRA_REQUEST_ID, "routine-1")
                .putExtra(WonderFoodCommandContract.EXTRA_ACTION_TYPE, "inventory.add")
                .putExtra(WonderFoodCommandContract.EXTRA_NAME, "Milk")
                .putExtra("quantity", "1 gal")
                .putExtra("zone", "fridge")
                .putExtra("category", "dairy"),
        )

        assertNotNull(command)
        requireNotNull(command)
        assertEquals(WonderFoodVoiceAction.LINK_ACTION, command.action)
        assertEquals("routine-1", command.idempotencyKey)
        val action = command.linkActions.single()
        assertEquals("inventory.add", action.type)
        assertEquals("inventory", action.targetKind)
        assertEquals("Milk", action.displayName)
        assertEquals("1 gal", action.fields["quantity"])
        assertEquals("fridge", action.fields["zone"])
    }

    @Test
    fun commandIntentFallsBackToAiReviewText() {
        val command = WonderFoodDeepLink.from(
            Intent(WonderFoodCommandContract.ACTION_COMMAND)
                .putExtra(WonderFoodCommandContract.EXTRA_REQUEST_ID_SNAKE, "whatsapp-1")
                .putExtra(Intent.EXTRA_TEXT, "WhatsApp: bought rice, dal, and yogurt"),
        )

        assertNotNull(command)
        requireNotNull(command)
        assertEquals(WonderFoodVoiceAction.AI_REVIEW, command.action)
        assertEquals("whatsapp-1", command.idempotencyKey)
        assertEquals("WhatsApp: bought rice, dal, and yogurt", command.text)
    }

    @Test
    fun commandIntentAcceptsBulkActionsJson() {
        val command = WonderFoodDeepLink.from(
            Intent(WonderFoodCommandContract.ACTION_COMMAND)
                .putExtra(WonderFoodCommandContract.EXTRA_IDEMPOTENCY_KEY, "bulk-1")
                .putExtra(
                    WonderFoodCommandContract.EXTRA_ACTIONS_JSON,
                    """
                    [
                      {"type":"inventory.add","name":"Eggs","quantity":"12","zone":"fridge"},
                      {"type":"grocery.add","name":"Bananas","quantity":"6"}
                    ]
                    """.trimIndent(),
                ),
        )

        assertNotNull(command)
        requireNotNull(command)
        assertEquals(WonderFoodVoiceAction.LINK_ACTION, command.action)
        assertEquals("bulk-1", command.idempotencyKey)
        assertEquals(2, command.linkActions.size)
        assertEquals("inventory", command.linkActions[0].targetKind)
        assertEquals("grocery", command.linkActions[1].targetKind)
    }

    @Test
    fun shareIntentAndCommandTextIntentShareSameReviewEnvelope() {
        val shared = WonderFoodDeepLink.from(
            Intent(Intent.ACTION_SEND)
                .setType("text/plain")
                .putExtra(Intent.EXTRA_TEXT, "Need oats, bananas, and chicken thighs"),
        )
        val actionText = WonderFoodDeepLink.from(
            Intent(WonderFoodCommandContract.ACTION_COMMAND)
                .putExtra(Intent.EXTRA_TEXT, "Need oats, bananas, and chicken thighs"),
        )

        assertNotNull(shared)
        assertNotNull(actionText)
        assertEquals(WonderFoodVoiceAction.AI_REVIEW, requireNotNull(shared).action)
        assertEquals(WonderFoodVoiceAction.AI_REVIEW, requireNotNull(actionText).action)
        assertEquals(requireNotNull(shared).text, requireNotNull(actionText).text)
        assertEquals("Need oats, bananas, and chicken thighs", requireNotNull(shared).text)
    }

    @Test
    fun actionIntentAndDeepLinkActionsResolveEquivalentLinkActions() {
        val deepLink = WonderFoodDeepLink.from(
            Intent(
                Intent.ACTION_VIEW,
                Uri.parse("wonderfood://action?type=inventory.add&name=Eggs&quantity=12&zone=fridge&idempotencyKey=dl-1"),
            ),
        )
        val commandIntent = WonderFoodDeepLink.from(
            Intent(WonderFoodCommandContract.ACTION_COMMAND)
                .putExtra(WonderFoodCommandContract.EXTRA_REQUEST_ID, "dl-1")
                .putExtra(WonderFoodCommandContract.EXTRA_ACTION_TYPE, "inventory.add")
                .putExtra(WonderFoodCommandContract.EXTRA_NAME, "Eggs")
                .putExtra("quantity", "12")
                .putExtra("zone", "fridge"),
        )

        val deepAction = requireNotNull(deepLink).linkActions.single()
        val intentAction = requireNotNull(commandIntent).linkActions.single()
        assertEquals(deepAction.type, intentAction.type)
        assertEquals(deepAction.targetKind, intentAction.targetKind)
        assertEquals(deepAction.displayName, intentAction.displayName)
        assertEquals(deepAction.fields["name"], intentAction.fields["name"])
        assertEquals(deepAction.fields["quantity"], intentAction.fields["quantity"])
        assertEquals(deepAction.fields["zone"], intentAction.fields["zone"])
    }

    @Test
    fun deepLinkAndCommandIntentBulkActionsResolveEquivalentPayloads() {
        val actions = """
            [
              {"type":"inventory.add","name":"Eggs","quantity":"12","zone":"fridge"},
              {"type":"grocery.add","name":"Bananas","quantity":"6"}
            ]
            """.trimIndent()
        val deepLink = WonderFoodDeepLink.from(
            Intent(
                Intent.ACTION_VIEW,
                Uri.parse("wonderfood://action?actions=${Uri.encode(actions)}"),
            ),
        )
        val commandIntent = WonderFoodDeepLink.from(
            Intent(WonderFoodCommandContract.ACTION_COMMAND)
                .putExtra(WonderFoodCommandContract.EXTRA_IDEMPOTENCY_KEY, "bulk-1")
                .putExtra(WonderFoodCommandContract.EXTRA_ACTIONS_JSON, actions),
        )

        assertEquals(
            requireNotNull(deepLink).linkActions,
            requireNotNull(commandIntent).linkActions,
        )
    }

    @Test
    fun actionLinkRejectsUnknownVerb() {
        val command = WonderFoodDeepLink.from(
            Intent(
                Intent.ACTION_VIEW,
                Uri.parse("wonderfood://action?type=inventory.overwrite&name=Eggs&quantity=1"),
            ),
        )

        assertNull(command)
    }

    @Test
    fun bulkActionRejectsWholePayloadWhenOneActionIsUnsupported() {
        val actions = """[{"type":"inventory.add","name":"Eggs"},{"type":"inventory.overwrite","name":"Milk"}]"""
        val command = WonderFoodDeepLink.from(
            Intent(
                Intent.ACTION_VIEW,
                Uri.parse("wonderfood://action?actions=${Uri.encode(actions)}"),
            ),
        )

        assertNull(command)
    }

    @Test
    fun canonicalPlanningEntryActionMapsToPlanEntry() {
        val command = WonderFoodDeepLink.from(
            Intent(
                Intent.ACTION_VIEW,
                Uri.parse("wonderfood://action?type=planning.update_meal_plan_entry&id=7&status=eaten"),
            ),
        )

        assertNotNull(command)
        assertEquals("plan_entry", requireNotNull(command).linkActions.single().targetKind)
    }
}
