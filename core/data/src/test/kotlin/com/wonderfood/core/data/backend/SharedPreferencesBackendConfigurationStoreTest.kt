package com.wonderfood.core.data.backend

import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SharedPreferencesBackendConfigurationStoreTest {
    private lateinit var store: SharedPreferencesBackendConfigurationStore

    @Before
    fun setUp() = runTest {
        store = SharedPreferencesBackendConfigurationStore(ApplicationProvider.getApplicationContext())
        store.clearActiveConfiguration()
        store.setOnboardingDismissed(false)
    }

    @Test
    fun startsWithoutActiveConfiguration() = runTest {
        assertNull(store.activeConfiguration())
    }

    @Test
    fun persistsLocalBackendConfiguration() = runTest {
        store.saveActiveConfiguration(LocalSqliteConfig())

        assertEquals(BackendType.LOCAL_SQLITE, store.activeConfiguration()?.type)
    }

    @Test
    fun persistsGoogleSheetsConfigurationWithoutSecretValue() = runTest {
        store.saveActiveConfiguration(
            GoogleSheetsConfig(
                spreadsheetUrl = "https://docs.google.com/spreadsheets/d/sheet12345678901234567890/edit",
                spreadsheetId = "sheet12345678901234567890",
                accountEmail = "user@example.com",
                credentialRef = CredentialRef(BackendType.GOOGLE_SHEETS, "google-sheets-primary"),
            ),
        )

        val config = store.activeConfiguration() as GoogleSheetsConfig
        assertEquals(BackendType.GOOGLE_SHEETS, config.type)
        assertEquals("sheet12345678901234567890", config.spreadsheetId)
        assertEquals("google-sheets-primary", config.credentialRef.alias)
    }

    @Test
    fun persistsOnboardingDismissalSeparatelyFromProviderConfig() = runTest {
        store.setOnboardingDismissed(true)

        assertTrue(store.onboardingDismissed())
        assertNull(store.activeConfiguration())
    }
}
