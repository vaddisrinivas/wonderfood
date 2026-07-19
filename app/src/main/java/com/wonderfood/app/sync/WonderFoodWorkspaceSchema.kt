package com.wonderfood.app.sync

import com.wonderfood.core.model.EntityType
import com.wonderfood.core.model.FoodUnit
import com.wonderfood.core.model.NutritionBasisType
import com.wonderfood.core.model.NutritionSnapshot
import com.wonderfood.core.model.Receipt
import com.wonderfood.core.model.Recipe
import com.wonderfood.core.model.RecipeIngredient
import com.wonderfood.core.model.StockLot
import com.wonderfood.core.model.WonderFoodSnapshot

internal object WonderFoodWorkspaceSchema {
    const val WORKSPACE_SCHEMA_VERSION = 3

    const val HOME = "Home"
    const val KITCHEN = "Kitchen"
    const val RECIPES = "Recipes"
    const val MEALS = "Meals"
    const val PLANS = "Plans"
    const val SHOPPING = "Shopping"
    const val PURCHASES = "Purchases"
    const val GOALS = "Goals"

    const val FOODS = "Foods"
    const val PRODUCTS = "Products"
    const val RECIPE_INGREDIENTS = "Recipe Ingredients"
    const val RECIPE_REVISIONS = "Recipe Revisions"
    const val INVENTORY_ACTIVITY = "Inventory Activity"
    const val SHOPPING_DEMAND = "Shopping Demand"
    const val PURCHASE_LINES = "Purchase Lines"
    const val NUTRITION_FACTS = "Nutrition Facts"
    const val MEMBERS = "Members"
    const val ACTIVITY = "Activity"
    const val WORKSPACE = "Workspace"

    val everydayTables: List<WorkspaceTable> = listOf(
        WorkspaceTable(
            title = HOME,
            titleField = "Metric",
            fields = listOf(
                WorkspaceField.title("Metric"),
                WorkspaceField("Value", "rich_text"),
                WorkspaceField("Status", "select"),
                WorkspaceField("Updated", "date"),
                WorkspaceField.identifier(),
            ),
        ),
        WorkspaceTable(
            title = KITCHEN,
            titleField = "Food",
            fields = listOf(
                WorkspaceField.title("Food"),
                WorkspaceField("On hand", "number"),
                WorkspaceField("Unit", "select"),
                WorkspaceField("Pantry state", "select"),
                WorkspaceField("Location", "select"),
                WorkspaceField("Best by", "date"),
                WorkspaceField("Food ID", "rich_text"),
                WorkspaceField("Product ID", "rich_text"),
                WorkspaceField("Lot ID", "rich_text"),
                WorkspaceField("Source", "rich_text"),
                WorkspaceField("Updated", "date"),
                WorkspaceField.identifier(),
            ),
        ),
        WorkspaceTable(
            title = RECIPES,
            titleField = "Recipe",
            fields = listOf(
                WorkspaceField.title("Recipe"),
                WorkspaceField("Recipe state", "select"),
                WorkspaceField("Servings", "number"),
                WorkspaceField("Prep", "number"),
                WorkspaceField("Cook", "number"),
                WorkspaceField("Ingredients", "rich_text"),
                WorkspaceField("Directions", "rich_text"),
                WorkspaceField("Source", "url"),
                WorkspaceField("Match notes", "rich_text"),
                WorkspaceField("Updated", "date"),
                WorkspaceField.identifier(),
            ),
        ),
        WorkspaceTable(
            title = MEALS,
            titleField = "Meal",
            fields = listOf(
                WorkspaceField.title("Meal"),
                WorkspaceField("When", "date"),
                WorkspaceField("Slot", "select"),
                WorkspaceField("Meal state", "select"),
                WorkspaceField("Recipe ID", "rich_text"),
                WorkspaceField("Food IDs", "rich_text"),
                WorkspaceField("Servings", "number"),
                WorkspaceField("Plan ID", "rich_text"),
                WorkspaceField("Notes", "rich_text"),
                WorkspaceField("Updated", "date"),
                WorkspaceField.identifier(),
            ),
        ),
        WorkspaceTable(
            title = PLANS,
            titleField = "Plan",
            fields = listOf(
                WorkspaceField.title("Plan"),
                WorkspaceField("Kind", "select"),
                WorkspaceField("Starts", "date"),
                WorkspaceField("Ends", "date"),
                WorkspaceField("Plan state", "select"),
                WorkspaceField("Meals", "number"),
                WorkspaceField("Servings", "number"),
                WorkspaceField("Notes", "rich_text"),
                WorkspaceField("Updated", "date"),
                WorkspaceField.identifier(),
            ),
        ),
        WorkspaceTable(
            title = SHOPPING,
            titleField = "Item",
            fields = listOf(
                WorkspaceField.title("Item"),
                WorkspaceField("Needed", "number"),
                WorkspaceField("Unit", "select"),
                WorkspaceField("Cart state", "select"),
                WorkspaceField("Food ID", "rich_text"),
                WorkspaceField("Recipe ID", "rich_text"),
                WorkspaceField("Reason", "rich_text"),
                WorkspaceField("Updated", "date"),
                WorkspaceField.identifier(),
            ),
        ),
        WorkspaceTable(
            title = PURCHASES,
            titleField = "Purchase",
            fields = listOf(
                WorkspaceField.title("Purchase"),
                WorkspaceField("Merchant", "rich_text"),
                WorkspaceField("Purchased", "date"),
                WorkspaceField("Subtotal", "number"),
                WorkspaceField("Total", "number"),
                WorkspaceField("Currency", "select"),
                WorkspaceField("Purchase state", "select"),
                WorkspaceField("Line count", "number"),
                WorkspaceField("Updated", "date"),
                WorkspaceField.identifier(),
            ),
        ),
        WorkspaceTable(
            title = GOALS,
            titleField = "Goal",
            fields = listOf(
                WorkspaceField.title("Goal"),
                WorkspaceField("Scope", "select"),
                WorkspaceField("Target", "number"),
                WorkspaceField("Unit", "select"),
                WorkspaceField("Period", "select"),
                WorkspaceField("Status", "select"),
                WorkspaceField("Updated", "date"),
                WorkspaceField.identifier(),
            ),
        ),
    )

    val managedTables: List<WorkspaceTable> = listOf(
        WorkspaceTable(
            title = FOODS,
            titleField = "Food",
            fields = listOf(
                WorkspaceField.title("Food"),
                WorkspaceField("Food state", "select"),
                WorkspaceField("Aliases", "rich_text"),
                WorkspaceField("Stock lots", "rich_text"),
                WorkspaceField("Nutrition profiles", "rich_text"),
                WorkspaceField("Source", "rich_text"),
                WorkspaceField("Confidence", "select"),
                WorkspaceField("Updated", "date"),
                WorkspaceField.identifier(),
            ),
        ),
        WorkspaceTable(
            title = PRODUCTS,
            titleField = "Product",
            fields = listOf(
                WorkspaceField.title("Product"),
                WorkspaceField("Food ID", "rich_text"),
                WorkspaceField("Brand", "rich_text"),
                WorkspaceField("Barcode", "rich_text"),
                WorkspaceField("Package size", "rich_text"),
                WorkspaceField("Source", "rich_text"),
                WorkspaceField("Updated", "date"),
                WorkspaceField.identifier(),
            ),
        ),
        WorkspaceTable(
            title = RECIPE_INGREDIENTS,
            titleField = "Ingredient",
            fields = listOf(
                WorkspaceField.title("Ingredient"),
                WorkspaceField("Recipe ID", "rich_text"),
                WorkspaceField("Food ID", "rich_text"),
                WorkspaceField("Amount", "number"),
                WorkspaceField("Unit", "select"),
                WorkspaceField("Preparation", "rich_text"),
                WorkspaceField("Optional", "checkbox"),
                WorkspaceField("Substitutes", "rich_text"),
                WorkspaceField("Match confidence", "select"),
                WorkspaceField("Updated", "date"),
                WorkspaceField.identifier(),
            ),
        ),
        WorkspaceTable(
            title = RECIPE_REVISIONS,
            titleField = "Revision",
            fields = listOf(
                WorkspaceField.title("Revision"),
                WorkspaceField("Recipe ID", "rich_text"),
                WorkspaceField("Revision number", "number"),
                WorkspaceField("Reason", "rich_text"),
                WorkspaceField("Content hash", "rich_text"),
                WorkspaceField("Created", "date"),
                WorkspaceField.identifier(),
            ),
        ),
        WorkspaceTable(
            title = INVENTORY_ACTIVITY,
            titleField = "Activity",
            fields = listOf(
                WorkspaceField.title("Activity"),
                WorkspaceField("Subject type", "select"),
                WorkspaceField("Subject ID", "rich_text"),
                WorkspaceField("Action", "select"),
                WorkspaceField("Quantity", "number"),
                WorkspaceField("Unit", "select"),
                WorkspaceField("When", "date"),
                WorkspaceField("Note", "rich_text"),
                WorkspaceField.identifier(),
            ),
        ),
        WorkspaceTable(
            title = SHOPPING_DEMAND,
            titleField = "Demand",
            fields = listOf(
                WorkspaceField.title("Demand"),
                WorkspaceField("Shopping item ID", "rich_text"),
                WorkspaceField("Source type", "select"),
                WorkspaceField("Source ID", "rich_text"),
                WorkspaceField("Food ID", "rich_text"),
                WorkspaceField("Quantity", "number"),
                WorkspaceField("Unit", "select"),
                WorkspaceField("Reason", "rich_text"),
                WorkspaceField("Status", "select"),
                WorkspaceField.identifier(),
            ),
        ),
        WorkspaceTable(
            title = PURCHASE_LINES,
            titleField = "Line",
            fields = listOf(
                WorkspaceField.title("Line"),
                WorkspaceField("Purchase ID", "rich_text"),
                WorkspaceField("Shopping item ID", "rich_text"),
                WorkspaceField("Food ID", "rich_text"),
                WorkspaceField("Quantity", "number"),
                WorkspaceField("Unit", "select"),
                WorkspaceField("Line total", "number"),
                WorkspaceField("Currency", "select"),
                WorkspaceField.identifier(),
            ),
        ),
        WorkspaceTable(
            title = NUTRITION_FACTS,
            titleField = "Profile",
            fields = listOf(
                WorkspaceField.title("Profile"),
                WorkspaceField("Subject type", "select"),
                WorkspaceField("Subject ID", "rich_text"),
                WorkspaceField("Basis", "select"),
                WorkspaceField("Basis quantity", "number"),
                WorkspaceField("Basis unit", "select"),
                WorkspaceField("Calories", "number"),
                WorkspaceField("Protein g", "number"),
                WorkspaceField("Carbs g", "number"),
                WorkspaceField("Fat g", "number"),
                WorkspaceField("Fiber g", "number"),
                WorkspaceField("Sugar g", "number"),
                WorkspaceField("Sodium mg", "number"),
                WorkspaceField("Source", "rich_text"),
                WorkspaceField("Confidence", "select"),
                WorkspaceField("Updated", "date"),
                WorkspaceField.identifier(),
            ),
        ),
        WorkspaceTable(
            title = MEMBERS,
            titleField = "Member",
            fields = listOf(
                WorkspaceField.title("Member"),
                WorkspaceField("Role", "select"),
                WorkspaceField("Timezone", "rich_text"),
                WorkspaceField("Status", "select"),
                WorkspaceField("Updated", "date"),
                WorkspaceField.identifier(),
            ),
        ),
        WorkspaceTable(
            title = ACTIVITY,
            titleField = "Event",
            fields = listOf(
                WorkspaceField.title("Event"),
                WorkspaceField("Actor", "rich_text"),
                WorkspaceField("Origin", "select"),
                WorkspaceField("Action", "select"),
                WorkspaceField("Subject type", "select"),
                WorkspaceField("Subject ID", "rich_text"),
                WorkspaceField("When", "date"),
                WorkspaceField("Patch", "rich_text"),
                WorkspaceField.identifier(),
            ),
        ),
        WorkspaceTable(
            title = WORKSPACE,
            titleField = "Setting",
            fields = listOf(
                WorkspaceField.title("Setting"),
                WorkspaceField("Value", "rich_text"),
                WorkspaceField("Updated", "date"),
                WorkspaceField.identifier(),
            ),
        ),
    )

    val tables: List<WorkspaceTable> = everydayTables + managedTables

    fun rows(snapshot: WonderFoodSnapshot, updatedAt: String): Map<String, List<WorkspaceRow>> {
        require(updatedAt.isNotBlank()) { "Workspace updated timestamp must not be blank." }
        val pagesById = snapshot.pages.associateBy { it.id.value }
        val foodsById = snapshot.foods.associateBy { it.id.value }
        val recipesById = snapshot.recipes.associateBy { it.id.value }
        val nutritionById = snapshot.nutritionSnapshots.associateBy { it.id.value }
        val lotsByFoodId = snapshot.stockLots.groupBy { it.foodId.value }
        val aliasesByFoodId = snapshot.foodAliases.groupBy { it.foodId.value }

        val kitchen = snapshot.stockLots.map { lot ->
            val food = foodsById[lot.foodId.value]
            row(
                tableTitle = KITCHEN,
                identifier = lot.id.value,
                values = linkedMapOf(
                    "Food" to (food?.name ?: lot.foodId.value),
                    "On hand" to lot.quantity.amount,
                    "Unit" to lot.quantity.unit.workspaceName(),
                    "Pantry state" to lot.status.workspaceName(),
                    "Location" to lot.location.orEmpty(),
                    "Best by" to lot.expiresOn?.value,
                    "Food ID" to lot.foodId.value,
                    "Product ID" to "",
                    "Lot ID" to lot.id.value,
                    "Source" to lot.source.label,
                    "Updated" to updatedAt,
                ),
            )
        }
        val recipes = snapshot.recipes.map { recipe ->
            row(
                tableTitle = RECIPES,
                identifier = recipe.id.value,
                values = linkedMapOf(
                    "Recipe" to recipe.title,
                    "Recipe state" to recipe.status.workspaceName(),
                    "Servings" to recipe.servings.amount,
                    "Prep" to recipe.prepMinutes,
                    "Cook" to recipe.cookMinutes,
                    "Ingredients" to recipe.ingredients.joinToString(separator = "\n") { it.humanLine(foodsById) },
                    "Directions" to recipe.steps.sortedBy { it.order }.joinToString(separator = "\n") { "${it.order + 1}. ${it.instruction}" },
                    "Source" to recipe.source.uri.orEmpty(),
                    "Match notes" to recipe.matchNotes(snapshot.stockLots),
                    "Updated" to updatedAt,
                ),
            )
        }
        val mealRows = snapshot.mealPlans.flatMap { plan ->
            plan.entries.map { entry ->
                val recipe = entry.recipeId?.value?.let { recipesById[it] }
                val food = entry.foodId?.value?.let { foodsById[it] }
                row(
                    tableTitle = MEALS,
                    identifier = entry.id.value,
                    values = linkedMapOf(
                        "Meal" to (recipe?.title ?: food?.name ?: entry.mealSlot.workspaceName()),
                        "When" to entry.date.value,
                        "Slot" to entry.mealSlot.workspaceName(),
                        "Meal state" to entry.status.workspaceName(),
                        "Recipe ID" to entry.recipeId?.value.orEmpty(),
                        "Food IDs" to entry.foodId?.value.orEmpty(),
                        "Servings" to entry.quantity.amount,
                        "Plan ID" to plan.id.value,
                        "Notes" to "Planned from ${plan.name}",
                        "Updated" to updatedAt,
                    ),
                )
            }
        } + snapshot.mealLogs.map { log ->
            row(
                tableTitle = MEALS,
                identifier = log.id.value,
                values = linkedMapOf(
                    "Meal" to mealLogName(log.foodIds.mapNotNull { foodsById[it.value]?.name }, log.recipeIds.mapNotNull { recipesById[it.value]?.title }),
                    "When" to log.occurredAt.value,
                    "Slot" to log.mealSlot.workspaceName(),
                    "Meal state" to log.status.workspaceName(),
                    "Recipe ID" to log.recipeIds.joinToString { it.value },
                    "Food IDs" to log.foodIds.joinToString { it.value },
                    "Servings" to null,
                    "Plan ID" to log.planEntryId?.value.orEmpty(),
                    "Notes" to "Logged meal",
                    "Updated" to updatedAt,
                ),
            )
        }
        val plans = snapshot.mealPlans.map { plan ->
            row(
                tableTitle = PLANS,
                identifier = plan.id.value,
                values = linkedMapOf(
                    "Plan" to plan.name,
                    "Kind" to "Scheduled",
                    "Starts" to plan.startsOn.value,
                    "Ends" to plan.endsOn.value,
                    "Plan state" to plan.status.workspaceName(),
                    "Meals" to plan.entries.size,
                    "Servings" to plan.entries.mapNotNull { it.quantity.amount }.sum().takeIf { it > 0.0 },
                    "Notes" to "",
                    "Updated" to updatedAt,
                ),
            )
        }
        val shopping = snapshot.shoppingItems.map { item ->
            val label = item.foodId?.value?.let { foodsById[it]?.name }
                ?: pagesById[item.pageId.value]?.title
                ?: item.reason
                ?: item.id.value
            row(
                tableTitle = SHOPPING,
                identifier = item.id.value,
                values = linkedMapOf(
                    "Item" to label,
                    "Needed" to item.quantity.amount,
                    "Unit" to item.quantity.unit.workspaceName(),
                    "Cart state" to item.status.workspaceName(),
                    "Food ID" to item.foodId?.value.orEmpty(),
                    "Recipe ID" to item.recipeId?.value.orEmpty(),
                    "Reason" to item.reason.orEmpty(),
                    "Updated" to updatedAt,
                ),
            )
        }
        val purchases = snapshot.receipts.map { receipt ->
            row(
                tableTitle = PURCHASES,
                identifier = receipt.id.value,
                values = linkedMapOf(
                    "Purchase" to receipt.displayName(pagesById[receipt.pageId.value]?.title),
                    "Merchant" to receipt.merchantName.orEmpty(),
                    "Purchased" to receipt.purchasedAt?.value,
                    "Subtotal" to receipt.subtotal?.amount,
                    "Total" to receipt.total?.amount,
                    "Currency" to (receipt.total?.currencyCode ?: receipt.subtotal?.currencyCode ?: ""),
                    "Purchase state" to receipt.status.workspaceName(),
                    "Line count" to receipt.itemIds.size,
                    "Updated" to updatedAt,
                ),
            )
        }
        val foods = snapshot.foods.map { food ->
            row(
                tableTitle = FOODS,
                identifier = food.id.value,
                values = linkedMapOf(
                    "Food" to food.name,
                    "Food state" to food.status.workspaceName(),
                    "Aliases" to aliasesByFoodId[food.id.value].orEmpty().joinToString { it.name },
                    "Stock lots" to lotsByFoodId[food.id.value].orEmpty().joinToString { it.id.value },
                    "Nutrition profiles" to food.nutritionSnapshotIds.joinToString { it.value },
                    "Source" to food.source.label,
                    "Confidence" to food.confidence.state.workspaceName(),
                    "Updated" to updatedAt,
                ),
            )
        }
        val recipeIngredients = snapshot.recipes.flatMap { recipe ->
            recipe.ingredients.map { ingredient ->
                row(
                    tableTitle = RECIPE_INGREDIENTS,
                    identifier = ingredient.id.value,
                    values = linkedMapOf(
                        "Ingredient" to ingredient.humanLine(foodsById),
                        "Recipe ID" to recipe.id.value,
                        "Food ID" to ingredient.foodId?.value.orEmpty(),
                        "Amount" to ingredient.quantity.amount,
                        "Unit" to ingredient.quantity.unit.workspaceName(),
                        "Preparation" to ingredient.preparation.orEmpty(),
                        "Optional" to ingredient.optional,
                        "Substitutes" to ingredient.substituteFoodIds.joinToString { it.value },
                        "Match confidence" to ingredient.confidence.state.workspaceName(),
                        "Updated" to updatedAt,
                    ),
                )
            }
        }
        val recipeRevisions = snapshot.recipes.map { recipe ->
            row(
                tableTitle = RECIPE_REVISIONS,
                identifier = "revision:${recipe.id.value}:current",
                values = linkedMapOf(
                    "Revision" to "${recipe.title} current",
                    "Recipe ID" to recipe.id.value,
                    "Revision number" to 1,
                    "Reason" to "Current imported household recipe",
                    "Content hash" to recipe.contentFingerprint(),
                    "Created" to updatedAt,
                ),
            )
        }
        val inventoryActivity = snapshot.foodEvents
            .filter { it.subject.type == EntityType.STOCK_LOT || it.subject.type == EntityType.FOOD }
            .map { event ->
                row(
                    tableTitle = INVENTORY_ACTIVITY,
                    identifier = event.id.value,
                    values = linkedMapOf(
                        "Activity" to event.type.workspaceName(),
                        "Subject type" to event.subject.type.workspaceName(),
                        "Subject ID" to event.subject.id,
                        "Action" to event.type.workspaceName(),
                        "Quantity" to event.quantity?.amount,
                        "Unit" to event.quantity?.unit?.workspaceName().orEmpty(),
                        "When" to event.occurredAt.value,
                        "Note" to event.note.orEmpty(),
                    ),
                )
            }
        val shoppingDemand = snapshot.shoppingItems.map { item ->
            row(
                tableTitle = SHOPPING_DEMAND,
                identifier = "demand:${item.id.value}",
                values = linkedMapOf(
                    "Demand" to (item.reason ?: item.id.value),
                    "Shopping item ID" to item.id.value,
                    "Source type" to if (item.recipeId != null) "Recipe" else "Manual",
                    "Source ID" to item.recipeId?.value.orEmpty(),
                    "Food ID" to item.foodId?.value.orEmpty(),
                    "Quantity" to item.quantity.amount,
                    "Unit" to item.quantity.unit.workspaceName(),
                    "Reason" to item.reason.orEmpty(),
                    "Status" to item.status.workspaceName(),
                ),
            )
        }
        val purchaseLines = snapshot.receipts.flatMap { receipt ->
            receipt.itemIds.mapIndexed { index, itemId ->
                val shoppingItem = snapshot.shoppingItems.firstOrNull { it.id == itemId }
                row(
                    tableTitle = PURCHASE_LINES,
                    identifier = "purchase_line:${receipt.id.value}:$index",
                    values = linkedMapOf(
                        "Line" to (shoppingItem?.let { it.foodId?.value?.let { id -> foodsById[id]?.name } ?: it.reason } ?: itemId.value),
                        "Purchase ID" to receipt.id.value,
                        "Shopping item ID" to itemId.value,
                        "Food ID" to shoppingItem?.foodId?.value.orEmpty(),
                        "Quantity" to shoppingItem?.quantity?.amount,
                        "Unit" to shoppingItem?.quantity?.unit?.workspaceName().orEmpty(),
                        "Line total" to null,
                        "Currency" to (receipt.total?.currencyCode ?: receipt.subtotal?.currencyCode ?: ""),
                    ),
                )
            }
        }
        val nutritionFacts = snapshot.nutritionSnapshots.map { nutrition ->
            row(
                tableTitle = NUTRITION_FACTS,
                identifier = nutrition.id.value,
                values = nutrition.toWorkspaceValues(updatedAt),
            )
        }
        val activity = snapshot.foodEvents.map { event ->
            row(
                tableTitle = ACTIVITY,
                identifier = event.id.value,
                values = linkedMapOf(
                    "Event" to event.type.workspaceName(),
                    "Actor" to event.source.label,
                    "Origin" to event.source.kind.workspaceName(),
                    "Action" to event.type.workspaceName(),
                    "Subject type" to event.subject.type.workspaceName(),
                    "Subject ID" to event.subject.id,
                    "When" to event.occurredAt.value,
                    "Patch" to event.note.orEmpty(),
                ),
            )
        }
        val workspace = listOf(
            row(WORKSPACE, "workspace:schema_version", linkedMapOf("Setting" to "Schema version", "Value" to WORKSPACE_SCHEMA_VERSION.toString(), "Updated" to updatedAt)),
            row(WORKSPACE, "workspace:snapshot_schema_version", linkedMapOf("Setting" to "Snapshot schema version", "Value" to snapshot.schemaVersion.toString(), "Updated" to updatedAt)),
            row(WORKSPACE, "workspace:authority", linkedMapOf("Setting" to "Authority", "Value" to "Selected in WonderFood", "Updated" to updatedAt)),
            row(WORKSPACE, "workspace:last_sync", linkedMapOf("Setting" to "Last sync", "Value" to updatedAt, "Updated" to updatedAt)),
        )
        val home = listOf(
            homeRow("home-kitchen-items", "Kitchen items", kitchen.size.toString(), "Current", updatedAt),
            homeRow("home-use-soon", "Use soon", kitchen.count { it.values["Best by"]?.toString()?.isNotBlank() == true }.toString(), "Current", updatedAt),
            homeRow("home-shopping-items", "Shopping items", shopping.size.toString(), "Current", updatedAt),
            homeRow("home-recipes", "Recipes", recipes.size.toString(), "Current", updatedAt),
            homeRow("home-planned-meals", "Planned meals", mealRows.count { it.values["Meal state"] != "Confirmed" }.toString(), "Current", updatedAt),
            homeRow("home-spending-total", "Spending total", "=SUM(Purchases!E2:E)", "Formula", updatedAt),
            homeRow("home-nutrition-profiles", "Nutrition profiles", nutritionById.size.toString(), "Current", updatedAt),
            homeRow("home-last-sync", "Last sync", updatedAt, "Current", updatedAt),
        )

        return tables.associate { table ->
            table.title to when (table.title) {
                HOME -> home
                KITCHEN -> kitchen
                RECIPES -> recipes
                MEALS -> mealRows
                PLANS -> plans
                SHOPPING -> shopping
                PURCHASES -> purchases
                GOALS -> emptyList()
                FOODS -> foods
                PRODUCTS -> emptyList()
                RECIPE_INGREDIENTS -> recipeIngredients
                RECIPE_REVISIONS -> recipeRevisions
                INVENTORY_ACTIVITY -> inventoryActivity
                SHOPPING_DEMAND -> shoppingDemand
                PURCHASE_LINES -> purchaseLines
                NUTRITION_FACTS -> nutritionFacts
                MEMBERS -> emptyList()
                ACTIVITY -> activity
                WORKSPACE -> workspace
                else -> emptyList()
            }
        }
    }

    private fun row(tableTitle: String, identifier: String, values: LinkedHashMap<String, Any?>): WorkspaceRow =
        WorkspaceRow(
            tableTitle = tableTitle,
            identifier = identifier,
            values = values.apply { put("identifier", identifier) },
        )

    private fun homeRow(identifier: String, metric: String, value: String, status: String, updatedAt: String): WorkspaceRow =
        row(
            tableTitle = HOME,
            identifier = identifier,
            values = linkedMapOf(
                "Metric" to metric,
                "Value" to value,
                "Status" to status,
                "Updated" to updatedAt,
            ),
        )

    private fun RecipeIngredient.humanLine(foodsById: Map<String, com.wonderfood.core.model.Food>): String {
        val amount = quantity.amount?.let { trimNumber(it) }.orEmpty()
        val unit = quantity.unit.takeUnless { it == FoodUnit.UNKNOWN }?.workspaceName().orEmpty()
        val foodName = foodId?.value?.let { foodsById[it]?.name } ?: displayName
        val prep = preparation?.takeIf { it.isNotBlank() }?.let { ", $it" }.orEmpty()
        return listOf(amount, unit, foodName).filter { it.isNotBlank() }.joinToString(" ") + prep
    }

    private fun Recipe.matchNotes(lots: List<StockLot>): String {
        val activeFoodIds = lots.filter { it.quantity.amount != 0.0 }.map { it.foodId }.toSet()
        val matched = ingredients.count { it.foodId != null && it.foodId in activeFoodIds }
        if (ingredients.isEmpty()) return "No structured ingredients yet"
        return "$matched of ${ingredients.size} matched in Kitchen"
    }

    private fun Recipe.contentFingerprint(): String =
        listOf(
            id.value,
            title,
            ingredients.joinToString("|") { it.displayName },
            steps.joinToString("|") { it.instruction },
        ).joinToString("::").hashCode().toUInt().toString(16)

    private fun Receipt.displayName(pageTitle: String?): String =
        merchantName?.takeIf { it.isNotBlank() } ?: pageTitle?.takeIf { it.isNotBlank() } ?: purchasedAt?.value ?: id.value

    private fun NutritionSnapshot.toWorkspaceValues(updatedAt: String): LinkedHashMap<String, Any?> =
        linkedMapOf(
            "Profile" to "${subject.type.workspaceName()} nutrition",
            "Subject type" to subject.type.workspaceName(),
            "Subject ID" to subject.id,
            "Basis" to basis.type.workspaceName(),
            "Basis quantity" to basis.quantity.amount,
            "Basis unit" to basis.quantity.unit.workspaceName(),
            "Calories" to values.energyKcal,
            "Protein g" to values.proteinGrams,
            "Carbs g" to values.carbohydrateGrams,
            "Fat g" to values.fatGrams,
            "Fiber g" to values.fiberGrams,
            "Sugar g" to values.sugarGrams,
            "Sodium mg" to values.sodiumMilligrams,
            "Source" to source.label,
            "Confidence" to confidence.state.workspaceName(),
            "Updated" to (capturedAt?.value ?: updatedAt),
        )

    private fun mealLogName(foodNames: List<String>, recipeNames: List<String>): String =
        (recipeNames + foodNames).takeIf { it.isNotEmpty() }?.joinToString() ?: "Logged meal"

    private fun Enum<*>.workspaceName(): String =
        name.lowercase().split("_").joinToString(" ") { word -> word.replaceFirstChar { it.titlecase() } }

    private fun FoodUnit.workspaceName(): String = when (this) {
        FoodUnit.EACH -> "item"
        FoodUnit.GRAM -> "g"
        FoodUnit.KILOGRAM -> "kg"
        FoodUnit.MILLILITER -> "mL"
        FoodUnit.LITER -> "L"
        FoodUnit.TABLESPOON -> "tbsp"
        FoodUnit.TEASPOON -> "tsp"
        FoodUnit.OUNCE -> "oz"
        FoodUnit.POUND -> "lb"
        FoodUnit.SERVING -> "serving"
        FoodUnit.PACKAGE -> "package"
        FoodUnit.CAN -> "can"
        FoodUnit.BOTTLE -> "bottle"
        FoodUnit.SLICE -> "slice"
        FoodUnit.PINCH -> "pinch"
        FoodUnit.CUP -> "cup"
        FoodUnit.UNKNOWN -> ""
    }

    private fun NutritionBasisType.workspaceName(): String = when (this) {
        NutritionBasisType.PER_100_GRAMS -> "per 100 g"
        NutritionBasisType.PER_SERVING -> "per serving"
        NutritionBasisType.PER_CONTAINER -> "per container"
        NutritionBasisType.PER_RECIPE -> "per recipe"
        NutritionBasisType.PER_PORTION -> "per portion"
        NutritionBasisType.UNKNOWN -> ""
    }

    private fun trimNumber(value: Double): String =
        if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()
}

internal data class WorkspaceTable(
    val title: String,
    val titleField: String,
    val fields: List<WorkspaceField>,
)

internal data class WorkspaceField(
    val name: String,
    val notionType: String,
) {
    companion object {
        fun title(name: String): WorkspaceField = WorkspaceField(name, "title")
        fun identifier(): WorkspaceField = WorkspaceField("identifier", "rich_text")
    }
}

internal data class WorkspaceRow(
    val tableTitle: String,
    val identifier: String,
    val values: Map<String, Any?>,
)
