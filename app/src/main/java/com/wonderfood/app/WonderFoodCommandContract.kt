package com.wonderfood.app

/**
 * Public command contract for launchers, automations, shares, and other apps.
 *
 * External callers should treat these commands as proposals. WonderFood stages structured
 * food changes for user review before writing unless the ViewModel explicitly marks the
 * command as a low-risk direct action.
 */
object WonderFoodCommandContract {
    const val ACTION_COMMAND = "com.wonderfood.app.action.COMMAND"

    const val EXTRA_REQUEST_ID = "requestId"
    const val EXTRA_REQUEST_ID_SNAKE = "request_id"
    const val EXTRA_IDEMPOTENCY_KEY = "idempotencyKey"
    const val EXTRA_ACTION_TYPE = "type"
    const val EXTRA_ACTION = "action"
    const val EXTRA_ACTIONS_JSON = "actions"
    const val EXTRA_TARGET_ID = "id"
    const val EXTRA_TARGET_ID_CAMEL = "targetId"
    const val EXTRA_TARGET_ID_SNAKE = "target_id"
    const val EXTRA_TARGET_NAME = "target"
    const val EXTRA_TARGET_NAME_CAMEL = "targetName"
    const val EXTRA_TARGET_NAME_SNAKE = "target_name"
    const val EXTRA_NAME = "name"
    const val EXTRA_TITLE = "title"
    const val EXTRA_ITEM = "item"
    const val EXTRA_TEXT = "text"
    const val EXTRA_QUERY = "q"
    const val EXTRA_UTTERANCE = "utterance"

    const val MAX_SHARED_TEXT_LENGTH = 32_000
    const val MAX_LINK_TEXT_LENGTH = 1_000
    const val MAX_ACTIONS_JSON_LENGTH = 8_000
    const val MAX_BULK_ACTIONS = 12
    const val MAX_LINK_FIELD_LENGTH = 1_000

    val LINK_ACTION_FIELD_KEYS: Set<String> = setOf(
        "allergies",
        "amount",
        "calorie_goal",
        "calorie_target",
        "calories",
        "carbs_g",
        "category",
        "confidence",
        "custom_ai_instructions",
        "date_epoch_day",
        "days",
        "days_text",
        "diet_style",
        "dislikes",
        "duration_minutes",
        "ended_at_millis",
        "event_type",
        "expires_at_millis",
        "fat_g",
        "grocery_hint",
        "health_notes",
        "image_uri",
        "image_url",
        "ingredients",
        "inventory_item_id",
        "logged_date_epoch_day",
        "meal_log_id",
        "meal_slot",
        "name",
        "notes",
        "nutrition_source",
        "prep_minutes",
        "preferred_cuisines",
        "preferred_staples",
        "preferred_stores",
        "protein_g",
        "protein_goal",
        "quantity",
        "recipe_id",
        "serving_text",
        "servings",
        "shopping_trip_id",
        "slot",
        "source",
        "started_at_millis",
        "status",
        "steps",
        "tags",
        "text",
        "title",
        "unit",
        "used_items_text",
        "zone",
    )

    fun normalizeActionType(raw: String): String? {
        val normalized = raw
            .filterNot(Char::isISOControl)
            .trim()
            .lowercase()
            .replace('-', '_')
            .take(80)
        return normalized.takeIf(ACTION_SPECS::containsKey)
    }

    fun actionSpec(actionType: String): LinkActionSpec? =
        ACTION_SPECS[actionType.lowercase()]

    fun supportedActionTypes(): Set<String> = ACTION_SPECS.keys

    fun allowedFields(targetKind: String): Set<String> =
        TARGET_FIELDS[targetKind].orEmpty()

    private val TARGET_FIELDS: Map<String, Set<String>> = mapOf(
        "inventory" to setOf(
            "amount", "calories", "carbs_g", "category", "expires_at_millis", "fat_g", "image_uri",
            "image_url", "name", "notes", "nutrition_source", "protein_g", "quantity", "serving_text", "text",
            "title", "zone",
        ),
        "grocery" to setOf(
            "amount", "calories", "carbs_g", "category", "fat_g", "image_uri", "image_url", "name", "notes",
            "nutrition_source", "protein_g", "quantity", "serving_text", "source", "status", "text", "title",
        ),
        "recipe" to setOf("image_uri", "image_url", "ingredients", "name", "notes", "prep_minutes", "servings", "steps", "tags", "text", "title"),
        "meal_log" to setOf(
            "calories", "carbs_g", "date_epoch_day", "fat_g", "ingredients", "logged_date_epoch_day", "meal_slot",
            "name", "protein_g", "source", "text", "title", "used_items_text",
        ),
        "meal_plan" to setOf("date_epoch_day", "days", "days_text", "grocery_hint", "name", "text", "title"),
        "plan_entry" to setOf("calorie_target", "calories", "date_epoch_day", "meal_slot", "name", "slot", "status", "text", "title"),
        "preferences" to setOf(
            "allergies", "calorie_goal", "custom_ai_instructions", "diet_style", "dislikes", "health_notes",
            "preferred_cuisines", "preferred_staples", "preferred_stores", "protein_goal",
        ),
        "event" to setOf(
            "amount", "confidence", "duration_minutes", "ended_at_millis", "event_type", "inventory_item_id",
            "meal_log_id", "notes", "recipe_id", "shopping_trip_id", "source", "started_at_millis", "text", "title", "unit",
        ),
    )

    private val ACTION_SPECS: Map<String, LinkActionSpec> = buildMap {
        fun actions(target: String, operation: LinkActionOperation, vararg names: String) {
            names.forEach { name -> put(name, LinkActionSpec(target, operation)) }
        }

        actions("inventory", LinkActionOperation.CREATE, "inventory.add", "inventory.create", "inventory.add_lot")
        actions("inventory", LinkActionOperation.UPDATE, "inventory.edit", "inventory.update", "inventory.adjust_quantity", "inventory.move_lot")
        actions("inventory", LinkActionOperation.DELETE, "inventory.delete", "inventory.remove", "inventory.archive", "inventory.archive_lot")

        actions("grocery", LinkActionOperation.CREATE, "grocery.add", "grocery.create", "shopping.add_item")
        actions("grocery", LinkActionOperation.UPDATE, "grocery.edit", "grocery.update")
        actions("grocery", LinkActionOperation.DELETE, "grocery.delete", "grocery.remove", "grocery.archive", "shopping.remove_item")
        actions("grocery", LinkActionOperation.MARK_BOUGHT, "grocery.mark_bought", "shopping.mark_item_bought")

        actions("recipe", LinkActionOperation.CREATE, "recipe.add", "recipe.create", "recipe.save", "recipe.save_structured")
        actions("recipe", LinkActionOperation.UPDATE, "recipe.edit", "recipe.update", "recipe.update_structured")
        actions("recipe", LinkActionOperation.DELETE, "recipe.delete", "recipe.remove", "recipe.archive")

        actions("meal_log", LinkActionOperation.LOG, "meal.log", "meal_log.log", "meal_log.add")
        actions("meal_log", LinkActionOperation.UPDATE, "meal_log.edit", "meal_log.update")
        actions("meal_log", LinkActionOperation.DELETE, "meal_log.delete", "meal_log.remove", "meal_log.archive")

        actions("meal_plan", LinkActionOperation.CREATE, "meal_plan.add", "meal_plan.create", "planning.create_meal_plan")
        actions("meal_plan", LinkActionOperation.UPDATE, "meal_plan.edit", "meal_plan.update")
        actions("meal_plan", LinkActionOperation.DELETE, "meal_plan.delete", "meal_plan.remove", "meal_plan.archive")

        actions("plan_entry", LinkActionOperation.CREATE, "plan_entry.add", "plan_entry.create")
        actions("plan_entry", LinkActionOperation.UPDATE, "plan_entry.edit", "plan_entry.update", "planning.update_meal_plan_entry", "planning.mark_entry_status")
        actions("plan_entry", LinkActionOperation.DELETE, "plan_entry.delete", "plan_entry.remove", "plan_entry.archive")
        actions("plan_entry", LinkActionOperation.MARK_EATEN, "plan_entry.eaten")
        actions("plan_entry", LinkActionOperation.MARK_SKIPPED, "plan_entry.skipped")

        actions("preferences", LinkActionOperation.UPDATE, "preferences.edit", "preferences.update", "preferences.update_food_preferences")

        actions("event", LinkActionOperation.LOG, "event.log", "event.add")
        actions("event", LinkActionOperation.UPDATE, "event.edit", "event.update")
        actions("event", LinkActionOperation.DELETE, "event.delete", "event.remove", "event.archive")
    }
}

data class LinkActionSpec(
    val targetKind: String,
    val operation: LinkActionOperation,
)

enum class LinkActionOperation {
    CREATE,
    UPDATE,
    DELETE,
    LOG,
    MARK_BOUGHT,
    MARK_EATEN,
    MARK_SKIPPED,
}
