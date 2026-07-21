package com.wonderfood.app.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PostgresSchemaContractTest {
    @Test
    fun migrationsCoverCanonicalPostgresSurfaces() {
        val sql = PostgresSchemaContract.migrationSql.joinToString("\n")

        PostgresSchemaContract.requiredTables.forEach { table ->
            assertTrue("Missing table $table", sql.contains("create table if not exists $table"))
            assertTrue("Missing RLS enable for $table", sql.contains("alter table $table enable row level security"))
        }
        listOf(
            "schema_version",
            "schema_fingerprint",
            "remote_cursor",
            "idempotency_key",
            "remote_id",
            "reason",
            "base_json",
            "local_json",
            "remote_json",
        ).forEach { column ->
            assertTrue("Missing column $column", sql.contains(column))
        }
    }

    @Test
    fun membershipPoliciesCoverEveryExposedTable() {
        val policies = PostgresSchemaContract.membershipPolicySql

        assertEquals(PostgresSchemaContract.requiredTables.size, policies.size)
        PostgresSchemaContract.requiredTables.forEach { table ->
            val policy = policies.single { it.contains("policy ${table}_household_member") }
            assertTrue(policy.contains("wonderfood_household_members"))
            assertTrue(policy.contains("current_setting('request.jwt.claim.sub', true)"))
            assertTrue(policy.contains("with check"))
        }
    }

    @Test
    fun householdRoutesRequireHouseholdScope() {
        val householdRoutes = PostgresSchemaContract.requiredRoutes.filter { it.path.contains("{householdId}") }
        assertFalse(householdRoutes.isEmpty())
        assertTrue(householdRoutes.all { it.householdScoped })

        val unscopedRoutes = PostgresSchemaContract.requiredRoutes.filterNot { it.path.contains("{householdId}") }
        assertEquals(setOf("/health", "/schema"), unscopedRoutes.map { it.path }.toSet())
        assertTrue(unscopedRoutes.none { it.householdScoped })
    }

    @Test
    fun householdMembershipCheckFailsClosedForCrossHouseholdAccess() {
        val route = PostgresSchemaContract.requiredRoutes.single {
            it.path == "/households/{householdId}/snapshot/current" && it.method == "GET"
        }

        PostgresSchemaContract.requireMatchingHousehold(route, routeHouseholdId = "home", sessionHouseholdId = "home")

        val error = runCatching {
            PostgresSchemaContract.requireMatchingHousehold(route, routeHouseholdId = "home", sessionHouseholdId = "other")
        }.exceptionOrNull()
        assertTrue(error?.message.orEmpty().contains("another household"))
    }
}
