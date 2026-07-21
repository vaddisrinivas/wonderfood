package com.wonderfood.app.sync

import org.junit.Assert.assertTrue
import org.junit.Test

class WonderFoodWorkspaceSeedFixtureTest {
    @Test
    fun canonicalSnapshotExportsVisibleV4SheetsRows() {
        val rows = GoogleSheetsGateway().workspaceRows(
            snapshot = CanonicalWorkspaceTestFixture.snapshot(),
        )

        assertTrue(rows.getValue("Kitchen").flatten().contains("Basmati Rice"))
        assertTrue(rows.getValue("Recipes").flatten().contains("Spinach Rice Bowl"))
        assertTrue(rows.getValue("Ingredients").flatten().contains("Basmati Rice"))
        assertTrue(rows.containsKey("Purchase Lines"))
        assertTrue(rows.containsKey("Lists & Help"))
        assertTrue(rows.getValue("Home").flatten().contains("WonderFood Home"))
        assertTrue(rows.keys.none { it == "Purchases" || it == "Nutrition Facts" })
    }
}
