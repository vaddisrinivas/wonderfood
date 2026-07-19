package com.wonderfood.core.data.backend

import org.junit.Assert.assertEquals
import org.junit.Test

class PostgresConnectionParserTest {
    @Test
    fun infersSupabaseHttpsEndpoint() {
        val reference = PostgresConnectionParser.parse(
            endpoint = "https://abcxyz.supabase.co",
            householdId = "home",
        )

        assertEquals(PostgresConnectionMode.SUPABASE, reference.mode)
        assertEquals("https://abcxyz.supabase.co", reference.endpoint)
        assertEquals("home", reference.householdId)
    }

    @Test
    fun infersDirectDsn() {
        val reference = PostgresConnectionParser.parse(
            endpoint = "postgresql://user:pass@example.com:5432/wonderfood?sslmode=require",
            householdId = "home",
        )

        assertEquals(PostgresConnectionMode.DIRECT_DSN, reference.mode)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsHostedHttpEndpoint() {
        PostgresConnectionParser.parse(
            endpoint = "http://localhost:3000",
            householdId = "home",
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsDirectDsnWithDisabledTls() {
        PostgresConnectionParser.parse(
            endpoint = "postgres://user:pass@example.com/db?sslmode=disable",
            householdId = "home",
        )
    }
}
