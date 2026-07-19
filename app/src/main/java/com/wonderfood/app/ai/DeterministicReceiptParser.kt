package com.wonderfood.app.ai

import com.wonderfood.app.data.FoodCandidate
import com.wonderfood.app.data.FoodIntakeEnricher
import com.wonderfood.app.data.ReceiptDraft
import com.wonderfood.app.data.ReceiptItemDisposition
import com.wonderfood.app.data.ReceiptItemDraft
import com.wonderfood.app.data.StorageZone
import com.wonderfood.app.data.TrustedFoodLookup
import com.wonderfood.app.integration.capture.ProductionReceiptServingPicker
import com.wonderfood.app.integration.capture.ReceiptServingPicker
import com.wonderfood.app.integration.capture.BarcodeLookupProvider
import com.wonderfood.app.integration.capture.BundledBarcodeLookupProvider
import com.wonderfood.app.data.categorizeFood
import com.wonderfood.app.data.classifyStorageZone
import com.wonderfood.app.data.foodEmojiForName
import java.util.Locale

object DeterministicReceiptParser {
    fun tryParse(
        raw: String,
        promptContext: String? = null,
        barcodeLookupProvider: BarcodeLookupProvider = BundledBarcodeLookupProvider(),
        servingPicker: ReceiptServingPicker = ProductionReceiptServingPicker(),
    ): ReceiptParseResult? {
        if (!looksLikeReceiptText(raw, promptContext)) return null
        val items = parseLines(raw, barcodeLookupProvider, servingPicker)
        if (items.isEmpty()) return null
        return ReceiptParseResult(
            draft = ReceiptDraft(
                items = items,
                currencyCode = raw.receiptCurrencyCode(),
                subtotalCents = raw.receiptControlAmount("subtotal"),
                taxCents = raw.receiptControlAmount("tax"),
                totalCents = raw.receiptControlAmount("total", exclude = "subtotal"),
                rawText = raw,
                sourceLabel = "receipt_text",
            ),
            cleanedLines = items.map { item ->
                listOf(item.food.quantity, item.food.name).filter { it.isNotBlank() }.joinToString(" ")
            },
        )
    }

    fun looksLikeReceiptText(raw: String, promptContext: String? = null): Boolean {
        val lower = "$raw\n${promptContext.orEmpty()}".lowercase(Locale.US)
        if ("receipt" in lower || "subtotal" in lower || "total" in lower && priceLineCount(raw) >= 1) return true
        return priceLineCount(raw) >= 2
    }

    private fun parseLines(
        raw: String,
        barcodeLookupProvider: BarcodeLookupProvider,
        servingPicker: ReceiptServingPicker,
    ): List<ReceiptItemDraft> =
        raw.lines()
            .asSequence()
            .map { original -> original to cleanReceiptLine(original) }
            .filter { (_, line) -> line.isNotBlank() }
            .filterNot { (_, line) -> line.isReceiptControlLine(barcodeLookupProvider) }
            .filter { (original, line) -> original.isLikelyReceiptItemLine(line, barcodeLookupProvider) }
            .mapNotNull { (original, line) ->
                val barcodeCandidate = barcodeLookupProvider.lookupBarcode(line)
                val food = if (barcodeCandidate != null) {
                    barcodeCandidate.copy(
                        evidence = original.trim(),
                        confidence = 0.95,
                        zoneSource = "barcode_provider",
                        servingText = servingPicker.pickServingText(original, barcodeCandidate),
                    )
                } else {
                    line.toReceiptCandidate(original, servingPicker)
                } ?: return@mapNotNull null
                ReceiptItemDraft(
                    food = food,
                    disposition = if (FoodIntakeEnricher.isNonFood(food.name, food.category)) {
                        ReceiptItemDisposition.HOUSEHOLD
                    } else {
                        ReceiptItemDisposition.INVENTORY
                    },
                    receiptLine = original.trim(),
                    linePriceCents = original.receiptPriceCents(),
                )
            }
            .distinctBy { it.food.name.lowercase(Locale.US) }
            .take(MAX_RECEIPT_ITEMS)
            .toList()

    private fun FoodCandidate.withServingsFrom(
        rawLine: String,
        servingPicker: ReceiptServingPicker,
    ): FoodCandidate =
        copy(
            servingText = servingPicker.pickServingText(rawLine, this),
        )

    private fun cleanReceiptLine(line: String): String =
        line
            .replace(Regex("""(?i)\b(?:SALE|REG|CARD|DEBIT|CREDIT|VISA|MASTERCARD|AMEX)\b.*$"""), " ")
            .replace(Regex("""(?i)\b(?:USD|\$)\s*\d+(?:\.\d{2})?\b"""), " ")
            .replace(Regex("""(?<!\w)\d+\.\d{2}(?!\w)\s*$"""), " ")
            .replace(Regex("""(?i)\b(?:EA|EACH|LB|WT)\s*@\s*\d+(?:\.\d{2})?\b"""), " ")
            .replace(Regex("""[#*]+"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim(' ', '-', '.', ':')

    private fun String.isReceiptControlLine(barcodeLookupProvider: BarcodeLookupProvider): Boolean {
        if (barcodeLookupProvider.lookupBarcode(this) != null) return false
        val lower = lowercase(Locale.US)
        if (lower.length < 3) return true
        if (lower.all { it.isDigit() || it.isWhitespace() }) return true
        return CONTROL_WORDS.any { it in lower }
    }

    private fun String.isLikelyReceiptItemLine(cleaned: String, barcodeLookupProvider: BarcodeLookupProvider): Boolean =
        PRICE_PATTERN.containsMatchIn(this) ||
            barcodeLookupProvider.lookupBarcode(cleaned) != null ||
            FOOD_WORDS.any { word -> word in cleaned.lowercase(Locale.US) }

    private fun String.toReceiptCandidate(rawLine: String, servingPicker: ReceiptServingPicker): FoodCandidate? {
        val cleaned = replace(Regex("""(?i)\b(organic|org|fresh|frozen|canned)\b""")) { match -> match.value.lowercase(Locale.US) }
            .replace(Regex("""(?i)\b(?:item|sku|upc)\s*\d+\b"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()
        if (cleaned.length < 3 || cleaned.count(Char::isLetter) < 3) return null

        val quantityMatch = Regex("""(?i)^(\d+(?:\.\d+)?)\s*(x|ct|count|lb|lbs|oz|g|kg|pack|packs|bag|bags|can|cans|box|boxes)?\s+(.+)$""")
            .find(cleaned)
        val trailingQuantityMatch = Regex("""(?i)^(.+?)\s+(\d+(?:\.\d+)?)\s*(ct|count|lb|lbs|oz|g|kg|pack|packs|bag|bags|can|cans|box|boxes)$""")
            .find(cleaned)
        val quantity = quantityMatch?.let { match ->
            listOf(match.groupValues[1], match.groupValues[2]).filter { it.isNotBlank() }.joinToString(" ")
        } ?: trailingQuantityMatch?.let { match ->
            listOf(match.groupValues[2], match.groupValues[3]).filter { it.isNotBlank() }.joinToString(" ")
        }.orEmpty()
        val name = (
            quantityMatch?.groupValues?.getOrNull(3)
                ?: trailingQuantityMatch?.groupValues?.getOrNull(1)
                ?: cleaned
            )
            .replace(Regex("""(?i)\b(?:ea|each)\b"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()
            .toDisplayName()
        if (name.length < 3) return null
        return FoodCandidate(
            name = name,
            quantity = quantity,
            zone = if ("frozen" in name.lowercase(Locale.US)) StorageZone.FREEZER else classifyStorageZone(name),
            category = categorizeFood(name),
            nutritionSource = "receipt_text_unverified",
            notes = "receipt_text",
            imageUri = foodEmojiForName(name),
            evidence = this,
            confidence = 0.65,
            zoneSource = "app_storage_inference",
            warnings = listOf("Parsed from receipt text; verify the item name and quantity."),
        ).withServingsFrom(rawLine, servingPicker)
    }

    private fun priceLineCount(raw: String): Int =
        raw.lines().count { PRICE_PATTERN.containsMatchIn(it) }

    private fun String.receiptControlAmount(keyword: String, exclude: String = ""): Long? =
        lines()
            .lastOrNull { line ->
                val lower = line.lowercase(Locale.US)
                keyword in lower && (exclude.isBlank() || exclude !in lower)
            }
            ?.receiptPriceCents()

    private fun String.receiptCurrencyCode(): String = when {
        '€' in this -> "EUR"
        '£' in this -> "GBP"
        else -> "USD"
    }

    private fun String.receiptPriceCents(): Long? {
        val value = PRICE_PATTERN.findAll(this).lastOrNull()?.value
            ?.replace("$", "")
            ?.trim()
            ?.toBigDecimalOrNull()
            ?: return null
        return runCatching { value.movePointRight(2).longValueExact() }.getOrNull()
    }

    private fun String.toDisplayName(): String =
        lowercase(Locale.US)
            .split(Regex("""\s+"""))
            .joinToString(" ") { word ->
                word.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.US) else it.toString() }
            }

    private val PRICE_PATTERN = Regex("""(?<!\w)(?:\$?\d+\.\d{2})(?!\w)""")
    private val CONTROL_WORDS = setOf(
        "subtotal",
        "total",
        "tax",
        "balance",
        "change",
        "cash",
        "visa",
        "mastercard",
        "amex",
        "debit",
        "credit",
        "approval",
        "auth",
        "store",
        "thank you",
        "savings",
        "coupon",
        "receipt",
    )
    private val FOOD_WORDS = setOf(
        "egg",
        "milk",
        "yogurt",
        "rice",
        "lentil",
        "bean",
        "oat",
        "berry",
        "berries",
        "frozen",
        "bread",
        "tomato",
        "onion",
        "spinach",
        "lettuce",
        "chicken",
        "tofu",
        "paneer",
        "pasta",
        "pizza",
        "apple",
        "banana",
    )
    private const val MAX_RECEIPT_ITEMS = 40
}

data class ReceiptParseResult(
    val draft: ReceiptDraft,
    val cleanedLines: List<String>,
)
