package com.wonderfood.app.data

object TrustedFoodLookup {
    fun lookupBarcode(raw: String): FoodCandidate? {
        val barcode = normalizeBarcode(raw) ?: return null
        return bundledBarcodeItems[barcode]
    }

    fun normalizeBarcode(raw: String): String? {
        val digits = raw.filter(Char::isDigit)
        if (digits.length !in setOf(8, 12, 13, 14)) return null
        return digits
    }

    private val bundledBarcodeItems: Map<String, FoodCandidate> = mapOf(
        "012345678905" to FoodCandidate(
            name = "Generic Rolled Oats",
            quantity = "1 package",
            zone = StorageZone.PANTRY,
            category = "grain",
            servingText = "40 g",
            calories = 150,
            proteinGrams = 5.0,
            carbsGrams = 27.0,
            fatGrams = 3.0,
            nutritionSource = "bundled_barcode_provider",
            notes = "barcode:012345678905",
            imageUri = foodEmojiForName("oats"),
        ),
        "00000000000123" to FoodCandidate(
            name = "Generic Canned Tomatoes",
            quantity = "1 can",
            zone = StorageZone.PANTRY,
            category = "produce",
            servingText = "1/2 cup",
            calories = 25,
            proteinGrams = 1.0,
            carbsGrams = 5.0,
            fatGrams = 0.0,
            nutritionSource = "bundled_barcode_provider",
            notes = "barcode:00000000000123",
            imageUri = foodEmojiForName("tomatoes"),
        ),
    )
}
