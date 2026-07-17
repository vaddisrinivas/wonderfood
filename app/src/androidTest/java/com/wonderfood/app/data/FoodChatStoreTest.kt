package com.wonderfood.app.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.wonderfood.app.ai.FoodInterpreter
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class FoodChatStoreTest {
    private val testDbName = "wonderfood-test.db"
    private lateinit var context: Context
    private lateinit var store: FoodChatStore

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(testDbName)
        store = FoodChatStore(context, testDbName)
    }

    @After
    fun tearDown() {
        store.close()
        context.deleteDatabase(testDbName)
    }

    @Test
    fun appliesDraftsIntoStructuredMemory() {
        store.applyDraft(
            InventoryDraft(
                listOf(FoodCandidate(name = "Spinach", zone = StorageZone.FRIDGE, category = "produce")),
            ),
            sourceMessageId = null,
        )
        store.applyDraft(
            MealLogDraft(
                titleText = "Spinach Eggs",
                calories = 420,
                proteinGrams = 28.0,
                carbsGrams = 18.0,
                fatGrams = 22.0,
                mealSlot = MealSlot.BREAKFAST,
                usedItemsText = "Spinach",
            ),
            sourceMessageId = null,
        )
        store.applyDraft(
            MealPlanDraft(
                titleText = "Two meals",
                daysText = "Breakfast: Spinach Eggs",
                groceryHint = "eggs",
                entries = listOf(MealPlanEntryDraft(0, MealSlot.BREAKFAST, "Spinach Eggs", 420)),
            ),
            sourceMessageId = null,
        )

        val memory = store.readMemory()
        assertEquals("produce", memory.inventory.single().category)
        assertEquals(MealSlot.BREAKFAST, memory.mealLogs.single().mealSlot)
        assertEquals("Spinach", memory.mealLogs.single().usedItemsText)
        assertEquals("Spinach Eggs", memory.mealPlanEntries.single().title)
        assertTrue(memory.mealPlanEntries.single().calorieTarget == 420)
    }

    @Test
    fun newChatPreservesHistoryAndResetIsExplicit() {
        store.seedIfEmpty()
        store.insertMessage(ChatRole.USER, "First conversation")
        val firstChatMessages = store.readMemory().messages

        store.startNewChat()
        store.insertMessage(ChatRole.USER, "Second conversation")
        val allMessages = store.readMemory().messages

        assertTrue(allMessages.size > firstChatMessages.size)
        assertEquals(setOf(1L, 2L), allMessages.map(ChatMessage::chatId).toSet())
        assertTrue(allMessages.any { it.body == "First conversation" })
        assertTrue(allMessages.any { it.body == "Second conversation" })
        assertEquals(2L, store.readMemory().currentChatId)
        assertEquals("Second conversation", store.readMemory().currentChatMessages.last().body)

        store.clearChatHistory()
        val reset = store.readMemory()
        assertEquals(1, reset.messages.size)
        assertEquals(1L, reset.currentChatId)
        assertTrue(reset.messages.single().body.contains("reset"))
    }

    @Test
    fun chatMessagesCanBeEditedWithoutChangingTheirConversation() {
        val id = store.insertMessage(ChatRole.ASSISTANT, "Original reply")

        assertTrue(store.updateMessage(id, "Corrected reply"))

        val message = store.readMemory().messages.single()
        assertEquals("Corrected reply", message.body)
        assertEquals(1L, message.chatId)
    }

    @Test
    fun v10ChatHistoryMigratesIntoTheFirstConversation() {
        store.seedIfEmpty()
        store.insertMessage(ChatRole.USER, "Message from the v10 database")
        store.close()

        context.openOrCreateDatabase(testDbName, Context.MODE_PRIVATE, null).use { db ->
            db.execSQL("PRAGMA foreign_keys=OFF")
            db.execSQL("PRAGMA legacy_alter_table=ON")
            db.execSQL("ALTER TABLE chat_messages RENAME TO chat_messages_v11")
            db.execSQL(
                """
                CREATE TABLE chat_messages (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    role TEXT NOT NULL,
                    body TEXT NOT NULL,
                    created_at INTEGER NOT NULL
                )
                """.trimIndent(),
            )
            db.execSQL(
                """
                INSERT INTO chat_messages (id, role, body, created_at)
                SELECT id, role, body, created_at FROM chat_messages_v11
                """.trimIndent(),
            )
            db.execSQL("DROP TABLE chat_messages_v11")
            db.version = 10
        }

        store = FoodChatStore(context, testDbName)
        val migrated = store.readMemory()

        assertEquals(11, scalarLong("PRAGMA user_version"))
        assertTrue(migrated.messages.any { it.body == "Message from the v10 database" })
        assertTrue(migrated.messages.all { it.chatId == 1L })
    }

    @Test
    fun compositeDraftRollsBackEveryChildWhenOneTargetCannotResolve() {
        store.applyDraft(
            InventoryDraft(listOf(FoodCandidate(name = "Eggs", quantity = "12", zone = StorageZone.FRIDGE))),
            sourceMessageId = null,
        )
        val item = store.readMemory().inventory.single()
        val commandCountBefore = scalarLong("SELECT COUNT(*) FROM command_events")

        val failed = runCatching {
            store.applyDraft(
                CompositeDraft(
                    listOf(
                        LinkActionDraft(
                            actionType = "inventory.edit",
                            targetKind = "inventory",
                            targetRef = item.id.toString(),
                            fields = mapOf("quantity" to "6"),
                        ),
                        LinkActionDraft(
                            actionType = "inventory.edit",
                            targetKind = "inventory",
                            targetRef = "999999",
                            fields = mapOf("quantity" to "1"),
                        ),
                    ),
                ),
                sourceMessageId = null,
            )
        }.exceptionOrNull()

        assertTrue(failed is FoodDraftApplyException)
        assertEquals("12", store.readMemory().inventory.single().quantity)
        assertEquals(commandCountBefore, scalarLong("SELECT COUNT(*) FROM command_events"))
    }

    @Test
    fun mirrorsDraftWritesIntoV10StructuredTables() {
        store.applyDraft(
            InventoryDraft(
                listOf(
                    FoodCandidate(
                        name = "Toor dal",
                        quantity = "2 kg",
                        zone = StorageZone.PANTRY,
                        category = "protein",
                    ),
                ),
            ),
            sourceMessageId = null,
        )
        store.applyDraft(
            GroceryDraft(
                listOf(
                    FoodCandidate(
                        name = "Dahi",
                        quantity = "1 cup",
                        zone = StorageZone.FRIDGE,
                        category = "dairy",
                    ),
                ),
            ),
            sourceMessageId = null,
        )
        store.applyDraft(
            RecipeDraft(
                titleText = "Sambar",
                ingredientsText = "1 cup toor dal, bhindi, tamarind",
                stepsText = "Boil dal. Simmer with bhindi.",
                servings = 4,
                tags = "South Indian",
            ),
            sourceMessageId = null,
        )
        store.applyDraft(
            MealLogDraft(
                titleText = "Sambar",
                calories = 300,
                proteinGrams = 14.0,
                carbsGrams = 42.0,
                fatGrams = 8.0,
                mealSlot = MealSlot.LUNCH,
                usedItemsText = "toor dal, bhindi",
            ),
            sourceMessageId = null,
        )
        store.applyDraft(
            MealPlanDraft(
                titleText = "Sambar plan",
                daysText = "",
                groceryHint = "",
                entries = listOf(MealPlanEntryDraft(0, MealSlot.DINNER, "Sambar", 300)),
            ),
            sourceMessageId = null,
        )
        store.rejectDraft(
            GroceryDraft(listOf(FoodCandidate(name = "Duplicate test item"))),
            sourceMessageId = null,
        )

        assertEquals(11, scalarLong("PRAGMA user_version"))
        assertTrue(scalarLong("SELECT COUNT(*) FROM foods WHERE canonical_name = 'toor dal'") >= 1)
        assertEquals(
            "yogurt",
            scalarText(
                """
                SELECT foods.canonical_name
                FROM foods
                JOIN food_aliases ON food_aliases.food_id = foods.id
                WHERE lower(food_aliases.alias) = 'dahi'
                LIMIT 1
                """.trimIndent(),
            ),
        )
        assertTrue(
            scalarLong(
                """
                SELECT COUNT(*)
                FROM food_aliases
                WHERE lower(alias) IN ('toor dal', 'arhar dal', 'kandi pappu')
                """.trimIndent(),
            ) >= 2,
        )
        assertEquals(
            2.0,
            scalarDouble(
                """
                SELECT quantity_value
                FROM inventory_items
                WHERE lower(name) = 'toor dal'
                LIMIT 1
                """.trimIndent(),
            ),
            0.001,
        )
        assertEquals(
            "kg",
            scalarText(
                """
                SELECT units.code
                FROM inventory_items
                JOIN units ON units.id = inventory_items.unit_id
                WHERE lower(inventory_items.name) = 'toor dal'
                LIMIT 1
                """.trimIndent(),
            ),
        )
        assertEquals(1, scalarLong("SELECT COUNT(*) FROM inventory_batches"))
        assertEquals(
            "cup",
            scalarText(
                """
                SELECT units.code
                FROM grocery_items
                JOIN units ON units.id = grocery_items.unit_id
                WHERE lower(grocery_items.entered_name) = 'dahi'
                LIMIT 1
                """.trimIndent(),
            ),
        )
        assertEquals(3, scalarLong("SELECT COUNT(*) FROM recipe_ingredients"))
        assertEquals(2, scalarLong("SELECT COUNT(*) FROM recipe_steps"))
        assertTrue(scalarLong("SELECT COUNT(*) FROM recipe_tags") >= 2)
        assertEquals(2, scalarLong("SELECT COUNT(*) FROM meal_log_items"))
        assertEquals(1, scalarLong("SELECT COUNT(*) FROM meal_plan_entry_items WHERE recipe_id IS NOT NULL"))
        assertEquals(5, scalarLong("SELECT COUNT(*) FROM command_events WHERE status = 'APPLIED'"))
        assertEquals(1, scalarLong("SELECT COUNT(*) FROM command_events WHERE status = 'REJECTED'"))
    }

    @Test
    fun updatesMealLogInPlace() {
        store.applyDraft(
            MealLogDraft(
                titleText = "Tofu Bowl",
                calories = 520,
                proteinGrams = 20.0,
                carbsGrams = 52.0,
                fatGrams = 14.0,
                mealSlot = MealSlot.FLEX,
            ),
            sourceMessageId = null,
        )
        val meal = store.readMemory().mealLogs.single()

        store.updateMealLog(
            id = meal.id,
            title = "Tofu lunch bowl",
            calories = 650,
            proteinGrams = 32.0,
            carbsGrams = 58.0,
            fatGrams = 18.0,
            mealSlot = MealSlot.LUNCH,
            usedItemsText = "Tofu, rice",
            loggedDateEpochDay = meal.loggedDateEpochDay,
            source = "test_edit",
        )

        val updated = store.readMemory().mealLogs.single()
        assertEquals(meal.id, updated.id)
        assertEquals("Tofu lunch bowl", updated.title)
        assertEquals(650, updated.calories)
        assertEquals(32.0, requireNotNull(updated.proteinGrams), 0.0)
        assertEquals(MealSlot.LUNCH, updated.mealSlot)
        assertEquals("Tofu, rice", updated.usedItemsText)
    }

    @Test
    fun recordsMutationCommandsIntoCommandEvents() {
        store.recordMutationCommand(
            FoodMutationCommand(
                type = FoodMutationCommandType.UPDATE_INVENTORY,
                label = "Update pantry item",
                origin = FoodDraftCommandOrigin.MANUAL_SAVE,
                payload = mapOf("id" to "7", "name" to "Eggs"),
            ),
            status = "APPLIED",
            summary = "Kitchen page updated.",
        )

        assertEquals(
            1,
            scalarLong(
                """
                SELECT COUNT(*)
                FROM command_events
                WHERE command_type = 'UPDATE_INVENTORY'
                  AND source = 'manual'
                  AND status = 'APPLIED'
                  AND result_summary = 'Kitchen page updated.'
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun linkActionDraftUpdatesInventoryThroughExecutorAndAudit() {
        store.applyDraft(
            InventoryDraft(listOf(FoodCandidate(name = "Eggs", quantity = "12", zone = StorageZone.FRIDGE))),
            sourceMessageId = null,
        )
        val item = store.readMemory().inventory.single()
        val executor = FoodDraftCommandExecutor { store }
        val result = executor.execute(
            FoodDraftCommand(
                draft = LinkActionDraft(
                    actionType = "inventory.edit",
                    targetKind = "inventory",
                    targetRef = item.id.toString(),
                    fields = mapOf("quantity" to "6", "zone" to "fridge", "notes" to "GPT prefilled"),
                ),
                sourceMessageId = null,
                origin = FoodDraftCommandOrigin.EXTERNAL_PROPOSAL,
            ),
        )

        assertTrue(result is FoodDraftExecutionResult.Applied)
        val updated = store.readMemory().inventory.single()
        assertEquals(item.id, updated.id)
        assertEquals("6", updated.quantity)
        assertEquals(StorageZone.FRIDGE, updated.zone)
        assertEquals("GPT prefilled", updated.notes)
        assertEquals(
            1,
            scalarLong(
                """
                SELECT COUNT(*)
                FROM command_events
                WHERE command_type = 'UPDATE'
                  AND source = 'external_proposal'
                  AND status = 'APPLIED'
                  AND payload_json LIKE '%inventory.edit%'
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun compositeLinkActionDraftAppliesBulkActionsThroughReviewExecutor() {
        store.applyDraft(
            InventoryDraft(listOf(FoodCandidate(name = "Eggs", quantity = "12", zone = StorageZone.FRIDGE))),
            sourceMessageId = null,
        )
        store.applyDraft(
            GroceryDraft(listOf(FoodCandidate(name = "Old milk", quantity = "1"))),
            sourceMessageId = null,
        )
        val memory = store.readMemory()
        val inventoryId = memory.inventory.single().id
        val groceryId = memory.groceries.single().id
        val executor = FoodDraftCommandExecutor { store }
        val result = executor.execute(
            FoodDraftCommand(
                draft = CompositeDraft(
                    listOf(
                        LinkActionDraft(
                            actionType = "inventory.edit",
                            targetKind = "inventory",
                            targetRef = inventoryId.toString(),
                            fields = mapOf("quantity" to "18"),
                        ),
                        LinkActionDraft(
                            actionType = "grocery.delete",
                            targetKind = "grocery",
                            targetRef = groceryId.toString(),
                            destructive = true,
                        ),
                    ),
                ),
                sourceMessageId = null,
                origin = FoodDraftCommandOrigin.EXTERNAL_PROPOSAL,
            ),
        )

        assertTrue(result is FoodDraftExecutionResult.Applied)
        assertEquals("18", store.readMemory().inventory.single().quantity)
        assertTrue(store.readMemory().groceries.isEmpty())
        assertEquals(
            1,
            scalarLong(
                """
                SELECT COUNT(*)
                FROM command_events
                WHERE command_type = 'CREATE'
                  AND source = 'external_proposal'
                  AND status = 'APPLIED'
                  AND payload_json LIKE '%grocery.delete%'
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun purchaseTemplateDraftAppliesThroughExecutorAndAudit() {
        val turn = FoodInterpreter().interpret(
            text = "Stock weekly Costco",
            memory = store.readMemory(),
            promptContext = "Current WonderFood section: Kitchen.",
        )
        val executor = FoodDraftCommandExecutor { store }
        val result = executor.execute(
            FoodDraftCommand(
                draft = requireNotNull(turn.draft),
                sourceMessageId = null,
                origin = FoodDraftCommandOrigin.LOCAL_FALLBACK,
            ),
        )

        assertTrue(result is FoodDraftExecutionResult.Applied)
        assertEquals(6, store.readMemory().inventory.size)
        assertEquals(
            1,
            scalarLong(
                """
                SELECT COUNT(*)
                FROM command_events
                WHERE command_type = 'CREATE'
                  AND source = 'local_fallback'
                  AND status = 'APPLIED'
                  AND payload_json LIKE '%Greek Yogurt%'
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun editsIndividualMealPlanEntriesFromCalendar() {
        val today = java.time.LocalDate.now().toEpochDay()

        store.addMealPlanEntry(
            dateEpochDay = today,
            slot = MealSlot.LUNCH,
            title = "Generic Lunch",
            calorieTarget = 520,
        )
        val entry = store.readMemory().mealPlanEntries.single()

        store.updateMealPlanEntry(
            id = entry.id,
            dateEpochDay = today + 1,
            slot = MealSlot.DINNER,
            title = "Generic Dinner",
            calorieTarget = 640,
            status = MealPlanEntryStatus.SKIPPED,
        )

        val updated = store.readMemory().mealPlanEntries.single()
        assertEquals(entry.id, updated.id)
        assertEquals(today + 1, updated.dateEpochDay)
        assertEquals(MealSlot.DINNER, updated.slot)
        assertEquals("Generic Dinner", updated.title)
        assertEquals(640, updated.calorieTarget)
        assertEquals(MealPlanEntryStatus.SKIPPED, updated.status)
        assertTrue(updated.imageUri?.isNotBlank() == true)
        assertTrue(updated.source.isNotBlank())

        store.deleteMealPlanEntry(entry.id)
        assertTrue(store.readMemory().mealPlanEntries.isEmpty())
    }

    @Test
    fun updatesEditableObjectPagesInPlace() {
        store.applyDraft(
            InventoryDraft(
                listOf(
                    FoodCandidate(
                        name = "Generic Yogurt",
                        quantity = "1 cup",
                        zone = StorageZone.FRIDGE,
                        category = "dairy",
                        imageUri = "🥣",
                        imageUrl = "https://example.invalid/wonderfood/yogurt.png",
                    ),
                ),
            ),
            sourceMessageId = null,
        )
        store.applyDraft(
            GroceryDraft(
                listOf(
                    FoodCandidate(
                        name = "Generic Rice",
                        quantity = "1 bag",
                        zone = StorageZone.PANTRY,
                        category = "grain",
                    ),
                ),
            ),
            sourceMessageId = null,
        )
        store.applyDraft(
            MealPlanDraft(
                titleText = "Generic plan",
                daysText = "Lunch: Generic Rice Bowl",
                groceryHint = "spinach",
            ),
            sourceMessageId = null,
        )
        val receiptId = store.insertReceiptCapture("content://wonderfood-test/receipt/generic", rawText = "GENERIC RICE 5.99")

        val inventory = store.readMemory().inventory.single()
        store.updateInventory(
            id = inventory.id,
            name = "Generic Greek Yogurt",
            quantity = "2 cups",
            zone = StorageZone.FRIDGE,
            category = "dairy",
            servingText = "170 g",
            calories = 140,
            proteinGrams = 18.0,
            carbsGrams = 8.0,
            fatGrams = 3.0,
            nutritionSource = "test_label",
            notes = "edited page",
            imageUri = inventory.imageUri,
            imageUrl = "https://example.invalid/wonderfood/greek-yogurt.png",
            expiresAtMillis = null,
        )

        val grocery = store.readMemory().groceries.single()
        store.updateGrocery(
            id = grocery.id,
            name = "Generic Brown Rice",
            quantity = "2 bags",
            status = GroceryStatus.BOUGHT,
            category = "grain",
            servingText = "1 cup cooked",
            calories = 205,
            proteinGrams = 4.0,
            carbsGrams = 45.0,
            fatGrams = 1.0,
            nutritionSource = "test_label",
            source = "test_edit",
            imageUri = grocery.imageUri,
            imageUrl = "https://example.invalid/wonderfood/rice.png",
        )

        val plan = store.readMemory().mealPlans.single()
        store.updateMealPlan(
            id = plan.id,
            title = "Edited plan",
            daysText = "Lunch: Edited Rice Bowl\nDinner: Yogurt Plate",
            groceryHint = "berries",
            startDateEpochDay = plan.startDateEpochDay,
        )
        store.updateReceiptStatus(receiptId, "GENERIC BROWN RICE 6.49", ReceiptStatus.EXTRACTED)

        val memory = store.readMemory()
        val updatedInventory = memory.inventory.first { it.name == "Generic Greek Yogurt" }
        assertEquals(140, updatedInventory.calories)
        assertEquals("edited page", updatedInventory.notes)
        assertEquals("https://example.invalid/wonderfood/greek-yogurt.png", updatedInventory.imageUrl)
        assertTrue(memory.inventory.any { it.name == "Generic Brown Rice" })
        assertEquals("Generic Brown Rice", memory.groceries.single().name)
        assertEquals(GroceryStatus.BOUGHT, memory.groceries.single().status)
        assertEquals("Edited plan", memory.mealPlans.single().title)
        assertEquals(2, memory.mealPlanEntries.size)
        assertEquals("GENERIC BROWN RICE 6.49", memory.receipts.single().rawText)
    }

    @Test
    fun appliesCompositeRecipeAndMealPlanDraft() {
        store.applyDraft(
            CompositeDraft(
                drafts = listOf(
                    RecipeDraft(
                        titleText = "Tomato Peanut Curry",
                        ingredientsText = "Tomatoes, peanuts, spices",
                        stepsText = "Cook sauce. Simmer. Serve.",
                        servings = 2,
                        prepMinutes = 30,
                        tags = "chat-derived",
                    ),
                    MealPlanDraft(
                        titleText = "Tomorrow lunch",
                        daysText = "Lunch: Tomato Peanut Curry",
                        groceryHint = "tomatoes",
                        entries = listOf(MealPlanEntryDraft(1, MealSlot.LUNCH, "Tomato Peanut Curry", 620)),
                    ),
                ),
            ),
            sourceMessageId = null,
        )

        val memory = store.readMemory()
        assertEquals("Tomato Peanut Curry", memory.recipes.single().title)
        assertEquals("Tomato Peanut Curry", memory.mealPlanEntries.single().title)
        assertEquals(MealSlot.LUNCH, memory.mealPlanEntries.single().slot)
        assertEquals(1, memory.mealPlanEntries.single().dateEpochDay - requireNotNull(memory.mealPlans.single().startDateEpochDay))
    }

    @Test
    fun manualMealKeepsUnknownNutritionUnknown() {
        store.addMealLogManual("Unmeasured dinner", MealSlot.DINNER, java.time.LocalDate.now().toEpochDay())

        val meal = store.readMemory().mealLogs.single()
        assertNull(meal.calories)
        assertNull(meal.proteinGrams)
        assertNull(meal.carbsGrams)
        assertNull(meal.fatGrams)
    }

    @Test
    fun pantryAndGroceryDraftsDoNotInventMissingNutrition() {
        store.applyDraft(
            CompositeDraft(
                listOf(
                    InventoryDraft(listOf(FoodCandidate(name = "Unlabeled pantry item"))),
                    GroceryDraft(listOf(FoodCandidate(name = "Unlabeled grocery item"))),
                ),
            ),
            sourceMessageId = null,
        )

        val memory = store.readMemory()
        val inventory = memory.inventory.single()
        assertNull(inventory.calories)
        assertNull(inventory.proteinGrams)
        assertNull(inventory.carbsGrams)
        assertNull(inventory.fatGrams)
        assertTrue(inventory.nutritionSource.isBlank())
        val grocery = memory.groceries.single()
        assertNull(grocery.calories)
        assertNull(grocery.proteinGrams)
        assertNull(grocery.carbsGrams)
        assertNull(grocery.fatGrams)
        assertTrue(grocery.nutritionSource.isBlank())
    }

    @Test
    fun archivedObjectsCanBeRestored() {
        val id = store.addInventoryManual("Generic Beans", "2 cans", StorageZone.PANTRY)

        store.deleteInventory(id)
        assertTrue(store.readMemory().inventory.none { it.id == id })

        store.restoreInventory(id)
        assertEquals("Generic Beans", store.readMemory().inventory.single { it.id == id }.name)
    }

    @Test
    fun editingGroceryToBoughtReceivesItIntoKitchenOnce() {
        store.addGroceryManual("Generic Milk", "1 carton")
        val grocery = store.readMemory().groceries.single()

        store.updateGrocery(
            id = grocery.id,
            name = grocery.name,
            quantity = grocery.quantity,
            status = GroceryStatus.BOUGHT,
            category = grocery.category,
            servingText = grocery.servingText,
            calories = grocery.calories,
            proteinGrams = grocery.proteinGrams,
            carbsGrams = grocery.carbsGrams,
            fatGrams = grocery.fatGrams,
            nutritionSource = grocery.nutritionSource,
            source = "test",
            imageUri = grocery.imageUri,
            imageUrl = grocery.imageUrl,
        )

        assertEquals(1, store.readMemory().inventory.count { it.name == "Generic Milk" })
        store.markGroceryBought(grocery.id)
        assertEquals(1, store.readMemory().inventory.count { it.name == "Generic Milk" })
    }

    @Test
    fun eatenPlanEntryCreatesAndMaintainsOneMealLog() {
        val today = java.time.LocalDate.now().toEpochDay()
        store.addMealPlanEntry(today, MealSlot.LUNCH, "Generic Planned Bowl", 610)
        val entry = store.readMemory().mealPlanEntries.single()

        store.updateMealPlanEntry(entry.id, today, MealSlot.LUNCH, entry.title, 610, MealPlanEntryStatus.EATEN)
        assertEquals(1, store.readMemory().mealLogs.count { it.source == "plan_entry:${entry.id}" })

        store.updateMealPlanEntry(entry.id, today, MealSlot.LUNCH, entry.title, 610, MealPlanEntryStatus.PLANNED)
        assertTrue(store.readMemory().mealLogs.none { it.source == "plan_entry:${entry.id}" })

        store.updateMealPlanEntry(entry.id, today, MealSlot.LUNCH, entry.title, 610, MealPlanEntryStatus.EATEN)
        assertEquals(1, store.readMemory().mealLogs.count { it.source == "plan_entry:${entry.id}" })
    }

    @Test
    fun savesHealthAndAiPreferencesTogether() {
        val preferences = FoodPreferences(
            calorieGoal = "2100/day",
            proteinGoal = "140g/day",
            healthNotes = "Low sodium on rest days",
            customAiInstructions = "Prefer food already in the fridge",
            aiSkillOverride = "Always return editable proposals.",
        )

        store.savePreferences(preferences)

        assertEquals(preferences, store.readMemory().preferences)
    }

    @Test
    fun receiptAcceptancePutsAwayFoodAndExcludesHouseholdLines() {
        val executor = FoodDraftCommandExecutor { store }
        val result = executor.execute(
            FoodDraftCommand(
                draft = ReceiptDraft(
                    receiptId = 77,
                    merchant = "Generic Market",
                    storeLocation = "Main Street",
                    purchasedAtMillis = java.time.LocalDate.of(2026, 7, 17)
                        .atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli(),
                    currencyCode = "USD",
                    totalCents = 848,
                    items = listOf(
                        ReceiptItemDraft(
                            food = FoodCandidate(
                                name = "Mini Cucumbers",
                                quantity = "1 bag",
                                category = "vegetable",
                                evidence = "MINI CUCUMBERS 3.49",
                            ),
                            linePriceCents = 349,
                        ),
                        ReceiptItemDraft(
                            food = FoodCandidate(name = "Oven Cleaner Foam", category = "cleaning"),
                        ),
                    ),
                ),
                sourceMessageId = null,
                origin = FoodDraftCommandOrigin.RECEIPT,
            ),
        )

        assertTrue(result is FoodDraftExecutionResult.Applied)
        val memory = store.readMemory()
        assertEquals(listOf("Mini Cucumbers"), memory.inventory.map { it.name })
        val cucumber = memory.inventory.single()
        assertEquals(StorageZone.FRIDGE, cucumber.zone)
        assertEquals("produce", cucumber.category)
        assertTrue(cucumber.expiresAtMillis != null)
        assertTrue(cucumber.notes.contains("Receipt #77"))
        assertTrue(memory.groceries.isEmpty())
        assertEquals(349L, scalarLong("SELECT purchase_price_cents FROM inventory_batches"))
        assertEquals("USD", scalarText("SELECT currency_code FROM inventory_batches"))
        assertEquals("Generic Market — Main Street", scalarText("SELECT store_name FROM inventory_batches"))
        assertEquals(java.time.LocalDate.of(2026, 7, 17).toEpochDay(), scalarLong("SELECT purchase_date_epoch_day FROM inventory_batches"))
    }

    private fun scalarLong(sql: String, args: Array<String> = emptyArray()): Long =
        store.readableDatabase.rawQuery(sql, args).use { cursor ->
            assertTrue("Expected one long result for: $sql", cursor.moveToFirst())
            cursor.getLong(0)
        }

    private fun scalarDouble(sql: String, args: Array<String> = emptyArray()): Double =
        store.readableDatabase.rawQuery(sql, args).use { cursor ->
            assertTrue("Expected one double result for: $sql", cursor.moveToFirst())
            cursor.getDouble(0)
        }

    private fun scalarText(sql: String, args: Array<String> = emptyArray()): String =
        store.readableDatabase.rawQuery(sql, args).use { cursor ->
            assertTrue("Expected one text result for: $sql", cursor.moveToFirst())
            cursor.getString(0)
        }
}
