package com.wonderfood.app.data

import com.wonderfood.core.model.household.HouseholdSnapshot
import com.wonderfood.core.model.household.HouseholdEntityType
import com.wonderfood.core.model.household.InventoryLotStatus
import com.wonderfood.core.model.household.ItemKind
import com.wonderfood.core.model.household.Quantity
import com.wonderfood.core.model.household.QuantityUnit
import com.wonderfood.core.model.household.onHand

data class CanonicalKitchenPreviewItem(
    val id: String,
    val title: String,
    val subtitle: String,
) {
    companion object {
        private val activeLotStatuses = setOf(
            InventoryLotStatus.AVAILABLE,
            InventoryLotStatus.OPENED,
            InventoryLotStatus.RESERVED,
        )

        fun fromSnapshot(snapshot: HouseholdSnapshot?): List<CanonicalKitchenPreviewItem> {
            snapshot ?: return emptyList()
            val activeItemIds = snapshot.inventoryLots
                .asSequence()
                .filter { it.metadata.archivedAt == null }
                .filter { it.status in activeLotStatuses }
                .map { it.itemId }
                .toSet()
            val activeLotsByItem = snapshot.inventoryLots
                .asSequence()
                .filter { it.metadata.archivedAt == null }
                .filter { it.status in activeLotStatuses }
                .groupBy { it.itemId }
            val locationsById = snapshot.storageLocations.associateBy { it.metadata.id }
            val foodDetailsById = snapshot.foodDetails.associateBy { it.metadata.id }
            val nutritionById = snapshot.nutritionSnapshots.associateBy { it.metadata.id }
            val nutritionByItem = snapshot.nutritionSnapshots
                .filter { it.subject.type == HouseholdEntityType.ITEM }
                .groupBy { it.subject.id }

            return snapshot.items
                .asSequence()
                .filter { it.metadata.archivedAt == null }
                .filter { it.metadata.id in activeItemIds }
                .sortedBy { it.name.lowercase() }
                .map { item ->
                    val quantity = snapshot.onHand(item.metadata.id, item.defaultUnit)
                    val lots = activeLotsByItem[item.metadata.id].orEmpty()
                    val soonestExpiry = lots.mapNotNull { it.expiresOn?.value }.minOrNull()
                    val storage = lots.firstNotNullOfOrNull { lot -> lot.locationId?.let(locationsById::get)?.name }
                    val nutrition = if (item.kind == ItemKind.FOOD) {
                        val linkedNutrition = item.foodDetailsId
                            ?.let(foodDetailsById::get)
                            ?.nutritionSnapshotIds
                            .orEmpty()
                            .firstNotNullOfOrNull(nutritionById::get)
                        linkedNutrition ?: nutritionByItem[item.metadata.id].orEmpty().maxByOrNull { it.capturedAt.epochMillis }
                    } else {
                        null
                    }
                    CanonicalKitchenPreviewItem(
                        id = item.metadata.id.value,
                        title = item.name,
                        subtitle = listOf(
                            quantity.displayText(),
                            "${lots.size} lot${if (lots.size == 1) "" else "s"}",
                            storage?.let { "in $it" },
                            soonestExpiry?.let { "best by $it" },
                            item.kind.displayText(),
                            item.category,
                            nutrition?.values?.displayText(),
                        ).filterNotNull().filter { it.isNotBlank() }.joinToString("  "),
                    )
                }
                .toList()
        }
    }
}

private fun Quantity.displayText(): String =
    buildList {
        amount?.value?.let(::add)
        unit.takeIf { it != QuantityUnit.UNKNOWN }?.code?.replace('_', ' ')?.let(::add)
    }.joinToString(" ").ifBlank { "quantity unknown" }

private fun ItemKind.displayText(): String =
    name.lowercase().replace('_', ' ')

private fun com.wonderfood.core.model.household.NutritionValues.displayText(): String =
    buildList {
        energyKcal?.value?.let { add("$it kcal") }
        proteinGrams?.value?.let { add("${it}g protein") }
    }.joinToString("  ")
