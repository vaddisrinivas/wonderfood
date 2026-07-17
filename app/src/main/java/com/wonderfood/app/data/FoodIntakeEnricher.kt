package com.wonderfood.app.data

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

/** One deterministic post-processing contract for AI, OCR, CSV, links, and manual drafts. */
object FoodIntakeEnricher {
    fun normalizeCandidate(candidate: FoodCandidate): FoodCandidate {
        val name = candidate.name.trim()
        val category = canonicalCategory(candidate.category, name)
        return candidate.copy(
            category = category,
            imageUri = candidate.imageUri?.takeIf { it.isNotBlank() } ?: foodEmojiForName(name),
            imageUrl = candidate.imageUrl.takeIf { it.isRemoteUrl() }.orEmpty(),
            confidence = candidate.confidence.coerceIn(0.0, 1.0),
            warnings = candidate.warnings.map(String::trim).filter(String::isNotBlank).distinct(),
        )
    }

    fun normalizeReceiptItem(
        item: ReceiptItemDraft,
        purchasedAtMillis: Long? = null,
    ): ReceiptItemDraft {
        var food = normalizeCandidate(item.food)
        val inferredDisposition = when {
            item.disposition != ReceiptItemDisposition.INVENTORY -> item.disposition
            isNonFood(food.name, food.category) -> ReceiptItemDisposition.HOUSEHOLD
            else -> ReceiptItemDisposition.INVENTORY
        }
        if (inferredDisposition != ReceiptItemDisposition.INVENTORY) {
            food = food.copy(
                category = if (inferredDisposition == ReceiptItemDisposition.HOUSEHOLD) "household" else food.category,
                warnings = (food.warnings + if (inferredDisposition == ReceiptItemDisposition.HOUSEHOLD) {
                    "Non-food receipt line; it will stay out of Kitchen."
                } else {
                    "Ignored receipt line; it will not change Kitchen."
                }).distinct(),
            )
            return item.copy(food = food, disposition = inferredDisposition)
        }

        if (food.zoneSource.isBlank()) {
            val inferredZone = classifyStorageZone(food.name)
            food = food.copy(
                zone = inferredZone,
                zoneSource = "app_storage_inference",
                warnings = (food.warnings + "Storage location inferred; change it if the package says otherwise.").distinct(),
            )
        }

        if (food.expiresAtMillis == null) {
            estimatedShelfLifeDays(food)?.let { days ->
                val purchaseDate = purchasedAtMillis
                    ?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate() }
                    ?: LocalDate.now()
                food = food.copy(
                    expiresAtMillis = purchaseDate.plusDays(days.toLong())
                        .atStartOfDay(ZoneId.systemDefault())
                        .toInstant()
                        .toEpochMilli(),
                    expirySource = "app_shelf_life_estimate",
                    warnings = (food.warnings + "Best-before is an estimate, not a label date; check the package.").distinct(),
                )
            }
        }

        val hasNutrition = listOf(food.calories, food.proteinGrams, food.carbsGrams, food.fatGrams).any { it != null }
        food = when {
            hasNutrition && food.nutritionSource.isBlank() -> food.copy(
                nutritionSource = "ai_estimate",
                warnings = (food.warnings + "Nutrition is an estimate; verify against the package label.").distinct(),
            )
            !hasNutrition -> food.copy(
                warnings = (food.warnings + "Nutrition is unknown; no values were invented.").distinct(),
            )
            else -> food
        }
        return item.copy(food = food, disposition = inferredDisposition)
    }

    fun persistReceiptCandidate(candidate: FoodCandidate, receiptId: Long?): FoodCandidate {
        val trace = buildList {
            receiptId?.let { add("Receipt #$it") }
            candidate.evidence.takeIf { it.isNotBlank() }?.let { add("Evidence: ${it.take(220)}") }
            candidate.zoneSource.takeIf { it.isNotBlank() }?.let { add("Storage source: $it") }
            candidate.expirySource.takeIf { it.isNotBlank() }?.let { add("Best-before source: $it") }
            add("Inference confidence: ${"%.2f".format(Locale.US, candidate.confidence)}")
            candidate.warnings.forEach { add("Review: $it") }
        }.joinToString("\n")
        return candidate.copy(
            notes = listOf(candidate.notes.trim(), trace).filter(String::isNotBlank).joinToString("\n"),
        )
    }

    fun canonicalCategory(raw: String, name: String): String {
        val normalized = raw.trim().lowercase(Locale.US).replace('-', '_').replace(' ', '_')
        val alias = when (normalized) {
            "", "unknown" -> categorizeFood(name)
            "vegetable", "vegetables", "produce_vegetable" -> "produce"
            "fruits" -> "fruit"
            "grains", "carb", "carbs", "bakery" -> "grain"
            "protein", "proteins", "meat", "seafood" -> "protein"
            "milk", "cheese" -> "dairy"
            "drink", "drinks" -> "beverage"
            "cleaning", "cleaner", "personal_care", "non_food" -> "household"
            "sauce", "sauces" -> "condiment"
            "spices", "herb", "herbs" -> "spice"
            else -> normalized
        }
        return alias.takeIf { it in CANONICAL_CATEGORIES } ?: "other"
    }

    fun isNonFood(name: String, category: String = ""): Boolean {
        val text = "$name $category".lowercase(Locale.US)
        return NON_FOOD_WORDS.any { it in text }
    }

    private fun estimatedShelfLifeDays(food: FoodCandidate): Int? {
        val text = food.name.lowercase(Locale.US)
        if (isNonFood(text, food.category)) return null
        return when {
            food.zone == StorageZone.FREEZER -> 90
            listOf("fish", "salmon", "shrimp", "chicken", "beef", "steak", "meat").any { it in text } -> 2
            "berry" in text || "berries" in text -> 4
            "cucumber" in text || "eggplant" in text || "spinach" in text || "lettuce" in text -> 7
            "tomato" in text || "jalap" in text || "pepper" in text -> 10
            "lime" in text || "lemon" in text -> 21
            "milk" in text || "yogurt" in text || "curd" in text -> 10
            "egg" in text -> 28
            "bread" in text || "tortilla" in text -> if (food.zone == StorageZone.FRIDGE) 21 else 10
            food.zone == StorageZone.FRIDGE -> 10
            food.category in setOf("produce", "fruit") -> 14
            food.category in setOf("grain", "spice", "condiment") -> 180
            food.category == "beverage" -> 90
            else -> null
        }
    }

    private fun String.isRemoteUrl(): Boolean = startsWith("https://") || startsWith("http://")

    private val CANONICAL_CATEGORIES = setOf(
        "protein", "produce", "fruit", "grain", "dairy", "fat", "spice", "condiment",
        "prepared", "beverage", "household", "other",
    )

    private val NON_FOOD_WORDS = setOf(
        "cleaner", "cleaning", "detergent", "bleach", "soap", "shampoo", "conditioner",
        "toothpaste", "toothbrush", "deodorant", "paper towel", "toilet paper", "trash bag",
        "foil", "plastic wrap", "battery", "medicine", "vitamin", "household",
    )
}
