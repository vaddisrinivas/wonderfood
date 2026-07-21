package com.wonderfood.app.sync

import com.wonderfood.app.data.FoodCandidate
import com.wonderfood.app.data.FoodPreferences
import com.wonderfood.app.data.GroceryStatus
import com.wonderfood.app.data.MealLogDraft
import com.wonderfood.app.data.MealPlanDraft
import com.wonderfood.app.data.MealPlanEntryDraft
import com.wonderfood.app.data.MealSlot
import com.wonderfood.app.data.RecipeDraft
import com.wonderfood.app.data.ReceiptDraft
import com.wonderfood.app.data.ReceiptItemDisposition
import com.wonderfood.app.data.ReceiptItemDraft
import com.wonderfood.app.data.StorageZone
import com.wonderfood.app.data.categorizeFood
import com.wonderfood.app.data.classifyStorageZone
import com.wonderfood.core.model.household.HouseholdSnapshot
import com.wonderfood.core.model.household.HouseholdEntityType
import com.wonderfood.core.model.household.InventoryLot
import com.wonderfood.core.model.household.Item
import com.wonderfood.core.model.household.MealEntry
import com.wonderfood.core.model.household.MealEntryStatus
import com.wonderfood.core.model.household.MealPlan
import com.wonderfood.core.model.household.NutritionSnapshot
import com.wonderfood.core.model.household.Quantity
import com.wonderfood.core.model.household.QuantityUnit
import com.wonderfood.core.model.household.Purchase
import com.wonderfood.core.model.household.PurchaseLine
import com.wonderfood.core.model.household.PurchaseLineDisposition
import com.wonderfood.core.model.household.Recipe
import com.wonderfood.core.model.household.RecipeIngredient
import com.wonderfood.core.model.household.RecipeStep
import com.wonderfood.core.model.household.ShoppingLine
import com.wonderfood.core.model.household.ShoppingLineStatus
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

object WonderFoodCsvGateway {
    private val headers = listOf(
        "record_type",
        "id",
        "parent_id",
        "name",
        "title",
        "quantity",
        "zone",
        "status",
        "category",
        "serving_text",
        "calories",
        "protein_g",
        "carbs_g",
        "fat_g",
        "nutrition_source",
        "notes",
        "image_uri",
        "image_url",
        "expires_at",
        "source",
        "ingredients",
        "steps",
        "servings",
        "prep_minutes",
        "tags",
        "rating",
        "meal_slot",
        "used_items_text",
        "logged_date_epoch_day",
        "days_text",
        "grocery_hint",
        "start_date_epoch_day",
        "date_epoch_day",
        "slot",
        "calorie_target",
        "raw_text",
        "preference_key",
        "preference_value",
        "merchant",
        "purchased_at_millis",
        "currency",
        "subtotal_cents",
        "tax_cents",
        "total_cents",
        "amount_cents",
        "disposition",
    )

    fun export(snapshot: HouseholdSnapshot): String =
        buildString {
            appendLine(headers.toCsvLine())
            val itemsById = snapshot.items.associateBy { it.metadata.id }
            val nutritionById = snapshot.nutritionSnapshots.associateBy { it.metadata.id }
            val itemNutritionByItemId = snapshot.nutritionSnapshots
                .filter { it.subject.type == HouseholdEntityType.ITEM }
                .groupBy { it.subject.id }
            snapshot.inventoryLots.forEach { lot ->
                val item = itemsById[lot.itemId] ?: return@forEach
                appendLine(canonicalInventoryRow(item, lot, itemNutritionByItemId[item.metadata.id]?.firstOrNull()).toCsvLine())
            }
            snapshot.items
                .filter { item -> snapshot.inventoryLots.none { it.itemId == item.metadata.id } }
                .forEach { item ->
                    appendLine(canonicalInventoryRow(item, lot = null, itemNutritionByItemId[item.metadata.id]?.firstOrNull()).toCsvLine())
                }
            snapshot.shoppingLines.forEach { line ->
                appendLine(canonicalShoppingRow(line, line.itemId?.let(itemsById::get)).toCsvLine())
            }
            val ingredientsByRecipeId = snapshot.recipeIngredients.groupBy { it.recipeId }
            val stepsByRecipeId = snapshot.recipeSteps.groupBy { it.recipeId }
            snapshot.recipes.forEach { recipe ->
                appendLine(
                    canonicalRecipeRow(
                        recipe,
                        ingredientsByRecipeId[recipe.metadata.id].orEmpty(),
                        stepsByRecipeId[recipe.metadata.id].orEmpty(),
                    ).toCsvLine(),
                )
            }
            val mealEntriesByPlanId = snapshot.mealEntries
                .filter { it.mealPlanId != null }
                .groupBy { requireNotNull(it.mealPlanId) }
            snapshot.mealPlans.forEach { plan ->
                appendLine(canonicalMealPlanRow(plan, mealEntriesByPlanId[plan.metadata.id].orEmpty()).toCsvLine())
                mealEntriesByPlanId[plan.metadata.id].orEmpty().forEach { entry ->
                    appendLine(canonicalMealPlanEntryRow(entry).toCsvLine())
                }
            }
            snapshot.mealEntries
                .filter { it.mealPlanId == null || it.status == MealEntryStatus.EATEN }
                .forEach { entry ->
                    appendLine(canonicalMealLogRow(entry, entry.nutritionSnapshotIds.firstNotNullOfOrNull(nutritionById::get)).toCsvLine())
                }
            val purchaseLinesByPurchaseId = snapshot.purchaseLines.groupBy { it.purchaseId }
            snapshot.purchases.forEach { purchase ->
                appendLine(canonicalReceiptRow(purchase).toCsvLine())
                purchaseLinesByPurchaseId[purchase.metadata.id].orEmpty().forEach { line ->
                    appendLine(canonicalReceiptLineRow(line).toCsvLine())
                }
            }
        }

    fun parse(csv: String): WonderFoodCsvImport {
        val rows = parseCsv(csv)
        if (rows.isEmpty()) return WonderFoodCsvImport()
        val header = rows.first().map { it.trim() }
        val dataRows = rows.drop(1).filter { row -> row.any { it.isNotBlank() } }
        val mealPlans = linkedMapOf<String, MutableMealPlanCsv>()
        val preferences = mutableMapOf<String, String>()
        val inventory = mutableListOf<FoodCandidate>()
        val groceries = mutableListOf<FoodCandidate>()
        val recipes = mutableListOf<RecipeDraft>()
        val mealLogs = mutableListOf<MealLogDraft>()
        val receipts = linkedMapOf<String, MutableReceiptCsv>()

        dataRows.forEach { cells ->
            val row = header.indices.associate { index -> header[index] to cells.getOrElse(index) { "" } }
            val type = row.value("record_type").ifBlank {
                if (row.value("name").isNotBlank()) "inventory" else ""
            }.lowercase()
            when (type) {
                "inventory", "kitchen" -> inventory += row.toFoodCandidate(defaultZone = row.value("zone").parseZone(row.value("name")))
                "grocery", "shopping" -> groceries += row.toFoodCandidate(defaultZone = classifyStorageZone(row.value("name")))
                "recipe" -> recipes += RecipeDraft(
                    titleText = row.value("title").ifBlank { row.value("name").ifBlank { "Imported recipe" } },
                    ingredientsText = row.value("ingredients"),
                    stepsText = row.value("steps"),
                    servings = row.value("servings").toIntOrNull(),
                    prepMinutes = row.value("prep_minutes").toIntOrNull(),
                    tags = row.value("tags").ifBlank { "csv_import" },
                    imageUri = row.value("image_uri").ifBlank { null },
                    imageUrl = row.value("image_url"),
                )
                "meal_log", "meal" -> mealLogs += MealLogDraft(
                    titleText = row.value("title").ifBlank { row.value("name").ifBlank { "Imported meal" } },
                    calories = row.value("calories").toIntOrNull(),
                    proteinGrams = row.value("protein_g").toDoubleOrNull(),
                    carbsGrams = row.value("carbs_g").toDoubleOrNull(),
                    fatGrams = row.value("fat_g").toDoubleOrNull(),
                    mealSlot = row.value("meal_slot").ifBlank { row.value("slot") }.parseMealSlot(),
                    usedItemsText = row.value("used_items_text"),
                    loggedDateEpochDay = row.value("logged_date_epoch_day").toLongOrNull(),
                    source = row.value("source").ifBlank { "csv_import" },
                )
                "receipt" -> {
                    val id = row.value("id").ifBlank { "receipt:${receipts.size + 1}" }
                    receipts.getOrPut(id) { MutableReceiptCsv(id = id) }.apply {
                        merchant = row.value("merchant").ifBlank { row.value("title").ifBlank { merchant } }
                        purchasedAtMillis = row.value("purchased_at_millis").toLongOrNull() ?: purchasedAtMillis
                        currencyCode = row.value("currency").ifBlank { currencyCode }
                        subtotalCents = row.value("subtotal_cents").toLongOrNull() ?: subtotalCents
                        taxCents = row.value("tax_cents").toLongOrNull() ?: taxCents
                        totalCents = row.value("total_cents").toLongOrNull()
                            ?: row.value("amount_cents").toLongOrNull()
                            ?: totalCents
                        rawText = row.value("raw_text").ifBlank { rawText }
                    }
                }
                "receipt_line", "purchase_line" -> {
                    val parentId = row.value("parent_id").ifBlank { "receipt:${receipts.size + 1}" }
                    val receipt = receipts.getOrPut(parentId) { MutableReceiptCsv(id = parentId) }
                    receipt.items += ReceiptItemDraft(
                        food = row.toFoodCandidate(defaultZone = row.value("zone").parseZone(row.value("name"))),
                        disposition = row.value("disposition").toReceiptDisposition(row.value("category")),
                        receiptLine = row.value("raw_text").ifBlank { row.value("notes") },
                        linePriceCents = row.value("amount_cents").toLongOrNull(),
                    )
                }
                "meal_plan", "plan" -> {
                    val key = row.value("id").ifBlank { "plan:${mealPlans.size + 1}" }
                    mealPlans.getOrPut(key) { MutableMealPlanCsv(id = key) }.apply {
                        title = row.value("title").ifBlank { title }
                        daysText = row.value("days_text").ifBlank { daysText }
                        groceryHint = row.value("grocery_hint").ifBlank { groceryHint }
                        startDateEpochDay = row.value("start_date_epoch_day").toLongOrNull() ?: startDateEpochDay
                    }
                }
                "meal_plan_entry", "plan_entry" -> {
                    val parentId = row.value("parent_id").ifBlank { "plan:${mealPlans.size + 1}" }
                    val plan = mealPlans.getOrPut(parentId) { MutableMealPlanCsv(id = parentId) }
                    val dateEpochDay = row.value("date_epoch_day").toLongOrNull()
                    val dayOffset = if (dateEpochDay != null && plan.startDateEpochDay != null) {
                        (dateEpochDay - requireNotNull(plan.startDateEpochDay)).toInt()
                    } else {
                        plan.entries.size
                    }
                    plan.entries += MealPlanEntryDraft(
                        dayOffset = dayOffset,
                        slot = row.value("slot").parseMealSlot(),
                        title = row.value("title").ifBlank { "Imported planned meal" },
                        calorieTarget = row.value("calorie_target").toIntOrNull(),
                    )
                }
                "preference", "preferences" -> {
                    val key = row.value("preference_key")
                    if (key.isNotBlank()) preferences[key] = row.value("preference_value")
                }
            }
        }

        return WonderFoodCsvImport(
            inventory = inventory,
            groceries = groceries,
            recipes = recipes,
            receipts = receipts.values.mapNotNull { it.toDraftOrNull() },
            mealLogs = mealLogs,
            mealPlans = mealPlans.values.map { it.toDraft() },
            preferences = preferences.toFoodPreferencesOrNull(),
        )
    }

    private fun canonicalInventoryRow(item: Item, lot: InventoryLot?, nutrition: NutritionSnapshot?): List<String> =
        row(
            "record_type" to "inventory",
            "id" to (lot?.metadata?.id?.value ?: item.metadata.id.value),
            "parent_id" to item.metadata.id.value,
            "name" to item.name,
            "quantity" to (lot?.quantity ?: Quantity.unknown(item.defaultUnit)).csvText(),
            "zone" to "",
            "status" to (lot?.status?.name ?: "ITEM"),
            "category" to (item.category ?: item.kind.name.lowercase()),
            "serving_text" to nutrition?.basis?.csvText(),
            "calories" to nutrition?.values?.energyKcal?.value,
            "protein_g" to nutrition?.values?.proteinGrams?.value,
            "carbs_g" to nutrition?.values?.carbohydrateGrams?.value,
            "fat_g" to nutrition?.values?.fatGrams?.value,
            "nutrition_source" to nutrition?.provider,
            "notes" to listOfNotNull(
                item.notes,
                item.brand?.let { "Brand: $it" },
                item.preferredStore?.let { "Preferred store: $it" },
            ).joinToString(" | "),
            "expires_at" to lot?.expiresOn?.value,
            "source" to item.metadata.source.label,
        )

    private fun canonicalShoppingRow(line: ShoppingLine, item: Item?): List<String> =
        row(
            "record_type" to "grocery",
            "id" to line.metadata.id.value,
            "parent_id" to line.itemId?.value,
            "name" to line.displayName,
            "quantity" to line.quantity.csvText(),
            "status" to line.status.csvStatus(),
            "category" to (line.category ?: item?.category ?: item?.kind?.name?.lowercase()),
            "notes" to listOfNotNull(
                line.preferredStore?.let { "Preferred store: $it" },
                "Reason: ${line.reason.name.lowercase()}",
            ).joinToString(" | "),
            "source" to line.metadata.source.label,
        )

    private fun canonicalRecipeRow(recipe: Recipe, ingredients: List<RecipeIngredient>, steps: List<RecipeStep>): List<String> =
        row(
            "record_type" to "recipe",
            "id" to recipe.metadata.id.value,
            "title" to recipe.name,
            "ingredients" to ingredients
                .sortedBy { it.order }
                .joinToString("\n") { it.originalText },
            "steps" to steps
                .sortedBy { it.order }
                .joinToString("\n") { it.instruction },
            "servings" to recipe.yield.amount?.value,
            "prep_minutes" to recipe.prepMinutes,
            "tags" to listOfNotNull(recipe.category, recipe.cuisine)
                .plus(recipe.tags)
                .distinct()
                .joinToString(", "),
            "notes" to listOfNotNull(
                recipe.description,
                recipe.sourceUrl?.let { "Source URL: $it" },
                recipe.author?.let { "Author: $it" },
                recipe.difficulty?.let { "Difficulty: $it" },
                recipe.totalMinutes?.let { "Total minutes: $it" },
            ).joinToString(" | "),
            "source" to recipe.metadata.source.label,
        )

    private fun canonicalReceiptRow(purchase: Purchase): List<String> =
        row(
            "record_type" to "receipt",
            "id" to purchase.metadata.id.value,
            "merchant" to (purchase.merchantId?.value ?: purchase.paymentNote.extractMerchant()),
            "purchased_at_millis" to purchase.occurredAt.epochMillis,
            "currency" to listOf(purchase.total, purchase.subtotal, purchase.tax)
                .firstNotNullOfOrNull { it?.currencyCode },
            "subtotal_cents" to purchase.subtotal?.minorUnits,
            "tax_cents" to purchase.tax?.minorUnits,
            "total_cents" to purchase.total?.minorUnits,
            "amount_cents" to purchase.total?.minorUnits,
            "status" to purchase.status.name,
            "source" to purchase.metadata.source.label,
        )

    private fun canonicalMealPlanRow(plan: MealPlan, entries: List<MealEntry>): List<String> =
        row(
            "record_type" to "meal_plan",
            "id" to plan.metadata.id.value,
            "title" to plan.name,
            "days_text" to entries
                .sortedBy { it.scheduledAt.epochMillis }
                .joinToString("\n") { entry ->
                    "${entry.scheduledAt.toEpochDay().toIsoDate()} ${entry.slot}: ${entry.title}"
                },
            "status" to plan.status.name,
            "start_date_epoch_day" to plan.startsOn.toEpochDay(),
            "source" to plan.metadata.source.label,
        )

    private fun canonicalMealPlanEntryRow(entry: MealEntry): List<String> =
        row(
            "record_type" to "meal_plan_entry",
            "id" to entry.metadata.id.value,
            "parent_id" to entry.mealPlanId?.value,
            "title" to entry.title,
            "date_epoch_day" to entry.scheduledAt.toEpochDay(),
            "slot" to entry.slot,
            "status" to entry.status.name,
            "notes" to entry.notes,
            "source" to entry.metadata.source.label,
        )

    private fun canonicalMealLogRow(entry: MealEntry, nutrition: NutritionSnapshot?): List<String> =
        row(
            "record_type" to "meal_log",
            "id" to entry.metadata.id.value,
            "title" to entry.title,
            "calories" to nutrition?.values?.energyKcal?.value,
            "protein_g" to nutrition?.values?.proteinGrams?.value,
            "carbs_g" to nutrition?.values?.carbohydrateGrams?.value,
            "fat_g" to nutrition?.values?.fatGrams?.value,
            "nutrition_source" to nutrition?.provider,
            "meal_slot" to entry.slot,
            "logged_date_epoch_day" to entry.scheduledAt.toEpochDay(),
            "notes" to entry.notes,
            "source" to entry.metadata.source.label,
        )

    private fun canonicalReceiptLineRow(line: PurchaseLine): List<String> =
        row(
            "record_type" to "receipt_line",
            "id" to line.metadata.id.value,
            "parent_id" to line.purchaseId.value,
            "name" to line.displayName,
            "quantity" to line.quantity.csvText(),
            "category" to line.spendCategory,
            "amount_cents" to (line.finalAmount ?: line.lineSubtotal)?.minorUnits,
            "currency" to (line.finalAmount ?: line.lineSubtotal)?.currencyCode,
            "disposition" to line.disposition.csvDisposition(),
            "source" to line.metadata.source.label,
        )

    private fun row(vararg values: Pair<String, Any?>): List<String> {
        val map = values.associate { it.first to it.second?.toString().orEmpty() }
        return headers.map { map[it].orEmpty() }
    }

    private fun FoodPreferences.toCsvPreferenceRows(): List<List<String>> =
        listOf(
            "diet_style" to dietStyle,
            "allergies" to allergies,
            "dislikes" to dislikes,
            "preferred_staples" to preferredStaples,
            "preferred_cuisines" to preferredCuisines,
            "preferred_stores" to preferredStores,
            "calorie_goal" to calorieGoal,
            "protein_goal" to proteinGoal,
            "health_notes" to healthNotes,
            "ai_instructions" to customAiInstructions,
            "ai_skill_override" to aiSkillOverride,
        ).map { (key, value) ->
            row(
                "record_type" to "preference",
                "preference_key" to key,
                "preference_value" to value,
            )
        }

    private fun Map<String, String>.toFoodCandidate(defaultZone: StorageZone): FoodCandidate {
        val name = value("name").ifBlank { value("title").ifBlank { "Imported food" } }
        val nutritionInNotes = value("notes").parseEmbeddedNutrition()
        return FoodCandidate(
            name = name,
            quantity = value("quantity"),
            zone = value("zone").parseZone(name, defaultZone),
            category = value("category").ifBlank { categorizeFood(name) },
            servingText = value("serving_text").ifBlank { nutritionInNotes.servingText },
            calories = value("calories").toIntOrNull() ?: nutritionInNotes.calories,
            proteinGrams = value("protein_g").toDoubleOrNull() ?: nutritionInNotes.proteinGrams,
            carbsGrams = value("carbs_g").toDoubleOrNull() ?: nutritionInNotes.carbsGrams,
            fatGrams = value("fat_g").toDoubleOrNull() ?: nutritionInNotes.fatGrams,
            nutritionSource = value("nutrition_source").ifBlank { "csv_import" },
            notes = value("notes"),
            imageUri = value("image_uri").ifBlank { null },
            imageUrl = value("image_url"),
            expiresAtMillis = value("expires_at").toLongOrNull(),
            evidence = "csv:${value("id").ifBlank { name }}",
            confidence = if (nutritionInNotes.hasValues) 0.8 else 0.75,
            zoneSource = "csv_import",
        )
    }

    private data class EmbeddedNutrition(
        val servingText: String = "",
        val calories: Int? = null,
        val proteinGrams: Double? = null,
        val carbsGrams: Double? = null,
        val fatGrams: Double? = null,
    ) {
        val hasValues: Boolean
            get() = calories != null || proteinGrams != null || carbsGrams != null || fatGrams != null
    }

    private fun String.parseEmbeddedNutrition(): EmbeddedNutrition {
        val section = substringAfter("Nutrition per ", "")
            .substringBefore('|')
            .trim()
        if (section.isBlank()) return EmbeddedNutrition()
        val serving = section.substringBefore(':').trim().replace('_', ' ')
        val facts = Regex("""([a-zA-Z_]+)=(-?\d+(?:\.\d+)?)""")
            .findAll(section.substringAfter(':', ""))
            .associate { match -> match.groupValues[1].lowercase() to match.groupValues[2] }
        return EmbeddedNutrition(
            servingText = serving,
            calories = facts["kcal"]?.toDoubleOrNull()?.toInt(),
            proteinGrams = facts["protein_g"]?.toDoubleOrNull(),
            carbsGrams = facts["carbs_g"]?.toDoubleOrNull(),
            fatGrams = facts["fat_g"]?.toDoubleOrNull(),
        )
    }

    private fun Map<String, String>.value(key: String): String = get(key).orEmpty().trim()

    private fun Map<String, String>.toFoodPreferencesOrNull(): FoodPreferences? {
        if (isEmpty()) return null
        return FoodPreferences(
            dietStyle = get("diet_style").orEmpty(),
            allergies = get("allergies").orEmpty(),
            dislikes = get("dislikes").orEmpty(),
            preferredStaples = get("preferred_staples").orEmpty(),
            preferredCuisines = get("preferred_cuisines").orEmpty(),
            preferredStores = get("preferred_stores").orEmpty(),
            calorieGoal = get("calorie_goal").orEmpty(),
            proteinGoal = get("protein_goal").orEmpty(),
            healthNotes = get("health_notes").orEmpty(),
            customAiInstructions = get("ai_instructions").orEmpty(),
            aiSkillOverride = get("ai_skill_override").orEmpty(),
        )
    }

    private fun MutableMealPlanCsv.toDraft(): MealPlanDraft =
        MealPlanDraft(
            titleText = title.ifBlank { "Imported meal plan" },
            daysText = daysText,
            groceryHint = groceryHint,
            entries = entries,
            startDateEpochDay = startDateEpochDay,
        )

    private fun MutableReceiptCsv.toDraftOrNull(): ReceiptDraft? {
        if (items.isEmpty()) return null
        return ReceiptDraft(
            items = items,
            merchant = merchant,
            purchasedAtMillis = purchasedAtMillis,
            currencyCode = currencyCode.ifBlank { "USD" },
            subtotalCents = subtotalCents,
            taxCents = taxCents,
            totalCents = totalCents,
            rawText = rawText,
            sourceLabel = "csv_import",
        )
    }

    private fun String.parseZone(name: String, fallback: StorageZone = classifyStorageZone(name)): StorageZone =
        runCatching { StorageZone.valueOf(trim().uppercase()) }.getOrDefault(fallback)

    private fun String.parseMealSlot(): MealSlot =
        runCatching { MealSlot.valueOf(trim().uppercase()) }.getOrDefault(MealSlot.FLEX)

    private fun Quantity.csvText(): String {
        val amountText = amount?.value.orEmpty()
        val unitText = unit.takeUnless { it == QuantityUnit.UNKNOWN }?.code.orEmpty()
        return listOf(amountText, unitText)
            .filter { it.isNotBlank() }
            .joinToString(" ")
    }

    private fun ShoppingLineStatus.csvStatus(): String =
        when (this) {
            ShoppingLineStatus.NEEDED -> GroceryStatus.NEEDED.name
            ShoppingLineStatus.IN_CART -> GroceryStatus.NEEDED.name
            ShoppingLineStatus.PURCHASED -> GroceryStatus.BOUGHT.name
            ShoppingLineStatus.SKIPPED,
            ShoppingLineStatus.ARCHIVED,
            -> GroceryStatus.NEEDED.name
        }

    private fun PurchaseLineDisposition.csvDisposition(): String =
        when (this) {
            PurchaseLineDisposition.INVENTORY -> ReceiptItemDisposition.INVENTORY.name
            PurchaseLineDisposition.CONSUMED,
            PurchaseLineDisposition.SERVICE,
            PurchaseLineDisposition.IGNORED,
            -> ReceiptItemDisposition.IGNORE.name
        }

    private fun String.toReceiptDisposition(category: String): ReceiptItemDisposition =
        when (trim().uppercase()) {
            ReceiptItemDisposition.INVENTORY.name -> ReceiptItemDisposition.INVENTORY
            ReceiptItemDisposition.HOUSEHOLD.name -> ReceiptItemDisposition.HOUSEHOLD
            ReceiptItemDisposition.IGNORE.name,
            "IGNORED",
            -> ReceiptItemDisposition.IGNORE
            else -> if (category.lowercase() in setOf("household", "cleaning", "personal_care", "medicine", "pet")) {
                ReceiptItemDisposition.HOUSEHOLD
            } else {
                ReceiptItemDisposition.INVENTORY
            }
        }

    private fun String?.extractMerchant(): String? =
        this
            ?.lineSequence()
            ?.firstNotNullOfOrNull { line ->
                line.substringAfter("Merchant:", missingDelimiterValue = "")
                    .trim()
                    .takeIf { it.isNotBlank() }
            }

    private fun List<String>.toCsvLine(): String = joinToString(",") { it.csvEscaped() }

    private fun String.csvEscaped(): String {
        val normalized = replace("\r\n", "\n").replace("\r", "\n")
        val needsQuotes = normalized.any { it == ',' || it == '"' || it == '\n' } || normalized.startsWith(" ") || normalized.endsWith(" ")
        return if (needsQuotes) "\"${normalized.replace("\"", "\"\"")}\"" else normalized
    }

    private fun parseCsv(input: String): List<List<String>> {
        val rows = mutableListOf<MutableList<String>>()
        var row = mutableListOf<String>()
        val cell = StringBuilder()
        var inQuotes = false
        var index = 0
        while (index < input.length) {
            val char = input[index]
            when {
                inQuotes && char == '"' && input.getOrNull(index + 1) == '"' -> {
                    cell.append('"')
                    index++
                }
                char == '"' -> inQuotes = !inQuotes
                !inQuotes && char == ',' -> {
                    row.add(cell.toString())
                    cell.clear()
                }
                !inQuotes && char == '\n' -> {
                    row.add(cell.toString())
                    rows.add(row)
                    row = mutableListOf()
                    cell.clear()
                }
                !inQuotes && char == '\r' -> Unit
                else -> cell.append(char)
            }
            index++
        }
        row.add(cell.toString())
        if (row.any { it.isNotBlank() }) rows.add(row)
        return rows
    }

    fun defaultExportName(now: Instant = Instant.now()): String =
        "wonderfood-${now.toString().take(10)}.csv"
}

data class WonderFoodCsvImport(
    val inventory: List<FoodCandidate> = emptyList(),
    val groceries: List<FoodCandidate> = emptyList(),
    val recipes: List<RecipeDraft> = emptyList(),
    val receipts: List<ReceiptDraft> = emptyList(),
    val mealLogs: List<MealLogDraft> = emptyList(),
    val mealPlans: List<MealPlanDraft> = emptyList(),
    val preferences: FoodPreferences? = null,
) {
    val importedCount: Int
        get() = inventory.size + groceries.size + recipes.size + receipts.size + mealLogs.size + mealPlans.size + if (preferences == null) 0 else 1

    fun summary(): String =
        listOf(
            "${inventory.size} kitchen",
            "${groceries.size} shopping",
            "${recipes.size} recipes",
            "${receipts.size} receipts",
            "${mealLogs.size} meals",
            "${mealPlans.size} plans",
            if (preferences == null) null else "preferences",
        ).filterNotNull().joinToString(", ")

    fun canImportDirectlyToCanonicalHousehold(): Boolean =
        (inventory.isNotEmpty() || groceries.isNotEmpty() || recipes.isNotEmpty() || receipts.isNotEmpty() || mealLogs.isNotEmpty() || mealPlans.isNotEmpty()) &&
            preferences == null
}

private data class MutableReceiptCsv(
    val id: String,
    var merchant: String = "",
    var purchasedAtMillis: Long? = null,
    var currencyCode: String = "USD",
    var subtotalCents: Long? = null,
    var taxCents: Long? = null,
    var totalCents: Long? = null,
    var rawText: String = "",
    val items: MutableList<ReceiptItemDraft> = mutableListOf(),
)

private data class MutableMealPlanCsv(
    val id: String,
    var title: String = "",
    var daysText: String = "",
    var groceryHint: String = "",
    var startDateEpochDay: Long? = null,
    val entries: MutableList<MealPlanEntryDraft> = mutableListOf(),
)

private fun com.wonderfood.core.model.household.CalendarDate.toEpochDay(): Long =
    LocalDate.parse(value).toEpochDay()

private fun Long.toIsoDate(): String =
    LocalDate.ofEpochDay(this).toString()

private fun com.wonderfood.core.model.household.UtcTimestamp.toEpochDay(): Long =
    Instant.ofEpochMilli(epochMillis).atZone(ZoneOffset.UTC).toLocalDate().toEpochDay()
