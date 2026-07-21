package com.wonderfood.core.model.household

enum class WorkspaceSurfaceKind { DASHBOARD, TABLE, HELP, SUPPORT }
enum class WorkspaceValueType { TEXT, LONG_TEXT, NUMBER, CURRENCY, DATE, DATE_TIME, URL, FILE, CHECKBOX, SELECT, MULTI_SELECT, RELATION, COMPUTED }
enum class WorkspaceFieldOwner { HUMAN, SHARED, APP_DERIVED, SYSTEM }
enum class ConflictRisk { LOW, HIGH }

data class WorkspaceFieldDefinition(
    val key: String,
    val label: String,
    val type: WorkspaceValueType,
    val owner: WorkspaceFieldOwner,
    val conflictRisk: ConflictRisk,
    val required: Boolean = false,
) {
    init {
        require(Regex("[a-z][a-z0-9_]*").matches(key)) { "Workspace field key must be stable snake_case." }
        require(label.isNotBlank()) { "Workspace field label must not be blank." }
    }
}

data class WorkspaceSurfaceDefinition(
    val key: String,
    val label: String,
    val kind: WorkspaceSurfaceKind,
    val visible: Boolean,
    val fields: List<WorkspaceFieldDefinition> = emptyList(),
) {
    init {
        require(Regex("[a-z][a-z0-9_]*").matches(key)) { "Workspace surface key must be stable snake_case." }
        require(label.isNotBlank()) { "Workspace surface label must not be blank." }
        require(fields.map { it.key }.distinct().size == fields.size) { "Workspace field keys must be unique per surface." }
    }

    fun path(fieldKey: String): String = "$key.$fieldKey"
}

object HouseholdWorkspaceContract {
    const val SCHEMA_VERSION: Int = 1

    val home = WorkspaceSurfaceDefinition("home", "Home", WorkspaceSurfaceKind.DASHBOARD, visible = true)

    val kitchen = WorkspaceSurfaceDefinition(
        key = "kitchen",
        label = "Kitchen",
        kind = WorkspaceSurfaceKind.TABLE,
        visible = true,
        fields = listOf(
            field("identifier", "WonderFood ID", WorkspaceValueType.TEXT, WorkspaceFieldOwner.SYSTEM, ConflictRisk.HIGH, required = true),
            field("item", "Item", WorkspaceValueType.TEXT, WorkspaceFieldOwner.SHARED, ConflictRisk.LOW, required = true),
            field("kind", "Kind", WorkspaceValueType.SELECT, WorkspaceFieldOwner.SHARED, ConflictRisk.LOW, required = true),
            field("category", "Category", WorkspaceValueType.SELECT, WorkspaceFieldOwner.SHARED, ConflictRisk.LOW),
            field("on_hand", "On hand", WorkspaceValueType.NUMBER, WorkspaceFieldOwner.SHARED, ConflictRisk.HIGH),
            field("unit", "Unit", WorkspaceValueType.SELECT, WorkspaceFieldOwner.SHARED, ConflictRisk.HIGH),
            field("location", "Location", WorkspaceValueType.SELECT, WorkspaceFieldOwner.SHARED, ConflictRisk.LOW),
            field("best_before", "Best before", WorkspaceValueType.DATE, WorkspaceFieldOwner.SHARED, ConflictRisk.LOW),
            field("opened", "Opened", WorkspaceValueType.CHECKBOX, WorkspaceFieldOwner.SHARED, ConflictRisk.LOW),
            field("low_at", "Low at", WorkspaceValueType.NUMBER, WorkspaceFieldOwner.SHARED, ConflictRisk.HIGH),
            field("buy_next", "Buy next", WorkspaceValueType.CHECKBOX, WorkspaceFieldOwner.HUMAN, ConflictRisk.LOW),
            field("buy_quantity", "Buy quantity", WorkspaceValueType.NUMBER, WorkspaceFieldOwner.HUMAN, ConflictRisk.HIGH),
            field("preferred_store", "Preferred store", WorkspaceValueType.TEXT, WorkspaceFieldOwner.SHARED, ConflictRisk.LOW),
            field("notes", "Notes", WorkspaceValueType.LONG_TEXT, WorkspaceFieldOwner.SHARED, ConflictRisk.LOW),
            field("archived", "Archived", WorkspaceValueType.CHECKBOX, WorkspaceFieldOwner.SHARED, ConflictRisk.HIGH),
        ),
    )

    val shopping = WorkspaceSurfaceDefinition(
        key = "shopping",
        label = "Shopping",
        kind = WorkspaceSurfaceKind.TABLE,
        visible = true,
        fields = listOf(
            field("identifier", "WonderFood ID", WorkspaceValueType.TEXT, WorkspaceFieldOwner.SYSTEM, ConflictRisk.HIGH, required = true),
            field("item", "Item", WorkspaceValueType.TEXT, WorkspaceFieldOwner.SHARED, ConflictRisk.LOW, required = true),
            field("amount", "Amount", WorkspaceValueType.NUMBER, WorkspaceFieldOwner.SHARED, ConflictRisk.HIGH),
            field("unit", "Unit", WorkspaceValueType.SELECT, WorkspaceFieldOwner.SHARED, ConflictRisk.HIGH),
            field("category", "Category", WorkspaceValueType.SELECT, WorkspaceFieldOwner.SHARED, ConflictRisk.LOW),
            field("store", "Store", WorkspaceValueType.TEXT, WorkspaceFieldOwner.SHARED, ConflictRisk.LOW),
            field("status", "Status", WorkspaceValueType.SELECT, WorkspaceFieldOwner.SHARED, ConflictRisk.HIGH),
            field("reason", "Reason", WorkspaceValueType.SELECT, WorkspaceFieldOwner.SHARED, ConflictRisk.LOW),
            field("needed_for", "Needed for", WorkspaceValueType.RELATION, WorkspaceFieldOwner.SHARED, ConflictRisk.HIGH),
            field("estimated_price", "Estimated price", WorkspaceValueType.CURRENCY, WorkspaceFieldOwner.APP_DERIVED, ConflictRisk.LOW),
            field("actual_price", "Actual price", WorkspaceValueType.CURRENCY, WorkspaceFieldOwner.SHARED, ConflictRisk.HIGH),
            field("notes", "Notes", WorkspaceValueType.LONG_TEXT, WorkspaceFieldOwner.SHARED, ConflictRisk.LOW),
            field("archived", "Archived", WorkspaceValueType.CHECKBOX, WorkspaceFieldOwner.SHARED, ConflictRisk.HIGH),
        ),
    )

    val meals = WorkspaceSurfaceDefinition(
        key = "meals",
        label = "Meals",
        kind = WorkspaceSurfaceKind.TABLE,
        visible = true,
        fields = listOf(
            field("identifier", "WonderFood ID", WorkspaceValueType.TEXT, WorkspaceFieldOwner.SYSTEM, ConflictRisk.HIGH, required = true),
            field("meal", "Meal", WorkspaceValueType.TEXT, WorkspaceFieldOwner.SHARED, ConflictRisk.LOW, required = true),
            field("scheduled_at", "Date", WorkspaceValueType.DATE_TIME, WorkspaceFieldOwner.SHARED, ConflictRisk.HIGH, required = true),
            field("slot", "Meal slot", WorkspaceValueType.SELECT, WorkspaceFieldOwner.SHARED, ConflictRisk.HIGH),
            field("recipe", "Recipe", WorkspaceValueType.RELATION, WorkspaceFieldOwner.SHARED, ConflictRisk.HIGH),
            field("servings", "Servings", WorkspaceValueType.NUMBER, WorkspaceFieldOwner.SHARED, ConflictRisk.HIGH),
            field("status", "Status", WorkspaceValueType.SELECT, WorkspaceFieldOwner.SHARED, ConflictRisk.HIGH),
            field("leftovers", "Leftovers", WorkspaceValueType.NUMBER, WorkspaceFieldOwner.SHARED, ConflictRisk.HIGH),
            field("people", "People", WorkspaceValueType.MULTI_SELECT, WorkspaceFieldOwner.SHARED, ConflictRisk.LOW),
            field("notes", "Notes", WorkspaceValueType.LONG_TEXT, WorkspaceFieldOwner.SHARED, ConflictRisk.LOW),
            field("archived", "Archived", WorkspaceValueType.CHECKBOX, WorkspaceFieldOwner.SHARED, ConflictRisk.HIGH),
        ),
    )

    val recipes = WorkspaceSurfaceDefinition(
        key = "recipes",
        label = "Recipes",
        kind = WorkspaceSurfaceKind.TABLE,
        visible = true,
        fields = listOf(
            field("identifier", "WonderFood ID", WorkspaceValueType.TEXT, WorkspaceFieldOwner.SYSTEM, ConflictRisk.HIGH, required = true),
            field("recipe", "Recipe", WorkspaceValueType.TEXT, WorkspaceFieldOwner.SHARED, ConflictRisk.LOW, required = true),
            field("source_url", "Source", WorkspaceValueType.URL, WorkspaceFieldOwner.SHARED, ConflictRisk.LOW),
            field("image", "Image", WorkspaceValueType.FILE, WorkspaceFieldOwner.SHARED, ConflictRisk.LOW),
            field("cuisine", "Cuisine", WorkspaceValueType.SELECT, WorkspaceFieldOwner.SHARED, ConflictRisk.LOW),
            field("tags", "Tags", WorkspaceValueType.MULTI_SELECT, WorkspaceFieldOwner.SHARED, ConflictRisk.LOW),
            field("servings", "Servings", WorkspaceValueType.NUMBER, WorkspaceFieldOwner.SHARED, ConflictRisk.HIGH),
            field("prep_minutes", "Prep minutes", WorkspaceValueType.NUMBER, WorkspaceFieldOwner.SHARED, ConflictRisk.LOW),
            field("cook_minutes", "Cook minutes", WorkspaceValueType.NUMBER, WorkspaceFieldOwner.SHARED, ConflictRisk.LOW),
            field("ingredients", "Ingredients", WorkspaceValueType.LONG_TEXT, WorkspaceFieldOwner.SHARED, ConflictRisk.HIGH),
            field("instructions", "Instructions", WorkspaceValueType.LONG_TEXT, WorkspaceFieldOwner.SHARED, ConflictRisk.HIGH),
            field("favorite", "Favorite", WorkspaceValueType.CHECKBOX, WorkspaceFieldOwner.SHARED, ConflictRisk.LOW),
            field("can_make_percent", "Can make %", WorkspaceValueType.COMPUTED, WorkspaceFieldOwner.APP_DERIVED, ConflictRisk.LOW),
            field("missing_items", "Missing items", WorkspaceValueType.COMPUTED, WorkspaceFieldOwner.APP_DERIVED, ConflictRisk.LOW),
            field("last_matched", "Last matched", WorkspaceValueType.DATE_TIME, WorkspaceFieldOwner.APP_DERIVED, ConflictRisk.LOW),
            field("archived", "Archived", WorkspaceValueType.CHECKBOX, WorkspaceFieldOwner.SHARED, ConflictRisk.HIGH),
        ),
    )

    val spending = WorkspaceSurfaceDefinition(
        key = "spending",
        label = "Spending",
        kind = WorkspaceSurfaceKind.TABLE,
        visible = true,
        fields = listOf(
            field("identifier", "WonderFood ID", WorkspaceValueType.TEXT, WorkspaceFieldOwner.SYSTEM, ConflictRisk.HIGH, required = true),
            field("purchase", "Purchase", WorkspaceValueType.TEXT, WorkspaceFieldOwner.SHARED, ConflictRisk.LOW, required = true),
            field("occurred_at", "Date", WorkspaceValueType.DATE_TIME, WorkspaceFieldOwner.SHARED, ConflictRisk.HIGH, required = true),
            field("merchant", "Merchant", WorkspaceValueType.TEXT, WorkspaceFieldOwner.SHARED, ConflictRisk.LOW),
            field("total", "Total", WorkspaceValueType.CURRENCY, WorkspaceFieldOwner.SHARED, ConflictRisk.HIGH),
            field("currency", "Currency", WorkspaceValueType.SELECT, WorkspaceFieldOwner.SHARED, ConflictRisk.HIGH),
            field("category", "Category", WorkspaceValueType.SELECT, WorkspaceFieldOwner.SHARED, ConflictRisk.HIGH),
            field("food_amount", "Food amount", WorkspaceValueType.CURRENCY, WorkspaceFieldOwner.SHARED, ConflictRisk.HIGH),
            field("non_food_amount", "Non-food amount", WorkspaceValueType.CURRENCY, WorkspaceFieldOwner.SHARED, ConflictRisk.HIGH),
            field("tax", "Tax", WorkspaceValueType.CURRENCY, WorkspaceFieldOwner.SHARED, ConflictRisk.HIGH),
            field("discount", "Discount", WorkspaceValueType.CURRENCY, WorkspaceFieldOwner.SHARED, ConflictRisk.HIGH),
            field("receipt", "Receipt", WorkspaceValueType.FILE, WorkspaceFieldOwner.SHARED, ConflictRisk.LOW),
            field("status", "Status", WorkspaceValueType.SELECT, WorkspaceFieldOwner.SHARED, ConflictRisk.HIGH),
            field("notes", "Notes", WorkspaceValueType.LONG_TEXT, WorkspaceFieldOwner.SHARED, ConflictRisk.LOW),
            field("archived", "Archived", WorkspaceValueType.CHECKBOX, WorkspaceFieldOwner.SHARED, ConflictRisk.HIGH),
        ),
    )

    val help = WorkspaceSurfaceDefinition("lists_help", "Lists & Help", WorkspaceSurfaceKind.HELP, visible = true)

    val supportSurfaces = listOf(
        WorkspaceSurfaceDefinition("stock_lots", "Stock Lots", WorkspaceSurfaceKind.SUPPORT, visible = false),
        WorkspaceSurfaceDefinition("recipe_ingredients", "Recipe Ingredients", WorkspaceSurfaceKind.SUPPORT, visible = false),
        WorkspaceSurfaceDefinition("purchase_lines", "Purchase Lines", WorkspaceSurfaceKind.SUPPORT, visible = false),
        WorkspaceSurfaceDefinition("bindings", "Bindings", WorkspaceSurfaceKind.SUPPORT, visible = false),
        WorkspaceSurfaceDefinition("needs_review", "Needs review", WorkspaceSurfaceKind.SUPPORT, visible = false),
    )

    val visibleSurfaces: List<WorkspaceSurfaceDefinition> = listOf(home, kitchen, shopping, meals, recipes, spending, help)
    val allSurfaces: List<WorkspaceSurfaceDefinition> = visibleSurfaces + supportSurfaces

    fun field(path: String): WorkspaceFieldDefinition? {
        val surfaceKey = path.substringBefore('.', missingDelimiterValue = "")
        val fieldKey = path.substringAfter('.', missingDelimiterValue = "")
        if (surfaceKey.isBlank() || fieldKey.isBlank()) return null
        return allSurfaces.firstOrNull { it.key == surfaceKey }?.fields?.firstOrNull { it.key == fieldKey }
    }

    private fun field(
        key: String,
        label: String,
        type: WorkspaceValueType,
        owner: WorkspaceFieldOwner,
        conflictRisk: ConflictRisk,
        required: Boolean = false,
    ): WorkspaceFieldDefinition = WorkspaceFieldDefinition(key, label, type, owner, conflictRisk, required)
}
