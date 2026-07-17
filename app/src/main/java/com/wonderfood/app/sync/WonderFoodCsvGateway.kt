package com.wonderfood.app.sync

import com.wonderfood.app.data.FoodCandidate
import com.wonderfood.app.data.FoodMemory
import com.wonderfood.app.data.FoodPreferences
import com.wonderfood.app.data.GroceryStatus
import com.wonderfood.app.data.InventoryItem
import com.wonderfood.app.data.MealLogDraft
import com.wonderfood.app.data.MealPlanDraft
import com.wonderfood.app.data.MealPlanEntryDraft
import com.wonderfood.app.data.MealSlot
import com.wonderfood.app.data.RecipeDraft
import com.wonderfood.app.data.StorageZone
import com.wonderfood.app.data.categorizeFood
import com.wonderfood.app.data.classifyStorageZone
import java.time.Instant

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
    )

    fun export(memory: FoodMemory): String =
        buildString {
            appendLine(headers.toCsvLine())
            memory.inventory.forEach { appendLine(inventoryRow(it).toCsvLine()) }
            memory.groceries.forEach { item ->
                appendLine(
                    row(
                        "record_type" to "grocery",
                        "id" to item.id,
                        "name" to item.name,
                        "quantity" to item.quantity,
                        "status" to item.status.name,
                        "category" to item.category,
                        "serving_text" to item.servingText,
                        "calories" to item.calories,
                        "protein_g" to item.proteinGrams,
                        "carbs_g" to item.carbsGrams,
                        "fat_g" to item.fatGrams,
                        "nutrition_source" to item.nutritionSource,
                        "source" to item.source,
                        "image_uri" to item.imageUri,
                        "image_url" to item.imageUrl,
                    ).toCsvLine(),
                )
            }
            memory.recipes.forEach { recipe ->
                appendLine(
                    row(
                        "record_type" to "recipe",
                        "id" to recipe.id,
                        "title" to recipe.title,
                        "ingredients" to recipe.ingredients,
                        "steps" to recipe.steps,
                        "servings" to recipe.servings,
                        "prep_minutes" to recipe.prepMinutes,
                        "tags" to recipe.tags,
                        "rating" to recipe.rating,
                        "image_uri" to recipe.imageUri,
                        "image_url" to recipe.imageUrl,
                    ).toCsvLine(),
                )
            }
            memory.mealLogs.forEach { meal ->
                appendLine(
                    row(
                        "record_type" to "meal_log",
                        "id" to meal.id,
                        "title" to meal.title,
                        "calories" to meal.calories,
                        "protein_g" to meal.proteinGrams,
                        "carbs_g" to meal.carbsGrams,
                        "fat_g" to meal.fatGrams,
                        "meal_slot" to meal.mealSlot.name,
                        "used_items_text" to meal.usedItemsText,
                        "logged_date_epoch_day" to meal.loggedDateEpochDay,
                        "source" to meal.source,
                    ).toCsvLine(),
                )
            }
            memory.mealPlans.forEach { plan ->
                appendLine(
                    row(
                        "record_type" to "meal_plan",
                        "id" to plan.id,
                        "title" to plan.title,
                        "days_text" to plan.daysText,
                        "grocery_hint" to plan.groceryHint,
                        "status" to plan.status.name,
                        "start_date_epoch_day" to plan.startDateEpochDay,
                    ).toCsvLine(),
                )
            }
            memory.mealPlanEntries.forEach { entry ->
                appendLine(
                    row(
                        "record_type" to "meal_plan_entry",
                        "id" to entry.id,
                        "parent_id" to entry.planId,
                        "title" to entry.title,
                        "date_epoch_day" to entry.dateEpochDay,
                        "slot" to entry.slot.name,
                        "calorie_target" to entry.calorieTarget,
                        "status" to entry.status.name,
                        "notes" to entry.notes,
                        "source" to entry.source,
                        "image_uri" to entry.imageUri,
                        "image_url" to entry.imageUrl,
                    ).toCsvLine(),
                )
            }
            memory.receipts.forEach { receipt ->
                appendLine(
                    row(
                        "record_type" to "receipt",
                        "id" to receipt.id,
                        "image_uri" to receipt.imageUri,
                        "raw_text" to receipt.rawText,
                        "status" to receipt.status.name,
                    ).toCsvLine(),
                )
            }
            memory.preferences.toCsvPreferenceRows().forEach { appendLine(it.toCsvLine()) }
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
            mealLogs = mealLogs,
            mealPlans = mealPlans.values.map { it.toDraft() },
            preferences = preferences.toFoodPreferencesOrNull(),
        )
    }

    private fun inventoryRow(item: InventoryItem): List<String> =
        row(
            "record_type" to "inventory",
            "id" to item.id,
            "name" to item.name,
            "quantity" to item.quantity,
            "zone" to item.zone.name,
            "category" to item.category,
            "serving_text" to item.servingText,
            "calories" to item.calories,
            "protein_g" to item.proteinGrams,
            "carbs_g" to item.carbsGrams,
            "fat_g" to item.fatGrams,
            "nutrition_source" to item.nutritionSource,
            "notes" to item.notes,
            "image_uri" to item.imageUri,
            "image_url" to item.imageUrl,
            "expires_at" to item.expiresAtMillis,
            "source" to item.source,
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
        ).map { (key, value) ->
            row(
                "record_type" to "preference",
                "preference_key" to key,
                "preference_value" to value,
            )
        }

    private fun Map<String, String>.toFoodCandidate(defaultZone: StorageZone): FoodCandidate {
        val name = value("name").ifBlank { value("title").ifBlank { "Imported food" } }
        return FoodCandidate(
            name = name,
            quantity = value("quantity"),
            zone = value("zone").parseZone(name, defaultZone),
            category = value("category").ifBlank { categorizeFood(name) },
            servingText = value("serving_text"),
            calories = value("calories").toIntOrNull(),
            proteinGrams = value("protein_g").toDoubleOrNull(),
            carbsGrams = value("carbs_g").toDoubleOrNull(),
            fatGrams = value("fat_g").toDoubleOrNull(),
            nutritionSource = value("nutrition_source").ifBlank { "csv_import" },
            notes = value("notes"),
            imageUri = value("image_uri").ifBlank { null },
            imageUrl = value("image_url"),
            expiresAtMillis = value("expires_at").toLongOrNull(),
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

    private fun String.parseZone(name: String, fallback: StorageZone = classifyStorageZone(name)): StorageZone =
        runCatching { StorageZone.valueOf(trim().uppercase()) }.getOrDefault(fallback)

    private fun String.parseMealSlot(): MealSlot =
        runCatching { MealSlot.valueOf(trim().uppercase()) }.getOrDefault(MealSlot.FLEX)

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
    val mealLogs: List<MealLogDraft> = emptyList(),
    val mealPlans: List<MealPlanDraft> = emptyList(),
    val preferences: FoodPreferences? = null,
) {
    val importedCount: Int
        get() = inventory.size + groceries.size + recipes.size + mealLogs.size + mealPlans.size + if (preferences == null) 0 else 1

    fun summary(): String =
        listOf(
            "${inventory.size} kitchen",
            "${groceries.size} shopping",
            "${recipes.size} recipes",
            "${mealLogs.size} meals",
            "${mealPlans.size} plans",
            if (preferences == null) null else "preferences",
        ).filterNotNull().joinToString(", ")
}

private data class MutableMealPlanCsv(
    val id: String,
    var title: String = "",
    var daysText: String = "",
    var groceryHint: String = "",
    var startDateEpochDay: Long? = null,
    val entries: MutableList<MealPlanEntryDraft> = mutableListOf(),
)
