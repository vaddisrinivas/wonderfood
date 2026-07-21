package com.wonderfood.core.data.room

import org.junit.Assert.assertEquals
import org.junit.Test

class WonderFoodMigrationsTest {
    @Test
    fun registersContiguousHouseholdSchemaMigrationsThroughVersionTen() {
        val versions = WonderFoodMigrations.ALL.map { it.startVersion to it.endVersion }

        assertEquals(
            listOf(
                1 to 2,
                2 to 3,
                3 to 4,
                4 to 5,
                5 to 6,
                6 to 7,
                7 to 8,
                8 to 9,
                9 to 10,
            ),
            versions,
        )
    }
}
