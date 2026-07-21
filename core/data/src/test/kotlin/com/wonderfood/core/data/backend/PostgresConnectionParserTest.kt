package com.wonderfood.core.data.backend

import org.junit.Assert.assertEquals
import org.junit.Test

class PostgresConnectionParserTest {
    @Test
    fun infersWonderFoodServerHttpsEndpoint() {
        val reference = PostgresConnectionParser.parse(
            endpoint = "https://api.example.com",
            householdId = "home",
        )

        assertEquals(PostgresConnectionMode.WONDERFOOD_SERVER, reference.mode)
        assertEquals("https://api.example.com", reference.endpoint)
        assertEquals("home", reference.householdId)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsHostedHttpEndpoint() {
        PostgresConnectionParser.parse(
            endpoint = "http://localhost:3000",
            householdId = "home",
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsRawPostgresDsn() {
        PostgresConnectionParser.parse(
            endpoint = "postgres.example.com:5432/wonderfood",
            householdId = "home",
        )
    }
}
