package com.wonderfood.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WonderFoodProviderContractsTest {
    @Test
    fun theMealDbProviderReturnsReviewableRecipePreviewWithAttribution() {
        val result = TheMealDbRecipeLookupProvider.search(
            RecipeLookupQuery(text = "pasta tomato"),
        ).first()

        assertEquals("themealdb", result.attribution.provider)
        assertTrue(result.value.titleText.contains("Arrabiata"))
        assertTrue(result.value.ingredientsText.contains("tomatoes"))
        assertTrue(result.attribution.sourceUrl.contains("themealdb"))
        assertTrue(result.cachePolicy.offlineReadable)
        assertTrue(result.warnings.single().contains("review", ignoreCase = true))
    }

    @Test
    fun openFoodFactsPackageProviderMapsBarcodeNutritionAndWarnings() {
        val result = OpenFoodFactsPackageLookupProvider().lookup(BarcodeLookupQuery("737628064502"))

        assertNotNull(result)
        requireNotNull(result)
        assertEquals("open_food_facts", result.attribution.provider)
        assertEquals("Generic rolled oats", result.value.name)
        assertEquals(150, result.value.calories)
        assertTrue(result.value.warnings.any { it.contains("Ingredients") })
        assertTrue(result.attribution.licenseNote.contains("incomplete coverage"))
    }
}
