package com.wonderfood.core.data.room

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object WonderFoodMigrations {
    val MIGRATION_1_2: Migration = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("PRAGMA foreign_keys=ON")
        }
    }

    val MIGRATION_2_3: Migration = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("PRAGMA foreign_keys=ON")
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS households (
                    id TEXT NOT NULL PRIMARY KEY,
                    name TEXT NOT NULL,
                    default_currency TEXT NOT NULL,
                    timezone TEXT NOT NULL,
                    locale TEXT NOT NULL,
                    active_data_home TEXT NOT NULL,
                    schema_version INTEGER NOT NULL,
                    created_at INTEGER NOT NULL,
                    updated_at INTEGER NOT NULL,
                    revision INTEGER NOT NULL
                )
                """.trimIndent(),
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS index_households_active_data_home ON households(active_data_home)")
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS household_items (
                    id TEXT NOT NULL PRIMARY KEY,
                    household_id TEXT NOT NULL,
                    name TEXT NOT NULL,
                    kind TEXT NOT NULL,
                    category TEXT,
                    brand TEXT,
                    default_unit TEXT NOT NULL,
                    preferred_store TEXT,
                    notes TEXT,
                    food_details_id TEXT,
                    created_at INTEGER NOT NULL,
                    updated_at INTEGER NOT NULL,
                    archived_at INTEGER,
                    revision INTEGER NOT NULL,
                    source_kind TEXT NOT NULL,
                    source_label TEXT NOT NULL,
                    source_actor_id TEXT,
                    source_device_id TEXT,
                    source_external_reference TEXT,
                    confidence_basis_points INTEGER,
                    confidence_rationale TEXT
                )
                """.trimIndent(),
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS index_household_items_household_id ON household_items(household_id)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_household_items_kind ON household_items(kind)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_household_items_name ON household_items(name)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_household_items_archived_at ON household_items(archived_at)")
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS household_inventory_lots (
                    id TEXT NOT NULL PRIMARY KEY,
                    household_id TEXT NOT NULL,
                    item_id TEXT NOT NULL,
                    quantity_amount TEXT,
                    quantity_unit TEXT NOT NULL,
                    location_id TEXT,
                    purchased_at INTEGER,
                    opened_at INTEGER,
                    expires_on TEXT,
                    purchase_line_id TEXT,
                    unit_cost_minor_units INTEGER,
                    unit_cost_currency TEXT,
                    status TEXT NOT NULL,
                    created_at INTEGER NOT NULL,
                    updated_at INTEGER NOT NULL,
                    archived_at INTEGER,
                    revision INTEGER NOT NULL,
                    source_kind TEXT NOT NULL,
                    source_label TEXT NOT NULL,
                    source_actor_id TEXT,
                    source_device_id TEXT,
                    source_external_reference TEXT,
                    confidence_basis_points INTEGER,
                    confidence_rationale TEXT
                )
                """.trimIndent(),
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS index_household_inventory_lots_household_id ON household_inventory_lots(household_id)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_household_inventory_lots_item_id ON household_inventory_lots(item_id)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_household_inventory_lots_status ON household_inventory_lots(status)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_household_inventory_lots_expires_on ON household_inventory_lots(expires_on)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_household_inventory_lots_archived_at ON household_inventory_lots(archived_at)")
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS household_shopping_lines (
                    id TEXT NOT NULL PRIMARY KEY,
                    household_id TEXT NOT NULL,
                    shopping_list_id TEXT NOT NULL,
                    item_id TEXT,
                    display_name TEXT NOT NULL,
                    quantity_amount TEXT,
                    quantity_unit TEXT NOT NULL,
                    category TEXT,
                    preferred_store TEXT,
                    status TEXT NOT NULL,
                    reason TEXT NOT NULL,
                    estimated_price_minor_units INTEGER,
                    estimated_price_currency TEXT,
                    created_at INTEGER NOT NULL,
                    updated_at INTEGER NOT NULL,
                    archived_at INTEGER,
                    revision INTEGER NOT NULL,
                    source_kind TEXT NOT NULL,
                    source_label TEXT NOT NULL,
                    source_actor_id TEXT,
                    source_device_id TEXT,
                    source_external_reference TEXT,
                    confidence_basis_points INTEGER,
                    confidence_rationale TEXT
                )
                """.trimIndent(),
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS index_household_shopping_lines_household_id ON household_shopping_lines(household_id)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_household_shopping_lines_shopping_list_id ON household_shopping_lines(shopping_list_id)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_household_shopping_lines_item_id ON household_shopping_lines(item_id)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_household_shopping_lines_status ON household_shopping_lines(status)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_household_shopping_lines_archived_at ON household_shopping_lines(archived_at)")
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS household_recipes (
                    id TEXT NOT NULL PRIMARY KEY,
                    household_id TEXT NOT NULL,
                    name TEXT NOT NULL,
                    description TEXT,
                    source_url TEXT,
                    source_provider TEXT,
                    author TEXT,
                    cuisine TEXT,
                    category TEXT,
                    tags TEXT NOT NULL,
                    yield_amount TEXT,
                    yield_unit TEXT NOT NULL,
                    prep_minutes INTEGER,
                    cook_minutes INTEGER,
                    total_minutes INTEGER,
                    difficulty TEXT,
                    status TEXT NOT NULL,
                    ingredient_ids TEXT NOT NULL,
                    step_ids TEXT NOT NULL,
                    attachment_ids TEXT NOT NULL,
                    nutrition_snapshot_ids TEXT NOT NULL,
                    created_at INTEGER NOT NULL,
                    updated_at INTEGER NOT NULL,
                    archived_at INTEGER,
                    revision INTEGER NOT NULL,
                    source_kind TEXT NOT NULL,
                    source_label TEXT NOT NULL,
                    source_actor_id TEXT,
                    source_device_id TEXT,
                    source_external_reference TEXT,
                    confidence_basis_points INTEGER,
                    confidence_rationale TEXT
                )
                """.trimIndent(),
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS index_household_recipes_household_id ON household_recipes(household_id)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_household_recipes_status ON household_recipes(status)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_household_recipes_name ON household_recipes(name)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_household_recipes_archived_at ON household_recipes(archived_at)")
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS household_purchases (
                    id TEXT NOT NULL PRIMARY KEY,
                    household_id TEXT NOT NULL,
                    merchant_id TEXT,
                    occurred_at INTEGER NOT NULL,
                    receipt_attachment_ids TEXT NOT NULL,
                    subtotal_minor_units INTEGER,
                    subtotal_currency TEXT,
                    tax_minor_units INTEGER,
                    tax_currency TEXT,
                    discount_minor_units INTEGER,
                    discount_currency TEXT,
                    tip_minor_units INTEGER,
                    tip_currency TEXT,
                    total_minor_units INTEGER,
                    total_currency TEXT,
                    payment_note TEXT,
                    status TEXT NOT NULL,
                    reconciliation_difference_minor_units INTEGER,
                    reconciliation_difference_currency TEXT,
                    created_at INTEGER NOT NULL,
                    updated_at INTEGER NOT NULL,
                    archived_at INTEGER,
                    revision INTEGER NOT NULL,
                    source_kind TEXT NOT NULL,
                    source_label TEXT NOT NULL,
                    source_actor_id TEXT,
                    source_device_id TEXT,
                    source_external_reference TEXT,
                    confidence_basis_points INTEGER,
                    confidence_rationale TEXT
                )
                """.trimIndent(),
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS index_household_purchases_household_id ON household_purchases(household_id)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_household_purchases_merchant_id ON household_purchases(merchant_id)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_household_purchases_occurred_at ON household_purchases(occurred_at)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_household_purchases_status ON household_purchases(status)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_household_purchases_archived_at ON household_purchases(archived_at)")
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS household_purchase_lines (
                    id TEXT NOT NULL PRIMARY KEY,
                    household_id TEXT NOT NULL,
                    purchase_id TEXT NOT NULL,
                    item_id TEXT,
                    shopping_line_id TEXT,
                    display_name TEXT NOT NULL,
                    quantity_amount TEXT,
                    quantity_unit TEXT NOT NULL,
                    unit_price_minor_units INTEGER,
                    unit_price_currency TEXT,
                    line_subtotal_minor_units INTEGER,
                    line_subtotal_currency TEXT,
                    discount_minor_units INTEGER,
                    discount_currency TEXT,
                    tax_allocation_minor_units INTEGER,
                    tax_allocation_currency TEXT,
                    final_amount_minor_units INTEGER,
                    final_amount_currency TEXT,
                    spend_category TEXT,
                    disposition TEXT NOT NULL,
                    inventory_lot_id TEXT,
                    review_state TEXT NOT NULL,
                    created_at INTEGER NOT NULL,
                    updated_at INTEGER NOT NULL,
                    archived_at INTEGER,
                    revision INTEGER NOT NULL,
                    source_kind TEXT NOT NULL,
                    source_label TEXT NOT NULL,
                    source_actor_id TEXT,
                    source_device_id TEXT,
                    source_external_reference TEXT,
                    confidence_basis_points INTEGER,
                    confidence_rationale TEXT
                )
                """.trimIndent(),
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS index_household_purchase_lines_household_id ON household_purchase_lines(household_id)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_household_purchase_lines_purchase_id ON household_purchase_lines(purchase_id)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_household_purchase_lines_item_id ON household_purchase_lines(item_id)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_household_purchase_lines_shopping_line_id ON household_purchase_lines(shopping_line_id)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_household_purchase_lines_inventory_lot_id ON household_purchase_lines(inventory_lot_id)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_household_purchase_lines_disposition ON household_purchase_lines(disposition)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_household_purchase_lines_review_state ON household_purchase_lines(review_state)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_household_purchase_lines_archived_at ON household_purchase_lines(archived_at)")
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS household_meal_plans (
                    id TEXT NOT NULL PRIMARY KEY,
                    household_id TEXT NOT NULL,
                    name TEXT NOT NULL,
                    starts_on TEXT NOT NULL,
                    ends_on TEXT NOT NULL,
                    status TEXT NOT NULL,
                    target_profile_ids TEXT NOT NULL,
                    budget_minor_units INTEGER,
                    budget_currency TEXT,
                    nutrition_target_snapshot_id TEXT,
                    created_at INTEGER NOT NULL,
                    updated_at INTEGER NOT NULL,
                    archived_at INTEGER,
                    revision INTEGER NOT NULL,
                    source_kind TEXT NOT NULL,
                    source_label TEXT NOT NULL,
                    source_actor_id TEXT,
                    source_device_id TEXT,
                    source_external_reference TEXT,
                    confidence_basis_points INTEGER,
                    confidence_rationale TEXT
                )
                """.trimIndent(),
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS index_household_meal_plans_household_id ON household_meal_plans(household_id)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_household_meal_plans_starts_on ON household_meal_plans(starts_on)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_household_meal_plans_ends_on ON household_meal_plans(ends_on)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_household_meal_plans_status ON household_meal_plans(status)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_household_meal_plans_archived_at ON household_meal_plans(archived_at)")
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS household_meal_entries (
                    id TEXT NOT NULL PRIMARY KEY,
                    household_id TEXT NOT NULL,
                    meal_plan_id TEXT,
                    scheduled_at INTEGER NOT NULL,
                    slot TEXT NOT NULL,
                    recipe_id TEXT,
                    prepared_batch_id TEXT,
                    title TEXT NOT NULL,
                    servings_amount TEXT,
                    servings_unit TEXT NOT NULL,
                    status TEXT NOT NULL,
                    leftover_intent TEXT,
                    nutrition_snapshot_ids TEXT NOT NULL,
                    notes TEXT,
                    created_at INTEGER NOT NULL,
                    updated_at INTEGER NOT NULL,
                    archived_at INTEGER,
                    revision INTEGER NOT NULL,
                    source_kind TEXT NOT NULL,
                    source_label TEXT NOT NULL,
                    source_actor_id TEXT,
                    source_device_id TEXT,
                    source_external_reference TEXT,
                    confidence_basis_points INTEGER,
                    confidence_rationale TEXT
                )
                """.trimIndent(),
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS index_household_meal_entries_household_id ON household_meal_entries(household_id)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_household_meal_entries_meal_plan_id ON household_meal_entries(meal_plan_id)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_household_meal_entries_scheduled_at ON household_meal_entries(scheduled_at)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_household_meal_entries_recipe_id ON household_meal_entries(recipe_id)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_household_meal_entries_prepared_batch_id ON household_meal_entries(prepared_batch_id)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_household_meal_entries_status ON household_meal_entries(status)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_household_meal_entries_archived_at ON household_meal_entries(archived_at)")
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS household_change_proposals (
                    id TEXT NOT NULL PRIMARY KEY,
                    household_id TEXT NOT NULL,
                    source_payload_reference TEXT NOT NULL,
                    requested_command_types TEXT NOT NULL,
                    warnings TEXT NOT NULL,
                    status TEXT NOT NULL,
                    reviewed_at INTEGER,
                    reviewer TEXT,
                    created_at INTEGER NOT NULL,
                    updated_at INTEGER NOT NULL,
                    archived_at INTEGER,
                    revision INTEGER NOT NULL,
                    source_kind TEXT NOT NULL,
                    source_label TEXT NOT NULL,
                    source_actor_id TEXT,
                    source_device_id TEXT,
                    source_external_reference TEXT,
                    confidence_basis_points INTEGER,
                    confidence_rationale TEXT
                )
                """.trimIndent(),
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS index_household_change_proposals_household_id ON household_change_proposals(household_id)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_household_change_proposals_status ON household_change_proposals(status)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_household_change_proposals_archived_at ON household_change_proposals(archived_at)")
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS household_command_records (
                    id TEXT NOT NULL PRIMARY KEY,
                    household_id TEXT NOT NULL,
                    type TEXT NOT NULL,
                    requested_at INTEGER NOT NULL,
                    applied_at INTEGER,
                    affected_entity_ids TEXT NOT NULL,
                    before_hash TEXT,
                    after_hash TEXT,
                    undo_command_id TEXT,
                    source_kind TEXT NOT NULL,
                    source_label TEXT NOT NULL,
                    source_actor_id TEXT,
                    source_device_id TEXT,
                    source_external_reference TEXT
                )
                """.trimIndent(),
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS index_household_command_records_household_id ON household_command_records(household_id)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_household_command_records_type ON household_command_records(type)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_household_command_records_applied_at ON household_command_records(applied_at)")
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS household_sync_outbox (
                    id TEXT NOT NULL PRIMARY KEY,
                    connection_id TEXT NOT NULL,
                    command_id TEXT NOT NULL,
                    operation TEXT NOT NULL,
                    household_id TEXT NOT NULL,
                    entity_type TEXT NOT NULL,
                    entity_id TEXT NOT NULL,
                    schema_version INTEGER NOT NULL,
                    revision INTEGER NOT NULL,
                    created_at INTEGER NOT NULL,
                    updated_at INTEGER NOT NULL,
                    archived_at INTEGER,
                    origin_device_id TEXT NOT NULL,
                    last_command_id TEXT NOT NULL,
                    payload_hash TEXT NOT NULL,
                    idempotency_key TEXT NOT NULL,
                    status TEXT NOT NULL,
                    retry_count INTEGER NOT NULL,
                    last_error TEXT
                )
                """.trimIndent(),
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS index_household_sync_outbox_connection_id ON household_sync_outbox(connection_id)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_household_sync_outbox_household_id ON household_sync_outbox(household_id)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_household_sync_outbox_status ON household_sync_outbox(status)")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_household_sync_outbox_idempotency_key ON household_sync_outbox(idempotency_key)")
        }
    }

    val MIGRATION_3_4: Migration = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("PRAGMA foreign_keys=ON")
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS household_recipe_ingredients (
                    id TEXT NOT NULL PRIMARY KEY,
                    household_id TEXT NOT NULL,
                    recipe_id TEXT NOT NULL,
                    item_id TEXT,
                    original_text TEXT NOT NULL,
                    quantity_amount TEXT,
                    quantity_unit TEXT NOT NULL,
                    preparation TEXT,
                    optional INTEGER NOT NULL,
                    section TEXT,
                    sort_order INTEGER NOT NULL,
                    substitute_item_ids TEXT NOT NULL,
                    created_at INTEGER NOT NULL,
                    updated_at INTEGER NOT NULL,
                    archived_at INTEGER,
                    revision INTEGER NOT NULL,
                    source_kind TEXT NOT NULL,
                    source_label TEXT NOT NULL,
                    source_actor_id TEXT,
                    source_device_id TEXT,
                    source_external_reference TEXT,
                    confidence_basis_points INTEGER,
                    confidence_rationale TEXT
                )
                """.trimIndent(),
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS index_household_recipe_ingredients_household_id ON household_recipe_ingredients(household_id)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_household_recipe_ingredients_recipe_id ON household_recipe_ingredients(recipe_id)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_household_recipe_ingredients_item_id ON household_recipe_ingredients(item_id)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_household_recipe_ingredients_archived_at ON household_recipe_ingredients(archived_at)")
        }
    }

    val MIGRATION_4_5: Migration = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("PRAGMA foreign_keys=ON")
            db.execSQL("ALTER TABLE household_shopping_lines ADD COLUMN source_entity_ids TEXT NOT NULL DEFAULT '[]'")
        }
    }

    val MIGRATION_5_6: Migration = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("PRAGMA foreign_keys=ON")
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS household_nutrition_snapshots (
                    id TEXT NOT NULL PRIMARY KEY,
                    household_id TEXT NOT NULL,
                    subject_type TEXT NOT NULL,
                    subject_id TEXT NOT NULL,
                    basis_amount TEXT,
                    basis_unit TEXT NOT NULL,
                    energy_kcal TEXT,
                    protein_grams TEXT,
                    carbohydrate_grams TEXT,
                    fat_grams TEXT,
                    fiber_grams TEXT,
                    sugar_grams TEXT,
                    sodium_milligrams TEXT,
                    provider TEXT,
                    captured_at INTEGER NOT NULL,
                    warnings TEXT NOT NULL,
                    created_at INTEGER NOT NULL,
                    updated_at INTEGER NOT NULL,
                    archived_at INTEGER,
                    revision INTEGER NOT NULL,
                    source_kind TEXT NOT NULL,
                    source_label TEXT NOT NULL,
                    source_actor_id TEXT,
                    source_device_id TEXT,
                    source_external_reference TEXT,
                    confidence_basis_points INTEGER,
                    confidence_rationale TEXT
                )
                """.trimIndent(),
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS index_household_nutrition_snapshots_household_id ON household_nutrition_snapshots(household_id)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_household_nutrition_snapshots_subject_type ON household_nutrition_snapshots(subject_type)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_household_nutrition_snapshots_subject_id ON household_nutrition_snapshots(subject_id)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_household_nutrition_snapshots_captured_at ON household_nutrition_snapshots(captured_at)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_household_nutrition_snapshots_archived_at ON household_nutrition_snapshots(archived_at)")
        }
    }

    val MIGRATION_6_7: Migration = object : Migration(6, 7) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("PRAGMA foreign_keys=ON")
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS household_recipe_steps (
                    id TEXT NOT NULL PRIMARY KEY,
                    household_id TEXT NOT NULL,
                    recipe_id TEXT NOT NULL,
                    section TEXT,
                    sort_order INTEGER NOT NULL,
                    instruction TEXT NOT NULL,
                    duration_minutes INTEGER,
                    timer_label TEXT,
                    ingredient_ids TEXT NOT NULL,
                    attachment_ids TEXT NOT NULL,
                    created_at INTEGER NOT NULL,
                    updated_at INTEGER NOT NULL,
                    archived_at INTEGER,
                    revision INTEGER NOT NULL,
                    source_kind TEXT NOT NULL,
                    source_label TEXT NOT NULL,
                    source_actor_id TEXT,
                    source_device_id TEXT,
                    source_external_reference TEXT,
                    confidence_basis_points INTEGER,
                    confidence_rationale TEXT
                )
                """.trimIndent(),
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS index_household_recipe_steps_household_id ON household_recipe_steps(household_id)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_household_recipe_steps_recipe_id ON household_recipe_steps(recipe_id)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_household_recipe_steps_archived_at ON household_recipe_steps(archived_at)")
        }
    }

    val MIGRATION_7_8: Migration = object : Migration(7, 8) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("PRAGMA foreign_keys=ON")
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS household_tombstones (
                    id TEXT NOT NULL PRIMARY KEY,
                    household_id TEXT NOT NULL,
                    entity_type TEXT NOT NULL,
                    entity_id TEXT NOT NULL,
                    reason TEXT NOT NULL,
                    command_id TEXT NOT NULL,
                    created_at INTEGER NOT NULL,
                    updated_at INTEGER NOT NULL,
                    archived_at INTEGER,
                    revision INTEGER NOT NULL,
                    source_kind TEXT NOT NULL,
                    source_label TEXT NOT NULL,
                    source_actor_id TEXT,
                    source_device_id TEXT,
                    source_external_reference TEXT,
                    confidence_basis_points INTEGER,
                    confidence_rationale TEXT
                )
                """.trimIndent(),
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS index_household_tombstones_household_id ON household_tombstones(household_id)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_household_tombstones_entity_type ON household_tombstones(entity_type)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_household_tombstones_entity_id ON household_tombstones(entity_id)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_household_tombstones_reason ON household_tombstones(reason)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_household_tombstones_command_id ON household_tombstones(command_id)")
        }
    }

    val MIGRATION_8_9: Migration = object : Migration(8, 9) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("PRAGMA foreign_keys=ON")
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS household_remote_bindings (
                    id TEXT NOT NULL PRIMARY KEY,
                    household_id TEXT NOT NULL,
                    connection_id TEXT NOT NULL,
                    entity_type TEXT NOT NULL,
                    entity_id TEXT NOT NULL,
                    remote_object_id TEXT NOT NULL,
                    remote_parent_id TEXT,
                    remote_schema_fingerprint TEXT
                )
                """.trimIndent(),
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS index_household_remote_bindings_household_id ON household_remote_bindings(household_id)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_household_remote_bindings_connection_id ON household_remote_bindings(connection_id)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_household_remote_bindings_entity_type ON household_remote_bindings(entity_type)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_household_remote_bindings_entity_id ON household_remote_bindings(entity_id)")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_household_remote_bindings_connection_id_entity_type_entity_id ON household_remote_bindings(connection_id, entity_type, entity_id)")
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS household_sync_bases (
                    id TEXT NOT NULL PRIMARY KEY,
                    household_id TEXT NOT NULL,
                    connection_id TEXT NOT NULL,
                    entity_type TEXT NOT NULL,
                    entity_id TEXT NOT NULL,
                    remote_object_id TEXT NOT NULL,
                    remote_parent_id TEXT,
                    remote_schema_fingerprint TEXT,
                    schema_version INTEGER NOT NULL,
                    revision INTEGER NOT NULL,
                    created_at INTEGER NOT NULL,
                    updated_at INTEGER NOT NULL,
                    archived_at INTEGER,
                    origin_device_id TEXT NOT NULL,
                    last_command_id TEXT NOT NULL,
                    payload_hash TEXT NOT NULL,
                    local_revision INTEGER NOT NULL,
                    remote_revision TEXT NOT NULL,
                    base_payload_hash TEXT NOT NULL,
                    pulled_at INTEGER NOT NULL
                )
                """.trimIndent(),
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS index_household_sync_bases_household_id ON household_sync_bases(household_id)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_household_sync_bases_connection_id ON household_sync_bases(connection_id)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_household_sync_bases_entity_type ON household_sync_bases(entity_type)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_household_sync_bases_entity_id ON household_sync_bases(entity_id)")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_household_sync_bases_connection_id_entity_type_entity_id ON household_sync_bases(connection_id, entity_type, entity_id)")
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS household_conflicts (
                    id TEXT NOT NULL PRIMARY KEY,
                    household_id TEXT NOT NULL,
                    entity_type TEXT NOT NULL,
                    entity_id TEXT NOT NULL,
                    base_hash TEXT NOT NULL,
                    app_hash TEXT NOT NULL,
                    data_home_hash TEXT NOT NULL,
                    decision_action TEXT NOT NULL,
                    decision_fields TEXT NOT NULL,
                    decision_reason TEXT NOT NULL,
                    app_changed_fields TEXT NOT NULL,
                    data_home_changed_fields TEXT NOT NULL,
                    created_at INTEGER NOT NULL,
                    updated_at INTEGER NOT NULL,
                    archived_at INTEGER,
                    revision INTEGER NOT NULL,
                    source_kind TEXT NOT NULL,
                    source_label TEXT NOT NULL,
                    source_actor_id TEXT,
                    source_device_id TEXT,
                    source_external_reference TEXT,
                    confidence_basis_points INTEGER,
                    confidence_rationale TEXT
                )
                """.trimIndent(),
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS index_household_conflicts_household_id ON household_conflicts(household_id)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_household_conflicts_entity_type ON household_conflicts(entity_type)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_household_conflicts_entity_id ON household_conflicts(entity_id)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_household_conflicts_decision_action ON household_conflicts(decision_action)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_household_conflicts_archived_at ON household_conflicts(archived_at)")
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS household_latest_safety_snapshots (
                    id TEXT NOT NULL PRIMARY KEY,
                    household_id TEXT NOT NULL,
                    reason TEXT NOT NULL,
                    created_at INTEGER NOT NULL,
                    local_replica_hash TEXT NOT NULL,
                    active_data_home TEXT NOT NULL,
                    connection_id TEXT,
                    command_id TEXT
                )
                """.trimIndent(),
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS index_household_latest_safety_snapshots_household_id ON household_latest_safety_snapshots(household_id)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_household_latest_safety_snapshots_reason ON household_latest_safety_snapshots(reason)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_household_latest_safety_snapshots_active_data_home ON household_latest_safety_snapshots(active_data_home)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_household_latest_safety_snapshots_connection_id ON household_latest_safety_snapshots(connection_id)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_household_latest_safety_snapshots_command_id ON household_latest_safety_snapshots(command_id)")
        }
    }

    val MIGRATION_9_10: Migration = object : Migration(9, 10) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("PRAGMA foreign_keys=ON")
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS household_profiles (
                    id TEXT NOT NULL PRIMARY KEY,
                    household_id TEXT NOT NULL,
                    display_name TEXT NOT NULL,
                    role TEXT NOT NULL,
                    dietary_tags TEXT NOT NULL,
                    allergies TEXT NOT NULL,
                    hard_exclusions TEXT NOT NULL,
                    dislikes TEXT NOT NULL,
                    nutrition_goals TEXT NOT NULL,
                    budget_sensitivity TEXT,
                    created_at INTEGER NOT NULL,
                    updated_at INTEGER NOT NULL,
                    archived_at INTEGER,
                    revision INTEGER NOT NULL,
                    source_kind TEXT NOT NULL,
                    source_label TEXT NOT NULL,
                    source_actor_id TEXT,
                    source_device_id TEXT,
                    source_external_reference TEXT,
                    confidence_basis_points INTEGER,
                    confidence_rationale TEXT
                )
                """.trimIndent(),
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS index_household_profiles_household_id ON household_profiles(household_id)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_household_profiles_display_name ON household_profiles(display_name)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_household_profiles_role ON household_profiles(role)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_household_profiles_archived_at ON household_profiles(archived_at)")
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS household_food_details (
                    id TEXT NOT NULL PRIMARY KEY,
                    household_id TEXT NOT NULL,
                    item_id TEXT NOT NULL,
                    dietary_tags TEXT NOT NULL,
                    allergen_tags TEXT NOT NULL,
                    typical_shelf_life_days INTEGER,
                    default_serving_amount TEXT,
                    default_serving_unit TEXT,
                    nutrition_snapshot_ids TEXT NOT NULL,
                    external_identifiers TEXT NOT NULL,
                    created_at INTEGER NOT NULL,
                    updated_at INTEGER NOT NULL,
                    archived_at INTEGER,
                    revision INTEGER NOT NULL,
                    source_kind TEXT NOT NULL,
                    source_label TEXT NOT NULL,
                    source_actor_id TEXT,
                    source_device_id TEXT,
                    source_external_reference TEXT,
                    confidence_basis_points INTEGER,
                    confidence_rationale TEXT
                )
                """.trimIndent(),
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS index_household_food_details_household_id ON household_food_details(household_id)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_household_food_details_item_id ON household_food_details(item_id)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_household_food_details_archived_at ON household_food_details(archived_at)")
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS household_storage_locations (
                    id TEXT NOT NULL PRIMARY KEY,
                    household_id TEXT NOT NULL,
                    name TEXT NOT NULL,
                    type TEXT NOT NULL,
                    parent_location_id TEXT,
                    sort_order INTEGER NOT NULL,
                    created_at INTEGER NOT NULL,
                    updated_at INTEGER NOT NULL,
                    archived_at INTEGER,
                    revision INTEGER NOT NULL,
                    source_kind TEXT NOT NULL,
                    source_label TEXT NOT NULL,
                    source_actor_id TEXT,
                    source_device_id TEXT,
                    source_external_reference TEXT,
                    confidence_basis_points INTEGER,
                    confidence_rationale TEXT
                )
                """.trimIndent(),
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS index_household_storage_locations_household_id ON household_storage_locations(household_id)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_household_storage_locations_type ON household_storage_locations(type)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_household_storage_locations_parent_location_id ON household_storage_locations(parent_location_id)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_household_storage_locations_archived_at ON household_storage_locations(archived_at)")
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS household_shopping_lists (
                    id TEXT NOT NULL PRIMARY KEY,
                    household_id TEXT NOT NULL,
                    name TEXT NOT NULL,
                    status TEXT NOT NULL,
                    merchant_id TEXT,
                    planned_for TEXT,
                    created_at INTEGER NOT NULL,
                    updated_at INTEGER NOT NULL,
                    archived_at INTEGER,
                    revision INTEGER NOT NULL,
                    source_kind TEXT NOT NULL,
                    source_label TEXT NOT NULL,
                    source_actor_id TEXT,
                    source_device_id TEXT,
                    source_external_reference TEXT,
                    confidence_basis_points INTEGER,
                    confidence_rationale TEXT
                )
                """.trimIndent(),
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS index_household_shopping_lists_household_id ON household_shopping_lists(household_id)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_household_shopping_lists_status ON household_shopping_lists(status)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_household_shopping_lists_merchant_id ON household_shopping_lists(merchant_id)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_household_shopping_lists_planned_for ON household_shopping_lists(planned_for)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_household_shopping_lists_archived_at ON household_shopping_lists(archived_at)")
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS household_cooking_sessions (
                    id TEXT NOT NULL PRIMARY KEY,
                    household_id TEXT NOT NULL,
                    recipe_id TEXT NOT NULL,
                    started_at INTEGER,
                    finished_at INTEGER,
                    current_step INTEGER NOT NULL,
                    servings_amount TEXT,
                    servings_unit TEXT NOT NULL,
                    state TEXT NOT NULL,
                    prepared_batch_id TEXT,
                    created_at INTEGER NOT NULL,
                    updated_at INTEGER NOT NULL,
                    archived_at INTEGER,
                    revision INTEGER NOT NULL,
                    source_kind TEXT NOT NULL,
                    source_label TEXT NOT NULL,
                    source_actor_id TEXT,
                    source_device_id TEXT,
                    source_external_reference TEXT,
                    confidence_basis_points INTEGER,
                    confidence_rationale TEXT
                )
                """.trimIndent(),
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS index_household_cooking_sessions_household_id ON household_cooking_sessions(household_id)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_household_cooking_sessions_recipe_id ON household_cooking_sessions(recipe_id)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_household_cooking_sessions_state ON household_cooking_sessions(state)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_household_cooking_sessions_prepared_batch_id ON household_cooking_sessions(prepared_batch_id)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_household_cooking_sessions_archived_at ON household_cooking_sessions(archived_at)")
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS household_prepared_batches (
                    id TEXT NOT NULL PRIMARY KEY,
                    household_id TEXT NOT NULL,
                    recipe_id TEXT,
                    meal_entry_id TEXT,
                    prepared_at INTEGER NOT NULL,
                    total_quantity_amount TEXT,
                    total_quantity_unit TEXT NOT NULL,
                    remaining_quantity_amount TEXT,
                    remaining_quantity_unit TEXT NOT NULL,
                    storage_location_id TEXT,
                    consume_by TEXT,
                    nutrition_snapshot_id TEXT,
                    created_at INTEGER NOT NULL,
                    updated_at INTEGER NOT NULL,
                    archived_at INTEGER,
                    revision INTEGER NOT NULL,
                    source_kind TEXT NOT NULL,
                    source_label TEXT NOT NULL,
                    source_actor_id TEXT,
                    source_device_id TEXT,
                    source_external_reference TEXT,
                    confidence_basis_points INTEGER,
                    confidence_rationale TEXT
                )
                """.trimIndent(),
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS index_household_prepared_batches_household_id ON household_prepared_batches(household_id)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_household_prepared_batches_recipe_id ON household_prepared_batches(recipe_id)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_household_prepared_batches_meal_entry_id ON household_prepared_batches(meal_entry_id)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_household_prepared_batches_storage_location_id ON household_prepared_batches(storage_location_id)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_household_prepared_batches_consume_by ON household_prepared_batches(consume_by)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_household_prepared_batches_archived_at ON household_prepared_batches(archived_at)")
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS household_merchants (
                    id TEXT NOT NULL PRIMARY KEY,
                    household_id TEXT NOT NULL,
                    name TEXT NOT NULL,
                    category TEXT,
                    location TEXT,
                    external_identifiers TEXT NOT NULL,
                    created_at INTEGER NOT NULL,
                    updated_at INTEGER NOT NULL,
                    archived_at INTEGER,
                    revision INTEGER NOT NULL,
                    source_kind TEXT NOT NULL,
                    source_label TEXT NOT NULL,
                    source_actor_id TEXT,
                    source_device_id TEXT,
                    source_external_reference TEXT,
                    confidence_basis_points INTEGER,
                    confidence_rationale TEXT
                )
                """.trimIndent(),
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS index_household_merchants_household_id ON household_merchants(household_id)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_household_merchants_name ON household_merchants(name)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_household_merchants_category ON household_merchants(category)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_household_merchants_archived_at ON household_merchants(archived_at)")
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS household_waste_events (
                    id TEXT NOT NULL PRIMARY KEY,
                    household_id TEXT NOT NULL,
                    inventory_lot_id TEXT NOT NULL,
                    quantity_amount TEXT,
                    quantity_unit TEXT NOT NULL,
                    reason TEXT NOT NULL,
                    estimated_cost_minor_units INTEGER,
                    estimated_cost_currency TEXT,
                    occurred_at INTEGER NOT NULL,
                    created_at INTEGER NOT NULL,
                    updated_at INTEGER NOT NULL,
                    archived_at INTEGER,
                    revision INTEGER NOT NULL,
                    source_kind TEXT NOT NULL,
                    source_label TEXT NOT NULL,
                    source_actor_id TEXT,
                    source_device_id TEXT,
                    source_external_reference TEXT,
                    confidence_basis_points INTEGER,
                    confidence_rationale TEXT
                )
                """.trimIndent(),
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS index_household_waste_events_household_id ON household_waste_events(household_id)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_household_waste_events_inventory_lot_id ON household_waste_events(inventory_lot_id)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_household_waste_events_occurred_at ON household_waste_events(occurred_at)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_household_waste_events_archived_at ON household_waste_events(archived_at)")
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS household_inventory_events (
                    id TEXT NOT NULL PRIMARY KEY,
                    household_id TEXT NOT NULL,
                    item_id TEXT NOT NULL,
                    lot_id TEXT,
                    type TEXT NOT NULL,
                    quantity_delta_amount TEXT,
                    quantity_delta_unit TEXT,
                    reason TEXT,
                    related_entity_id TEXT,
                    command_id TEXT NOT NULL,
                    created_at INTEGER NOT NULL,
                    updated_at INTEGER NOT NULL,
                    archived_at INTEGER,
                    revision INTEGER NOT NULL,
                    source_kind TEXT NOT NULL,
                    source_label TEXT NOT NULL,
                    source_actor_id TEXT,
                    source_device_id TEXT,
                    source_external_reference TEXT,
                    confidence_basis_points INTEGER,
                    confidence_rationale TEXT
                )
                """.trimIndent(),
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS index_household_inventory_events_household_id ON household_inventory_events(household_id)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_household_inventory_events_item_id ON household_inventory_events(item_id)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_household_inventory_events_lot_id ON household_inventory_events(lot_id)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_household_inventory_events_type ON household_inventory_events(type)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_household_inventory_events_command_id ON household_inventory_events(command_id)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_household_inventory_events_archived_at ON household_inventory_events(archived_at)")
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS household_sync_cursors (
                    id TEXT NOT NULL PRIMARY KEY,
                    household_id TEXT NOT NULL,
                    connection_id TEXT NOT NULL,
                    cursor TEXT NOT NULL,
                    pulled_at INTEGER NOT NULL,
                    remote_high_watermark TEXT
                )
                """.trimIndent(),
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS index_household_sync_cursors_household_id ON household_sync_cursors(household_id)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_household_sync_cursors_connection_id ON household_sync_cursors(connection_id)")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_household_sync_cursors_household_id_connection_id ON household_sync_cursors(household_id, connection_id)")
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS household_recovery_snapshots (
                    id TEXT NOT NULL PRIMARY KEY,
                    household_id TEXT NOT NULL,
                    reason TEXT NOT NULL,
                    created_at INTEGER NOT NULL,
                    payload_hash TEXT NOT NULL,
                    object_count INTEGER NOT NULL,
                    command_id TEXT
                )
                """.trimIndent(),
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS index_household_recovery_snapshots_household_id ON household_recovery_snapshots(household_id)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_household_recovery_snapshots_reason ON household_recovery_snapshots(reason)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_household_recovery_snapshots_command_id ON household_recovery_snapshots(command_id)")
        }
    }

    val ALL: Array<Migration> = arrayOf(
        MIGRATION_1_2,
        MIGRATION_2_3,
        MIGRATION_3_4,
        MIGRATION_4_5,
        MIGRATION_5_6,
        MIGRATION_6_7,
        MIGRATION_7_8,
        MIGRATION_8_9,
        MIGRATION_9_10,
    )
}
