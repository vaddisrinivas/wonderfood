package com.wonderfood.core.data.backend

public object PostgresConnectionParser {
    public fun parse(
        endpoint: String,
        householdId: String,
        preferredMode: PostgresConnectionMode? = null,
    ): PostgresConnectionReference {
        val cleanEndpoint = endpoint.trim()
        val cleanHouseholdId = householdId.trim()
        require(cleanEndpoint.isNotBlank()) { "Postgres endpoint must not be blank." }
        require(cleanHouseholdId.isNotBlank()) { "Household ID must not be blank." }

        val mode = preferredMode ?: inferMode(cleanEndpoint)
        validateEndpoint(cleanEndpoint, mode)
        return PostgresConnectionReference(
            mode = mode,
            endpoint = cleanEndpoint.trimEnd('/'),
            householdId = cleanHouseholdId,
        )
    }

    private fun inferMode(endpoint: String): PostgresConnectionMode {
        val lower = endpoint.lowercase()
        return when {
            "/rest/v1" in lower -> PostgresConnectionMode.POSTGREST
            else -> PostgresConnectionMode.WONDERFOOD_SERVER
        }
    }

    private fun validateEndpoint(endpoint: String, mode: PostgresConnectionMode) {
        val lower = endpoint.lowercase()
        when (mode) {
            PostgresConnectionMode.POSTGREST,
            PostgresConnectionMode.WONDERFOOD_SERVER -> {
                require(lower.startsWith("https://")) { "Hosted Postgres backends must use HTTPS." }
            }
        }
    }
}

public data class PostgresConnectionReference(
    val mode: PostgresConnectionMode,
    val endpoint: String,
    val householdId: String,
) {
    init {
        require(endpoint.isNotBlank()) { "Postgres endpoint must not be blank." }
        require(householdId.isNotBlank()) { "Household ID must not be blank." }
    }
}
