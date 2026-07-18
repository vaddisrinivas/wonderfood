package com.wonderfood.app.health

import android.content.Context
import android.content.Intent
import androidx.activity.result.contract.ActivityResultContract
import com.wonderfood.app.data.MealLog
import java.time.LocalDate

class HealthConnectGateway(private val context: Context) {
    val healthPermissions: Set<String> = emptySet()
    val nutritionWritePermissions: Set<String> = emptySet()

    fun permissionContract(): ActivityResultContract<Set<String>, Set<String>> =
        object : ActivityResultContract<Set<String>, Set<String>>() {
            override fun createIntent(context: Context, input: Set<String>): Intent =
                Intent("${context.packageName}.NO_HEALTH_CONNECT_PERMISSION_REQUEST")

            override fun parseResult(resultCode: Int, intent: Intent?): Set<String> =
                emptySet()
        }

    fun availabilityLabel(): String =
        "Health Connect is not included in the FOSS build"

    suspend fun statusLabel(): String =
        availabilityLabel()

    suspend fun dailySummary(date: LocalDate = LocalDate.now()): HealthDailySummary =
        HealthDailySummary(label = availabilityLabel())

    fun settingsIntent(): Intent =
        Intent("${context.packageName}.NO_HEALTH_CONNECT_SETTINGS")

    fun hasHealthConnect(): Boolean =
        false

    fun hasAllPermissions(granted: Set<String>): Boolean =
        false

    suspend fun grantedPermissions(): Set<String> =
        emptySet()

    fun requiredPermissionsLabel(): String =
        "Not available in the FOSS build"

    fun canWriteNutrition(granted: Set<String>): Boolean =
        false

    fun permissionsToRequest(): Set<String> =
        emptySet()

    fun statusFromSummary(summary: HealthDailySummary): String =
        summary.label

    suspend fun exportMeal(meal: MealLog): HealthExportResult =
        HealthExportResult.Unavailable
}

data class HealthDailySummary(
    val isAvailable: Boolean = false,
    val isConnected: Boolean = false,
    val grantedPermissions: Int = 0,
    val requestedPermissions: Int = 0,
    val steps: Long? = null,
    val activeCaloriesKcal: Int? = null,
    val totalCaloriesBurnedKcal: Int? = null,
    val nutritionCaloriesKcal: Int? = null,
    val nutritionProteinGrams: Int? = null,
    val nutritionCarbsGrams: Int? = null,
    val nutritionFatGrams: Int? = null,
    val label: String = "Health Connect is not included in the FOSS build",
)

enum class HealthExportResult {
    Exported,
    MissingNutrition,
    MissingPermission,
    Unavailable,
    Failed,
}
