package com.wonderfood.app.sync

import com.wonderfood.core.model.household.CalendarDate
import com.wonderfood.core.model.household.EntityId
import com.wonderfood.core.model.household.EntityMetadata
import com.wonderfood.core.model.household.HouseholdSnapshot
import com.wonderfood.core.model.household.InventoryLot
import com.wonderfood.core.model.household.InventoryLotStatus
import com.wonderfood.core.model.household.Item
import com.wonderfood.core.model.household.Money
import com.wonderfood.core.model.household.Quantity
import com.wonderfood.core.model.household.QuantityUnit
import com.wonderfood.core.model.household.UtcTimestamp
import java.math.BigDecimal
import java.math.MathContext
import java.util.Currency

internal const val WORKSPACE_GRAPH_SCHEMA_VERSION = 4

enum class WorkspaceGraphSurface(
    val key: String,
    val label: String,
    val visibleInSheets: Boolean,
    val primary: Boolean,
) {
    HOME("home", "Home", true, true),
    KITCHEN("kitchen", "Kitchen", true, true),
    SHOPPING("shopping", "Shopping", true, true),
    MEALS("meals", "Meals", true, true),
    RECIPES("recipes", "Recipes", true, true),
    INGREDIENTS("ingredients", "Ingredients", true, false),
    SPENDING("spending", "Spending", true, true),
    PURCHASE_LINES("purchase_lines", "Purchase Lines", true, false),
    LISTS_HELP("lists_help", "Lists & Help", true, true),
    STOCK_LOTS("stock_lots", "Stock Lots", false, false),
    BINDINGS("bindings", "Bindings", false, false),
    NEEDS_REVIEW("needs_review", "Needs Review", false, false),
    SYSTEM("system", "WonderFood System", false, false),
}

internal enum class WorkspaceGraphValueType {
    TITLE, TEXT, LONG_TEXT, DECIMAL, MONEY, DATE, DATE_TIME, BOOLEAN, SELECT, MULTI_SELECT, URL, RELATION, COMPUTED,
}

internal enum class WorkspaceGraphFieldOwner { HUMAN, SHARED, APP_DERIVED, SYSTEM }
internal enum class WorkspaceGraphConflictRisk { LOW, HIGH }

internal data class WorkspaceGraphField(
    val key: String,
    val label: String,
    val type: WorkspaceGraphValueType,
    val owner: WorkspaceGraphFieldOwner,
    val risk: WorkspaceGraphConflictRisk,
    val relationTarget: WorkspaceGraphSurface? = null,
    val formulaKey: String? = null,
    val hidden: Boolean = false,
)

internal data class WorkspaceGraphSurfaceSchema(
    val surface: WorkspaceGraphSurface,
    val fields: List<WorkspaceGraphField>,
) {
    val titleField: WorkspaceGraphField = fields.single { it.type == WorkspaceGraphValueType.TITLE }
}

internal sealed interface WorkspaceGraphValue {
    data class Text(val value: String) : WorkspaceGraphValue
    data class Decimal(val value: BigDecimal) : WorkspaceGraphValue
    data class MoneyValue(val majorUnits: BigDecimal, val currencyCode: String) : WorkspaceGraphValue
    data class Date(val value: String) : WorkspaceGraphValue
    data class DateTime(val epochMillis: Long) : WorkspaceGraphValue
    data class BooleanValue(val value: Boolean) : WorkspaceGraphValue
    data class TextList(val values: List<String>) : WorkspaceGraphValue
    data class Relation(val target: WorkspaceGraphSurface, val canonicalIds: List<String>) : WorkspaceGraphValue
    data class Computed(val formulaKey: String) : WorkspaceGraphValue
}

internal data class WorkspaceGraphRow(
    val surface: WorkspaceGraphSurface,
    val canonicalId: String,
    val revision: Long,
    val archived: Boolean,
    val updatedAt: Long,
    val values: Map<String, WorkspaceGraphValue>,
)

internal data class WorkspaceGraphProjection(
    val schemaVersion: Int,
    val householdId: String,
    val defaultCurrency: String,
    val timezone: String,
    val locale: String,
    val schemas: List<WorkspaceGraphSurfaceSchema>,
    val rows: Map<WorkspaceGraphSurface, List<WorkspaceGraphRow>>,
) {
    init { require(schemaVersion == WORKSPACE_GRAPH_SCHEMA_VERSION) }
}

internal object WorkspaceGraphContract {
    val supportedUnits: List<String> = QuantityUnit.SUPPORTED.map { it.code }

    val schemas: List<WorkspaceGraphSurfaceSchema> = listOf(
        schema(WorkspaceGraphSurface.HOME, field("dashboard", "Dashboard", WorkspaceGraphValueType.TITLE)),
        schema(
            WorkspaceGraphSurface.KITCHEN,
            field("item", "Item", WorkspaceGraphValueType.TITLE),
            field("kind", "Kind", WorkspaceGraphValueType.SELECT), field("category", "Category", WorkspaceGraphValueType.SELECT),
            field("on_hand", "On hand", WorkspaceGraphValueType.DECIMAL, high = true), field("unit", "Unit", WorkspaceGraphValueType.SELECT, high = true),
            field("location", "Location", WorkspaceGraphValueType.SELECT), field("best_before", "Best before", WorkspaceGraphValueType.DATE),
            field("opened", "Opened", WorkspaceGraphValueType.BOOLEAN), field("low_at", "Low at", WorkspaceGraphValueType.DECIMAL, high = true),
            field("low_stock", "Low stock", WorkspaceGraphValueType.COMPUTED, owner = WorkspaceGraphFieldOwner.APP_DERIVED, formula = "kitchen_low_stock"),
            field("buy_next", "Buy next", WorkspaceGraphValueType.BOOLEAN, owner = WorkspaceGraphFieldOwner.HUMAN),
            field("buy_quantity", "Buy quantity", WorkspaceGraphValueType.DECIMAL, owner = WorkspaceGraphFieldOwner.HUMAN, high = true),
            field("preferred_store", "Preferred store", WorkspaceGraphValueType.TEXT), field("notes", "Notes", WorkspaceGraphValueType.LONG_TEXT),
            field("archived", "Archived", WorkspaceGraphValueType.BOOLEAN, high = true),
            relation("ingredients", "Ingredients", WorkspaceGraphSurface.INGREDIENTS), relation("shopping", "Shopping", WorkspaceGraphSurface.SHOPPING),
            relation("stock_lots", "Stock lots", WorkspaceGraphSurface.STOCK_LOTS),
        ),
        schema(
            WorkspaceGraphSurface.SHOPPING,
            field("item", "Item", WorkspaceGraphValueType.TITLE), relation("kitchen_item", "Kitchen item", WorkspaceGraphSurface.KITCHEN, high = true),
            field("amount", "Amount", WorkspaceGraphValueType.DECIMAL, high = true), field("unit", "Unit", WorkspaceGraphValueType.SELECT, high = true),
            field("category", "Category", WorkspaceGraphValueType.SELECT), field("store", "Store", WorkspaceGraphValueType.TEXT),
            field("status", "Status", WorkspaceGraphValueType.SELECT, high = true), field("reason", "Reason", WorkspaceGraphValueType.SELECT),
            relation("needed_for_recipes", "Needed for recipes", WorkspaceGraphSurface.RECIPES, high = true),
            relation("needed_for_meals", "Needed for meals", WorkspaceGraphSurface.MEALS, high = true),
            field("on_hand", "On hand", WorkspaceGraphValueType.COMPUTED, owner = WorkspaceGraphFieldOwner.APP_DERIVED, formula = "shopping_on_hand"),
            field("kitchen_unit", "Kitchen unit", WorkspaceGraphValueType.COMPUTED, owner = WorkspaceGraphFieldOwner.APP_DERIVED, formula = "shopping_kitchen_unit"),
            field("still_needed", "Still needed", WorkspaceGraphValueType.COMPUTED, owner = WorkspaceGraphFieldOwner.APP_DERIVED, formula = "shopping_still_needed"),
            field("estimated_price", "Estimated price", WorkspaceGraphValueType.MONEY, owner = WorkspaceGraphFieldOwner.APP_DERIVED),
            field("notes", "Notes", WorkspaceGraphValueType.LONG_TEXT), field("archived", "Archived", WorkspaceGraphValueType.BOOLEAN, high = true),
        ),
        schema(
            WorkspaceGraphSurface.MEALS,
            field("meal", "Meal", WorkspaceGraphValueType.TITLE), field("scheduled_at", "Date", WorkspaceGraphValueType.DATE_TIME, high = true),
            field("slot", "Meal slot", WorkspaceGraphValueType.SELECT, high = true), relation("recipe", "Recipe", WorkspaceGraphSurface.RECIPES, high = true),
            field("servings", "Servings", WorkspaceGraphValueType.DECIMAL, high = true), field("status", "Status", WorkspaceGraphValueType.SELECT, high = true),
            field("leftovers", "Leftovers", WorkspaceGraphValueType.TEXT, high = true), field("people", "People", WorkspaceGraphValueType.MULTI_SELECT),
            field("notes", "Notes", WorkspaceGraphValueType.LONG_TEXT), field("archived", "Archived", WorkspaceGraphValueType.BOOLEAN, high = true),
            field("recipe_readiness", "Recipe readiness", WorkspaceGraphValueType.COMPUTED, owner = WorkspaceGraphFieldOwner.APP_DERIVED, formula = "meal_recipe_readiness"),
            field("missing_items", "Missing items", WorkspaceGraphValueType.COMPUTED, owner = WorkspaceGraphFieldOwner.APP_DERIVED, formula = "meal_missing_items"),
        ),
        schema(
            WorkspaceGraphSurface.RECIPES,
            field("recipe", "Recipe", WorkspaceGraphValueType.TITLE), field("source_url", "Source", WorkspaceGraphValueType.URL),
            field("cuisine", "Cuisine", WorkspaceGraphValueType.SELECT), field("tags", "Tags", WorkspaceGraphValueType.MULTI_SELECT),
            field("servings", "Servings", WorkspaceGraphValueType.DECIMAL, high = true), field("prep_minutes", "Prep minutes", WorkspaceGraphValueType.DECIMAL),
            field("cook_minutes", "Cook minutes", WorkspaceGraphValueType.DECIMAL), field("instructions", "Instructions", WorkspaceGraphValueType.LONG_TEXT, high = true),
            field("favorite", "Favorite", WorkspaceGraphValueType.BOOLEAN), field("archived", "Archived", WorkspaceGraphValueType.BOOLEAN, high = true),
            relation("ingredients", "Ingredients", WorkspaceGraphSurface.INGREDIENTS, high = true),
            field("ingredient_count", "Ingredient count", WorkspaceGraphValueType.COMPUTED, owner = WorkspaceGraphFieldOwner.APP_DERIVED, formula = "recipe_ingredient_count"),
            field("ready_count", "Ready count", WorkspaceGraphValueType.COMPUTED, owner = WorkspaceGraphFieldOwner.APP_DERIVED, formula = "recipe_ready_count"),
            field("can_make_percent", "Can make %", WorkspaceGraphValueType.COMPUTED, owner = WorkspaceGraphFieldOwner.APP_DERIVED, formula = "recipe_can_make_percent"),
            field("missing_items", "Missing items", WorkspaceGraphValueType.COMPUTED, owner = WorkspaceGraphFieldOwner.APP_DERIVED, formula = "recipe_missing_items"),
        ),
        schema(
            WorkspaceGraphSurface.INGREDIENTS,
            field("ingredient", "Ingredient", WorkspaceGraphValueType.TITLE), relation("recipe", "Recipe", WorkspaceGraphSurface.RECIPES, high = true),
            relation("kitchen_item", "Kitchen item", WorkspaceGraphSurface.KITCHEN, high = true), field("amount", "Amount", WorkspaceGraphValueType.DECIMAL, high = true),
            field("unit", "Unit", WorkspaceGraphValueType.SELECT, high = true), field("preparation", "Preparation", WorkspaceGraphValueType.TEXT),
            field("optional", "Optional", WorkspaceGraphValueType.BOOLEAN), field("notes", "Notes", WorkspaceGraphValueType.LONG_TEXT),
            field("on_hand", "On hand", WorkspaceGraphValueType.COMPUTED, owner = WorkspaceGraphFieldOwner.APP_DERIVED, formula = "ingredient_on_hand"),
            field("kitchen_unit", "Kitchen unit", WorkspaceGraphValueType.COMPUTED, owner = WorkspaceGraphFieldOwner.APP_DERIVED, formula = "ingredient_kitchen_unit"),
            field("missing_amount", "Missing amount", WorkspaceGraphValueType.COMPUTED, owner = WorkspaceGraphFieldOwner.APP_DERIVED, formula = "ingredient_missing_amount"),
            field("status", "Status", WorkspaceGraphValueType.COMPUTED, owner = WorkspaceGraphFieldOwner.APP_DERIVED, formula = "ingredient_status"),
            field("ready_score", "Ready score", WorkspaceGraphValueType.COMPUTED, owner = WorkspaceGraphFieldOwner.SYSTEM, formula = "ingredient_ready_score", hidden = true),
            field("required_score", "Required score", WorkspaceGraphValueType.COMPUTED, owner = WorkspaceGraphFieldOwner.SYSTEM, formula = "ingredient_required_score", hidden = true),
            field("archived", "Archived", WorkspaceGraphValueType.BOOLEAN, high = true),
        ),
        schema(
            WorkspaceGraphSurface.SPENDING,
            field("purchase", "Purchase", WorkspaceGraphValueType.TITLE), field("occurred_at", "Date", WorkspaceGraphValueType.DATE_TIME, high = true),
            field("merchant", "Merchant", WorkspaceGraphValueType.TEXT), field("currency", "Currency", WorkspaceGraphValueType.SELECT, high = true),
            field("entered_total", "Entered total", WorkspaceGraphValueType.MONEY, high = true), field("tax", "Tax", WorkspaceGraphValueType.MONEY, high = true),
            field("discount", "Discount", WorkspaceGraphValueType.MONEY, high = true), field("status", "Status", WorkspaceGraphValueType.SELECT, high = true),
            field("notes", "Notes", WorkspaceGraphValueType.LONG_TEXT), field("archived", "Archived", WorkspaceGraphValueType.BOOLEAN, high = true),
            relation("lines", "Purchase lines", WorkspaceGraphSurface.PURCHASE_LINES, high = true),
            field("lines_subtotal", "Lines subtotal", WorkspaceGraphValueType.COMPUTED, owner = WorkspaceGraphFieldOwner.APP_DERIVED, formula = "spending_lines_subtotal"),
            field("food_amount", "Food amount", WorkspaceGraphValueType.COMPUTED, owner = WorkspaceGraphFieldOwner.APP_DERIVED, formula = "spending_food_amount"),
            field("non_food_amount", "Non-food amount", WorkspaceGraphValueType.COMPUTED, owner = WorkspaceGraphFieldOwner.APP_DERIVED, formula = "spending_non_food_amount"),
            field("line_count", "Line count", WorkspaceGraphValueType.COMPUTED, owner = WorkspaceGraphFieldOwner.APP_DERIVED, formula = "spending_line_count"),
            field("effective_total", "Effective total", WorkspaceGraphValueType.COMPUTED, owner = WorkspaceGraphFieldOwner.APP_DERIVED, formula = "spending_effective_total"),
            field("difference", "Difference", WorkspaceGraphValueType.COMPUTED, owner = WorkspaceGraphFieldOwner.APP_DERIVED, formula = "spending_difference"),
        ),
        schema(
            WorkspaceGraphSurface.PURCHASE_LINES,
            field("line", "Line", WorkspaceGraphValueType.TITLE), relation("purchase", "Purchase", WorkspaceGraphSurface.SPENDING, high = true),
            relation("kitchen_item", "Kitchen item", WorkspaceGraphSurface.KITCHEN), relation("shopping_line", "Shopping line", WorkspaceGraphSurface.SHOPPING),
            field("quantity", "Quantity", WorkspaceGraphValueType.DECIMAL, high = true), field("unit", "Unit", WorkspaceGraphValueType.SELECT, high = true),
            field("unit_price", "Unit price", WorkspaceGraphValueType.MONEY, high = true), field("subtotal", "Subtotal", WorkspaceGraphValueType.MONEY, high = true),
            field("discount", "Discount", WorkspaceGraphValueType.MONEY, high = true), field("tax", "Tax", WorkspaceGraphValueType.MONEY, high = true),
            field("final_amount", "Final amount", WorkspaceGraphValueType.COMPUTED, owner = WorkspaceGraphFieldOwner.APP_DERIVED, formula = "purchase_line_final_amount"),
            field("food_amount_component", "Food amount component", WorkspaceGraphValueType.COMPUTED, owner = WorkspaceGraphFieldOwner.SYSTEM, formula = "purchase_line_food_amount", hidden = true),
            field("non_food_amount_component", "Non-food amount component", WorkspaceGraphValueType.COMPUTED, owner = WorkspaceGraphFieldOwner.SYSTEM, formula = "purchase_line_non_food_amount", hidden = true),
            field("currency", "Currency", WorkspaceGraphValueType.COMPUTED, owner = WorkspaceGraphFieldOwner.APP_DERIVED, formula = "purchase_line_currency"),
            field("category", "Category", WorkspaceGraphValueType.SELECT), field("disposition", "Disposition", WorkspaceGraphValueType.SELECT),
            field("archived", "Archived", WorkspaceGraphValueType.BOOLEAN, high = true),
        ),
        schema(WorkspaceGraphSurface.LISTS_HELP, field("guide", "Guide", WorkspaceGraphValueType.TITLE), field("details", "Details", WorkspaceGraphValueType.LONG_TEXT)),
        schema(WorkspaceGraphSurface.STOCK_LOTS, field("lot", "Lot", WorkspaceGraphValueType.TITLE), relation("kitchen_item", "Kitchen item", WorkspaceGraphSurface.KITCHEN), field("quantity", "Quantity", WorkspaceGraphValueType.DECIMAL), field("unit", "Unit", WorkspaceGraphValueType.SELECT), field("location", "Location", WorkspaceGraphValueType.TEXT), field("best_before", "Best before", WorkspaceGraphValueType.DATE), field("opened", "Opened", WorkspaceGraphValueType.BOOLEAN), field("archived", "Archived", WorkspaceGraphValueType.BOOLEAN)),
        schema(WorkspaceGraphSurface.BINDINGS, field("binding", "Binding", WorkspaceGraphValueType.TITLE), system("canonical_id", "Canonical ID"), system("entity_type", "Entity type"), system("revision", "Revision")),
        schema(WorkspaceGraphSurface.NEEDS_REVIEW, field("review", "Needs review", WorkspaceGraphValueType.TITLE), field("reason", "Reason", WorkspaceGraphValueType.LONG_TEXT), system("canonical_id", "Canonical ID")),
        schema(WorkspaceGraphSurface.SYSTEM, field("setting", "Setting", WorkspaceGraphValueType.TITLE), system("value", "Value")),
    )

    fun schema(surface: WorkspaceGraphSurface): WorkspaceGraphSurfaceSchema = schemas.single { it.surface == surface }

    private fun schema(surface: WorkspaceGraphSurface, vararg fields: WorkspaceGraphField) = WorkspaceGraphSurfaceSchema(surface, fields.toList())
    private fun field(key: String, label: String, type: WorkspaceGraphValueType, owner: WorkspaceGraphFieldOwner = WorkspaceGraphFieldOwner.SHARED, high: Boolean = false, formula: String? = null, hidden: Boolean = false) = WorkspaceGraphField(key, label, type, owner, if (high) WorkspaceGraphConflictRisk.HIGH else WorkspaceGraphConflictRisk.LOW, formulaKey = formula, hidden = hidden)
    private fun relation(key: String, label: String, target: WorkspaceGraphSurface, high: Boolean = false) = WorkspaceGraphField(key, label, WorkspaceGraphValueType.RELATION, WorkspaceGraphFieldOwner.SHARED, if (high) WorkspaceGraphConflictRisk.HIGH else WorkspaceGraphConflictRisk.LOW, relationTarget = target)
    private fun system(key: String, label: String) = field(key, label, WorkspaceGraphValueType.TEXT, WorkspaceGraphFieldOwner.SYSTEM, hidden = true)
}

internal object WorkspaceGraphProjector {
    fun project(snapshot: HouseholdSnapshot): WorkspaceGraphProjection {
        val itemById = snapshot.items.associateBy { it.metadata.id }
        val recipeById = snapshot.recipes.associateBy { it.metadata.id }
        val mealById = snapshot.mealEntries.associateBy { it.metadata.id }
        val merchantById = snapshot.merchants.associateBy { it.metadata.id }
        val locations = snapshot.storageLocations.associateBy { it.metadata.id }
        val lotsByItem = snapshot.inventoryLots.groupBy { it.itemId }
        val ingredientsByRecipe = snapshot.recipeIngredients.groupBy { it.recipeId }
        val purchaseLinesByPurchase = snapshot.purchaseLines.groupBy { it.purchaseId }
        val shoppingByItem = snapshot.shoppingLines.filter { it.itemId != null }.groupBy { it.itemId }

        val kitchenRows = snapshot.items.map { item ->
            val activeLots = lotsByItem[item.metadata.id].orEmpty().filterNot { it.metadata.archivedAt != null || it.status in setOf(InventoryLotStatus.CONSUMED, InventoryLotStatus.DISCARDED, InventoryLotStatus.ARCHIVED) }
            val total = totalQuantity(activeLots.map { it.quantity }, item.defaultUnit)
            val ingredientIds = snapshot.recipeIngredients.filter { it.itemId == item.metadata.id }.map { it.metadata.id.value }
            val shoppingIds = shoppingByItem[item.metadata.id].orEmpty().map { it.metadata.id.value }
            row(WorkspaceGraphSurface.KITCHEN, item.metadata, mapOf(
                "item" to text(item.name), "kind" to text(item.kind.name.humanize()), "category" to text(item.category),
                "on_hand" to decimal(total.amount), "unit" to unit(total.unit), "location" to text(activeLots.mapNotNull { it.locationId?.let(locations::get)?.name }.distinct().singleOrNull()),
                "best_before" to date(activeLots.mapNotNull { it.expiresOn }.minByOrNull { it.value }), "opened" to bool(activeLots.any { it.openedAt != null || it.status == InventoryLotStatus.OPENED }),
                "low_at" to decimal(item.refillThreshold?.amount), "low_stock" to computed("kitchen_low_stock"), "buy_next" to bool(shoppingIds.isNotEmpty()),
                "buy_quantity" to decimal(shoppingByItem[item.metadata.id].orEmpty().firstOrNull()?.quantity?.amount), "preferred_store" to text(item.preferredStore),
                "notes" to text(item.notes), "archived" to bool(item.metadata.archivedAt != null),
                "ingredients" to relation(WorkspaceGraphSurface.INGREDIENTS, ingredientIds), "shopping" to relation(WorkspaceGraphSurface.SHOPPING, shoppingIds),
                "stock_lots" to relation(WorkspaceGraphSurface.STOCK_LOTS, lotsByItem[item.metadata.id].orEmpty().map { it.metadata.id.value }),
            ))
        }

        val ingredientRows = snapshot.recipeIngredients.map { ingredient ->
            val linkedItem = ingredient.itemId?.let(itemById::get)
            row(WorkspaceGraphSurface.INGREDIENTS, ingredient.metadata, mapOf(
                "ingredient" to text(linkedItem?.name ?: ingredient.originalText.ingredientName(ingredient.quantity)),
                "recipe" to relation(WorkspaceGraphSurface.RECIPES, listOf(ingredient.recipeId.value)),
                "kitchen_item" to relation(WorkspaceGraphSurface.KITCHEN, listOfNotNull(ingredient.itemId?.value)),
                "amount" to decimal(ingredient.quantity.amount), "unit" to unit(ingredient.quantity.unit), "preparation" to text(ingredient.preparation),
                "optional" to bool(ingredient.optional), "notes" to text(ingredient.originalText.takeUnless { linkedItem != null && it.equals(linkedItem.name, true) }),
                "on_hand" to computed("ingredient_on_hand"), "kitchen_unit" to computed("ingredient_kitchen_unit"), "missing_amount" to computed("ingredient_missing_amount"),
                "status" to computed("ingredient_status"), "ready_score" to computed("ingredient_ready_score"), "required_score" to computed("ingredient_required_score"),
                "archived" to bool(ingredient.metadata.archivedAt != null),
            ))
        }

        val recipeRows = snapshot.recipes.map { recipe ->
            val steps = snapshot.recipeSteps.filter { it.recipeId == recipe.metadata.id && it.metadata.archivedAt == null }.sortedBy { it.order }
            row(WorkspaceGraphSurface.RECIPES, recipe.metadata, mapOf(
                "recipe" to text(recipe.name), "source_url" to text(recipe.sourceUrl), "cuisine" to text(recipe.cuisine), "tags" to list(recipe.tags.sorted()),
                "servings" to decimal(recipe.yield.amount), "prep_minutes" to decimal(recipe.prepMinutes), "cook_minutes" to decimal(recipe.cookMinutes),
                "instructions" to text(steps.joinToString("\n") { "${it.order + 1}. ${it.instruction}" }.ifBlank { recipe.description.orEmpty() }),
                "favorite" to bool(recipe.tags.any { it.equals("favorite", true) }), "archived" to bool(recipe.metadata.archivedAt != null),
                "ingredients" to relation(WorkspaceGraphSurface.INGREDIENTS, ingredientsByRecipe[recipe.metadata.id].orEmpty().map { it.metadata.id.value }),
                "ingredient_count" to computed("recipe_ingredient_count"), "ready_count" to computed("recipe_ready_count"),
                "can_make_percent" to computed("recipe_can_make_percent"), "missing_items" to computed("recipe_missing_items"),
            ))
        }

        val mealRows = snapshot.mealEntries.map { meal -> row(WorkspaceGraphSurface.MEALS, meal.metadata, mapOf(
            "meal" to text(meal.title), "scheduled_at" to datetime(meal.scheduledAt), "slot" to text(meal.slot),
            "recipe" to relation(WorkspaceGraphSurface.RECIPES, listOfNotNull(meal.recipeId?.value)), "servings" to decimal(meal.servings.amount),
            "status" to text(meal.status.name.humanize()), "leftovers" to text(meal.leftoverIntent),
            "people" to list(meal.mealPlanId?.let { planId -> snapshot.mealPlans.firstOrNull { it.metadata.id == planId }?.targetProfileIds.orEmpty().mapNotNull { id -> snapshot.profiles.firstOrNull { it.metadata.id == id }?.displayName } }.orEmpty()),
            "notes" to text(meal.notes), "archived" to bool(meal.metadata.archivedAt != null),
            "recipe_readiness" to computed("meal_recipe_readiness"), "missing_items" to computed("meal_missing_items"),
        )) }

        val shoppingRows = snapshot.shoppingLines.map { line ->
            val recipeIds = line.sourceEntityIds.filter(recipeById::containsKey).map { it.value }
            val mealIds = line.sourceEntityIds.filter(mealById::containsKey).map { it.value }
            row(WorkspaceGraphSurface.SHOPPING, line.metadata, mapOf(
                "item" to text(line.displayName), "kitchen_item" to relation(WorkspaceGraphSurface.KITCHEN, listOfNotNull(line.itemId?.value)),
                "amount" to decimal(line.quantity.amount), "unit" to unit(line.quantity.unit), "category" to text(line.category), "store" to text(line.preferredStore),
                "status" to text(line.status.name.humanize()), "reason" to text(line.reason.name.humanize()),
                "needed_for_recipes" to relation(WorkspaceGraphSurface.RECIPES, recipeIds), "needed_for_meals" to relation(WorkspaceGraphSurface.MEALS, mealIds),
                "on_hand" to computed("shopping_on_hand"), "kitchen_unit" to computed("shopping_kitchen_unit"), "still_needed" to computed("shopping_still_needed"),
                "estimated_price" to money(line.estimatedPrice), "notes" to null, "archived" to bool(line.metadata.archivedAt != null),
            ))
        }

        val spendingRows = snapshot.purchases.map { purchase ->
            val merchant = purchase.merchantId?.let(merchantById::get)
            val lines = purchaseLinesByPurchase[purchase.metadata.id].orEmpty()
            row(WorkspaceGraphSurface.SPENDING, purchase.metadata, mapOf(
                "purchase" to text(merchant?.name?.let { "$it purchase" } ?: "Purchase"), "occurred_at" to datetime(purchase.occurredAt),
                "merchant" to text(merchant?.name), "currency" to text(listOfNotNull(purchase.total, purchase.subtotal, purchase.tax).firstOrNull()?.currencyCode ?: snapshot.household.defaultCurrency),
                "entered_total" to money(purchase.total), "tax" to money(purchase.tax), "discount" to money(purchase.discount),
                "status" to text(purchase.status.name.humanize()), "notes" to text(purchase.paymentNote), "archived" to bool(purchase.metadata.archivedAt != null),
                "lines" to relation(WorkspaceGraphSurface.PURCHASE_LINES, lines.map { it.metadata.id.value }),
                "lines_subtotal" to computed("spending_lines_subtotal"), "food_amount" to computed("spending_food_amount"),
                "non_food_amount" to computed("spending_non_food_amount"), "line_count" to computed("spending_line_count"),
                "effective_total" to computed("spending_effective_total"), "difference" to computed("spending_difference"),
            ))
        }

        val purchaseLineRows = snapshot.purchaseLines.map { line -> row(WorkspaceGraphSurface.PURCHASE_LINES, line.metadata, mapOf(
            "line" to text(line.displayName), "purchase" to relation(WorkspaceGraphSurface.SPENDING, listOf(line.purchaseId.value)),
            "kitchen_item" to relation(WorkspaceGraphSurface.KITCHEN, listOfNotNull(line.itemId?.value)),
            "shopping_line" to relation(WorkspaceGraphSurface.SHOPPING, listOfNotNull(line.shoppingLineId?.value)),
            "quantity" to decimal(line.quantity.amount), "unit" to unit(line.quantity.unit), "unit_price" to money(line.unitPrice),
            "subtotal" to money(line.lineSubtotal), "discount" to money(line.discount), "tax" to money(line.taxAllocation),
            "final_amount" to computed("purchase_line_final_amount"),
            "food_amount_component" to computed("purchase_line_food_amount"), "non_food_amount_component" to computed("purchase_line_non_food_amount"),
            "currency" to computed("purchase_line_currency"),
            "category" to text(line.spendCategory), "disposition" to text(line.disposition.name.humanize()), "archived" to bool(line.metadata.archivedAt != null),
        )) }

        val lotRows = snapshot.inventoryLots.map { lot -> row(WorkspaceGraphSurface.STOCK_LOTS, lot.metadata, mapOf(
            "lot" to text(itemById[lot.itemId]?.name?.let { "$it lot" } ?: "Inventory lot"), "kitchen_item" to relation(WorkspaceGraphSurface.KITCHEN, listOf(lot.itemId.value)),
            "quantity" to decimal(lot.quantity.amount), "unit" to unit(lot.quantity.unit), "location" to text(lot.locationId?.let(locations::get)?.name),
            "best_before" to date(lot.expiresOn), "opened" to bool(lot.openedAt != null || lot.status == InventoryLotStatus.OPENED), "archived" to bool(lot.metadata.archivedAt != null),
        )) }

        val rows = mapOf(
            WorkspaceGraphSurface.HOME to listOf(WorkspaceGraphRow(WorkspaceGraphSurface.HOME, snapshot.household.id.value, snapshot.household.revision, false, snapshot.household.updatedAt.epochMillis, mapOf("dashboard" to requiredText(snapshot.household.name)))),
            WorkspaceGraphSurface.KITCHEN to kitchenRows, WorkspaceGraphSurface.SHOPPING to shoppingRows, WorkspaceGraphSurface.MEALS to mealRows,
            WorkspaceGraphSurface.RECIPES to recipeRows, WorkspaceGraphSurface.INGREDIENTS to ingredientRows,
            WorkspaceGraphSurface.SPENDING to spendingRows, WorkspaceGraphSurface.PURCHASE_LINES to purchaseLineRows,
            WorkspaceGraphSurface.LISTS_HELP to helpRows(snapshot), WorkspaceGraphSurface.STOCK_LOTS to lotRows,
            WorkspaceGraphSurface.BINDINGS to emptyList(), WorkspaceGraphSurface.NEEDS_REVIEW to emptyList(),
            WorkspaceGraphSurface.SYSTEM to systemRows(snapshot),
        )
        return WorkspaceGraphProjection(WORKSPACE_GRAPH_SCHEMA_VERSION, snapshot.household.id.value, snapshot.household.defaultCurrency, snapshot.household.timezone, snapshot.household.locale, WorkspaceGraphContract.schemas, rows)
    }

    private fun helpRows(snapshot: HouseholdSnapshot): List<WorkspaceGraphRow> = listOf(
        supportRow(snapshot, "units", "Units", "Amounts may be blank when unknown. Supported units: ${WorkspaceGraphContract.supportedUnits.joinToString()}"),
        supportRow(snapshot, "currency", "Currency", "Dashboard totals use ${snapshot.household.defaultCurrency}; different currencies are never summed together."),
        supportRow(snapshot, "relations", "Linked details", "Recipes link to Ingredients and Kitchen; purchases link to Purchase Lines, Kitchen, and Shopping."),
    )

    private fun systemRows(snapshot: HouseholdSnapshot): List<WorkspaceGraphRow> = listOf(
        supportRow(snapshot, "schema", "Workspace schema", WORKSPACE_GRAPH_SCHEMA_VERSION.toString(), WorkspaceGraphSurface.SYSTEM),
        supportRow(snapshot, "household", "Household", snapshot.household.id.value, WorkspaceGraphSurface.SYSTEM),
    )

    private fun supportRow(snapshot: HouseholdSnapshot, suffix: String, title: String, details: String, surface: WorkspaceGraphSurface = WorkspaceGraphSurface.LISTS_HELP) =
        WorkspaceGraphRow(surface, "${snapshot.household.id.value}:$suffix", snapshot.household.revision, false, snapshot.household.updatedAt.epochMillis, if (surface == WorkspaceGraphSurface.SYSTEM) mapOf("setting" to requiredText(title), "value" to requiredText(details)) else mapOf("guide" to requiredText(title), "details" to requiredText(details)))
}

internal fun Money.toWorkspaceMajorUnits(): BigDecimal {
    val digits = runCatching { Currency.getInstance(currencyCode).defaultFractionDigits }.getOrDefault(2).coerceAtLeast(0)
    return BigDecimal.valueOf(minorUnits, digits)
}

private enum class UnitDimension { COUNT, MASS, VOLUME, UNKNOWN }
private data class UnitScale(val dimension: UnitDimension, val baseFactor: BigDecimal)

private val UNIT_SCALES = mapOf(
    "gram" to UnitScale(UnitDimension.MASS, BigDecimal.ONE), "kilogram" to UnitScale(UnitDimension.MASS, BigDecimal("1000")),
    "ounce" to UnitScale(UnitDimension.MASS, BigDecimal("28.349523125")), "pound" to UnitScale(UnitDimension.MASS, BigDecimal("453.59237")),
    "milliliter" to UnitScale(UnitDimension.VOLUME, BigDecimal.ONE), "liter" to UnitScale(UnitDimension.VOLUME, BigDecimal("1000")),
    "teaspoon" to UnitScale(UnitDimension.VOLUME, BigDecimal("4.92892159375")), "tablespoon" to UnitScale(UnitDimension.VOLUME, BigDecimal("14.78676478125")),
    "cup" to UnitScale(UnitDimension.VOLUME, BigDecimal("236.5882365")), "fluid_ounce" to UnitScale(UnitDimension.VOLUME, BigDecimal("29.5735295625")),
    "pint" to UnitScale(UnitDimension.VOLUME, BigDecimal("473.176473")), "quart" to UnitScale(UnitDimension.VOLUME, BigDecimal("946.352946")),
    "gallon" to UnitScale(UnitDimension.VOLUME, BigDecimal("3785.411784")),
)

private fun totalQuantity(quantities: List<Quantity>, preferred: QuantityUnit): Quantity {
    val known = quantities.filter { it.amount != null }
    if (known.isEmpty()) return Quantity.unknown(preferred)
    val target = preferred.takeUnless { it == QuantityUnit.UNKNOWN } ?: known.first().unit
    val values = known.mapNotNull { quantity -> convert(quantity.amount!!.toBigDecimal(), quantity.unit, target) }
    if (values.size != known.size) return Quantity.unknown(target)
    return Quantity(com.wonderfood.core.model.household.DecimalAmount.of(values.fold(BigDecimal.ZERO, BigDecimal::add).toPlainString()), target)
}

private fun convert(amount: BigDecimal, from: QuantityUnit, to: QuantityUnit): BigDecimal? {
    if (from == to) return amount
    val source = UNIT_SCALES[from.code] ?: return null
    val target = UNIT_SCALES[to.code] ?: return null
    if (source.dimension != target.dimension) return null
    return amount.multiply(source.baseFactor).divide(target.baseFactor, MathContext.DECIMAL128).stripTrailingZeros()
}

private fun row(surface: WorkspaceGraphSurface, metadata: EntityMetadata, values: Map<String, WorkspaceGraphValue?>) = WorkspaceGraphRow(surface, metadata.id.value, metadata.revision, metadata.archivedAt != null, metadata.updatedAt.epochMillis, values.filterValues { it != null }.mapValues { it.value!! })
private fun text(value: String?): WorkspaceGraphValue.Text? = value?.takeIf { it.isNotBlank() }?.let(WorkspaceGraphValue::Text)
private fun requiredText(value: String): WorkspaceGraphValue.Text = WorkspaceGraphValue.Text(value)
private fun decimal(value: com.wonderfood.core.model.household.DecimalAmount?): WorkspaceGraphValue.Decimal? = value?.toBigDecimal()?.let(WorkspaceGraphValue::Decimal)
private fun decimal(value: Int?): WorkspaceGraphValue.Decimal? = value?.let { WorkspaceGraphValue.Decimal(it.toBigDecimal()) }
private fun money(value: Money?): WorkspaceGraphValue.MoneyValue? = value?.let { WorkspaceGraphValue.MoneyValue(it.toWorkspaceMajorUnits(), it.currencyCode) }
private fun date(value: CalendarDate?): WorkspaceGraphValue.Date? = value?.value?.let(WorkspaceGraphValue::Date)
private fun datetime(value: UtcTimestamp): WorkspaceGraphValue.DateTime = WorkspaceGraphValue.DateTime(value.epochMillis)
private fun bool(value: Boolean) = WorkspaceGraphValue.BooleanValue(value)
private fun unit(value: QuantityUnit): WorkspaceGraphValue.Text? = value.takeUnless { it == QuantityUnit.UNKNOWN }?.code?.let(WorkspaceGraphValue::Text)
private fun list(values: List<String>) = WorkspaceGraphValue.TextList(values)
private fun relation(target: WorkspaceGraphSurface, ids: List<String>) = WorkspaceGraphValue.Relation(target, ids)
private fun computed(key: String) = WorkspaceGraphValue.Computed(key)
private fun String.humanize(): String = lowercase().replace('_', ' ').replaceFirstChar(Char::uppercase)
private fun String.ingredientName(quantity: Quantity): String {
    var candidate = trim()
    quantity.amount?.value?.let { amount -> candidate = candidate.replace(Regex("^${Regex.escape(amount)}(?:\\.0+)?\\s+", RegexOption.IGNORE_CASE), "") }
    if (quantity.unit != QuantityUnit.UNKNOWN) candidate = candidate.replace(Regex("^${Regex.escape(quantity.unit.code.replace('_', ' '))}s?\\s+", RegexOption.IGNORE_CASE), "")
    return candidate.ifBlank { trim() }
}
