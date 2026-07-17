package com.wonderfood.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FoodIntakeEnricherTest {
    @Test
    fun receiptFoodGetsCanonicalLocationExpiryAndTransparentUnknownNutrition() {
        val normalized = FoodDraftNormalizer.normalize(
            ReceiptDraft(
                items = listOf(
                    ReceiptItemDraft(
                        food = FoodCandidate(
                            name = "Mini Cucumbers",
                            category = "vegetable",
                            evidence = "MINI CUCUMBERS 3.99",
                        ),
                    ),
                ),
            ),
        ) as ReceiptDraft

        val item = normalized.items.single()
        assertEquals(ReceiptItemDisposition.INVENTORY, item.disposition)
        assertEquals("produce", item.food.category)
        assertEquals(StorageZone.FRIDGE, item.food.zone)
        assertEquals("app_storage_inference", item.food.zoneSource)
        assertNotNull(item.food.expiresAtMillis)
        assertEquals("app_shelf_life_estimate", item.food.expirySource)
        assertNull(item.food.calories)
        assertTrue(item.food.warnings.any { it.contains("Nutrition is unknown") })
    }

    @Test
    fun receiptHouseholdLineNeverBecomesFoodInventory() {
        val normalized = FoodDraftNormalizer.normalize(
            ReceiptDraft(
                items = listOf(
                    ReceiptItemDraft(food = FoodCandidate(name = "Oven Cleaner Foam", category = "cleaning")),
                ),
            ),
        ) as ReceiptDraft

        val item = normalized.items.single()
        assertEquals(ReceiptItemDisposition.HOUSEHOLD, item.disposition)
        assertEquals("household", item.food.category)
        assertNull(item.food.expiresAtMillis)
    }

    @Test
    fun receiptNutritionWithoutSourceIsMarkedAiEstimate() {
        val normalized = FoodDraftNormalizer.normalize(
            ReceiptDraft(
                items = listOf(
                    ReceiptItemDraft(
                        food = FoodCandidate(
                            name = "Greek Yogurt",
                            servingText = "170 g",
                            calories = 100,
                            proteinGrams = 17.0,
                        ),
                    ),
                ),
            ),
        ) as ReceiptDraft

        assertEquals("ai_estimate", normalized.items.single().food.nutritionSource)
        assertTrue(normalized.items.single().food.warnings.any { it.contains("Nutrition is an estimate") })
    }
}
