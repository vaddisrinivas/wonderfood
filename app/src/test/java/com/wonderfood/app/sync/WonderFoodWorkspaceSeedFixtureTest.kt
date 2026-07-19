package com.wonderfood.app.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WonderFoodWorkspaceSeedFixtureTest {
    @Test
    fun seedSnapshotProjectsIntoEverydayAndManagedWorkspaceTables() {
        val rows = WonderFoodWorkspaceSchema.rows(
            snapshot = WonderFoodWorkspaceSeedFixture.snapshot(),
            updatedAt = "2026-07-19T12:00:00Z",
        )

        assertEquals(3, rows.getValue(WonderFoodWorkspaceSchema.KITCHEN).size)
        assertEquals(2, rows.getValue(WonderFoodWorkspaceSchema.RECIPES).size)
        assertEquals(3, rows.getValue(WonderFoodWorkspaceSchema.MEALS).size)
        assertEquals(1, rows.getValue(WonderFoodWorkspaceSchema.PLANS).size)
        assertEquals(3, rows.getValue(WonderFoodWorkspaceSchema.SHOPPING).size)
        assertEquals(1, rows.getValue(WonderFoodWorkspaceSchema.PURCHASES).size)
        assertTrue(rows.getValue(WonderFoodWorkspaceSchema.NUTRITION_FACTS).size >= 3)
        assertTrue(rows.getValue(WonderFoodWorkspaceSchema.RECIPE_INGREDIENTS).size >= 6)
        assertTrue(rows.getValue(WonderFoodWorkspaceSchema.ACTIVITY).isNotEmpty())
        assertTrue(rows.getValue(WonderFoodWorkspaceSchema.HOME).any { it.values["Metric"] == "Spending total" })
    }

    @Test
    fun seedSnapshotExportsVisibleSheetsRows() {
        val rows = GoogleSheetsGateway().workspaceRows(
            snapshot = WonderFoodWorkspaceSeedFixture.snapshot(),
            updatedAt = "2026-07-19T12:00:00Z",
        )

        assertTrue(rows.getValue("Kitchen").flatten().contains("Basmati Rice"))
        assertTrue(rows.getValue("Recipes").flatten().contains("Spinach Rice Bowl"))
        assertTrue(rows.getValue("Shopping").flatten().contains("Eggs"))
        assertTrue(rows.getValue("Purchases").flatten().contains("Receipt 70"))
        assertTrue(rows.getValue("Nutrition Facts").flatten().contains("seed label"))
    }
}
