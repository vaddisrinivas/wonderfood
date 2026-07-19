package com.wonderfood.app.sync

import com.wonderfood.core.data.backend.PostgresConnectionMode
import com.wonderfood.core.model.WonderFoodSnapshot
import com.wonderfood.core.model.WonderFoodSnapshotCodec
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import org.json.JSONArray
import org.json.JSONObject

class PostgresGateway {
    fun validateHostedApi(
        mode: PostgresConnectionMode,
        endpoint: String,
        token: String,
    ): PostgresGatewayConnection {
        require(mode != PostgresConnectionMode.DIRECT_DSN) { "Direct DSN mode is validated by local parsing only." }
        require(endpoint.isNotBlank()) { "Postgres endpoint must not be blank." }
        require(token.isNotBlank()) { "Postgres API token must not be blank." }
        val url = apiRootFor(mode, endpoint)
        val response = request(
            method = "GET",
            url = url,
            token = token,
            includeSupabaseApiKey = mode == PostgresConnectionMode.SUPABASE,
        )
        return PostgresGatewayConnection(
            mode = mode,
            endpoint = endpoint,
            reachable = true,
            summary = response.take(2_000),
        )
    }

    fun readRemoteSnapshot(
        mode: PostgresConnectionMode,
        endpoint: String,
        token: String,
        householdId: String,
    ): PostgresRemoteSnapshotResult {
        require(mode != PostgresConnectionMode.DIRECT_DSN) { "Direct DSN mode needs a server-side sync adapter before remote snapshot read." }
        require(endpoint.isNotBlank()) { "Postgres endpoint must not be blank." }
        require(token.isNotBlank()) { "Postgres API token must not be blank." }
        require(householdId.isNotBlank()) { "Household ID must not be blank." }
        val response = request(
            method = "GET",
            url = snapshotReadUrl(mode, endpoint, householdId),
            token = token,
            includeSupabaseApiKey = mode == PostgresConnectionMode.SUPABASE,
        )
        return parseSnapshotResponse(mode, endpoint, householdId, response)
    }

    fun exportSnapshot(
        mode: PostgresConnectionMode,
        endpoint: String,
        token: String,
        householdId: String,
        snapshot: WonderFoodSnapshot,
        updatedAt: String,
    ): PostgresSnapshotExportResult {
        require(mode != PostgresConnectionMode.DIRECT_DSN) { "Direct DSN mode needs a server-side sync adapter before snapshot export." }
        require(endpoint.isNotBlank()) { "Postgres endpoint must not be blank." }
        require(token.isNotBlank()) { "Postgres API token must not be blank." }
        require(householdId.isNotBlank()) { "Household ID must not be blank." }
        require(updatedAt.isNotBlank()) { "Snapshot updated timestamp must not be blank." }
        val body = snapshotExportBody(householdId, snapshot, updatedAt)
        val url = snapshotExportUrl(mode, endpoint)
        val response = request(
            method = if (mode == PostgresConnectionMode.WONDERFOOD_SERVER) "POST" else "POST",
            url = url,
            token = token,
            includeSupabaseApiKey = mode == PostgresConnectionMode.SUPABASE,
            body = body,
            preferResolutionMerge = mode == PostgresConnectionMode.SUPABASE || mode == PostgresConnectionMode.POSTGREST,
        )
        return PostgresSnapshotExportResult(
            mode = mode,
            endpoint = endpoint,
            householdId = householdId,
            updatedAt = updatedAt,
            byteCount = body.toString().toByteArray(StandardCharsets.UTF_8).size,
            summary = response.take(2_000),
        )
    }

    internal fun apiRootFor(mode: PostgresConnectionMode, endpoint: String): String {
        val clean = endpoint.trimEnd('/')
        return when (mode) {
            PostgresConnectionMode.SUPABASE -> "$clean/rest/v1/"
            PostgresConnectionMode.POSTGREST -> if (clean.endsWith("/rest/v1")) "$clean/" else "$clean/"
            PostgresConnectionMode.WONDERFOOD_SERVER -> "$clean/health"
            PostgresConnectionMode.DIRECT_DSN -> error("Direct DSN has no hosted API root.")
        }
    }

    internal fun snapshotExportUrl(mode: PostgresConnectionMode, endpoint: String): String {
        val clean = endpoint.trimEnd('/')
        return when (mode) {
            PostgresConnectionMode.SUPABASE -> "$clean/rest/v1/wonderfood_snapshots"
            PostgresConnectionMode.POSTGREST -> "$clean/wonderfood_snapshots"
            PostgresConnectionMode.WONDERFOOD_SERVER -> "$clean/snapshots"
            PostgresConnectionMode.DIRECT_DSN -> error("Direct DSN has no hosted snapshot URL.")
        }
    }

    internal fun snapshotReadUrl(mode: PostgresConnectionMode, endpoint: String, householdId: String): String {
        val clean = endpoint.trimEnd('/')
        val household = url(householdId)
        return when (mode) {
            PostgresConnectionMode.SUPABASE ->
                "$clean/rest/v1/wonderfood_snapshots?household_id=eq.$household&snapshot_id=eq.current&select=snapshot_json,updated_at&order=updated_at.desc&limit=1"
            PostgresConnectionMode.POSTGREST ->
                "$clean/wonderfood_snapshots?household_id=eq.$household&snapshot_id=eq.current&select=snapshot_json,updated_at&order=updated_at.desc&limit=1"
            PostgresConnectionMode.WONDERFOOD_SERVER -> "$clean/snapshots/$household/current"
            PostgresConnectionMode.DIRECT_DSN -> error("Direct DSN has no hosted snapshot URL.")
        }
    }

    internal fun snapshotExportBody(
        householdId: String,
        snapshot: WonderFoodSnapshot,
        updatedAt: String,
    ): JSONObject =
        JSONObject()
            .put("household_id", householdId)
            .put("snapshot_id", "current")
            .put("schema_version", snapshot.schemaVersion)
            .put("updated_at", updatedAt)
            .put("food_count", snapshot.foods.size)
            .put("stock_lot_count", snapshot.stockLots.size)
            .put("shopping_item_count", snapshot.shoppingItems.size)
            .put("recipe_count", snapshot.recipes.size)
            .put("meal_plan_count", snapshot.mealPlans.size)
            .put("meal_log_count", snapshot.mealLogs.size)
            .put("event_count", snapshot.foodEvents.size)
            .put("snapshot_json", WonderFoodSnapshotCodec.encode(snapshot))

    internal fun parseSnapshotResponse(
        mode: PostgresConnectionMode,
        endpoint: String,
        householdId: String,
        response: String,
    ): PostgresRemoteSnapshotResult {
        if (response.isBlank()) {
            return PostgresRemoteSnapshotResult(mode, endpoint, householdId, null, null)
        }
        val record = when (mode) {
            PostgresConnectionMode.SUPABASE,
            PostgresConnectionMode.POSTGREST -> JSONArray(response).optJSONObject(0)
            PostgresConnectionMode.WONDERFOOD_SERVER -> JSONObject(response)
            PostgresConnectionMode.DIRECT_DSN -> error("Direct DSN has no hosted snapshot response.")
        } ?: return PostgresRemoteSnapshotResult(mode, endpoint, householdId, null, null)
        val snapshotJson = record.optString("snapshot_json").ifBlank {
            record.optJSONObject("snapshot")?.toString().orEmpty()
        }
        if (snapshotJson.isBlank()) {
            return PostgresRemoteSnapshotResult(mode, endpoint, householdId, record.optString("updated_at").ifBlank { null }, null)
        }
        return PostgresRemoteSnapshotResult(
            mode = mode,
            endpoint = endpoint,
            householdId = householdId,
            updatedAt = record.optString("updated_at").ifBlank { null },
            snapshot = WonderFoodSnapshotCodec.decode(snapshotJson),
        )
    }

    private fun request(
        method: String,
        url: String,
        token: String,
        includeSupabaseApiKey: Boolean,
        body: JSONObject? = null,
        preferResolutionMerge: Boolean = false,
    ): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = TIMEOUT_MILLIS
            readTimeout = TIMEOUT_MILLIS
            setRequestProperty("Authorization", "Bearer $token")
            if (includeSupabaseApiKey) setRequestProperty("apikey", token)
            setRequestProperty("Accept", "application/openapi+json, application/json")
            if (preferResolutionMerge) setRequestProperty("Prefer", "resolution=merge-duplicates,return=representation")
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
            }
        }
        if (body != null) {
            connection.outputStream.use { output ->
                output.write(body.toString().toByteArray(StandardCharsets.UTF_8))
            }
        }
        val code = connection.responseCode
        val stream = if (code in 200..299) connection.inputStream else connection.errorStream
        val response = stream?.use { it.readBytes() }?.toString(StandardCharsets.UTF_8).orEmpty()
        connection.disconnect()
        if (code !in 200..299) {
            val message = response.ifBlank { "HTTP $code" }
            error("Postgres API check failed: $message")
        }
        return response
    }

    private fun url(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8.name()).replace("+", "%20")

    private companion object {
        const val TIMEOUT_MILLIS = 30_000
    }
}

data class PostgresGatewayConnection(
    val mode: PostgresConnectionMode,
    val endpoint: String,
    val reachable: Boolean,
    val summary: String,
)

data class PostgresSnapshotExportResult(
    val mode: PostgresConnectionMode,
    val endpoint: String,
    val householdId: String,
    val updatedAt: String,
    val byteCount: Int,
    val summary: String,
)

data class PostgresRemoteSnapshotResult(
    val mode: PostgresConnectionMode,
    val endpoint: String,
    val householdId: String,
    val updatedAt: String?,
    val snapshot: WonderFoodSnapshot?,
)
