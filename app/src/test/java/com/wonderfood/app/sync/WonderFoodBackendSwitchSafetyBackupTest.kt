package com.wonderfood.app.sync

import androidx.test.core.app.ApplicationProvider
import com.wonderfood.app.data.FoodChatStore
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class WonderFoodBackendSwitchSafetyBackupTest {
    @Test
    fun createsLatestBackendSwitchSafetyLabel() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val store = FoodChatStore(context)
        val gateway = WonderFoodBackupGateway(context)

        val snapshot = gateway.createBackendSwitchSafetyBackup(
            memory = store.readMemory(),
            fromLabel = "On this phone",
            toLabel = "Google Sheets",
        )

        assertTrue(snapshot.fileName.startsWith("wonderfood-safety-before-backend-switch-"))
        assertTrue(gateway.latestBackendSwitchSafetyLabel().contains("On this phone -> Google Sheets"))
    }
}
