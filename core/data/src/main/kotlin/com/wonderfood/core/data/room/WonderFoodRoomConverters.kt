package com.wonderfood.core.data.room

import androidx.room.TypeConverter
import com.wonderfood.core.model.AttachmentId
import com.wonderfood.core.model.AttachmentKind
import com.wonderfood.core.model.EntityRef
import com.wonderfood.core.model.EntityType
import com.wonderfood.core.model.FoodAliasId
import com.wonderfood.core.model.FoodEventType
import com.wonderfood.core.model.FoodId
import com.wonderfood.core.model.FoodStatus
import com.wonderfood.core.model.FoodUnit
import com.wonderfood.core.model.IsoDate
import com.wonderfood.core.model.IsoTimestamp
import com.wonderfood.core.model.MealLogStatus
import com.wonderfood.core.model.MealPlanStatus
import com.wonderfood.core.model.MealSlot
import com.wonderfood.core.model.NutritionBasisType
import com.wonderfood.core.model.NutritionSnapshotId
import com.wonderfood.core.model.PageKind
import com.wonderfood.core.model.PlanEntryStatus
import com.wonderfood.core.model.RecipeId
import com.wonderfood.core.model.ReceiptStatus
import com.wonderfood.core.model.RelationId
import com.wonderfood.core.model.RelationType
import com.wonderfood.core.model.ShoppingItemStatus
import com.wonderfood.core.model.SourceKind
import com.wonderfood.core.model.StockLotId
import com.wonderfood.core.model.StockLotStatus
import com.wonderfood.core.model.TruthState
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

class WonderFoodRoomConverters {
    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = false
    }

    @TypeConverter
    fun stringListToJson(value: List<String>): String =
        json.encodeToString(ListSerializer(String.serializer()), value)

    @TypeConverter
    fun jsonToStringList(value: String): List<String> =
        json.decodeFromString(ListSerializer(String.serializer()), value)

    @TypeConverter
    fun stringMapToJson(value: Map<String, String>): String =
        json.encodeToString(MapSerializer(String.serializer(), String.serializer()), value)

    @TypeConverter
    fun jsonToStringMap(value: String): Map<String, String> =
        json.decodeFromString(MapSerializer(String.serializer(), String.serializer()), value)

    @TypeConverter
    fun entityRefToJson(value: EntityRef?): String? =
        value?.let { json.encodeToString(PersistedEntityRef.serializer(), PersistedEntityRef(it.type.name, it.id)) }

    @TypeConverter
    fun jsonToEntityRef(value: String?): EntityRef? =
        value?.let {
            val persisted = json.decodeFromString(PersistedEntityRef.serializer(), it)
            EntityRef(enumValueOrUnknown(persisted.type), persisted.id)
        }

    @TypeConverter fun pageKindToString(value: PageKind): String = value.name
    @TypeConverter fun stringToPageKind(value: String): PageKind = enumValueOrUnknown(value)
    @TypeConverter fun sourceKindToString(value: SourceKind): String = value.name
    @TypeConverter fun stringToSourceKind(value: String): SourceKind = enumValueOrUnknown(value)
    @TypeConverter fun truthStateToString(value: TruthState): String = value.name
    @TypeConverter fun stringToTruthState(value: String): TruthState = enumValueOrUnknown(value)
    @TypeConverter fun entityTypeToString(value: EntityType?): String? = value?.name
    @TypeConverter fun stringToEntityType(value: String?): EntityType? = value?.let(::enumValueOrUnknown)
    @TypeConverter fun foodUnitToString(value: FoodUnit): String = value.name
    @TypeConverter fun stringToFoodUnit(value: String): FoodUnit = enumValueOrUnknown(value)
    @TypeConverter fun nutritionBasisTypeToString(value: NutritionBasisType): String = value.name
    @TypeConverter fun stringToNutritionBasisType(value: String): NutritionBasisType = enumValueOrUnknown(value)
    @TypeConverter fun foodStatusToString(value: FoodStatus): String = value.name
    @TypeConverter fun stringToFoodStatus(value: String): FoodStatus = enumValueOrUnknown(value)
    @TypeConverter fun stockLotStatusToString(value: StockLotStatus): String = value.name
    @TypeConverter fun stringToStockLotStatus(value: String): StockLotStatus = enumValueOrUnknown(value)
    @TypeConverter fun recipeStatusToString(value: com.wonderfood.core.model.RecipeStatus): String = value.name
    @TypeConverter fun stringToRecipeStatus(value: String): com.wonderfood.core.model.RecipeStatus = enumValueOrUnknown(value)
    @TypeConverter fun mealSlotToString(value: MealSlot): String = value.name
    @TypeConverter fun stringToMealSlot(value: String): MealSlot = enumValueOrUnknown(value)
    @TypeConverter fun mealPlanStatusToString(value: MealPlanStatus): String = value.name
    @TypeConverter fun stringToMealPlanStatus(value: String): MealPlanStatus = enumValueOrUnknown(value)
    @TypeConverter fun planEntryStatusToString(value: PlanEntryStatus): String = value.name
    @TypeConverter fun stringToPlanEntryStatus(value: String): PlanEntryStatus = enumValueOrUnknown(value)
    @TypeConverter fun mealLogStatusToString(value: MealLogStatus): String = value.name
    @TypeConverter fun stringToMealLogStatus(value: String): MealLogStatus = enumValueOrUnknown(value)
    @TypeConverter fun shoppingItemStatusToString(value: ShoppingItemStatus): String = value.name
    @TypeConverter fun stringToShoppingItemStatus(value: String): ShoppingItemStatus = enumValueOrUnknown(value)
    @TypeConverter fun receiptStatusToString(value: ReceiptStatus): String = value.name
    @TypeConverter fun stringToReceiptStatus(value: String): ReceiptStatus = enumValueOrUnknown(value)
    @TypeConverter fun foodEventTypeToString(value: FoodEventType): String = value.name
    @TypeConverter fun stringToFoodEventType(value: String): FoodEventType = enumValueOrUnknown(value)
    @TypeConverter fun relationTypeToString(value: RelationType): String = value.name
    @TypeConverter fun stringToRelationType(value: String): RelationType = enumValueOrUnknown(value)
    @TypeConverter fun attachmentKindToString(value: AttachmentKind): String = value.name
    @TypeConverter fun stringToAttachmentKind(value: String): AttachmentKind = enumValueOrUnknown(value)

    @TypeConverter fun isoDateToString(value: IsoDate?): String? = value?.value
    @TypeConverter fun stringToIsoDate(value: String?): IsoDate? = value?.let(::IsoDate)
    @TypeConverter fun isoTimestampToString(value: IsoTimestamp?): String? = value?.value
    @TypeConverter fun stringToIsoTimestamp(value: String?): IsoTimestamp? = value?.let(::IsoTimestamp)
    @TypeConverter fun foodIdToString(value: FoodId?): String? = value?.value
    @TypeConverter fun stringToFoodId(value: String?): FoodId? = value?.let(::FoodId)
    @TypeConverter fun recipeIdToString(value: RecipeId?): String? = value?.value
    @TypeConverter fun stringToRecipeId(value: String?): RecipeId? = value?.let(::RecipeId)
    @TypeConverter fun relationIdToString(value: RelationId?): String? = value?.value
    @TypeConverter fun stringToRelationId(value: String?): RelationId? = value?.let(::RelationId)
    @TypeConverter fun attachmentIdToString(value: AttachmentId?): String? = value?.value
    @TypeConverter fun stringToAttachmentId(value: String?): AttachmentId? = value?.let(::AttachmentId)
    @TypeConverter fun foodAliasIdToString(value: FoodAliasId?): String? = value?.value
    @TypeConverter fun stringToFoodAliasId(value: String?): FoodAliasId? = value?.let(::FoodAliasId)
    @TypeConverter fun stockLotIdToString(value: StockLotId?): String? = value?.value
    @TypeConverter fun stringToStockLotId(value: String?): StockLotId? = value?.let(::StockLotId)
    @TypeConverter fun nutritionSnapshotIdToString(value: NutritionSnapshotId?): String? = value?.value
    @TypeConverter fun stringToNutritionSnapshotId(value: String?): NutritionSnapshotId? = value?.let(::NutritionSnapshotId)
}

@Serializable
private data class PersistedEntityRef(
    val type: String,
    val id: String,
)

private inline fun <reified T : Enum<T>> enumValueOrUnknown(value: String): T =
    enumValues<T>().firstOrNull { it.name == value } ?: enumValues<T>().first()
