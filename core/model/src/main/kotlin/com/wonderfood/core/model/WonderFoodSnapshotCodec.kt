package com.wonderfood.core.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

object WonderFoodSnapshotCodec {
    const val CURRENT_SCHEMA_VERSION: Int = 1

    internal val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = false
    }

    fun encode(snapshot: WonderFoodSnapshot): String =
        json.encodeToString(snapshot.toDto())

    fun decode(encoded: String): WonderFoodSnapshot =
        json.decodeFromString<WonderFoodSnapshotDto>(encoded).toDomain()

    fun rows(snapshot: WonderFoodSnapshot, updatedAt: String): List<WonderFoodSnapshotRow> {
        require(updatedAt.isNotBlank()) { "Snapshot row updated timestamp must not be blank." }
        val dto = snapshot.toDto()
        return buildList {
            add(
                WonderFoodSnapshotRow(
                    tab = "_meta",
                    id = "snapshot",
                    version = snapshot.schemaVersion.toLong(),
                    updatedAt = updatedAt,
                    archivedAt = null,
                    payloadJson = json.encodeToString(dto),
                ),
            )
            addAll(dto.pages.map { it.row("pages", it.id, updatedAt) })
            addAll(dto.foods.map { it.row("foods", it.id, updatedAt) })
            addAll(dto.foodAliases.map { it.row("food_aliases", it.id, updatedAt) })
            addAll(dto.stockLots.map { it.row("stock_lots", it.id, updatedAt) })
            addAll(dto.nutritionSnapshots.map { it.row("nutrition_snapshots", it.id, updatedAt) })
            addAll(dto.recipes.map { it.row("recipes", it.id, updatedAt) })
            addAll(dto.mealPlans.map { it.row("meal_plans", it.id, updatedAt) })
            addAll(dto.mealLogs.map { it.row("meal_logs", it.id, updatedAt) })
            addAll(dto.shoppingItems.map { it.row("shopping_items", it.id, updatedAt) })
            addAll(dto.receipts.map { it.row("receipts", it.id, updatedAt) })
            addAll(dto.foodEvents.map { it.row("events", it.id, updatedAt) })
            addAll(dto.relations.map { it.row("relations", it.id, updatedAt) })
            addAll(dto.attachments.map { it.row("attachments", it.id, updatedAt) })
        }
    }

    fun decodeSnapshotRow(row: WonderFoodSnapshotRow): WonderFoodSnapshot? =
        row.takeIf { it.tab == "_meta" && it.id == "snapshot" }
            ?.let { decode(it.payloadJson) }
}

data class WonderFoodSnapshotRow(
    val tab: String,
    val id: String,
    val version: Long,
    val updatedAt: String,
    val archivedAt: String?,
    val payloadJson: String,
) {
    init {
        require(tab.isNotBlank()) { "Snapshot row tab must not be blank." }
        require(id.isNotBlank()) { "Snapshot row id must not be blank." }
        require(version >= 0L) { "Snapshot row version must not be negative." }
        require(updatedAt.isNotBlank()) { "Snapshot row updated timestamp must not be blank." }
        require(payloadJson.isNotBlank()) { "Snapshot row payload must not be blank." }
    }
}

private inline fun <reified T> T.row(tab: String, id: String, updatedAt: String): WonderFoodSnapshotRow =
    WonderFoodSnapshotRow(
        tab = tab,
        id = id,
        version = 1L,
        updatedAt = updatedAt,
        archivedAt = null,
        payloadJson = WonderFoodSnapshotCodec.json.encodeToString(this),
    )

@Serializable
internal data class WonderFoodSnapshotDto(
    val schemaVersion: Int,
    val pages: List<PageDto>,
    val foods: List<FoodDto>,
    val foodAliases: List<FoodAliasDto>,
    val stockLots: List<StockLotDto>,
    val nutritionSnapshots: List<NutritionSnapshotDto>,
    val recipes: List<RecipeDto>,
    val mealPlans: List<MealPlanDto>,
    val mealLogs: List<MealLogDto>,
    val shoppingItems: List<ShoppingItemDto>,
    val receipts: List<ReceiptDto>,
    val foodEvents: List<FoodEventDto>,
    val relations: List<RelationDto>,
    val attachments: List<AttachmentDto>,
)

@Serializable
internal data class EntityRefDto(
    val type: String,
    val id: String,
)

@Serializable
internal data class SourceDto(
    val id: String,
    val kind: String,
    val label: String,
    val externalId: String?,
    val uri: String?,
    val capturedAt: String?,
    val truthState: String,
)

@Serializable
internal data class ConfidenceDto(
    val score: Double?,
    val state: String,
    val rationale: String?,
)

@Serializable
internal data class QuantityDto(
    val amount: Double?,
    val unit: String,
    val truthState: String,
)

@Serializable
internal data class ServingBasisDto(
    val type: String,
    val quantity: QuantityDto,
    val description: String?,
)

@Serializable
internal data class MoneyDto(
    val amount: Double?,
    val currencyCode: String?,
    val truthState: String,
)

@Serializable
internal data class NutritionValuesDto(
    val energyKcal: Double?,
    val proteinGrams: Double?,
    val carbohydrateGrams: Double?,
    val fatGrams: Double?,
    val fiberGrams: Double?,
    val sugarGrams: Double?,
    val sodiumMilligrams: Double?,
)

@Serializable
internal data class PageDto(
    val id: String,
    val title: String,
    val kind: String,
    val entity: EntityRefDto?,
    val aliases: List<String>,
    val relationIds: List<String>,
    val attachmentIds: List<String>,
    val truthState: String,
    val source: SourceDto,
    val confidence: ConfidenceDto,
)

@Serializable
internal data class FoodDto(
    val id: String,
    val pageId: String,
    val name: String,
    val status: String,
    val aliasIds: List<String>,
    val stockLotIds: List<String>,
    val nutritionSnapshotIds: List<String>,
    val attachmentIds: List<String>,
    val source: SourceDto,
    val confidence: ConfidenceDto,
    val truthState: String,
)

@Serializable
internal data class FoodAliasDto(
    val id: String,
    val foodId: String,
    val name: String,
    val locale: String?,
    val source: SourceDto,
    val confidence: ConfidenceDto,
    val truthState: String,
)

@Serializable
internal data class StockLotDto(
    val id: String,
    val foodId: String,
    val quantity: QuantityDto,
    val purchasedOn: String?,
    val expiresOn: String?,
    val location: String?,
    val status: String,
    val source: SourceDto,
    val confidence: ConfidenceDto,
    val truthState: String,
)

@Serializable
internal data class NutritionSnapshotDto(
    val id: String,
    val subject: EntityRefDto,
    val basis: ServingBasisDto,
    val values: NutritionValuesDto,
    val source: SourceDto,
    val confidence: ConfidenceDto,
    val capturedAt: String?,
    val truthState: String,
)

@Serializable
internal data class RecipeDto(
    val id: String,
    val pageId: String,
    val title: String,
    val description: String?,
    val status: String,
    val servings: QuantityDto,
    val prepMinutes: Int?,
    val cookMinutes: Int?,
    val ingredients: List<RecipeIngredientDto>,
    val steps: List<RecipeStepDto>,
    val nutritionSnapshotIds: List<String>,
    val attachmentIds: List<String>,
    val source: SourceDto,
    val confidence: ConfidenceDto,
    val truthState: String,
)

@Serializable
internal data class RecipeIngredientDto(
    val id: String,
    val recipeId: String,
    val foodId: String?,
    val displayName: String,
    val quantity: QuantityDto,
    val preparation: String?,
    val optional: Boolean,
    val substituteFoodIds: List<String>,
    val source: SourceDto,
    val confidence: ConfidenceDto,
    val truthState: String,
)

@Serializable
internal data class RecipeStepDto(
    val id: String,
    val recipeId: String,
    val order: Int,
    val instruction: String,
    val durationMinutes: Int?,
    val attachmentIds: List<String>,
    val source: SourceDto,
    val confidence: ConfidenceDto,
    val truthState: String,
)

@Serializable
internal data class MealPlanDto(
    val id: String,
    val pageId: String,
    val name: String,
    val startsOn: String,
    val endsOn: String,
    val status: String,
    val entries: List<PlanEntryDto>,
    val source: SourceDto,
    val confidence: ConfidenceDto,
    val truthState: String,
)

@Serializable
internal data class PlanEntryDto(
    val id: String,
    val mealPlanId: String,
    val date: String,
    val mealSlot: String,
    val recipeId: String?,
    val foodId: String?,
    val quantity: QuantityDto,
    val status: String,
    val source: SourceDto,
    val confidence: ConfidenceDto,
    val truthState: String,
)

@Serializable
internal data class MealLogDto(
    val id: String,
    val pageId: String,
    val occurredAt: String,
    val mealSlot: String,
    val planEntryId: String?,
    val foodIds: List<String>,
    val recipeIds: List<String>,
    val nutritionSnapshotIds: List<String>,
    val status: String,
    val source: SourceDto,
    val confidence: ConfidenceDto,
    val truthState: String,
)

@Serializable
internal data class ShoppingItemDto(
    val id: String,
    val pageId: String,
    val foodId: String?,
    val recipeId: String?,
    val quantity: QuantityDto,
    val reason: String?,
    val status: String,
    val source: SourceDto,
    val confidence: ConfidenceDto,
    val truthState: String,
)

@Serializable
internal data class ReceiptDto(
    val id: String,
    val pageId: String,
    val merchantName: String?,
    val purchasedAt: String?,
    val itemIds: List<String>,
    val subtotal: MoneyDto?,
    val tax: MoneyDto?,
    val total: MoneyDto?,
    val attachmentIds: List<String>,
    val status: String,
    val source: SourceDto,
    val confidence: ConfidenceDto,
    val truthState: String,
)

@Serializable
internal data class FoodEventDto(
    val id: String,
    val subject: EntityRefDto,
    val type: String,
    val occurredAt: String,
    val quantity: QuantityDto?,
    val note: String?,
    val source: SourceDto,
    val confidence: ConfidenceDto,
    val truthState: String,
)

@Serializable
internal data class RelationDto(
    val id: String,
    val from: EntityRefDto,
    val to: EntityRefDto,
    val type: String,
    val source: SourceDto,
    val confidence: ConfidenceDto,
    val truthState: String,
)

@Serializable
internal data class AttachmentDto(
    val id: String,
    val kind: String,
    val uri: String,
    val label: String?,
    val checksum: String?,
    val source: SourceDto,
    val confidence: ConfidenceDto,
    val truthState: String,
)

private fun WonderFoodSnapshot.toDto() = WonderFoodSnapshotDto(
    schemaVersion = schemaVersion,
    pages = pages.map { it.toDto() },
    foods = foods.map { it.toDto() },
    foodAliases = foodAliases.map { it.toDto() },
    stockLots = stockLots.map { it.toDto() },
    nutritionSnapshots = nutritionSnapshots.map { it.toDto() },
    recipes = recipes.map { it.toDto() },
    mealPlans = mealPlans.map { it.toDto() },
    mealLogs = mealLogs.map { it.toDto() },
    shoppingItems = shoppingItems.map { it.toDto() },
    receipts = receipts.map { it.toDto() },
    foodEvents = foodEvents.map { it.toDto() },
    relations = relations.map { it.toDto() },
    attachments = attachments.map { it.toDto() },
)

private fun WonderFoodSnapshotDto.toDomain() = WonderFoodSnapshot(
    schemaVersion = schemaVersion,
    pages = pages.map { it.toDomain() },
    foods = foods.map { it.toDomain() },
    foodAliases = foodAliases.map { it.toDomain() },
    stockLots = stockLots.map { it.toDomain() },
    nutritionSnapshots = nutritionSnapshots.map { it.toDomain() },
    recipes = recipes.map { it.toDomain() },
    mealPlans = mealPlans.map { it.toDomain() },
    mealLogs = mealLogs.map { it.toDomain() },
    shoppingItems = shoppingItems.map { it.toDomain() },
    receipts = receipts.map { it.toDomain() },
    foodEvents = foodEvents.map { it.toDomain() },
    relations = relations.map { it.toDomain() },
    attachments = attachments.map { it.toDomain() },
)

private fun EntityRef.toDto() = EntityRefDto(
    type = type.name,
    id = id,
)

private fun EntityRefDto.toDomain() = EntityRef(
    type = enumByName(type),
    id = id,
)

private fun Source.toDto() = SourceDto(
    id = id.value,
    kind = kind.name,
    label = label,
    externalId = externalId,
    uri = uri,
    capturedAt = capturedAt?.value,
    truthState = truthState.name,
)

private fun SourceDto.toDomain() = Source(
    id = SourceId(id),
    kind = enumByName(kind),
    label = label,
    externalId = externalId,
    uri = uri,
    capturedAt = capturedAt?.let(::IsoTimestamp),
    truthState = enumByName(truthState),
)

private fun Confidence.toDto() = ConfidenceDto(
    score = score,
    state = state.name,
    rationale = rationale,
)

private fun ConfidenceDto.toDomain() = Confidence(
    score = score,
    state = enumByName(state),
    rationale = rationale,
)

private fun Quantity.toDto() = QuantityDto(
    amount = amount,
    unit = unit.name,
    truthState = truthState.name,
)

private fun QuantityDto.toDomain() = Quantity(
    amount = amount,
    unit = enumByName(unit),
    truthState = enumByName(truthState),
)

private fun ServingBasis.toDto() = ServingBasisDto(
    type = type.name,
    quantity = quantity.toDto(),
    description = description,
)

private fun ServingBasisDto.toDomain() = ServingBasis(
    type = enumByName(type),
    quantity = quantity.toDomain(),
    description = description,
)

private fun Money.toDto() = MoneyDto(
    amount = amount,
    currencyCode = currencyCode,
    truthState = truthState.name,
)

private fun MoneyDto.toDomain() = Money(
    amount = amount,
    currencyCode = currencyCode,
    truthState = enumByName(truthState),
)

private fun NutritionValues.toDto() = NutritionValuesDto(
    energyKcal = energyKcal,
    proteinGrams = proteinGrams,
    carbohydrateGrams = carbohydrateGrams,
    fatGrams = fatGrams,
    fiberGrams = fiberGrams,
    sugarGrams = sugarGrams,
    sodiumMilligrams = sodiumMilligrams,
)

private fun NutritionValuesDto.toDomain() = NutritionValues(
    energyKcal = energyKcal,
    proteinGrams = proteinGrams,
    carbohydrateGrams = carbohydrateGrams,
    fatGrams = fatGrams,
    fiberGrams = fiberGrams,
    sugarGrams = sugarGrams,
    sodiumMilligrams = sodiumMilligrams,
)

private fun Page.toDto() = PageDto(
    id = id.value,
    title = title,
    kind = kind.name,
    entity = entity?.toDto(),
    aliases = aliases,
    relationIds = relationIds.map { it.value },
    attachmentIds = attachmentIds.map { it.value },
    truthState = truthState.name,
    source = source.toDto(),
    confidence = confidence.toDto(),
)

private fun PageDto.toDomain() = Page(
    id = PageId(id),
    title = title,
    kind = enumByName(kind),
    entity = entity?.toDomain(),
    aliases = aliases,
    relationIds = relationIds.map(::RelationId),
    attachmentIds = attachmentIds.map(::AttachmentId),
    truthState = enumByName(truthState),
    source = source.toDomain(),
    confidence = confidence.toDomain(),
)

private fun Food.toDto() = FoodDto(
    id = id.value,
    pageId = pageId.value,
    name = name,
    status = status.name,
    aliasIds = aliasIds.map { it.value },
    stockLotIds = stockLotIds.map { it.value },
    nutritionSnapshotIds = nutritionSnapshotIds.map { it.value },
    attachmentIds = attachmentIds.map { it.value },
    source = source.toDto(),
    confidence = confidence.toDto(),
    truthState = truthState.name,
)

private fun FoodDto.toDomain() = Food(
    id = FoodId(id),
    pageId = PageId(pageId),
    name = name,
    status = enumByName(status),
    aliasIds = aliasIds.map(::FoodAliasId),
    stockLotIds = stockLotIds.map(::StockLotId),
    nutritionSnapshotIds = nutritionSnapshotIds.map(::NutritionSnapshotId),
    attachmentIds = attachmentIds.map(::AttachmentId),
    source = source.toDomain(),
    confidence = confidence.toDomain(),
    truthState = enumByName(truthState),
)

private fun FoodAlias.toDto() = FoodAliasDto(
    id = id.value,
    foodId = foodId.value,
    name = name,
    locale = locale,
    source = source.toDto(),
    confidence = confidence.toDto(),
    truthState = truthState.name,
)

private fun FoodAliasDto.toDomain() = FoodAlias(
    id = FoodAliasId(id),
    foodId = FoodId(foodId),
    name = name,
    locale = locale,
    source = source.toDomain(),
    confidence = confidence.toDomain(),
    truthState = enumByName(truthState),
)

private fun StockLot.toDto() = StockLotDto(
    id = id.value,
    foodId = foodId.value,
    quantity = quantity.toDto(),
    purchasedOn = purchasedOn?.value,
    expiresOn = expiresOn?.value,
    location = location,
    status = status.name,
    source = source.toDto(),
    confidence = confidence.toDto(),
    truthState = truthState.name,
)

private fun StockLotDto.toDomain() = StockLot(
    id = StockLotId(id),
    foodId = FoodId(foodId),
    quantity = quantity.toDomain(),
    purchasedOn = purchasedOn?.let(::IsoDate),
    expiresOn = expiresOn?.let(::IsoDate),
    location = location,
    status = enumByName(status),
    source = source.toDomain(),
    confidence = confidence.toDomain(),
    truthState = enumByName(truthState),
)

private fun NutritionSnapshot.toDto() = NutritionSnapshotDto(
    id = id.value,
    subject = subject.toDto(),
    basis = basis.toDto(),
    values = values.toDto(),
    source = source.toDto(),
    confidence = confidence.toDto(),
    capturedAt = capturedAt?.value,
    truthState = truthState.name,
)

private fun NutritionSnapshotDto.toDomain() = NutritionSnapshot(
    id = NutritionSnapshotId(id),
    subject = subject.toDomain(),
    basis = basis.toDomain(),
    values = values.toDomain(),
    source = source.toDomain(),
    confidence = confidence.toDomain(),
    capturedAt = capturedAt?.let(::IsoTimestamp),
    truthState = enumByName(truthState),
)

private fun Recipe.toDto() = RecipeDto(
    id = id.value,
    pageId = pageId.value,
    title = title,
    description = description,
    status = status.name,
    servings = servings.toDto(),
    prepMinutes = prepMinutes,
    cookMinutes = cookMinutes,
    ingredients = ingredients.map { it.toDto() },
    steps = steps.map { it.toDto() },
    nutritionSnapshotIds = nutritionSnapshotIds.map { it.value },
    attachmentIds = attachmentIds.map { it.value },
    source = source.toDto(),
    confidence = confidence.toDto(),
    truthState = truthState.name,
)

private fun RecipeDto.toDomain() = Recipe(
    id = RecipeId(id),
    pageId = PageId(pageId),
    title = title,
    description = description,
    status = enumByName(status),
    servings = servings.toDomain(),
    prepMinutes = prepMinutes,
    cookMinutes = cookMinutes,
    ingredients = ingredients.map { it.toDomain() },
    steps = steps.map { it.toDomain() },
    nutritionSnapshotIds = nutritionSnapshotIds.map(::NutritionSnapshotId),
    attachmentIds = attachmentIds.map(::AttachmentId),
    source = source.toDomain(),
    confidence = confidence.toDomain(),
    truthState = enumByName(truthState),
)

private fun RecipeIngredient.toDto() = RecipeIngredientDto(
    id = id.value,
    recipeId = recipeId.value,
    foodId = foodId?.value,
    displayName = displayName,
    quantity = quantity.toDto(),
    preparation = preparation,
    optional = optional,
    substituteFoodIds = substituteFoodIds.map { it.value },
    source = source.toDto(),
    confidence = confidence.toDto(),
    truthState = truthState.name,
)

private fun RecipeIngredientDto.toDomain() = RecipeIngredient(
    id = RecipeIngredientId(id),
    recipeId = RecipeId(recipeId),
    foodId = foodId?.let(::FoodId),
    displayName = displayName,
    quantity = quantity.toDomain(),
    preparation = preparation,
    optional = optional,
    substituteFoodIds = substituteFoodIds.map(::FoodId),
    source = source.toDomain(),
    confidence = confidence.toDomain(),
    truthState = enumByName(truthState),
)

private fun RecipeStep.toDto() = RecipeStepDto(
    id = id.value,
    recipeId = recipeId.value,
    order = order,
    instruction = instruction,
    durationMinutes = durationMinutes,
    attachmentIds = attachmentIds.map { it.value },
    source = source.toDto(),
    confidence = confidence.toDto(),
    truthState = truthState.name,
)

private fun RecipeStepDto.toDomain() = RecipeStep(
    id = RecipeStepId(id),
    recipeId = RecipeId(recipeId),
    order = order,
    instruction = instruction,
    durationMinutes = durationMinutes,
    attachmentIds = attachmentIds.map(::AttachmentId),
    source = source.toDomain(),
    confidence = confidence.toDomain(),
    truthState = enumByName(truthState),
)

private fun MealPlan.toDto() = MealPlanDto(
    id = id.value,
    pageId = pageId.value,
    name = name,
    startsOn = startsOn.value,
    endsOn = endsOn.value,
    status = status.name,
    entries = entries.map { it.toDto() },
    source = source.toDto(),
    confidence = confidence.toDto(),
    truthState = truthState.name,
)

private fun MealPlanDto.toDomain() = MealPlan(
    id = MealPlanId(id),
    pageId = PageId(pageId),
    name = name,
    startsOn = IsoDate(startsOn),
    endsOn = IsoDate(endsOn),
    status = enumByName(status),
    entries = entries.map { it.toDomain() },
    source = source.toDomain(),
    confidence = confidence.toDomain(),
    truthState = enumByName(truthState),
)

private fun PlanEntry.toDto() = PlanEntryDto(
    id = id.value,
    mealPlanId = mealPlanId.value,
    date = date.value,
    mealSlot = mealSlot.name,
    recipeId = recipeId?.value,
    foodId = foodId?.value,
    quantity = quantity.toDto(),
    status = status.name,
    source = source.toDto(),
    confidence = confidence.toDto(),
    truthState = truthState.name,
)

private fun PlanEntryDto.toDomain() = PlanEntry(
    id = PlanEntryId(id),
    mealPlanId = MealPlanId(mealPlanId),
    date = IsoDate(date),
    mealSlot = enumByName(mealSlot),
    recipeId = recipeId?.let(::RecipeId),
    foodId = foodId?.let(::FoodId),
    quantity = quantity.toDomain(),
    status = enumByName(status),
    source = source.toDomain(),
    confidence = confidence.toDomain(),
    truthState = enumByName(truthState),
)

private fun MealLog.toDto() = MealLogDto(
    id = id.value,
    pageId = pageId.value,
    occurredAt = occurredAt.value,
    mealSlot = mealSlot.name,
    planEntryId = planEntryId?.value,
    foodIds = foodIds.map { it.value },
    recipeIds = recipeIds.map { it.value },
    nutritionSnapshotIds = nutritionSnapshotIds.map { it.value },
    status = status.name,
    source = source.toDto(),
    confidence = confidence.toDto(),
    truthState = truthState.name,
)

private fun MealLogDto.toDomain() = MealLog(
    id = MealLogId(id),
    pageId = PageId(pageId),
    occurredAt = IsoTimestamp(occurredAt),
    mealSlot = enumByName(mealSlot),
    planEntryId = planEntryId?.let(::PlanEntryId),
    foodIds = foodIds.map(::FoodId),
    recipeIds = recipeIds.map(::RecipeId),
    nutritionSnapshotIds = nutritionSnapshotIds.map(::NutritionSnapshotId),
    status = enumByName(status),
    source = source.toDomain(),
    confidence = confidence.toDomain(),
    truthState = enumByName(truthState),
)

private fun ShoppingItem.toDto() = ShoppingItemDto(
    id = id.value,
    pageId = pageId.value,
    foodId = foodId?.value,
    recipeId = recipeId?.value,
    quantity = quantity.toDto(),
    reason = reason,
    status = status.name,
    source = source.toDto(),
    confidence = confidence.toDto(),
    truthState = truthState.name,
)

private fun ShoppingItemDto.toDomain() = ShoppingItem(
    id = ShoppingItemId(id),
    pageId = PageId(pageId),
    foodId = foodId?.let(::FoodId),
    recipeId = recipeId?.let(::RecipeId),
    quantity = quantity.toDomain(),
    reason = reason,
    status = enumByName(status),
    source = source.toDomain(),
    confidence = confidence.toDomain(),
    truthState = enumByName(truthState),
)

private fun Receipt.toDto() = ReceiptDto(
    id = id.value,
    pageId = pageId.value,
    merchantName = merchantName,
    purchasedAt = purchasedAt?.value,
    itemIds = itemIds.map { it.value },
    subtotal = subtotal?.toDto(),
    tax = tax?.toDto(),
    total = total?.toDto(),
    attachmentIds = attachmentIds.map { it.value },
    status = status.name,
    source = source.toDto(),
    confidence = confidence.toDto(),
    truthState = truthState.name,
)

private fun ReceiptDto.toDomain() = Receipt(
    id = ReceiptId(id),
    pageId = PageId(pageId),
    merchantName = merchantName,
    purchasedAt = purchasedAt?.let(::IsoTimestamp),
    itemIds = itemIds.map(::ShoppingItemId),
    subtotal = subtotal?.toDomain(),
    tax = tax?.toDomain(),
    total = total?.toDomain(),
    attachmentIds = attachmentIds.map(::AttachmentId),
    status = enumByName(status),
    source = source.toDomain(),
    confidence = confidence.toDomain(),
    truthState = enumByName(truthState),
)

private fun FoodEvent.toDto() = FoodEventDto(
    id = id.value,
    subject = subject.toDto(),
    type = type.name,
    occurredAt = occurredAt.value,
    quantity = quantity?.toDto(),
    note = note,
    source = source.toDto(),
    confidence = confidence.toDto(),
    truthState = truthState.name,
)

private fun FoodEventDto.toDomain() = FoodEvent(
    id = FoodEventId(id),
    subject = subject.toDomain(),
    type = enumByName(type),
    occurredAt = IsoTimestamp(occurredAt),
    quantity = quantity?.toDomain(),
    note = note,
    source = source.toDomain(),
    confidence = confidence.toDomain(),
    truthState = enumByName(truthState),
)

private fun Relation.toDto() = RelationDto(
    id = id.value,
    from = from.toDto(),
    to = to.toDto(),
    type = type.name,
    source = source.toDto(),
    confidence = confidence.toDto(),
    truthState = truthState.name,
)

private fun RelationDto.toDomain() = Relation(
    id = RelationId(id),
    from = from.toDomain(),
    to = to.toDomain(),
    type = enumByName(type),
    source = source.toDomain(),
    confidence = confidence.toDomain(),
    truthState = enumByName(truthState),
)

private fun Attachment.toDto() = AttachmentDto(
    id = id.value,
    kind = kind.name,
    uri = uri,
    label = label,
    checksum = checksum,
    source = source.toDto(),
    confidence = confidence.toDto(),
    truthState = truthState.name,
)

private fun AttachmentDto.toDomain() = Attachment(
    id = AttachmentId(id),
    kind = enumByName(kind),
    uri = uri,
    label = label,
    checksum = checksum,
    source = source.toDomain(),
    confidence = confidence.toDomain(),
    truthState = enumByName(truthState),
)

private inline fun <reified T : Enum<T>> enumByName(name: String): T =
    enumValues<T>().firstOrNull { it.name == name } ?: enumValues<T>().first()
