package com.wonderfood.core.data.room

import com.wonderfood.core.data.HouseholdRepository
import com.wonderfood.core.engine.HouseholdCommand
import com.wonderfood.core.engine.HouseholdCommandExecutionResult
import com.wonderfood.core.model.AttachmentKind as LegacyAttachmentKind
import com.wonderfood.core.model.SourceKind as LegacySourceKind
import com.wonderfood.core.model.household.CalendarDate
import com.wonderfood.core.model.household.ChangeProposal
import com.wonderfood.core.model.household.CommandId
import com.wonderfood.core.model.household.CommandIntent
import com.wonderfood.core.model.household.CommandRecord
import com.wonderfood.core.model.household.ConnectionId
import com.wonderfood.core.model.household.ConflictRecord
import com.wonderfood.core.model.household.CookingSession
import com.wonderfood.core.model.household.CookingSessionState
import com.wonderfood.core.model.household.Attachment
import com.wonderfood.core.model.household.Confidence
import com.wonderfood.core.model.household.DataHomeAdapterOperation
import com.wonderfood.core.model.household.DataHomeKind
import com.wonderfood.core.model.household.DecimalAmount
import com.wonderfood.core.model.household.EntityId
import com.wonderfood.core.model.household.EntityMetadata
import com.wonderfood.core.model.household.FoodDetails
import com.wonderfood.core.model.household.Household
import com.wonderfood.core.model.household.HouseholdEntityType
import com.wonderfood.core.model.household.HouseholdRole
import com.wonderfood.core.model.household.HouseholdId
import com.wonderfood.core.model.household.HouseholdSnapshot
import com.wonderfood.core.model.household.InventoryEvent
import com.wonderfood.core.model.household.InventoryEventType
import com.wonderfood.core.model.household.InventoryLot
import com.wonderfood.core.model.household.InventoryLotStatus
import com.wonderfood.core.model.household.Item
import com.wonderfood.core.model.household.ItemKind
import com.wonderfood.core.model.household.Merchant
import com.wonderfood.core.model.household.MealEntry
import com.wonderfood.core.model.household.MealPlan
import com.wonderfood.core.model.household.Money
import com.wonderfood.core.model.household.NutritionSnapshot
import com.wonderfood.core.model.household.NutritionValues
import com.wonderfood.core.model.household.PayloadHash
import com.wonderfood.core.model.household.PreparedBatch
import com.wonderfood.core.model.household.Profile
import com.wonderfood.core.model.household.Purchase
import com.wonderfood.core.model.household.PurchaseLine
import com.wonderfood.core.model.household.Quantity
import com.wonderfood.core.model.household.QuantityUnit
import com.wonderfood.core.model.household.Recipe
import com.wonderfood.core.model.household.RecipeIngredient
import com.wonderfood.core.model.household.RecipeStep
import com.wonderfood.core.model.household.RecoverySnapshot
import com.wonderfood.core.model.household.RemoteBinding
import com.wonderfood.core.model.household.ReviewState
import com.wonderfood.core.model.household.ShoppingList
import com.wonderfood.core.model.household.ShoppingListStatus
import com.wonderfood.core.model.household.ShoppingLine
import com.wonderfood.core.model.household.ShoppingLineStatus
import com.wonderfood.core.model.household.ShoppingReason
import com.wonderfood.core.model.household.SourceKind
import com.wonderfood.core.model.household.SourceRef
import com.wonderfood.core.model.household.StorageLocation
import com.wonderfood.core.model.household.StorageLocationType
import com.wonderfood.core.model.household.SyncBase
import com.wonderfood.core.model.household.SyncCursor
import com.wonderfood.core.model.household.SyncDecision
import com.wonderfood.core.model.household.SyncDecisionAction
import com.wonderfood.core.model.household.LatestSafetySnapshot
import com.wonderfood.core.model.household.SyncOutboxRecord
import com.wonderfood.core.model.household.SyncOutboxStatus
import com.wonderfood.core.model.household.SyncRecordEnvelope
import com.wonderfood.core.model.household.TombstoneReason
import com.wonderfood.core.model.household.TombstoneRecord
import com.wonderfood.core.model.household.UtcTimestamp
import com.wonderfood.core.model.household.WasteEvent
import java.time.Instant
import kotlinx.coroutines.flow.first

public class RoomHouseholdRepository(
    private val database: WonderFoodDatabase,
) : HouseholdRepository {
    private val dao: HouseholdDao = database.householdDao()
    private val legacyDao: WonderFoodDao = database.wonderFoodDao()

    override suspend fun apply(command: HouseholdCommand): HouseholdCommandExecutionResult {
        val applied = when (command) {
            is HouseholdCommand.UpsertHousehold ->
                dao.upsertHouseholdAndRecord(command.household.toEntity(), command.record.toEntity())
            is HouseholdCommand.UpsertProfile ->
                dao.upsertProfileAndRecord(command.profile.toEntity(), command.record.toEntity())
            is HouseholdCommand.UpsertItem ->
                dao.upsertItemAndRecord(command.item.toEntity(), command.record.toEntity())
            is HouseholdCommand.UpsertFoodDetails ->
                dao.upsertFoodDetailsAndRecord(command.details.toEntity(), command.record.toEntity())
            is HouseholdCommand.UpsertStorageLocation ->
                dao.upsertStorageLocationAndRecord(command.location.toEntity(), command.record.toEntity())
            is HouseholdCommand.UpsertInventoryLot ->
                dao.upsertInventoryLotAndRecord(command.lot.toEntity(), command.record.toEntity())
            is HouseholdCommand.StoreInventoryEvent ->
                dao.upsertInventoryEventAndRecord(command.event.toEntity(), command.record.toEntity())
            is HouseholdCommand.UpsertShoppingList ->
                dao.upsertShoppingListAndRecord(command.list.toEntity(), command.record.toEntity())
            is HouseholdCommand.UpsertShoppingLine ->
                dao.upsertShoppingLineAndRecord(command.line.toEntity(), command.record.toEntity())
            is HouseholdCommand.UpsertRecipe ->
                dao.upsertRecipeAndRecord(command.recipe.toEntity(), command.record.toEntity())
            is HouseholdCommand.UpsertRecipeIngredient ->
                dao.upsertRecipeIngredientAndRecord(command.ingredient.toEntity(), command.record.toEntity())
            is HouseholdCommand.UpsertRecipeStep ->
                dao.upsertRecipeStepAndRecord(command.step.toEntity(), command.record.toEntity())
            is HouseholdCommand.UpsertCookingSession ->
                dao.upsertCookingSessionAndRecord(command.session.toEntity(), command.record.toEntity())
            is HouseholdCommand.UpsertPreparedBatch ->
                dao.upsertPreparedBatchAndRecord(command.batch.toEntity(), command.record.toEntity())
            is HouseholdCommand.UpsertMerchant ->
                dao.upsertMerchantAndRecord(command.merchant.toEntity(), command.record.toEntity())
            is HouseholdCommand.UpsertPurchase ->
                dao.upsertPurchaseAndRecord(command.purchase.toEntity(), command.record.toEntity())
            is HouseholdCommand.UpsertPurchaseLine ->
                dao.upsertPurchaseLineAndRecord(command.line.toEntity(), command.record.toEntity())
            is HouseholdCommand.StoreWasteEvent ->
                dao.upsertWasteEventAndRecord(command.wasteEvent.toEntity(), command.record.toEntity())
            is HouseholdCommand.UpsertMealPlan ->
                dao.upsertMealPlanAndRecord(command.plan.toEntity(), command.record.toEntity())
            is HouseholdCommand.UpsertMealEntry ->
                dao.upsertMealEntryAndRecord(command.entry.toEntity(), command.record.toEntity())
            is HouseholdCommand.UpsertNutritionSnapshot ->
                dao.upsertNutritionSnapshotAndRecord(command.snapshot.toEntity(), command.record.toEntity())
            is HouseholdCommand.StoreProposal ->
                dao.upsertChangeProposalAndRecord(command.proposal.toEntity(), command.record.toEntity())
            is HouseholdCommand.EnqueueOutbox ->
                dao.upsertSyncOutboxAndRecord(command.outbox.toEntity(), command.record.toEntity())
            is HouseholdCommand.UpsertAttachment ->
                upsertAttachmentAndRecord(command.attachment, command.record.toEntity())
            is HouseholdCommand.StoreTombstone ->
                dao.upsertTombstoneAndRecord(command.tombstone.toEntity(), command.record.toEntity())
            is HouseholdCommand.StoreRemoteBinding ->
                dao.upsertRemoteBindingAndRecord(command.binding.toEntity(command.record.householdId), command.record.toEntity())
            is HouseholdCommand.StoreSyncBase ->
                dao.upsertSyncBaseAndRecord(command.base.toEntity(), command.record.toEntity())
            is HouseholdCommand.StoreSyncCursor ->
                dao.upsertSyncCursorAndRecord(command.cursor.toEntity(), command.record.toEntity())
            is HouseholdCommand.StoreConflict ->
                dao.upsertConflictAndRecord(command.conflict.toEntity(), command.record.toEntity())
            is HouseholdCommand.StoreLatestSafetySnapshot ->
                dao.upsertLatestSafetySnapshotAndRecord(command.safetySnapshot.toEntity(), command.record.toEntity())
            is HouseholdCommand.StoreRecoverySnapshot ->
                dao.upsertRecoverySnapshotAndRecord(command.recoverySnapshot.toEntity(), command.record.toEntity())
        }
        return if (applied) {
            HouseholdCommandExecutionResult.Applied(command.record.commandId)
        } else {
            HouseholdCommandExecutionResult.Duplicate(command.record.commandId)
        }
    }

    override suspend fun snapshot(householdId: HouseholdId): HouseholdSnapshot? {
        val household = dao.getHousehold(householdId.value)?.toDomain() ?: return null
        return HouseholdSnapshot(
            household = household,
            profiles = dao.getProfiles(householdId.value).map { it.toDomain() },
            items = dao.getItems(householdId.value).map { it.toDomain() },
            foodDetails = dao.getFoodDetails(householdId.value).map { it.toDomain() },
            storageLocations = dao.getStorageLocations(householdId.value).map { it.toDomain() },
            inventoryLots = dao.getInventoryLots(householdId.value).map { it.toDomain() },
            inventoryEvents = dao.getInventoryEvents(householdId.value).map { it.toDomain() },
            shoppingLists = dao.getShoppingLists(householdId.value).map { it.toDomain() },
            shoppingLines = dao.getShoppingLines(householdId.value).map { it.toDomain() },
            recipes = dao.getRecipes(householdId.value).map { it.toDomain() },
            recipeIngredients = dao.getRecipeIngredients(householdId.value).map { it.toDomain() },
            recipeSteps = dao.getRecipeSteps(householdId.value).map { it.toDomain() },
            cookingSessions = dao.getCookingSessions(householdId.value).map { it.toDomain() },
            preparedBatches = dao.getPreparedBatches(householdId.value).map { it.toDomain() },
            merchants = dao.getMerchants(householdId.value).map { it.toDomain() },
            purchases = dao.getPurchases(householdId.value).map { it.toDomain() },
            purchaseLines = dao.getPurchaseLines(householdId.value).map { it.toDomain() },
            wasteEvents = dao.getWasteEvents(householdId.value).map { it.toDomain() },
            mealPlans = dao.getMealPlans(householdId.value).map { it.toDomain() },
            mealEntries = dao.getMealEntries(householdId.value).map { it.toDomain() },
            nutritionSnapshots = dao.getNutritionSnapshots(householdId.value).map { it.toDomain() },
            attachments = legacyDao.observeAttachments().first().map { it.toHouseholdDomain(householdId) },
            proposals = dao.getChangeProposals(householdId.value).map { it.toDomain() },
            remoteBindings = dao.getRemoteBindings(householdId.value).map { it.toDomain() },
            syncBases = dao.getSyncBases(householdId.value).map { it.toDomain() },
            syncCursors = dao.getSyncCursors(householdId.value).map { it.toDomain() },
            conflicts = dao.getConflicts(householdId.value).map { it.toDomain() },
            latestSafetySnapshots = dao.getLatestSafetySnapshots(householdId.value).map { it.toDomain() },
            recoverySnapshots = dao.getRecoverySnapshots(householdId.value).map { it.toDomain() },
            tombstones = dao.getTombstones(householdId.value).map { it.toDomain() },
            commandRecords = dao.getCommandRecords(householdId.value).map { it.toDomain() },
        )
    }

    override suspend fun searchItems(householdId: HouseholdId, query: String): List<Item> {
        val normalized = query.trim()
        if (normalized.isBlank()) return emptyList()
        return dao.searchItems(householdId.value, normalized).map { it.toDomain() }
    }

    private suspend fun upsertAttachmentAndRecord(attachment: Attachment, record: HouseholdCommandRecordEntity): Boolean =
        database.inTransaction {
            if (dao.getCommandRecord(record.id) != null) return@inTransaction false
            legacyDao.upsertAttachment(attachment.toEntity())
            dao.upsertCommandRecord(record)
            true
        }
}

private fun Attachment.toEntity(): AttachmentEntity = AttachmentEntity(
    id = metadata.id.value,
    kind = kind.toLegacyAttachmentKind(),
    uri = localUri ?: remoteReference ?: "unknown://household-attachment",
    label = label,
    checksum = checksum,
    source = SourceColumns(
        id = "${metadata.id.value}:source",
        kind = metadata.source.toLegacySourceKind(),
        label = metadata.source.label,
        externalId = metadata.source.externalReference,
        uri = metadata.source.externalReference,
        capturedAt = Instant.ofEpochMilli(metadata.createdAt.epochMillis).toString(),
        truthState = com.wonderfood.core.model.TruthState.UNKNOWN,
    ),
    confidence = ConfidenceColumns(
        score = null,
        state = com.wonderfood.core.model.TruthState.UNKNOWN,
        rationale = null,
    ),
    truthState = com.wonderfood.core.model.TruthState.UNKNOWN,
    createdAt = Instant.ofEpochMilli(metadata.createdAt.epochMillis).toString(),
    updatedAt = Instant.ofEpochMilli(metadata.updatedAt.epochMillis).toString(),
    archivedAt = metadata.archivedAt?.let { Instant.ofEpochMilli(it.epochMillis).toString() },
    deletedAt = null,
)

private fun com.wonderfood.core.model.household.AttachmentKind.toLegacyAttachmentKind(): LegacyAttachmentKind =
    when (this) {
        com.wonderfood.core.model.household.AttachmentKind.IMAGE -> LegacyAttachmentKind.IMAGE
        com.wonderfood.core.model.household.AttachmentKind.DOCUMENT -> LegacyAttachmentKind.DOCUMENT
        com.wonderfood.core.model.household.AttachmentKind.RECEIPT -> LegacyAttachmentKind.RECEIPT_PHOTO
        com.wonderfood.core.model.household.AttachmentKind.BARCODE -> LegacyAttachmentKind.BARCODE
        com.wonderfood.core.model.household.AttachmentKind.VOICE -> LegacyAttachmentKind.VOICE_NOTE
        com.wonderfood.core.model.household.AttachmentKind.LINK -> LegacyAttachmentKind.LINK
    }

private fun AttachmentEntity.toHouseholdDomain(householdId: HouseholdId): Attachment = Attachment(
    metadata = EntityMetadata(
        id = EntityId(id),
        householdId = householdId,
        createdAt = UtcTimestamp(Instant.parse(createdAt).toEpochMilli()),
        updatedAt = UtcTimestamp(Instant.parse(updatedAt).toEpochMilli()),
        archivedAt = archivedAt?.let { UtcTimestamp(Instant.parse(it).toEpochMilli()) },
        source = source.toHouseholdSourceRef(),
        confidence = confidence.score?.let { Confidence((it * 10_000).toInt(), confidence.rationale) },
    ),
    kind = kind.toHouseholdAttachmentKind(),
    localUri = uri.takeIf { it.startsWith("content://") || it.startsWith("file://") },
    remoteReference = uri.takeUnless { it.startsWith("content://") || it.startsWith("file://") },
    checksum = checksum,
    label = label,
    capturedAt = UtcTimestamp(Instant.parse(createdAt).toEpochMilli()),
)

private fun LegacyAttachmentKind.toHouseholdAttachmentKind(): com.wonderfood.core.model.household.AttachmentKind =
    when (this) {
        LegacyAttachmentKind.IMAGE -> com.wonderfood.core.model.household.AttachmentKind.IMAGE
        LegacyAttachmentKind.DOCUMENT -> com.wonderfood.core.model.household.AttachmentKind.DOCUMENT
        LegacyAttachmentKind.RECEIPT_PHOTO -> com.wonderfood.core.model.household.AttachmentKind.RECEIPT
        LegacyAttachmentKind.BARCODE -> com.wonderfood.core.model.household.AttachmentKind.BARCODE
        LegacyAttachmentKind.VOICE_NOTE -> com.wonderfood.core.model.household.AttachmentKind.VOICE
        LegacyAttachmentKind.LINK,
        LegacyAttachmentKind.UNKNOWN -> com.wonderfood.core.model.household.AttachmentKind.LINK
    }

private fun SourceColumns.toHouseholdSourceRef(): SourceRef =
    SourceRef(
        kind = when (kind) {
            LegacySourceKind.USER -> SourceKind.MANUAL
            LegacySourceKind.AI -> SourceKind.AI_PROPOSAL
            LegacySourceKind.RECEIPT -> SourceKind.RECEIPT
            LegacySourceKind.SYSTEM -> SourceKind.SYSTEM
            LegacySourceKind.IMPORT,
            LegacySourceKind.BARCODE,
            LegacySourceKind.NUTRITION_PROVIDER,
            LegacySourceKind.USDA,
            LegacySourceKind.OPEN_FOOD_FACTS,
            LegacySourceKind.HEALTH_CONNECT,
            LegacySourceKind.UNKNOWN -> SourceKind.DETERMINISTIC_IMPORT
        },
        label = label,
        externalReference = externalId ?: uri,
    )

private fun SourceRef.toLegacySourceKind(): LegacySourceKind =
    when (kind) {
        SourceKind.MANUAL,
        SourceKind.DATA_HOME_HUMAN -> LegacySourceKind.USER
        SourceKind.DETERMINISTIC_IMPORT -> LegacySourceKind.IMPORT
        SourceKind.AI_PROPOSAL -> LegacySourceKind.AI
        SourceKind.RECEIPT -> LegacySourceKind.RECEIPT
        SourceKind.RECIPE,
        SourceKind.SHOPPING_LINE -> LegacySourceKind.IMPORT
        SourceKind.SYSTEM -> LegacySourceKind.SYSTEM
    }

private fun Household.toEntity(): HouseholdEntity = HouseholdEntity(
    id = id.value,
    name = name,
    defaultCurrency = defaultCurrency,
    timezone = timezone,
    locale = locale,
    activeDataHome = activeDataHome.name,
    schemaVersion = schemaVersion,
    createdAt = createdAt.epochMillis,
    updatedAt = updatedAt.epochMillis,
    revision = revision,
)

private fun HouseholdEntity.toDomain(): Household = Household(
    id = HouseholdId(id),
    name = name,
    defaultCurrency = defaultCurrency,
    timezone = timezone,
    locale = locale,
    activeDataHome = enumValue(activeDataHome),
    schemaVersion = schemaVersion,
    createdAt = UtcTimestamp(createdAt),
    updatedAt = UtcTimestamp(updatedAt),
    revision = revision,
)

private fun Profile.toEntity(): HouseholdProfileEntity = HouseholdProfileEntity(
    id = metadata.id.value,
    householdId = metadata.householdId.value,
    displayName = displayName,
    role = role.name,
    dietaryTags = dietaryTags.toList().sorted(),
    allergies = allergies.toList().sorted(),
    hardExclusions = hardExclusions.toList().sorted(),
    dislikes = dislikes.toList().sorted(),
    nutritionGoals = nutritionGoals.mapValues { it.value.value },
    budgetSensitivity = budgetSensitivity,
    createdAt = metadata.createdAt.epochMillis,
    updatedAt = metadata.updatedAt.epochMillis,
    archivedAt = metadata.archivedAt?.epochMillis,
    revision = metadata.revision,
    sourceKind = metadata.source.kind.name,
    sourceLabel = metadata.source.label,
    sourceActorId = metadata.source.actorId,
    sourceDeviceId = metadata.source.deviceId,
    sourceExternalReference = metadata.source.externalReference,
    confidenceBasisPoints = metadata.confidence?.basisPoints,
    confidenceRationale = metadata.confidence?.rationale,
)

private fun HouseholdProfileEntity.toDomain(): Profile = Profile(
    metadata = metadata(),
    displayName = displayName,
    role = enumValue<HouseholdRole>(role),
    dietaryTags = dietaryTags.toSet(),
    allergies = allergies.toSet(),
    hardExclusions = hardExclusions.toSet(),
    dislikes = dislikes.toSet(),
    nutritionGoals = nutritionGoals.mapValues { DecimalAmount(it.value) },
    budgetSensitivity = budgetSensitivity,
)

private fun Item.toEntity(): HouseholdItemEntity = HouseholdItemEntity(
    id = metadata.id.value,
    householdId = metadata.householdId.value,
    name = name,
    kind = kind.name,
    category = category,
    brand = brand,
    defaultUnit = defaultUnit.code,
    preferredStore = preferredStore,
    notes = notes,
    foodDetailsId = foodDetailsId?.value,
    createdAt = metadata.createdAt.epochMillis,
    updatedAt = metadata.updatedAt.epochMillis,
    archivedAt = metadata.archivedAt?.epochMillis,
    revision = metadata.revision,
    sourceKind = metadata.source.kind.name,
    sourceLabel = metadata.source.label,
    sourceActorId = metadata.source.actorId,
    sourceDeviceId = metadata.source.deviceId,
    sourceExternalReference = metadata.source.externalReference,
    confidenceBasisPoints = metadata.confidence?.basisPoints,
    confidenceRationale = metadata.confidence?.rationale,
)

private fun HouseholdItemEntity.toDomain(): Item = Item(
    metadata = metadata(),
    name = name,
    kind = enumValue(kind),
    category = category,
    brand = brand,
    defaultUnit = QuantityUnit(defaultUnit),
    preferredStore = preferredStore,
    notes = notes,
    foodDetailsId = foodDetailsId?.let(::EntityId),
)

private fun FoodDetails.toEntity(): HouseholdFoodDetailsEntity = HouseholdFoodDetailsEntity(
    id = metadata.id.value,
    householdId = metadata.householdId.value,
    itemId = itemId.value,
    dietaryTags = dietaryTags.toList().sorted(),
    allergenTags = allergenTags.toList().sorted(),
    typicalShelfLifeDays = typicalShelfLifeDays,
    defaultServingAmount = defaultServing?.amount?.value,
    defaultServingUnit = defaultServing?.unit?.code,
    nutritionSnapshotIds = nutritionSnapshotIds.map { it.value },
    externalIdentifiers = externalIdentifiers,
    createdAt = metadata.createdAt.epochMillis,
    updatedAt = metadata.updatedAt.epochMillis,
    archivedAt = metadata.archivedAt?.epochMillis,
    revision = metadata.revision,
    sourceKind = metadata.source.kind.name,
    sourceLabel = metadata.source.label,
    sourceActorId = metadata.source.actorId,
    sourceDeviceId = metadata.source.deviceId,
    sourceExternalReference = metadata.source.externalReference,
    confidenceBasisPoints = metadata.confidence?.basisPoints,
    confidenceRationale = metadata.confidence?.rationale,
)

private fun HouseholdFoodDetailsEntity.toDomain(): FoodDetails = FoodDetails(
    metadata = metadata(),
    itemId = EntityId(itemId),
    dietaryTags = dietaryTags.toSet(),
    allergenTags = allergenTags.toSet(),
    typicalShelfLifeDays = typicalShelfLifeDays,
    defaultServing = defaultServingUnit?.let { unit -> quantity(defaultServingAmount, unit) },
    nutritionSnapshotIds = nutritionSnapshotIds.map(::EntityId),
    externalIdentifiers = externalIdentifiers,
)

private fun StorageLocation.toEntity(): HouseholdStorageLocationEntity = HouseholdStorageLocationEntity(
    id = metadata.id.value,
    householdId = metadata.householdId.value,
    name = name,
    type = type.name,
    parentLocationId = parentLocationId?.value,
    sortOrder = sortOrder,
    createdAt = metadata.createdAt.epochMillis,
    updatedAt = metadata.updatedAt.epochMillis,
    archivedAt = metadata.archivedAt?.epochMillis,
    revision = metadata.revision,
    sourceKind = metadata.source.kind.name,
    sourceLabel = metadata.source.label,
    sourceActorId = metadata.source.actorId,
    sourceDeviceId = metadata.source.deviceId,
    sourceExternalReference = metadata.source.externalReference,
    confidenceBasisPoints = metadata.confidence?.basisPoints,
    confidenceRationale = metadata.confidence?.rationale,
)

private fun HouseholdStorageLocationEntity.toDomain(): StorageLocation = StorageLocation(
    metadata = metadata(),
    name = name,
    type = enumValue<StorageLocationType>(type),
    parentLocationId = parentLocationId?.let(::EntityId),
    sortOrder = sortOrder,
)

private fun InventoryLot.toEntity(): HouseholdInventoryLotEntity = HouseholdInventoryLotEntity(
    id = metadata.id.value,
    householdId = metadata.householdId.value,
    itemId = itemId.value,
    quantityAmount = quantity.amount?.value,
    quantityUnit = quantity.unit.code,
    locationId = locationId?.value,
    purchasedAt = purchasedAt?.epochMillis,
    openedAt = openedAt?.epochMillis,
    expiresOn = expiresOn?.value,
    purchaseLineId = purchaseLineId?.value,
    unitCostMinorUnits = unitCost?.minorUnits,
    unitCostCurrency = unitCost?.currencyCode,
    status = status.name,
    createdAt = metadata.createdAt.epochMillis,
    updatedAt = metadata.updatedAt.epochMillis,
    archivedAt = metadata.archivedAt?.epochMillis,
    revision = metadata.revision,
    sourceKind = metadata.source.kind.name,
    sourceLabel = metadata.source.label,
    sourceActorId = metadata.source.actorId,
    sourceDeviceId = metadata.source.deviceId,
    sourceExternalReference = metadata.source.externalReference,
    confidenceBasisPoints = metadata.confidence?.basisPoints,
    confidenceRationale = metadata.confidence?.rationale,
)

private fun HouseholdInventoryLotEntity.toDomain(): InventoryLot = InventoryLot(
    metadata = metadata(),
    itemId = EntityId(itemId),
    quantity = quantity(quantityAmount, quantityUnit),
    locationId = locationId?.let(::EntityId),
    purchasedAt = purchasedAt?.let(::UtcTimestamp),
    openedAt = openedAt?.let(::UtcTimestamp),
    expiresOn = expiresOn?.let(::CalendarDate),
    purchaseLineId = purchaseLineId?.let(::EntityId),
    unitCost = money(unitCostMinorUnits, unitCostCurrency),
    status = enumValue(status),
)

private fun InventoryEvent.toEntity(): HouseholdInventoryEventEntity = HouseholdInventoryEventEntity(
    id = metadata.id.value,
    householdId = metadata.householdId.value,
    itemId = itemId.value,
    lotId = lotId?.value,
    type = type.name,
    quantityDeltaAmount = quantityDelta?.amount?.value,
    quantityDeltaUnit = quantityDelta?.unit?.code,
    reason = reason,
    relatedEntityId = relatedEntityId?.value,
    commandId = commandId.value,
    createdAt = metadata.createdAt.epochMillis,
    updatedAt = metadata.updatedAt.epochMillis,
    archivedAt = metadata.archivedAt?.epochMillis,
    revision = metadata.revision,
    sourceKind = metadata.source.kind.name,
    sourceLabel = metadata.source.label,
    sourceActorId = metadata.source.actorId,
    sourceDeviceId = metadata.source.deviceId,
    sourceExternalReference = metadata.source.externalReference,
    confidenceBasisPoints = metadata.confidence?.basisPoints,
    confidenceRationale = metadata.confidence?.rationale,
)

private fun HouseholdInventoryEventEntity.toDomain(): InventoryEvent = InventoryEvent(
    metadata = metadata(),
    itemId = EntityId(itemId),
    lotId = lotId?.let(::EntityId),
    type = enumValue<InventoryEventType>(type),
    quantityDelta = quantityDeltaUnit?.let { unit -> quantity(quantityDeltaAmount, unit) },
    reason = reason,
    relatedEntityId = relatedEntityId?.let(::EntityId),
    commandId = CommandId(commandId),
)

private fun ShoppingList.toEntity(): HouseholdShoppingListEntity = HouseholdShoppingListEntity(
    id = metadata.id.value,
    householdId = metadata.householdId.value,
    name = name,
    status = status.name,
    merchantId = merchantId?.value,
    plannedFor = plannedFor?.value,
    createdAt = metadata.createdAt.epochMillis,
    updatedAt = metadata.updatedAt.epochMillis,
    archivedAt = metadata.archivedAt?.epochMillis,
    revision = metadata.revision,
    sourceKind = metadata.source.kind.name,
    sourceLabel = metadata.source.label,
    sourceActorId = metadata.source.actorId,
    sourceDeviceId = metadata.source.deviceId,
    sourceExternalReference = metadata.source.externalReference,
    confidenceBasisPoints = metadata.confidence?.basisPoints,
    confidenceRationale = metadata.confidence?.rationale,
)

private fun HouseholdShoppingListEntity.toDomain(): ShoppingList = ShoppingList(
    metadata = metadata(),
    name = name,
    status = enumValue<ShoppingListStatus>(status),
    merchantId = merchantId?.let(::EntityId),
    plannedFor = plannedFor?.let(::CalendarDate),
)

private fun ShoppingLine.toEntity(): HouseholdShoppingLineEntity = HouseholdShoppingLineEntity(
    id = metadata.id.value,
    householdId = metadata.householdId.value,
    shoppingListId = shoppingListId.value,
    itemId = itemId?.value,
    displayName = displayName,
    quantityAmount = quantity.amount?.value,
    quantityUnit = quantity.unit.code,
    category = category,
    preferredStore = preferredStore,
    status = status.name,
    reason = reason.name,
    sourceEntityIds = sourceEntityIds.map { it.value },
    estimatedPriceMinorUnits = estimatedPrice?.minorUnits,
    estimatedPriceCurrency = estimatedPrice?.currencyCode,
    createdAt = metadata.createdAt.epochMillis,
    updatedAt = metadata.updatedAt.epochMillis,
    archivedAt = metadata.archivedAt?.epochMillis,
    revision = metadata.revision,
    sourceKind = metadata.source.kind.name,
    sourceLabel = metadata.source.label,
    sourceActorId = metadata.source.actorId,
    sourceDeviceId = metadata.source.deviceId,
    sourceExternalReference = metadata.source.externalReference,
    confidenceBasisPoints = metadata.confidence?.basisPoints,
    confidenceRationale = metadata.confidence?.rationale,
)

private fun HouseholdShoppingLineEntity.toDomain(): ShoppingLine = ShoppingLine(
    metadata = metadata(),
    shoppingListId = EntityId(shoppingListId),
    itemId = itemId?.let(::EntityId),
    displayName = displayName,
    quantity = quantity(quantityAmount, quantityUnit),
    category = category,
    preferredStore = preferredStore,
    status = enumValue(status),
    reason = enumValue(reason),
    sourceEntityIds = sourceEntityIds.map(::EntityId),
    estimatedPrice = money(estimatedPriceMinorUnits, estimatedPriceCurrency),
)

private fun Recipe.toEntity(): HouseholdRecipeEntity = HouseholdRecipeEntity(
    id = metadata.id.value,
    householdId = metadata.householdId.value,
    name = name,
    description = description,
    sourceUrl = sourceUrl,
    sourceProvider = sourceProvider,
    author = author,
    cuisine = cuisine,
    category = category,
    tags = tags.toList().sorted(),
    yieldAmount = yield.amount?.value,
    yieldUnit = yield.unit.code,
    prepMinutes = prepMinutes,
    cookMinutes = cookMinutes,
    totalMinutes = totalMinutes,
    difficulty = difficulty,
    status = status.name,
    ingredientIds = ingredientIds.map { it.value },
    stepIds = stepIds.map { it.value },
    attachmentIds = attachmentIds.map { it.value },
    nutritionSnapshotIds = nutritionSnapshotIds.map { it.value },
    createdAt = metadata.createdAt.epochMillis,
    updatedAt = metadata.updatedAt.epochMillis,
    archivedAt = metadata.archivedAt?.epochMillis,
    revision = metadata.revision,
    sourceKind = metadata.source.kind.name,
    sourceLabel = metadata.source.label,
    sourceActorId = metadata.source.actorId,
    sourceDeviceId = metadata.source.deviceId,
    sourceExternalReference = metadata.source.externalReference,
    confidenceBasisPoints = metadata.confidence?.basisPoints,
    confidenceRationale = metadata.confidence?.rationale,
)

private fun HouseholdRecipeEntity.toDomain(): Recipe = Recipe(
    metadata = metadata(),
    name = name,
    description = description,
    sourceUrl = sourceUrl,
    sourceProvider = sourceProvider,
    author = author,
    cuisine = cuisine,
    category = category,
    tags = tags.toSet(),
    yield = quantity(yieldAmount, yieldUnit),
    prepMinutes = prepMinutes,
    cookMinutes = cookMinutes,
    totalMinutes = totalMinutes,
    difficulty = difficulty,
    status = enumValue(status),
    ingredientIds = ingredientIds.map(::EntityId),
    stepIds = stepIds.map(::EntityId),
    attachmentIds = attachmentIds.map(::EntityId),
    nutritionSnapshotIds = nutritionSnapshotIds.map(::EntityId),
)

private fun RecipeIngredient.toEntity(): HouseholdRecipeIngredientEntity = HouseholdRecipeIngredientEntity(
    id = metadata.id.value,
    householdId = metadata.householdId.value,
    recipeId = recipeId.value,
    itemId = itemId?.value,
    originalText = originalText,
    quantityAmount = quantity.amount?.value,
    quantityUnit = quantity.unit.code,
    preparation = preparation,
    optional = optional,
    section = section,
    sortOrder = order,
    substituteItemIds = substituteItemIds.map { it.value },
    createdAt = metadata.createdAt.epochMillis,
    updatedAt = metadata.updatedAt.epochMillis,
    archivedAt = metadata.archivedAt?.epochMillis,
    revision = metadata.revision,
    sourceKind = metadata.source.kind.name,
    sourceLabel = metadata.source.label,
    sourceActorId = metadata.source.actorId,
    sourceDeviceId = metadata.source.deviceId,
    sourceExternalReference = metadata.source.externalReference,
    confidenceBasisPoints = metadata.confidence?.basisPoints,
    confidenceRationale = metadata.confidence?.rationale,
)

private fun HouseholdRecipeIngredientEntity.toDomain(): RecipeIngredient = RecipeIngredient(
    metadata = metadata(),
    recipeId = EntityId(recipeId),
    itemId = itemId?.let(::EntityId),
    originalText = originalText,
    quantity = quantity(quantityAmount, quantityUnit),
    preparation = preparation,
    optional = optional,
    section = section,
    order = sortOrder,
    substituteItemIds = substituteItemIds.map(::EntityId),
)

private fun RecipeStep.toEntity(): HouseholdRecipeStepEntity = HouseholdRecipeStepEntity(
    id = metadata.id.value,
    householdId = metadata.householdId.value,
    recipeId = recipeId.value,
    section = section,
    sortOrder = order,
    instruction = instruction,
    durationMinutes = durationMinutes,
    timerLabel = timerLabel,
    ingredientIds = ingredientIds.map { it.value },
    attachmentIds = attachmentIds.map { it.value },
    createdAt = metadata.createdAt.epochMillis,
    updatedAt = metadata.updatedAt.epochMillis,
    archivedAt = metadata.archivedAt?.epochMillis,
    revision = metadata.revision,
    sourceKind = metadata.source.kind.name,
    sourceLabel = metadata.source.label,
    sourceActorId = metadata.source.actorId,
    sourceDeviceId = metadata.source.deviceId,
    sourceExternalReference = metadata.source.externalReference,
    confidenceBasisPoints = metadata.confidence?.basisPoints,
    confidenceRationale = metadata.confidence?.rationale,
)

private fun HouseholdRecipeStepEntity.toDomain(): RecipeStep = RecipeStep(
    metadata = metadata(),
    recipeId = EntityId(recipeId),
    section = section,
    order = sortOrder,
    instruction = instruction,
    durationMinutes = durationMinutes,
    timerLabel = timerLabel,
    ingredientIds = ingredientIds.map(::EntityId),
    attachmentIds = attachmentIds.map(::EntityId),
)

private fun CookingSession.toEntity(): HouseholdCookingSessionEntity = HouseholdCookingSessionEntity(
    id = metadata.id.value,
    householdId = metadata.householdId.value,
    recipeId = recipeId.value,
    startedAt = startedAt?.epochMillis,
    finishedAt = finishedAt?.epochMillis,
    currentStep = currentStep,
    servingsAmount = servings.amount?.value,
    servingsUnit = servings.unit.code,
    state = state.name,
    preparedBatchId = preparedBatchId?.value,
    createdAt = metadata.createdAt.epochMillis,
    updatedAt = metadata.updatedAt.epochMillis,
    archivedAt = metadata.archivedAt?.epochMillis,
    revision = metadata.revision,
    sourceKind = metadata.source.kind.name,
    sourceLabel = metadata.source.label,
    sourceActorId = metadata.source.actorId,
    sourceDeviceId = metadata.source.deviceId,
    sourceExternalReference = metadata.source.externalReference,
    confidenceBasisPoints = metadata.confidence?.basisPoints,
    confidenceRationale = metadata.confidence?.rationale,
)

private fun HouseholdCookingSessionEntity.toDomain(): CookingSession = CookingSession(
    metadata = metadata(),
    recipeId = EntityId(recipeId),
    startedAt = startedAt?.let(::UtcTimestamp),
    finishedAt = finishedAt?.let(::UtcTimestamp),
    currentStep = currentStep,
    servings = quantity(servingsAmount, servingsUnit),
    state = enumValue<CookingSessionState>(state),
    preparedBatchId = preparedBatchId?.let(::EntityId),
)

private fun PreparedBatch.toEntity(): HouseholdPreparedBatchEntity = HouseholdPreparedBatchEntity(
    id = metadata.id.value,
    householdId = metadata.householdId.value,
    recipeId = recipeId?.value,
    mealEntryId = mealEntryId?.value,
    preparedAt = preparedAt.epochMillis,
    totalQuantityAmount = totalQuantity.amount?.value,
    totalQuantityUnit = totalQuantity.unit.code,
    remainingQuantityAmount = remainingQuantity.amount?.value,
    remainingQuantityUnit = remainingQuantity.unit.code,
    storageLocationId = storageLocationId?.value,
    consumeBy = consumeBy?.value,
    nutritionSnapshotId = nutritionSnapshotId?.value,
    createdAt = metadata.createdAt.epochMillis,
    updatedAt = metadata.updatedAt.epochMillis,
    archivedAt = metadata.archivedAt?.epochMillis,
    revision = metadata.revision,
    sourceKind = metadata.source.kind.name,
    sourceLabel = metadata.source.label,
    sourceActorId = metadata.source.actorId,
    sourceDeviceId = metadata.source.deviceId,
    sourceExternalReference = metadata.source.externalReference,
    confidenceBasisPoints = metadata.confidence?.basisPoints,
    confidenceRationale = metadata.confidence?.rationale,
)

private fun HouseholdPreparedBatchEntity.toDomain(): PreparedBatch = PreparedBatch(
    metadata = metadata(),
    recipeId = recipeId?.let(::EntityId),
    mealEntryId = mealEntryId?.let(::EntityId),
    preparedAt = UtcTimestamp(preparedAt),
    totalQuantity = quantity(totalQuantityAmount, totalQuantityUnit),
    remainingQuantity = quantity(remainingQuantityAmount, remainingQuantityUnit),
    storageLocationId = storageLocationId?.let(::EntityId),
    consumeBy = consumeBy?.let(::CalendarDate),
    nutritionSnapshotId = nutritionSnapshotId?.let(::EntityId),
)

private fun Merchant.toEntity(): HouseholdMerchantEntity = HouseholdMerchantEntity(
    id = metadata.id.value,
    householdId = metadata.householdId.value,
    name = name,
    category = category,
    location = location,
    externalIdentifiers = externalIdentifiers,
    createdAt = metadata.createdAt.epochMillis,
    updatedAt = metadata.updatedAt.epochMillis,
    archivedAt = metadata.archivedAt?.epochMillis,
    revision = metadata.revision,
    sourceKind = metadata.source.kind.name,
    sourceLabel = metadata.source.label,
    sourceActorId = metadata.source.actorId,
    sourceDeviceId = metadata.source.deviceId,
    sourceExternalReference = metadata.source.externalReference,
    confidenceBasisPoints = metadata.confidence?.basisPoints,
    confidenceRationale = metadata.confidence?.rationale,
)

private fun HouseholdMerchantEntity.toDomain(): Merchant = Merchant(
    metadata = metadata(),
    name = name,
    category = category,
    location = location,
    externalIdentifiers = externalIdentifiers,
)

private fun Purchase.toEntity(): HouseholdPurchaseEntity = HouseholdPurchaseEntity(
    id = metadata.id.value,
    householdId = metadata.householdId.value,
    merchantId = merchantId?.value,
    occurredAt = occurredAt.epochMillis,
    receiptAttachmentIds = receiptAttachmentIds.map { it.value },
    subtotalMinorUnits = subtotal?.minorUnits,
    subtotalCurrency = subtotal?.currencyCode,
    taxMinorUnits = tax?.minorUnits,
    taxCurrency = tax?.currencyCode,
    discountMinorUnits = discount?.minorUnits,
    discountCurrency = discount?.currencyCode,
    tipMinorUnits = tip?.minorUnits,
    tipCurrency = tip?.currencyCode,
    totalMinorUnits = total?.minorUnits,
    totalCurrency = total?.currencyCode,
    paymentNote = paymentNote,
    status = status.name,
    reconciliationDifferenceMinorUnits = reconciliationDifference?.minorUnits,
    reconciliationDifferenceCurrency = reconciliationDifference?.currencyCode,
    createdAt = metadata.createdAt.epochMillis,
    updatedAt = metadata.updatedAt.epochMillis,
    archivedAt = metadata.archivedAt?.epochMillis,
    revision = metadata.revision,
    sourceKind = metadata.source.kind.name,
    sourceLabel = metadata.source.label,
    sourceActorId = metadata.source.actorId,
    sourceDeviceId = metadata.source.deviceId,
    sourceExternalReference = metadata.source.externalReference,
    confidenceBasisPoints = metadata.confidence?.basisPoints,
    confidenceRationale = metadata.confidence?.rationale,
)

private fun HouseholdPurchaseEntity.toDomain(): Purchase = Purchase(
    metadata = metadata(),
    merchantId = merchantId?.let(::EntityId),
    occurredAt = UtcTimestamp(occurredAt),
    receiptAttachmentIds = receiptAttachmentIds.map(::EntityId),
    subtotal = money(subtotalMinorUnits, subtotalCurrency),
    tax = money(taxMinorUnits, taxCurrency),
    discount = money(discountMinorUnits, discountCurrency),
    tip = money(tipMinorUnits, tipCurrency),
    total = money(totalMinorUnits, totalCurrency),
    paymentNote = paymentNote,
    status = enumValue(status),
    reconciliationDifference = money(reconciliationDifferenceMinorUnits, reconciliationDifferenceCurrency),
)

private fun PurchaseLine.toEntity(): HouseholdPurchaseLineEntity = HouseholdPurchaseLineEntity(
    id = metadata.id.value,
    householdId = metadata.householdId.value,
    purchaseId = purchaseId.value,
    itemId = itemId?.value,
    shoppingLineId = shoppingLineId?.value,
    displayName = displayName,
    quantityAmount = quantity.amount?.value,
    quantityUnit = quantity.unit.code,
    unitPriceMinorUnits = unitPrice?.minorUnits,
    unitPriceCurrency = unitPrice?.currencyCode,
    lineSubtotalMinorUnits = lineSubtotal?.minorUnits,
    lineSubtotalCurrency = lineSubtotal?.currencyCode,
    discountMinorUnits = discount?.minorUnits,
    discountCurrency = discount?.currencyCode,
    taxAllocationMinorUnits = taxAllocation?.minorUnits,
    taxAllocationCurrency = taxAllocation?.currencyCode,
    finalAmountMinorUnits = finalAmount?.minorUnits,
    finalAmountCurrency = finalAmount?.currencyCode,
    spendCategory = spendCategory,
    disposition = disposition.name,
    inventoryLotId = inventoryLotId?.value,
    reviewState = reviewState.name,
    createdAt = metadata.createdAt.epochMillis,
    updatedAt = metadata.updatedAt.epochMillis,
    archivedAt = metadata.archivedAt?.epochMillis,
    revision = metadata.revision,
    sourceKind = metadata.source.kind.name,
    sourceLabel = metadata.source.label,
    sourceActorId = metadata.source.actorId,
    sourceDeviceId = metadata.source.deviceId,
    sourceExternalReference = metadata.source.externalReference,
    confidenceBasisPoints = metadata.confidence?.basisPoints,
    confidenceRationale = metadata.confidence?.rationale,
)

private fun HouseholdPurchaseLineEntity.toDomain(): PurchaseLine = PurchaseLine(
    metadata = metadata(),
    purchaseId = EntityId(purchaseId),
    itemId = itemId?.let(::EntityId),
    shoppingLineId = shoppingLineId?.let(::EntityId),
    displayName = displayName,
    quantity = quantity(quantityAmount, quantityUnit),
    unitPrice = money(unitPriceMinorUnits, unitPriceCurrency),
    lineSubtotal = money(lineSubtotalMinorUnits, lineSubtotalCurrency),
    discount = money(discountMinorUnits, discountCurrency),
    taxAllocation = money(taxAllocationMinorUnits, taxAllocationCurrency),
    finalAmount = money(finalAmountMinorUnits, finalAmountCurrency),
    spendCategory = spendCategory,
    disposition = enumValue(disposition),
    inventoryLotId = inventoryLotId?.let(::EntityId),
    reviewState = enumValue(reviewState),
)

private fun WasteEvent.toEntity(): HouseholdWasteEventEntity = HouseholdWasteEventEntity(
    id = metadata.id.value,
    householdId = metadata.householdId.value,
    inventoryLotId = inventoryLotId.value,
    quantityAmount = quantity.amount?.value,
    quantityUnit = quantity.unit.code,
    reason = reason,
    estimatedCostMinorUnits = estimatedCost?.minorUnits,
    estimatedCostCurrency = estimatedCost?.currencyCode,
    occurredAt = occurredAt.epochMillis,
    createdAt = metadata.createdAt.epochMillis,
    updatedAt = metadata.updatedAt.epochMillis,
    archivedAt = metadata.archivedAt?.epochMillis,
    revision = metadata.revision,
    sourceKind = metadata.source.kind.name,
    sourceLabel = metadata.source.label,
    sourceActorId = metadata.source.actorId,
    sourceDeviceId = metadata.source.deviceId,
    sourceExternalReference = metadata.source.externalReference,
    confidenceBasisPoints = metadata.confidence?.basisPoints,
    confidenceRationale = metadata.confidence?.rationale,
)

private fun HouseholdWasteEventEntity.toDomain(): WasteEvent = WasteEvent(
    metadata = metadata(),
    inventoryLotId = EntityId(inventoryLotId),
    quantity = quantity(quantityAmount, quantityUnit),
    reason = reason,
    estimatedCost = money(estimatedCostMinorUnits, estimatedCostCurrency),
    occurredAt = UtcTimestamp(occurredAt),
)

private fun MealPlan.toEntity(): HouseholdMealPlanEntity = HouseholdMealPlanEntity(
    id = metadata.id.value,
    householdId = metadata.householdId.value,
    name = name,
    startsOn = startsOn.value,
    endsOn = endsOn.value,
    status = status.name,
    targetProfileIds = targetProfileIds.map { it.value },
    budgetMinorUnits = budget?.minorUnits,
    budgetCurrency = budget?.currencyCode,
    nutritionTargetSnapshotId = nutritionTargetSnapshotId?.value,
    createdAt = metadata.createdAt.epochMillis,
    updatedAt = metadata.updatedAt.epochMillis,
    archivedAt = metadata.archivedAt?.epochMillis,
    revision = metadata.revision,
    sourceKind = metadata.source.kind.name,
    sourceLabel = metadata.source.label,
    sourceActorId = metadata.source.actorId,
    sourceDeviceId = metadata.source.deviceId,
    sourceExternalReference = metadata.source.externalReference,
    confidenceBasisPoints = metadata.confidence?.basisPoints,
    confidenceRationale = metadata.confidence?.rationale,
)

private fun HouseholdMealPlanEntity.toDomain(): MealPlan = MealPlan(
    metadata = metadata(),
    name = name,
    startsOn = CalendarDate(startsOn),
    endsOn = CalendarDate(endsOn),
    status = enumValue(status),
    targetProfileIds = targetProfileIds.map(::EntityId),
    budget = money(budgetMinorUnits, budgetCurrency),
    nutritionTargetSnapshotId = nutritionTargetSnapshotId?.let(::EntityId),
)

private fun MealEntry.toEntity(): HouseholdMealEntryEntity = HouseholdMealEntryEntity(
    id = metadata.id.value,
    householdId = metadata.householdId.value,
    mealPlanId = mealPlanId?.value,
    scheduledAt = scheduledAt.epochMillis,
    slot = slot,
    recipeId = recipeId?.value,
    preparedBatchId = preparedBatchId?.value,
    title = title,
    servingsAmount = servings.amount?.value,
    servingsUnit = servings.unit.code,
    status = status.name,
    leftoverIntent = leftoverIntent,
    nutritionSnapshotIds = nutritionSnapshotIds.map { it.value },
    notes = notes,
    createdAt = metadata.createdAt.epochMillis,
    updatedAt = metadata.updatedAt.epochMillis,
    archivedAt = metadata.archivedAt?.epochMillis,
    revision = metadata.revision,
    sourceKind = metadata.source.kind.name,
    sourceLabel = metadata.source.label,
    sourceActorId = metadata.source.actorId,
    sourceDeviceId = metadata.source.deviceId,
    sourceExternalReference = metadata.source.externalReference,
    confidenceBasisPoints = metadata.confidence?.basisPoints,
    confidenceRationale = metadata.confidence?.rationale,
)

private fun HouseholdMealEntryEntity.toDomain(): MealEntry = MealEntry(
    metadata = metadata(),
    mealPlanId = mealPlanId?.let(::EntityId),
    scheduledAt = UtcTimestamp(scheduledAt),
    slot = slot,
    recipeId = recipeId?.let(::EntityId),
    preparedBatchId = preparedBatchId?.let(::EntityId),
    title = title,
    servings = quantity(servingsAmount, servingsUnit),
    status = enumValue(status),
    leftoverIntent = leftoverIntent,
    nutritionSnapshotIds = nutritionSnapshotIds.map(::EntityId),
    notes = notes,
)

private fun NutritionSnapshot.toEntity(): HouseholdNutritionSnapshotEntity = HouseholdNutritionSnapshotEntity(
    id = metadata.id.value,
    householdId = metadata.householdId.value,
    subjectType = subject.type.name,
    subjectId = subject.id.value,
    basisAmount = basis.amount?.value,
    basisUnit = basis.unit.code,
    energyKcal = values.energyKcal?.value,
    proteinGrams = values.proteinGrams?.value,
    carbohydrateGrams = values.carbohydrateGrams?.value,
    fatGrams = values.fatGrams?.value,
    fiberGrams = values.fiberGrams?.value,
    sugarGrams = values.sugarGrams?.value,
    sodiumMilligrams = values.sodiumMilligrams?.value,
    provider = provider,
    capturedAt = capturedAt.epochMillis,
    warnings = warnings,
    createdAt = metadata.createdAt.epochMillis,
    updatedAt = metadata.updatedAt.epochMillis,
    archivedAt = metadata.archivedAt?.epochMillis,
    revision = metadata.revision,
    sourceKind = metadata.source.kind.name,
    sourceLabel = metadata.source.label,
    sourceActorId = metadata.source.actorId,
    sourceDeviceId = metadata.source.deviceId,
    sourceExternalReference = metadata.source.externalReference,
    confidenceBasisPoints = metadata.confidence?.basisPoints,
    confidenceRationale = metadata.confidence?.rationale,
)

private fun HouseholdNutritionSnapshotEntity.toDomain(): NutritionSnapshot = NutritionSnapshot(
    metadata = metadata(),
    subject = com.wonderfood.core.model.household.EntityReference(
        type = enumValue(subjectType),
        id = EntityId(subjectId),
    ),
    basis = quantity(basisAmount, basisUnit),
    values = NutritionValues(
        energyKcal = energyKcal?.let(DecimalAmount::of),
        proteinGrams = proteinGrams?.let(DecimalAmount::of),
        carbohydrateGrams = carbohydrateGrams?.let(DecimalAmount::of),
        fatGrams = fatGrams?.let(DecimalAmount::of),
        fiberGrams = fiberGrams?.let(DecimalAmount::of),
        sugarGrams = sugarGrams?.let(DecimalAmount::of),
        sodiumMilligrams = sodiumMilligrams?.let(DecimalAmount::of),
    ),
    provider = provider,
    capturedAt = UtcTimestamp(capturedAt),
    warnings = warnings,
)

private fun ChangeProposal.toEntity(): HouseholdChangeProposalEntity = HouseholdChangeProposalEntity(
    id = metadata.id.value,
    householdId = metadata.householdId.value,
    sourcePayloadReference = sourcePayloadReference,
    requestedCommandTypes = requestedCommands.map { it.type },
    warnings = warnings,
    status = status.name,
    reviewedAt = reviewedAt?.epochMillis,
    reviewer = reviewer,
    createdAt = metadata.createdAt.epochMillis,
    updatedAt = metadata.updatedAt.epochMillis,
    archivedAt = metadata.archivedAt?.epochMillis,
    revision = metadata.revision,
    sourceKind = metadata.source.kind.name,
    sourceLabel = metadata.source.label,
    sourceActorId = metadata.source.actorId,
    sourceDeviceId = metadata.source.deviceId,
    sourceExternalReference = metadata.source.externalReference,
    confidenceBasisPoints = metadata.confidence?.basisPoints,
    confidenceRationale = metadata.confidence?.rationale,
)

private fun HouseholdChangeProposalEntity.toDomain(): ChangeProposal = ChangeProposal(
    metadata = metadata(),
    sourcePayloadReference = sourcePayloadReference,
    requestedCommands = requestedCommandTypes.map { CommandIntent(it, "persisted") },
    warnings = warnings,
    status = enumValue(status),
    reviewedAt = reviewedAt?.let(::UtcTimestamp),
    reviewer = reviewer,
)

private fun CommandRecord.toEntity(): HouseholdCommandRecordEntity = HouseholdCommandRecordEntity(
    id = commandId.value,
    householdId = householdId.value,
    type = type,
    requestedAt = requestedAt.epochMillis,
    appliedAt = appliedAt?.epochMillis,
    affectedEntityIds = affectedEntityIds.map { it.value },
    beforeHash = beforeHash,
    afterHash = afterHash,
    undoCommandId = undoCommandId?.value,
    sourceKind = source.kind.name,
    sourceLabel = source.label,
    sourceActorId = source.actorId,
    sourceDeviceId = source.deviceId,
    sourceExternalReference = source.externalReference,
)

private fun HouseholdCommandRecordEntity.toDomain(): CommandRecord = CommandRecord(
    commandId = CommandId(id),
    householdId = HouseholdId(householdId),
    type = type,
    source = source(),
    requestedAt = UtcTimestamp(requestedAt),
    appliedAt = appliedAt?.let(::UtcTimestamp),
    affectedEntityIds = affectedEntityIds.map(::EntityId),
    beforeHash = beforeHash,
    afterHash = afterHash,
    undoCommandId = undoCommandId?.let(::CommandId),
)

private fun SyncOutboxRecord.toEntity(): HouseholdSyncOutboxEntity = HouseholdSyncOutboxEntity(
    id = id.value,
    connectionId = connectionId.value,
    commandId = commandId.value,
    operation = operation.name,
    householdId = envelope.householdId.value,
    entityType = envelope.entityType.name,
    entityId = envelope.entityId.value,
    schemaVersion = envelope.schemaVersion,
    revision = envelope.revision,
    createdAt = envelope.createdAt.epochMillis,
    updatedAt = envelope.updatedAt.epochMillis,
    archivedAt = envelope.archivedAt?.epochMillis,
    originDeviceId = envelope.originDeviceId,
    lastCommandId = envelope.lastCommandId.value,
    payloadHash = envelope.payloadHash.value,
    idempotencyKey = idempotencyKey,
    status = status.name,
    retryCount = retryCount,
    lastError = lastError,
)

private fun RemoteBinding.toEntity(householdId: HouseholdId): HouseholdRemoteBindingEntity = HouseholdRemoteBindingEntity(
    id = "${connectionId.value}:${entityType.name}:${entityId.value}",
    householdId = householdId.value,
    connectionId = connectionId.value,
    entityType = entityType.name,
    entityId = entityId.value,
    remoteObjectId = remoteObjectId,
    remoteParentId = remoteParentId,
    remoteSchemaFingerprint = remoteSchemaFingerprint,
)

private fun HouseholdRemoteBindingEntity.toDomain(): RemoteBinding = RemoteBinding(
    connectionId = ConnectionId(connectionId),
    entityType = enumValue(entityType),
    entityId = EntityId(entityId),
    remoteObjectId = remoteObjectId,
    remoteParentId = remoteParentId,
    remoteSchemaFingerprint = remoteSchemaFingerprint,
)

private fun SyncBase.toEntity(): HouseholdSyncBaseEntity = HouseholdSyncBaseEntity(
    id = "${binding.connectionId.value}:${binding.entityType.name}:${binding.entityId.value}",
    householdId = envelope.householdId.value,
    connectionId = binding.connectionId.value,
    entityType = binding.entityType.name,
    entityId = binding.entityId.value,
    remoteObjectId = binding.remoteObjectId,
    remoteParentId = binding.remoteParentId,
    remoteSchemaFingerprint = binding.remoteSchemaFingerprint,
    schemaVersion = envelope.schemaVersion,
    revision = envelope.revision,
    createdAt = envelope.createdAt.epochMillis,
    updatedAt = envelope.updatedAt.epochMillis,
    archivedAt = envelope.archivedAt?.epochMillis,
    originDeviceId = envelope.originDeviceId,
    lastCommandId = envelope.lastCommandId.value,
    payloadHash = envelope.payloadHash.value,
    localRevision = localRevision,
    remoteRevision = remoteRevision,
    basePayloadHash = basePayloadHash.value,
    pulledAt = pulledAt.epochMillis,
)

private fun HouseholdSyncBaseEntity.toDomain(): SyncBase = SyncBase(
    binding = RemoteBinding(
        connectionId = ConnectionId(connectionId),
        entityType = enumValue(entityType),
        entityId = EntityId(entityId),
        remoteObjectId = remoteObjectId,
        remoteParentId = remoteParentId,
        remoteSchemaFingerprint = remoteSchemaFingerprint,
    ),
    envelope = syncEnvelope(),
    localRevision = localRevision,
    remoteRevision = remoteRevision,
    basePayloadHash = PayloadHash(basePayloadHash),
    pulledAt = UtcTimestamp(pulledAt),
)

private fun SyncCursor.toEntity(): HouseholdSyncCursorEntity = HouseholdSyncCursorEntity(
    id = id.value,
    householdId = householdId.value,
    connectionId = connectionId.value,
    cursor = cursor,
    pulledAt = pulledAt.epochMillis,
    remoteHighWatermark = remoteHighWatermark,
)

private fun HouseholdSyncCursorEntity.toDomain(): SyncCursor = SyncCursor(
    id = EntityId(id),
    householdId = HouseholdId(householdId),
    connectionId = ConnectionId(connectionId),
    cursor = cursor,
    pulledAt = UtcTimestamp(pulledAt),
    remoteHighWatermark = remoteHighWatermark,
)

private fun ConflictRecord.toEntity(): HouseholdConflictEntity = HouseholdConflictEntity(
    id = metadata.id.value,
    householdId = metadata.householdId.value,
    entityType = entityType.name,
    entityId = entityId.value,
    baseHash = baseHash.value,
    appHash = appHash.value,
    dataHomeHash = dataHomeHash.value,
    decisionAction = decision.action.name,
    decisionFields = decision.fields.toList().sorted(),
    decisionReason = decision.reason,
    appChangedFields = appChangedFields.toList().sorted(),
    dataHomeChangedFields = dataHomeChangedFields.toList().sorted(),
    createdAt = metadata.createdAt.epochMillis,
    updatedAt = metadata.updatedAt.epochMillis,
    archivedAt = metadata.archivedAt?.epochMillis,
    revision = metadata.revision,
    sourceKind = metadata.source.kind.name,
    sourceLabel = metadata.source.label,
    sourceActorId = metadata.source.actorId,
    sourceDeviceId = metadata.source.deviceId,
    sourceExternalReference = metadata.source.externalReference,
    confidenceBasisPoints = metadata.confidence?.basisPoints,
    confidenceRationale = metadata.confidence?.rationale,
)

private fun HouseholdConflictEntity.toDomain(): ConflictRecord = ConflictRecord(
    metadata = metadata(),
    entityType = enumValue(entityType),
    entityId = EntityId(entityId),
    baseHash = PayloadHash(baseHash),
    appHash = PayloadHash(appHash),
    dataHomeHash = PayloadHash(dataHomeHash),
    decision = SyncDecision(
        action = enumValue<SyncDecisionAction>(decisionAction),
        fields = decisionFields.toSet(),
        reason = decisionReason,
    ),
    appChangedFields = appChangedFields.toSet(),
    dataHomeChangedFields = dataHomeChangedFields.toSet(),
)

private fun LatestSafetySnapshot.toEntity(): HouseholdLatestSafetySnapshotEntity = HouseholdLatestSafetySnapshotEntity(
    id = id.value,
    householdId = householdId.value,
    reason = reason,
    createdAt = createdAt.epochMillis,
    localReplicaHash = localReplicaHash.value,
    activeDataHome = activeDataHome.name,
    connectionId = connectionId?.value,
    commandId = commandId?.value,
)

private fun HouseholdLatestSafetySnapshotEntity.toDomain(): LatestSafetySnapshot = LatestSafetySnapshot(
    id = EntityId(id),
    householdId = HouseholdId(householdId),
    reason = reason,
    createdAt = UtcTimestamp(createdAt),
    localReplicaHash = PayloadHash(localReplicaHash),
    activeDataHome = enumValue(activeDataHome),
    connectionId = connectionId?.let(::ConnectionId),
    commandId = commandId?.let(::CommandId),
)

private fun RecoverySnapshot.toEntity(): HouseholdRecoverySnapshotEntity = HouseholdRecoverySnapshotEntity(
    id = id.value,
    householdId = householdId.value,
    reason = reason,
    createdAt = createdAt.epochMillis,
    payloadHash = payloadHash.value,
    objectCount = objectCount,
    commandId = commandId?.value,
)

private fun HouseholdRecoverySnapshotEntity.toDomain(): RecoverySnapshot = RecoverySnapshot(
    id = EntityId(id),
    householdId = HouseholdId(householdId),
    reason = reason,
    createdAt = UtcTimestamp(createdAt),
    payloadHash = PayloadHash(payloadHash),
    objectCount = objectCount,
    commandId = commandId?.let(::CommandId),
)

private fun TombstoneRecord.toEntity(): HouseholdTombstoneEntity = HouseholdTombstoneEntity(
    id = metadata.id.value,
    householdId = metadata.householdId.value,
    entityType = entityType.name,
    entityId = entityId.value,
    reason = reason.name,
    commandId = commandId.value,
    createdAt = metadata.createdAt.epochMillis,
    updatedAt = metadata.updatedAt.epochMillis,
    archivedAt = metadata.archivedAt?.epochMillis,
    revision = metadata.revision,
    sourceKind = metadata.source.kind.name,
    sourceLabel = metadata.source.label,
    sourceActorId = metadata.source.actorId,
    sourceDeviceId = metadata.source.deviceId,
    sourceExternalReference = metadata.source.externalReference,
    confidenceBasisPoints = metadata.confidence?.basisPoints,
    confidenceRationale = metadata.confidence?.rationale,
)

private fun HouseholdTombstoneEntity.toDomain(): TombstoneRecord = TombstoneRecord(
    metadata = metadata(),
    entityType = enumValue(entityType),
    entityId = EntityId(entityId),
    reason = enumValue(reason),
    commandId = CommandId(commandId),
)

private fun HouseholdSyncBaseEntity.syncEnvelope(): SyncRecordEnvelope = SyncRecordEnvelope(
    householdId = HouseholdId(householdId),
    entityType = enumValue(entityType),
    entityId = EntityId(entityId),
    schemaVersion = schemaVersion,
    revision = revision,
    createdAt = UtcTimestamp(createdAt),
    updatedAt = UtcTimestamp(updatedAt),
    archivedAt = archivedAt?.let(::UtcTimestamp),
    originDeviceId = originDeviceId,
    lastCommandId = CommandId(lastCommandId),
    payloadHash = PayloadHash(payloadHash),
)

private fun HouseholdItemEntity.metadata(): EntityMetadata = EntityMetadata(
    id = EntityId(id),
    householdId = HouseholdId(householdId),
    createdAt = UtcTimestamp(createdAt),
    updatedAt = UtcTimestamp(updatedAt),
    archivedAt = archivedAt?.let(::UtcTimestamp),
    revision = revision,
    source = source(),
    confidence = confidence(),
)

private fun HouseholdProfileEntity.metadata(): EntityMetadata = EntityMetadata(
    id = EntityId(id),
    householdId = HouseholdId(householdId),
    createdAt = UtcTimestamp(createdAt),
    updatedAt = UtcTimestamp(updatedAt),
    archivedAt = archivedAt?.let(::UtcTimestamp),
    revision = revision,
    source = source(),
    confidence = confidence(),
)

private fun HouseholdFoodDetailsEntity.metadata(): EntityMetadata = EntityMetadata(
    id = EntityId(id),
    householdId = HouseholdId(householdId),
    createdAt = UtcTimestamp(createdAt),
    updatedAt = UtcTimestamp(updatedAt),
    archivedAt = archivedAt?.let(::UtcTimestamp),
    revision = revision,
    source = source(),
    confidence = confidence(),
)

private fun HouseholdStorageLocationEntity.metadata(): EntityMetadata = EntityMetadata(
    id = EntityId(id),
    householdId = HouseholdId(householdId),
    createdAt = UtcTimestamp(createdAt),
    updatedAt = UtcTimestamp(updatedAt),
    archivedAt = archivedAt?.let(::UtcTimestamp),
    revision = revision,
    source = source(),
    confidence = confidence(),
)

private fun HouseholdInventoryLotEntity.metadata(): EntityMetadata = EntityMetadata(
    id = EntityId(id),
    householdId = HouseholdId(householdId),
    createdAt = UtcTimestamp(createdAt),
    updatedAt = UtcTimestamp(updatedAt),
    archivedAt = archivedAt?.let(::UtcTimestamp),
    revision = revision,
    source = source(),
    confidence = confidence(),
)

private fun HouseholdInventoryEventEntity.metadata(): EntityMetadata = EntityMetadata(
    id = EntityId(id),
    householdId = HouseholdId(householdId),
    createdAt = UtcTimestamp(createdAt),
    updatedAt = UtcTimestamp(updatedAt),
    archivedAt = archivedAt?.let(::UtcTimestamp),
    revision = revision,
    source = source(),
    confidence = confidence(),
)

private fun HouseholdShoppingLineEntity.metadata(): EntityMetadata = EntityMetadata(
    id = EntityId(id),
    householdId = HouseholdId(householdId),
    createdAt = UtcTimestamp(createdAt),
    updatedAt = UtcTimestamp(updatedAt),
    archivedAt = archivedAt?.let(::UtcTimestamp),
    revision = revision,
    source = source(),
    confidence = confidence(),
)

private fun HouseholdShoppingListEntity.metadata(): EntityMetadata = EntityMetadata(
    id = EntityId(id),
    householdId = HouseholdId(householdId),
    createdAt = UtcTimestamp(createdAt),
    updatedAt = UtcTimestamp(updatedAt),
    archivedAt = archivedAt?.let(::UtcTimestamp),
    revision = revision,
    source = source(),
    confidence = confidence(),
)

private fun HouseholdRecipeEntity.metadata(): EntityMetadata = EntityMetadata(
    id = EntityId(id),
    householdId = HouseholdId(householdId),
    createdAt = UtcTimestamp(createdAt),
    updatedAt = UtcTimestamp(updatedAt),
    archivedAt = archivedAt?.let(::UtcTimestamp),
    revision = revision,
    source = source(),
    confidence = confidence(),
)

private fun HouseholdRecipeIngredientEntity.metadata(): EntityMetadata = EntityMetadata(
    id = EntityId(id),
    householdId = HouseholdId(householdId),
    createdAt = UtcTimestamp(createdAt),
    updatedAt = UtcTimestamp(updatedAt),
    archivedAt = archivedAt?.let(::UtcTimestamp),
    revision = revision,
    source = source(),
    confidence = confidence(),
)

private fun HouseholdRecipeStepEntity.metadata(): EntityMetadata = EntityMetadata(
    id = EntityId(id),
    householdId = HouseholdId(householdId),
    createdAt = UtcTimestamp(createdAt),
    updatedAt = UtcTimestamp(updatedAt),
    archivedAt = archivedAt?.let(::UtcTimestamp),
    revision = revision,
    source = source(),
    confidence = confidence(),
)

private fun HouseholdCookingSessionEntity.metadata(): EntityMetadata = EntityMetadata(
    id = EntityId(id),
    householdId = HouseholdId(householdId),
    createdAt = UtcTimestamp(createdAt),
    updatedAt = UtcTimestamp(updatedAt),
    archivedAt = archivedAt?.let(::UtcTimestamp),
    revision = revision,
    source = source(),
    confidence = confidence(),
)

private fun HouseholdPreparedBatchEntity.metadata(): EntityMetadata = EntityMetadata(
    id = EntityId(id),
    householdId = HouseholdId(householdId),
    createdAt = UtcTimestamp(createdAt),
    updatedAt = UtcTimestamp(updatedAt),
    archivedAt = archivedAt?.let(::UtcTimestamp),
    revision = revision,
    source = source(),
    confidence = confidence(),
)

private fun HouseholdMerchantEntity.metadata(): EntityMetadata = EntityMetadata(
    id = EntityId(id),
    householdId = HouseholdId(householdId),
    createdAt = UtcTimestamp(createdAt),
    updatedAt = UtcTimestamp(updatedAt),
    archivedAt = archivedAt?.let(::UtcTimestamp),
    revision = revision,
    source = source(),
    confidence = confidence(),
)

private fun HouseholdPurchaseEntity.metadata(): EntityMetadata = EntityMetadata(
    id = EntityId(id),
    householdId = HouseholdId(householdId),
    createdAt = UtcTimestamp(createdAt),
    updatedAt = UtcTimestamp(updatedAt),
    archivedAt = archivedAt?.let(::UtcTimestamp),
    revision = revision,
    source = source(),
    confidence = confidence(),
)

private fun HouseholdPurchaseLineEntity.metadata(): EntityMetadata = EntityMetadata(
    id = EntityId(id),
    householdId = HouseholdId(householdId),
    createdAt = UtcTimestamp(createdAt),
    updatedAt = UtcTimestamp(updatedAt),
    archivedAt = archivedAt?.let(::UtcTimestamp),
    revision = revision,
    source = source(),
    confidence = confidence(),
)

private fun HouseholdWasteEventEntity.metadata(): EntityMetadata = EntityMetadata(
    id = EntityId(id),
    householdId = HouseholdId(householdId),
    createdAt = UtcTimestamp(createdAt),
    updatedAt = UtcTimestamp(updatedAt),
    archivedAt = archivedAt?.let(::UtcTimestamp),
    revision = revision,
    source = source(),
    confidence = confidence(),
)

private fun HouseholdMealPlanEntity.metadata(): EntityMetadata = EntityMetadata(
    id = EntityId(id),
    householdId = HouseholdId(householdId),
    createdAt = UtcTimestamp(createdAt),
    updatedAt = UtcTimestamp(updatedAt),
    archivedAt = archivedAt?.let(::UtcTimestamp),
    revision = revision,
    source = source(),
    confidence = confidence(),
)

private fun HouseholdMealEntryEntity.metadata(): EntityMetadata = EntityMetadata(
    id = EntityId(id),
    householdId = HouseholdId(householdId),
    createdAt = UtcTimestamp(createdAt),
    updatedAt = UtcTimestamp(updatedAt),
    archivedAt = archivedAt?.let(::UtcTimestamp),
    revision = revision,
    source = source(),
    confidence = confidence(),
)

private fun HouseholdNutritionSnapshotEntity.metadata(): EntityMetadata = EntityMetadata(
    id = EntityId(id),
    householdId = HouseholdId(householdId),
    createdAt = UtcTimestamp(createdAt),
    updatedAt = UtcTimestamp(updatedAt),
    archivedAt = archivedAt?.let(::UtcTimestamp),
    revision = revision,
    source = source(),
    confidence = confidence(),
)

private fun HouseholdChangeProposalEntity.metadata(): EntityMetadata = EntityMetadata(
    id = EntityId(id),
    householdId = HouseholdId(householdId),
    createdAt = UtcTimestamp(createdAt),
    updatedAt = UtcTimestamp(updatedAt),
    archivedAt = archivedAt?.let(::UtcTimestamp),
    revision = revision,
    source = source(),
    confidence = confidence(),
)

private fun HouseholdTombstoneEntity.metadata(): EntityMetadata = EntityMetadata(
    id = EntityId(id),
    householdId = HouseholdId(householdId),
    createdAt = UtcTimestamp(createdAt),
    updatedAt = UtcTimestamp(updatedAt),
    archivedAt = archivedAt?.let(::UtcTimestamp),
    revision = revision,
    source = source(),
    confidence = confidence(),
)

private fun HouseholdConflictEntity.metadata(): EntityMetadata = EntityMetadata(
    id = EntityId(id),
    householdId = HouseholdId(householdId),
    createdAt = UtcTimestamp(createdAt),
    updatedAt = UtcTimestamp(updatedAt),
    archivedAt = archivedAt?.let(::UtcTimestamp),
    revision = revision,
    source = source(),
    confidence = confidence(),
)

private fun HouseholdItemEntity.source(): SourceRef =
    SourceRef(enumValue(sourceKind), sourceLabel, sourceActorId, sourceDeviceId, sourceExternalReference)

private fun HouseholdProfileEntity.source(): SourceRef =
    SourceRef(enumValue(sourceKind), sourceLabel, sourceActorId, sourceDeviceId, sourceExternalReference)

private fun HouseholdFoodDetailsEntity.source(): SourceRef =
    SourceRef(enumValue(sourceKind), sourceLabel, sourceActorId, sourceDeviceId, sourceExternalReference)

private fun HouseholdStorageLocationEntity.source(): SourceRef =
    SourceRef(enumValue(sourceKind), sourceLabel, sourceActorId, sourceDeviceId, sourceExternalReference)

private fun HouseholdInventoryLotEntity.source(): SourceRef =
    SourceRef(enumValue(sourceKind), sourceLabel, sourceActorId, sourceDeviceId, sourceExternalReference)

private fun HouseholdInventoryEventEntity.source(): SourceRef =
    SourceRef(enumValue(sourceKind), sourceLabel, sourceActorId, sourceDeviceId, sourceExternalReference)

private fun HouseholdShoppingLineEntity.source(): SourceRef =
    SourceRef(enumValue(sourceKind), sourceLabel, sourceActorId, sourceDeviceId, sourceExternalReference)

private fun HouseholdShoppingListEntity.source(): SourceRef =
    SourceRef(enumValue(sourceKind), sourceLabel, sourceActorId, sourceDeviceId, sourceExternalReference)

private fun HouseholdRecipeEntity.source(): SourceRef =
    SourceRef(enumValue(sourceKind), sourceLabel, sourceActorId, sourceDeviceId, sourceExternalReference)

private fun HouseholdRecipeIngredientEntity.source(): SourceRef =
    SourceRef(enumValue(sourceKind), sourceLabel, sourceActorId, sourceDeviceId, sourceExternalReference)

private fun HouseholdRecipeStepEntity.source(): SourceRef =
    SourceRef(enumValue(sourceKind), sourceLabel, sourceActorId, sourceDeviceId, sourceExternalReference)

private fun HouseholdCookingSessionEntity.source(): SourceRef =
    SourceRef(enumValue(sourceKind), sourceLabel, sourceActorId, sourceDeviceId, sourceExternalReference)

private fun HouseholdPreparedBatchEntity.source(): SourceRef =
    SourceRef(enumValue(sourceKind), sourceLabel, sourceActorId, sourceDeviceId, sourceExternalReference)

private fun HouseholdMerchantEntity.source(): SourceRef =
    SourceRef(enumValue(sourceKind), sourceLabel, sourceActorId, sourceDeviceId, sourceExternalReference)

private fun HouseholdPurchaseEntity.source(): SourceRef =
    SourceRef(enumValue(sourceKind), sourceLabel, sourceActorId, sourceDeviceId, sourceExternalReference)

private fun HouseholdPurchaseLineEntity.source(): SourceRef =
    SourceRef(enumValue(sourceKind), sourceLabel, sourceActorId, sourceDeviceId, sourceExternalReference)

private fun HouseholdWasteEventEntity.source(): SourceRef =
    SourceRef(enumValue(sourceKind), sourceLabel, sourceActorId, sourceDeviceId, sourceExternalReference)

private fun HouseholdMealPlanEntity.source(): SourceRef =
    SourceRef(enumValue(sourceKind), sourceLabel, sourceActorId, sourceDeviceId, sourceExternalReference)

private fun HouseholdMealEntryEntity.source(): SourceRef =
    SourceRef(enumValue(sourceKind), sourceLabel, sourceActorId, sourceDeviceId, sourceExternalReference)

private fun HouseholdNutritionSnapshotEntity.source(): SourceRef =
    SourceRef(enumValue(sourceKind), sourceLabel, sourceActorId, sourceDeviceId, sourceExternalReference)

private fun HouseholdChangeProposalEntity.source(): SourceRef =
    SourceRef(enumValue(sourceKind), sourceLabel, sourceActorId, sourceDeviceId, sourceExternalReference)

private fun HouseholdTombstoneEntity.source(): SourceRef =
    SourceRef(enumValue(sourceKind), sourceLabel, sourceActorId, sourceDeviceId, sourceExternalReference)

private fun HouseholdConflictEntity.source(): SourceRef =
    SourceRef(enumValue(sourceKind), sourceLabel, sourceActorId, sourceDeviceId, sourceExternalReference)

private fun HouseholdCommandRecordEntity.source(): SourceRef =
    SourceRef(enumValue(sourceKind), sourceLabel, sourceActorId, sourceDeviceId, sourceExternalReference)

private fun HouseholdItemEntity.confidence(): Confidence? =
    confidenceBasisPoints?.let { Confidence(it, confidenceRationale) }

private fun HouseholdProfileEntity.confidence(): Confidence? =
    confidenceBasisPoints?.let { Confidence(it, confidenceRationale) }

private fun HouseholdFoodDetailsEntity.confidence(): Confidence? =
    confidenceBasisPoints?.let { Confidence(it, confidenceRationale) }

private fun HouseholdStorageLocationEntity.confidence(): Confidence? =
    confidenceBasisPoints?.let { Confidence(it, confidenceRationale) }

private fun HouseholdInventoryLotEntity.confidence(): Confidence? =
    confidenceBasisPoints?.let { Confidence(it, confidenceRationale) }

private fun HouseholdInventoryEventEntity.confidence(): Confidence? =
    confidenceBasisPoints?.let { Confidence(it, confidenceRationale) }

private fun HouseholdShoppingLineEntity.confidence(): Confidence? =
    confidenceBasisPoints?.let { Confidence(it, confidenceRationale) }

private fun HouseholdShoppingListEntity.confidence(): Confidence? =
    confidenceBasisPoints?.let { Confidence(it, confidenceRationale) }

private fun HouseholdRecipeEntity.confidence(): Confidence? =
    confidenceBasisPoints?.let { Confidence(it, confidenceRationale) }

private fun HouseholdRecipeIngredientEntity.confidence(): Confidence? =
    confidenceBasisPoints?.let { Confidence(it, confidenceRationale) }

private fun HouseholdRecipeStepEntity.confidence(): Confidence? =
    confidenceBasisPoints?.let { Confidence(it, confidenceRationale) }

private fun HouseholdCookingSessionEntity.confidence(): Confidence? =
    confidenceBasisPoints?.let { Confidence(it, confidenceRationale) }

private fun HouseholdPreparedBatchEntity.confidence(): Confidence? =
    confidenceBasisPoints?.let { Confidence(it, confidenceRationale) }

private fun HouseholdMerchantEntity.confidence(): Confidence? =
    confidenceBasisPoints?.let { Confidence(it, confidenceRationale) }

private fun HouseholdPurchaseEntity.confidence(): Confidence? =
    confidenceBasisPoints?.let { Confidence(it, confidenceRationale) }

private fun HouseholdPurchaseLineEntity.confidence(): Confidence? =
    confidenceBasisPoints?.let { Confidence(it, confidenceRationale) }

private fun HouseholdWasteEventEntity.confidence(): Confidence? =
    confidenceBasisPoints?.let { Confidence(it, confidenceRationale) }

private fun HouseholdMealPlanEntity.confidence(): Confidence? =
    confidenceBasisPoints?.let { Confidence(it, confidenceRationale) }

private fun HouseholdMealEntryEntity.confidence(): Confidence? =
    confidenceBasisPoints?.let { Confidence(it, confidenceRationale) }

private fun HouseholdNutritionSnapshotEntity.confidence(): Confidence? =
    confidenceBasisPoints?.let { Confidence(it, confidenceRationale) }

private fun HouseholdChangeProposalEntity.confidence(): Confidence? =
    confidenceBasisPoints?.let { Confidence(it, confidenceRationale) }

private fun HouseholdTombstoneEntity.confidence(): Confidence? =
    confidenceBasisPoints?.let { Confidence(it, confidenceRationale) }

private fun HouseholdConflictEntity.confidence(): Confidence? =
    confidenceBasisPoints?.let { Confidence(it, confidenceRationale) }

private fun quantity(amount: String?, unit: String): Quantity =
    Quantity(amount?.let(::DecimalAmount), QuantityUnit(unit))

private fun money(minorUnits: Long?, currency: String?): Money? =
    minorUnits?.let { Money(it, requireNotNull(currency) { "Currency is required when money is present." }) }

private inline fun <reified T : Enum<T>> enumValue(value: String): T =
    enumValues<T>().first { it.name == value }
