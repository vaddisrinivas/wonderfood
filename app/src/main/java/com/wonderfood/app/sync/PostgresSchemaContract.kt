package com.wonderfood.app.sync

object PostgresSchemaContract {
    const val SCHEMA_VERSION: Int = 1
    const val SCHEMA_FINGERPRINT: String = "wf-postgres-v1-canonical-household"
    const val SESSION_HOUSEHOLD_HEADER: String = "X-WonderFood-Household"

    val requiredTables: List<String> = listOf(
        "wonderfood_household_members",
        "wonderfood_sync_cursors",
        "wonderfood_remote_bindings",
        "wonderfood_snapshots",
        "wonderfood_outbox",
        "wonderfood_tombstones",
        "wonderfood_conflicts",
    )

    val requiredRoutes: List<PostgresApiRoute> = listOf(
        PostgresApiRoute("GET", "/health", householdScoped = false),
        PostgresApiRoute("GET", "/schema", householdScoped = false),
        PostgresApiRoute("GET", "/households/{householdId}/cursors", householdScoped = true),
        PostgresApiRoute("PUT", "/households/{householdId}/cursors/{cursorName}", householdScoped = true),
        PostgresApiRoute("GET", "/households/{householdId}/snapshot/current", householdScoped = true),
        PostgresApiRoute("PUT", "/households/{householdId}/snapshot/current", householdScoped = true),
        PostgresApiRoute("POST", "/households/{householdId}/outbox", householdScoped = true),
        PostgresApiRoute("PUT", "/households/{householdId}/bindings/{provider}/{remoteId}", householdScoped = true),
        PostgresApiRoute("PUT", "/households/{householdId}/tombstones/{entityType}/{entityId}", householdScoped = true),
        PostgresApiRoute("POST", "/households/{householdId}/conflicts", householdScoped = true),
        PostgresApiRoute("POST", "/households/{householdId}/repair", householdScoped = true),
    )

    val migrationSql: List<String> = listOf(
        """
        create table if not exists wonderfood_household_members (
          household_id text not null,
          account_subject text not null,
          role text not null check (role in ('owner','member')),
          created_at timestamptz not null default now(),
          primary key (household_id, account_subject)
        );
        """.trimIndent(),
        """
        create table if not exists wonderfood_snapshots (
          household_id text not null,
          snapshot_id text not null,
          schema_version integer not null,
          schema_fingerprint text not null,
          updated_at timestamptz not null,
          snapshot_json jsonb not null,
          primary key (household_id, snapshot_id)
        );
        """.trimIndent(),
        """
        create table if not exists wonderfood_sync_cursors (
          household_id text not null,
          cursor_name text not null,
          provider text not null,
          remote_cursor text not null,
          updated_at timestamptz not null,
          primary key (household_id, cursor_name)
        );
        """.trimIndent(),
        """
        create table if not exists wonderfood_remote_bindings (
          household_id text not null,
          provider text not null,
          local_id text not null,
          remote_id text not null,
          entity_type text not null,
          updated_at timestamptz not null,
          primary key (household_id, provider, local_id)
        );
        """.trimIndent(),
        """
        create table if not exists wonderfood_outbox (
          household_id text not null,
          command_id text not null,
          idempotency_key text not null,
          command_json jsonb not null,
          created_at timestamptz not null,
          pushed_at timestamptz,
          primary key (household_id, command_id),
          unique (household_id, idempotency_key)
        );
        """.trimIndent(),
        """
        create table if not exists wonderfood_tombstones (
          household_id text not null,
          entity_type text not null,
          entity_id text not null,
          reason text not null,
          command_id text not null,
          updated_at timestamptz not null,
          primary key (household_id, entity_type, entity_id)
        );
        """.trimIndent(),
        """
        create table if not exists wonderfood_conflicts (
          household_id text not null,
          conflict_id text not null,
          entity_type text not null,
          entity_id text not null,
          base_json jsonb not null,
          local_json jsonb not null,
          remote_json jsonb not null,
          source text not null,
          updated_at timestamptz not null,
          primary key (household_id, conflict_id)
        );
        """.trimIndent(),
        """
        alter table wonderfood_household_members enable row level security;
        alter table wonderfood_snapshots enable row level security;
        alter table wonderfood_sync_cursors enable row level security;
        alter table wonderfood_remote_bindings enable row level security;
        alter table wonderfood_outbox enable row level security;
        alter table wonderfood_tombstones enable row level security;
        alter table wonderfood_conflicts enable row level security;
        """.trimIndent(),
    )

    val membershipPolicySql: List<String> =
        requiredTables.map { table ->
            """
            create policy ${table}_household_member on $table
            using (
              exists (
                select 1 from wonderfood_household_members m
                where m.household_id = $table.household_id
                  and m.account_subject = current_setting('request.jwt.claim.sub', true)
              )
            )
            with check (
              exists (
                select 1 from wonderfood_household_members m
                where m.household_id = $table.household_id
                  and m.account_subject = current_setting('request.jwt.claim.sub', true)
              )
            );
            """.trimIndent()
        }

    fun requireMatchingHousehold(route: PostgresApiRoute, routeHouseholdId: String?, sessionHouseholdId: String?) {
        if (!route.householdScoped) return
        require(!routeHouseholdId.isNullOrBlank()) { "Household route must include a household ID." }
        require(!sessionHouseholdId.isNullOrBlank()) { "Authenticated session must include a household ID." }
        require(routeHouseholdId == sessionHouseholdId) { "Authenticated session cannot access another household." }
    }
}

data class PostgresApiRoute(
    val method: String,
    val path: String,
    val householdScoped: Boolean,
)
