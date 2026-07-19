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
            endpoint = if (mode == PostgresConnectionMode.DIRECT_DSN) DIRECT_DSN_ENDPOINT_LABEL else cleanEndpoint.trimEnd('/'),
            householdId = cleanHouseholdId,
            credentialSecret = if (mode == PostgresConnectionMode.DIRECT_DSN) cleanEndpoint else null,
        )
    }

    private fun inferMode(endpoint: String): PostgresConnectionMode {
        val lower = endpoint.lowercase()
        return when {
            lower.startsWith("postgres://") || lower.startsWith("postgresql://") -> PostgresConnectionMode.DIRECT_DSN
            ".supabase.co" in lower -> PostgresConnectionMode.SUPABASE
            "/rest/v1" in lower -> PostgresConnectionMode.POSTGREST
            else -> PostgresConnectionMode.WONDERFOOD_SERVER
        }
    }

    private fun validateEndpoint(endpoint: String, mode: PostgresConnectionMode) {
        val lower = endpoint.lowercase()
        when (mode) {
            PostgresConnectionMode.DIRECT_DSN -> {
                require(lower.startsWith("postgres://") || lower.startsWith("postgresql://")) {
                    "Direct PostgreSQL mode requires a postgres:// or postgresql:// connection string."
                }
                require("sslmode=disable" !in lower) { "Direct PostgreSQL connections must not disable TLS." }
            }
            PostgresConnectionMode.SUPABASE,
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
    val credentialSecret: String? = null,
) {
    init {
        require(endpoint.isNotBlank()) { "Postgres endpoint must not be blank." }
        require(householdId.isNotBlank()) { "Household ID must not be blank." }
    }
}

private const val DIRECT_DSN_ENDPOINT_LABEL = "direct-postgres"
