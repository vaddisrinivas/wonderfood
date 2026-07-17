package com.wonderfood.core.ai

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

public const val COMMAND_ENVELOPE_SCHEMA_VERSION: String = "wf.ai.command-envelope.v1"
public const val SKILL_CATALOG_VERSION: String = "wf.ai.skill-catalog.v1"
public const val DEFAULT_SKILL_VERSION: String = "1.0.0"

@Serializable
public data class CommandEnvelope(
    @SerialName("schema_version")
    public val schemaVersion: String,
    @SerialName("catalog_version")
    public val catalogVersion: String,
    @SerialName("skill_id")
    public val skillId: SkillId,
    @SerialName("skill_version")
    public val skillVersion: String,
    @SerialName("envelope_id")
    public val envelopeId: String,
    @SerialName("idempotency_key")
    public val idempotencyKey: String,
    public val status: EnvelopeStatus,
    public val evidence: List<CommandEvidence>,
    public val commands: List<Command>,
    public val confidence: Confidence,
    public val confirmation: Confirmation,
    public val warnings: List<CommandWarning>,
    public val unsupported: UnsupportedReason?,
) {
    public val hasMutatingCommands: Boolean
        get() = commands.any { it.mutation }
}

@Serializable
public enum class SkillId(public val wireName: String) {
    @SerialName("inventory")
    INVENTORY("inventory"),

    @SerialName("shopping")
    SHOPPING("shopping"),

    @SerialName("recipes")
    RECIPES("recipes"),

    @SerialName("meals")
    MEALS("meals"),

    @SerialName("planning")
    PLANNING("planning"),

    @SerialName("preferences")
    PREFERENCES("preferences"),

    @SerialName("receipt_parsing")
    RECEIPT_PARSING("receipt_parsing"),

    @SerialName("nutrition_correction")
    NUTRITION_CORRECTION("nutrition_correction"),

    @SerialName("navigation")
    NAVIGATION("navigation"),
}

@Serializable
public enum class EnvelopeStatus(public val wireName: String) {
    @SerialName("commands")
    COMMANDS("commands"),

    @SerialName("needs_confirmation")
    NEEDS_CONFIRMATION("needs_confirmation"),

    @SerialName("needs_clarification")
    NEEDS_CLARIFICATION("needs_clarification"),

    @SerialName("unsupported")
    UNSUPPORTED("unsupported"),
}

@Serializable
public data class CommandEvidence(
    @SerialName("evidence_id")
    public val evidenceId: String,
    public val type: EvidenceType,
    @SerialName("source_ref")
    public val sourceRef: String,
    public val quote: String,
    @SerialName("observed_at")
    public val observedAt: String?,
    public val confidence: Double,
)

@Serializable
public enum class EvidenceType(public val wireName: String) {
    @SerialName("user_text")
    USER_TEXT("user_text"),

    @SerialName("receipt_text")
    RECEIPT_TEXT("receipt_text"),

    @SerialName("receipt_image")
    RECEIPT_IMAGE("receipt_image"),

    @SerialName("barcode")
    BARCODE("barcode"),

    @SerialName("inventory_snapshot")
    INVENTORY_SNAPSHOT("inventory_snapshot"),

    @SerialName("shopping_snapshot")
    SHOPPING_SNAPSHOT("shopping_snapshot"),

    @SerialName("recipe_snapshot")
    RECIPE_SNAPSHOT("recipe_snapshot"),

    @SerialName("meal_snapshot")
    MEAL_SNAPSHOT("meal_snapshot"),

    @SerialName("plan_snapshot")
    PLAN_SNAPSHOT("plan_snapshot"),

    @SerialName("preference_snapshot")
    PREFERENCE_SNAPSHOT("preference_snapshot"),

    @SerialName("nutrition_label")
    NUTRITION_LABEL("nutrition_label"),

    @SerialName("provider_result")
    PROVIDER_RESULT("provider_result"),

    @SerialName("app_context")
    APP_CONTEXT("app_context"),
}

@Serializable
public data class Confidence(
    public val score: Double,
    public val rationale: String,
)

@Serializable
public data class Confirmation(
    public val required: Boolean,
    public val level: ConfirmationLevel,
    public val reason: String,
    public val prompt: String,
)

@Serializable
public enum class ConfirmationLevel(public val wireName: String) {
    @SerialName("none")
    NONE("none"),

    @SerialName("review")
    REVIEW("review"),

    @SerialName("confirm")
    CONFIRM("confirm"),

    @SerialName("confirm_destructive")
    CONFIRM_DESTRUCTIVE("confirm_destructive"),
}

@Serializable
public data class CommandWarning(
    public val code: String,
    public val severity: WarningSeverity,
    public val message: String,
    @SerialName("evidence_refs")
    public val evidenceRefs: List<String>,
)

@Serializable
public enum class WarningSeverity(public val wireName: String) {
    @SerialName("info")
    INFO("info"),

    @SerialName("review")
    REVIEW("review"),

    @SerialName("blocker")
    BLOCKER("blocker"),
}

@Serializable
public data class UnsupportedReason(
    public val code: String,
    public val message: String,
    @SerialName("allowed_alternatives")
    public val allowedAlternatives: List<String>,
)

@Serializable
public data class Command(
    @SerialName("command_id")
    public val commandId: String,
    public val type: CommandType,
    public val summary: String,
    public val payload: JsonObject,
    @SerialName("evidence_refs")
    public val evidenceRefs: List<String>,
    public val confidence: Confidence,
    public val confirmation: Confirmation,
    public val destructive: Boolean,
    public val mutation: Boolean,
)

@Serializable
public enum class CommandType(public val wireName: String) {
    @SerialName("inventory.add_lot")
    INVENTORY_ADD_LOT("inventory.add_lot"),

    @SerialName("inventory.adjust_quantity")
    INVENTORY_ADJUST_QUANTITY("inventory.adjust_quantity"),

    @SerialName("inventory.move_lot")
    INVENTORY_MOVE_LOT("inventory.move_lot"),

    @SerialName("inventory.archive_lot")
    INVENTORY_ARCHIVE_LOT("inventory.archive_lot"),

    @SerialName("shopping.add_item")
    SHOPPING_ADD_ITEM("shopping.add_item"),

    @SerialName("shopping.mark_item_bought")
    SHOPPING_MARK_ITEM_BOUGHT("shopping.mark_item_bought"),

    @SerialName("shopping.remove_item")
    SHOPPING_REMOVE_ITEM("shopping.remove_item"),

    @SerialName("recipe.save_structured")
    RECIPE_SAVE_STRUCTURED("recipe.save_structured"),

    @SerialName("recipe.update_structured")
    RECIPE_UPDATE_STRUCTURED("recipe.update_structured"),

    @SerialName("recipe.archive")
    RECIPE_ARCHIVE("recipe.archive"),

    @SerialName("meal.log")
    MEAL_LOG("meal.log"),

    @SerialName("meal.record_inventory_use")
    MEAL_RECORD_INVENTORY_USE("meal.record_inventory_use"),

    @SerialName("meal.record_leftovers")
    MEAL_RECORD_LEFTOVERS("meal.record_leftovers"),

    @SerialName("planning.create_meal_plan")
    PLANNING_CREATE_MEAL_PLAN("planning.create_meal_plan"),

    @SerialName("planning.update_meal_plan_entry")
    PLANNING_UPDATE_MEAL_PLAN_ENTRY("planning.update_meal_plan_entry"),

    @SerialName("planning.mark_entry_status")
    PLANNING_MARK_ENTRY_STATUS("planning.mark_entry_status"),

    @SerialName("preferences.update_food_preferences")
    PREFERENCES_UPDATE_FOOD_PREFERENCES("preferences.update_food_preferences"),

    @SerialName("preferences.clear_field")
    PREFERENCES_CLEAR_FIELD("preferences.clear_field"),

    @SerialName("receipt.attach_parse")
    RECEIPT_ATTACH_PARSE("receipt.attach_parse"),

    @SerialName("receipt.propose_items")
    RECEIPT_PROPOSE_ITEMS("receipt.propose_items"),

    @SerialName("nutrition.correct_inventory_item")
    NUTRITION_CORRECT_INVENTORY_ITEM("nutrition.correct_inventory_item"),

    @SerialName("nutrition.correct_meal_log")
    NUTRITION_CORRECT_MEAL_LOG("nutrition.correct_meal_log"),

    @SerialName("nutrition.mark_unknown")
    NUTRITION_MARK_UNKNOWN("nutrition.mark_unknown"),

    @SerialName("navigation.open_destination")
    NAVIGATION_OPEN_DESTINATION("navigation.open_destination"),

    @SerialName("navigation.open_detail")
    NAVIGATION_OPEN_DETAIL("navigation.open_detail"),

    @SerialName("navigation.search")
    NAVIGATION_SEARCH("navigation.search"),
}
