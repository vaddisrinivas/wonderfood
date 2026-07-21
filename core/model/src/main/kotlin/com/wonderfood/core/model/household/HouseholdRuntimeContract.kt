package com.wonderfood.core.model.household

private fun requireRuntimeText(value: String, label: String): String {
    require(value.isNotBlank()) { "$label must not be blank." }
    return value
}

enum class RuntimeSurfaceArea {
    COMMANDS,
    STORAGE,
    UI,
    PROVIDERS,
    BACKUP,
    AI,
    TESTS,
}

enum class RuntimeSurfaceDisposition {
    KEEP_AND_RETARGET,
    REPLACE,
    DELETE_AFTER_CALLERS_MIGRATE,
}

data class RuntimeSurfaceInventoryItem(
    val area: RuntimeSurfaceArea,
    val name: String,
    val currentPattern: String,
    val canonicalTarget: String,
    val disposition: RuntimeSurfaceDisposition,
) {
    init {
        requireRuntimeText(name, "Runtime surface name")
        requireRuntimeText(currentPattern, "Current runtime pattern")
        requireRuntimeText(canonicalTarget, "Canonical target")
    }
}

object HouseholdRuntimeContract {
    val mutationIngress: Set<SourceKind> = setOf(
        SourceKind.MANUAL,
        SourceKind.DATA_HOME_HUMAN,
        SourceKind.DETERMINISTIC_IMPORT,
        SourceKind.AI_PROPOSAL,
        SourceKind.RECEIPT,
        SourceKind.RECIPE,
        SourceKind.SHOPPING_LINE,
        SourceKind.SYSTEM,
    )

    val commandBoundary = "CanonicalCommand -> HouseholdRepository -> SQLite transaction -> audit/outbox"

    val legacyReplacementInventory: List<RuntimeSurfaceInventoryItem> = listOf(
        RuntimeSurfaceInventoryItem(
            area = RuntimeSurfaceArea.COMMANDS,
            name = "FoodCommandExecutor and FoodDraftCommandExecutor",
            currentPattern = "Food-only command graph and app draft mutation commands",
            canonicalTarget = "Household command handlers for item, inventory, shopping, recipe, meal, purchase, proposal, and sync review changes",
            disposition = RuntimeSurfaceDisposition.REPLACE,
        ),
        RuntimeSurfaceInventoryItem(
            area = RuntimeSurfaceArea.STORAGE,
            name = "Retired app memory store and Room food command tables",
            currentPattern = "Retired app store plus separate core food Room schema",
            canonicalTarget = "Destructive Room reset with household entities, event ledger, outbox, remote bindings, sync bases, tombstones, conflicts, and latest-safety snapshots",
            disposition = RuntimeSurfaceDisposition.DELETE_AFTER_CALLERS_MIGRATE,
        ),
        RuntimeSurfaceInventoryItem(
            area = RuntimeSurfaceArea.UI,
            name = "Main app food screens and settings provider flows",
            currentPattern = "Food-first memory screens with provider controls in settings",
            canonicalTarget = "Now, Food, Week, Cart shell with first-boot data-home choice, global quick add, reversible kitchen/cart actions, spending, and Needs review",
            disposition = RuntimeSurfaceDisposition.KEEP_AND_RETARGET,
        ),
        RuntimeSurfaceInventoryItem(
            area = RuntimeSurfaceArea.PROVIDERS,
            name = "GoogleSheetsGateway, NotionGateway, PostgresGateway, snapshot coordinators",
            currentPattern = "Provider-specific snapshot bridges and partial workspace helpers",
            canonicalTarget = "Single DataHomeAdapter contract with provision, probe, scan, pull, push, health, disconnect, repair, and three-way merge decisions",
            disposition = RuntimeSurfaceDisposition.REPLACE,
        ),
        RuntimeSurfaceInventoryItem(
            area = RuntimeSurfaceArea.BACKUP,
            name = "WonderFoodBackupGateway and backend switch safety backup",
            currentPattern = "HouseholdUiMemory manifest and database-file backup",
            canonicalTarget = "Household latest-safety snapshot before attach, switch, remote replace, and bulk conflict resolution",
            disposition = RuntimeSurfaceDisposition.KEEP_AND_RETARGET,
        ),
        RuntimeSurfaceInventoryItem(
            area = RuntimeSurfaceArea.AI,
            name = "StructuredProposalGateway and skill command envelopes",
            currentPattern = "Typed proposal scaffolding targeting food commands",
            canonicalTarget = "Versioned household proposals whose accepted commands route through the canonical repository only",
            disposition = RuntimeSurfaceDisposition.KEEP_AND_RETARGET,
        ),
        RuntimeSurfaceInventoryItem(
            area = RuntimeSurfaceArea.TESTS,
            name = "Runtime, gateway, command, AI, emulator, and live workspace tests",
            currentPattern = "Retired food/memory assertions plus provider snapshot proof",
            canonicalTarget = "Household model, repository, command, conflict, offline replay, provider fake/live, UI, accessibility, and release proof gates",
            disposition = RuntimeSurfaceDisposition.REPLACE,
        ),
    )

    fun inventoryFor(area: RuntimeSurfaceArea): List<RuntimeSurfaceInventoryItem> =
        legacyReplacementInventory.filter { it.area == area }

    fun mutationBoundaryFor(source: SourceKind): String {
        require(source in mutationIngress) { "Unsupported mutation source: $source" }
        return commandBoundary
    }
}
