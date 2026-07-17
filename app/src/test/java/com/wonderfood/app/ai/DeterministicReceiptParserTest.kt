package com.wonderfood.app.ai

import com.wonderfood.app.data.StorageZone
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

        assertEquals(listOf("Eggs Large", "Frozen Berries", "Rice Basmati"), result.draft.items.map { it.name })
        assertEquals(StorageZone.FREEZER, result.draft.items.single { it.name == "Frozen Berries" }.zone)
        assertTrue(result.draft.items.none { it.name.contains("Subtotal", ignoreCase = true) })
    }

    @Test
    fun barcodeLineUsesTrustedProviderSnapshot() {
        val result = requireNotNull(DeterministicReceiptParser.tryParse("Receipt\n012345678905 4.99"))

        val item = result.draft.items.single()
        assertEquals("Generic Rolled Oats", item.name)
        assertEquals("bundled_barcode_provider", item.nutritionSource)
        assertNotNull(item.calories)
    }
}
