package com.wonderfood.core.data.backend

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BackendOnboardingTest {
    @Test
    fun firstRunStartsBeforeProviderSelection() {
        val state = BackendOnboardingState.firstRun()

        assertEquals(BackendOnboardingStep.VALUE_PROMISE, state.step)
        assertNull(state.selectedType)
        assertNull(state.activeConfiguration)
        assertNull(state.connectionResult)
        assertNull(state.foundData)
    }

    @Test
    fun providerTypesMapToTheirSetupScreens() {
        assertEquals(BackendOnboardingStep.CONFIGURE_LOCAL, BackendType.LOCAL_SQLITE.configureStep())
        assertEquals(BackendOnboardingStep.CONFIGURE_GOOGLE_SHEETS, BackendType.GOOGLE_SHEETS.configureStep())
        assertEquals(BackendOnboardingStep.CONFIGURE_NOTION, BackendType.NOTION.configureStep())
        assertEquals(BackendOnboardingStep.CONFIGURE_POSTGRES, BackendType.POSTGRES.configureStep())
    }
}
