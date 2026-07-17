package com.wonderfood.core.ai

import com.wonderfood.core.model.Confidence as DomainConfidence
import com.wonderfood.core.model.EntityRef
import com.wonderfood.core.model.EntityType
import com.wonderfood.core.model.FoodUnit
import com.wonderfood.core.model.IsoTimestamp
import com.wonderfood.core.model.NutritionBasisType
import com.wonderfood.core.model.NutritionValues
import com.wonderfood.core.model.Quantity
import com.wonderfood.core.model.ServingBasis
import com.wonderfood.core.model.SourceKind
import com.wonderfood.core.model.TruthState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NutritionProviderChainTest {
    @Test
    fun userCorrectionWinsOverLabelAndExternalProviders() {
        val correction = snapshot(
            sourceType = NutritionSourceType.USER_CORRECTION,
            energyKcal = 111.0,
            corrected = true,
            lookupDerived = false,
            state = TruthState.USER_CONFIRMED,
            sourceId = "user-correction:yogurt",
            label = "User correction",
        )
        val chain = NutritionProviderChain.default(
            localLookupStore = InMemoryNutritionLocalLookupStore(
                userCorrections = mapOf("generic yogurt" to correction),
            ),
            openFoodFactsClient = FakeOpenFoodFactsNutritionClient(
                productsByBarcode = mapOf(
                    "1234567890123" to openFoodFactsProduct("1234567890123", energyKcal = 999.0),
                ),
            ),
            clock = CLOCK,
        )

        val result = chain.resolve(
            NutritionResolutionRequest(
                subject = FOOD_SUBJECT,
                foodName = "Generic Yogurt",
                barcode = "1234567890123",
                packageLabel = labelInput(energyKcal = 140.0),
            ),
        )

        assertTrue(result.isKnown)
        assertEquals(111.0, result.snapshot?.values?.energyKcal)
        assertEquals(NutritionSourceType.USER_CORRECTION, result.provenance?.sourceType)
        assertTrue(result.provenance?.corrected == true)
        assertFalse(result.provenance?.estimated == true)
        assertEquals(listOf("user_corrected_local"), result.attempts.map { it.providerId })
    }

    @Test
    fun packageLabelRecordsSourceBasisConfidenceTimestampAndUnknownFields() {
        val label = labelInput(
            energyKcal = 140.0,
            proteinGrams = 18.0,
            carbohydrateGrams = null,
            fatGrams = null,
        )
        val result = NutritionProviderChain.default(clock = CLOCK).resolve(
            NutritionResolutionRequest(
                subject = FOOD_SUBJECT,
                packageLabel = label,
            ),
        )

        assertTrue(result.isKnown)
        assertEquals("nutrition-label:generic-yogurt-label", result.provenance?.sourceId)
        assertEquals(NutritionSourceType.PACKAGE_LABEL, result.provenance?.sourceType)
        assertEquals(NutritionBasisType.PER_SERVING, result.provenance?.basis?.type)
        assertEquals(0.92, result.provenance?.confidence?.score)
        assertEquals(NOW, result.provenance?.capturedAt)
        assertFalse(result.provenance?.corrected == true)
        assertFalse(result.provenance?.estimated == true)
        assertFalse(result.provenance?.lookupDerived == true)
        assertEquals(SourceKind.USER, result.snapshot?.source?.kind)
        assertEquals(NOW, result.snapshot?.capturedAt)
        assertNull(result.snapshot?.values?.carbohydrateGrams)
        assertNull(result.snapshot?.values?.fatGrams)
    }

    @Test
    fun openFoodFactsPrecedesUsdaRecipeAndAiWhenBarcodeLookupMatches() {
        val chain = NutritionProviderChain.default(
            openFoodFactsClient = FakeOpenFoodFactsNutritionClient(
                productsByBarcode = mapOf(
                    "1234567890123" to openFoodFactsProduct("1234567890123", energyKcal = 120.0),
                ),
            ),
            usdaClient = usdaClient("generic yogurt", fdcId = "1001", energyKcal = 220.0),
            recipeCalculator = StaticRecipeNutritionCalculator(
                snapshotsByRecipeId = mapOf("recipe-1" to recipeSnapshot(energyKcal = 330.0)),
            ),
            aiEstimator = StaticAiNutritionEstimator(
                estimatesByFoodName = mapOf("generic yogurt" to aiSnapshot(energyKcal = 440.0)),
            ),
            clock = CLOCK,
        )

        val result = chain.resolve(
            NutritionResolutionRequest(
                subject = FOOD_SUBJECT,
                foodName = "generic yogurt",
                barcode = "1234567890123",
                recipe = RecipeNutritionInput(
                    recipeId = "recipe-1",
                    title = "Generic Yogurt Bowl",
                    servings = 1.0,
                    ingredientSnapshots = emptyList(),
                ),
                allowAiEstimate = true,
            ),
        )

        assertTrue(result.isKnown)
        assertEquals(NutritionSourceType.OPEN_FOOD_FACTS, result.provenance?.sourceType)
        assertEquals(120.0, result.snapshot?.values?.energyKcal)
        assertTrue(result.provenance?.lookupDerived == true)
        assertEquals(
            listOf(
                "user_corrected_local",
                "package_label",
                "open_food_facts_barcode",
            ),
            result.attempts.map { it.providerId },
        )
    }

    @Test
    fun unknownSurvivesProviderFailureWithoutFallbackEstimate() {
        val chain = NutritionProviderChain.default(
            openFoodFactsClient = FakeOpenFoodFactsNutritionClient(
                failuresByBarcode = mapOf("1234567890123" to "fixture network failure"),
            ),
            clock = CLOCK,
        )

        val result = chain.resolve(
            NutritionResolutionRequest(
                subject = FOOD_SUBJECT,
                barcode = "1234567890123",
                allowAiEstimate = false,
            ),
        )

        assertEquals(NutritionResolutionStatus.UNKNOWN, result.status)
        assertNull(result.snapshot)
        assertNull(result.provenance)
        assertTrue(
            result.attempts.any {
                it.providerId == "open_food_facts_barcode" &&
                    it.status == NutritionProviderAttemptStatus.FAILURE
            },
        )
    }

    @Test
    fun offlineWithoutCacheSkipsNetworkProvidersAndReturnsUnknown() {
        val chain = NutritionProviderChain.default(
            openFoodFactsClient = FakeOpenFoodFactsNutritionClient(
                productsByBarcode = mapOf(
                    "1234567890123" to openFoodFactsProduct("1234567890123", energyKcal = 120.0),
                ),
            ),
            clock = CLOCK,
        )

        val result = chain.resolve(
            NutritionResolutionRequest(
                subject = FOOD_SUBJECT,
                barcode = "1234567890123",
                isOnline = false,
            ),
        )

        assertEquals(NutritionResolutionStatus.UNKNOWN, result.status)
        assertNull(result.snapshot)
        assertTrue(
            result.attempts.any {
                it.providerId == "open_food_facts_barcode" &&
                    it.status == NutritionProviderAttemptStatus.OFFLINE_SKIPPED
            },
        )
    }

    @Test
    fun offlineCanUseKnownCacheBeforeNetworkSkip() {
        val cache = InMemoryNutritionProviderCache()
        cache.putKnown(
            key = NutritionCacheKey("open_food_facts_barcode", "barcode:1234567890123"),
            snapshot = offSnapshot(barcode = "1234567890123", energyKcal = 120.0),
            storedAt = NOW,
        )
        val chain = NutritionProviderChain.default(
            cache = cache,
            openFoodFactsClient = FakeOpenFoodFactsNutritionClient(),
            clock = CLOCK,
        )

        val result = chain.resolve(
            NutritionResolutionRequest(
                subject = FOOD_SUBJECT,
                barcode = "1234567890123",
                isOnline = false,
            ),
        )

        assertTrue(result.isKnown)
        assertEquals(NutritionSourceType.OPEN_FOOD_FACTS, result.provenance?.sourceType)
        assertEquals(NutritionProviderAttemptStatus.CACHE_HIT, result.attempts.last().status)
        assertTrue(result.attempts.last().fromCache)
    }

    @Test
    fun rateLimitedProviderIsSkippedAndNextProviderCanResolve() {
        val chain = NutritionProviderChain.default(
            openFoodFactsClient = FakeOpenFoodFactsNutritionClient(
                productsByBarcode = mapOf(
                    "1234567890123" to openFoodFactsProduct("1234567890123", energyKcal = 120.0),
                ),
            ),
            usdaClient = usdaClient("generic yogurt", fdcId = "1001", energyKcal = 150.0),
            rateLimitState = DenyProviderRateLimitState("open_food_facts_barcode"),
            clock = CLOCK,
        )

        val result = chain.resolve(
            NutritionResolutionRequest(
                subject = FOOD_SUBJECT,
                foodName = "Generic Yogurt",
                barcode = "1234567890123",
            ),
        )

        assertTrue(result.isKnown)
        assertEquals(NutritionSourceType.USDA, result.provenance?.sourceType)
        assertEquals(150.0, result.snapshot?.values?.energyKcal)
        assertTrue(
            result.attempts.any {
                it.providerId == "open_food_facts_barcode" &&
                    it.status == NutritionProviderAttemptStatus.RATE_LIMITED
            },
        )
    }

    @Test
    fun aiEstimateFallbackRequiresExplicitPermissionAndIsFlaggedEstimated() {
        val estimator = StaticAiNutritionEstimator(
            estimatesByFoodName = mapOf("generic yogurt" to aiSnapshot(energyKcal = 130.0)),
        )
        val disabled = NutritionProviderChain.default(aiEstimator = estimator, clock = CLOCK).resolve(
            NutritionResolutionRequest(
                subject = FOOD_SUBJECT,
                foodName = "Generic Yogurt",
                allowAiEstimate = false,
            ),
        )
        val enabled = NutritionProviderChain.default(aiEstimator = estimator, clock = CLOCK).resolve(
            NutritionResolutionRequest(
                subject = FOOD_SUBJECT,
                foodName = "Generic Yogurt",
                allowAiEstimate = true,
            ),
        )

        assertEquals(NutritionResolutionStatus.UNKNOWN, disabled.status)
        assertTrue(enabled.isKnown)
        assertEquals(NutritionSourceType.AI_ESTIMATE, enabled.provenance?.sourceType)
        assertTrue(enabled.provenance?.estimated == true)
        assertFalse(enabled.provenance?.corrected == true)
        assertEquals(TruthState.ESTIMATED, enabled.snapshot?.truthState)
    }

    private class DenyProviderRateLimitState(
        private val deniedProviderId: String,
    ) : NutritionRateLimitState {
        override fun tryAcquire(
            providerId: String,
            nowEpochMillis: Long,
        ): NutritionRateLimitDecision =
            if (providerId == deniedProviderId) {
                NutritionRateLimitDecision(allowed = false, retryAfterMillis = 1_000)
            } else {
                NutritionRateLimitDecision(allowed = true)
            }
    }

    private companion object {
        val NOW = IsoTimestamp("2026-01-15T12:00:00Z")
        val CLOCK = FixedNutritionClock(NOW, epochMillis = 1000L)
        val FOOD_SUBJECT = EntityRef(EntityType.FOOD, "food-generic-yogurt")

        fun basis(
            type: NutritionBasisType = NutritionBasisType.PER_SERVING,
            description: String = "170 g",
        ): ServingBasis =
            ServingBasis(
                type = type,
                quantity = Quantity(
                    amount = 170.0,
                    unit = FoodUnit.GRAM,
                    truthState = TruthState.PROVIDER_CONFIRMED,
                ),
                description = description,
            )

        fun values(
            energyKcal: Double?,
            proteinGrams: Double? = null,
            carbohydrateGrams: Double? = null,
            fatGrams: Double? = null,
        ): NutritionValues =
            NutritionValues(
                energyKcal = energyKcal,
                proteinGrams = proteinGrams,
                carbohydrateGrams = carbohydrateGrams,
                fatGrams = fatGrams,
                fiberGrams = null,
                sugarGrams = null,
                sodiumMilligrams = null,
            )

        fun labelInput(
            energyKcal: Double?,
            proteinGrams: Double? = 18.0,
            carbohydrateGrams: Double? = null,
            fatGrams: Double? = null,
        ): PackageNutritionLabelInput =
            PackageNutritionLabelInput(
                labelId = "generic-yogurt-label",
                foodName = "Generic Plain Yogurt",
                brand = null,
                basis = basis(),
                values = values(
                    energyKcal = energyKcal,
                    proteinGrams = proteinGrams,
                    carbohydrateGrams = carbohydrateGrams,
                    fatGrams = fatGrams,
                ),
                confidenceScore = 0.92,
                observedAt = NOW,
            )

        fun snapshot(
            sourceType: NutritionSourceType,
            energyKcal: Double?,
            corrected: Boolean,
            lookupDerived: Boolean,
            state: TruthState,
            sourceId: String,
            label: String,
            estimated: Boolean = sourceType == NutritionSourceType.AI_ESTIMATE,
        ): ProviderNutritionSnapshot {
            val servingBasis = basis()
            return ProviderNutritionSnapshot(
                basis = servingBasis,
                values = values(energyKcal = energyKcal),
                provenance = NutritionProvenance(
                    sourceId = sourceId,
                    sourceType = sourceType,
                    sourceLabel = label,
                    basis = servingBasis,
                    confidence = DomainConfidence(
                        score = 0.95,
                        state = state,
                        rationale = "$label fixture.",
                    ),
                    capturedAt = NOW,
                    corrected = corrected,
                    estimated = estimated,
                    lookupDerived = lookupDerived,
                    externalId = sourceId.substringAfter(':', sourceId),
                    uri = null,
                ),
            )
        }

        fun offSnapshot(
            barcode: String,
            energyKcal: Double?,
        ): ProviderNutritionSnapshot =
            snapshot(
                sourceType = NutritionSourceType.OPEN_FOOD_FACTS,
                energyKcal = energyKcal,
                corrected = false,
                lookupDerived = true,
                state = TruthState.PROVIDER_CONFIRMED,
                sourceId = "open-food-facts:$barcode",
                label = "Open Food Facts",
            )

        fun openFoodFactsProduct(
            barcode: String,
            energyKcal: Double?,
        ): OpenFoodFactsProduct =
            OpenFoodFactsProduct(
                barcode = barcode,
                foodName = "Generic Yogurt",
                basis = basis(type = NutritionBasisType.PER_100_GRAMS, description = "100 g"),
                values = values(energyKcal = energyKcal),
                confidenceScore = 0.86,
                observedAt = NOW,
                uri = "https://world.openfoodfacts.org/product/$barcode",
            )

        fun usdaClient(
            query: String,
            fdcId: String,
            energyKcal: Double?,
        ): UsdaNutritionClient =
            FakeUsdaNutritionClient(
                matchesByQuery = mapOf(query to fdcId),
                detailsByFdcId = mapOf(
                    fdcId to UsdaFoodDetails(
                        fdcId = fdcId,
                        description = "Generic yogurt, plain",
                        basis = basis(type = NutritionBasisType.PER_100_GRAMS, description = "100 g"),
                        values = values(energyKcal = energyKcal),
                        confidenceScore = 0.9,
                        observedAt = NOW,
                    ),
                ),
            )

        fun recipeSnapshot(energyKcal: Double?): ProviderNutritionSnapshot =
            snapshot(
                sourceType = NutritionSourceType.RECIPE_CALCULATION,
                energyKcal = energyKcal,
                corrected = false,
                lookupDerived = false,
                state = TruthState.INFERRED,
                sourceId = "recipe-calculation:recipe-1",
                label = "Recipe calculation",
            )

        fun aiSnapshot(energyKcal: Double?): ProviderNutritionSnapshot =
            snapshot(
                sourceType = NutritionSourceType.AI_ESTIMATE,
                energyKcal = energyKcal,
                corrected = false,
                lookupDerived = false,
                state = TruthState.ESTIMATED,
                sourceId = "ai-estimate:generic-yogurt",
                label = "AI estimate",
                estimated = true,
            )
    }
}
