package com.wonderfood.core.model.household

import java.math.BigDecimal
import java.util.UUID

private fun requireUuid(value: String, label: String): String {
    require(value.isNotBlank()) { "$label must not be blank." }
    runCatching { UUID.fromString(value) }
        .getOrElse { throw IllegalArgumentException("$label must be a UUID.", it) }
    return value
}

private fun requireText(value: String, label: String): String {
    require(value.isNotBlank()) { "$label must not be blank." }
    return value
}

private fun newUuid(): String = UUID.randomUUID().toString()

@JvmInline
value class HouseholdId(val value: String) {
    init { requireUuid(value, "Household ID") }

    companion object { fun new(): HouseholdId = HouseholdId(newUuid()) }
}

@JvmInline
value class EntityId(val value: String) {
    init { requireUuid(value, "Entity ID") }

    companion object { fun new(): EntityId = EntityId(newUuid()) }
}

@JvmInline
value class CommandId(val value: String) {
    init { requireUuid(value, "Command ID") }

    companion object { fun new(): CommandId = CommandId(newUuid()) }
}

@JvmInline
value class ConnectionId(val value: String) {
    init { requireUuid(value, "Connection ID") }

    companion object { fun new(): ConnectionId = ConnectionId(newUuid()) }
}

@JvmInline
value class UtcTimestamp(val epochMillis: Long)

@JvmInline
value class CalendarDate(val value: String) {
    init {
        require(Regex("\\d{4}-\\d{2}-\\d{2}").matches(value)) {
            "Calendar date must use YYYY-MM-DD."
        }
    }
}

@JvmInline
value class DecimalAmount(val value: String) {
    init {
        val parsed = value.toBigDecimalOrNull()
        require(parsed != null && parsed >= BigDecimal.ZERO) {
            "Decimal amount must be a non-negative decimal."
        }
        require(value == parsed.stripTrailingZeros().toPlainString()) {
            "Decimal amount must use canonical plain notation."
        }
    }

    fun toBigDecimal(): BigDecimal = value.toBigDecimal()

    companion object {
        fun of(value: String): DecimalAmount {
            val parsed = value.toBigDecimalOrNull()
                ?: throw IllegalArgumentException("Decimal amount must be numeric.")
            require(parsed >= BigDecimal.ZERO) { "Decimal amount must not be negative." }
            return DecimalAmount(parsed.stripTrailingZeros().toPlainString())
        }
    }
}

@JvmInline
value class QuantityUnit(val code: String) {
    init {
        require(Regex("[a-z][a-z0-9_-]{0,31}").matches(code)) {
            "Quantity unit must be a lowercase stable code."
        }
    }

    companion object {
        val UNKNOWN = QuantityUnit("unknown")
        val EACH = QuantityUnit("each")
        val PACKAGE = QuantityUnit("package")
        val SERVING = QuantityUnit("serving")
        val CAN = QuantityUnit("can")
        val BOTTLE = QuantityUnit("bottle")
        val BAG = QuantityUnit("bag")
        val BUNCH = QuantityUnit("bunch")
        val CLOVE = QuantityUnit("clove")
        val SLICE = QuantityUnit("slice")
        val GRAM = QuantityUnit("gram")
        val KILOGRAM = QuantityUnit("kilogram")
        val OUNCE = QuantityUnit("ounce")
        val POUND = QuantityUnit("pound")
        val MILLILITER = QuantityUnit("milliliter")
        val LITER = QuantityUnit("liter")
        val TEASPOON = QuantityUnit("teaspoon")
        val TABLESPOON = QuantityUnit("tablespoon")
        val CUP = QuantityUnit("cup")
        val FLUID_OUNCE = QuantityUnit("fluid_ounce")
        val PINT = QuantityUnit("pint")
        val QUART = QuantityUnit("quart")
        val GALLON = QuantityUnit("gallon")

        val SUPPORTED: List<QuantityUnit> = listOf(
            EACH, PACKAGE, SERVING, CAN, BOTTLE, BAG, BUNCH, CLOVE, SLICE,
            GRAM, KILOGRAM, OUNCE, POUND,
            MILLILITER, LITER, TEASPOON, TABLESPOON, CUP, FLUID_OUNCE, PINT, QUART, GALLON,
        )
    }
}

data class Quantity(
    val amount: DecimalAmount?,
    val unit: QuantityUnit,
) {
    val isKnown: Boolean get() = amount != null
    val isZero: Boolean get() = amount?.toBigDecimal() == BigDecimal.ZERO

    companion object {
        fun unknown(unit: QuantityUnit = QuantityUnit.UNKNOWN): Quantity = Quantity(null, unit)
        fun zero(unit: QuantityUnit): Quantity = Quantity(DecimalAmount.of("0"), unit)
    }
}

data class Money(
    val minorUnits: Long,
    val currencyCode: String,
) {
    init {
        require(Regex("[A-Z]{3}").matches(currencyCode)) {
            "Currency code must use uppercase ISO 4217 format."
        }
    }

    operator fun plus(other: Money): Money {
        require(currencyCode == other.currencyCode) { "Cannot add different currencies." }
        return copy(minorUnits = Math.addExact(minorUnits, other.minorUnits))
    }
}

enum class SourceKind {
    MANUAL,
    DATA_HOME_HUMAN,
    DETERMINISTIC_IMPORT,
    AI_PROPOSAL,
    RECEIPT,
    RECIPE,
    SHOPPING_LINE,
    SYSTEM,
}

data class SourceRef(
    val kind: SourceKind,
    val label: String,
    val actorId: String? = null,
    val deviceId: String? = null,
    val externalReference: String? = null,
) {
    init { requireText(label, "Source label") }
}

data class Confidence(
    val basisPoints: Int,
    val rationale: String? = null,
) {
    init { require(basisPoints in 0..10_000) { "Confidence must be between 0 and 10000 basis points." } }
}

data class EntityMetadata(
    val id: EntityId,
    val householdId: HouseholdId,
    val createdAt: UtcTimestamp,
    val updatedAt: UtcTimestamp,
    val archivedAt: UtcTimestamp? = null,
    val revision: Long = 0,
    val source: SourceRef,
    val confidence: Confidence? = null,
) {
    init {
        require(updatedAt.epochMillis >= createdAt.epochMillis) { "Updated time precedes created time." }
        archivedAt?.let {
            require(it.epochMillis >= createdAt.epochMillis) { "Archived time precedes created time." }
        }
        require(revision >= 0) { "Revision must not be negative." }
    }
}

enum class DataHomeKind { LOCAL, NOTION, GOOGLE_SHEETS, POSTGRES }
enum class HouseholdRole { OWNER, MEMBER, VIEWER }
enum class ItemKind { FOOD, HOUSEHOLD, CLEANING, PERSONAL_CARE, MEDICINE, PET, OTHER }
enum class StorageLocationType { FRIDGE, FREEZER, PANTRY, CABINET, BATHROOM, GARAGE, MEDICINE, OTHER }
enum class InventoryLotStatus { AVAILABLE, OPENED, RESERVED, CONSUMED, DISCARDED, ARCHIVED }
enum class InventoryEventType { ADD, ADJUST, MOVE, OPEN, CONSUME, DISCARD, ARCHIVE, RESTORE }
enum class ShoppingListStatus { ACTIVE, COMPLETED, ARCHIVED }
enum class ShoppingLineStatus { NEEDED, IN_CART, PURCHASED, SKIPPED, ARCHIVED }
enum class ShoppingReason { MANUAL, LOW_STOCK, RECIPE_GAP, HOUSEHOLD_STAPLE, RECEIPT_REORDER, AI_SUGGESTION }
enum class RecipeStatus { DRAFT, ACTIVE, ARCHIVED }
enum class CookingSessionState { READY, COOKING, PAUSED, FINISHED, ABANDONED }
enum class MealPlanStatus { DRAFT, ACTIVE, COMPLETED, ARCHIVED, TEMPLATE }
enum class MealEntryStatus { PROPOSED, PLANNED, COOKED, EATEN, SKIPPED, ARCHIVED }
enum class PurchaseStatus { DRAFT, REVIEWED, RECONCILED, REFUNDED, ARCHIVED }
enum class PurchaseLineDisposition { INVENTORY, CONSUMED, SERVICE, IGNORED }
enum class SpendingCategory {
    FOOD,
    HOUSEHOLD,
    CLEANING,
    PERSONAL_CARE,
    MEDICINE,
    PET,
    OTHER,
    UNCERTAIN,
}
enum class ReviewState { PENDING, ACCEPTED, REJECTED, EXPIRED }
enum class AttachmentKind { IMAGE, DOCUMENT, RECEIPT, BARCODE, VOICE, LINK }
enum class HouseholdEntityType {
    ITEM,
    FOOD_DETAILS,
    INVENTORY_LOT,
    STORAGE_LOCATION,
    INVENTORY_EVENT,
    SHOPPING_LIST,
    SHOPPING_LINE,
    RECIPE,
    RECIPE_INGREDIENT,
    RECIPE_STEP,
    COOKING_SESSION,
    PREPARED_BATCH,
    MEAL_PLAN,
    MEAL_ENTRY,
    MERCHANT,
    PURCHASE,
    PURCHASE_LINE,
    WASTE_EVENT,
    NUTRITION_SNAPSHOT,
    ATTACHMENT,
}

data class Household(
    val id: HouseholdId,
    val name: String,
    val defaultCurrency: String,
    val timezone: String,
    val locale: String,
    val activeDataHome: DataHomeKind,
    val schemaVersion: Int,
    val createdAt: UtcTimestamp,
    val updatedAt: UtcTimestamp,
    val revision: Long,
) {
    init {
        requireText(name, "Household name")
        require(Regex("[A-Z]{3}").matches(defaultCurrency)) { "Default currency must be ISO 4217." }
        requireText(timezone, "Timezone")
        requireText(locale, "Locale")
        require(schemaVersion > 0) { "Schema version must be positive." }
        require(revision >= 0) { "Revision must not be negative." }
    }
}

data class Profile(
    val metadata: EntityMetadata,
    val displayName: String,
    val role: HouseholdRole,
    val dietaryTags: Set<String> = emptySet(),
    val allergies: Set<String> = emptySet(),
    val hardExclusions: Set<String> = emptySet(),
    val dislikes: Set<String> = emptySet(),
    val nutritionGoals: Map<String, DecimalAmount> = emptyMap(),
    val budgetSensitivity: String? = null,
) {
    init { requireText(displayName, "Profile display name") }
}

data class Item(
    val metadata: EntityMetadata,
    val name: String,
    val kind: ItemKind,
    val category: String? = null,
    val brand: String? = null,
    val identifiers: Map<String, String> = emptyMap(),
    val aliases: Set<String> = emptySet(),
    val defaultUnit: QuantityUnit = QuantityUnit.UNKNOWN,
    val preferredStore: String? = null,
    val refillThreshold: Quantity? = null,
    val notes: String? = null,
    val attachmentIds: List<EntityId> = emptyList(),
    val foodDetailsId: EntityId? = null,
) {
    init {
        requireText(name, "Item name")
        require(kind == ItemKind.FOOD || foodDetailsId == null) {
            "Only food items may reference food details."
        }
    }
}

data class FoodDetails(
    val metadata: EntityMetadata,
    val itemId: EntityId,
    val dietaryTags: Set<String> = emptySet(),
    val allergenTags: Set<String> = emptySet(),
    val typicalShelfLifeDays: Int? = null,
    val defaultServing: Quantity? = null,
    val nutritionSnapshotIds: List<EntityId> = emptyList(),
    val externalIdentifiers: Map<String, String> = emptyMap(),
) {
    init { typicalShelfLifeDays?.let { require(it >= 0) { "Shelf life must not be negative." } } }
}

data class StorageLocation(
    val metadata: EntityMetadata,
    val name: String,
    val type: StorageLocationType,
    val parentLocationId: EntityId? = null,
    val sortOrder: Int = 0,
) {
    init { requireText(name, "Storage location name") }
}

data class InventoryLot(
    val metadata: EntityMetadata,
    val itemId: EntityId,
    val quantity: Quantity,
    val locationId: EntityId? = null,
    val purchasedAt: UtcTimestamp? = null,
    val openedAt: UtcTimestamp? = null,
    val expiresOn: CalendarDate? = null,
    val purchaseLineId: EntityId? = null,
    val unitCost: Money? = null,
    val status: InventoryLotStatus = InventoryLotStatus.AVAILABLE,
)

data class InventoryEvent(
    val metadata: EntityMetadata,
    val itemId: EntityId,
    val lotId: EntityId? = null,
    val type: InventoryEventType,
    val quantityDelta: Quantity? = null,
    val reason: String? = null,
    val relatedEntityId: EntityId? = null,
    val commandId: CommandId,
)

data class ShoppingList(
    val metadata: EntityMetadata,
    val name: String,
    val status: ShoppingListStatus,
    val merchantId: EntityId? = null,
    val plannedFor: CalendarDate? = null,
) {
    init { requireText(name, "Shopping list name") }
}

data class ShoppingLine(
    val metadata: EntityMetadata,
    val shoppingListId: EntityId,
    val itemId: EntityId? = null,
    val displayName: String,
    val quantity: Quantity = Quantity.unknown(),
    val category: String? = null,
    val preferredStore: String? = null,
    val status: ShoppingLineStatus = ShoppingLineStatus.NEEDED,
    val reason: ShoppingReason = ShoppingReason.MANUAL,
    val sourceEntityIds: List<EntityId> = emptyList(),
    val estimatedPrice: Money? = null,
) {
    init { requireText(displayName, "Shopping line name") }
}

data class Recipe(
    val metadata: EntityMetadata,
    val name: String,
    val description: String? = null,
    val sourceUrl: String? = null,
    val sourceProvider: String? = null,
    val author: String? = null,
    val cuisine: String? = null,
    val category: String? = null,
    val tags: Set<String> = emptySet(),
    val yield: Quantity = Quantity.unknown(QuantityUnit.SERVING),
    val prepMinutes: Int? = null,
    val cookMinutes: Int? = null,
    val totalMinutes: Int? = null,
    val difficulty: String? = null,
    val status: RecipeStatus = RecipeStatus.DRAFT,
    val ingredientIds: List<EntityId> = emptyList(),
    val stepIds: List<EntityId> = emptyList(),
    val attachmentIds: List<EntityId> = emptyList(),
    val nutritionSnapshotIds: List<EntityId> = emptyList(),
) {
    init {
        requireText(name, "Recipe name")
        listOf(prepMinutes, cookMinutes, totalMinutes).filterNotNull().forEach {
            require(it >= 0) { "Recipe duration must not be negative." }
        }
    }
}

data class RecipeIngredient(
    val metadata: EntityMetadata,
    val recipeId: EntityId,
    val itemId: EntityId? = null,
    val originalText: String,
    val quantity: Quantity = Quantity.unknown(),
    val preparation: String? = null,
    val optional: Boolean = false,
    val section: String? = null,
    val order: Int,
    val substituteItemIds: List<EntityId> = emptyList(),
) {
    init {
        requireText(originalText, "Ingredient text")
        require(order >= 0) { "Ingredient order must not be negative." }
    }
}

data class RecipeStep(
    val metadata: EntityMetadata,
    val recipeId: EntityId,
    val section: String? = null,
    val order: Int,
    val instruction: String,
    val durationMinutes: Int? = null,
    val timerLabel: String? = null,
    val ingredientIds: List<EntityId> = emptyList(),
    val attachmentIds: List<EntityId> = emptyList(),
) {
    init {
        require(order >= 0) { "Recipe step order must not be negative." }
        requireText(instruction, "Recipe step instruction")
        durationMinutes?.let { require(it >= 0) { "Step duration must not be negative." } }
    }
}

data class CookingSession(
    val metadata: EntityMetadata,
    val recipeId: EntityId,
    val startedAt: UtcTimestamp? = null,
    val finishedAt: UtcTimestamp? = null,
    val currentStep: Int = 0,
    val servings: Quantity = Quantity.unknown(QuantityUnit.SERVING),
    val state: CookingSessionState = CookingSessionState.READY,
    val preparedBatchId: EntityId? = null,
) {
    init { require(currentStep >= 0) { "Current step must not be negative." } }
}

data class PreparedBatch(
    val metadata: EntityMetadata,
    val recipeId: EntityId? = null,
    val mealEntryId: EntityId? = null,
    val preparedAt: UtcTimestamp,
    val totalQuantity: Quantity,
    val remainingQuantity: Quantity,
    val storageLocationId: EntityId? = null,
    val consumeBy: CalendarDate? = null,
    val nutritionSnapshotId: EntityId? = null,
)

data class MealPlan(
    val metadata: EntityMetadata,
    val name: String,
    val startsOn: CalendarDate,
    val endsOn: CalendarDate,
    val status: MealPlanStatus,
    val targetProfileIds: List<EntityId> = emptyList(),
    val budget: Money? = null,
    val nutritionTargetSnapshotId: EntityId? = null,
) {
    init { requireText(name, "Meal plan name") }
}

data class MealEntry(
    val metadata: EntityMetadata,
    val mealPlanId: EntityId? = null,
    val scheduledAt: UtcTimestamp,
    val slot: String,
    val recipeId: EntityId? = null,
    val preparedBatchId: EntityId? = null,
    val title: String,
    val servings: Quantity = Quantity.unknown(QuantityUnit.SERVING),
    val status: MealEntryStatus,
    val leftoverIntent: String? = null,
    val nutritionSnapshotIds: List<EntityId> = emptyList(),
    val notes: String? = null,
) {
    init {
        requireText(slot, "Meal slot")
        requireText(title, "Meal title")
    }
}

data class Merchant(
    val metadata: EntityMetadata,
    val name: String,
    val category: String? = null,
    val location: String? = null,
    val externalIdentifiers: Map<String, String> = emptyMap(),
) {
    init { requireText(name, "Merchant name") }
}

data class Purchase(
    val metadata: EntityMetadata,
    val merchantId: EntityId? = null,
    val occurredAt: UtcTimestamp,
    val receiptAttachmentIds: List<EntityId> = emptyList(),
    val subtotal: Money? = null,
    val tax: Money? = null,
    val discount: Money? = null,
    val tip: Money? = null,
    val total: Money? = null,
    val paymentNote: String? = null,
    val status: PurchaseStatus,
    val reconciliationDifference: Money? = null,
)

data class PurchaseLine(
    val metadata: EntityMetadata,
    val purchaseId: EntityId,
    val itemId: EntityId? = null,
    val shoppingLineId: EntityId? = null,
    val displayName: String,
    val quantity: Quantity = Quantity.unknown(),
    val unitPrice: Money? = null,
    val lineSubtotal: Money? = null,
    val discount: Money? = null,
    val taxAllocation: Money? = null,
    val finalAmount: Money? = null,
    val spendCategory: String? = null,
    val disposition: PurchaseLineDisposition,
    val inventoryLotId: EntityId? = null,
    val reviewState: ReviewState,
) {
    init { requireText(displayName, "Purchase line name") }
}

data class WasteEvent(
    val metadata: EntityMetadata,
    val inventoryLotId: EntityId,
    val quantity: Quantity,
    val reason: String,
    val estimatedCost: Money? = null,
    val occurredAt: UtcTimestamp,
) {
    init { requireText(reason, "Waste reason") }
}

data class EntityReference(
    val type: HouseholdEntityType,
    val id: EntityId,
)

data class NutritionValues(
    val energyKcal: DecimalAmount? = null,
    val proteinGrams: DecimalAmount? = null,
    val carbohydrateGrams: DecimalAmount? = null,
    val fatGrams: DecimalAmount? = null,
    val fiberGrams: DecimalAmount? = null,
    val sugarGrams: DecimalAmount? = null,
    val sodiumMilligrams: DecimalAmount? = null,
)

data class NutritionSnapshot(
    val metadata: EntityMetadata,
    val subject: EntityReference,
    val basis: Quantity,
    val values: NutritionValues,
    val provider: String? = null,
    val capturedAt: UtcTimestamp,
    val warnings: List<String> = emptyList(),
)

data class Attachment(
    val metadata: EntityMetadata,
    val kind: AttachmentKind,
    val localUri: String? = null,
    val remoteReference: String? = null,
    val mimeType: String? = null,
    val checksum: String? = null,
    val label: String? = null,
    val capturedAt: UtcTimestamp,
) {
    init { require(localUri != null || remoteReference != null) { "Attachment requires a local or remote reference." } }
}

data class CommandIntent(
    val type: String,
    val payloadHash: String,
) {
    init {
        requireText(type, "Command intent type")
        requireText(payloadHash, "Command payload hash")
    }
}

data class ChangeProposal(
    val metadata: EntityMetadata,
    val sourcePayloadReference: String,
    val requestedCommands: List<CommandIntent>,
    val warnings: List<String> = emptyList(),
    val status: ReviewState,
    val reviewedAt: UtcTimestamp? = null,
    val reviewer: String? = null,
) {
    init { requireText(sourcePayloadReference, "Proposal source reference") }
}

data class CommandRecord(
    val commandId: CommandId,
    val householdId: HouseholdId,
    val type: String,
    val source: SourceRef,
    val requestedAt: UtcTimestamp,
    val appliedAt: UtcTimestamp?,
    val affectedEntityIds: List<EntityId>,
    val beforeHash: String? = null,
    val afterHash: String? = null,
    val undoCommandId: CommandId? = null,
) {
    init { requireText(type, "Command type") }
}

data class HouseholdSnapshot(
    val household: Household,
    val profiles: List<Profile> = emptyList(),
    val items: List<Item> = emptyList(),
    val foodDetails: List<FoodDetails> = emptyList(),
    val storageLocations: List<StorageLocation> = emptyList(),
    val inventoryLots: List<InventoryLot> = emptyList(),
    val inventoryEvents: List<InventoryEvent> = emptyList(),
    val shoppingLists: List<ShoppingList> = emptyList(),
    val shoppingLines: List<ShoppingLine> = emptyList(),
    val recipes: List<Recipe> = emptyList(),
    val recipeIngredients: List<RecipeIngredient> = emptyList(),
    val recipeSteps: List<RecipeStep> = emptyList(),
    val cookingSessions: List<CookingSession> = emptyList(),
    val preparedBatches: List<PreparedBatch> = emptyList(),
    val mealPlans: List<MealPlan> = emptyList(),
    val mealEntries: List<MealEntry> = emptyList(),
    val merchants: List<Merchant> = emptyList(),
    val purchases: List<Purchase> = emptyList(),
    val purchaseLines: List<PurchaseLine> = emptyList(),
    val wasteEvents: List<WasteEvent> = emptyList(),
    val nutritionSnapshots: List<NutritionSnapshot> = emptyList(),
    val attachments: List<Attachment> = emptyList(),
    val proposals: List<ChangeProposal> = emptyList(),
    val remoteBindings: List<RemoteBinding> = emptyList(),
    val syncBases: List<SyncBase> = emptyList(),
    val syncCursors: List<SyncCursor> = emptyList(),
    val conflicts: List<ConflictRecord> = emptyList(),
    val latestSafetySnapshots: List<LatestSafetySnapshot> = emptyList(),
    val recoverySnapshots: List<RecoverySnapshot> = emptyList(),
    val tombstones: List<TombstoneRecord> = emptyList(),
    val commandRecords: List<CommandRecord> = emptyList(),
)

fun HouseholdSnapshot.spendingMinorUnitsByCurrency(): Map<String, Long> =
    purchaseLines
        .asSequence()
        .filter { it.metadata.archivedAt == null }
        .filter { it.disposition != PurchaseLineDisposition.IGNORED }
        .mapNotNull { it.finalAmount }
        .groupBy { it.currencyCode }
        .mapValues { (_, amounts) -> amounts.sumOf { it.minorUnits } }

fun Purchase.calculatedReconciliationDifference(lines: List<PurchaseLine> = emptyList()): Money? {
    val actualTotal = total ?: return null
    val currency = actualTotal.currencyCode
    val base = subtotal?.takeIfCurrency(currency)
        ?: lines
            .asSequence()
            .filter { it.metadata.archivedAt == null }
            .filter { it.disposition != PurchaseLineDisposition.IGNORED }
            .mapNotNull { it.lineSubtotal ?: it.finalAmount }
            .filter { it.currencyCode == currency }
            .toList()
            .takeIf { it.isNotEmpty() }
            ?.sumAsMoney(currency)
        ?: return null
    val expectedMinorUnits = base.minorUnits +
        (tax?.takeIfCurrency(currency)?.minorUnits ?: 0L) +
        (tip?.takeIfCurrency(currency)?.minorUnits ?: 0L) -
        (discount?.takeIfCurrency(currency)?.minorUnits ?: 0L)
    return Money(actualTotal.minorUnits - expectedMinorUnits, currency)
}

fun Purchase.isReconciled(lines: List<PurchaseLine> = emptyList(), toleranceMinorUnits: Long = 0L): Boolean =
    calculatedReconciliationDifference(lines)?.let { kotlin.math.abs(it.minorUnits) <= toleranceMinorUnits } ?: false

fun PurchaseLine.resolvedSpendingCategory(itemKind: ItemKind? = null): SpendingCategory =
    spendCategory.toSpendingCategory()
        ?: when (itemKind) {
            ItemKind.FOOD -> SpendingCategory.FOOD
            ItemKind.HOUSEHOLD -> SpendingCategory.HOUSEHOLD
            ItemKind.CLEANING -> SpendingCategory.CLEANING
            ItemKind.PERSONAL_CARE -> SpendingCategory.PERSONAL_CARE
            ItemKind.MEDICINE -> SpendingCategory.MEDICINE
            ItemKind.PET -> SpendingCategory.PET
            ItemKind.OTHER -> SpendingCategory.OTHER
            null -> SpendingCategory.UNCERTAIN
        }

fun String?.toSpendingCategory(): SpendingCategory? =
    this
        ?.trim()
        ?.lowercase()
        ?.replace('_', '-')
        ?.let { normalized ->
            when (normalized) {
                "food", "grocery", "groceries", "produce", "dairy", "meat", "seafood", "bakery", "pantry" -> SpendingCategory.FOOD
                "household", "home", "paper-goods", "paper goods" -> SpendingCategory.HOUSEHOLD
                "cleaning", "cleaner", "cleaners" -> SpendingCategory.CLEANING
                "personal-care", "personal care", "toiletries", "hygiene" -> SpendingCategory.PERSONAL_CARE
                "medicine", "medical", "pharmacy", "health" -> SpendingCategory.MEDICINE
                "pet", "pets", "pet-care", "pet care" -> SpendingCategory.PET
                "other", "misc", "miscellaneous" -> SpendingCategory.OTHER
                "uncertain", "unknown", "unsure", "needs-review", "needs review" -> SpendingCategory.UNCERTAIN
                else -> null
            }
        }

fun HouseholdSnapshot.onHand(itemId: EntityId, unit: QuantityUnit): Quantity {
    val known = inventoryLots
        .asSequence()
        .filter { it.metadata.archivedAt == null && it.itemId == itemId }
        .filter { it.status in setOf(InventoryLotStatus.AVAILABLE, InventoryLotStatus.OPENED, InventoryLotStatus.RESERVED) }
        .filter { it.quantity.unit == unit }
        .mapNotNull { it.quantity.amount?.toBigDecimal() }
        .toList()
    if (known.isEmpty()) return Quantity.unknown(unit)
    return Quantity(DecimalAmount.of(known.fold(BigDecimal.ZERO, BigDecimal::add).toPlainString()), unit)
}

private fun Money.takeIfCurrency(currency: String): Money? =
    takeIf { it.currencyCode == currency }

private fun List<Money>.sumAsMoney(currency: String): Money =
    Money(sumOf { it.minorUnits }, currency)
