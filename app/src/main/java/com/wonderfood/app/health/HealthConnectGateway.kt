package com.wonderfood.app.health

import android.content.Context
import androidx.activity.result.contract.ActivityResultContract
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.MealType
import androidx.health.connect.client.records.NutritionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.health.connect.client.units.Energy
import androidx.health.connect.client.units.Mass
import com.wonderfood.app.data.MealLog
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.roundToInt

class HealthConnectGateway(private val context: Context) {
    private val client: HealthConnectClient? by lazy {
        runCatching { HealthConnectClient.getOrCreate(context) }.getOrNull()
    }

    val healthPermissions: Set<String> =
        setOf(
            HealthPermission.getReadPermission(StepsRecord::class),
            HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class),
            HealthPermission.getReadPermission(TotalCaloriesBurnedRecord::class),
            HealthPermission.getReadPermission(NutritionRecord::class),
            HealthPermission.getWritePermission(NutritionRecord::class),
        )

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
        val summary = dailySummary()
        return summary.label
    }

    suspend fun dailySummary(date: LocalDate = LocalDate.now()): HealthDailySummary {
        if (HealthConnectClient.getSdkStatus(context) != HealthConnectClient.SDK_AVAILABLE) {
            return HealthDailySummary(label = availabilityLabel())
        }
        val healthClient = client ?: return HealthDailySummary(isAvailable = true, label = "Health Connect unavailable")
        val granted = runCatching { client?.permissionController?.getGrantedPermissions() }
            .getOrNull()
            .orEmpty()
        val summary = HealthDailySummary(
            isAvailable = true,
            isConnected = healthPermissions.all { it in granted },
            grantedPermissions = granted.count { it in healthPermissions },
            requestedPermissions = healthPermissions.size,
            label = if (healthPermissions.all { it in granted }) {
                "Health Connect automatic"
            } else {
                "Health Connect needs one-time permission"
            },
        )
        val zone = ZoneId.systemDefault()
        val start = date.atStartOfDay(zone).toInstant()
        val end = date.plusDays(1).atStartOfDay(zone).toInstant()
        val metrics = buildSet {
            if (HealthPermission.getReadPermission(StepsRecord::class) in granted) add(StepsRecord.COUNT_TOTAL)
            if (HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class) in granted) add(ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL)
            if (HealthPermission.getReadPermission(TotalCaloriesBurnedRecord::class) in granted) add(TotalCaloriesBurnedRecord.ENERGY_TOTAL)
            if (HealthPermission.getReadPermission(NutritionRecord::class) in granted) {
                add(NutritionRecord.ENERGY_TOTAL)
                add(NutritionRecord.PROTEIN_TOTAL)
                add(NutritionRecord.TOTAL_CARBOHYDRATE_TOTAL)
                add(NutritionRecord.TOTAL_FAT_TOTAL)
            }
        }
        if (metrics.isEmpty()) return summary
        return runCatching {
            healthClient.aggregate(
                AggregateRequest(
                    metrics = metrics,
                    timeRangeFilter = TimeRangeFilter.between(start, end),
                ),
            )
        }.fold(
            onSuccess = { result ->
                summary.copy(
                    steps = result[StepsRecord.COUNT_TOTAL],
                    activeCaloriesKcal = result[ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL]?.inKilocalories?.roundToInt(),
                    totalCaloriesBurnedKcal = result[TotalCaloriesBurnedRecord.ENERGY_TOTAL]?.inKilocalories?.roundToInt(),
                    nutritionCaloriesKcal = result[NutritionRecord.ENERGY_TOTAL]?.inKilocalories?.roundToInt(),
                    nutritionProteinGrams = result[NutritionRecord.PROTEIN_TOTAL]?.inGrams?.roundToInt(),
                    nutritionCarbsGrams = result[NutritionRecord.TOTAL_CARBOHYDRATE_TOTAL]?.inGrams?.roundToInt(),
                    nutritionFatGrams = result[NutritionRecord.TOTAL_FAT_TOTAL]?.inGrams?.roundToInt(),
                )
            },
            onFailure = {
                summary.copy(label = "Health Connect connected, read failed")
            },
        )
    }

    fun settingsIntent() =
        HealthConnectClient.getHealthConnectManageDataIntent(context)

    fun hasHealthConnect(): Boolean =
        HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE

    fun hasAllPermissions(granted: Set<String>): Boolean =
        healthPermissions.all { it in granted }

    suspend fun grantedPermissions(): Set<String> =
        runCatching { client?.permissionController?.getGrantedPermissions() }
            .getOrNull()
            .orEmpty()

    fun requiredPermissionsLabel(): String =
        "Steps, active calories, total calories, nutrition read, nutrition write"

    fun canWriteNutrition(granted: Set<String>): Boolean =
        nutritionWritePermissions.all { it in granted }

    fun permissionsToRequest(): Set<String> =
        healthPermissions

    fun statusFromSummary(summary: HealthDailySummary): String =
        when {
            !summary.isAvailable -> summary.label
            summary.isConnected -> "Health Connect automatic"
            else -> "Health Connect needs one-time permission"
        }

    suspend fun exportMeal(meal: MealLog): HealthExportResult {
        val calories = meal.calories ?: return HealthExportResult.MissingNutrition
        val protein = meal.proteinGrams ?: return HealthExportResult.MissingNutrition
        val carbs = meal.carbsGrams ?: return HealthExportResult.MissingNutrition
        val fat = meal.fatGrams ?: return HealthExportResult.MissingNutrition
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
            energy = Energy.kilocalories(calories.toDouble()),
            protein = Mass.grams(protein),
            totalCarbohydrate = Mass.grams(carbs),
            totalFat = Mass.grams(fat),
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

data class HealthDailySummary(
    val isAvailable: Boolean = false,
    val isConnected: Boolean = false,
    val grantedPermissions: Int = 0,
    val requestedPermissions: Int = 5,
    val steps: Long? = null,
    val activeCaloriesKcal: Int? = null,
    val totalCaloriesBurnedKcal: Int? = null,
    val nutritionCaloriesKcal: Int? = null,
    val nutritionProteinGrams: Int? = null,
    val nutritionCarbsGrams: Int? = null,
    val nutritionFatGrams: Int? = null,
    val label: String = "Checking Health Connect",
)

enum class HealthExportResult {
    Exported,
    MissingNutrition,
    MissingPermission,
    Unavailable,
    Failed,
}
