package com.wonderfood.core.data.backend

public data class BackendOnboardingState(
    val step: BackendOnboardingStep,
    val selectedType: BackendType?,
    val activeConfiguration: BackendConfig?,
    val connectionResult: ConnectionResult?,
    val foundData: SnapshotSummary?,
) {
    public companion object {
        public fun firstRun(): BackendOnboardingState =
            BackendOnboardingState(
                step = BackendOnboardingStep.VALUE_PROMISE,
                selectedType = null,
                activeConfiguration = null,
                connectionResult = null,
                foundData = null,
            )
    }
}

public enum class BackendOnboardingStep {
    VALUE_PROMISE,
    CHOOSE_DATA_HOME,
    CONFIGURE_LOCAL,
    CONFIGURE_GOOGLE_SHEETS,
    CONFIGURE_NOTION,
    CONFIGURE_POSTGRES,
    EXISTING_DATA_PREVIEW,
    READY,
}

public fun BackendType.configureStep(): BackendOnboardingStep =
    when (this) {
        BackendType.LOCAL_SQLITE -> BackendOnboardingStep.CONFIGURE_LOCAL
        BackendType.GOOGLE_SHEETS -> BackendOnboardingStep.CONFIGURE_GOOGLE_SHEETS
        BackendType.NOTION -> BackendOnboardingStep.CONFIGURE_NOTION
        BackendType.POSTGRES -> BackendOnboardingStep.CONFIGURE_POSTGRES
    }
