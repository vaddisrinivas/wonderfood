package com.wonderfood.app.sync

data class GoogleAccountProfile(
    val email: String,
    val displayName: String,
)

data class GoogleDriveAccess(
    val accessToken: String,
    val accountEmail: String,
)

data class GoogleDriveBackupPayload(
    val fileName: String,
    val bytes: ByteArray,
    val snapshot: BackupSnapshot,
) {
    val sizeBytes: Int get() = bytes.size
}

data class GoogleDriveBackupFile(
    val id: String,
    val name: String,
    val modifiedTime: String,
    val sizeBytes: Long?,
)

data class GoogleDriveBackupDownload(
    val remoteFile: GoogleDriveBackupFile,
    val bytes: ByteArray,
)
