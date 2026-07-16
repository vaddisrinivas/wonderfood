package com.wonderfood.core.ai

import com.wonderfood.core.model.Confidence as DomainConfidence
import com.wonderfood.core.model.EntityRef
import com.wonderfood.core.model.EntityType
import com.wonderfood.core.model.FoodUnit
import com.wonderfood.core.model.IsoTimestamp
import com.wonderfood.core.model.NutritionBasisType
import com.wonderfood.core.model.NutritionSnapshot
import com.wonderfood.core.model.NutritionSnapshotId
import com.wonderfood.core.model.NutritionValues
import com.wonderfood.core.model.Quantity
import com.wonderfood.core.model.ServingBasis
import com.wonderfood.core.model.Source
import com.wonderfood.core.model.SourceId
import com.wonderfood.core.model.SourceKind
import com.wonderfood.core.model.TruthState

public data class NutritionResolutionRequest(
    public val subject: EntityRef = EntityRef(EntityType.UNKNOWN, "nutrition-target"),
    public val foodName: String? = null,
    public val barcode: String? = null,
    public val packageLabel: PackageNutritionLabelInput? = null,
    public val recipe: RecipeNutritionInput? = null,
    public val allowAiEstimate: Boolean = false,
    public val isOnline: Boolean = true,
    public val observedAt: IsoTimestamp? = null,
) {
    init {
        require(
            !foodName.isNullOrBlank() ||
                !barcode.isNullOrBlank() ||
                packageLabel != null ||
                recipe != null,
        ) {
            "Nutrition resolution needs a food name, barcode, package label, or recipe input."
        }
    }
}

public data class PackageNutritionLabelInput(
    public val labelId: String,
    public val foodName: String,
    public val brand: String? = null,
    public val basis: ServingBasis,
    public val values: NutritionValues,
    public val confidenceScore: Double,
    public val observedAt: IsoTimestamp,
) {
    init {
        require(labelId.isNotBlank()) { "Label id must not be blank." }
        require(foodName.isNotBlank()) { "Label food name must not be blank." }
        require(confidenceScore in 0.0..1.0) { "Label confidence must be between 0.0 and 1.0." }
    }
}

public data class RecipeNutritionInput(
    public val recipeId: String,
    public val title: String,
    public val servings: Double?,
    public val ingredientSnapshots: List<ProviderNutritionSnapshot>,
) {
    init {
        require(recipeId.isNotBlank()) { "Recipe id must not be blank." }
        require(title.isNotBlank()) { "Recipe title must not be blank." }
        servings?.let { require(it > 0.0) { "Recipe servings must be positive when known." } }
    }
}

public data class ProviderNutritionSnapshot(
    public val basis: ServingBasis,
    public val values: NutritionValues,
    public val provenance: NutritionProvenance,
    public val displayName: String? = null,
)

public data class NutritionProvenance(
    public val sourceId: String,
    public val sourceType: NutritionSourceType,
    public val sourceLabel: String,
    public val basis: ServingBasis,
    public val confidence: DomainConfidence,
    public val capturedAt: IsoTimestamp,
    public val corrected: Boolean,
    public val estimated: Boolean,
    public val lookupDerived: Boolean,
    public val externalId: String? = null,
    public val uri: String? = null,
) {
    init {
        require(sourceId.isNotBlank()) { "Nutrition source id must not be blank." }
        require(sourceLabel.isNotBlank()) { "Nutrition source label must not be blank." }
    }
}

public enum class NutritionSourceType {
    USER_CORRECTION,
    LOCAL_LOOKUP,
    PACKAGE_LABEL,
    OPEN_FOOD_FACTS,
    USDA,
    RECIPE_CALCULATION,
    AI_ESTIMATE,
}

public enum class NutritionResolutionStatus {
    KNOWN,
    UNKNOWN,
}

public data class NutritionResolution(
    public val status: NutritionResolutionStatus,
    public val snapshot: NutritionSnapshot?,
    public val provenance: NutritionProvenance?,
    public val attempts: List<NutritionProviderAttempt>,
) {
    public val isKnown: Boolean
        get() = status == NutritionResolutionStatus.KNOWN

    public companion object {
        public fun unknown(attempts: List<NutritionProviderAttempt>): NutritionResolution =
            NutritionResolution(
                status = NutritionResolutionStatus.UNKNOWN,
                snapshot = null,
                provenance = null,
                attempts = attempts,
            )
    }
}

public data class NutritionProviderAttempt(
    public val providerId: String,
    public val sourceType: NutritionSourceType,
    public val status: NutritionProviderAttemptStatus,
    public val message: String,
    public val fromCache: Boolean = false,
)

public enum class NutritionProviderAttemptStatus {
    CACHE_HIT,
    CACHE_UNAVAILABLE,
    KNOWN,
    NO_MATCH,
    UNAVAILABLE,
    FAILURE,
    OFFLINE_SKIPPED,
    RATE_LIMITED,
}

public sealed class NutritionProviderResult {
    public data class Known(
        public val snapshot: ProviderNutritionSnapshot,
        public val message: String = "Nutrition known.",
    ) : NutritionProviderResult()

    public data class NoMatch(
        public val message: String = "No nutrition match.",
    ) : NutritionProviderResult()

    public data class Unavailable(
        public val message: String,
    ) : NutritionProviderResult()

    public data class Failure(
        public val message: String,
        public val retryable: Boolean,
    ) : NutritionProviderResult()
}

public data class NutritionProviderContext(
    public val clock: NutritionClock,
)

public interface NutritionProvider {
    public val id: String
    public val sourceType: NutritionSourceType
    public val requiresNetwork: Boolean

    public fun cacheKey(request: NutritionResolutionRequest): NutritionCacheKey? = null

    public fun resolve(
        request: NutritionResolutionRequest,
        context: NutritionProviderContext,
    ): NutritionProviderResult
}

public class NutritionProviderChain(
    providers: List<NutritionProvider>,
    private val cache: NutritionProviderCache = InMemoryNutritionProviderCache(),
    private val rateLimitState: NutritionRateLimitState = NoopNutritionRateLimitState,
    private val clock: NutritionClock = SystemNutritionClock,
    private val mapper: NutritionProvenanceMapper = NutritionProvenanceMapper(),
) {
    private val providers: List<NutritionProvider> = providers.toList()

    init {
        require(providers.isNotEmpty()) { "Nutrition provider chain must include at least one provider." }
        require(providers.map { it.id }.distinct().size == providers.size) {
            "Nutrition provider ids must be unique."
        }
    }

    public fun resolve(request: NutritionResolutionRequest): NutritionResolution {
        val attempts = mutableListOf<NutritionProviderAttempt>()
        val context = NutritionProviderContext(clock = clock)
        providers.forEach { provider ->
            val cacheKey = provider.cacheKey(request)
            if (cacheKey != null) {
                when (val cached = readCache(cacheKey, provider, attempts)) {
                    is CachedNutritionResult.Known -> {
                        attempts += attempt(
                            provider = provider,
                            status = NutritionProviderAttemptStatus.CACHE_HIT,
                            message = "Resolved from cached ${provider.id} nutrition.",
                            fromCache = true,
                        )
                        return known(request, cached.snapshot, attempts)
                    }

                    is CachedNutritionResult.Unavailable -> attempts += attempt(
                        provider = provider,
                        status = NutritionProviderAttemptStatus.CACHE_UNAVAILABLE,
                        message = cached.reason,
                        fromCache = true,
                    )

                    null -> Unit
                }
            }

            if (provider.requiresNetwork && !request.isOnline) {
                attempts += attempt(
                    provider = provider,
                    status = NutritionProviderAttemptStatus.OFFLINE_SKIPPED,
                    message = "Skipped ${provider.id}; request is offline.",
                )
                return@forEach
            }

            if (provider.requiresNetwork) {
                val decision = rateLimitState.tryAcquire(
                    providerId = provider.id,
                    nowEpochMillis = clock.nowEpochMillis(),
                )
                if (!decision.allowed) {
                    attempts += attempt(
                        provider = provider,
                        status = NutritionProviderAttemptStatus.RATE_LIMITED,
                        message = "Skipped ${provider.id}; retry after ${decision.retryAfterMillis ?: 0} ms.",
                    )
                    return@forEach
                }
            }

            when (val result = runProvider(provider, request, context)) {
                is NutritionProviderResult.Known -> {
                    if (!result.snapshot.values.hasKnownValue()) {
                        attempts += attempt(
                            provider = provider,
                            status = NutritionProviderAttemptStatus.NO_MATCH,
                            message = "Provider returned no known nutrition fields.",
                        )
                        return@forEach
                    }
                    cacheKey?.let { writeKnownCache(it, result.snapshot, provider, attempts) }
                    attempts += attempt(
                        provider = provider,
                        status = NutritionProviderAttemptStatus.KNOWN,
                        message = result.message,
                    )
                    return known(request, result.snapshot, attempts)
                }

                is NutritionProviderResult.NoMatch -> attempts += attempt(
                    provider = provider,
                    status = NutritionProviderAttemptStatus.NO_MATCH,
                    message = result.message,
                )

                is NutritionProviderResult.Unavailable -> {
                    cacheKey?.let { writeUnavailableCache(it, result.message, provider, attempts) }
                    attempts += attempt(
                        provider = provider,
                        status = NutritionProviderAttemptStatus.UNAVAILABLE,
                        message = result.message,
                    )
                }

                is NutritionProviderResult.Failure -> attempts += attempt(
                    provider = provider,
                    status = NutritionProviderAttemptStatus.FAILURE,
                    message = result.message,
                )
            }
        }
        return NutritionResolution.unknown(attempts)
    }

    private fun readCache(
        key: NutritionCacheKey,
        provider: NutritionProvider,
        attempts: MutableList<NutritionProviderAttempt>,
    ): CachedNutritionResult? =
        try {
            cache.get(key)
        } catch (error: Throwable) {
            attempts += attempt(
                provider = provider,
                status = NutritionProviderAttemptStatus.FAILURE,
                message = "Cache read failed: ${error.message ?: error.javaClass.simpleName}",
                fromCache = true,
            )
            null
        }

    private fun writeKnownCache(
        key: NutritionCacheKey,
        snapshot: ProviderNutritionSnapshot,
        provider: NutritionProvider,
        attempts: MutableList<NutritionProviderAttempt>,
    ) {
        try {
            cache.putKnown(key, snapshot, clock.nowIsoTimestamp())
        } catch (error: Throwable) {
            attempts += attempt(
                provider = provider,
                status = NutritionProviderAttemptStatus.FAILURE,
                message = "Cache write failed: ${error.message ?: error.javaClass.simpleName}",
                fromCache = true,
            )
        }
    }

    private fun writeUnavailableCache(
        key: NutritionCacheKey,
        reason: String,
        provider: NutritionProvider,
        attempts: MutableList<NutritionProviderAttempt>,
    ) {
        try {
            cache.putUnavailable(key, reason, clock.nowIsoTimestamp())
        } catch (error: Throwable) {
            attempts += attempt(
                provider = provider,
                status = NutritionProviderAttemptStatus.FAILURE,
                message = "Cache write failed: ${error.message ?: error.javaClass.simpleName}",
                fromCache = true,
            )
        }
    }

    private fun runProvider(
        provider: NutritionProvider,
        request: NutritionResolutionRequest,
        context: NutritionProviderContext,
    ): NutritionProviderResult =
        try {
            provider.resolve(request, context)
        } catch (error: Throwable) {
            NutritionProviderResult.Failure(
                message = error.message ?: error.javaClass.simpleName,
                retryable = true,
            )
        }

    private fun known(
        request: NutritionResolutionRequest,
        providerSnapshot: ProviderNutritionSnapshot,
        attempts: List<NutritionProviderAttempt>,
    ): NutritionResolution =
        NutritionResolution(
            status = NutritionResolutionStatus.KNOWN,
            snapshot = mapper.toDomainSnapshot(request.subject, providerSnapshot),
            provenance = providerSnapshot.provenance,
            attempts = attempts.toList(),
        )

    private fun attempt(
        provider: NutritionProvider,
        status: NutritionProviderAttemptStatus,
        message: String,
        fromCache: Boolean = false,
    ): NutritionProviderAttempt =
        NutritionProviderAttempt(
            providerId = provider.id,
            sourceType = provider.sourceType,
            status = status,
            message = message,
            fromCache = fromCache,
        )

    public companion object {
        public fun default(
            localLookupStore: NutritionLocalLookupStore = InMemoryNutritionLocalLookupStore(),
            openFoodFactsClient: OpenFoodFactsNutritionClient = FakeOpenFoodFactsNutritionClient(),
            usdaClient: UsdaNutritionClient = FakeUsdaNutritionClient(),
            recipeCalculator: RecipeNutritionCalculator = StaticRecipeNutritionCalculator(),
            aiEstimator: AiNutritionEstimator = StaticAiNutritionEstimator(),
            cache: NutritionProviderCache = InMemoryNutritionProviderCache(),
            rateLimitState: NutritionRateLimitState = NoopNutritionRateLimitState,
            clock: NutritionClock = SystemNutritionClock,
        ): NutritionProviderChain =
            NutritionProviderChain(
                providers = listOf(
                    UserCorrectedLocalNutritionProvider(localLookupStore),
                    PackageLabelNutritionProvider(),
                    OpenFoodFactsBarcodeNutritionProvider(openFoodFactsClient),
                    UsdaSearchDetailsNutritionProvider(usdaClient),
                    RecipeCalculationNutritionProvider(recipeCalculator),
                    AiEstimateNutritionProvider(aiEstimator),
                ),
                cache = cache,
                rateLimitState = rateLimitState,
                clock = clock,
            )
    }
}

public class NutritionProvenanceMapper {
    public fun toDomainSnapshot(
        subject: EntityRef,
        snapshot: ProviderNutritionSnapshot,
    ): NutritionSnapshot =
        NutritionSnapshot(
            id = NutritionSnapshotId(
                "nutrition:${snapshot.provenance.sourceId}:${subject.type.name.lowercase()}:${subject.id}",
            ),
            subject = subject,
            basis = snapshot.basis,
            values = snapshot.values,
            source = Source(
                id = SourceId(snapshot.provenance.sourceId),
                kind = snapshot.provenance.sourceType.toSourceKind(),
                label = snapshot.provenance.sourceLabel,
                externalId = snapshot.provenance.externalId,
                uri = snapshot.provenance.uri,
                capturedAt = snapshot.provenance.capturedAt,
                truthState = snapshot.provenance.confidence.state,
            ),
            confidence = snapshot.provenance.confidence,
            capturedAt = snapshot.provenance.capturedAt,
            truthState = snapshot.provenance.confidence.state,
        )
}

public interface NutritionLocalLookupStore {
    public fun userCorrectionFor(request: NutritionResolutionRequest): ProviderNutritionSnapshot?
    public fun localLookupFor(request: NutritionResolutionRequest): ProviderNutritionSnapshot?
}

public class InMemoryNutritionLocalLookupStore(
    userCorrections: Map<String, ProviderNutritionSnapshot> = emptyMap(),
    localLookups: Map<String, ProviderNutritionSnapshot> = emptyMap(),
) : NutritionLocalLookupStore {
    private val userCorrections: Map<String, ProviderNutritionSnapshot> =
        userCorrections.mapKeys { normalizeNutritionKey(it.key) }
    private val localLookups: Map<String, ProviderNutritionSnapshot> =
        localLookups.mapKeys { normalizeNutritionKey(it.key) }

    override fun userCorrectionFor(request: NutritionResolutionRequest): ProviderNutritionSnapshot? =
        candidateKeys(request).firstNotNullOfOrNull { userCorrections[it] }

    override fun localLookupFor(request: NutritionResolutionRequest): ProviderNutritionSnapshot? =
        candidateKeys(request).firstNotNullOfOrNull { localLookups[it] }
}

public class UserCorrectedLocalNutritionProvider(
    private val store: NutritionLocalLookupStore,
) : NutritionProvider {
    override val id: String = "user_corrected_local"
    override val sourceType: NutritionSourceType = NutritionSourceType.USER_CORRECTION
    override val requiresNetwork: Boolean = false

    override fun resolve(
        request: NutritionResolutionRequest,
        context: NutritionProviderContext,
    ): NutritionProviderResult {
        store.userCorrectionFor(request)?.let {
            return NutritionProviderResult.Known(it, "Resolved from user-corrected nutrition.")
        }
        store.localLookupFor(request)?.let {
            return NutritionProviderResult.Known(it, "Resolved from local nutrition lookup.")
        }
        return NutritionProviderResult.NoMatch("No user correction or local nutrition match.")
    }
}

public class PackageLabelNutritionProvider : NutritionProvider {
    override val id: String = "package_label"
    override val sourceType: NutritionSourceType = NutritionSourceType.PACKAGE_LABEL
    override val requiresNetwork: Boolean = false

    override fun resolve(
        request: NutritionResolutionRequest,
        context: NutritionProviderContext,
    ): NutritionProviderResult {
        val label = request.packageLabel ?: return NutritionProviderResult.NoMatch("No package label input.")
        if (!label.values.hasKnownValue()) {
            return NutritionProviderResult.NoMatch("Package label contains no known nutrition fields.")
        }
        return NutritionProviderResult.Known(
            snapshot = ProviderNutritionSnapshot(
                basis = label.basis,
                values = label.values,
                displayName = label.foodName,
                provenance = NutritionProvenance(
                    sourceId = "nutrition-label:${label.labelId}",
                    sourceType = NutritionSourceType.PACKAGE_LABEL,
                    sourceLabel = "Package nutrition label",
                    basis = label.basis,
                    confidence = DomainConfidence(
                        score = label.confidenceScore,
                        state = TruthState.PROVIDER_CONFIRMED,
                        rationale = "Nutrition values came from package label input.",
                    ),
                    capturedAt = label.observedAt,
                    corrected = false,
                    estimated = false,
                    lookupDerived = false,
                    externalId = label.labelId,
                    uri = null,
                ),
            ),
            message = "Resolved from package label input.",
        )
    }
}

public class OpenFoodFactsBarcodeNutritionProvider(
    private val client: OpenFoodFactsNutritionClient,
) : NutritionProvider {
    override val id: String = "open_food_facts_barcode"
    override val sourceType: NutritionSourceType = NutritionSourceType.OPEN_FOOD_FACTS
    override val requiresNetwork: Boolean = true

    override fun cacheKey(request: NutritionResolutionRequest): NutritionCacheKey? =
        request.barcode?.takeIf { it.isNotBlank() }?.let { NutritionCacheKey(id, "barcode:${it.trim()}") }

    override fun resolve(
        request: NutritionResolutionRequest,
        context: NutritionProviderContext,
    ): NutritionProviderResult {
        val barcode = request.barcode?.trim().orEmpty()
        if (barcode.isBlank()) return NutritionProviderResult.NoMatch("No barcode for Open Food Facts lookup.")
        return when (val result = client.fetchByBarcode(barcode)) {
            is OpenFoodFactsProductResult.Found -> NutritionProviderResult.Known(
                snapshot = result.product.toProviderSnapshot(context.clock),
                message = "Resolved from Open Food Facts barcode lookup.",
            )

            OpenFoodFactsProductResult.NotFound -> NutritionProviderResult.NoMatch("Open Food Facts barcode not found.")
            is OpenFoodFactsProductResult.Unavailable -> NutritionProviderResult.Unavailable(result.message)
            is OpenFoodFactsProductResult.Failure -> NutritionProviderResult.Failure(result.message, result.retryable)
        }
    }
}

public class UsdaSearchDetailsNutritionProvider(
    private val client: UsdaNutritionClient,
) : NutritionProvider {
    override val id: String = "usda_search_details"
    override val sourceType: NutritionSourceType = NutritionSourceType.USDA
    override val requiresNetwork: Boolean = true

    override fun cacheKey(request: NutritionResolutionRequest): NutritionCacheKey? =
        request.foodName?.takeIf { it.isNotBlank() }?.let {
            NutritionCacheKey(id, "query:${normalizeNutritionKey(it)}")
        }

    override fun resolve(
        request: NutritionResolutionRequest,
        context: NutritionProviderContext,
    ): NutritionProviderResult {
        val query = request.foodName?.trim().orEmpty()
        if (query.isBlank()) return NutritionProviderResult.NoMatch("No food name for USDA search.")
        val match = when (val search = client.search(query)) {
            is UsdaSearchResult.Match -> search.fdcId
            UsdaSearchResult.NoMatch -> return NutritionProviderResult.NoMatch("USDA search found no match.")
            is UsdaSearchResult.Unavailable -> return NutritionProviderResult.Unavailable(search.message)
            is UsdaSearchResult.Failure -> return NutritionProviderResult.Failure(search.message, search.retryable)
        }
        return when (val details = client.details(match)) {
            is UsdaDetailsResult.Found -> NutritionProviderResult.Known(
                snapshot = details.food.toProviderSnapshot(context.clock),
                message = "Resolved from USDA search/details lookup.",
            )

            UsdaDetailsResult.NotFound -> NutritionProviderResult.NoMatch("USDA details not found.")
            is UsdaDetailsResult.Unavailable -> NutritionProviderResult.Unavailable(details.message)
            is UsdaDetailsResult.Failure -> NutritionProviderResult.Failure(details.message, details.retryable)
        }
    }
}

public class RecipeCalculationNutritionProvider(
    private val calculator: RecipeNutritionCalculator,
) : NutritionProvider {
    override val id: String = "recipe_calculation"
    override val sourceType: NutritionSourceType = NutritionSourceType.RECIPE_CALCULATION
    override val requiresNetwork: Boolean = false

    override fun resolve(
        request: NutritionResolutionRequest,
        context: NutritionProviderContext,
    ): NutritionProviderResult {
        val recipe = request.recipe ?: return NutritionProviderResult.NoMatch("No recipe input for nutrition calculation.")
        return calculator.calculate(recipe)
            ?.let { NutritionProviderResult.Known(it, "Resolved from recipe calculation hook.") }
            ?: NutritionProviderResult.NoMatch("Recipe calculator could not produce nutrition.")
    }
}

public class AiEstimateNutritionProvider(
    private val estimator: AiNutritionEstimator,
) : NutritionProvider {
    override val id: String = "ai_estimate"
    override val sourceType: NutritionSourceType = NutritionSourceType.AI_ESTIMATE
    override val requiresNetwork: Boolean = false

    override fun resolve(
        request: NutritionResolutionRequest,
        context: NutritionProviderContext,
    ): NutritionProviderResult {
        if (!request.allowAiEstimate) {
            return NutritionProviderResult.NoMatch("AI estimate fallback is disabled.")
        }
        return estimator.estimate(request)
            ?.let { NutritionProviderResult.Known(it, "Resolved from AI estimate fallback.") }
            ?: NutritionProviderResult.NoMatch("AI estimator has no deterministic fixture.")
    }
}

public data class NutritionCacheKey(
    public val providerId: String,
    public val lookupKey: String,
) {
    init {
        require(providerId.isNotBlank()) { "Cache provider id must not be blank." }
        require(lookupKey.isNotBlank()) { "Cache lookup key must not be blank." }
    }
}

public sealed class CachedNutritionResult {
    public data class Known(
        public val snapshot: ProviderNutritionSnapshot,
        public val storedAt: IsoTimestamp,
    ) : CachedNutritionResult()

    public data class Unavailable(
        public val reason: String,
        public val storedAt: IsoTimestamp,
    ) : CachedNutritionResult()
}

public interface NutritionProviderCache {
    public fun get(key: NutritionCacheKey): CachedNutritionResult?
    public fun putKnown(
        key: NutritionCacheKey,
        snapshot: ProviderNutritionSnapshot,
        storedAt: IsoTimestamp,
    )

    public fun putUnavailable(
        key: NutritionCacheKey,
        reason: String,
        storedAt: IsoTimestamp,
    )
}

public class InMemoryNutritionProviderCache : NutritionProviderCache {
    private val entries: MutableMap<NutritionCacheKey, CachedNutritionResult> = linkedMapOf()

    override fun get(key: NutritionCacheKey): CachedNutritionResult? = entries[key]

    override fun putKnown(
        key: NutritionCacheKey,
        snapshot: ProviderNutritionSnapshot,
        storedAt: IsoTimestamp,
    ) {
        entries[key] = CachedNutritionResult.Known(snapshot, storedAt)
    }

    override fun putUnavailable(
        key: NutritionCacheKey,
        reason: String,
        storedAt: IsoTimestamp,
    ) {
        entries[key] = CachedNutritionResult.Unavailable(reason, storedAt)
    }
}

public data class NutritionRateLimitDecision(
    public val allowed: Boolean,
    public val retryAfterMillis: Long? = null,
)

public interface NutritionRateLimitState {
    public fun tryAcquire(
        providerId: String,
        nowEpochMillis: Long,
    ): NutritionRateLimitDecision
}

public object NoopNutritionRateLimitState : NutritionRateLimitState {
    override fun tryAcquire(
        providerId: String,
        nowEpochMillis: Long,
    ): NutritionRateLimitDecision = NutritionRateLimitDecision(allowed = true)
}

public class InMemoryFixedWindowNutritionRateLimitState(
    private val maxCalls: Int,
    private val windowMillis: Long,
) : NutritionRateLimitState {
    private val callsByProvider: MutableMap<String, MutableList<Long>> = linkedMapOf()

    init {
        require(maxCalls >= 0) { "maxCalls cannot be negative." }
        require(windowMillis > 0) { "windowMillis must be positive." }
    }

    override fun tryAcquire(
        providerId: String,
        nowEpochMillis: Long,
    ): NutritionRateLimitDecision {
        val calls = callsByProvider.getOrPut(providerId) { mutableListOf() }
        calls.removeAll { nowEpochMillis - it >= windowMillis }
        if (calls.size >= maxCalls) {
            val oldest = calls.minOrNull() ?: nowEpochMillis
            return NutritionRateLimitDecision(
                allowed = false,
                retryAfterMillis = (windowMillis - (nowEpochMillis - oldest)).coerceAtLeast(0),
            )
        }
        calls += nowEpochMillis
        return NutritionRateLimitDecision(allowed = true)
    }
}

public interface NutritionClock {
    public fun nowIsoTimestamp(): IsoTimestamp
    public fun nowEpochMillis(): Long
}

public object SystemNutritionClock : NutritionClock {
    override fun nowIsoTimestamp(): IsoTimestamp =
        IsoTimestamp(java.time.Instant.now().toString())

    override fun nowEpochMillis(): Long =
        java.time.Instant.now().toEpochMilli()
}

public class FixedNutritionClock(
    private val isoTimestamp: IsoTimestamp,
    private val epochMillis: Long = 0L,
) : NutritionClock {
    override fun nowIsoTimestamp(): IsoTimestamp = isoTimestamp
    override fun nowEpochMillis(): Long = epochMillis
}

public interface OpenFoodFactsNutritionClient {
    public fun fetchByBarcode(barcode: String): OpenFoodFactsProductResult
}

public sealed class OpenFoodFactsProductResult {
    public data class Found(public val product: OpenFoodFactsProduct) : OpenFoodFactsProductResult()
    public data object NotFound : OpenFoodFactsProductResult()
    public data class Unavailable(public val message: String) : OpenFoodFactsProductResult()
    public data class Failure(public val message: String, public val retryable: Boolean) : OpenFoodFactsProductResult()
}

public data class OpenFoodFactsProduct(
    public val barcode: String,
    public val foodName: String,
    public val basis: ServingBasis,
    public val values: NutritionValues,
    public val confidenceScore: Double = 0.86,
    public val observedAt: IsoTimestamp? = null,
    public val uri: String? = null,
) {
    init {
        require(barcode.isNotBlank()) { "Open Food Facts barcode must not be blank." }
        require(foodName.isNotBlank()) { "Open Food Facts food name must not be blank." }
        require(confidenceScore in 0.0..1.0) { "Open Food Facts confidence must be between 0.0 and 1.0." }
    }
}

public class FakeOpenFoodFactsNutritionClient(
    productsByBarcode: Map<String, OpenFoodFactsProduct> = emptyMap(),
    private val unavailableBarcodes: Set<String> = emptySet(),
    private val failuresByBarcode: Map<String, String> = emptyMap(),
) : OpenFoodFactsNutritionClient {
    private val productsByBarcode: Map<String, OpenFoodFactsProduct> =
        productsByBarcode.mapKeys { it.key.trim() }

    override fun fetchByBarcode(barcode: String): OpenFoodFactsProductResult {
        val key = barcode.trim()
        failuresByBarcode[key]?.let { return OpenFoodFactsProductResult.Failure(it, retryable = true) }
        if (key in unavailableBarcodes) return OpenFoodFactsProductResult.Unavailable("Open Food Facts unavailable.")
        return productsByBarcode[key]
            ?.let(OpenFoodFactsProductResult::Found)
            ?: OpenFoodFactsProductResult.NotFound
    }
}

public interface UsdaNutritionClient {
    public fun search(query: String): UsdaSearchResult
    public fun details(fdcId: String): UsdaDetailsResult
}

public sealed class UsdaSearchResult {
    public data class Match(public val fdcId: String) : UsdaSearchResult() {
        init {
            require(fdcId.isNotBlank()) { "USDA FDC id must not be blank." }
        }
    }

    public data object NoMatch : UsdaSearchResult()
    public data class Unavailable(public val message: String) : UsdaSearchResult()
    public data class Failure(public val message: String, public val retryable: Boolean) : UsdaSearchResult()
}

public sealed class UsdaDetailsResult {
    public data class Found(public val food: UsdaFoodDetails) : UsdaDetailsResult()
    public data object NotFound : UsdaDetailsResult()
    public data class Unavailable(public val message: String) : UsdaDetailsResult()
    public data class Failure(public val message: String, public val retryable: Boolean) : UsdaDetailsResult()
}

public data class UsdaFoodDetails(
    public val fdcId: String,
    public val description: String,
    public val basis: ServingBasis,
    public val values: NutritionValues,
    public val confidenceScore: Double = 0.9,
    public val observedAt: IsoTimestamp? = null,
) {
    init {
        require(fdcId.isNotBlank()) { "USDA FDC id must not be blank." }
        require(description.isNotBlank()) { "USDA description must not be blank." }
        require(confidenceScore in 0.0..1.0) { "USDA confidence must be between 0.0 and 1.0." }
    }
}

public class FakeUsdaNutritionClient(
    matchesByQuery: Map<String, String> = emptyMap(),
    detailsByFdcId: Map<String, UsdaFoodDetails> = emptyMap(),
    private val unavailableQueries: Set<String> = emptySet(),
    private val failuresByQuery: Map<String, String> = emptyMap(),
) : UsdaNutritionClient {
    private val matchesByQuery: Map<String, String> =
        matchesByQuery.mapKeys { normalizeNutritionKey(it.key) }
    private val detailsByFdcId: Map<String, UsdaFoodDetails> = detailsByFdcId.toMap()

    override fun search(query: String): UsdaSearchResult {
        val key = normalizeNutritionKey(query)
        failuresByQuery[key]?.let { return UsdaSearchResult.Failure(it, retryable = true) }
        if (key in unavailableQueries.map(::normalizeNutritionKey)) {
            return UsdaSearchResult.Unavailable("USDA unavailable.")
        }
        return matchesByQuery[key]?.let(UsdaSearchResult::Match) ?: UsdaSearchResult.NoMatch
    }

    override fun details(fdcId: String): UsdaDetailsResult =
        detailsByFdcId[fdcId]?.let(UsdaDetailsResult::Found) ?: UsdaDetailsResult.NotFound
}

public interface RecipeNutritionCalculator {
    public fun calculate(recipe: RecipeNutritionInput): ProviderNutritionSnapshot?
}

public class StaticRecipeNutritionCalculator(
    snapshotsByRecipeId: Map<String, ProviderNutritionSnapshot> = emptyMap(),
) : RecipeNutritionCalculator {
    private val snapshotsByRecipeId: Map<String, ProviderNutritionSnapshot> = snapshotsByRecipeId.toMap()

    override fun calculate(recipe: RecipeNutritionInput): ProviderNutritionSnapshot? =
        snapshotsByRecipeId[recipe.recipeId]
}

public interface AiNutritionEstimator {
    public fun estimate(request: NutritionResolutionRequest): ProviderNutritionSnapshot?
}

public class StaticAiNutritionEstimator(
    estimatesByFoodName: Map<String, ProviderNutritionSnapshot> = emptyMap(),
) : AiNutritionEstimator {
    private val estimatesByFoodName: Map<String, ProviderNutritionSnapshot> =
        estimatesByFoodName.mapKeys { normalizeNutritionKey(it.key) }

    override fun estimate(request: NutritionResolutionRequest): ProviderNutritionSnapshot? =
        request.foodName?.let { estimatesByFoodName[normalizeNutritionKey(it)] }
}

private fun OpenFoodFactsProduct.toProviderSnapshot(clock: NutritionClock): ProviderNutritionSnapshot {
    val capturedAt = observedAt ?: clock.nowIsoTimestamp()
    return ProviderNutritionSnapshot(
        basis = basis,
        values = values,
        displayName = foodName,
        provenance = NutritionProvenance(
            sourceId = "open-food-facts:$barcode",
            sourceType = NutritionSourceType.OPEN_FOOD_FACTS,
            sourceLabel = "Open Food Facts",
            basis = basis,
            confidence = DomainConfidence(
                score = confidenceScore,
                state = TruthState.PROVIDER_CONFIRMED,
                rationale = "Barcode nutrition lookup from Open Food Facts.",
            ),
            capturedAt = capturedAt,
            corrected = false,
            estimated = false,
            lookupDerived = true,
            externalId = barcode,
            uri = uri,
        ),
    )
}

private fun UsdaFoodDetails.toProviderSnapshot(clock: NutritionClock): ProviderNutritionSnapshot {
    val capturedAt = observedAt ?: clock.nowIsoTimestamp()
    return ProviderNutritionSnapshot(
        basis = basis,
        values = values,
        displayName = description,
        provenance = NutritionProvenance(
            sourceId = "usda:$fdcId",
            sourceType = NutritionSourceType.USDA,
            sourceLabel = "USDA FoodData Central",
            basis = basis,
            confidence = DomainConfidence(
                score = confidenceScore,
                state = TruthState.PROVIDER_CONFIRMED,
                rationale = "Nutrition lookup from USDA search/details.",
            ),
            capturedAt = capturedAt,
            corrected = false,
            estimated = false,
            lookupDerived = true,
            externalId = fdcId,
            uri = null,
        ),
    )
}

private fun NutritionValues.hasKnownValue(): Boolean =
    energyKcal != null ||
        proteinGrams != null ||
        carbohydrateGrams != null ||
        fatGrams != null ||
        fiberGrams != null ||
        sugarGrams != null ||
        sodiumMilligrams != null

private fun NutritionSourceType.toSourceKind(): SourceKind =
    when (this) {
        NutritionSourceType.USER_CORRECTION -> SourceKind.USER
        NutritionSourceType.LOCAL_LOOKUP -> SourceKind.SYSTEM
        NutritionSourceType.PACKAGE_LABEL -> SourceKind.USER
        NutritionSourceType.OPEN_FOOD_FACTS -> SourceKind.OPEN_FOOD_FACTS
        NutritionSourceType.USDA -> SourceKind.USDA
        NutritionSourceType.RECIPE_CALCULATION -> SourceKind.SYSTEM
        NutritionSourceType.AI_ESTIMATE -> SourceKind.AI
    }

private fun candidateKeys(request: NutritionResolutionRequest): List<String> =
    listOfNotNull(
        request.barcode?.takeIf { it.isNotBlank() }?.let { "barcode:${it.trim()}" },
        request.foodName?.takeIf { it.isNotBlank() }?.let(::normalizeNutritionKey),
        request.packageLabel?.foodName?.let(::normalizeNutritionKey),
    )

private fun normalizeNutritionKey(value: String): String =
    value.trim().lowercase().replace(Regex("\\s+"), " ")
