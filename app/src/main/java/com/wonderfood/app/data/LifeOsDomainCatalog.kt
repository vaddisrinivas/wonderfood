package com.wonderfood.app.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class LifeOsDomain(
    val id: String,
    val label: String,
    val emoji: String,
    val status: String,
    val summary: String,
    val bottomTabs: List<String>,
    val skills: List<String>,
    val schemaSurfaces: List<String>,
    val dataPlanes: List<String>,
) {
    val statusLabel: String
        get() = status.replace('-', ' ').replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
}

data class LifeOsDomainCatalog(
    val schemaVersion: String,
    val activeDomainId: String,
    val domains: List<LifeOsDomain>,
) {
    val activeDomain: LifeOsDomain?
        get() = domains.firstOrNull { it.id == activeDomainId }

    companion object {
        fun bundled(context: Context): LifeOsDomainCatalog =
            runCatching {
                context.assets.open("lifeos/domain-catalog.v1.json").bufferedReader().use { reader ->
                    fromJson(JSONObject(reader.readText()))
                }
            }.getOrElse { fallback() }

        private fun fromJson(root: JSONObject): LifeOsDomainCatalog =
            LifeOsDomainCatalog(
                schemaVersion = root.optString("schema_version", "lifeos.domain-catalog.v1"),
                activeDomainId = root.optString("active_domain_id", "food"),
                domains = root.optJSONArray("domains").toDomains(),
            )

        private fun JSONArray?.toDomains(): List<LifeOsDomain> {
            if (this == null) return fallback().domains
            return buildList {
                for (index in 0 until length()) {
                    val item = optJSONObject(index) ?: continue
                    val id = item.optString("id").trim()
                    val label = item.optString("label").trim()
                    if (id.isBlank() || label.isBlank()) continue
                    add(
                        LifeOsDomain(
                            id = id,
                            label = label,
                            emoji = item.optString("emoji", "🧩"),
                            status = item.optString("status", "available"),
                            summary = item.optString("summary"),
                            bottomTabs = item.optJSONArray("bottom_tabs").strings(),
                            skills = item.optJSONArray("skills").strings(),
                            schemaSurfaces = item.optJSONArray("schema_surfaces").strings(),
                            dataPlanes = item.optJSONArray("data_planes").strings(),
                        ),
                    )
                }
            }.ifEmpty { fallback().domains }
        }

        private fun JSONArray?.strings(): List<String> {
            if (this == null) return emptyList()
            return List(length()) { index -> optString(index).trim() }.filter { it.isNotBlank() }
        }

        private fun fallback(): LifeOsDomainCatalog =
            LifeOsDomainCatalog(
                schemaVersion = "lifeos.domain-catalog.v1",
                activeDomainId = "food",
                domains = listOf(
                    LifeOsDomain(
                        id = "food",
                        label = "Food",
                        emoji = "🍽️",
                        status = "active",
                        summary = "Kitchen, shopping, recipes, meal planning, receipts, nutrition, and Health Connect context.",
                        bottomTabs = listOf("Now", "Food", "Week", "Saved", "Cart"),
                        skills = listOf("inventory", "shopping", "recipes", "meal_logging", "planning", "receipt_parsing"),
                        schemaSurfaces = listOf("Home", "Kitchen", "Shopping", "Meals", "Recipes", "Spending"),
                        dataPlanes = listOf("Notion", "Google Sheets", "SQLite", "Postgres", "MCP"),
                    ),
                ),
            )
    }
}
