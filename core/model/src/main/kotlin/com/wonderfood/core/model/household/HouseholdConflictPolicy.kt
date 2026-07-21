package com.wonderfood.core.model.household

data class FieldDelta(
    val before: String?,
    val after: String?,
)

data class RecordDelta(
    val fields: Map<String, FieldDelta> = emptyMap(),
    val invalidFields: Set<String> = emptySet(),
)

data class RecoveryFieldValue(
    val field: String,
    val base: String?,
    val displacedAppValue: String?,
    val selectedDataHomeValue: String?,
)

enum class SyncDecisionAction {
    NO_OP,
    PULL_DATA_HOME,
    PUSH_APP,
    MERGE,
    DATA_HOME_WINS_OVERLAP,
    NEEDS_REVIEW,
}

data class SyncDecision(
    val action: SyncDecisionAction,
    val fields: Set<String> = emptySet(),
    val reason: String,
    val recoveryHistory: List<RecoveryFieldValue> = emptyList(),
)

object HouseholdConflictPolicy {
    fun decide(app: RecordDelta, dataHome: RecordDelta): SyncDecision {
        val invalid = app.invalidFields + dataHome.invalidFields
        if (invalid.isNotEmpty()) {
            return SyncDecision(SyncDecisionAction.NEEDS_REVIEW, invalid, "One or more values are invalid.")
        }

        if (app.fields.isEmpty() && dataHome.fields.isEmpty()) {
            return SyncDecision(SyncDecisionAction.NO_OP, reason = "Neither side changed.")
        }
        if (app.fields.isEmpty()) {
            return SyncDecision(SyncDecisionAction.PULL_DATA_HOME, dataHome.fields.keys, "Only the data home changed.")
        }
        if (dataHome.fields.isEmpty()) {
            return SyncDecision(SyncDecisionAction.PUSH_APP, app.fields.keys, "Only the app changed.")
        }

        val archivePaths = (app.fields.keys + dataHome.fields.keys).filter { it.endsWith(".archived") }.toSet()
        val appOtherFields = app.fields.keys - archivePaths
        val dataHomeOtherFields = dataHome.fields.keys - archivePaths
        val archiveOverlapsEdit =
            (archivePaths.any { it in app.fields } && dataHomeOtherFields.isNotEmpty()) ||
                (archivePaths.any { it in dataHome.fields } && appOtherFields.isNotEmpty())
        if (archiveOverlapsEdit) {
            return SyncDecision(
                SyncDecisionAction.NEEDS_REVIEW,
                archivePaths + appOtherFields + dataHomeOtherFields,
                "Archive overlaps another edit.",
            )
        }

        val overlapping = app.fields.keys.intersect(dataHome.fields.keys)
        val divergent = overlapping.filterTo(linkedSetOf()) { path ->
            app.fields.getValue(path).after != dataHome.fields.getValue(path).after
        }
        if (divergent.isEmpty()) {
            return SyncDecision(
                SyncDecisionAction.MERGE,
                app.fields.keys + dataHome.fields.keys,
                "Changes are disjoint or converge on the same value.",
            )
        }

        val highRisk = divergent.filterTo(linkedSetOf()) { path ->
            HouseholdWorkspaceContract.field(path)?.conflictRisk != ConflictRisk.LOW
        }
        if (highRisk.isNotEmpty()) {
            return SyncDecision(
                SyncDecisionAction.NEEDS_REVIEW,
                highRisk,
                "Both sides changed the same high-risk field.",
            )
        }

        return SyncDecision(
            SyncDecisionAction.DATA_HOME_WINS_OVERLAP,
            divergent,
            "The selected human data home wins overlapping low-risk fields.",
            recoveryHistory = divergent.map { path ->
                RecoveryFieldValue(
                    field = path,
                    base = app.fields.getValue(path).before,
                    displacedAppValue = app.fields.getValue(path).after,
                    selectedDataHomeValue = dataHome.fields.getValue(path).after,
                )
            },
        )
    }
}
