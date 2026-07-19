package com.wonderfood.app.sync

import com.wonderfood.core.data.room.LocalSqliteBackend
import com.wonderfood.core.model.WonderFoodSnapshot
import com.wonderfood.core.model.WonderFoodSnapshotCodec

class GoogleSheetsSnapshotSyncCoordinator(
    private val localBackend: LocalSqliteBackend? = null,
    private val sheetsGateway: GoogleSheetsSnapshotGateway,
    private val clock: () -> String,
) {
    suspend fun exportLocalSnapshot(accessToken: String, spreadsheetId: String): GoogleSheetsSnapshotSyncResult.Exported {
        val snapshot = requireNotNull(localBackend) {
            "Local backend is required for exportLocalSnapshot."
        }.exportSnapshot()
        return exportSnapshot(accessToken, spreadsheetId, snapshot)
    }

    fun exportSnapshot(
        accessToken: String,
        spreadsheetId: String,
        snapshot: WonderFoodSnapshot,
    ): GoogleSheetsSnapshotSyncResult.Exported {
        val export = sheetsGateway.exportSnapshotRows(
            accessToken = accessToken,
            spreadsheetId = spreadsheetId,
            snapshot = snapshot,
            updatedAt = clock(),
        )
        return GoogleSheetsSnapshotSyncResult.Exported(
            spreadsheetId = export.spreadsheetId,
            rowCount = export.rowCount,
            tabs = export.tabs,
        )
    }

    fun readRemoteSnapshot(accessToken: String, spreadsheetId: String): GoogleSheetsSnapshotSyncResult {
        val rows = sheetsGateway.readSnapshotRows(accessToken, spreadsheetId)
        val snapshot = rows.firstNotNullOfOrNull(WonderFoodSnapshotCodec::decodeSnapshotRow)
            ?: return GoogleSheetsSnapshotSyncResult.NoSnapshot(spreadsheetId, rows.size)
        return GoogleSheetsSnapshotSyncResult.RemoteSnapshot(
            spreadsheetId = spreadsheetId,
            rowCount = rows.size,
            snapshot = snapshot,
        )
    }

    fun readRemoteWorkspaceDraft(accessToken: String, spreadsheetId: String): GoogleSheetsSnapshotSyncResult {
        val rows = sheetsGateway.readWorkspaceRows(accessToken, spreadsheetId)
        val draft = GoogleSheetsWorkspaceDraftImporter.toDraft(rows)
            ?: return GoogleSheetsSnapshotSyncResult.NoWorkspaceDraft(spreadsheetId, rows.size)
        return GoogleSheetsSnapshotSyncResult.RemoteWorkspaceDraft(
            spreadsheetId = spreadsheetId,
            rowCount = rows.size,
            draft = draft,
        )
    }

    fun readRemoteWorkspaceMerge(
        accessToken: String,
        spreadsheetId: String,
        baseSnapshot: WonderFoodSnapshot,
    ): GoogleSheetsSnapshotSyncResult {
        val rows = sheetsGateway.readWorkspaceRows(accessToken, spreadsheetId)
        if (rows.isEmpty()) return GoogleSheetsSnapshotSyncResult.NoWorkspaceDraft(spreadsheetId, rows.size)
        val merge = WonderFoodWorkspaceSnapshotMerger.merge(
            snapshot = baseSnapshot,
            rows = rows,
            updatedAt = clock(),
        )
        if (merge.changes.isEmpty() && merge.conflicts.isEmpty()) {
            return GoogleSheetsSnapshotSyncResult.NoWorkspaceDraft(spreadsheetId, rows.size)
        }
        return GoogleSheetsSnapshotSyncResult.RemoteWorkspaceMerge(
            spreadsheetId = spreadsheetId,
            rowCount = rows.size,
            merge = merge,
        )
    }
}

sealed interface GoogleSheetsSnapshotSyncResult {
    data class Exported(
        val spreadsheetId: String,
        val rowCount: Int,
        val tabs: List<String>,
    ) : GoogleSheetsSnapshotSyncResult

    data class RemoteSnapshot(
        val spreadsheetId: String,
        val rowCount: Int,
        val snapshot: WonderFoodSnapshot,
    ) : GoogleSheetsSnapshotSyncResult

    data class NoSnapshot(
        val spreadsheetId: String,
        val rowCount: Int,
    ) : GoogleSheetsSnapshotSyncResult

    data class RemoteWorkspaceDraft(
        val spreadsheetId: String,
        val rowCount: Int,
        val draft: com.wonderfood.app.data.FoodDraft,
    ) : GoogleSheetsSnapshotSyncResult

    data class RemoteWorkspaceMerge(
        val spreadsheetId: String,
        val rowCount: Int,
        val merge: WorkspaceMergeResult,
    ) : GoogleSheetsSnapshotSyncResult

    data class NoWorkspaceDraft(
        val spreadsheetId: String,
        val rowCount: Int,
    ) : GoogleSheetsSnapshotSyncResult
}
