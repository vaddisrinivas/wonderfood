package com.wonderfood.app.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.wonderfood.app.LinkActionOperation
import com.wonderfood.app.WonderFoodCommandContract
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.json.JSONArray
import org.json.JSONObject

class FoodChatStore(
    context: Context,
    dbName: String = DB_NAME,
) : SQLiteOpenHelper(context, dbName, null, DB_VERSION), FoodDraftCommandSink, FoodMutationCommandSink {
    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        db.setForeignKeyConstraintsEnabled(true)
    }

    override fun onCreate(db: SQLiteDatabase) {
        createCoreTables(db)
        createV2Tables(db)
        createV4Tables(db)
        createV5Tables(db)
        createV8Tables(db)
        createV9Tables(db)
        createV10Tables(db)
        createV11Tables(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) migrateToV2(db)
        if (oldVersion < 3) migrateToV3(db)
        if (oldVersion < 4) migrateToV4(db)
        if (oldVersion < 5) migrateToV5(db)
        if (oldVersion < 6) migrateToV6(db)
        if (oldVersion < 7) migrateToV7(db)
        if (oldVersion < 8) migrateToV8(db)
        if (oldVersion < 9) migrateToV9(db)
        if (oldVersion < 10) migrateToV10(db)
        if (oldVersion < 11) migrateToV11(db)
    }

    @Synchronized
    fun seedIfEmpty() {
        if (readMessages().isNotEmpty()) return
        insertMessage(
            role = ChatRole.ASSISTANT,
            body = "Tell me food things naturally. Try: `I bought eggs, Greek yogurt, spinach and frozen berries` or `Log chicken rice bowl for lunch`.",
        )
    }

    @Synchronized
    fun insertMessage(role: ChatRole, body: String, chatId: Long = currentChatId()): Long {
        val now = now()
        return writableDatabase.insert(
            "chat_messages",
            null,
            ContentValues().apply {
                put("role", role.name)
                put("body", body)
                put("created_at", now)
                put("chat_id", chatId)
            },
        )
    }

    @Synchronized
    fun startNewChat(): Long {
        val chatId = currentChatId() + 1L
        return insertMessage(
            role = ChatRole.ASSISTANT,
            body = "New chat started. Your kitchen, recipes, groceries, plans, and settings are still saved.",
            chatId = chatId,
        )
    }

    @Synchronized
    fun clearChatHistory(): Long {
        writableDatabase.delete("chat_messages", null, null)
        return insertMessage(
            role = ChatRole.ASSISTANT,
            body = "Chat memory reset. Your kitchen, recipes, groceries, plans, and settings are still saved.",
            chatId = 1L,
        )
    }

    @Synchronized
    fun updateMessage(id: Long, body: String): Boolean {
        val clean = body.trim()
        if (clean.isBlank()) return false
        return writableDatabase.update(
            "chat_messages",
            ContentValues().apply { put("body", clean) },
            "id = ?",
            arrayOf(id.toString()),
        ) == 1
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
    fun checkpointForBackup() {
        runCatching {
            writableDatabase.rawQuery("PRAGMA wal_checkpoint(FULL)", emptyArray()).use { cursor ->
                while (cursor.moveToNext()) Unit
            }
        }
    }

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
            PREF_AI_SKILL_OVERRIDE to preferences.aiSkillOverride,
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
        return applyDraft(draft, sourceMessageId, draft.draftSource.name.lowercase())
    }

    @Synchronized
    override fun applyDraft(draft: FoodDraft, sourceMessageId: Long?, writeSource: String): String {
        val source = writeSource.ifBlank { draft.draftSource.name.lowercase() }
        val db = writableDatabase
        db.beginTransaction()
        return try {
            val summary = applyDraftChanges(draft, sourceMessageId, source)
            recordChatAction(draft, ChatActionStatus.ACCEPTED, sourceMessageId, summary)
            recordCommandEvent(draft, status = "APPLIED", sourceMessageId = sourceMessageId, summary = summary, sourceOverride = source)
            db.setTransactionSuccessful()
            summary
        } finally {
            db.endTransaction()
        }
    }

    private fun applyDraftChanges(draft: FoodDraft, sourceMessageId: Long?, writeSource: String): String =
        when (draft) {
            is CompositeDraft -> {
                val summaries = draft.drafts.map { applyDraftChanges(it, sourceMessageId, writeSource) }
                "Saved ${draft.drafts.size} linked change${draft.drafts.size.plural}: ${summaries.joinToString(" ")}"
            }
            is InventoryDraft -> {
                draft.items.forEach { addInventory(it, sourceMessageId, source = writeSource) }
                "Saved ${draft.items.size} inventory item${draft.items.size.plural}."
            }
            is GroceryDraft -> {
                draft.items.forEach { addGrocery(it, sourceMessageId, source = writeSource) }
                "Added ${draft.items.size} grocery item${draft.items.size.plural}."
            }
            is ReceiptDraft -> {
                val inventoryItems = draft.items.filter { it.disposition == ReceiptItemDisposition.INVENTORY }
                inventoryItems.forEach { receiptItem ->
                    val item = FoodIntakeEnricher.persistReceiptCandidate(receiptItem.food, draft.receiptId)
                    addInventory(
                        item,
                        sourceMessageId,
                        source = "receipt",
                        transactionAction = InventoryAction.BOUGHT,
                        transactionReason = "Purchased on receipt${draft.receiptId?.let { " #$it" }.orEmpty()}",
                        purchase = InventoryBatchPurchase(
                            purchasedAtMillis = draft.purchasedAtMillis,
                            priceCents = receiptItem.linePriceCents,
                            currencyCode = draft.currencyCode,
                            storeName = listOf(draft.merchant, draft.storeLocation)
                                .filter(String::isNotBlank)
                                .joinToString(" — "),
                        ),
                    )
                }
                val excluded = draft.items.size - inventoryItems.size
                "Put away ${inventoryItems.size} receipt item${inventoryItems.size.plural}" +
                    if (excluded > 0) "; kept $excluded non-food/ignored line${excluded.plural} out of Kitchen." else "."
            }
            is LinkActionDraft -> applyLinkActionDraft(draft, sourceMessageId, writeSource)
            is RecipeDraft -> {
                addRecipe(draft, sourceMessageId, source = writeSource)
                "Saved recipe: ${draft.titleText}."
            }
            is MealLogDraft -> {
                addMealLog(draft.copy(source = draft.source.ifBlank { writeSource }), sourceMessageId)
                "Logged meal: ${draft.titleText}."
            }
            is MealPlanDraft -> {
                addMealPlan(draft, sourceMessageId, source = writeSource)
                "Saved meal plan: ${draft.titleText}."
            }
        }

    private fun applyLinkActionDraft(draft: LinkActionDraft, sourceMessageId: Long?, writeSource: String): String =
        when (draft.targetKind) {
            "event" -> applyEventLinkAction(draft, writeSource)
            "grocery" -> applyGroceryLinkAction(draft, sourceMessageId, writeSource)
            "inventory" -> applyInventoryLinkAction(draft, sourceMessageId, writeSource)
            "meal_log" -> applyMealLogLinkAction(draft, sourceMessageId, writeSource)
            "meal_plan" -> applyMealPlanLinkAction(draft, sourceMessageId, writeSource)
            "plan_entry" -> applyPlanEntryLinkAction(draft, writeSource)
            "preferences" -> applyPreferencesLinkAction(draft)
            "recipe" -> applyRecipeLinkAction(draft, sourceMessageId, writeSource)
            else -> throw FoodDraftApplyException("Linked action target '${draft.targetKind}' is not supported. No changes were applied.")
        }

    private fun applyInventoryLinkAction(draft: LinkActionDraft, sourceMessageId: Long?, writeSource: String): String {
        if (draft.isCreateAction()) {
            val candidate = draft.toFoodCandidate(defaultName = "Linked pantry item")
            addInventory(candidate, sourceMessageId, source = writeSource)
            return "Added pantry item: ${candidate.name}."
        }
        val item = resolveInventoryLinkTarget(draft)
            ?: return draft.unresolvedTargetMessage("pantry item")
        if (draft.destructive) {
            deleteInventory(item.id)
            return "Archived pantry item: ${item.name}."
        }
        val name = draft.firstField("name", "item", "title").ifBlank { item.name }
        updateInventory(
            id = item.id,
            name = name,
            quantity = draft.firstField("quantity", "amount").ifBlank { item.quantity },
            zone = draft.storageZone(item.zone, name),
            category = draft.firstField("category").ifBlank { item.category.ifBlank { categorizeFood(name) } },
            servingText = draft.firstField("serving_text").ifBlank { item.servingText },
            calories = draft.firstInt("calories") ?: item.calories,
            proteinGrams = draft.firstDouble("protein_g") ?: item.proteinGrams,
            carbsGrams = draft.firstDouble("carbs_g") ?: item.carbsGrams,
            fatGrams = draft.firstDouble("fat_g") ?: item.fatGrams,
            nutritionSource = draft.firstField("nutrition_source").ifBlank { item.nutritionSource },
            notes = draft.firstField("notes", "text").ifBlank { item.notes },
            imageUri = draft.firstField("image_uri").ifBlank { item.imageUri.orEmpty() }.ifBlank { null },
            imageUrl = draft.firstField("image_url").ifBlank { item.imageUrl },
            expiresAtMillis = draft.firstLong("expires_at_millis") ?: item.expiresAtMillis,
        )
        return "Updated pantry item: $name."
    }

    private fun applyGroceryLinkAction(draft: LinkActionDraft, sourceMessageId: Long?, writeSource: String): String {
        if (draft.isCreateAction()) {
            val candidate = draft.toFoodCandidate(defaultName = "Linked grocery item")
            addGrocery(candidate, sourceMessageId, source = writeSource)
            return "Added grocery item: ${candidate.name}."
        }
        val item = resolveGroceryLinkTarget(draft)
            ?: return draft.unresolvedTargetMessage("grocery item")
        if (draft.isBoughtAction()) return markGroceryBought(item.id)
        if (draft.destructive) {
            deleteGrocery(item.id)
            return "Archived grocery item: ${item.name}."
        }
        val name = draft.firstField("name", "item", "title").ifBlank { item.name }
        updateGrocery(
            id = item.id,
            name = name,
            quantity = draft.firstField("quantity", "amount").ifBlank { item.quantity },
            status = draft.groceryStatus(item.status),
            category = draft.firstField("category").ifBlank { item.category.ifBlank { categorizeFood(name) } },
            servingText = draft.firstField("serving_text").ifBlank { item.servingText },
            calories = draft.firstInt("calories") ?: item.calories,
            proteinGrams = draft.firstDouble("protein_g") ?: item.proteinGrams,
            carbsGrams = draft.firstDouble("carbs_g") ?: item.carbsGrams,
            fatGrams = draft.firstDouble("fat_g") ?: item.fatGrams,
            nutritionSource = draft.firstField("nutrition_source").ifBlank { item.nutritionSource.ifBlank { writeSource } },
            source = draft.firstField("source").ifBlank { writeSource },
            imageUri = draft.firstField("image_uri").ifBlank { item.imageUri.orEmpty() }.ifBlank { null },
            imageUrl = draft.firstField("image_url").ifBlank { item.imageUrl },
        )
        return "Updated grocery item: $name."
    }

    private fun applyRecipeLinkAction(draft: LinkActionDraft, sourceMessageId: Long?, writeSource: String): String {
        if (draft.isCreateAction()) {
            val title = draft.firstField("title", "name").ifBlank { draft.displayName }.ifBlank { "Linked recipe" }
            addRecipe(
                RecipeDraft(
                    titleText = title,
                    ingredientsText = draft.firstField("ingredients"),
                    stepsText = draft.firstField("steps", "text", "notes"),
                    servings = draft.firstInt("servings"),
                    prepMinutes = draft.firstInt("prep_minutes"),
                    tags = draft.firstField("tags"),
                    imageUri = draft.firstField("image_uri").ifBlank { null },
                    imageUrl = draft.firstField("image_url"),
                ),
                sourceMessageId,
                source = writeSource,
            )
            return "Saved recipe: $title."
        }
        val recipe = resolveRecipeLinkTarget(draft)
            ?: return draft.unresolvedTargetMessage("recipe")
        if (draft.destructive) {
            deleteRecipe(recipe.id)
            return "Archived recipe: ${recipe.title}."
        }
        val title = draft.firstField("title", "name").ifBlank { recipe.title }
        updateRecipe(
            id = recipe.id,
            title = title,
            ingredients = draft.firstField("ingredients").ifBlank { recipe.ingredients },
            steps = draft.firstField("steps", "text", "notes").ifBlank { recipe.steps },
            servings = draft.firstInt("servings") ?: recipe.servings,
            prepMinutes = draft.firstInt("prep_minutes") ?: recipe.prepMinutes,
            tags = draft.firstField("tags").ifBlank { recipe.tags },
            imageUri = draft.firstField("image_uri").ifBlank { recipe.imageUri.orEmpty() }.ifBlank { null },
            imageUrl = draft.firstField("image_url").ifBlank { recipe.imageUrl },
        )
        return "Updated recipe: $title."
    }

    private fun applyMealLogLinkAction(draft: LinkActionDraft, sourceMessageId: Long?, writeSource: String): String {
        if (draft.isCreateAction() || draft.isLogAction()) {
            val title = draft.firstField("title", "name", "text").ifBlank { draft.displayName }.ifBlank { "Linked meal" }
            addMealLog(
                MealLogDraft(
                    titleText = title,
                    calories = draft.firstInt("calories"),
                    proteinGrams = draft.firstDouble("protein_g"),
                    carbsGrams = draft.firstDouble("carbs_g"),
                    fatGrams = draft.firstDouble("fat_g"),
                    mealSlot = draft.mealSlot(MealSlot.FLEX),
                    usedItemsText = draft.firstField("used_items_text", "ingredients"),
                    loggedDateEpochDay = draft.firstLong("logged_date_epoch_day", "date_epoch_day"),
                    source = writeSource,
                ),
                sourceMessageId,
            )
            return "Logged meal: $title."
        }
        val meal = resolveMealLogLinkTarget(draft)
            ?: return draft.unresolvedTargetMessage("meal log")
        if (draft.destructive) {
            deleteMealLog(meal.id)
            return "Archived meal log: ${meal.title}."
        }
        val title = draft.firstField("title", "name", "text").ifBlank { meal.title }
        updateMealLog(
            id = meal.id,
            title = title,
            calories = draft.firstInt("calories") ?: meal.calories,
            proteinGrams = draft.firstDouble("protein_g") ?: meal.proteinGrams,
            carbsGrams = draft.firstDouble("carbs_g") ?: meal.carbsGrams,
            fatGrams = draft.firstDouble("fat_g") ?: meal.fatGrams,
            mealSlot = draft.mealSlot(meal.mealSlot),
            usedItemsText = draft.firstField("used_items_text", "ingredients").ifBlank { meal.usedItemsText },
            loggedDateEpochDay = draft.firstLong("logged_date_epoch_day", "date_epoch_day") ?: meal.loggedDateEpochDay,
            source = draft.firstField("source").ifBlank { meal.source.ifBlank { writeSource } },
        )
        return "Updated meal log: $title."
    }

    private fun applyMealPlanLinkAction(draft: LinkActionDraft, sourceMessageId: Long?, writeSource: String): String {
        if (draft.isCreateAction()) {
            val title = draft.firstField("title", "name").ifBlank { draft.displayName }.ifBlank { "Linked meal plan" }
            addMealPlan(
                MealPlanDraft(
                    titleText = title,
                    daysText = draft.firstField("days_text", "days", "text"),
                    groceryHint = draft.firstField("grocery_hint"),
                    startDateEpochDay = draft.firstLong("date_epoch_day"),
                ),
                sourceMessageId,
                source = writeSource,
            )
            return "Saved meal plan: $title."
        }
        val plan = resolveMealPlanLinkTarget(draft)
            ?: return draft.unresolvedTargetMessage("meal plan")
        if (draft.destructive) {
            val entryIds = readMealPlanEntries().filter { it.planId == plan.id }.map { it.id }.toSet()
            deleteMealPlanEntries(entryIds)
            writableDatabase.delete("meal_plans", "id = ?", arrayOf(plan.id.toString()))
            return "Deleted meal plan: ${plan.title}."
        }
        val title = draft.firstField("title", "name").ifBlank { plan.title }
        updateMealPlan(
            id = plan.id,
            title = title,
            daysText = draft.firstField("days_text", "days", "text").ifBlank { plan.daysText },
            groceryHint = draft.firstField("grocery_hint").ifBlank { plan.groceryHint },
            startDateEpochDay = draft.firstLong("date_epoch_day") ?: plan.startDateEpochDay,
        )
        return "Updated meal plan: $title."
    }

    private fun applyPlanEntryLinkAction(draft: LinkActionDraft, writeSource: String): String {
        if (draft.isCreateAction()) {
            val title = draft.firstField("title", "name", "text").ifBlank { draft.displayName }.ifBlank { "Linked planned meal" }
            val entryId = addMealPlanEntry(
                dateEpochDay = draft.firstLong("date_epoch_day") ?: todayEpochDay(),
                slot = draft.mealSlot(MealSlot.FLEX),
                title = title,
                calorieTarget = draft.firstInt("calorie_target", "calories"),
            )
            return "Added planned meal: $title (#$entryId)."
        }
        val entry = resolvePlanEntryLinkTarget(draft)
            ?: return draft.unresolvedTargetMessage("planned meal")
        if (draft.destructive) {
            deleteMealPlanEntry(entry.id)
            return "Archived planned meal: ${entry.title}."
        }
        val title = draft.firstField("title", "name", "text").ifBlank { entry.title }
        updateMealPlanEntry(
            id = entry.id,
            dateEpochDay = draft.firstLong("date_epoch_day") ?: entry.dateEpochDay,
            slot = draft.mealSlot(entry.slot),
            title = title,
            calorieTarget = draft.firstInt("calorie_target", "calories") ?: entry.calorieTarget,
            status = draft.planEntryStatus(entry.status),
        )
        return "Updated planned meal: $title."
    }

    private fun applyPreferencesLinkAction(draft: LinkActionDraft): String {
        if (draft.destructive) {
            throw FoodDraftApplyException("Preference clear/delete links are not supported. No changes were applied.")
        }
        val current = readPreferences()
        savePreferences(
            current.copy(
                dietStyle = draft.firstField("diet_style").ifBlank { current.dietStyle },
                allergies = draft.firstField("allergies").ifBlank { current.allergies },
                dislikes = draft.firstField("dislikes").ifBlank { current.dislikes },
                preferredStaples = draft.firstField("preferred_staples").ifBlank { current.preferredStaples },
                preferredCuisines = draft.firstField("preferred_cuisines").ifBlank { current.preferredCuisines },
                preferredStores = draft.firstField("preferred_stores").ifBlank { current.preferredStores },
                calorieGoal = draft.firstField("calorie_goal").ifBlank { current.calorieGoal },
                proteinGoal = draft.firstField("protein_goal").ifBlank { current.proteinGoal },
                healthNotes = draft.firstField("health_notes").ifBlank { current.healthNotes },
                customAiInstructions = draft.firstField("custom_ai_instructions").ifBlank { current.customAiInstructions },
            ),
        )
        return "Updated preferences."
    }

    private fun applyEventLinkAction(draft: LinkActionDraft, writeSource: String): String {
        if (draft.isCreateAction() || draft.isLogAction()) {
            val eventType = draft.foodEventType(FoodEventType.MEAL)
            val eventId = logFoodEvent(
                type = eventType,
                amount = draft.firstDouble("amount"),
                unit = draft.firstField("unit"),
                source = draft.firstField("source").ifBlank { writeSource },
                confidence = draft.foodEventConfidence(FoodEventConfidence.ESTIMATED),
                relatedRecipeId = draft.firstLong("recipe_id"),
                mealLogId = draft.firstLong("meal_log_id"),
                shoppingTripId = draft.firstLong("shopping_trip_id"),
                inventoryItemId = draft.firstLong("inventory_item_id"),
                note = draft.firstField("notes", "text", "title"),
            )
            return "Logged ${eventType.label.lowercase()} event (#$eventId)."
        }
        val event = resolveEventLinkTarget(draft)
            ?: return draft.unresolvedTargetMessage("event")
        if (draft.destructive) {
            val deleted = deleteFoodEvent(event.id)
            return if (deleted) "Deleted event: ${event.note.ifBlank { event.type.label }}." else "Event not found."
        }
        val eventType = draft.foodEventType(event.type)
        writableDatabase.update(
            "food_events",
            ContentValues().apply {
                put("type", eventType.name)
                put("started_at", draft.firstLong("started_at_millis") ?: event.startedAtMillis)
                val endedAt = draft.firstLong("ended_at_millis") ?: event.endedAtMillis
                if (endedAt == null) putNull("ended_at") else put("ended_at", endedAt)
                val duration = draft.firstInt("duration_minutes") ?: event.durationMinutes
                if (duration == null) putNull("duration_minutes") else put("duration_minutes", duration)
                val amount = draft.firstDouble("amount") ?: event.amount
                if (amount == null) putNull("amount") else put("amount", amount)
                put("unit", draft.firstField("unit").ifBlank { event.unit })
                put("source", draft.firstField("source").ifBlank { event.source.ifBlank { writeSource } })
                put("confidence", draft.foodEventConfidence(event.confidence).name)
                put("note", draft.firstField("notes", "text", "title").ifBlank { event.note })
            },
            "id = ?",
            arrayOf(event.id.toString()),
        )
        return "Updated event: ${draft.firstField("notes", "text", "title").ifBlank { event.note.ifBlank { eventType.label } }}."
    }

    private fun resolveInventoryLinkTarget(draft: LinkActionDraft): InventoryItem? =
        readInventory().resolveByIdOrName(draft, InventoryItem::id, InventoryItem::name)

    private fun resolveGroceryLinkTarget(draft: LinkActionDraft): GroceryItem? =
        readGroceries().resolveByIdOrName(draft, GroceryItem::id, GroceryItem::name)

    private fun resolveRecipeLinkTarget(draft: LinkActionDraft): Recipe? =
        readRecipes().resolveByIdOrName(draft, Recipe::id, Recipe::title)

    private fun resolveMealLogLinkTarget(draft: LinkActionDraft): MealLog? =
        readMealLogs().resolveByIdOrName(draft, MealLog::id, MealLog::title)

    private fun resolveMealPlanLinkTarget(draft: LinkActionDraft): MealPlan? =
        readMealPlans().resolveByIdOrName(draft, MealPlan::id, MealPlan::title)

    private fun resolvePlanEntryLinkTarget(draft: LinkActionDraft): MealPlanEntry? =
        readMealPlanEntries().resolveByIdOrName(draft, MealPlanEntry::id, MealPlanEntry::title)

    private fun resolveEventLinkTarget(draft: LinkActionDraft): FoodEvent? =
        draft.targetId()?.let { id -> readEvents().firstOrNull { it.id == id } }
            ?: draft.displayName
                .takeIf { it.isNotBlank() }
                ?.let { name -> readEvents().filter { it.note.equals(name, ignoreCase = true) }.singleOrNull() }

    private fun <T> List<T>.resolveByIdOrName(
        draft: LinkActionDraft,
        idOf: (T) -> Long,
        nameOf: (T) -> String,
    ): T? {
        draft.targetId()?.let { id -> return firstOrNull { idOf(it) == id } }
        val name = draft.displayName.ifBlank { draft.firstField("target", "name", "title", "item") }
        if (name.isBlank()) return null
        return filter { nameOf(it).equals(name, ignoreCase = true) }.singleOrNull()
    }

    private fun LinkActionDraft.toFoodCandidate(defaultName: String): FoodCandidate {
        val name = firstField("name", "item", "title", "text")
            .ifBlank { displayName }
            .ifBlank { defaultName }
        return FoodCandidate(
            name = name,
            quantity = firstField("quantity", "amount"),
            zone = storageZone(classifyStorageZone(name), name),
            category = firstField("category").ifBlank { categorizeFood(name) },
            servingText = firstField("serving_text"),
            calories = firstInt("calories"),
            proteinGrams = firstDouble("protein_g"),
            carbsGrams = firstDouble("carbs_g"),
            fatGrams = firstDouble("fat_g"),
            nutritionSource = firstField("nutrition_source"),
            notes = firstField("notes"),
            imageUri = firstField("image_uri").ifBlank { null },
            imageUrl = firstField("image_url"),
            expiresAtMillis = firstLong("expires_at_millis"),
        )
    }

    private fun LinkActionDraft.targetId(): Long? =
        targetRef.toLongOrNull()

    private fun LinkActionDraft.firstField(vararg keys: String): String =
        keys.firstNotNullOfOrNull { key -> fields[key]?.takeIf { it.isNotBlank() } }.orEmpty()

    private fun LinkActionDraft.firstInt(vararg keys: String): Int? =
        firstField(*keys).toIntOrNull()

    private fun LinkActionDraft.firstLong(vararg keys: String): Long? =
        firstField(*keys).toLongOrNull()

    private fun LinkActionDraft.firstDouble(vararg keys: String): Double? =
        firstField(*keys).toDoubleOrNull()

    private fun LinkActionDraft.storageZone(default: StorageZone, itemName: String): StorageZone =
        when (firstField("zone").lowercase()) {
            "fridge", "refrigerator" -> StorageZone.FRIDGE
            "freezer", "frozen" -> StorageZone.FREEZER
            "pantry", "shelf", "cupboard" -> StorageZone.PANTRY
            else -> default.takeUnless { itemName.isBlank() } ?: classifyStorageZone(itemName)
        }

    private fun LinkActionDraft.groceryStatus(default: GroceryStatus): GroceryStatus =
        when (firstField("status").lowercase()) {
            "bought", "done", "purchased" -> GroceryStatus.BOUGHT
            "needed", "need", "todo" -> GroceryStatus.NEEDED
            else -> default
        }

    private fun LinkActionDraft.mealSlot(default: MealSlot): MealSlot =
        when (firstField("meal_slot", "slot").lowercase()) {
            "breakfast" -> MealSlot.BREAKFAST
            "lunch" -> MealSlot.LUNCH
            "dinner" -> MealSlot.DINNER
            "snack" -> MealSlot.SNACK
            "flex", "flexible" -> MealSlot.FLEX
            else -> default
        }

    private fun LinkActionDraft.planEntryStatus(default: MealPlanEntryStatus): MealPlanEntryStatus =
        when {
            actionType.endsWith(".eaten") || firstField("status").equals("eaten", ignoreCase = true) -> MealPlanEntryStatus.EATEN
            actionType.endsWith(".skipped") || firstField("status").equals("skipped", ignoreCase = true) -> MealPlanEntryStatus.SKIPPED
            firstField("status").equals("draft", ignoreCase = true) -> MealPlanEntryStatus.DRAFT
            firstField("status").equals("planned", ignoreCase = true) -> MealPlanEntryStatus.PLANNED
            else -> default
        }

    private fun LinkActionDraft.foodEventType(default: FoodEventType): FoodEventType =
        when (firstField("event_type", "type").lowercase()) {
            "water", "hydration" -> FoodEventType.WATER
            "meal" -> FoodEventType.MEAL
            "cook", "cooking" -> FoodEventType.COOK
            "shop", "shopping" -> FoodEventType.SHOP
            "prep" -> FoodEventType.PREP
            "grocery_purchase", "purchase" -> FoodEventType.GROCERY_PURCHASE
            "pantry_use", "inventory_use" -> FoodEventType.PANTRY_USE
            "outside_food", "restaurant" -> FoodEventType.OUTSIDE_FOOD
            else -> default
        }

    private fun LinkActionDraft.foodEventConfidence(default: FoodEventConfidence): FoodEventConfidence =
        when (firstField("confidence").lowercase()) {
            "exact" -> FoodEventConfidence.EXACT
            "estimated", "estimate" -> FoodEventConfidence.ESTIMATED
            "ai_estimated", "ai estimated" -> FoodEventConfidence.AI_ESTIMATED
            else -> default
        }

    private fun LinkActionDraft.isCreateAction(): Boolean =
        WonderFoodCommandContract.actionSpec(actionType)?.operation == LinkActionOperation.CREATE

    private fun LinkActionDraft.isLogAction(): Boolean =
        WonderFoodCommandContract.actionSpec(actionType)?.operation == LinkActionOperation.LOG

    private fun LinkActionDraft.isBoughtAction(): Boolean =
        WonderFoodCommandContract.actionSpec(actionType)?.operation == LinkActionOperation.MARK_BOUGHT ||
            firstField("status").equals("bought", ignoreCase = true)

    private fun LinkActionDraft.unresolvedTargetMessage(label: String): Nothing {
        val target = targetRef.ifBlank { displayName }.ifBlank { firstField("name", "title", "item") }
        val message = if (target.isBlank()) {
            "Could not resolve a unique $label. No changes were applied."
        } else {
            "Could not resolve a unique $label for '$target'. No changes were applied."
        }
        throw FoodDraftApplyException(message)
    }

    @Synchronized
    fun rejectDraft(draft: FoodDraft, sourceMessageId: Long?): String {
        return rejectDraft(draft, sourceMessageId, draft.draftSource.name.lowercase())
    }

    @Synchronized
    override fun rejectDraft(draft: FoodDraft, sourceMessageId: Long?, writeSource: String): String {
        val source = writeSource.ifBlank { draft.draftSource.name.lowercase() }
        val summary = "Rejected ${draft.title.lowercase()}."
        recordChatAction(draft, ChatActionStatus.REJECTED, sourceMessageId, summary)
        recordCommandEvent(draft, status = "REJECTED", sourceMessageId = sourceMessageId, summary = summary, sourceOverride = source)
        return summary
    }

    @Synchronized
    override fun recordMutationCommand(command: FoodMutationCommand, status: String, summary: String) {
        val safeStatus = when (status) {
            "APPLIED", "REJECTED", "FAILED", "UNDONE" -> status
            else -> "FAILED"
        }
        val now = now()
        val payload = JSONObject().apply {
            put("type", command.type.commandType)
            put("label", command.label)
            put("origin", command.origin.writeSource)
            put(
                "fields",
                JSONObject().also { fields ->
                    command.payload.forEach { (key, value) ->
                        if (value == null) fields.put(key, JSONObject.NULL) else fields.put(key, value)
                    }
                },
            )
        }
        val payloadText = payload.toString()
        writableDatabase.insert(
            "command_events",
            null,
            ContentValues().apply {
                put("idempotency_key", "mutation:${command.sourceMessageId ?: "local"}:${command.type.commandType}:${payloadText.hashCode()}")
                put("source", command.origin.writeSource)
                put("command_type", command.type.commandType)
                put("payload_json", payloadText)
                put("status", safeStatus)
                put("confidence", 1.0)
                if (command.sourceMessageId == null) putNull("source_message_id") else put("source_message_id", command.sourceMessageId)
                put("result_summary", summary)
                putNull("undo_payload_json")
                put("created_at", now)
                if (safeStatus == "APPLIED") put("applied_at", now) else putNull("applied_at")
            },
        )
    }

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
        archiveRow("inventory_items", id)
    }

    @Synchronized
    fun deleteGrocery(id: Long) {
        archiveRow("grocery_items", id)
    }

    @Synchronized
    fun restoreInventory(id: Long) = restoreRow("inventory_items", id)

    @Synchronized
    fun restoreGrocery(id: Long) = restoreRow("grocery_items", id)

    @Synchronized
    fun updateInventory(
        id: Long,
        name: String,
        quantity: String,
        zone: StorageZone,
        category: String,
        servingText: String,
        calories: Int?,
        proteinGrams: Double?,
        carbsGrams: Double?,
        fatGrams: Double?,
        nutritionSource: String,
        notes: String,
        imageUri: String?,
        imageUrl: String,
        expiresAtMillis: Long?,
    ) {
        val updatedAt = now()
        writableDatabase.update(
            "inventory_items",
            ContentValues().apply {
                put("name", name)
                put("quantity", quantity)
                put("zone", zone.name)
                put("category", category)
                put("serving_text", servingText)
                put("calories", calories)
                put("protein_g", proteinGrams)
                put("carbs_g", carbsGrams)
                put("fat_g", fatGrams)
                put("nutrition_source", nutritionSource)
                put("notes", notes)
                put("image_uri", imageUri ?: foodEmojiForName(name))
                put("image_url", imageUrl)
                put("expires_at", expiresAtMillis)
                put("updated_at", updatedAt)
            },
            "id = ?",
            arrayOf(id.toString()),
        )
        insertInventoryTransaction(
            inventoryItemId = id,
            itemName = name,
            quantityText = quantity,
            zone = zone,
            action = InventoryAction.UPDATED,
            reason = "Edited kitchen page",
            relatedRecipeId = null,
            relatedMealLogId = null,
            source = "manual_edit",
        )
        syncInventoryStructured(
            inventoryItemId = id,
            candidate = FoodCandidate(
                name = name,
                quantity = quantity,
                zone = zone,
                category = category,
                servingText = servingText,
                calories = calories,
                proteinGrams = proteinGrams,
                carbsGrams = carbsGrams,
                fatGrams = fatGrams,
                nutritionSource = nutritionSource,
                notes = notes,
                imageUri = imageUri,
                imageUrl = imageUrl,
                expiresAtMillis = expiresAtMillis,
            ),
            sourceMessageId = null,
            source = "manual_edit",
            now = updatedAt,
            createBatch = false,
        )
    }

    @Synchronized
    fun updateGrocery(
        id: Long,
        name: String,
        quantity: String,
        status: GroceryStatus,
        category: String,
        servingText: String,
        calories: Int?,
        proteinGrams: Double?,
        carbsGrams: Double?,
        fatGrams: Double?,
        nutritionSource: String,
        source: String,
        imageUri: String?,
        imageUrl: String,
    ) {
        val previousStatus = readGroceries().firstOrNull { it.id == id }?.status
        val shouldReceive = previousStatus == GroceryStatus.NEEDED && status == GroceryStatus.BOUGHT
        val updatedAt = now()
        writableDatabase.update(
            "grocery_items",
            ContentValues().apply {
                put("name", name)
                put("quantity", quantity)
                put("status", if (shouldReceive) GroceryStatus.NEEDED.name else status.name)
                put("category", category)
                put("serving_text", servingText)
                put("calories", calories)
                put("protein_g", proteinGrams)
                put("carbs_g", carbsGrams)
                put("fat_g", fatGrams)
                put("nutrition_source", nutritionSource)
                put("source", source)
                put("image_uri", imageUri ?: foodEmojiForName(name))
                put("image_url", imageUrl)
                put("updated_at", updatedAt)
            },
            "id = ?",
            arrayOf(id.toString()),
        )
        syncGroceryStructured(
            groceryItemId = id,
            candidate = FoodCandidate(
                name = name,
                quantity = quantity,
                zone = classifyStorageZone(name),
                category = category,
                servingText = servingText,
                calories = calories,
                proteinGrams = proteinGrams,
                carbsGrams = carbsGrams,
                fatGrams = fatGrams,
                nutritionSource = nutritionSource,
                imageUri = imageUri,
                imageUrl = imageUrl,
            ),
            now = updatedAt,
        )
        if (shouldReceive) markGroceryBought(id)
    }

    @Synchronized
    fun deleteRecipe(id: Long) {
        archiveRow("recipes", id)
    }

    @Synchronized
    fun restoreRecipe(id: Long) = restoreRow("recipes", id)

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
        imageUrl: String,
    ) {
        val updatedAt = now()
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
                put("image_url", imageUrl)
                put("updated_at", updatedAt)
            },
            "id = ?",
            arrayOf(id.toString()),
        )
        syncRecipeStructured(
            db = writableDatabase,
            recipeId = id,
            title = title,
            ingredientsText = ingredients,
            stepsText = steps,
            tags = tags,
            now = updatedAt,
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
    fun updateMealPlan(
        id: Long,
        title: String,
        daysText: String,
        groceryHint: String,
        startDateEpochDay: Long?,
    ) {
        val now = now()
        val startDay = startDateEpochDay ?: todayEpochDay()
        writableDatabase.update(
            "meal_plans",
            ContentValues().apply {
                put("title", title)
                put("days_text", daysText)
                put("grocery_hint", groceryHint)
                put("start_date_epoch_day", startDay)
                put("updated_at", now)
            },
            "id = ?",
            arrayOf(id.toString()),
        )
        writableDatabase.delete("meal_plan_entries", "plan_id = ?", arrayOf(id.toString()))
        daysText.toMealPlanEntries().forEach { entry ->
            val entryId = writableDatabase.insert(
                "meal_plan_entries",
                null,
                ContentValues().apply {
                    put("plan_id", id)
                    put("date_epoch_day", startDay + entry.dayOffset)
                    put("slot", entry.slot.name)
                    put("title", entry.title)
                    put("calorie_target", entry.calorieTarget)
                    put("status", MealPlanEntryStatus.PLANNED.name)
                    put("notes", "Regenerated from meal plan text")
                    put("source", "manual_edit")
                    put("image_uri", foodEmojiForName(entry.title))
                    put("image_url", "")
                    putNull("recipe_id")
                    put("created_at", now)
                    put("updated_at", now)
                },
            )
            if (entryId != -1L) {
                syncMealPlanEntryStructured(
                    db = writableDatabase,
                    entryId = entryId,
                    title = entry.title,
                    recipeId = null,
                    now = now,
                )
            }
        }
    }

    @Synchronized
    fun addMealPlanEntry(
        dateEpochDay: Long,
        slot: MealSlot,
        title: String,
        calorieTarget: Int?,
    ): Long {
        val now = now()
        val planId = writableDatabase.rawQuery(
            "SELECT id FROM meal_plans ORDER BY updated_at DESC, id DESC LIMIT 1",
            emptyArray(),
        ).use { cursor ->
            if (cursor.moveToFirst()) {
                cursor.long("id")
            } else {
                writableDatabase.insert(
                    "meal_plans",
                    null,
                    ContentValues().apply {
                        put("title", "Calendar edits")
                        put("days_text", "")
                        put("grocery_hint", "")
                        put("status", MealPlanStatus.ACCEPTED.name)
                        put("start_date_epoch_day", dateEpochDay)
                        putNull("source_message_id")
                        put("created_at", now)
                        put("updated_at", now)
                    },
                )
            }
        }
        val entryId = writableDatabase.insert(
            "meal_plan_entries",
            null,
            ContentValues().apply {
                put("plan_id", planId)
                put("date_epoch_day", dateEpochDay)
                put("slot", slot.name)
                put("title", title)
                put("calorie_target", calorieTarget)
                put("status", MealPlanEntryStatus.PLANNED.name)
                put("notes", "Added from calendar")
                put("source", "manual_calendar")
                put("image_uri", foodEmojiForName(title))
                put("image_url", "")
                putNull("recipe_id")
                put("created_at", now)
                put("updated_at", now)
            },
        )
        if (entryId != -1L) {
            syncMealPlanEntryStructured(
                db = writableDatabase,
                entryId = entryId,
                title = title,
                recipeId = null,
                now = now,
            )
        }
        touchMealPlan(planId, now)
        return entryId
    }

    @Synchronized
    fun updateMealPlanEntry(
        id: Long,
        dateEpochDay: Long,
        slot: MealSlot,
        title: String,
        calorieTarget: Int?,
        status: MealPlanEntryStatus,
    ) {
        val entry = readMealPlanEntries().firstOrNull { it.id == id } ?: return
        val now = now()
        writableDatabase.update(
            "meal_plan_entries",
            ContentValues().apply {
                put("date_epoch_day", dateEpochDay)
                put("slot", slot.name)
                put("title", title)
                put("calorie_target", calorieTarget)
                put("status", status.name)
                put("notes", entry.notes)
                put("source", entry.source.ifBlank { "manual_calendar" })
                put("image_uri", entry.imageUri ?: foodEmojiForName(title))
                put("image_url", entry.imageUrl)
                if (entry.recipeId == null) putNull("recipe_id") else put("recipe_id", entry.recipeId)
                put("updated_at", now)
            },
            "id = ?",
            arrayOf(id.toString()),
        )
        syncMealPlanEntryStructured(
            db = writableDatabase,
            entryId = id,
            title = title,
            recipeId = entry.recipeId,
            now = now,
        )
        val syncedEntry = readMealPlanEntries().firstOrNull { it.id == id }
            ?: entry.copy(
                dateEpochDay = dateEpochDay,
                slot = slot,
                title = title,
                calorieTarget = calorieTarget,
                status = status,
            )
        syncMealLogForPlanEntry(entry = syncedEntry)
        touchMealPlan(entry.planId, now)
    }

    @Synchronized
    fun deleteMealPlanEntry(id: Long) {
        val entry = readMealPlanEntries().firstOrNull { it.id == id }
        archiveRow("meal_plan_entries", id)
        archiveGeneratedPlanMeal(id)
        if (entry != null) touchMealPlan(entry.planId, now())
    }

    @Synchronized
    fun restoreMealPlanEntry(id: Long) {
        restoreRow("meal_plan_entries", id)
        readMealPlanEntries().firstOrNull { it.id == id }?.let(::syncMealLogForPlanEntry)
    }

    @Synchronized
    fun deleteMealPlanEntries(ids: Set<Long>) {
        if (ids.isEmpty()) return
        val entries = readMealPlanEntries().filter { it.id in ids }
        entries.forEach { entry ->
            archiveRow("meal_plan_entries", entry.id)
            archiveGeneratedPlanMeal(entry.id)
        }
        entries.map { it.planId }.distinct().forEach { planId -> touchMealPlan(planId, now()) }
    }

    @Synchronized
    fun restoreMealPlanEntries(ids: Set<Long>) {
        if (ids.isEmpty()) return
        ids.forEach { id -> restoreRow("meal_plan_entries", id) }
        readMealPlanEntries()
            .filter { it.id in ids }
            .onEach(::syncMealLogForPlanEntry)
            .map { it.planId }
            .distinct()
            .forEach { planId -> touchMealPlan(planId, now()) }
    }

    @Synchronized
    fun deleteAllMealPlans(): Pair<Int, Int> {
        val planCount = readMealPlans().size
        val entryCount = readMealPlanEntries().size
        writableDatabase.delete("meal_plan_entries", null, null)
        writableDatabase.delete("meal_plans", null, null)
        return planCount to entryCount
    }

    private fun touchMealPlan(planId: Long, updatedAt: Long = now()) {
        writableDatabase.update(
            "meal_plans",
            ContentValues().apply {
                put("updated_at", updatedAt)
            },
            "id = ?",
            arrayOf(planId.toString()),
        )
    }

    @Synchronized
    fun deleteMealLog(id: Long) {
        archiveRow("meal_logs", id)
    }

    @Synchronized
    fun restoreMealLog(id: Long) = restoreRow("meal_logs", id)

    @Synchronized
    fun updateMealLog(
        id: Long,
        title: String,
        calories: Int?,
        proteinGrams: Double?,
        carbsGrams: Double?,
        fatGrams: Double?,
        mealSlot: MealSlot,
        usedItemsText: String,
        loggedDateEpochDay: Long,
        source: String,
    ) {
        val updatedAt = now()
        writableDatabase.update(
            "meal_logs",
            ContentValues().apply {
                put("title", title)
                put("calories", calories)
                put("protein_g", proteinGrams)
                put("carbs_g", carbsGrams)
                put("fat_g", fatGrams)
                put("meal_slot", mealSlot.name)
                put("used_items_text", usedItemsText)
                put("logged_date_epoch_day", loggedDateEpochDay)
                put("source", source)
                put("updated_at", updatedAt)
            },
            "id = ?",
            arrayOf(id.toString()),
        )
        writableDatabase.update(
            "food_events",
            ContentValues().apply {
                if (calories == null) putNull("amount") else put("amount", calories.toDouble())
                put("note", title)
            },
            "meal_log_id = ? AND type = ?",
            arrayOf(id.toString(), FoodEventType.MEAL.name),
        )
        val recipeId = writableDatabase.rawQuery(
            "SELECT recipe_id FROM meal_logs WHERE id = ? LIMIT 1",
            arrayOf(id.toString()),
        ).use { cursor -> if (cursor.moveToFirst()) cursor.nullableLong("recipe_id") else null }
        syncMealLogStructured(
            db = writableDatabase,
            mealLogId = id,
            title = title,
            usedItemsText = usedItemsText,
            recipeId = recipeId,
            calories = calories,
            proteinGrams = proteinGrams,
            carbsGrams = carbsGrams,
            fatGrams = fatGrams,
            now = updatedAt,
        )
    }

    @Synchronized
    fun markGroceryBought(id: Long): String {
        val item = readGroceries().firstOrNull { it.id == id } ?: return "Grocery item not found."
        if (item.status == GroceryStatus.BOUGHT) return "${item.name} is already marked bought."
        val now = now()
        writableDatabase.beginTransaction()
        try {
            writableDatabase.update(
                "grocery_items",
                ContentValues().apply {
                    put("status", GroceryStatus.BOUGHT.name)
                    put("updated_at", now)
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
                    servingText = item.servingText,
                    calories = item.calories,
                    proteinGrams = item.proteinGrams,
                    carbsGrams = item.carbsGrams,
                    fatGrams = item.fatGrams,
                    nutritionSource = item.nutritionSource,
                    notes = "Moved from shopping; storage location inferred from item name.",
                    imageUri = item.imageUri,
                    imageUrl = item.imageUrl,
                    zoneSource = "app_storage_inference",
                    warnings = listOf("Storage location was inferred when the shopping item was marked bought."),
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
            writableDatabase.setTransactionSuccessful()
        } finally {
            writableDatabase.endTransaction()
        }
        return "Moved ${item.name} into inventory and recorded shopping."
    }

    @Synchronized
    fun undoGroceryBought(id: Long): String {
        val item = readGroceries().firstOrNull { it.id == id } ?: return "Grocery item not found."
        if (item.status != GroceryStatus.BOUGHT) return "${item.name} is already on the shopping list."
        writableDatabase.beginTransaction()
        try {
            writableDatabase.update(
                "grocery_items",
                ContentValues().apply {
                    put("status", GroceryStatus.NEEDED.name)
                    put("updated_at", now())
                },
                "id = ?",
                arrayOf(id.toString()),
            )
            latestGroceryPurchaseTransaction(item.name)?.let { transaction ->
                if (transaction.inventoryItemId != null && inventoryWasCreatedByTransaction(transaction.inventoryItemId, transaction.createdAtMillis)) {
                    archiveRow("inventory_items", transaction.inventoryItemId)
                }
                writableDatabase.delete("inventory_transactions", "id = ?", arrayOf(transaction.id.toString()))
            }
            writableDatabase.delete(
                "food_events",
                """
                id IN (
                    SELECT id FROM food_events
                    WHERE type = ? AND note = ?
                    ORDER BY id DESC
                    LIMIT 1
                )
                """.trimIndent(),
                arrayOf(FoodEventType.GROCERY_PURCHASE.name, "Bought ${item.name}"),
            )
            writableDatabase.setTransactionSuccessful()
        } finally {
            writableDatabase.endTransaction()
        }
        return "Moved ${item.name} back to shopping."
    }

    @Synchronized
    fun addInventoryManual(name: String, quantity: String, zone: StorageZone): Long =
        addInventory(
            candidate = FoodCandidate(name = name, quantity = quantity, zone = zone),
            sourceMessageId = null,
            source = "manual",
        )

    @Synchronized
    fun addGroceryManual(name: String, quantity: String) {
        addGrocery(
            candidate = FoodCandidate(name = name, quantity = quantity, zone = classifyStorageZone(name)),
            sourceMessageId = null,
            source = "manual",
        )
    }

    @Synchronized
    fun addRecipeManual(title: String, ingredients: String, steps: String) {
        addRecipe(
            draft = RecipeDraft(titleText = title, ingredientsText = ingredients, stepsText = steps, tags = "manual"),
            sourceMessageId = null,
            source = "manual",
        )
    }

    @Synchronized
    fun addMealLogManual(
        title: String,
        slot: MealSlot,
        dateEpochDay: Long,
        calories: Int? = null,
    ): Long =
        addMealLog(
            draft = MealLogDraft(
                titleText = title,
                calories = calories,
                mealSlot = slot,
                loggedDateEpochDay = dateEpochDay,
                source = "manual",
            ),
            sourceMessageId = null,
        )

    @Synchronized
    fun cookRecipe(recipeId: Long): String {
        val recipe = readRecipes().firstOrNull { it.id == recipeId } ?: return "Recipe not found."
        val matched = matchedInventoryForRecipe(recipe)
        val usedNames = matched.joinToString(", ") { it.name }
        val mealId = addMealLog(
            MealLogDraft(
                titleText = recipe.title,
                calories = null,
                proteinGrams = null,
                carbsGrams = null,
                fatGrams = null,
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
            "Logged ${recipe.title}. Recorded ${matched.size} ingredient use${matched.size.plural}; review quantities before pantry amounts are reduced."
        }
    }

    @Synchronized
    fun logWater(ml: Int, source: String = "assistant"): String {
        return logWaterEvent(ml, source).second
    }

    @Synchronized
    fun logWaterEvent(ml: Int, source: String = "assistant"): Pair<Long, String> {
        val safeMl = ml.coerceIn(1, 5000)
        val eventId = insertFoodEvent(
            type = FoodEventType.WATER,
            amount = safeMl.toDouble(),
            unit = "ml",
            source = source,
            confidence = FoodEventConfidence.EXACT,
            note = "Water",
        )
        return eventId to "Logged ${safeMl} ml water."
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
    fun deleteFoodEvent(id: Long): Boolean =
        writableDatabase.delete("food_events", "id = ?", arrayOf(id.toString())) > 0

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

    private fun syncMealLogForPlanEntry(entry: MealPlanEntry) {
        val source = planEntryMealSource(entry.id)
        if (entry.status != MealPlanEntryStatus.EATEN) {
            archiveGeneratedPlanMeal(entry.id)
            return
        }
        val existingId = readableDatabase.rawQuery(
            "SELECT id FROM meal_logs WHERE source = ? LIMIT 1",
            arrayOf(source),
        ).use { cursor -> if (cursor.moveToFirst()) cursor.long("id") else null }
        if (existingId == null) {
            addMealLog(
                draft = MealLogDraft(
                    titleText = entry.title,
                    calories = entry.calorieTarget,
                    mealSlot = entry.slot,
                    loggedDateEpochDay = entry.dateEpochDay,
                    source = source,
                ),
                sourceMessageId = null,
                relatedRecipeId = entry.recipeId,
            )
        } else {
            restoreRow("meal_logs", existingId)
            updateMealLog(
                id = existingId,
                title = entry.title,
                calories = entry.calorieTarget,
                proteinGrams = null,
                carbsGrams = null,
                fatGrams = null,
                mealSlot = entry.slot,
                usedItemsText = "",
                loggedDateEpochDay = entry.dateEpochDay,
                source = source,
            )
        }
    }

    private fun archiveGeneratedPlanMeal(entryId: Long) {
        writableDatabase.update(
            "meal_logs",
            ContentValues().apply { put("archived", 1) },
            "source = ?",
            arrayOf(planEntryMealSource(entryId)),
        )
    }

    private fun planEntryMealSource(entryId: Long): String = "plan_entry:$entryId"

    private fun archiveRow(table: String, id: Long) {
        writableDatabase.update(
            table,
            ContentValues().apply { put("archived", 1) },
            "id = ?",
            arrayOf(id.toString()),
        )
    }

    private fun restoreRow(table: String, id: Long) {
        writableDatabase.update(
            table,
            ContentValues().apply { put("archived", 0) },
            "id = ?",
            arrayOf(id.toString()),
        )
    }

    private fun addInventory(
        candidate: FoodCandidate,
        sourceMessageId: Long?,
        source: String = "chat",
        transactionAction: InventoryAction = InventoryAction.ADDED,
        transactionReason: String = "Saved from $source",
        purchase: InventoryBatchPurchase? = null,
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
        if (itemId != -1L) {
            syncInventoryStructured(
                inventoryItemId = itemId,
                candidate = candidate,
                sourceMessageId = sourceMessageId,
                source = source,
                now = now,
                createBatch = existingId == null || transactionAction == InventoryAction.BOUGHT,
                purchase = purchase,
            )
        }
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
        return itemId
    }

    private data class InventoryBatchPurchase(
        val purchasedAtMillis: Long?,
        val priceCents: Long?,
        val currencyCode: String,
        val storeName: String,
    )

    private data class GroceryPurchaseTransaction(
        val id: Long,
        val inventoryItemId: Long?,
        val createdAtMillis: Long,
    )

    private fun latestGroceryPurchaseTransaction(itemName: String): GroceryPurchaseTransaction? =
        readableDatabase.rawQuery(
            """
            SELECT id, inventory_item_id, created_at
            FROM inventory_transactions
            WHERE item_name = ? AND action = ? AND source = ? AND reason = ?
            ORDER BY id DESC
            LIMIT 1
            """.trimIndent(),
            arrayOf(itemName, InventoryAction.BOUGHT.name, "grocery", "Marked bought from shopping list"),
        ).use { cursor ->
            if (cursor.moveToFirst()) {
                GroceryPurchaseTransaction(
                    id = cursor.long("id"),
                    inventoryItemId = cursor.nullableLong("inventory_item_id"),
                    createdAtMillis = cursor.long("created_at"),
                )
            } else {
                null
            }
        }

    private fun inventoryWasCreatedByTransaction(inventoryItemId: Long, transactionCreatedAtMillis: Long): Boolean =
        readableDatabase.rawQuery(
            "SELECT created_at FROM inventory_items WHERE id = ?",
            arrayOf(inventoryItemId.toString()),
        ).use { cursor ->
            if (!cursor.moveToFirst()) return@use false
            val itemCreatedAt = cursor.long("created_at")
            val delta = if (itemCreatedAt > transactionCreatedAtMillis) {
                itemCreatedAt - transactionCreatedAtMillis
            } else {
                transactionCreatedAtMillis - itemCreatedAt
            }
            delta <= 2_000L
        }

    private fun addGrocery(candidate: FoodCandidate, sourceMessageId: Long?, source: String = "chat") {
        val now = now()
        val values = groceryValues(candidate, sourceMessageId, source, now, includeCreatedAt = true)
        val existingId = findGroceryId(candidate.name)
        val itemId = if (existingId == null) {
            writableDatabase.insert("grocery_items", null, values)
        } else {
            values.remove("created_at")
            writableDatabase.update("grocery_items", values, "id = ?", arrayOf(existingId.toString()))
            existingId
        }
        if (itemId != -1L) {
            syncGroceryStructured(itemId, candidate, now)
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
            put("image_uri", draft.imageUri ?: foodEmojiForName(draft.titleText))
            put("image_url", draft.imageUrl)
            put("source_message_id", sourceMessageId)
            put("updated_at", now)
        }
        val recipeId = if (existingId == null) {
            values.put("created_at", now)
            writableDatabase.insert("recipes", null, values)
        } else {
            writableDatabase.update("recipes", values, "id = ?", arrayOf(existingId.toString()))
            existingId
        }
        if (recipeId != -1L) {
            syncRecipeStructured(
                db = writableDatabase,
                recipeId = recipeId,
                title = draft.titleText,
                ingredientsText = draft.ingredientsText,
                stepsText = draft.stepsText,
                tags = listOf(draft.tags, source).filter { it.isNotBlank() }.joinToString(", "),
                now = now,
            )
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
                if (relatedRecipeId == null) putNull("recipe_id") else put("recipe_id", relatedRecipeId)
                put("source_message_id", sourceMessageId)
                put("created_at", now)
                put("updated_at", now)
            },
        )
        if (mealId != -1L) {
            syncMealLogStructured(
                db = writableDatabase,
                mealLogId = mealId,
                title = draft.titleText,
                usedItemsText = draft.usedItemsText,
                recipeId = relatedRecipeId,
                calories = draft.calories,
                proteinGrams = draft.proteinGrams,
                carbsGrams = draft.carbsGrams,
                fatGrams = draft.fatGrams,
                now = now,
            )
        }
        recordUsedItems(
            usedItemsText = draft.usedItemsText,
            mealId = mealId,
            recipeId = relatedRecipeId,
            reason = "Logged ${draft.titleText}",
            source = draft.source,
        )
        insertFoodEvent(
            type = FoodEventType.MEAL,
            amount = draft.calories?.toDouble(),
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
            val entryId = writableDatabase.insert(
                "meal_plan_entries",
                null,
                ContentValues().apply {
                    put("plan_id", planId)
                    put("date_epoch_day", startDay + entry.dayOffset)
                    put("slot", entry.slot.name)
                    put("title", entry.title)
                    put("calorie_target", entry.calorieTarget)
                    put("status", MealPlanEntryStatus.PLANNED.name)
                    put("notes", "Accepted from $source")
                    put("source", source)
                    put("image_uri", foodEmojiForName(entry.title))
                    put("image_url", "")
                    putNull("recipe_id")
                    put("created_at", now)
                    put("updated_at", now)
                },
            )
            if (entryId != -1L) {
                syncMealPlanEntryStructured(
                    db = writableDatabase,
                    entryId = entryId,
                    title = entry.title,
                    recipeId = null,
                    now = now,
                )
            }
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
            val nutrition = candidate
            put("serving_text", nutrition.servingText)
            put("calories", nutrition.calories)
            put("protein_g", nutrition.proteinGrams)
            put("carbs_g", nutrition.carbsGrams)
            put("fat_g", nutrition.fatGrams)
            put("nutrition_source", nutrition.nutritionSource)
            put("notes", candidate.notes)
            put("image_uri", candidate.imageUri ?: foodEmojiForName(candidate.name))
            put("image_url", candidate.imageUrl)
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
            val nutrition = candidate
            put("serving_text", nutrition.servingText)
            put("calories", nutrition.calories)
            put("protein_g", nutrition.proteinGrams)
            put("carbs_g", nutrition.carbsGrams)
            put("fat_g", nutrition.fatGrams)
            put("nutrition_source", nutrition.nutritionSource)
            put("source", source)
            put("image_uri", candidate.imageUri ?: foodEmojiForName(candidate.name))
            put("source_message_id", sourceMessageId)
            put("image_url", candidate.imageUrl)
            if (includeCreatedAt) put("created_at", now)
            put("updated_at", now)
        }

    private fun findInventoryId(name: String, zone: StorageZone): Long? =
        readableDatabase.rawQuery(
            "SELECT id FROM inventory_items WHERE lower(name) = lower(?) AND zone = ? AND archived = 0 LIMIT 1",
            arrayOf(name, zone.name),
        ).use { cursor -> if (cursor.moveToFirst()) cursor.long("id") else null }

    private fun findGroceryId(name: String): Long? =
        readableDatabase.rawQuery(
            "SELECT id FROM grocery_items WHERE lower(name) = lower(?) AND status = ? AND archived = 0 LIMIT 1",
            arrayOf(name, GroceryStatus.NEEDED.name),
        ).use { cursor -> if (cursor.moveToFirst()) cursor.long("id") else null }

    private fun findRecipeId(title: String): Long? =
        readableDatabase.rawQuery(
            "SELECT id FROM recipes WHERE lower(title) = lower(?) AND archived = 0 LIMIT 1",
            arrayOf(title),
        ).use { cursor -> if (cursor.moveToFirst()) cursor.long("id") else null }

    private fun readMessages(): List<ChatMessage> =
        readableDatabase.rawQuery(
            "SELECT id, role, body, created_at, chat_id FROM chat_messages ORDER BY id ASC",
            emptyArray(),
        ).useRows { cursor ->
            ChatMessage(
                id = cursor.long("id"),
                role = runCatching { ChatRole.valueOf(cursor.string("role")) }.getOrDefault(ChatRole.ASSISTANT),
                body = cursor.string("body"),
                createdAtMillis = cursor.long("created_at"),
                chatId = cursor.long("chat_id"),
            )
        }

    private fun currentChatId(): Long =
        readableDatabase.rawQuery(
            "SELECT COALESCE(MAX(chat_id), 1) FROM chat_messages",
            emptyArray(),
        ).use { cursor ->
            if (cursor.moveToFirst()) cursor.getLong(0).coerceAtLeast(1L) else 1L
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
            SELECT i.id, i.name, i.quantity, i.zone, i.category, i.serving_text, i.calories, i.protein_g, i.carbs_g, i.fat_g,
                   i.nutrition_source, i.notes, i.image_uri, i.image_url, i.expires_at, i.source, i.created_at, i.updated_at,
                   (SELECT b.purchase_date_epoch_day FROM inventory_batches b WHERE b.inventory_item_id = i.id ORDER BY b.id DESC LIMIT 1) AS purchase_date_epoch_day,
                   (SELECT b.purchase_price_cents FROM inventory_batches b WHERE b.inventory_item_id = i.id ORDER BY b.id DESC LIMIT 1) AS purchase_price_cents,
                   COALESCE((SELECT b.currency_code FROM inventory_batches b WHERE b.inventory_item_id = i.id ORDER BY b.id DESC LIMIT 1), 'USD') AS currency_code,
                   COALESCE((SELECT b.store_name FROM inventory_batches b WHERE b.inventory_item_id = i.id ORDER BY b.id DESC LIMIT 1), '') AS store_name
            FROM inventory_items i WHERE i.archived = 0 ORDER BY i.id DESC
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
                imageUrl = cursor.string("image_url"),
                purchaseDateEpochDay = cursor.nullableLong("purchase_date_epoch_day"),
                purchasePriceCents = cursor.nullableLong("purchase_price_cents"),
                currencyCode = cursor.string("currency_code"),
                storeName = cursor.string("store_name"),
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
                   nutrition_source, source, image_uri, image_url, created_at, updated_at
            FROM grocery_items WHERE archived = 0 ORDER BY status ASC, id DESC
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
                imageUrl = cursor.string("image_url"),
            )
        }

    private fun readRecipes(): List<Recipe> =
        readableDatabase.rawQuery(
            """
            SELECT id, title, ingredients, steps, servings, prep_minutes, tags, rating, image_uri, image_url, created_at, updated_at
            FROM recipes WHERE archived = 0 ORDER BY id DESC
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
                imageUrl = cursor.string("image_url"),
            )
        }

    private fun readMealLogs(): List<MealLog> =
        readableDatabase.rawQuery(
            """
            SELECT id, title, calories, protein_g, carbs_g, fat_g, meal_slot, used_items_text,
                   logged_date_epoch_day, source, created_at, updated_at
            FROM meal_logs WHERE archived = 0 ORDER BY id DESC
            """.trimIndent(),
            emptyArray(),
        ).useRows { cursor ->
            val createdAt = cursor.long("created_at")
            val loggedDay = cursor.long("logged_date_epoch_day").takeIf { it > 0 } ?: createdAt.toEpochDay()
            MealLog(
                id = cursor.long("id"),
                title = cursor.string("title"),
                calories = cursor.nullableInt("calories"),
                proteinGrams = cursor.nullableDouble("protein_g"),
                carbsGrams = cursor.nullableDouble("carbs_g"),
                fatGrams = cursor.nullableDouble("fat_g"),
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
            SELECT id, plan_id, date_epoch_day, slot, title, calorie_target, status,
                notes, source, image_uri, image_url, recipe_id, created_at, updated_at
            FROM meal_plan_entries WHERE archived = 0 ORDER BY date_epoch_day ASC, id ASC
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
                notes = cursor.string("notes"),
                source = cursor.string("source"),
                imageUri = cursor.nullableString("image_uri"),
                imageUrl = cursor.string("image_url"),
                recipeId = cursor.nullableLong("recipe_id"),
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
            aiSkillOverride = values[PREF_AI_SKILL_OVERRIDE].orEmpty(),
        )
    }

    private fun createCoreTables(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS chat_messages (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                role TEXT NOT NULL,
                body TEXT NOT NULL,
                created_at INTEGER NOT NULL,
                chat_id INTEGER NOT NULL DEFAULT 1
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
                image_url TEXT NOT NULL DEFAULT '',
                expires_at INTEGER,
                source TEXT NOT NULL,
                source_message_id INTEGER,
                archived INTEGER NOT NULL DEFAULT 0,
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
                image_url TEXT NOT NULL DEFAULT '',
                source_message_id INTEGER,
                archived INTEGER NOT NULL DEFAULT 0,
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
                image_url TEXT NOT NULL DEFAULT '',
                source_message_id INTEGER,
                archived INTEGER NOT NULL DEFAULT 0,
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
                calories INTEGER,
                protein_g REAL,
                carbs_g REAL,
                fat_g REAL,
                meal_slot TEXT NOT NULL DEFAULT 'FLEX',
                used_items_text TEXT NOT NULL DEFAULT '',
                logged_date_epoch_day INTEGER NOT NULL DEFAULT 0,
                source TEXT NOT NULL,
                source_message_id INTEGER,
                archived INTEGER NOT NULL DEFAULT 0,
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
                notes TEXT NOT NULL DEFAULT '',
                source TEXT NOT NULL DEFAULT '',
                image_uri TEXT,
                image_url TEXT NOT NULL DEFAULT '',
                recipe_id INTEGER,
                archived INTEGER NOT NULL DEFAULT 0,
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

    private fun createV7Tables(db: SQLiteDatabase) {
        db.addColumn("inventory_items", "image_url TEXT NOT NULL DEFAULT ''")
        db.addColumn("grocery_items", "image_url TEXT NOT NULL DEFAULT ''")
        db.addColumn("recipes", "image_url TEXT NOT NULL DEFAULT ''")
    }

    private fun createV8Tables(db: SQLiteDatabase) {
        db.addColumn("meal_plan_entries", "notes TEXT NOT NULL DEFAULT ''")
        db.addColumn("meal_plan_entries", "source TEXT NOT NULL DEFAULT ''")
        db.addColumn("meal_plan_entries", "image_uri TEXT")
        db.addColumn("meal_plan_entries", "image_url TEXT NOT NULL DEFAULT ''")
        db.addColumn("meal_plan_entries", "recipe_id INTEGER")
    }

    private fun createV9Tables(db: SQLiteDatabase) {
        db.addColumn("inventory_items", "archived INTEGER NOT NULL DEFAULT 0")
        db.addColumn("grocery_items", "archived INTEGER NOT NULL DEFAULT 0")
        db.addColumn("recipes", "archived INTEGER NOT NULL DEFAULT 0")
        db.addColumn("meal_logs", "archived INTEGER NOT NULL DEFAULT 0")
        db.addColumn("meal_plan_entries", "archived INTEGER NOT NULL DEFAULT 0")
    }

    private fun createV10Tables(db: SQLiteDatabase) {
        addV10CompatibilityColumns(db)
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS units (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                code TEXT NOT NULL UNIQUE,
                name TEXT NOT NULL,
                unit_type TEXT NOT NULL CHECK(unit_type IN ('MASS', 'VOLUME', 'COUNT', 'HOUSEHOLD', 'ENERGY')),
                conversion_to_base REAL,
                base_unit_code TEXT
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS storage_zones (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL UNIQUE,
                zone_type TEXT NOT NULL CHECK(zone_type IN ('PANTRY', 'FRIDGE', 'FREEZER', 'COUNTER', 'OTHER')),
                parent_zone_id INTEGER,
                sort_order INTEGER NOT NULL DEFAULT 0,
                FOREIGN KEY(parent_zone_id) REFERENCES storage_zones(id) ON DELETE SET NULL
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS food_categories (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL UNIQUE,
                parent_category_id INTEGER,
                sort_order INTEGER NOT NULL DEFAULT 0,
                FOREIGN KEY(parent_category_id) REFERENCES food_categories(id) ON DELETE SET NULL
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS foods (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                canonical_name TEXT NOT NULL,
                display_name TEXT NOT NULL,
                emoji TEXT NOT NULL DEFAULT '',
                category_id INTEGER,
                default_unit_id INTEGER,
                food_type TEXT NOT NULL DEFAULT 'INGREDIENT' CHECK(food_type IN ('INGREDIENT', 'RECIPE', 'PREPARED', 'HOUSEHOLD')),
                storage_default TEXT,
                shelf_life_days INTEGER,
                barcode TEXT,
                brand TEXT,
                variety TEXT,
                notes TEXT,
                image_uri TEXT,
                image_url TEXT,
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL,
                FOREIGN KEY(category_id) REFERENCES food_categories(id) ON DELETE SET NULL,
                FOREIGN KEY(default_unit_id) REFERENCES units(id) ON DELETE SET NULL,
                UNIQUE(canonical_name, brand, variety)
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS food_aliases (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                food_id INTEGER NOT NULL,
                alias TEXT NOT NULL COLLATE NOCASE,
                language TEXT NOT NULL DEFAULT 'en',
                alias_type TEXT NOT NULL DEFAULT 'COMMON' CHECK(alias_type IN ('COMMON', 'REGIONAL', 'BRAND', 'USER', 'AI')),
                created_at INTEGER NOT NULL,
                FOREIGN KEY(food_id) REFERENCES foods(id) ON DELETE CASCADE,
                UNIQUE(alias, language)
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS food_category_links (
                food_id INTEGER NOT NULL,
                category_id INTEGER NOT NULL,
                PRIMARY KEY(food_id, category_id),
                FOREIGN KEY(food_id) REFERENCES foods(id) ON DELETE CASCADE,
                FOREIGN KEY(category_id) REFERENCES food_categories(id) ON DELETE CASCADE
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS food_nutrition (
                food_id INTEGER PRIMARY KEY,
                serving_quantity REAL,
                serving_unit_id INTEGER,
                serving_text TEXT NOT NULL DEFAULT '',
                calories_kcal REAL,
                protein_g REAL,
                carbs_g REAL,
                fat_g REAL,
                fiber_g REAL,
                sodium_mg REAL,
                source TEXT,
                verified INTEGER NOT NULL DEFAULT 0,
                updated_at INTEGER NOT NULL,
                FOREIGN KEY(food_id) REFERENCES foods(id) ON DELETE CASCADE,
                FOREIGN KEY(serving_unit_id) REFERENCES units(id) ON DELETE SET NULL
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS inventory_batches (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                inventory_item_id INTEGER,
                food_id INTEGER NOT NULL,
                quantity_value REAL,
                unit_id INTEGER,
                quantity_text TEXT NOT NULL DEFAULT '',
                original_quantity_value REAL,
                storage_zone_id INTEGER,
                purchase_date_epoch_day INTEGER,
                opened_date_epoch_day INTEGER,
                expires_date_epoch_day INTEGER,
                best_before_date_epoch_day INTEGER,
                purchase_price_cents INTEGER,
                currency_code TEXT NOT NULL DEFAULT 'USD',
                store_name TEXT,
                package_state TEXT NOT NULL DEFAULT 'UNOPENED' CHECK(package_state IN ('UNOPENED', 'OPENED', 'PARTIAL', 'FROZEN', 'PREPARED', 'UNKNOWN')),
                status TEXT NOT NULL DEFAULT 'AVAILABLE' CHECK(status IN ('AVAILABLE', 'CONSUMED', 'EXPIRED', 'DISCARDED', 'DONATED', 'ARCHIVED')),
                source TEXT NOT NULL DEFAULT 'manual',
                source_message_id INTEGER,
                receipt_item_id INTEGER,
                notes TEXT,
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL,
                FOREIGN KEY(inventory_item_id) REFERENCES inventory_items(id) ON DELETE SET NULL,
                FOREIGN KEY(food_id) REFERENCES foods(id),
                FOREIGN KEY(unit_id) REFERENCES units(id) ON DELETE SET NULL,
                FOREIGN KEY(storage_zone_id) REFERENCES storage_zones(id) ON DELETE SET NULL,
                CHECK(quantity_value IS NULL OR quantity_value >= 0)
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS recipe_ingredients (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                recipe_id INTEGER NOT NULL,
                food_id INTEGER,
                ingredient_text TEXT NOT NULL,
                quantity_value REAL,
                unit_id INTEGER,
                preparation TEXT NOT NULL DEFAULT '',
                is_optional INTEGER NOT NULL DEFAULT 0,
                group_name TEXT NOT NULL DEFAULT '',
                sort_order INTEGER NOT NULL DEFAULT 0,
                substitute_group TEXT NOT NULL DEFAULT '',
                FOREIGN KEY(recipe_id) REFERENCES recipes(id) ON DELETE CASCADE,
                FOREIGN KEY(food_id) REFERENCES foods(id) ON DELETE SET NULL,
                FOREIGN KEY(unit_id) REFERENCES units(id) ON DELETE SET NULL
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS recipe_steps (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                recipe_id INTEGER NOT NULL,
                step_number INTEGER NOT NULL,
                instruction TEXT NOT NULL,
                duration_minutes INTEGER,
                temperature_value REAL,
                temperature_unit TEXT,
                timer_label TEXT,
                FOREIGN KEY(recipe_id) REFERENCES recipes(id) ON DELETE CASCADE,
                UNIQUE(recipe_id, step_number)
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS tags (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL UNIQUE
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS recipe_tags (
                recipe_id INTEGER NOT NULL,
                tag_id INTEGER NOT NULL,
                PRIMARY KEY(recipe_id, tag_id),
                FOREIGN KEY(recipe_id) REFERENCES recipes(id) ON DELETE CASCADE,
                FOREIGN KEY(tag_id) REFERENCES tags(id) ON DELETE CASCADE
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS meal_log_items (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                meal_log_id INTEGER NOT NULL,
                food_id INTEGER,
                recipe_id INTEGER,
                quantity_value REAL,
                unit_id INTEGER,
                serving_count REAL,
                calories_kcal REAL,
                protein_g REAL,
                carbs_g REAL,
                fat_g REAL,
                inventory_batch_id INTEGER,
                item_text TEXT NOT NULL DEFAULT '',
                FOREIGN KEY(meal_log_id) REFERENCES meal_logs(id) ON DELETE CASCADE,
                FOREIGN KEY(food_id) REFERENCES foods(id) ON DELETE SET NULL,
                FOREIGN KEY(recipe_id) REFERENCES recipes(id) ON DELETE SET NULL,
                FOREIGN KEY(unit_id) REFERENCES units(id) ON DELETE SET NULL,
                FOREIGN KEY(inventory_batch_id) REFERENCES inventory_batches(id) ON DELETE SET NULL,
                CHECK(food_id IS NOT NULL OR recipe_id IS NOT NULL OR item_text <> '')
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS meal_plan_entry_items (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                meal_plan_entry_id INTEGER NOT NULL,
                recipe_id INTEGER,
                food_id INTEGER,
                servings REAL,
                item_text TEXT NOT NULL DEFAULT '',
                FOREIGN KEY(meal_plan_entry_id) REFERENCES meal_plan_entries(id) ON DELETE CASCADE,
                FOREIGN KEY(recipe_id) REFERENCES recipes(id) ON DELETE SET NULL,
                FOREIGN KEY(food_id) REFERENCES foods(id) ON DELETE SET NULL
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS command_events (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                idempotency_key TEXT,
                source TEXT NOT NULL DEFAULT 'app',
                command_type TEXT NOT NULL,
                payload_json TEXT NOT NULL,
                status TEXT NOT NULL CHECK(status IN ('PROPOSED', 'APPLIED', 'REJECTED', 'FAILED', 'UNDONE')),
                confidence REAL,
                source_message_id INTEGER,
                result_summary TEXT NOT NULL DEFAULT '',
                undo_payload_json TEXT,
                created_at INTEGER NOT NULL,
                applied_at INTEGER,
                FOREIGN KEY(source_message_id) REFERENCES chat_messages(id) ON DELETE SET NULL
            )
            """.trimIndent(),
        )
        createV10Indexes(db)
        seedStructuredReferenceData(db)
    }

    private fun addV10CompatibilityColumns(db: SQLiteDatabase) {
        db.addColumn("inventory_items", "food_id INTEGER")
        db.addColumn("inventory_items", "quantity_value REAL")
        db.addColumn("inventory_items", "unit_id INTEGER")
        db.addColumn("inventory_items", "quantity_text TEXT NOT NULL DEFAULT ''")
        db.addColumn("inventory_items", "storage_zone_id INTEGER")
        db.addColumn("grocery_items", "food_id INTEGER")
        db.addColumn("grocery_items", "entered_name TEXT NOT NULL DEFAULT ''")
        db.addColumn("grocery_items", "quantity_value REAL")
        db.addColumn("grocery_items", "unit_id INTEGER")
        db.addColumn("grocery_items", "quantity_text TEXT NOT NULL DEFAULT ''")
        db.addColumn("recipes", "food_id INTEGER")
        db.addColumn("meal_logs", "recipe_id INTEGER")
        db.addColumn("inventory_transactions", "food_id INTEGER")
        db.addColumn("inventory_transactions", "quantity_delta REAL")
        db.addColumn("inventory_transactions", "unit_id INTEGER")
        db.addColumn("meal_plan_entries", "food_id INTEGER")
        db.addColumn("meal_plan_entries", "planned_servings REAL")
        db.addColumn("meal_plan_entries", "leftover_source_entry_id INTEGER")
        db.addColumn("meal_plan_entries", "eating_location TEXT")
    }

    private fun createV10Indexes(db: SQLiteDatabase) {
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_foods_name ON foods(canonical_name COLLATE NOCASE)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_food_aliases_alias ON food_aliases(alias COLLATE NOCASE)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_inventory_items_food ON inventory_items(food_id)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_inventory_transactions_food ON inventory_transactions(food_id)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_inventory_batches_food ON inventory_batches(food_id)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_inventory_batches_expiry ON inventory_batches(expires_date_epoch_day)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_inventory_batches_status ON inventory_batches(status)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_grocery_items_food_status ON grocery_items(food_id, status)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_recipe_ingredients_recipe ON recipe_ingredients(recipe_id)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_recipe_ingredients_food ON recipe_ingredients(food_id)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_meal_logs_date_v10 ON meal_logs(logged_date_epoch_day)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_meal_log_items_meal ON meal_log_items(meal_log_id)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_meal_plan_entries_plan_date_v10 ON meal_plan_entries(plan_id, date_epoch_day)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_meal_plan_entry_items_entry ON meal_plan_entry_items(meal_plan_entry_id)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_command_events_key ON command_events(idempotency_key)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_command_events_type_status ON command_events(command_type, status)")
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

    private fun migrateToV7(db: SQLiteDatabase) {
        createV7Tables(db)
    }

    private fun migrateToV8(db: SQLiteDatabase) {
        createV8Tables(db)
    }

    private fun migrateToV9(db: SQLiteDatabase) {
        createV9Tables(db)
        db.execSQL("ALTER TABLE meal_logs RENAME TO meal_logs_v8")
        db.execSQL(
            """
            CREATE TABLE meal_logs (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                title TEXT NOT NULL,
                calories INTEGER,
                protein_g REAL,
                carbs_g REAL,
                fat_g REAL,
                meal_slot TEXT NOT NULL DEFAULT 'FLEX',
                used_items_text TEXT NOT NULL DEFAULT '',
                logged_date_epoch_day INTEGER NOT NULL DEFAULT 0,
                source TEXT NOT NULL,
                source_message_id INTEGER,
                archived INTEGER NOT NULL DEFAULT 0,
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            INSERT INTO meal_logs (
                id, title, calories, protein_g, carbs_g, fat_g, meal_slot, used_items_text,
                logged_date_epoch_day, source, source_message_id, archived, created_at, updated_at
            )
            SELECT id, title, calories, protein_g, carbs_g, fat_g, meal_slot, used_items_text,
                   logged_date_epoch_day, source, source_message_id, archived, created_at, updated_at
            FROM meal_logs_v8
            """.trimIndent(),
        )
        db.execSQL("DROP TABLE meal_logs_v8")
    }

    private fun migrateToV10(db: SQLiteDatabase) {
        createV10Tables(db)
        backfillStructuredFoodData(db)
    }

    private fun createV11Tables(db: SQLiteDatabase) {
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_chat_messages_chat_id ON chat_messages(chat_id, id)")
    }

    private fun migrateToV11(db: SQLiteDatabase) {
        db.addColumn("chat_messages", "chat_id INTEGER NOT NULL DEFAULT 1")
        createV11Tables(db)
    }

    private data class ParsedQuantity(
        val value: Double?,
        val unitCode: String?,
        val text: String,
    )

    private fun seedStructuredReferenceData(db: SQLiteDatabase) {
        listOf(
            UnitSeed("g", "gram", "MASS", 1.0, "g"),
            UnitSeed("kg", "kilogram", "MASS", 1000.0, "g"),
            UnitSeed("oz", "ounce", "MASS", 28.3495, "g"),
            UnitSeed("lb", "pound", "MASS", 453.592, "g"),
            UnitSeed("ml", "milliliter", "VOLUME", 1.0, "ml"),
            UnitSeed("l", "liter", "VOLUME", 1000.0, "ml"),
            UnitSeed("tsp", "teaspoon", "VOLUME", 4.92892, "ml"),
            UnitSeed("tbsp", "tablespoon", "VOLUME", 14.7868, "ml"),
            UnitSeed("cup", "cup", "VOLUME", 236.588, "ml"),
            UnitSeed("gallon", "gallon", "VOLUME", 3785.41, "ml"),
            UnitSeed("item", "item", "COUNT", 1.0, "item"),
            UnitSeed("packet", "packet", "COUNT", 1.0, "packet"),
            UnitSeed("can", "can", "COUNT", 1.0, "can"),
            UnitSeed("bottle", "bottle", "COUNT", 1.0, "bottle"),
            UnitSeed("bunch", "bunch", "COUNT", 1.0, "bunch"),
            UnitSeed("serving", "serving", "HOUSEHOLD", 1.0, "serving"),
            UnitSeed("kcal", "kilocalorie", "ENERGY", 1.0, "kcal"),
        ).forEach { seed ->
            db.insertWithOnConflict(
                "units",
                null,
                ContentValues().apply {
                    put("code", seed.code)
                    put("name", seed.name)
                    put("unit_type", seed.type)
                    put("conversion_to_base", seed.conversionToBase)
                    put("base_unit_code", seed.baseUnitCode)
                },
                SQLiteDatabase.CONFLICT_IGNORE,
            )
        }
        listOf(
            "protein",
            "produce",
            "fruit",
            "grain",
            "dairy",
            "fat",
            "spice",
            "condiment",
            "prepared",
            "beverage",
            "household",
            "other",
        ).forEachIndexed { index, name ->
            db.insertWithOnConflict(
                "food_categories",
                null,
                ContentValues().apply {
                    put("name", name)
                    putNull("parent_category_id")
                    put("sort_order", index)
                },
                SQLiteDatabase.CONFLICT_IGNORE,
            )
        }
        listOf(
            Triple(StorageZone.PANTRY.name, "PANTRY", 0),
            Triple(StorageZone.FRIDGE.name, "FRIDGE", 1),
            Triple(StorageZone.FREEZER.name, "FREEZER", 2),
        ).forEach { (name, type, sortOrder) ->
            db.insertWithOnConflict(
                "storage_zones",
                null,
                ContentValues().apply {
                    put("name", name)
                    put("zone_type", type)
                    putNull("parent_zone_id")
                    put("sort_order", sortOrder)
                },
                SQLiteDatabase.CONFLICT_IGNORE,
            )
        }
    }

    private data class UnitSeed(
        val code: String,
        val name: String,
        val type: String,
        val conversionToBase: Double?,
        val baseUnitCode: String?,
    )

    private fun backfillStructuredFoodData(db: SQLiteDatabase) {
        seedStructuredReferenceData(db)
        backfillInventory(db)
        backfillGroceries(db)
        backfillRecipes(db)
        backfillMealsAndPlans(db)
    }

    private fun backfillInventory(db: SQLiteDatabase) {
        db.rawQuery(
            """
            SELECT id, name, quantity, zone, category, serving_text, calories, protein_g, carbs_g, fat_g,
                   nutrition_source, notes, image_uri, image_url, expires_at, source, source_message_id, created_at, updated_at
            FROM inventory_items
            """.trimIndent(),
            emptyArray(),
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val name = cursor.string("name")
                val quantity = cursor.string("quantity")
                val category = cursor.string("category").ifBlank { categorizeFood(name) }
                val zone = runCatching { StorageZone.valueOf(cursor.string("zone")) }.getOrDefault(StorageZone.PANTRY)
                val foodId = ensureFood(
                    db = db,
                    name = name,
                    category = category,
                    imageUri = cursor.nullableString("image_uri"),
                    imageUrl = cursor.string("image_url"),
                    now = cursor.long("updated_at"),
                )
                val parsed = parseQuantity(quantity)
                val unitId = parsed.unitCode?.let { unitId(db, it) }
                val storageZoneId = ensureStorageZone(db, zone)
                db.update(
                    "inventory_items",
                    ContentValues().apply {
                        put("food_id", foodId)
                        put("quantity_value", parsed.value)
                        if (unitId == null) putNull("unit_id") else put("unit_id", unitId)
                        put("quantity_text", parsed.text)
                        put("storage_zone_id", storageZoneId)
                    },
                    "id = ?",
                    arrayOf(cursor.long("id").toString()),
                )
                upsertFoodNutritionFromRow(db, foodId, cursor)
                ensureInventoryBatch(
                    db = db,
                    inventoryItemId = cursor.long("id"),
                    foodId = foodId,
                    parsed = parsed,
                    storageZoneId = storageZoneId,
                    expiresAtMillis = cursor.nullableLong("expires_at"),
                    source = cursor.string("source").ifBlank { "legacy_backfill" },
                    sourceMessageId = cursor.nullableLong("source_message_id"),
                    notes = cursor.string("notes"),
                    now = cursor.long("updated_at"),
                    createIfExisting = false,
                )
            }
        }
    }

    private fun backfillGroceries(db: SQLiteDatabase) {
        db.rawQuery(
            """
            SELECT id, name, quantity, category, serving_text, calories, protein_g, carbs_g, fat_g,
                   nutrition_source, image_uri, image_url, updated_at
            FROM grocery_items
            """.trimIndent(),
            emptyArray(),
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val name = cursor.string("name")
                val category = cursor.string("category").ifBlank { categorizeFood(name) }
                val foodId = ensureFood(
                    db = db,
                    name = name,
                    category = category,
                    imageUri = cursor.nullableString("image_uri"),
                    imageUrl = cursor.string("image_url"),
                    now = cursor.long("updated_at"),
                )
                val parsed = parseQuantity(cursor.string("quantity"))
                val unitId = parsed.unitCode?.let { unitId(db, it) }
                db.update(
                    "grocery_items",
                    ContentValues().apply {
                        put("food_id", foodId)
                        put("entered_name", name)
                        put("quantity_value", parsed.value)
                        if (unitId == null) putNull("unit_id") else put("unit_id", unitId)
                        put("quantity_text", parsed.text)
                    },
                    "id = ?",
                    arrayOf(cursor.long("id").toString()),
                )
                upsertFoodNutritionFromRow(db, foodId, cursor)
            }
        }
    }

    private fun backfillRecipes(db: SQLiteDatabase) {
        db.rawQuery(
            "SELECT id, title, ingredients, steps, tags, image_uri, image_url, updated_at FROM recipes",
            emptyArray(),
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val recipeId = cursor.long("id")
                val foodId = ensureFood(
                    db = db,
                    name = cursor.string("title"),
                    category = "prepared",
                    foodType = "RECIPE",
                    imageUri = cursor.nullableString("image_uri"),
                    imageUrl = cursor.string("image_url"),
                    now = cursor.long("updated_at"),
                )
                db.update(
                    "recipes",
                    ContentValues().apply { put("food_id", foodId) },
                    "id = ?",
                    arrayOf(recipeId.toString()),
                )
                syncRecipeStructured(
                    db = db,
                    recipeId = recipeId,
                    title = cursor.string("title"),
                    ingredientsText = cursor.string("ingredients"),
                    stepsText = cursor.string("steps"),
                    tags = cursor.string("tags"),
                    now = cursor.long("updated_at"),
                )
            }
        }
    }

    private fun backfillMealsAndPlans(db: SQLiteDatabase) {
        db.rawQuery(
            "SELECT id, title, used_items_text, recipe_id, calories, protein_g, carbs_g, fat_g, updated_at FROM meal_logs",
            emptyArray(),
        ).use { cursor ->
            while (cursor.moveToNext()) {
                syncMealLogStructured(
                    db = db,
                    mealLogId = cursor.long("id"),
                    title = cursor.string("title"),
                    usedItemsText = cursor.string("used_items_text"),
                    recipeId = cursor.nullableLong("recipe_id"),
                    calories = cursor.nullableInt("calories"),
                    proteinGrams = cursor.nullableDouble("protein_g"),
                    carbsGrams = cursor.nullableDouble("carbs_g"),
                    fatGrams = cursor.nullableDouble("fat_g"),
                    now = cursor.long("updated_at"),
                )
            }
        }
        db.rawQuery(
            "SELECT id, title, recipe_id, updated_at FROM meal_plan_entries",
            emptyArray(),
        ).use { cursor ->
            while (cursor.moveToNext()) {
                syncMealPlanEntryStructured(
                    db = db,
                    entryId = cursor.long("id"),
                    title = cursor.string("title"),
                    recipeId = cursor.nullableLong("recipe_id"),
                    now = cursor.long("updated_at"),
                )
            }
        }
    }

    private fun addNutritionColumns(db: SQLiteDatabase, table: String) {
        db.addColumn(table, "serving_text TEXT NOT NULL DEFAULT ''")
        db.addColumn(table, "calories INTEGER")
        db.addColumn(table, "protein_g REAL")
        db.addColumn(table, "carbs_g REAL")
        db.addColumn(table, "fat_g REAL")
        db.addColumn(table, "nutrition_source TEXT NOT NULL DEFAULT ''")
    }

    private fun unitId(db: SQLiteDatabase, code: String): Long? =
        db.rawQuery(
            "SELECT id FROM units WHERE code = ? LIMIT 1",
            arrayOf(code),
        ).use { cursor -> if (cursor.moveToFirst()) cursor.long("id") else null }

    private fun ensureStorageZone(db: SQLiteDatabase, zone: StorageZone): Long =
        ensureStorageZone(db, zone.name, zone.name)

    private fun ensureStorageZone(db: SQLiteDatabase, name: String, type: String): Long {
        val normalizedType = when (type.uppercase()) {
            "FRIDGE", "REFRIGERATOR" -> "FRIDGE"
            "FREEZER" -> "FREEZER"
            "PANTRY" -> "PANTRY"
            "COUNTER", "COUNTERTOP" -> "COUNTER"
            else -> "OTHER"
        }
        db.insertWithOnConflict(
            "storage_zones",
            null,
            ContentValues().apply {
                put("name", name.uppercase())
                put("zone_type", normalizedType)
                putNull("parent_zone_id")
                put("sort_order", 99)
            },
            SQLiteDatabase.CONFLICT_IGNORE,
        )
        return db.rawQuery(
            "SELECT id FROM storage_zones WHERE name = ? LIMIT 1",
            arrayOf(name.uppercase()),
        ).use { cursor ->
            if (cursor.moveToFirst()) cursor.long("id") else error("Unable to create storage zone $name")
        }
    }

    private fun ensureCategory(db: SQLiteDatabase, category: String): Long? {
        val normalized = category.ifBlank { "other" }.lowercase()
        db.insertWithOnConflict(
            "food_categories",
            null,
            ContentValues().apply {
                put("name", normalized)
                putNull("parent_category_id")
                put("sort_order", 99)
            },
            SQLiteDatabase.CONFLICT_IGNORE,
        )
        return db.rawQuery(
            "SELECT id FROM food_categories WHERE name = ? LIMIT 1",
            arrayOf(normalized),
        ).use { cursor -> if (cursor.moveToFirst()) cursor.long("id") else null }
    }

    private fun ensureFood(
        db: SQLiteDatabase,
        name: String,
        category: String,
        foodType: String = "INGREDIENT",
        imageUri: String? = null,
        imageUrl: String = "",
        now: Long = now(),
    ): Long {
        val canonical = name.canonicalFoodName()
        val categoryId = ensureCategory(db, category)
        val existing = db.rawQuery(
            "SELECT id FROM foods WHERE canonical_name = ? AND brand IS NULL AND variety IS NULL LIMIT 1",
            arrayOf(canonical),
        ).use { cursor -> if (cursor.moveToFirst()) cursor.long("id") else null }
        val foodId = existing ?: db.insert(
            "foods",
            null,
            ContentValues().apply {
                put("canonical_name", canonical)
                put("display_name", name.cleanDisplayFoodName())
                put("emoji", imageUri?.takeIf { it.length <= 8 } ?: foodEmojiForName(name))
                if (categoryId == null) putNull("category_id") else put("category_id", categoryId)
                putNull("default_unit_id")
                put("food_type", foodType)
                put("storage_default", classifyStorageZone(name).name)
                putNull("shelf_life_days")
                putNull("barcode")
                putNull("brand")
                putNull("variety")
                putNull("notes")
                put("image_uri", imageUri)
                put("image_url", imageUrl)
                put("created_at", now)
                put("updated_at", now)
            },
        )
        db.update(
            "foods",
            ContentValues().apply {
                put("display_name", name.cleanDisplayFoodName())
                if (categoryId == null) putNull("category_id") else put("category_id", categoryId)
                if (imageUri != null) put("image_uri", imageUri)
                if (imageUrl.isNotBlank()) put("image_url", imageUrl)
                put("updated_at", now)
            },
            "id = ?",
            arrayOf(foodId.toString()),
        )
        if (categoryId != null) {
            db.insertWithOnConflict(
                "food_category_links",
                null,
                ContentValues().apply {
                    put("food_id", foodId)
                    put("category_id", categoryId)
                },
                SQLiteDatabase.CONFLICT_IGNORE,
            )
        }
        addFoodAlias(db, foodId, name, "USER", now)
        commonAliasesFor(name).forEach { alias -> addFoodAlias(db, foodId, alias, "REGIONAL", now) }
        return foodId
    }

    private fun addFoodAlias(db: SQLiteDatabase, foodId: Long, alias: String, type: String, now: Long) {
        val cleaned = alias.cleanDisplayFoodName()
        if (cleaned.isBlank()) return
        db.insertWithOnConflict(
            "food_aliases",
            null,
            ContentValues().apply {
                put("food_id", foodId)
                put("alias", cleaned)
                put("language", "en")
                put("alias_type", type)
                put("created_at", now)
            },
            SQLiteDatabase.CONFLICT_IGNORE,
        )
    }

    private fun upsertFoodNutritionFromRow(db: SQLiteDatabase, foodId: Long, cursor: Cursor) {
        val servingText = cursor.string("serving_text")
        val calories = cursor.nullableDouble("calories")
        val protein = cursor.nullableDouble("protein_g")
        val carbs = cursor.nullableDouble("carbs_g")
        val fat = cursor.nullableDouble("fat_g")
        if (servingText.isBlank() && calories == null && protein == null && carbs == null && fat == null) return
        val serving = parseQuantity(servingText)
        val unitId = serving.unitCode?.let { unitId(db, it) }
        db.insertWithOnConflict(
            "food_nutrition",
            null,
            ContentValues().apply {
                put("food_id", foodId)
                put("serving_quantity", serving.value)
                if (unitId == null) putNull("serving_unit_id") else put("serving_unit_id", unitId)
                put("serving_text", servingText)
                put("calories_kcal", calories)
                put("protein_g", protein)
                put("carbs_g", carbs)
                put("fat_g", fat)
                putNull("fiber_g")
                putNull("sodium_mg")
                put("source", cursor.string("nutrition_source"))
                put("verified", 0)
                put("updated_at", now())
            },
            SQLiteDatabase.CONFLICT_REPLACE,
        )
    }

    private fun syncInventoryStructured(
        inventoryItemId: Long,
        candidate: FoodCandidate,
        sourceMessageId: Long?,
        source: String,
        now: Long,
        createBatch: Boolean = true,
        purchase: InventoryBatchPurchase? = null,
    ) {
        val db = writableDatabase
        val category = candidate.category.ifBlank { categorizeFood(candidate.name) }
        val foodId = ensureFood(db, candidate.name, category, imageUri = candidate.imageUri, imageUrl = candidate.imageUrl, now = now)
        val parsed = parseQuantity(candidate.quantity)
        val unitId = parsed.unitCode?.let { unitId(db, it) }
        val storageZoneId = ensureStorageZone(db, candidate.zone)
        db.update(
            "inventory_items",
            ContentValues().apply {
                put("food_id", foodId)
                put("quantity_value", parsed.value)
                if (unitId == null) putNull("unit_id") else put("unit_id", unitId)
                put("quantity_text", parsed.text)
                put("storage_zone_id", storageZoneId)
            },
            "id = ?",
            arrayOf(inventoryItemId.toString()),
        )
        upsertFoodNutritionFromCandidate(db, foodId, candidate)
        ensureInventoryBatch(
            db = db,
            inventoryItemId = inventoryItemId,
            foodId = foodId,
            parsed = parsed,
            storageZoneId = storageZoneId,
            expiresAtMillis = candidate.expiresAtMillis,
            source = source,
            sourceMessageId = sourceMessageId,
            notes = candidate.notes,
            now = now,
            createIfExisting = createBatch,
            purchase = purchase,
        )
    }

    private fun syncGroceryStructured(groceryItemId: Long, candidate: FoodCandidate, now: Long) {
        val db = writableDatabase
        val category = candidate.category.ifBlank { categorizeFood(candidate.name) }
        val foodId = ensureFood(db, candidate.name, category, imageUri = candidate.imageUri, imageUrl = candidate.imageUrl, now = now)
        val parsed = parseQuantity(candidate.quantity)
        val unitId = parsed.unitCode?.let { unitId(db, it) }
        db.update(
            "grocery_items",
            ContentValues().apply {
                put("food_id", foodId)
                put("entered_name", candidate.name)
                put("quantity_value", parsed.value)
                if (unitId == null) putNull("unit_id") else put("unit_id", unitId)
                put("quantity_text", parsed.text)
            },
            "id = ?",
            arrayOf(groceryItemId.toString()),
        )
        upsertFoodNutritionFromCandidate(db, foodId, candidate)
    }

    private fun ensureInventoryBatch(
        db: SQLiteDatabase,
        inventoryItemId: Long,
        foodId: Long,
        parsed: ParsedQuantity,
        storageZoneId: Long,
        expiresAtMillis: Long?,
        source: String,
        sourceMessageId: Long?,
        notes: String,
        now: Long,
        createIfExisting: Boolean,
        purchase: InventoryBatchPurchase? = null,
    ): Long {
        val existingBatchId = db.rawQuery(
            "SELECT id FROM inventory_batches WHERE inventory_item_id = ? ORDER BY id DESC LIMIT 1",
            arrayOf(inventoryItemId.toString()),
        ).use { cursor -> if (cursor.moveToFirst()) cursor.long("id") else null }
        val unitId = parsed.unitCode?.let { unitId(db, it) }
        if (!createIfExisting && existingBatchId != null) {
            db.update(
                "inventory_batches",
                ContentValues().apply {
                    put("food_id", foodId)
                    put("quantity_value", parsed.value)
                    if (unitId == null) putNull("unit_id") else put("unit_id", unitId)
                    put("quantity_text", parsed.text)
                    put("original_quantity_value", parsed.value)
                    put("storage_zone_id", storageZoneId)
                    if (expiresAtMillis == null) putNull("expires_date_epoch_day") else put("expires_date_epoch_day", expiresAtMillis.toEpochDay())
                    if (expiresAtMillis == null) putNull("best_before_date_epoch_day") else put("best_before_date_epoch_day", expiresAtMillis.toEpochDay())
                    purchase?.let {
                        if (it.purchasedAtMillis == null) putNull("purchase_date_epoch_day") else put("purchase_date_epoch_day", it.purchasedAtMillis.toEpochDay())
                        if (it.priceCents == null) putNull("purchase_price_cents") else put("purchase_price_cents", it.priceCents)
                        put("currency_code", it.currencyCode.trim().uppercase().ifBlank { "USD" })
                        if (it.storeName.isBlank()) putNull("store_name") else put("store_name", it.storeName)
                    }
                    put("source", source)
                    if (sourceMessageId == null) putNull("source_message_id") else put("source_message_id", sourceMessageId)
                    put("notes", notes)
                    put("updated_at", now)
                },
                "id = ?",
                arrayOf(existingBatchId.toString()),
            )
            return existingBatchId
        }
        return db.insert(
            "inventory_batches",
            null,
            ContentValues().apply {
                put("inventory_item_id", inventoryItemId)
                put("food_id", foodId)
                put("quantity_value", parsed.value)
                if (unitId == null) putNull("unit_id") else put("unit_id", unitId)
                put("quantity_text", parsed.text)
                put("original_quantity_value", parsed.value)
                put("storage_zone_id", storageZoneId)
                if (purchase?.purchasedAtMillis == null) putNull("purchase_date_epoch_day") else put("purchase_date_epoch_day", purchase.purchasedAtMillis.toEpochDay())
                putNull("opened_date_epoch_day")
                if (expiresAtMillis == null) putNull("expires_date_epoch_day") else put("expires_date_epoch_day", expiresAtMillis.toEpochDay())
                if (expiresAtMillis == null) putNull("best_before_date_epoch_day") else put("best_before_date_epoch_day", expiresAtMillis.toEpochDay())
                if (purchase?.priceCents == null) putNull("purchase_price_cents") else put("purchase_price_cents", purchase.priceCents)
                put("currency_code", purchase?.currencyCode?.trim()?.uppercase()?.ifBlank { "USD" } ?: "USD")
                if (purchase?.storeName.isNullOrBlank()) putNull("store_name") else put("store_name", purchase.storeName)
                put("package_state", "UNKNOWN")
                put("status", "AVAILABLE")
                put("source", source)
                if (sourceMessageId == null) putNull("source_message_id") else put("source_message_id", sourceMessageId)
                putNull("receipt_item_id")
                put("notes", notes)
                put("created_at", now)
                put("updated_at", now)
            },
        )
    }

    private fun upsertFoodNutritionFromCandidate(db: SQLiteDatabase, foodId: Long, candidate: FoodCandidate) {
        val nutrition = candidate
        val serving = parseQuantity(nutrition.servingText)
        val unitId = serving.unitCode?.let { unitId(db, it) }
        db.insertWithOnConflict(
            "food_nutrition",
            null,
            ContentValues().apply {
                put("food_id", foodId)
                put("serving_quantity", serving.value)
                if (unitId == null) putNull("serving_unit_id") else put("serving_unit_id", unitId)
                put("serving_text", nutrition.servingText)
                put("calories_kcal", nutrition.calories?.toDouble())
                put("protein_g", nutrition.proteinGrams)
                put("carbs_g", nutrition.carbsGrams)
                put("fat_g", nutrition.fatGrams)
                putNull("fiber_g")
                putNull("sodium_mg")
                put("source", nutrition.nutritionSource)
                put("verified", 0)
                put("updated_at", now())
            },
            SQLiteDatabase.CONFLICT_REPLACE,
        )
    }

    private fun syncRecipeStructured(
        db: SQLiteDatabase,
        recipeId: Long,
        title: String,
        ingredientsText: String,
        stepsText: String,
        tags: String,
        now: Long,
    ) {
        db.delete("recipe_ingredients", "recipe_id = ?", arrayOf(recipeId.toString()))
        parseIngredientLines(ingredientsText).forEachIndexed { index, ingredientText ->
            val parsed = parseQuantity(ingredientText)
            val foodName = ingredientText.cleanIngredientName().ifBlank { ingredientText }
            val foodId = ensureFood(db, foodName, categorizeFood(foodName), now = now)
            db.insert(
                "recipe_ingredients",
                null,
                ContentValues().apply {
                    put("recipe_id", recipeId)
                    put("food_id", foodId)
                    put("ingredient_text", ingredientText)
                    put("quantity_value", parsed.value)
                    parsed.unitCode?.let { code -> unitId(db, code) }?.let { put("unit_id", it) } ?: putNull("unit_id")
                    put("preparation", "")
                    put("is_optional", if ("optional" in ingredientText.lowercase()) 1 else 0)
                    put("group_name", "")
                    put("sort_order", index)
                    put("substitute_group", "")
                },
            )
        }
        db.delete("recipe_steps", "recipe_id = ?", arrayOf(recipeId.toString()))
        parseStepLines(stepsText).forEachIndexed { index, step ->
            db.insertWithOnConflict(
                "recipe_steps",
                null,
                ContentValues().apply {
                    put("recipe_id", recipeId)
                    put("step_number", index + 1)
                    put("instruction", step)
                    putNull("duration_minutes")
                    putNull("temperature_value")
                    putNull("temperature_unit")
                    putNull("timer_label")
                },
                SQLiteDatabase.CONFLICT_REPLACE,
            )
        }
        db.delete("recipe_tags", "recipe_id = ?", arrayOf(recipeId.toString()))
        tags.split(",", ";", "\n")
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinctBy { it.lowercase() }
            .forEach { tag ->
                val tagId = ensureTag(db, tag)
                db.insertWithOnConflict(
                    "recipe_tags",
                    null,
                    ContentValues().apply {
                        put("recipe_id", recipeId)
                        put("tag_id", tagId)
                    },
                    SQLiteDatabase.CONFLICT_IGNORE,
                )
            }
        val recipeFoodId = ensureFood(db, title, "prepared", foodType = "RECIPE", now = now)
        db.update(
            "recipes",
            ContentValues().apply { put("food_id", recipeFoodId) },
            "id = ?",
            arrayOf(recipeId.toString()),
        )
    }

    private fun syncMealLogStructured(
        db: SQLiteDatabase,
        mealLogId: Long,
        title: String,
        usedItemsText: String,
        recipeId: Long?,
        calories: Int?,
        proteinGrams: Double?,
        carbsGrams: Double?,
        fatGrams: Double?,
        now: Long,
    ) {
        db.delete("meal_log_items", "meal_log_id = ?", arrayOf(mealLogId.toString()))
        val tokens = parseIngredientLines(usedItemsText).ifEmpty { listOf(title).filter { it.isNotBlank() } }
        tokens.forEach { token ->
            val foodName = token.cleanIngredientName().ifBlank { token }
            val foodId = if (recipeId == null) ensureFood(db, foodName, categorizeFood(foodName), now = now) else null
            db.insert(
                "meal_log_items",
                null,
                ContentValues().apply {
                    put("meal_log_id", mealLogId)
                    if (foodId == null) putNull("food_id") else put("food_id", foodId)
                    if (recipeId == null) putNull("recipe_id") else put("recipe_id", recipeId)
                    putNull("quantity_value")
                    putNull("unit_id")
                    put("serving_count", 1.0)
                    put("calories_kcal", calories?.toDouble())
                    put("protein_g", proteinGrams)
                    put("carbs_g", carbsGrams)
                    put("fat_g", fatGrams)
                    putNull("inventory_batch_id")
                    put("item_text", token)
                },
            )
        }
    }

    private fun syncMealPlanEntryStructured(
        db: SQLiteDatabase,
        entryId: Long,
        title: String,
        recipeId: Long?,
        now: Long,
    ) {
        val matchedRecipeId = recipeId ?: db.rawQuery(
            "SELECT id FROM recipes WHERE lower(title) = lower(?) AND archived = 0 LIMIT 1",
            arrayOf(title),
        ).use { cursor -> if (cursor.moveToFirst()) cursor.long("id") else null }
        val foodId = if (matchedRecipeId == null) {
            ensureFood(db, title, categorizeFood(title), foodType = "PREPARED", now = now)
        } else {
            null
        }
        db.update(
            "meal_plan_entries",
            ContentValues().apply {
                if (foodId == null) putNull("food_id") else put("food_id", foodId)
                if (matchedRecipeId == null) putNull("recipe_id") else put("recipe_id", matchedRecipeId)
            },
            "id = ?",
            arrayOf(entryId.toString()),
        )
        db.delete("meal_plan_entry_items", "meal_plan_entry_id = ?", arrayOf(entryId.toString()))
        db.insert(
            "meal_plan_entry_items",
            null,
            ContentValues().apply {
                put("meal_plan_entry_id", entryId)
                if (matchedRecipeId == null) putNull("recipe_id") else put("recipe_id", matchedRecipeId)
                if (foodId == null) putNull("food_id") else put("food_id", foodId)
                put("servings", 1.0)
                put("item_text", title)
            },
        )
    }

    private fun ensureTag(db: SQLiteDatabase, tag: String): Long {
        db.insertWithOnConflict(
            "tags",
            null,
            ContentValues().apply { put("name", tag) },
            SQLiteDatabase.CONFLICT_IGNORE,
        )
        return db.rawQuery(
            "SELECT id FROM tags WHERE name = ? LIMIT 1",
            arrayOf(tag),
        ).use { cursor ->
            if (cursor.moveToFirst()) cursor.long("id") else error("Unable to create tag $tag")
        }
    }

    private fun parseQuantity(raw: String): ParsedQuantity {
        val text = raw.trim()
        if (text.isBlank()) return ParsedQuantity(null, null, "")
        val lower = text.lowercase()
        val wordValue = WORD_NUMBERS.entries.firstOrNull { (word, _) -> lower.startsWith("$word ") }?.value
        val number = Regex("""(?<![\w.])(\d+(?:\.\d+)?|\d+/\d+)(?![\w.])""")
            .find(lower)
            ?.groupValues
            ?.getOrNull(1)
            ?.toQuantityDouble()
            ?: wordValue
        val unitCode = UNIT_ALIASES.entries.firstOrNull { (alias, _) ->
            Regex("""\b${Regex.escape(alias)}s?\b""").containsMatchIn(lower)
        }?.value
        return ParsedQuantity(number, unitCode, text)
    }

    private fun parseIngredientLines(text: String): List<String> =
        text.split("\n", ";", ",")
            .map { it.trim().trim('-', '*', '•') }
            .filter { it.isNotBlank() }
            .distinctBy { it.lowercase() }

    private fun parseStepLines(text: String): List<String> =
        text.lines()
            .flatMap { line -> line.split(Regex("""(?<=[.!?])\s+(?=[A-Z0-9])""")) }
            .map { it.trim().trim('-', '*', '•') }
            .filter { it.length >= 3 }

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

    private fun recordCommandEvent(
        draft: FoodDraft,
        status: String,
        sourceMessageId: Long?,
        summary: String,
        sourceOverride: String = draft.draftSource.name.lowercase(),
    ) {
        val now = now()
        val payload = draftPayloadJson(draft)
        val payloadText = payload.toString()
        writableDatabase.insert(
            "command_events",
            null,
            ContentValues().apply {
                put("idempotency_key", "draft:${sourceMessageId ?: "local"}:${draft.operation.name}:${payloadText.hashCode()}")
                put("source", sourceOverride.ifBlank { draft.draftSource.name.lowercase() })
                put("command_type", draft.operation.name)
                put("payload_json", payloadText)
                put("status", status)
                put("confidence", draft.confidence)
                if (sourceMessageId == null) putNull("source_message_id") else put("source_message_id", sourceMessageId)
                put("result_summary", summary)
                putNull("undo_payload_json")
                put("created_at", now)
                if (status == "APPLIED") put("applied_at", now) else putNull("applied_at")
            },
        )
    }

    private fun draftPayloadJson(draft: FoodDraft): JSONObject =
        JSONObject().apply {
            put("title", draft.title)
            put("summary", draft.summary)
            put("operation", draft.operation.name)
            put("source", draft.draftSource.name)
            put("confidence", draft.confidence)
            put("rows", JSONArray().also { rows -> draft.rows.forEach(rows::put) })
            when (draft) {
                is CompositeDraft -> put(
                    "drafts",
                    JSONArray().also { children -> draft.drafts.forEach { children.put(draftPayloadJson(it)) } },
                )
                is InventoryDraft -> put(
                    "items",
                    JSONArray().also { items -> draft.items.forEach { items.put(candidatePayloadJson(it)) } },
                )
                is GroceryDraft -> put(
                    "items",
                    JSONArray().also { items -> draft.items.forEach { items.put(candidatePayloadJson(it)) } },
                )
                is ReceiptDraft -> {
                    putNullable("receipt_id", draft.receiptId)
                    put("merchant", draft.merchant)
                    put("store_location", draft.storeLocation)
                    putNullable("purchased_at", draft.purchasedAtMillis)
                    put("currency_code", draft.currencyCode)
                    putNullable("subtotal_cents", draft.subtotalCents)
                    putNullable("tax_cents", draft.taxCents)
                    putNullable("total_cents", draft.totalCents)
                    put("source_label", draft.sourceLabel)
                    put(
                        "items",
                        JSONArray().also { items ->
                            draft.items.forEach { item ->
                                items.put(
                                    JSONObject().apply {
                                        put("disposition", item.disposition.name)
                                        put("receipt_line", item.receiptLine)
                                        putNullable("line_price_cents", item.linePriceCents)
                                        put("food", candidatePayloadJson(item.food))
                                    },
                                )
                            }
                        },
                    )
                }
                is LinkActionDraft -> {
                    put("action_type", draft.actionType)
                    put("target_kind", draft.targetKind)
                    put("target_ref", draft.targetRef)
                    put("display_name", draft.displayName)
                    put("destructive", draft.destructive)
                    put("sensitive", draft.sensitive)
                    put(
                        "fields",
                        JSONObject().also { fields ->
                            draft.fields.entries.sortedBy { it.key }.forEach { (key, value) -> fields.put(key, value) }
                        },
                    )
                }
                is RecipeDraft -> {
                    put("recipe_title", draft.titleText)
                    put("ingredients", draft.ingredientsText)
                    put("steps", draft.stepsText)
                    putNullable("servings", draft.servings)
                    putNullable("prep_minutes", draft.prepMinutes)
                    put("tags", draft.tags)
                    putNullable("image_uri", draft.imageUri)
                    put("image_url", draft.imageUrl)
                }
                is MealLogDraft -> {
                    put("meal_title", draft.titleText)
                    putNullable("calories", draft.calories)
                    putNullable("protein_g", draft.proteinGrams)
                    putNullable("carbs_g", draft.carbsGrams)
                    putNullable("fat_g", draft.fatGrams)
                    put("meal_slot", draft.mealSlot.name)
                    put("used_items_text", draft.usedItemsText)
                    putNullable("logged_date_epoch_day", draft.loggedDateEpochDay)
                    put("meal_source", draft.source)
                }
                is MealPlanDraft -> {
                    put("plan_title", draft.titleText)
                    put("days_text", draft.daysText)
                    put("grocery_hint", draft.groceryHint)
                    putNullable("start_date_epoch_day", draft.startDateEpochDay)
                    put(
                        "entries",
                        JSONArray().also { entries ->
                            draft.entries.forEach { entry ->
                                entries.put(
                                    JSONObject().apply {
                                        put("day_offset", entry.dayOffset)
                                        put("slot", entry.slot.name)
                                        put("title", entry.title)
                                        putNullable("calorie_target", entry.calorieTarget)
                                    },
                                )
                            }
                        },
                    )
                }
            }
        }

    private fun candidatePayloadJson(candidate: FoodCandidate): JSONObject =
        JSONObject().apply {
            put("name", candidate.name)
            put("quantity", candidate.quantity)
            put("zone", candidate.zone.name)
            put("category", candidate.category)
            put("serving_text", candidate.servingText)
            putNullable("calories", candidate.calories)
            putNullable("protein_g", candidate.proteinGrams)
            putNullable("carbs_g", candidate.carbsGrams)
            putNullable("fat_g", candidate.fatGrams)
            put("nutrition_source", candidate.nutritionSource)
            put("notes", candidate.notes)
            putNullable("image_uri", candidate.imageUri)
            put("image_url", candidate.imageUrl)
            putNullable("expires_at", candidate.expiresAtMillis)
            put("confidence", candidate.confidence)
            put("evidence", candidate.evidence)
            put("zone_source", candidate.zoneSource)
            put("expiry_source", candidate.expirySource)
            put("warnings", JSONArray().also { warnings -> candidate.warnings.forEach(warnings::put) })
        }

    private fun JSONObject.putNullable(key: String, value: Any?) {
        put(key, value ?: JSONObject.NULL)
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
        val db = writableDatabase
        val parsed = parseQuantity(quantityText)
        val foodId = ensureFood(db, itemName, categorizeFood(itemName), now = now)
        val unitId = parsed.unitCode?.let { unitId(db, it) }
        db.insert(
            "inventory_transactions",
            null,
            ContentValues().apply {
                if (inventoryItemId == null) putNull("inventory_item_id") else put("inventory_item_id", inventoryItemId)
                put("food_id", foodId)
                put("item_name", itemName)
                put("quantity_text", quantityText)
                if (parsed.value == null) putNull("quantity_delta") else put("quantity_delta", parsed.value)
                if (unitId == null) putNull("unit_id") else put("unit_id", unitId)
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

    companion object {
        private const val DB_NAME = "wonderfood.db"
        const val SCHEMA_VERSION = 11
        private const val DB_VERSION = SCHEMA_VERSION
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
        private const val PREF_AI_SKILL_OVERRIDE = "ai_skill_override"
    }
}

fun classifyStorageZone(value: String): StorageZone {
    val text = value.lowercase()
    return when {
        listOf("frozen", "freezer", "ice cream", "popsicle").any { it in text } -> StorageZone.FREEZER
        listOf(
            "milk",
            "yogurt",
            "curd",
            "dahi",
            "paneer",
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
            "cucumber",
            "eggplant",
            "brinjal",
            "tomato",
            "pepper",
            "jalap",
            "lime",
            "lemon",
            "grape",
        ).any { it in text } -> StorageZone.FRIDGE
        else -> StorageZone.PANTRY
    }
}

fun categorizeFood(value: String): String {
    val text = value.lowercase()
    return when {
        listOf("chicken", "fish", "salmon", "beef", "egg", "tofu", "bean", "lentil", "dal", "yogurt", "curd", "dahi", "paneer").any { it in text } -> "protein"
        listOf("spinach", "lettuce", "greens", "broccoli", "carrot", "pepper", "jalap", "tomato", "cucumber", "potato", "onion", "bhindi", "okra", "lauki", "gourd", "brinjal", "eggplant").any { it in text } -> "produce"
        listOf("berry", "banana", "apple", "orange", "grape", "lime", "lemon", "fruit").any { it in text } -> "fruit"
        listOf("rice", "oat", "pasta", "bread", "tortilla", "cereal").any { it in text } -> "grain"
        listOf("milk", "cheese", "cream", "yogurt", "curd", "dahi", "paneer").any { it in text } -> "dairy"
        listOf("water", "juice", "soda", "coffee", "tea", "drink", "beverage").any { it in text } -> "beverage"
        listOf("oil", "butter", "avocado", "nuts", "peanut").any { it in text } -> "fat"
        else -> "other"
    }
}

private val WORD_NUMBERS = linkedMapOf(
    "half" to 0.5,
    "one" to 1.0,
    "two" to 2.0,
    "three" to 3.0,
    "four" to 4.0,
    "five" to 5.0,
    "six" to 6.0,
    "seven" to 7.0,
    "eight" to 8.0,
    "nine" to 9.0,
    "ten" to 10.0,
    "dozen" to 12.0,
)

private val UNIT_ALIASES = linkedMapOf(
    "kilograms" to "kg",
    "kilogram" to "kg",
    "kgs" to "kg",
    "kg" to "kg",
    "grams" to "g",
    "gram" to "g",
    "g" to "g",
    "pounds" to "lb",
    "pound" to "lb",
    "lbs" to "lb",
    "lb" to "lb",
    "ounces" to "oz",
    "ounce" to "oz",
    "oz" to "oz",
    "liters" to "l",
    "liter" to "l",
    "litres" to "l",
    "litre" to "l",
    "l" to "l",
    "milliliters" to "ml",
    "milliliter" to "ml",
    "millilitres" to "ml",
    "millilitre" to "ml",
    "ml" to "ml",
    "tablespoons" to "tbsp",
    "tablespoon" to "tbsp",
    "tbsp" to "tbsp",
    "teaspoons" to "tsp",
    "teaspoon" to "tsp",
    "tsp" to "tsp",
    "cups" to "cup",
    "cup" to "cup",
    "packets" to "packet",
    "packet" to "packet",
    "packs" to "packet",
    "pack" to "packet",
    "cans" to "can",
    "can" to "can",
    "bottles" to "bottle",
    "bottle" to "bottle",
    "bunches" to "bunch",
    "bunch" to "bunch",
    "servings" to "serving",
    "serving" to "serving",
    "items" to "item",
    "item" to "item",
)

private fun String.toQuantityDouble(): Double? {
    val cleaned = trim()
    if ("/" in cleaned) {
        val numerator = cleaned.substringBefore("/").toDoubleOrNull()
        val denominator = cleaned.substringAfter("/").toDoubleOrNull()
        if (numerator != null && denominator != null && denominator != 0.0) return numerator / denominator
    }
    return cleaned.toDoubleOrNull()
}

private fun String.cleanDisplayFoodName(): String =
    trim()
        .replace(Regex("""\s+"""), " ")
        .trim('-', '*', '•', ':')
        .trim()

private fun String.canonicalFoodName(): String {
    val cleaned = cleanDisplayFoodName()
        .lowercase()
        .replace("&", " and ")
        .replace(Regex("""[^\p{L}\p{N}\s]"""), " ")
        .replace(Regex("""\s+"""), " ")
        .trim()
    val mapped = when (cleaned) {
        "curd", "dahi", "yoghurt" -> "yogurt"
        "toor dal", "tuvar dal", "arhar dal", "kandi pappu", "pigeon pea", "pigeon peas" -> "toor dal"
        "bhindi", "lady finger", "ladies finger", "okra" -> "okra"
        "lauki", "doodhi", "bottle gourd" -> "bottle gourd"
        "brinjal", "eggplant", "aubergine" -> "eggplant"
        "kabuli chana", "garbanzo bean", "garbanzo beans", "chickpea", "chickpeas" -> "chickpea"
        else -> cleaned
    }
    return when {
        mapped.endsWith("ies") && mapped.length > 4 -> mapped.dropLast(3) + "y"
        mapped.endsWith("es") && mapped.length > 4 && !mapped.endsWith("ses") -> mapped.dropLast(2)
        mapped.endsWith("s") && mapped.length > 3 && !mapped.endsWith("ss") && mapped !in setOf("rice", "oats") -> mapped.dropLast(1)
        else -> mapped
    }
}

private fun commonAliasesFor(name: String): List<String> =
    when (name.canonicalFoodName()) {
        "yogurt" -> listOf("curd", "dahi", "yoghurt")
        "toor dal" -> listOf("toor dal", "tuvar dal", "arhar dal", "kandi pappu", "pigeon peas")
        "okra" -> listOf("bhindi", "lady finger", "ladies finger")
        "bottle gourd" -> listOf("lauki", "doodhi")
        "eggplant" -> listOf("brinjal", "aubergine")
        "chickpea" -> listOf("chickpeas", "garbanzo beans", "kabuli chana")
        else -> emptyList()
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
