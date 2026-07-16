package com.wonderfood.app.health

import android.content.Context
import androidx.activity.result.contract.ActivityResultContract
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.MealType
import androidx.health.connect.client.records.NutritionRecord
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.units.Energy
import androidx.health.connect.client.units.Mass
import com.wonderfood.app.data.MealLog
import java.time.Instant

class HealthConnectGateway(private val context: Context) {
    private val client: HealthConnectClient? by lazy {
        runCatching { HealthConnectClient.getOrCreate(context) }.getOrNull()
    }

    val nutritionWritePermissions: Set<String> =
        setOf(HealthPermission.getWritePermission(NutritionRecord::class))

    fun permissionContract(): ActivityResultContract<Set<String>, Set<String>> =
        PermissionController.createRequestPermissionResultContract()

    fun availabilityLabel(): String =
        when (HealthConnectClient.getSdkStatus(context)) {
            HealthConnectClient.SDK_AVAILABLE -> "Health Connect ready"
            HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> "Health Connect update needed"
            else -> "Health Connect unavailable"
        }

    suspend fun statusLabel(): String {
        if (HealthConnectClient.getSdkStatus(context) != HealthConnectClient.SDK_AVAILABLE) {
            return availabilityLabel()
        }
        val granted = runCatching { client?.permissionController?.getGrantedPermissions() }
            .getOrNull()
            .orEmpty()
        return if (nutritionWritePermissions.all { it in granted }) {
            "Health Connect connected"
        } else {
            "Health Connect ready"
        }
    }

    suspend fun exportMeal(meal: MealLog): HealthExportResult {
        if (HealthConnectClient.getSdkStatus(context) != HealthConnectClient.SDK_AVAILABLE) {
            return HealthExportResult.Unavailable
        }
        val healthClient = client ?: return HealthExportResult.Unavailable
        val granted = runCatching { healthClient.permissionController.getGrantedPermissions() }
            .getOrNull()
            .orEmpty()
        if (!nutritionWritePermissions.all { it in granted }) return HealthExportResult.MissingPermission

        val start = Instant.ofEpochMilli(meal.createdAtMillis)
        val record = NutritionRecord(
            startTime = start,
            endTime = start.plusSeconds(60),
            startZoneOffset = null,
            endZoneOffset = null,
            name = meal.title.take(120),
            mealType = MealType.MEAL_TYPE_UNKNOWN,
            energy = Energy.kilocalories(meal.calories.toDouble()),
            protein = Mass.grams(meal.proteinGrams),
            totalCarbohydrate = Mass.grams(meal.carbsGrams),
            totalFat = Mass.grams(meal.fatGrams),
            metadata = Metadata.manualEntry(clientRecordId = "wonderfood_meal_${meal.id}"),
        )

        return runCatching {
            healthClient.deleteRecords(
                recordType = NutritionRecord::class,
                recordIdsList = emptyList(),
                clientRecordIdsList = listOf("wonderfood_meal_${meal.id}"),
            )
            healthClient.insertRecords(listOf(record))
        }.fold(
            onSuccess = { HealthExportResult.Exported },
            onFailure = { HealthExportResult.Failed },
        )
    }
}

enum class HealthExportResult {
    Exported,
    MissingPermission,
    Unavailable,
    Failed,
}
