package com.wonderfood.app.sync

import android.content.Context
import android.os.Build
import com.wonderfood.app.data.FoodChatStore
import com.wonderfood.app.data.FoodMemory
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import org.json.JSONObject

class WonderFoodBackupGateway(private val context: Context) {
    private val backupDir: File
        get() = File(context.filesDir, "sync-backups").apply { mkdirs() }

    fun createEncryptedBackup(passphrase: String, memory: FoodMemory): BackupSnapshot {
        require(passphrase.length >= MIN_PASSPHRASE_LENGTH) {
            "Use at least $MIN_PASSPHRASE_LENGTH characters for cloud-ready backup encryption."
        }
        val databaseFile = context.getDatabasePath(DB_NAME)
        require(databaseFile.exists()) { "WonderFood database was not found yet." }
        val createdAt = Instant.now()
        val manifest = backupManifest(createdAt, memory, databaseFile)
        val zipBytes = zipBytes(
            entries = mapOf(
                MANIFEST_ENTRY to manifest.toString(2).toByteArray(Charsets.UTF_8),
                DB_ENTRY to databaseFile.readBytes(),
            ),
        )
        val encrypted = encrypt(zipBytes, passphrase)
        val timestamp = backupTimestamp(createdAt)
        val file = File(backupDir, "wonderfood-$timestamp.wfbackup")
        file.writeBytes(encrypted)
        File(backupDir, LATEST_BACKUP).writeBytes(encrypted)
        return BackupSnapshot(
            fileName = file.name,
            sizeBytes = file.length(),
            createdAtMillis = createdAt.toEpochMilli(),
            itemCount = memory.inventory.size + memory.groceries.size + memory.recipes.size + memory.mealLogs.size,
        )
    }

    fun createGoogleDriveBackup(memory: FoodMemory): GoogleDriveBackupPayload {
        val databaseFile = context.getDatabasePath(DB_NAME)
        require(databaseFile.exists()) { "WonderFood database was not found yet." }
        val createdAt = Instant.now()
        val manifest = backupManifest(createdAt, memory, databaseFile)
            .put("format", "wonderfood.cloud-backup.v1")
            .put("cloud_target", "google_drive_appDataFolder")
            .put("protection", "Google Account + Drive appDataFolder")
        val zipBytes = zipBytes(
            entries = mapOf(
                MANIFEST_ENTRY to manifest.toString(2).toByteArray(Charsets.UTF_8),
                DB_ENTRY to databaseFile.readBytes(),
            ),
        )
        val fileName = "wonderfood-${backupTimestamp(createdAt)}.wfcloudbackup"
        File(backupDir, LATEST_CLOUD_BACKUP).writeBytes(zipBytes)
        return GoogleDriveBackupPayload(
            fileName = fileName,
            bytes = zipBytes,
            snapshot = BackupSnapshot(
                fileName = fileName,
                sizeBytes = zipBytes.size.toLong(),
                createdAtMillis = createdAt.toEpochMilli(),
                itemCount = memory.inventory.size + memory.groceries.size + memory.recipes.size + memory.mealLogs.size,
            ),
        )
    }

    fun createRestoreSafetyBackup(memory: FoodMemory): BackupSnapshot {
        val payload = createGoogleDriveBackup(memory)
        val file = File(backupDir, "wonderfood-safety-before-restore-${backupTimestamp(Instant.now())}.wfcloudbackup")
        file.writeBytes(payload.bytes)
        return payload.snapshot.copy(fileName = file.name, sizeBytes = file.length())
    }

    fun createBackendSwitchSafetyBackup(
        memory: FoodMemory,
        fromLabel: String,
        toLabel: String,
    ): BackupSnapshot {
        val payload = createGoogleDriveBackup(memory)
        val timestamp = backupTimestamp(Instant.now())
        val file = File(backupDir, "wonderfood-safety-before-backend-switch-$timestamp.wfcloudbackup")
        file.writeBytes(payload.bytes)
        File(backupDir, LATEST_BACKEND_SWITCH_SAFETY).writeBytes(payload.bytes)
        File(backupDir, LATEST_BACKEND_SWITCH_SAFETY_MANIFEST).writeText(
            JSONObject()
                .put("from", fromLabel.ifBlank { "Unknown" })
                .put("to", toLabel.ifBlank { "Unknown" })
                .put("file_name", file.name)
                .put("created_at_millis", payload.snapshot.createdAtMillis)
                .put("size_bytes", file.length())
                .put("item_count", payload.snapshot.itemCount)
                .toString(),
        )
        return payload.snapshot.copy(fileName = file.name, sizeBytes = file.length())
    }

    fun restoreLatestEncryptedBackup(passphrase: String): BackupSnapshot {
        require(passphrase.length >= MIN_PASSPHRASE_LENGTH) {
            "Enter the backup passphrase used to create the backup."
        }
        val file = File(backupDir, LATEST_BACKUP)
        require(file.exists()) { "No local WonderFood backup found yet." }
        val zipBytes = decrypt(file.readBytes(), passphrase)
        val entries = unzipBytes(zipBytes)
        val dbBytes = entries[DB_ENTRY] ?: error("Backup is missing $DB_ENTRY.")
        val manifest = entries[MANIFEST_ENTRY]
            ?.toString(Charsets.UTF_8)
            ?.let(::JSONObject)
            ?: JSONObject()
        val tempDb = File(context.cacheDir, "wonderfood-restore-${System.currentTimeMillis()}.db")
        tempDb.writeBytes(dbBytes)
        val target = context.getDatabasePath(DB_NAME)
        target.parentFile?.mkdirs()
        tempDb.copyTo(target, overwrite = true)
        tempDb.delete()
        return BackupSnapshot(
            fileName = file.name,
            sizeBytes = file.length(),
            createdAtMillis = manifest.optLong("created_at_millis", 0L),
            itemCount = manifest.optJSONObject("counts")?.optInt("food_objects", 0) ?: 0,
        )
    }

    fun restoreGoogleDriveBackup(bytes: ByteArray, sourceName: String = LATEST_CLOUD_BACKUP): BackupSnapshot {
        require(bytes.isNotEmpty()) { "Google Drive backup was empty." }
        val entries = unzipBytes(bytes)
        val dbBytes = entries[DB_ENTRY] ?: error("Backup is missing $DB_ENTRY.")
        val manifest = entries[MANIFEST_ENTRY]
            ?.toString(Charsets.UTF_8)
            ?.let(::JSONObject)
            ?: JSONObject()
        require(manifest.optString("database", DB_ENTRY) == DB_ENTRY) {
            "Backup manifest points to an unsupported database file."
        }
        val tempDb = File(context.cacheDir, "wonderfood-cloud-restore-${System.currentTimeMillis()}.db")
        tempDb.writeBytes(dbBytes)
        val target = context.getDatabasePath(DB_NAME)
        target.parentFile?.mkdirs()
        tempDb.copyTo(target, overwrite = true)
        tempDb.delete()
        File(backupDir, LATEST_CLOUD_BACKUP).writeBytes(bytes)
        return BackupSnapshot(
            fileName = sourceName,
            sizeBytes = bytes.size.toLong(),
            createdAtMillis = manifest.optLong("created_at_millis", 0L),
            itemCount = manifest.optJSONObject("counts")?.optInt("food_objects", 0) ?: 0,
        )
    }

    fun previewGoogleDriveBackup(
        bytes: ByteArray,
        sourceName: String = LATEST_CLOUD_BACKUP,
        remoteModifiedTime: String = "",
        remoteSizeBytes: Long? = null,
    ): BackupManifestPreview {
        require(bytes.isNotEmpty()) { "Google Drive backup was empty." }
        val entries = unzipBytes(bytes)
        require(entries[DB_ENTRY] != null) { "Backup is missing $DB_ENTRY." }
        val manifest = entries[MANIFEST_ENTRY]
            ?.toString(Charsets.UTF_8)
            ?.let(::JSONObject)
            ?: JSONObject()
        require(manifest.optString("database", DB_ENTRY) == DB_ENTRY) {
            "Backup manifest points to an unsupported database file."
        }
        val counts = manifest.optJSONObject("counts") ?: JSONObject()
        return BackupManifestPreview(
            fileName = sourceName,
            modifiedTime = remoteModifiedTime,
            sizeBytes = remoteSizeBytes ?: bytes.size.toLong(),
            format = manifest.optString("format", "unknown"),
            schemaVersion = manifest.optInt("schema_version", 0),
            device = manifest.optString("device_model", "Unknown device"),
            createdAtMillis = manifest.optLong("created_at_millis", 0L),
            inventoryCount = counts.optInt("inventory", 0),
            groceryCount = counts.optInt("groceries", 0),
            recipeCount = counts.optInt("recipes", 0),
            mealCount = counts.optInt("meal_logs", 0),
            mealPlanCount = counts.optInt("meal_plans", 0),
            planEntryCount = counts.optInt("plan_entries", 0),
            messageCount = counts.optInt("messages", 0),
            itemCount = counts.optInt("food_objects", 0),
        )
    }

    fun latestBackupLabel(): String {
        val file = File(backupDir, LATEST_BACKUP)
        if (!file.exists()) return "No encrypted backup on this phone yet."
        return "Latest local backup: ${file.length() / 1024} KB"
    }

    fun latestCloudBackupLabel(): String {
        val file = File(backupDir, LATEST_CLOUD_BACKUP)
        if (!file.exists()) return "No Google backup cached on this phone yet."
        return "Latest Google backup cached: ${file.length() / 1024} KB"
    }

    fun latestBackendSwitchSafetyLabel(): String {
        val manifest = File(backupDir, LATEST_BACKEND_SWITCH_SAFETY_MANIFEST)
        if (!manifest.exists()) return ""
        val json = runCatching { JSONObject(manifest.readText()) }.getOrNull() ?: return ""
        val from = json.optString("from", "Unknown")
        val to = json.optString("to", "Unknown")
        val sizeKb = json.optLong("size_bytes", 0L) / 1024
        return "Rollback snapshot before $from -> $to: $sizeKb KB"
    }

    fun deleteLocalBackups() {
        backupDir.deleteRecursively()
    }

    private fun backupManifest(createdAt: Instant, memory: FoodMemory, databaseFile: File): JSONObject =
        JSONObject()
            .put("format", "wonderfood.backup.v1")
            .put("created_at", createdAt.toString())
            .put("created_at_millis", createdAt.toEpochMilli())
            .put("schema_version", FoodChatStore.SCHEMA_VERSION)
            .put("device_model", "${Build.MANUFACTURER} ${Build.MODEL}".trim())
            .put("android_sdk", Build.VERSION.SDK_INT)
            .put("database", DB_ENTRY)
            .put("database_sha256", databaseFile.readBytes().sha256Hex())
            .put(
                "counts",
                JSONObject()
                    .put("inventory", memory.inventory.size)
                    .put("groceries", memory.groceries.size)
                    .put("recipes", memory.recipes.size)
                    .put("meal_logs", memory.mealLogs.size)
                    .put("meal_plans", memory.mealPlans.size)
                    .put("plan_entries", memory.mealPlanEntries.size)
                    .put("messages", memory.messages.size)
                    .put("food_objects", memory.inventory.size + memory.groceries.size + memory.recipes.size + memory.mealLogs.size),
            )
            .put("secret_policy", "AI provider API keys are intentionally excluded from cross-device backup.")
            .put("future_cloud_target", "Google Drive appDataFolder with drive.appdata scope")

    private fun zipBytes(entries: Map<String, ByteArray>): ByteArray =
        ByteArrayOutputStream().use { output ->
            ZipOutputStream(output).use { zip ->
                entries.forEach { (name, bytes) ->
                    zip.putNextEntry(ZipEntry(name))
                    zip.write(bytes)
                    zip.closeEntry()
                }
            }
            output.toByteArray()
        }

    private fun unzipBytes(bytes: ByteArray): Map<String, ByteArray> {
        val result = mutableMapOf<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (!entry.isDirectory && entry.name in setOf(MANIFEST_ENTRY, DB_ENTRY)) {
                    result[entry.name] = zip.readBytes()
                }
                zip.closeEntry()
            }
        }
        return result
    }

    private fun encrypt(clearBytes: ByteArray, passphrase: String): ByteArray {
        val salt = ByteArray(SALT_BYTES).also(SecureRandom()::nextBytes)
        val iv = ByteArray(IV_BYTES).also(SecureRandom()::nextBytes)
        val key = keyFromPassphrase(passphrase, salt)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
        val encrypted = cipher.doFinal(clearBytes)
        return MAGIC + byteArrayOf(VERSION.toByte(), salt.size.toByte(), iv.size.toByte()) + salt + iv + encrypted
    }

    private fun decrypt(payload: ByteArray, passphrase: String): ByteArray {
        require(payload.size > MAGIC.size + 3 && payload.take(MAGIC.size).toByteArray().contentEquals(MAGIC)) {
            "Backup format is not recognized."
        }
        var offset = MAGIC.size
        val version = payload[offset++].toInt()
        require(version == VERSION) { "Backup version $version is not supported." }
        val saltSize = payload[offset++].toInt()
        val ivSize = payload[offset++].toInt()
        val salt = payload.copyOfRange(offset, offset + saltSize)
        offset += saltSize
        val iv = payload.copyOfRange(offset, offset + ivSize)
        offset += ivSize
        val encrypted = payload.copyOfRange(offset, payload.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, keyFromPassphrase(passphrase, salt), GCMParameterSpec(GCM_TAG_BITS, iv))
        return cipher.doFinal(encrypted)
    }

    private fun keyFromPassphrase(passphrase: String, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(passphrase.toCharArray(), salt, PBKDF2_ITERATIONS, KEY_BITS)
        val keyBytes = SecretKeyFactory.getInstance(PBKDF2).generateSecret(spec).encoded
        return SecretKeySpec(keyBytes, "AES")
    }

    private fun backupTimestamp(createdAt: Instant): String =
        DateTimeFormatter.ISO_INSTANT.format(createdAt)
            .replace(":", "-")
            .replace(".", "-")

    private fun ByteArray.sha256Hex(): String =
        MessageDigest.getInstance("SHA-256")
            .digest(this)
            .joinToString("") { "%02x".format(it) }

    companion object {
        private const val DB_NAME = "wonderfood.db"
        private const val DB_ENTRY = "wonderfood.db"
        private const val MANIFEST_ENTRY = "manifest.json"
        private const val LATEST_BACKUP = "wonderfood-latest.wfbackup"
        private const val LATEST_CLOUD_BACKUP = "wonderfood-latest.wfcloudbackup"
        private const val LATEST_BACKEND_SWITCH_SAFETY = "wonderfood-latest-backend-switch-safety.wfcloudbackup"
        private const val LATEST_BACKEND_SWITCH_SAFETY_MANIFEST = "wonderfood-latest-backend-switch-safety.json"
        private const val MIN_PASSPHRASE_LENGTH = 8
        private const val VERSION = 1
        private const val SALT_BYTES = 16
        private const val IV_BYTES = 12
        private const val KEY_BITS = 256
        private const val GCM_TAG_BITS = 128
        private const val PBKDF2_ITERATIONS = 120_000
        private const val PBKDF2 = "PBKDF2WithHmacSHA256"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private val MAGIC = byteArrayOf('W'.code.toByte(), 'F'.code.toByte(), 'B'.code.toByte(), '1'.code.toByte())
    }
}

data class BackupSnapshot(
    val fileName: String,
    val sizeBytes: Long,
    val createdAtMillis: Long,
    val itemCount: Int,
)

data class BackupManifestPreview(
    val fileName: String,
    val modifiedTime: String,
    val sizeBytes: Long,
    val format: String,
    val schemaVersion: Int,
    val device: String,
    val createdAtMillis: Long,
    val inventoryCount: Int,
    val groceryCount: Int,
    val recipeCount: Int,
    val mealCount: Int,
    val mealPlanCount: Int,
    val planEntryCount: Int,
    val messageCount: Int,
    val itemCount: Int,
)
