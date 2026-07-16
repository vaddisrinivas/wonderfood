package com.wonderfood.app.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class FoodChatStore(context: Context, dbName: String = DB_NAME) : SQLiteOpenHelper(context, dbName, null, DB_VERSION) {
    override fun onCreate(db: SQLiteDatabase) {
        createCoreTables(db)
        createV2Tables(db)
        createV4Tables(db)
        createV5Tables(db)
        createV6Tables(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) migrateToV2(db)
        if (oldVersion < 3) migrateToV3(db)
        if (oldVersion < 4) migrateToV4(db)
        if (oldVersion < 5) migrateToV5(db)
        if (oldVersion < 6) migrateToV6(db)
    }

    @Synchronized
    fun seedIfEmpty() {
        val hadMessages = readMessages().isNotEmpty()
        val seededPersonalFood = seedPersonalFoodVaultIfEmpty()
        if (hadMessages) return
        insertMessage(
            role = ChatRole.ASSISTANT,
            body = if (seededPersonalFood) {
                "Your kitchen memory is ready. Tell me what changed, what you ate, or what you want to cook next."
            } else {
                "Tell me food things naturally. Try: `I bought eggs, Greek yogurt, spinach and frozen berries` or `Log chicken rice bowl for lunch`."
            },
        )
    }

    @Synchronized
    fun insertMessage(role: ChatRole, body: String): Long {
        val now = now()
        return writableDatabase.insert(
            "chat_messages",
            null,
            ContentValues().apply {
                put("role", role.name)
                put("body", body)
                put("created_at", now)
            },
        )
    }

    @Synchronized
    fun insertReceiptCapture(imageUri: String, rawText: String = "", status: ReceiptStatus = ReceiptStatus.SAVED): Long {
        val now = now()
        return writableDatabase.insert(
            "receipt_captures",
            null,
            ContentValues().apply {
                put("image_uri", imageUri)
                put("raw_text", rawText)
                put("status", status.name)
                put("created_at", now)
            },
        )
    }

    @Synchronized
    fun updateReceiptStatus(id: Long, rawText: String, status: ReceiptStatus) {
        writableDatabase.update(
            "receipt_captures",
            ContentValues().apply {
                put("raw_text", rawText)
                put("status", status.name)
            },
            "id = ?",
            arrayOf(id.toString()),
        )
    }

    @Synchronized
    fun readMemory(): FoodMemory =
        FoodMemory(
            messages = readMessages(),
            actions = readActions(),
            events = readEvents(),
            inventory = readInventory(),
            inventoryTransactions = readInventoryTransactions(),
            groceries = readGroceries(),
            recipes = readRecipes(),
            mealLogs = readMealLogs(),
            mealPlans = readMealPlans(),
            mealPlanEntries = readMealPlanEntries(),
            receipts = readReceipts(),
            preferences = readPreferences(),
        )

    @Synchronized
    fun savePreferences(preferences: FoodPreferences) {
        val now = now()
        val values = mapOf(
            PREF_DIET_STYLE to preferences.dietStyle,
            PREF_ALLERGIES to preferences.allergies,
            PREF_DISLIKES to preferences.dislikes,
            PREF_STAPLES to preferences.preferredStaples,
            PREF_CUISINES to preferences.preferredCuisines,
            PREF_STORES to preferences.preferredStores,
            PREF_CALORIE_GOAL to preferences.calorieGoal,
            PREF_PROTEIN_GOAL to preferences.proteinGoal,
            PREF_HEALTH_NOTES to preferences.healthNotes,
            PREF_AI_INSTRUCTIONS to preferences.customAiInstructions,
        )
        values.forEach { (key, value) ->
            writableDatabase.insertWithOnConflict(
                "user_preferences",
                null,
                ContentValues().apply {
                    put("key", key)
                    put("value", value)
                    put("updated_at", now)
                },
                SQLiteDatabase.CONFLICT_REPLACE,
            )
        }
    }

    @Synchronized
    fun applyDraft(draft: FoodDraft, sourceMessageId: Long?): String {
        val summary = when (draft) {
            is InventoryDraft -> {
                draft.items.forEach { addInventory(it, sourceMessageId) }
                "Saved ${draft.items.size} inventory item${draft.items.size.plural}."
            }
            is GroceryDraft -> {
                draft.items.forEach { addGrocery(it, sourceMessageId) }
                "Added ${draft.items.size} grocery item${draft.items.size.plural}."
            }
            is RecipeDraft -> {
                addRecipe(draft, sourceMessageId)
                "Saved recipe: ${draft.titleText}."
            }
            is MealLogDraft -> {
                addMealLog(draft, sourceMessageId)
                "Logged meal: ${draft.titleText}."
            }
            is MealPlanDraft -> {
                addMealPlan(draft, sourceMessageId)
                "Saved meal plan: ${draft.titleText}."
            }
        }
        recordChatAction(draft, ChatActionStatus.ACCEPTED, sourceMessageId, summary)
        return summary
    }

    @Synchronized
    fun rejectDraft(draft: FoodDraft, sourceMessageId: Long?): String {
        val summary = "Rejected ${draft.title.lowercase()}."
        recordChatAction(draft, ChatActionStatus.REJECTED, sourceMessageId, summary)
        return summary
    }

    private fun seedPersonalFoodVaultIfEmpty(): Boolean {
        if (!PERSONAL_SEED_ENABLED) return false
        val alreadySeeded = readInventory().any { it.source == PERSONAL_SEED_SOURCE } ||
            readRecipes().any { PERSONAL_SEED_SOURCE in it.tags } ||
            readMealPlans().any { it.title == PERSONAL_MEAL_PLAN_TITLE }
        if (alreadySeeded) return false

        val groceries = personalVaultGroceries()
        val recipes = personalVaultRecipes()
        groceries.forEach { addInventory(it, sourceMessageId = null, source = PERSONAL_SEED_SOURCE) }
        recipes.forEach { addRecipe(it, sourceMessageId = null, source = PERSONAL_SEED_SOURCE) }
        addMealPlan(personalVaultMealPlan(), sourceMessageId = null, source = PERSONAL_SEED_SOURCE)
        if (readPreferences().isEmpty) savePreferences(personalVaultPreferences())
        return true
    }

    private fun personalVaultGroceries(): List<FoodCandidate> {
        val names = listOf(
            "Onions",
            "Tomatoes",
            "Cilantro",
            "Potatoes",
            "Cucumber",
            "Bottle gourd",
            "Pumpkin",
            "Drumsticks",
            "Brinjal",
            "Cauliflower",
            "Cabbage",
            "Green mango",
            "Frozen peas carrots corn",
            "Frozen bhindi",
            "Frozen dondakaya",
            "Lettuce",
            "Garlic",
            "Green chilies",
            "Greek yogurt",
            "Chickpeas",
            "Sprouted moong",
            "Chickpea pasta",
            "Matki",
            "Rajma",
            "Tortillas",
            "Ragi flour",
            "Protein powder",
            "Milk",
            "Water",
            "Hummus",
            "Lemons",
            "Tamarind extract",
            "Jaggery",
            "Rice",
            "Oats",
            "Bananas",
            "Eggs",
            "Spinach",
            "Frozen berries",
            "Paneer",
            "Peanuts",
            "Curry leaves",
        )
        return names.map { name ->
            FoodCandidate(
                name = name,
                quantity = "on hand",
                zone = classifyStorageZone(name),
                category = categorizeFood(name),
                notes = "Seeded from your personal Notion and AI-vault food memory.",
            )
        }
    }

    private fun personalVaultRecipes(): List<RecipeDraft> =
        listOf(
            RecipeDraft(
                titleText = "Rajma Curry",
                ingredientsText = "Rajma, onions, tomatoes, garlic, green chilies, spices, cilantro.",
                stepsText = "Soak and cook rajma. Make onion tomato masala. Simmer rajma until thick. Finish with cilantro. Serve with tortilla, rice, or salad.",
                servings = 3,
                prepMinutes = 45,
                tags = "indian, beans, meal prep, high fiber",
            ),
            RecipeDraft(
                titleText = "Lettuce Salad",
                ingredientsText = "Lettuce, cucumber, tomatoes, chickpeas or sprouted moong, hummus, lemon, salt, pepper.",
                stepsText = "Chop vegetables. Add protein. Thin hummus with lemon as dressing. Toss right before eating.",
                servings = 2,
                prepMinutes = 12,
                tags = "salad, quick, high protein",
            ),
            RecipeDraft(
                titleText = "Paneer Biryani",
                ingredientsText = "Paneer, rice, onions, tomatoes, yogurt, green chilies, spices, cilantro.",
                stepsText = "Marinate paneer with yogurt and spices. Cook masala. Layer with partly cooked rice. Steam until fragrant.",
                servings = 3,
                prepMinutes = 50,
                tags = "indian, paneer, rice",
            ),
            RecipeDraft(
                titleText = "Vankaya Pulusu Pachadi with Ragi",
                ingredientsText = "Brinjal, tamarind extract, onions, green chilies, spices, cilantro, ragi flour.",
                stepsText = "Cook brinjal in tamarind base until soft. Temper spices. Serve runny with ragi mudda.",
                servings = 3,
                prepMinutes = 35,
                tags = "south indian, ragi, pulusu",
            ),
            RecipeDraft(
                titleText = "Gummadikaya Anapakaya Pulusu",
                ingredientsText = "Pumpkin, bottle gourd, tamarind extract, onions, green chilies, jaggery, spices.",
                stepsText = "Simmer vegetables in tamarind water. Balance sour heat with a little jaggery. Temper and serve with rice or ragi.",
                servings = 4,
                prepMinutes = 35,
                tags = "south indian, vegetables, pulusu",
            ),
            RecipeDraft(
                titleText = "Cucumber Salad",
                ingredientsText = "Cucumber, yogurt, lemon, cilantro, green chilies, salt, roasted peanuts.",
                stepsText = "Dice cucumber. Mix with yogurt, lemon, herbs, chilies, and crushed peanuts. Chill briefly.",
                servings = 2,
                prepMinutes = 10,
                tags = "cooling, quick, side",
            ),
            RecipeDraft(
                titleText = "Mixed Veg Curry",
                ingredientsText = "Cauliflower, cabbage, peas, carrots, corn, onions, tomatoes, garlic, spices.",
                stepsText = "Saute aromatics. Add vegetables and spices. Cook covered until tender. Finish dry or saucy based on meal.",
                servings = 4,
                prepMinutes = 30,
                tags = "vegetables, meal prep",
            ),
            RecipeDraft(
                titleText = "Cabbage Stir Fry",
                ingredientsText = "Cabbage, green chilies, garlic, curry leaves, mustard seeds, spices.",
                stepsText = "Temper spices and curry leaves. Add cabbage and chilies. Stir fry until tender-crisp.",
                servings = 3,
                prepMinutes = 20,
                tags = "quick, side, vegetables",
            ),
            RecipeDraft(
                titleText = "Tomato Peanut Curry",
                ingredientsText = "Tomatoes, onion, green chilies, curry leaves, peanuts, oil, salt, cilantro.",
                stepsText = "Roast peanuts and crush. Cook onion, chilies, curry leaves, and tomatoes into a soft base. Add peanut powder and simmer until glossy.",
                servings = 3,
                prepMinutes = 25,
                tags = "ai vault, curry, peanut",
            ),
            RecipeDraft(
                titleText = "Majjiga Pulusu",
                ingredientsText = "Yogurt or buttermilk, bottle gourd or cucumber, green chilies, curry leaves, spices.",
                stepsText = "Cook vegetables until tender. Add seasoned yogurt base on low heat. Temper spices separately and mix in.",
                servings = 3,
                prepMinutes = 30,
                tags = "south indian, ragi pairing, yogurt",
            ),
            RecipeDraft(
                titleText = "Pachi Pulusu",
                ingredientsText = "Tamarind extract, onion, green chilies, jaggery, cilantro, tempering spices.",
                stepsText = "Mix tamarind water with onion, chilies, salt, and jaggery. Temper mustard and curry leaves. Serve runny with rice or ragi.",
                servings = 2,
                prepMinutes = 12,
                tags = "south indian, no cook, ragi pairing",
            ),
        )

    private fun personalVaultMealPlan(): MealPlanDraft =
        MealPlanDraft(
            titleText = PERSONAL_MEAL_PLAN_TITLE,
            daysText = """
                Today Lunch: Rajma Curry with tortilla and lettuce salad
                Today Dinner: Gummadikaya Anapakaya Pulusu with rice
                Friday Lunch: Tomato Peanut Curry with ragi
                Friday Dinner: Paneer Biryani with cucumber salad
                Saturday Lunch: Mixed Veg Curry with rice and yogurt
                Sunday Lunch: Vankaya Pulusu Pachadi with ragi
                Monday Lunch: Cabbage Stir Fry with chickpea pasta
            """.trimIndent(),
            groceryHint = "Top up yogurt, cilantro, lemons, tomatoes, green chilies, lettuce, paneer, and fresh vegetables if low.",
            entries = listOf(
                MealPlanEntryDraft(0, MealSlot.LUNCH, "Rajma Curry with tortilla and lettuce salad", 650),
                MealPlanEntryDraft(0, MealSlot.DINNER, "Gummadikaya Anapakaya Pulusu with rice", 560),
                MealPlanEntryDraft(1, MealSlot.LUNCH, "Tomato Peanut Curry with ragi", 620),
                MealPlanEntryDraft(1, MealSlot.DINNER, "Paneer Biryani with cucumber salad", 760),
                MealPlanEntryDraft(2, MealSlot.LUNCH, "Mixed Veg Curry with rice and yogurt", 610),
                MealPlanEntryDraft(3, MealSlot.LUNCH, "Vankaya Pulusu Pachadi with ragi", 580),
                MealPlanEntryDraft(4, MealSlot.LUNCH, "Cabbage Stir Fry with chickpea pasta", 590),
            ),
        )

    private fun personalVaultPreferences(): FoodPreferences =
        FoodPreferences(
            dietStyle = "high protein, meal prep friendly, South Indian comfort food",
            allergies = "",
            dislikes = "",
            preferredStaples = "ragi, rice, yogurt, rajma, chickpeas, sprouted moong, paneer, tortillas",
            preferredCuisines = "South Indian, Indian, Mediterranean",
            preferredStores = "Costco, Trader Joe's, Indian grocery store",
            calorieGoal = "flexible by activity",
            proteinGoal = "15-25g per meal minimum",
            healthNotes = "Prioritize perishables and fresh produce first. Keep wet/runny sides for ragi mudda.",
            customAiInstructions = "When planning meals, use what is already in fridge/freezer/pantry first, call out missing groceries, estimate nutrition, and ask before making aggressive pantry quantity deductions.",
        )

    @Synchronized
    fun deleteInventory(id: Long) {
        readInventory().firstOrNull { it.id == id }?.let { item ->
            insertInventoryTransaction(
                inventoryItemId = item.id,
                itemName = item.name,
                quantityText = item.quantity,
                zone = item.zone,
                action = InventoryAction.REMOVED,
                reason = "Removed from kitchen",
                relatedRecipeId = null,
                relatedMealLogId = null,
                source = "manual",
            )
        }
        writableDatabase.delete("inventory_items", "id = ?", arrayOf(id.toString()))
    }

    @Synchronized
    fun deleteGrocery(id: Long) {
        writableDatabase.delete("grocery_items", "id = ?", arrayOf(id.toString()))
    }

    @Synchronized
    fun deleteRecipe(id: Long) {
        writableDatabase.delete("recipes", "id = ?", arrayOf(id.toString()))
    }

    @Synchronized
    fun updateRecipe(
        id: Long,
        title: String,
        ingredients: String,
        steps: String,
        servings: Int?,
        prepMinutes: Int?,
        tags: String,
        imageUri: String?,
    ) {
        writableDatabase.update(
            "recipes",
            ContentValues().apply {
                put("title", title)
                put("ingredients", ingredients)
                put("steps", steps)
                put("servings", servings)
                put("prep_minutes", prepMinutes)
                put("tags", tags)
                put("image_uri", imageUri)
                put("updated_at", now())
            },
            "id = ?",
            arrayOf(id.toString()),
        )
    }

    @Synchronized
    fun updateRecipeImage(id: Long, imageUri: String?) {
        writableDatabase.update(
            "recipes",
            ContentValues().apply {
                put("image_uri", imageUri)
                put("updated_at", now())
            },
            "id = ?",
            arrayOf(id.toString()),
        )
    }

    @Synchronized
    fun deleteMealLog(id: Long) {
        writableDatabase.delete("meal_logs", "id = ?", arrayOf(id.toString()))
    }

    @Synchronized
    fun markGroceryBought(id: Long): String {
        val item = readGroceries().firstOrNull { it.id == id } ?: return "Grocery item not found."
        writableDatabase.update(
            "grocery_items",
            ContentValues().apply {
                put("status", GroceryStatus.BOUGHT.name)
                put("updated_at", now())
            },
            "id = ?",
            arrayOf(id.toString()),
        )
        addInventory(
            FoodCandidate(
                name = item.name,
                quantity = item.quantity,
                zone = classifyStorageZone(item.name),
                category = item.category,
                imageUri = item.imageUri,
            ),
            sourceMessageId = null,
            source = "grocery",
            transactionAction = InventoryAction.BOUGHT,
            transactionReason = "Marked bought from shopping list",
        )
        insertFoodEvent(
            type = FoodEventType.GROCERY_PURCHASE,
            amount = 1.0,
            unit = "item",
            source = "manual",
            confidence = FoodEventConfidence.EXACT,
            note = "Bought ${item.name}",
        )
        return "Moved ${item.name} into inventory and recorded shopping."
    }

    @Synchronized
    fun cookRecipe(recipeId: Long): String {
        val recipe = readRecipes().firstOrNull { it.id == recipeId } ?: return "Recipe not found."
        val matched = matchedInventoryForRecipe(recipe)
        val usedNames = matched.joinToString(", ") { it.name }
        val mealId = addMealLog(
            MealLogDraft(
                titleText = recipe.title,
                calories = estimateRecipeCalories(recipe),
                proteinGrams = estimateRecipeProtein(recipe),
                carbsGrams = 45.0,
                fatGrams = 16.0,
                mealSlot = MealSlot.FLEX,
                usedItemsText = usedNames,
                source = "recipe_confirmed",
            ),
            sourceMessageId = null,
            relatedRecipeId = recipe.id,
        )
        insertFoodEvent(
            type = FoodEventType.COOK,
            amount = recipe.servings?.toDouble(),
            unit = "servings",
            source = "manual",
            confidence = FoodEventConfidence.ESTIMATED,
            relatedRecipeId = recipe.id,
            mealLogId = mealId,
            note = "Cooked ${recipe.title}",
        )
        markMatchingPlanEaten(recipe.title)
        return if (matched.isEmpty()) {
            "Logged ${recipe.title}. No pantry items matched, so inventory was not deducted."
        } else {
            "Logged ${recipe.title}. Recorded ${matched.size} ingredient use${matched.size.plural}; quantities need your review before hard deduction."
        }
    }

    @Synchronized
    fun logWater(ml: Int, source: String = "assistant"): String {
        val safeMl = ml.coerceIn(1, 5000)
        insertFoodEvent(
            type = FoodEventType.WATER,
            amount = safeMl.toDouble(),
            unit = "ml",
            source = source,
            confidence = FoodEventConfidence.EXACT,
            note = "Water",
        )
        return "Logged ${safeMl} ml water."
    }

    @Synchronized
    fun logFoodEvent(
        type: FoodEventType,
        amount: Double? = null,
        unit: String = "",
        source: String = "assistant",
        confidence: FoodEventConfidence = FoodEventConfidence.ESTIMATED,
        relatedRecipeId: Long? = null,
        mealLogId: Long? = null,
        shoppingTripId: Long? = null,
        inventoryItemId: Long? = null,
        note: String = "",
    ): Long =
        insertFoodEvent(
            type = type,
            amount = amount,
            unit = unit,
            source = source,
            confidence = confidence,
            relatedRecipeId = relatedRecipeId,
            mealLogId = mealLogId,
            shoppingTripId = shoppingTripId,
            inventoryItemId = inventoryItemId,
            note = note,
        )

    @Synchronized
    fun addMissingRecipeGroceries(recipeId: Long): String {
        val recipe = readRecipes().firstOrNull { it.id == recipeId } ?: return "Recipe not found."
        val missing = missingIngredientsForRecipe(recipe)
        if (missing.isEmpty()) return "This recipe looks covered by your kitchen."
        missing.forEach { name ->
            addGrocery(
                FoodCandidate(
                    name = name,
                    quantity = "for ${recipe.title}",
                    zone = classifyStorageZone(name),
                    category = categorizeFood(name),
                    notes = "Needed for ${recipe.title}",
                ),
                sourceMessageId = null,
                source = "recipe_need",
            )
        }
        return "Added ${missing.size} missing ingredient${missing.size.plural} for ${recipe.title} to the shopping list."
    }

    private fun addInventory(
        candidate: FoodCandidate,
        sourceMessageId: Long?,
        source: String = "chat",
        transactionAction: InventoryAction = InventoryAction.ADDED,
        transactionReason: String = "Saved from $source",
    ): Long {
        val now = now()
        val values = inventoryValues(candidate, sourceMessageId, source, now, includeCreatedAt = true)
        val existingId = findInventoryId(candidate.name, candidate.zone)
        val itemId = if (existingId == null) {
            writableDatabase.insert("inventory_items", null, values)
        } else {
            values.remove("created_at")
            writableDatabase.update("inventory_items", values, "id = ?", arrayOf(existingId.toString()))
            existingId
        }
        if (source != PERSONAL_SEED_SOURCE) {
            insertInventoryTransaction(
                inventoryItemId = itemId,
                itemName = candidate.name,
                quantityText = candidate.quantity,
                zone = candidate.zone,
                action = if (existingId == null) transactionAction else if (transactionAction == InventoryAction.BOUGHT) InventoryAction.BOUGHT else InventoryAction.UPDATED,
                reason = transactionReason,
                relatedRecipeId = null,
                relatedMealLogId = null,
                source = source,
            )
        }
        return itemId
    }

    private fun addGrocery(candidate: FoodCandidate, sourceMessageId: Long?, source: String = "chat") {
        val now = now()
        val values = groceryValues(candidate, sourceMessageId, source, now, includeCreatedAt = true)
        val existingId = findGroceryId(candidate.name)
        if (existingId == null) {
            writableDatabase.insert("grocery_items", null, values)
        } else {
            values.remove("created_at")
            writableDatabase.update("grocery_items", values, "id = ?", arrayOf(existingId.toString()))
        }
    }

    private fun addRecipe(draft: RecipeDraft, sourceMessageId: Long?, source: String = "chat") {
        val now = now()
        val existingId = findRecipeId(draft.titleText)
        val values = ContentValues().apply {
            put("title", draft.titleText)
            put("ingredients", draft.ingredientsText)
            put("steps", draft.stepsText)
            put("servings", draft.servings)
            put("prep_minutes", draft.prepMinutes)
            put("tags", listOf(draft.tags, source).filter { it.isNotBlank() }.joinToString(", "))
            putNull("rating")
            putNull("image_uri")
            put("source_message_id", sourceMessageId)
            put("updated_at", now)
        }
        if (existingId == null) {
            values.put("created_at", now)
            writableDatabase.insert("recipes", null, values)
        } else {
            writableDatabase.update("recipes", values, "id = ?", arrayOf(existingId.toString()))
        }
    }

    private fun addMealLog(draft: MealLogDraft, sourceMessageId: Long?, relatedRecipeId: Long? = null): Long {
        val now = now()
        val loggedDay = draft.loggedDateEpochDay ?: todayEpochDay()
        val mealId = writableDatabase.insert(
            "meal_logs",
            null,
            ContentValues().apply {
                put("title", draft.titleText)
                put("calories", draft.calories)
                put("protein_g", draft.proteinGrams)
                put("carbs_g", draft.carbsGrams)
                put("fat_g", draft.fatGrams)
                put("meal_slot", draft.mealSlot.name)
                put("used_items_text", draft.usedItemsText)
                put("logged_date_epoch_day", loggedDay)
                put("source", draft.source)
                put("source_message_id", sourceMessageId)
                put("created_at", now)
                put("updated_at", now)
            },
        )
        recordUsedItems(
            usedItemsText = draft.usedItemsText,
            mealId = mealId,
            recipeId = relatedRecipeId,
            reason = "Logged ${draft.titleText}",
            source = draft.source,
        )
        insertFoodEvent(
            type = FoodEventType.MEAL,
            amount = draft.calories.toDouble(),
            unit = "kcal",
            source = draft.source,
            confidence = if ("ai" in draft.source) FoodEventConfidence.AI_ESTIMATED else FoodEventConfidence.ESTIMATED,
            relatedRecipeId = relatedRecipeId,
            mealLogId = mealId,
            note = draft.titleText,
        )
        return mealId
    }

    private fun addMealPlan(draft: MealPlanDraft, sourceMessageId: Long?, source: String = "chat") {
        val now = now()
        val startDay = draft.startDateEpochDay ?: todayEpochDay()
        val planId = writableDatabase.insert(
            "meal_plans",
            null,
            ContentValues().apply {
                put("title", draft.titleText)
                put("days_text", draft.daysText)
                put("grocery_hint", draft.groceryHint)
                put("status", MealPlanStatus.ACCEPTED.name)
                put("start_date_epoch_day", startDay)
                put("source_message_id", sourceMessageId)
                put("created_at", now)
                put("updated_at", now)
            },
        )
        val entries = draft.entries.ifEmpty { draft.daysText.toMealPlanEntries() }
        entries.forEach { entry ->
            writableDatabase.insert(
                "meal_plan_entries",
                null,
                ContentValues().apply {
                    put("plan_id", planId)
                    put("date_epoch_day", startDay + entry.dayOffset)
                    put("slot", entry.slot.name)
                    put("title", entry.title)
                    put("calorie_target", entry.calorieTarget)
                    put("status", MealPlanEntryStatus.PLANNED.name)
                    put("created_at", now)
                    put("updated_at", now)
                },
            )
        }
    }

    private fun inventoryValues(
        candidate: FoodCandidate,
        sourceMessageId: Long?,
        source: String,
        now: Long,
        includeCreatedAt: Boolean,
    ): ContentValues =
        ContentValues().apply {
            put("name", candidate.name)
            put("quantity", candidate.quantity)
            put("zone", candidate.zone.name)
            put("category", candidate.category.ifBlank { categorizeFood(candidate.name) })
            val nutrition = candidate.withEstimatedNutrition()
            put("serving_text", nutrition.servingText)
            put("calories", nutrition.calories)
            put("protein_g", nutrition.proteinGrams)
            put("carbs_g", nutrition.carbsGrams)
            put("fat_g", nutrition.fatGrams)
            put("nutrition_source", nutrition.nutritionSource)
            put("notes", candidate.notes)
            put("image_uri", candidate.imageUri)
            put("expires_at", candidate.expiresAtMillis)
            put("source", source)
            put("source_message_id", sourceMessageId)
            if (includeCreatedAt) put("created_at", now)
            put("updated_at", now)
        }

    private fun groceryValues(
        candidate: FoodCandidate,
        sourceMessageId: Long?,
        source: String,
        now: Long,
        includeCreatedAt: Boolean,
    ): ContentValues =
        ContentValues().apply {
            put("name", candidate.name)
            put("quantity", candidate.quantity)
            put("status", GroceryStatus.NEEDED.name)
            put("category", candidate.category.ifBlank { categorizeFood(candidate.name) })
            val nutrition = candidate.withEstimatedNutrition()
            put("serving_text", nutrition.servingText)
            put("calories", nutrition.calories)
            put("protein_g", nutrition.proteinGrams)
            put("carbs_g", nutrition.carbsGrams)
            put("fat_g", nutrition.fatGrams)
            put("nutrition_source", nutrition.nutritionSource)
            put("source", source)
            put("image_uri", candidate.imageUri)
            put("source_message_id", sourceMessageId)
            if (includeCreatedAt) put("created_at", now)
            put("updated_at", now)
        }

    private fun findInventoryId(name: String, zone: StorageZone): Long? =
        readableDatabase.rawQuery(
            "SELECT id FROM inventory_items WHERE lower(name) = lower(?) AND zone = ? LIMIT 1",
            arrayOf(name, zone.name),
        ).use { cursor -> if (cursor.moveToFirst()) cursor.long("id") else null }

    private fun findGroceryId(name: String): Long? =
        readableDatabase.rawQuery(
            "SELECT id FROM grocery_items WHERE lower(name) = lower(?) AND status = ? LIMIT 1",
            arrayOf(name, GroceryStatus.NEEDED.name),
        ).use { cursor -> if (cursor.moveToFirst()) cursor.long("id") else null }

    private fun findRecipeId(title: String): Long? =
        readableDatabase.rawQuery(
            "SELECT id FROM recipes WHERE lower(title) = lower(?) LIMIT 1",
            arrayOf(title),
        ).use { cursor -> if (cursor.moveToFirst()) cursor.long("id") else null }

    private fun readMessages(limit: Int = 200): List<ChatMessage> =
        readableDatabase.rawQuery(
            "SELECT id, role, body, created_at FROM chat_messages ORDER BY id ASC LIMIT ?",
            arrayOf(limit.toString()),
        ).useRows { cursor ->
            ChatMessage(
                id = cursor.long("id"),
                role = runCatching { ChatRole.valueOf(cursor.string("role")) }.getOrDefault(ChatRole.ASSISTANT),
                body = cursor.string("body"),
                createdAtMillis = cursor.long("created_at"),
            )
        }

    private fun readActions(limit: Int = 80): List<ChatAction> =
        readableDatabase.rawQuery(
            """
            SELECT id, title, summary, rows_text, operation, status, source, confidence,
                   source_message_id, created_at, resolved_at
            FROM chat_actions ORDER BY id DESC LIMIT ?
            """.trimIndent(),
            arrayOf(limit.toString()),
        ).useRows { cursor ->
            ChatAction(
                id = cursor.long("id"),
                title = cursor.string("title"),
                summary = cursor.string("summary"),
                rowsText = cursor.string("rows_text"),
                operation = runCatching { FoodOperation.valueOf(cursor.string("operation")) }.getOrDefault(FoodOperation.UPDATE),
                status = runCatching { ChatActionStatus.valueOf(cursor.string("status")) }.getOrDefault(ChatActionStatus.ACCEPTED),
                source = runCatching { DraftSource.valueOf(cursor.string("source")) }.getOrDefault(DraftSource.CHAT),
                confidence = cursor.double("confidence"),
                sourceMessageId = cursor.nullableLong("source_message_id"),
                createdAtMillis = cursor.long("created_at"),
                resolvedAtMillis = cursor.nullableLong("resolved_at"),
            )
        }

    private fun readEvents(limit: Int = 240): List<FoodEvent> =
        readableDatabase.rawQuery(
            """
            SELECT id, type, started_at, ended_at, duration_minutes, amount, unit, source, confidence,
                   related_recipe_id, meal_log_id, shopping_trip_id, inventory_item_id, note, created_at
            FROM food_events ORDER BY id DESC LIMIT ?
            """.trimIndent(),
            arrayOf(limit.toString()),
        ).useRows { cursor ->
            FoodEvent(
                id = cursor.long("id"),
                type = runCatching { FoodEventType.valueOf(cursor.string("type")) }.getOrDefault(FoodEventType.MEAL),
                startedAtMillis = cursor.long("started_at"),
                endedAtMillis = cursor.nullableLong("ended_at"),
                durationMinutes = cursor.nullableInt("duration_minutes"),
                amount = cursor.nullableDouble("amount"),
                unit = cursor.string("unit"),
                source = cursor.string("source"),
                confidence = runCatching { FoodEventConfidence.valueOf(cursor.string("confidence")) }.getOrDefault(FoodEventConfidence.ESTIMATED),
                relatedRecipeId = cursor.nullableLong("related_recipe_id"),
                mealLogId = cursor.nullableLong("meal_log_id"),
                shoppingTripId = cursor.nullableLong("shopping_trip_id"),
                inventoryItemId = cursor.nullableLong("inventory_item_id"),
                note = cursor.string("note"),
                createdAtMillis = cursor.long("created_at"),
            )
        }

    private fun readInventory(): List<InventoryItem> =
        readableDatabase.rawQuery(
            """
            SELECT id, name, quantity, zone, category, serving_text, calories, protein_g, carbs_g, fat_g,
                   nutrition_source, notes, image_uri, expires_at, source, created_at, updated_at
            FROM inventory_items ORDER BY id DESC
            """.trimIndent(),
            emptyArray(),
        ).useRows { cursor ->
            InventoryItem(
                id = cursor.long("id"),
                name = cursor.string("name"),
                quantity = cursor.string("quantity"),
                zone = runCatching { StorageZone.valueOf(cursor.string("zone")) }.getOrDefault(StorageZone.PANTRY),
                category = cursor.string("category").ifBlank { categorizeFood(cursor.string("name")) },
                servingText = cursor.string("serving_text"),
                calories = cursor.nullableInt("calories"),
                proteinGrams = cursor.nullableDouble("protein_g"),
                carbsGrams = cursor.nullableDouble("carbs_g"),
                fatGrams = cursor.nullableDouble("fat_g"),
                nutritionSource = cursor.string("nutrition_source"),
                notes = cursor.string("notes"),
                imageUri = cursor.nullableString("image_uri"),
                expiresAtMillis = cursor.nullableLong("expires_at"),
                source = cursor.string("source"),
                createdAtMillis = cursor.long("created_at"),
                updatedAtMillis = cursor.long("updated_at"),
            )
        }

    private fun readInventoryTransactions(limit: Int = 160): List<InventoryTransaction> =
        readableDatabase.rawQuery(
            """
            SELECT id, inventory_item_id, item_name, quantity_text, zone, action, reason,
                   related_recipe_id, related_meal_log_id, occurred_date_epoch_day, source, created_at
            FROM inventory_transactions ORDER BY id DESC LIMIT ?
            """.trimIndent(),
            arrayOf(limit.toString()),
        ).useRows { cursor ->
            InventoryTransaction(
                id = cursor.long("id"),
                inventoryItemId = cursor.nullableLong("inventory_item_id"),
                itemName = cursor.string("item_name"),
                quantityText = cursor.string("quantity_text"),
                zone = runCatching { StorageZone.valueOf(cursor.string("zone")) }.getOrDefault(StorageZone.PANTRY),
                action = runCatching { InventoryAction.valueOf(cursor.string("action")) }.getOrDefault(InventoryAction.UPDATED),
                reason = cursor.string("reason"),
                relatedRecipeId = cursor.nullableLong("related_recipe_id"),
                relatedMealLogId = cursor.nullableLong("related_meal_log_id"),
                occurredDateEpochDay = cursor.long("occurred_date_epoch_day"),
                source = cursor.string("source"),
                createdAtMillis = cursor.long("created_at"),
            )
        }

    private fun readGroceries(): List<GroceryItem> =
        readableDatabase.rawQuery(
            """
            SELECT id, name, quantity, status, category, serving_text, calories, protein_g, carbs_g, fat_g,
                   nutrition_source, source, image_uri, created_at, updated_at
            FROM grocery_items ORDER BY status ASC, id DESC
            """.trimIndent(),
            emptyArray(),
        ).useRows { cursor ->
            GroceryItem(
                id = cursor.long("id"),
                name = cursor.string("name"),
                quantity = cursor.string("quantity"),
                status = runCatching { GroceryStatus.valueOf(cursor.string("status")) }.getOrDefault(GroceryStatus.NEEDED),
                category = cursor.string("category").ifBlank { categorizeFood(cursor.string("name")) },
                servingText = cursor.string("serving_text"),
                calories = cursor.nullableInt("calories"),
                proteinGrams = cursor.nullableDouble("protein_g"),
                carbsGrams = cursor.nullableDouble("carbs_g"),
                fatGrams = cursor.nullableDouble("fat_g"),
                nutritionSource = cursor.string("nutrition_source"),
                source = cursor.string("source").ifBlank { "chat" },
                imageUri = cursor.nullableString("image_uri"),
                createdAtMillis = cursor.long("created_at"),
                updatedAtMillis = cursor.long("updated_at"),
            )
        }

    private fun readRecipes(): List<Recipe> =
        readableDatabase.rawQuery(
            """
            SELECT id, title, ingredients, steps, servings, prep_minutes, tags, rating, image_uri, created_at, updated_at
            FROM recipes ORDER BY id DESC
            """.trimIndent(),
            emptyArray(),
        ).useRows { cursor ->
            Recipe(
                id = cursor.long("id"),
                title = cursor.string("title"),
                ingredients = cursor.string("ingredients"),
                steps = cursor.string("steps"),
                servings = cursor.nullableInt("servings"),
                prepMinutes = cursor.nullableInt("prep_minutes"),
                tags = cursor.string("tags"),
                rating = cursor.nullableInt("rating"),
                imageUri = cursor.nullableString("image_uri"),
                createdAtMillis = cursor.long("created_at"),
                updatedAtMillis = cursor.long("updated_at"),
            )
        }

    private fun readMealLogs(): List<MealLog> =
        readableDatabase.rawQuery(
            """
            SELECT id, title, calories, protein_g, carbs_g, fat_g, meal_slot, used_items_text,
                   logged_date_epoch_day, source, created_at, updated_at
            FROM meal_logs ORDER BY id DESC
            """.trimIndent(),
            emptyArray(),
        ).useRows { cursor ->
            val createdAt = cursor.long("created_at")
            val loggedDay = cursor.long("logged_date_epoch_day").takeIf { it > 0 } ?: createdAt.toEpochDay()
            MealLog(
                id = cursor.long("id"),
                title = cursor.string("title"),
                calories = cursor.int("calories"),
                proteinGrams = cursor.double("protein_g"),
                carbsGrams = cursor.double("carbs_g"),
                fatGrams = cursor.double("fat_g"),
                mealSlot = runCatching { MealSlot.valueOf(cursor.string("meal_slot")) }.getOrDefault(MealSlot.FLEX),
                usedItemsText = cursor.string("used_items_text"),
                loggedDateEpochDay = loggedDay,
                source = cursor.string("source"),
                createdAtMillis = createdAt,
                updatedAtMillis = cursor.long("updated_at"),
            )
        }

    private fun readMealPlans(): List<MealPlan> =
        readableDatabase.rawQuery(
            """
            SELECT id, title, days_text, grocery_hint, status, start_date_epoch_day, created_at, updated_at
            FROM meal_plans ORDER BY id DESC
            """.trimIndent(),
            emptyArray(),
        ).useRows { cursor ->
            MealPlan(
                id = cursor.long("id"),
                title = cursor.string("title"),
                daysText = cursor.string("days_text"),
                groceryHint = cursor.string("grocery_hint"),
                status = runCatching { MealPlanStatus.valueOf(cursor.string("status")) }.getOrDefault(MealPlanStatus.ACCEPTED),
                startDateEpochDay = cursor.nullableLong("start_date_epoch_day"),
                createdAtMillis = cursor.long("created_at"),
                updatedAtMillis = cursor.long("updated_at"),
            )
        }

    private fun readMealPlanEntries(): List<MealPlanEntry> =
        readableDatabase.rawQuery(
            """
            SELECT id, plan_id, date_epoch_day, slot, title, calorie_target, status, created_at, updated_at
            FROM meal_plan_entries ORDER BY date_epoch_day ASC, id ASC
            """.trimIndent(),
            emptyArray(),
        ).useRows { cursor ->
            MealPlanEntry(
                id = cursor.long("id"),
                planId = cursor.long("plan_id"),
                dateEpochDay = cursor.long("date_epoch_day"),
                slot = runCatching { MealSlot.valueOf(cursor.string("slot")) }.getOrDefault(MealSlot.FLEX),
                title = cursor.string("title"),
                calorieTarget = cursor.nullableInt("calorie_target"),
                status = runCatching { MealPlanEntryStatus.valueOf(cursor.string("status")) }.getOrDefault(MealPlanEntryStatus.PLANNED),
                createdAtMillis = cursor.long("created_at"),
                updatedAtMillis = cursor.long("updated_at"),
            )
        }

    private fun readReceipts(): List<ReceiptCapture> =
        readableDatabase.rawQuery(
            "SELECT id, image_uri, raw_text, status, created_at FROM receipt_captures ORDER BY id DESC",
            emptyArray(),
        ).useRows { cursor ->
            ReceiptCapture(
                id = cursor.long("id"),
                imageUri = cursor.string("image_uri"),
                rawText = cursor.string("raw_text"),
                status = runCatching { ReceiptStatus.valueOf(cursor.string("status")) }.getOrDefault(ReceiptStatus.SAVED),
                createdAtMillis = cursor.long("created_at"),
            )
        }

    private fun readPreferences(): FoodPreferences {
        val values = readableDatabase.rawQuery(
            "SELECT key, value FROM user_preferences",
            emptyArray(),
        ).useRows { cursor -> cursor.string("key") to cursor.string("value") }
            .toMap()
        return FoodPreferences(
            dietStyle = values[PREF_DIET_STYLE].orEmpty(),
            allergies = values[PREF_ALLERGIES].orEmpty(),
            dislikes = values[PREF_DISLIKES].orEmpty(),
            preferredStaples = values[PREF_STAPLES].orEmpty(),
            preferredCuisines = values[PREF_CUISINES].orEmpty(),
            preferredStores = values[PREF_STORES].orEmpty(),
            calorieGoal = values[PREF_CALORIE_GOAL].orEmpty(),
            proteinGoal = values[PREF_PROTEIN_GOAL].orEmpty(),
            healthNotes = values[PREF_HEALTH_NOTES].orEmpty(),
            customAiInstructions = values[PREF_AI_INSTRUCTIONS].orEmpty(),
        )
    }

    private fun createCoreTables(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS chat_messages (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                role TEXT NOT NULL,
                body TEXT NOT NULL,
                created_at INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS inventory_items (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL,
                quantity TEXT NOT NULL,
                zone TEXT NOT NULL,
                category TEXT NOT NULL DEFAULT '',
                serving_text TEXT NOT NULL DEFAULT '',
                calories INTEGER,
                protein_g REAL,
                carbs_g REAL,
                fat_g REAL,
                nutrition_source TEXT NOT NULL DEFAULT '',
                notes TEXT NOT NULL DEFAULT '',
                image_uri TEXT,
                expires_at INTEGER,
                source TEXT NOT NULL,
                source_message_id INTEGER,
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS grocery_items (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL,
                quantity TEXT NOT NULL,
                status TEXT NOT NULL,
                category TEXT NOT NULL DEFAULT '',
                serving_text TEXT NOT NULL DEFAULT '',
                calories INTEGER,
                protein_g REAL,
                carbs_g REAL,
                fat_g REAL,
                nutrition_source TEXT NOT NULL DEFAULT '',
                source TEXT NOT NULL DEFAULT 'chat',
                image_uri TEXT,
                source_message_id INTEGER,
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS recipes (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                title TEXT NOT NULL,
                ingredients TEXT NOT NULL,
                steps TEXT NOT NULL,
                servings INTEGER,
                prep_minutes INTEGER,
                tags TEXT NOT NULL DEFAULT '',
                rating INTEGER,
                image_uri TEXT,
                source_message_id INTEGER,
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS meal_logs (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                title TEXT NOT NULL,
                calories INTEGER NOT NULL,
                protein_g REAL NOT NULL,
                carbs_g REAL NOT NULL,
                fat_g REAL NOT NULL,
                meal_slot TEXT NOT NULL DEFAULT 'FLEX',
                used_items_text TEXT NOT NULL DEFAULT '',
                logged_date_epoch_day INTEGER NOT NULL DEFAULT 0,
                source TEXT NOT NULL,
                source_message_id INTEGER,
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS meal_plans (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                title TEXT NOT NULL,
                days_text TEXT NOT NULL,
                grocery_hint TEXT NOT NULL,
                status TEXT NOT NULL,
                start_date_epoch_day INTEGER,
                source_message_id INTEGER,
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL
            )
            """.trimIndent(),
        )
    }

    private fun createV2Tables(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS meal_plan_entries (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                plan_id INTEGER NOT NULL,
                date_epoch_day INTEGER NOT NULL,
                slot TEXT NOT NULL,
                title TEXT NOT NULL,
                calorie_target INTEGER,
                status TEXT NOT NULL,
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS receipt_captures (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                image_uri TEXT NOT NULL,
                raw_text TEXT NOT NULL DEFAULT '',
                status TEXT NOT NULL,
                created_at INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS user_preferences (
                key TEXT PRIMARY KEY,
                value TEXT NOT NULL,
                updated_at INTEGER NOT NULL
            )
            """.trimIndent(),
        )
    }

    private fun createV4Tables(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS chat_actions (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                title TEXT NOT NULL,
                summary TEXT NOT NULL,
                rows_text TEXT NOT NULL,
                operation TEXT NOT NULL,
                status TEXT NOT NULL,
                source TEXT NOT NULL,
                confidence REAL NOT NULL,
                source_message_id INTEGER,
                created_at INTEGER NOT NULL,
                resolved_at INTEGER
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS inventory_transactions (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                inventory_item_id INTEGER,
                item_name TEXT NOT NULL,
                quantity_text TEXT NOT NULL DEFAULT '',
                zone TEXT NOT NULL,
                action TEXT NOT NULL,
                reason TEXT NOT NULL DEFAULT '',
                related_recipe_id INTEGER,
                related_meal_log_id INTEGER,
                occurred_date_epoch_day INTEGER NOT NULL,
                source TEXT NOT NULL DEFAULT 'chat',
                created_at INTEGER NOT NULL
            )
            """.trimIndent(),
        )
    }

    private fun createV5Tables(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS food_events (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                type TEXT NOT NULL,
                started_at INTEGER NOT NULL,
                ended_at INTEGER,
                duration_minutes INTEGER,
                amount REAL,
                unit TEXT NOT NULL DEFAULT '',
                source TEXT NOT NULL DEFAULT 'manual',
                confidence TEXT NOT NULL DEFAULT 'ESTIMATED',
                related_recipe_id INTEGER,
                meal_log_id INTEGER,
                shopping_trip_id INTEGER,
                inventory_item_id INTEGER,
                note TEXT NOT NULL DEFAULT '',
                created_at INTEGER NOT NULL
            )
            """.trimIndent(),
        )
    }

    private fun createV6Tables(db: SQLiteDatabase) {
        db.addColumn("recipes", "image_uri TEXT")
    }

    private fun migrateToV2(db: SQLiteDatabase) {
        db.addColumn("inventory_items", "category TEXT NOT NULL DEFAULT ''")
        db.addColumn("inventory_items", "notes TEXT NOT NULL DEFAULT ''")
        db.addColumn("inventory_items", "image_uri TEXT")
        db.addColumn("inventory_items", "expires_at INTEGER")
        db.addColumn("grocery_items", "category TEXT NOT NULL DEFAULT ''")
        db.addColumn("grocery_items", "source TEXT NOT NULL DEFAULT 'chat'")
        db.addColumn("grocery_items", "image_uri TEXT")
        db.addColumn("recipes", "servings INTEGER")
        db.addColumn("recipes", "prep_minutes INTEGER")
        db.addColumn("recipes", "tags TEXT NOT NULL DEFAULT ''")
        db.addColumn("meal_logs", "meal_slot TEXT NOT NULL DEFAULT 'FLEX'")
        db.addColumn("meal_logs", "used_items_text TEXT NOT NULL DEFAULT ''")
        db.addColumn("meal_logs", "logged_date_epoch_day INTEGER NOT NULL DEFAULT 0")
        db.addColumn("meal_plans", "start_date_epoch_day INTEGER")
        createV2Tables(db)
    }

    private fun migrateToV3(db: SQLiteDatabase) {
        addNutritionColumns(db, "inventory_items")
        addNutritionColumns(db, "grocery_items")
    }

    private fun migrateToV4(db: SQLiteDatabase) {
        createV4Tables(db)
    }

    private fun migrateToV5(db: SQLiteDatabase) {
        createV5Tables(db)
    }

    private fun migrateToV6(db: SQLiteDatabase) {
        createV6Tables(db)
    }

    private fun addNutritionColumns(db: SQLiteDatabase, table: String) {
        db.addColumn(table, "serving_text TEXT NOT NULL DEFAULT ''")
        db.addColumn(table, "calories INTEGER")
        db.addColumn(table, "protein_g REAL")
        db.addColumn(table, "carbs_g REAL")
        db.addColumn(table, "fat_g REAL")
        db.addColumn(table, "nutrition_source TEXT NOT NULL DEFAULT ''")
    }

    private fun recordChatAction(
        draft: FoodDraft,
        status: ChatActionStatus,
        sourceMessageId: Long?,
        summary: String,
    ) {
        val now = now()
        writableDatabase.insert(
            "chat_actions",
            null,
            ContentValues().apply {
                put("title", draft.title)
                put("summary", summary)
                put("rows_text", draft.rows.joinToString("\n"))
                put("operation", draft.operation.name)
                put("status", status.name)
                put("source", draft.draftSource.name)
                put("confidence", draft.confidence)
                put("source_message_id", sourceMessageId)
                put("created_at", now)
                put("resolved_at", now)
            },
        )
    }

    private fun insertInventoryTransaction(
        inventoryItemId: Long?,
        itemName: String,
        quantityText: String,
        zone: StorageZone,
        action: InventoryAction,
        reason: String,
        relatedRecipeId: Long?,
        relatedMealLogId: Long?,
        source: String,
    ) {
        val now = now()
        writableDatabase.insert(
            "inventory_transactions",
            null,
            ContentValues().apply {
                if (inventoryItemId == null) putNull("inventory_item_id") else put("inventory_item_id", inventoryItemId)
                put("item_name", itemName)
                put("quantity_text", quantityText)
                put("zone", zone.name)
                put("action", action.name)
                put("reason", reason)
                if (relatedRecipeId == null) putNull("related_recipe_id") else put("related_recipe_id", relatedRecipeId)
                if (relatedMealLogId == null) putNull("related_meal_log_id") else put("related_meal_log_id", relatedMealLogId)
                put("occurred_date_epoch_day", todayEpochDay())
                put("source", source)
                put("created_at", now)
            },
        )
    }

    private fun insertFoodEvent(
        type: FoodEventType,
        startedAtMillis: Long = now(),
        endedAtMillis: Long? = null,
        durationMinutes: Int? = null,
        amount: Double? = null,
        unit: String = "",
        source: String = "manual",
        confidence: FoodEventConfidence = FoodEventConfidence.ESTIMATED,
        relatedRecipeId: Long? = null,
        mealLogId: Long? = null,
        shoppingTripId: Long? = null,
        inventoryItemId: Long? = null,
        note: String = "",
    ): Long {
        val createdAt = now()
        return writableDatabase.insert(
            "food_events",
            null,
            ContentValues().apply {
                put("type", type.name)
                put("started_at", startedAtMillis)
                if (endedAtMillis == null) putNull("ended_at") else put("ended_at", endedAtMillis)
                if (durationMinutes == null) putNull("duration_minutes") else put("duration_minutes", durationMinutes)
                if (amount == null) putNull("amount") else put("amount", amount)
                put("unit", unit)
                put("source", source)
                put("confidence", confidence.name)
                if (relatedRecipeId == null) putNull("related_recipe_id") else put("related_recipe_id", relatedRecipeId)
                if (mealLogId == null) putNull("meal_log_id") else put("meal_log_id", mealLogId)
                if (shoppingTripId == null) putNull("shopping_trip_id") else put("shopping_trip_id", shoppingTripId)
                if (inventoryItemId == null) putNull("inventory_item_id") else put("inventory_item_id", inventoryItemId)
                put("note", note)
                put("created_at", createdAt)
            },
        )
    }

    private fun recordUsedItems(
        usedItemsText: String,
        mealId: Long,
        recipeId: Long?,
        reason: String,
        source: String,
    ) {
        if (usedItemsText.isBlank()) return
        val tokens = usedItemsText
            .split(",", "\n", ";")
            .map { it.cleanIngredientName() }
            .filter { it.length >= 3 }
        val inventory = readInventory()
        tokens.forEach { token ->
            val item = inventory.firstOrNull { it.name.foodMatches(token) || token.foodMatches(it.name) } ?: return@forEach
            insertInventoryTransaction(
                inventoryItemId = item.id,
                itemName = item.name,
                quantityText = "used",
                zone = item.zone,
                action = InventoryAction.USED,
                reason = reason,
                relatedRecipeId = recipeId,
                relatedMealLogId = mealId,
                source = source,
            )
        }
    }

    private fun matchedInventoryForRecipe(recipe: Recipe): List<InventoryItem> {
        val inventory = readInventory()
        return recipe.ingredientNames().mapNotNull { ingredient ->
            inventory.firstOrNull { item -> item.name.foodMatches(ingredient) || ingredient.foodMatches(item.name) }
        }.distinctBy { it.id }
    }

    private fun missingIngredientsForRecipe(recipe: Recipe): List<String> {
        val inventory = readInventory()
        return recipe.ingredientNames()
            .filterNot { ingredient ->
                inventory.any { item -> item.name.foodMatches(ingredient) || ingredient.foodMatches(item.name) }
            }
            .take(12)
    }

    private fun markMatchingPlanEaten(title: String) {
        val today = todayEpochDay()
        readMealPlanEntries()
            .filter { it.dateEpochDay == today }
            .filter { entry -> entry.title.foodMatches(title) || title.foodMatches(entry.title) }
            .forEach { entry ->
                writableDatabase.update(
                    "meal_plan_entries",
                    ContentValues().apply {
                        put("status", MealPlanEntryStatus.EATEN.name)
                        put("updated_at", now())
                    },
                    "id = ?",
                    arrayOf(entry.id.toString()),
                )
            }
    }

    private fun estimateRecipeCalories(recipe: Recipe): Int =
        recipe.ingredientNames().sumOf { estimateItemNutrition(it).calories }.coerceIn(220, 950)

    private fun estimateRecipeProtein(recipe: Recipe): Double =
        recipe.ingredientNames().sumOf { estimateItemNutrition(it).proteinGrams }.coerceIn(8.0, 70.0)

    companion object {
        private const val DB_NAME = "wonderfood.db"
        private const val DB_VERSION = 6
        private const val PREF_DIET_STYLE = "diet_style"
        private const val PREF_ALLERGIES = "allergies"
        private const val PREF_DISLIKES = "dislikes"
        private const val PREF_STAPLES = "preferred_staples"
        private const val PREF_CUISINES = "preferred_cuisines"
        private const val PREF_STORES = "preferred_stores"
        private const val PREF_CALORIE_GOAL = "calorie_goal"
        private const val PREF_PROTEIN_GOAL = "protein_goal"
        private const val PREF_HEALTH_NOTES = "health_notes"
        private const val PREF_AI_INSTRUCTIONS = "ai_instructions"
        private const val PERSONAL_SEED_ENABLED = true
        private const val PERSONAL_SEED_SOURCE = "personal_seed"
        private const val PERSONAL_MEAL_PLAN_TITLE = "Personal food memory plan"
    }
}

fun classifyStorageZone(value: String): StorageZone {
    val text = value.lowercase()
    return when {
        listOf("frozen", "freezer", "ice cream", "popsicle").any { it in text } -> StorageZone.FREEZER
        listOf(
            "milk",
            "yogurt",
            "egg",
            "cheese",
            "spinach",
            "lettuce",
            "chicken",
            "fish",
            "tofu",
            "berries",
            "cilantro",
            "parsley",
        ).any { it in text } -> StorageZone.FRIDGE
        else -> StorageZone.PANTRY
    }
}

fun categorizeFood(value: String): String {
    val text = value.lowercase()
    return when {
        listOf("chicken", "fish", "salmon", "beef", "egg", "tofu", "bean", "lentil", "yogurt").any { it in text } -> "protein"
        listOf("spinach", "lettuce", "greens", "broccoli", "carrot", "pepper", "tomato").any { it in text } -> "produce"
        listOf("berry", "banana", "apple", "orange", "grape", "fruit").any { it in text } -> "fruit"
        listOf("rice", "oat", "pasta", "bread", "tortilla", "cereal").any { it in text } -> "grain"
        listOf("milk", "cheese", "cream").any { it in text } -> "dairy"
        listOf("oil", "butter", "avocado", "nuts", "peanut").any { it in text } -> "fat"
        else -> "other"
    }
}

private fun FoodCandidate.withEstimatedNutrition(): FoodCandidate {
    if (calories != null || proteinGrams != null || carbsGrams != null || fatGrams != null) return this
    val estimate = estimateItemNutrition(name)
    return copy(
        servingText = servingText.ifBlank { estimate.servingText },
        calories = estimate.calories,
        proteinGrams = estimate.proteinGrams,
        carbsGrams = estimate.carbsGrams,
        fatGrams = estimate.fatGrams,
        nutritionSource = nutritionSource.ifBlank { "ai_estimate_local" },
    )
}

private data class ItemNutritionEstimate(
    val servingText: String,
    val calories: Int,
    val proteinGrams: Double,
    val carbsGrams: Double,
    val fatGrams: Double,
)

private fun estimateItemNutrition(value: String): ItemNutritionEstimate {
    val text = value.lowercase()
    return when {
        "egg" in text -> ItemNutritionEstimate("1 large egg", 70, 6.0, 1.0, 5.0)
        "greek yogurt" in text || "yogurt" in text -> ItemNutritionEstimate("170 g", 120, 17.0, 7.0, 3.0)
        "spinach" in text -> ItemNutritionEstimate("100 g", 23, 3.0, 4.0, 0.0)
        "berries" in text || "berry" in text -> ItemNutritionEstimate("100 g", 57, 1.0, 14.0, 0.0)
        "oat" in text -> ItemNutritionEstimate("40 g dry", 150, 5.0, 27.0, 3.0)
        "banana" in text -> ItemNutritionEstimate("1 medium", 105, 1.0, 27.0, 0.0)
        "chicken" in text -> ItemNutritionEstimate("100 g cooked", 165, 31.0, 0.0, 4.0)
        "rice" in text -> ItemNutritionEstimate("1 cup cooked", 205, 4.0, 45.0, 0.0)
        "beans" in text || "lentil" in text -> ItemNutritionEstimate("1 cup cooked", 230, 15.0, 40.0, 1.0)
        "milk" in text -> ItemNutritionEstimate("1 cup", 150, 8.0, 12.0, 8.0)
        "bread" in text -> ItemNutritionEstimate("1 slice", 90, 4.0, 17.0, 1.0)
        "cheese" in text -> ItemNutritionEstimate("28 g", 110, 7.0, 1.0, 9.0)
        else -> ItemNutritionEstimate("1 serving", 100, 3.0, 12.0, 3.0)
    }
}

private fun List<String>.pickSlot(index: Int): MealSlot =
    when (getOrNull(index)?.lowercase().orEmpty()) {
        "breakfast" -> MealSlot.BREAKFAST
        "lunch" -> MealSlot.LUNCH
        "dinner" -> MealSlot.DINNER
        "snack" -> MealSlot.SNACK
        else -> MealSlot.FLEX
    }

private fun String.toMealPlanEntries(): List<MealPlanEntryDraft> =
    lines()
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .mapIndexed { index, line ->
            val beforeColon = line.substringBefore(":").trim().split(" ")
            MealPlanEntryDraft(
                dayOffset = index,
                slot = beforeColon.pickSlot(1),
                title = line.substringAfter(":", line).trim().ifBlank { line },
            )
        }

private fun Recipe.ingredientNames(): List<String> =
    ingredients
        .split(",", "\n", ";")
        .map { it.cleanIngredientName() }
        .filter { it.length >= 3 }
        .distinctBy { it.lowercase() }

private fun String.cleanIngredientName(): String =
    replace(Regex("""^[\s\-*•\d./]+"""), "")
        .replace(
            Regex(
                """\b(cups?|tbsp|tsp|teaspoons?|tablespoons?|grams?|g|kg|ml|liters?|large|medium|small|cooked|dry|fresh|optional|to taste)\b""",
                RegexOption.IGNORE_CASE,
            ),
            "",
        )
        .trim()

private fun String.foodMatches(other: String): Boolean {
    val left = cleanIngredientName().lowercase()
    val right = other.cleanIngredientName().lowercase()
    if (left.length < 3 || right.length < 3) return false
    return left.contains(right) || right.contains(left)
}

private fun SQLiteDatabase.addColumn(table: String, definition: String) {
    runCatching { execSQL("ALTER TABLE $table ADD COLUMN $definition") }
}

private val Int.plural: String
    get() = if (this == 1) "" else "s"

private fun now(): Long = System.currentTimeMillis()

private fun todayEpochDay(): Long = LocalDate.now().toEpochDay()

private fun Long.toEpochDay(): Long =
    Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).toLocalDate().toEpochDay()

private inline fun <T> Cursor.useRows(mapper: (Cursor) -> T): List<T> =
    use { cursor ->
        buildList {
            while (cursor.moveToNext()) add(mapper(cursor))
        }
    }

private fun Cursor.string(column: String): String = getString(getColumnIndexOrThrow(column)).orEmpty()

private fun Cursor.nullableString(column: String): String? {
    val index = getColumnIndexOrThrow(column)
    return if (isNull(index)) null else getString(index)
}

private fun Cursor.long(column: String): Long = getLong(getColumnIndexOrThrow(column))

private fun Cursor.nullableLong(column: String): Long? {
    val index = getColumnIndexOrThrow(column)
    return if (isNull(index)) null else getLong(index)
}

private fun Cursor.int(column: String): Int = getInt(getColumnIndexOrThrow(column))

private fun Cursor.double(column: String): Double = getDouble(getColumnIndexOrThrow(column))

private fun Cursor.nullableDouble(column: String): Double? {
    val index = getColumnIndexOrThrow(column)
    return if (isNull(index)) null else getDouble(index)
}

private fun Cursor.nullableInt(column: String): Int? {
    val index = getColumnIndexOrThrow(column)
    return if (isNull(index)) null else getInt(index)
}
