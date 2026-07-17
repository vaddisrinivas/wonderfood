package com.wonderfood.app.ai

import com.wonderfood.app.data.AiTurn
import com.wonderfood.app.data.FoodCandidate
import com.wonderfood.app.data.FoodMemory
import com.wonderfood.app.data.GroceryDraft
import com.wonderfood.app.data.InventoryDraft
import com.wonderfood.app.data.StorageZone
import com.wonderfood.app.data.categorizeFood
import com.wonderfood.app.data.classifyStorageZone
import com.wonderfood.app.data.foodEmojiForName
import java.util.Locale

object DeterministicPurchaseTemplates {
    fun tryTurn(text: String, memory: FoodMemory, promptContext: String? = null): AiTurn? {
        val lower = text.lowercase()
        val contextLower = promptContext.orEmpty().lowercase()
        val target = targetFor(lower, contextLower)
        val template = when {
            listOf("weekly costco", "costco weekly", "costco restock", "costco run").any { it in lower } ->
                Template(
                    label = "weekly Costco",
                    items = listOf(
                        templateItem("Eggs", "24 count", StorageZone.FRIDGE),
                        templateItem("Greek Yogurt", "1 tub", StorageZone.FRIDGE),
                        templateItem("Spinach", "1 box", StorageZone.FRIDGE),
                        templateItem("Frozen Berries", "1 bag", StorageZone.FREEZER),
                        templateItem("Rolled Oats", "1 bag", StorageZone.PANTRY),
                        templateItem("Milk", "1 gallon", StorageZone.FRIDGE),
                    ),
                )
            listOf("indian groceries", "indian grocery", "desi groceries", "desi grocery").any { it in lower } ->
                Template(
                    label = "Indian groceries",
                    items = listOf(
                        templateItem("Sona Masoori Rice", "1 bag", StorageZone.PANTRY),
                        templateItem("Toor Dal", "2 lb", StorageZone.PANTRY),
                        templateItem("Onions", "1 bag", StorageZone.PANTRY),
                        templateItem("Tomatoes", "6", StorageZone.FRIDGE),
                        templateItem("Green Chilies", "1 pack", StorageZone.FRIDGE),
                        templateItem("Cilantro", "1 bunch", StorageZone.FRIDGE),
                        templateItem("Curd", "1 tub", StorageZone.FRIDGE),
                    ),
                )
            lower.referencesStaplesTemplate() -> preferredStaplesTemplate(memory)
            else -> null
        } ?: return null

        val draft = when (target) {
            TemplateTarget.INVENTORY -> InventoryDraft(template.items)
            TemplateTarget.GROCERY -> GroceryDraft(template.items)
        }
        return AiTurn(
            reply = when (target) {
                TemplateTarget.INVENTORY ->
                    "I loaded the ${template.label} purchase template as pantry/fridge/freezer items. Review before saving."
                TemplateTarget.GROCERY ->
                    "I loaded the ${template.label} purchase template as a shopping list draft. Review before saving."
            },
            draft = draft,
        )
    }

    private fun targetFor(lower: String, contextLower: String): TemplateTarget {
        val grocerySignals = listOf("need", "buy", "to buy", "shopping", "grocery", "groceries", "shop")
        val inventorySignals = listOf("bought", "got", "picked up", "stock", "restock", "pantry", "fridge", "freezer", "kitchen")
        return when {
            grocerySignals.any { it in lower } || contextLower.looksLikeGroceryContext() -> TemplateTarget.GROCERY
            inventorySignals.any { it in lower } || contextLower.looksLikeInventoryContext() -> TemplateTarget.INVENTORY
            else -> TemplateTarget.GROCERY
        }
    }

    private fun preferredStaplesTemplate(memory: FoodMemory): Template {
        val customStaples = memory.preferences.preferredStaples
            .split(",", ";", "\n")
            .map { it.trim() }
            .filter { it.length >= 2 }
            .take(12)
        val names = customStaples.ifEmpty {
            listOf("Rice", "Dal", "Curd", "Oats", "Eggs", "Spinach")
        }
        return Template(
            label = "preferred staples",
            items = names.map { name ->
                templateItem(
                    name = name,
                    quantity = "",
                    zone = classifyStorageZone(name),
                )
            },
        )
    }

    private fun templateItem(name: String, quantity: String, zone: StorageZone): FoodCandidate =
        name.cleanTemplateName().let { cleanName ->
            FoodCandidate(
                name = cleanName,
                quantity = quantity,
                zone = zone,
                category = categorizeFood(cleanName),
                notes = "purchase_template",
                imageUri = foodEmojiForName(cleanName),
            )
        }

    private fun String.referencesStaplesTemplate(): Boolean =
        Regex("""\b(preferred\s+)?staples?\b""").containsMatchIn(this) &&
            listOf("add", "need", "buy", "stock", "restock", "template", "shopping", "grocery", "kitchen", "pantry").any { it in this }

    private fun String.looksLikeGroceryContext(): Boolean =
        listOf("section: shop", "shopping", "grocery item", "to-buy", "to buy").any { it in this }

    private fun String.looksLikeInventoryContext(): Boolean =
        listOf("section: kitchen", "kitchen:", "kitchen item", "pantry, fridge, freezer", "food memory").any { it in this }

    private fun String.cleanTemplateName(): String =
        trim()
            .replace(Regex("""\s+"""), " ")
            .split(" ")
            .filter { it.isNotBlank() }
            .joinToString(" ") { word ->
                word.replaceFirstChar { first ->
                    if (first.isLowerCase()) first.titlecase(Locale.US) else first.toString()
                }
            }

    private data class Template(
        val label: String,
        val items: List<FoodCandidate>,
    )

    private enum class TemplateTarget {
        INVENTORY,
        GROCERY,
    }
}
