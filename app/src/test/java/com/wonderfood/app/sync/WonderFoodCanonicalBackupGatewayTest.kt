package com.wonderfood.app.sync

import androidx.test.core.app.ApplicationProvider
import com.wonderfood.core.data.room.WonderFoodDatabaseFactory
import com.wonderfood.core.model.household.DataHomeKind
import com.wonderfood.core.model.household.EntityId
import com.wonderfood.core.model.household.EntityMetadata
import com.wonderfood.core.model.household.Household
import com.wonderfood.core.model.household.HouseholdId
import com.wonderfood.core.model.household.HouseholdSnapshot
import com.wonderfood.core.model.household.HouseholdWorkspaceContract
import com.wonderfood.core.model.household.Item
import com.wonderfood.core.model.household.ItemKind
import com.wonderfood.core.model.household.QuantityUnit
import com.wonderfood.core.model.household.SourceKind
import com.wonderfood.core.model.household.SourceRef
import com.wonderfood.core.model.household.UtcTimestamp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class WonderFoodCanonicalBackupGatewayTest {
    @Test
    fun encryptedBackupUsesCanonicalHouseholdDatabaseAndCounts() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val gateway = WonderFoodBackupGateway(context)
        val canonicalDb = writeCanonicalDb("canonical-db-proof")

        val backup = gateway.createEncryptedBackup("correct horse", snapshot())
        canonicalDb.writeText("stale-db")

        val restored = gateway.restoreLatestEncryptedBackup("correct horse")

        assertEquals(WonderFoodDatabaseFactory.DATABASE_NAME, backup.databaseName)
        assertEquals(WonderFoodDatabaseFactory.DATABASE_NAME, restored.databaseName)
        assertEquals(1, restored.itemCount)
        assertEquals("canonical-db-proof", canonicalDb.readText())
        assertTrue(backup.fileName.endsWith(".wfbackup"))
    }

    @Test
    fun googleDriveBackupAndSafetyBackupsUseCanonicalDatabase() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val gateway = WonderFoodBackupGateway(context)
        val canonicalDb = writeCanonicalDb("canonical-cloud-proof")

        val payload = gateway.createGoogleDriveBackup(snapshot())
        canonicalDb.writeText("stale-cloud-db")
        val restored = gateway.restoreGoogleDriveBackup(payload.bytes, payload.fileName)

        assertEquals(WonderFoodDatabaseFactory.DATABASE_NAME, payload.snapshot.databaseName)
        assertEquals(WonderFoodDatabaseFactory.DATABASE_NAME, restored.databaseName)
        assertEquals(1, restored.itemCount)
        assertEquals("canonical-cloud-proof", canonicalDb.readText())

        val preview = gateway.previewGoogleDriveBackup(payload.bytes, payload.fileName)
        assertEquals("wonderfood.household-cloud-backup.v105", preview.format)
        assertEquals(1, preview.itemCount)

        val restoreSafety = gateway.createRestoreSafetyBackup(snapshot())
        assertEquals(WonderFoodDatabaseFactory.DATABASE_NAME, restoreSafety.databaseName)
        assertTrue(restoreSafety.fileName.startsWith("wonderfood-safety-before-restore-"))

        val switchSafety = gateway.createBackendSwitchSafetyBackup(
            snapshot = snapshot(),
            fromLabel = "On this phone",
            toLabel = "Google Sheets",
        )
        assertEquals(WonderFoodDatabaseFactory.DATABASE_NAME, switchSafety.databaseName)
        assertTrue(gateway.latestBackendSwitchSafetyLabel().contains("On this phone -> Google Sheets"))
    }

    private fun writeCanonicalDb(contents: String) =
        ApplicationProvider
            .getApplicationContext<android.content.Context>()
            .getDatabasePath(WonderFoodDatabaseFactory.DATABASE_NAME)
            .also {
                it.parentFile?.mkdirs()
                it.writeText(contents)
            }

    private fun snapshot(): HouseholdSnapshot {
        val householdId = HouseholdId("00000000-0000-0000-0000-000000000105")
        val now = UtcTimestamp(1)
        return HouseholdSnapshot(
            household = Household(
                id = householdId,
                name = "My household",
                defaultCurrency = "USD",
                timezone = "America/New_York",
                locale = "en-US",
                activeDataHome = DataHomeKind.LOCAL,
                schemaVersion = HouseholdWorkspaceContract.SCHEMA_VERSION,
                createdAt = now,
                updatedAt = now,
                revision = 1,
            ),
            items = listOf(
                Item(
                    metadata = EntityMetadata(
                        id = EntityId("00000000-0000-0000-0000-000000000777"),
                        householdId = householdId,
                        createdAt = now,
                        updatedAt = now,
                        revision = 1,
                        source = SourceRef(SourceKind.MANUAL, "test"),
                    ),
                    name = "Dish soap",
                    kind = ItemKind.CLEANING,
                    category = "Cleaning",
                    defaultUnit = QuantityUnit.EACH,
                ),
            ),
        )
    }
}
