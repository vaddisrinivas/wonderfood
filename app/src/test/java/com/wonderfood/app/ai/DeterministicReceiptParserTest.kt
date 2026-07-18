package com.wonderfood.app.ai

import com.wonderfood.app.data.StorageZone
import com.wonderfood.app.data.ReceiptItemDisposition
import com.wonderfood.app.data.FoodCandidate
import com.wonderfood.app.integration.capture.BarcodeLookupProvider
import com.wonderfood.app.integration.capture.ProductionReceiptServingPicker
import com.wonderfood.app.integration.capture.ReceiptServingPicker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeterministicReceiptParserTest {
    @Test
    fun parsesReceiptLinesAndDropsTotals() {
        val raw = """
            GENERIC MARKET
            EGGS LARGE 12CT      3.99
            FROZEN BERRIES 2 BAG 9.49
            RICE BASMATI 5LB     12.99
            SUBTOTAL             26.47
            TAX                  0.00
            VISA APPROVED
        """.trimIndent()

        val result = requireNotNull(DeterministicReceiptParser.tryParse(raw))

        assertEquals(listOf("Eggs Large", "Frozen Berries", "Rice Basmati"), result.draft.items.map { it.food.name })
        assertEquals(StorageZone.FREEZER, result.draft.items.single { it.food.name == "Frozen Berries" }.food.zone)
        assertEquals(399L, result.draft.items.single { it.food.name == "Eggs Large" }.linePriceCents)
        assertEquals(2647L, result.draft.subtotalCents)
        assertEquals(0L, result.draft.taxCents)
        assertTrue(result.draft.items.none { it.food.name.contains("Subtotal", ignoreCase = true) })
    }

    @Test
    fun barcodeLineUsesTrustedProviderSnapshot() {
        val result = requireNotNull(DeterministicReceiptParser.tryParse("Receipt\n012345678905 4.99"))

        val item = result.draft.items.single()
        assertEquals("Generic Rolled Oats", item.food.name)
        assertEquals("bundled_barcode_provider", item.food.nutritionSource)
        assertNotNull(item.food.calories)
    }

    @Test
    fun keepsNonFoodVisibleButOutOfInventory() {
        val result = requireNotNull(
            DeterministicReceiptParser.tryParse(
                "Receipt\nOVEN CLEANER FOAM 4.99\nMINI CUCUMBERS 3.49\nTOTAL 8.48",
            ),
        )

        assertEquals(
            ReceiptItemDisposition.HOUSEHOLD,
            result.draft.items.single { it.food.name.contains("Oven Cleaner") }.disposition,
        )
        assertEquals(
            ReceiptItemDisposition.INVENTORY,
            result.draft.items.single { it.food.name.contains("Cucumbers") }.disposition,
        )
        assertEquals(848L, result.draft.totalCents)
    }

    @Test
    fun servingPickerDefaultsServeFromQuantityWhenUnknown() {
        val result = requireNotNull(
            DeterministicReceiptParser.tryParse(
                "Receipt\n3 LBS APPLES 4.99\nTOTAL 4.99",
                servingPicker = object : ReceiptServingPicker {
                    override val providerName: String = "test-serving-picker"
                    override fun pickServingText(rawLine: String, candidate: FoodCandidate): String =
                        ProductionReceiptServingPicker().pickServingText(rawLine, candidate)
                },
            ),
        )

        assertEquals("3 lbs", result.draft.items.single { it.food.name.contains("Apples") }.food.servingText)
    }

    @Test
    fun barcodeProviderContractCanBeInjected() {
        val result = requireNotNull(
            DeterministicReceiptParser.tryParse(
                "Receipt\n9876543210987 5.99\nTOTAL 5.99",
                barcodeLookupProvider = object : BarcodeLookupProvider {
                    override val sourceLabel: String = "contract-test-provider"
                    override fun lookupBarcode(value: String): FoodCandidate? =
                        if (value == "9876543210987") {
                            FoodCandidate(
                                name = "Oats",
                                quantity = "2",
                                nutritionSource = "contract_test",
                                servingText = "2 cups",
                            )
                        } else {
                            null
                        }
                },
                servingPicker = ProductionReceiptServingPicker(),
            ),
        )

        val item = result.draft.items.single()
        assertEquals("contract_test", item.food.nutritionSource)
        assertEquals("2 cups", item.food.servingText)
    }
}
