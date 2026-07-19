package com.wonderfood.app.sync

import com.wonderfood.core.data.backend.PostgresConnectionMode
import com.wonderfood.core.model.WonderFoodSnapshot
import com.wonderfood.core.model.WonderFoodSnapshotCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PostgresGatewayTest {
    @Test
    fun supabaseApiRootUsesRestV1() {
        val gateway = PostgresGateway()

        assertEquals(
            "https://abc.supabase.co/rest/v1/",
            gateway.apiRootFor(PostgresConnectionMode.SUPABASE, "https://abc.supabase.co/"),
        )
    }

    @Test
    fun postgrestApiRootPreservesProvidedRoot() {
        val gateway = PostgresGateway()

        assertEquals(
            "https://api.example.com/rest/v1/",
            gateway.apiRootFor(PostgresConnectionMode.POSTGREST, "https://api.example.com/rest/v1"),
        )
    }

    @Test
    fun wonderFoodServerUsesHealthEndpoint() {
        val gateway = PostgresGateway()

        assertEquals(
            "https://api.example.com/health",
            gateway.apiRootFor(PostgresConnectionMode.WONDERFOOD_SERVER, "https://api.example.com"),
        )
    }

    @Test
    fun snapshotExportUrlsTargetProviderSnapshotEndpoint() {
        val gateway = PostgresGateway()

        assertEquals(
            "https://abc.supabase.co/rest/v1/wonderfood_snapshots",
            gateway.snapshotExportUrl(PostgresConnectionMode.SUPABASE, "https://abc.supabase.co"),
        )
        assertEquals(
            "https://api.example.com/rest/v1/wonderfood_snapshots",
            gateway.snapshotExportUrl(PostgresConnectionMode.POSTGREST, "https://api.example.com/rest/v1"),
        )
        assertEquals(
            "https://api.example.com/snapshots",
            gateway.snapshotExportUrl(PostgresConnectionMode.WONDERFOOD_SERVER, "https://api.example.com"),
        )
    }

    @Test
    fun snapshotReadUrlsTargetCurrentHouseholdSnapshot() {
        val gateway = PostgresGateway()

        assertEquals(
            "https://abc.supabase.co/rest/v1/wonderfood_snapshots?household_id=eq.home%20kitchen&snapshot_id=eq.current&select=snapshot_json,updated_at&order=updated_at.desc&limit=1",
            gateway.snapshotReadUrl(PostgresConnectionMode.SUPABASE, "https://abc.supabase.co", "home kitchen"),
        )
        assertEquals(
            "https://api.example.com/rest/v1/wonderfood_snapshots?household_id=eq.home&snapshot_id=eq.current&select=snapshot_json,updated_at&order=updated_at.desc&limit=1",
            gateway.snapshotReadUrl(PostgresConnectionMode.POSTGREST, "https://api.example.com/rest/v1", "home"),
        )
        assertEquals(
            "https://api.example.com/snapshots/home/current",
            gateway.snapshotReadUrl(PostgresConnectionMode.WONDERFOOD_SERVER, "https://api.example.com", "home"),
        )
    }

    @Test
    fun snapshotExportBodyContainsHouseholdCountsAndCanonicalJson() {
        val body = PostgresGateway().snapshotExportBody(
            householdId = "home",
            snapshot = emptySnapshot(),
            updatedAt = "2026-07-19T12:00:00Z",
        )

        assertEquals("home", body.getString("household_id"))
        assertEquals("current", body.getString("snapshot_id"))
        assertEquals(WonderFoodSnapshotCodec.CURRENT_SCHEMA_VERSION, body.getInt("schema_version"))
        assertEquals(0, body.getInt("food_count"))
        assertTrue(body.getString("snapshot_json").contains("\"schemaVersion\""))
    }

    @Test
    fun parseSnapshotResponseDecodesPostgrestRows() {
        val snapshot = emptySnapshot()
        val encoded = WonderFoodSnapshotCodec.encode(snapshot)
        val response = """
            [
              {
                "updated_at": "2026-07-19T12:00:00Z",
                "snapshot_json": ${org.json.JSONObject.quote(encoded)}
              }
            ]
        """.trimIndent()

        val result = PostgresGateway().parseSnapshotResponse(
            mode = PostgresConnectionMode.POSTGREST,
            endpoint = "https://api.example.com/rest/v1",
            householdId = "home",
            response = response,
        )

        assertEquals("2026-07-19T12:00:00Z", result.updatedAt)
        assertEquals(snapshot, result.snapshot)
    }

    @Test
    fun parseSnapshotResponseHandlesEmptyRows() {
        val result = PostgresGateway().parseSnapshotResponse(
            mode = PostgresConnectionMode.SUPABASE,
            endpoint = "https://abc.supabase.co",
            householdId = "home",
            response = "[]",
        )

        assertEquals(null, result.updatedAt)
        assertEquals(null, result.snapshot)
    }

    private fun emptySnapshot() = WonderFoodSnapshot(
        schemaVersion = WonderFoodSnapshotCodec.CURRENT_SCHEMA_VERSION,
        pages = emptyList(),
        foods = emptyList(),
        foodAliases = emptyList(),
        stockLots = emptyList(),
        nutritionSnapshots = emptyList(),
        recipes = emptyList(),
        mealPlans = emptyList(),
        mealLogs = emptyList(),
        shoppingItems = emptyList(),
        receipts = emptyList(),
        foodEvents = emptyList(),
        relations = emptyList(),
        attachments = emptyList(),
    )
}
