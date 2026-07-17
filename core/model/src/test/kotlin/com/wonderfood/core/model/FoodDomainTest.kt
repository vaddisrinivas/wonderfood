package com.wonderfood.core.model

import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class FoodDomainTest {
    @Test
    fun nutritionValuesAreNullableAndDoNotInventMacroDefaults() {
        val values = NutritionValues(
            energyKcal = null,
            proteinGrams = null,
            carbohydrateGrams = null,
            fatGrams = null,
            fiberGrams = null,
            sugarGrams = null,
            sodiumMilligrams = null,
        )

        assertNull(values.energyKcal)
        assertNull(values.proteinGrams)
        assertNull(values.carbohydrateGrams)
        assertNull(values.fatGrams)
        assertNull(values.fiberGrams)
        assertNull(values.sugarGrams)
        assertNull(values.sodiumMilligrams)
    }

    @Test
    fun unknownTruthAndConfidenceAreFirstClass() {
        assertEquals(TruthState.UNKNOWN, TruthState.entries.first())
        assertEquals(SourceKind.UNKNOWN, SourceKind.entries.first())
        assertEquals(FoodUnit.UNKNOWN, FoodUnit.entries.first())
        assertEquals(TruthState.UNKNOWN, Confidence.UNKNOWN.state)
        assertNull(Confidence.UNKNOWN.score)

        assertThrows(IllegalArgumentException::class.java) {
            Confidence(
                score = 0.1,
                state = TruthState.UNKNOWN,
                rationale = "should not pretend unknown is scored",
            )
        }
    }

    @Test
    fun sourceAndConfidenceCarryProvenanceWithoutFrameworkTypes() {
        val source = Source(
            id = SourceId("source-manual-entry"),
            kind = SourceKind.USER,
            label = "Manual entry",
            externalId = null,
            uri = null,
            capturedAt = IsoTimestamp("2026-07-16T10:15:30Z"),
            truthState = TruthState.USER_CONFIRMED,
        )
        val confidence = Confidence(
            score = 0.96,
            state = TruthState.USER_CONFIRMED,
            rationale = "Typed by user",
        )

        assertEquals(SourceKind.USER, source.kind)
        assertEquals("Manual entry", source.label)
        assertEquals(0.96, confidence.score ?: -1.0, 0.0)
        assertEquals(TruthState.USER_CONFIRMED, confidence.state)
    }

    @Test
    fun idsAreStableStringsWithUuidFactories() {
        val generated = FoodId.new()

        UUID.fromString(generated.value)
        assertEquals("food-import-123", FoodId("food-import-123").value)
        assertThrows(IllegalArgumentException::class.java) {
            FoodId("")
        }
    }

    @Test
    fun explicitUnknownFoodDoesNotGainNutritionSnapshotsOrMacroValues() {
        val unknownFood = food(
            status = FoodStatus.UNKNOWN,
            nutritionSnapshotIds = emptyList(),
            source = source(truthState = TruthState.UNKNOWN),
            confidence = Confidence.UNKNOWN,
            truthState = TruthState.UNKNOWN,
        )
        val unknownNutrition = nutritionSnapshot(
            values = NutritionValues(
                energyKcal = null,
                proteinGrams = null,
                carbohydrateGrams = null,
                fatGrams = null,
                fiberGrams = null,
                sugarGrams = null,
                sodiumMilligrams = null,
            ),
            confidence = Confidence.UNKNOWN,
            truthState = TruthState.UNKNOWN,
        )

        assertEquals(FoodStatus.UNKNOWN, unknownFood.status)
        assertEquals(emptyList<NutritionSnapshotId>(), unknownFood.nutritionSnapshotIds)
        assertNull(unknownNutrition.values.energyKcal)
        assertNull(unknownNutrition.values.proteinGrams)
        assertNull(unknownNutrition.values.carbohydrateGrams)
        assertNull(unknownNutrition.values.fatGrams)
    }

    @Test
    fun unitsAndStatusesKeepUnknownFirstAndArchiveStatesExplicit() {
        assertEquals(FoodUnit.UNKNOWN, FoodUnit.entries.first())
        assertEquals(FoodStatus.UNKNOWN, FoodStatus.entries.first())
        assertEquals(StockLotStatus.UNKNOWN, StockLotStatus.entries.first())
        assertEquals(RecipeStatus.UNKNOWN, RecipeStatus.entries.first())
        assertEquals(MealPlanStatus.UNKNOWN, MealPlanStatus.entries.first())
        assertEquals(PlanEntryStatus.UNKNOWN, PlanEntryStatus.entries.first())
        assertEquals(MealLogStatus.UNKNOWN, MealLogStatus.entries.first())
        assertEquals(ShoppingItemStatus.UNKNOWN, ShoppingItemStatus.entries.first())
        assertEquals(ReceiptStatus.UNKNOWN, ReceiptStatus.entries.first())

        assertEquals(FoodStatus.ARCHIVED, FoodStatus.valueOf("ARCHIVED"))
        assertEquals(StockLotStatus.ARCHIVED, StockLotStatus.valueOf("ARCHIVED"))
        assertEquals(RecipeStatus.ARCHIVED, RecipeStatus.valueOf("ARCHIVED"))
        assertEquals(MealPlanStatus.ARCHIVED, MealPlanStatus.valueOf("ARCHIVED"))
        assertEquals(PlanEntryStatus.ARCHIVED, PlanEntryStatus.valueOf("ARCHIVED"))
        assertEquals(MealLogStatus.ARCHIVED, MealLogStatus.valueOf("ARCHIVED"))
        assertEquals(ShoppingItemStatus.ARCHIVED, ShoppingItemStatus.valueOf("ARCHIVED"))
        assertEquals(ReceiptStatus.ARCHIVED, ReceiptStatus.valueOf("ARCHIVED"))
        assertEquals(FoodEventType.ARCHIVED, FoodEventType.valueOf("ARCHIVED"))

        assertEquals(
            Quantity(amount = 2.5, unit = FoodUnit.CUP, truthState = TruthState.USER_CONFIRMED),
            Quantity(amount = 2.5, unit = FoodUnit.CUP, truthState = TruthState.USER_CONFIRMED),
        )
        assertNotEquals(
            Quantity(amount = 2.5, unit = FoodUnit.CUP, truthState = TruthState.USER_CONFIRMED),
            Quantity(amount = 2.5, unit = FoodUnit.GRAM, truthState = TruthState.USER_CONFIRMED),
        )
    }

    @Test
    fun archiveSemanticsMarkStatusAndEventWithoutErasingRelations() {
        val aliasId = FoodAliasId("alias-generic-eggs")
        val stockLotId = StockLotId("lot-generic-eggs")
        val nutritionSnapshotId = NutritionSnapshotId("nutrition-generic-eggs")
        val attachmentId = AttachmentId("attachment-generic-eggs")
        val activeFood = food(
            status = FoodStatus.ACTIVE,
            aliasIds = listOf(aliasId),
            stockLotIds = listOf(stockLotId),
            nutritionSnapshotIds = listOf(nutritionSnapshotId),
            attachmentIds = listOf(attachmentId),
        )

        val archivedFood = activeFood.copy(status = FoodStatus.ARCHIVED)
        val archiveEvent = FoodEvent(
            id = FoodEventId("event-archive-generic-eggs"),
            subject = EntityRef(EntityType.FOOD, activeFood.id.value),
            type = FoodEventType.ARCHIVED,
            occurredAt = IsoTimestamp("2026-01-15T12:00:00Z"),
            quantity = null,
            note = "Archived during user review",
            source = source(),
            confidence = confirmedConfidence(),
            truthState = TruthState.USER_CONFIRMED,
        )

        assertEquals(FoodStatus.ARCHIVED, archivedFood.status)
        assertEquals(activeFood.aliasIds, archivedFood.aliasIds)
        assertEquals(activeFood.stockLotIds, archivedFood.stockLotIds)
        assertEquals(activeFood.nutritionSnapshotIds, archivedFood.nutritionSnapshotIds)
        assertEquals(activeFood.attachmentIds, archivedFood.attachmentIds)
        assertEquals(EntityRef(EntityType.FOOD, activeFood.id.value), archiveEvent.subject)
        assertEquals(FoodEventType.ARCHIVED, archiveEvent.type)
    }

    @Test
    fun relationsAreTypedStableAndStructurallyEqual() {
        val relation = Relation(
            id = RelationId("relation-recipe-food"),
            from = EntityRef(EntityType.RECIPE, "recipe-generic-bowl"),
            to = EntityRef(EntityType.FOOD, "food-generic-rice"),
            type = RelationType.CONTAINS,
            source = source(),
            confidence = confirmedConfidence(),
            truthState = TruthState.USER_CONFIRMED,
        )

        assertEquals(
            relation,
            relation.copy(
                from = EntityRef(EntityType.RECIPE, "recipe-generic-bowl"),
                to = EntityRef(EntityType.FOOD, "food-generic-rice"),
            ),
        )
        assertNotEquals(relation, relation.copy(type = RelationType.SUBSTITUTE_FOR))
        assertThrows(IllegalArgumentException::class.java) {
            EntityRef(EntityType.FOOD, "")
        }
    }

    @Test
    fun invalidStateConstructionFailsForCurrentDomainInvariants() {
        assertThrows(IllegalArgumentException::class.java) { PageId("") }
        assertThrows(IllegalArgumentException::class.java) { IsoDate("") }
        assertThrows(IllegalArgumentException::class.java) { IsoTimestamp("") }
        assertThrows(IllegalArgumentException::class.java) { source(label = "") }
        assertThrows(IllegalArgumentException::class.java) {
            Confidence(score = -0.01, state = TruthState.ESTIMATED, rationale = "bad")
        }
        assertThrows(IllegalArgumentException::class.java) {
            Confidence(score = 1.01, state = TruthState.ESTIMATED, rationale = "bad")
        }
        assertThrows(IllegalArgumentException::class.java) {
            Quantity(amount = -1.0, unit = FoodUnit.GRAM, truthState = TruthState.USER_CONFIRMED)
        }
        assertThrows(IllegalArgumentException::class.java) {
            Money(amount = -1.0, currencyCode = "USD", truthState = TruthState.USER_CONFIRMED)
        }
        assertThrows(IllegalArgumentException::class.java) {
            Money(amount = 1.0, currencyCode = "US", truthState = TruthState.USER_CONFIRMED)
        }
        assertThrows(IllegalArgumentException::class.java) { page(title = "") }
        assertThrows(IllegalArgumentException::class.java) { food(name = "") }
        assertThrows(IllegalArgumentException::class.java) {
            FoodAlias(
                id = FoodAliasId("alias-invalid"),
                foodId = FoodId("food-generic"),
                name = "",
                locale = null,
                source = source(),
                confidence = confirmedConfidence(),
                truthState = TruthState.USER_CONFIRMED,
            )
        }
        assertThrows(IllegalArgumentException::class.java) { recipe(title = "") }
        assertThrows(IllegalArgumentException::class.java) { recipe(prepMinutes = -1) }
        assertThrows(IllegalArgumentException::class.java) { recipe(cookMinutes = -1) }
        assertThrows(IllegalArgumentException::class.java) {
            RecipeIngredient(
                id = RecipeIngredientId("ingredient-invalid"),
                recipeId = RecipeId("recipe-generic"),
                foodId = null,
                displayName = "",
                quantity = Quantity(null, FoodUnit.UNKNOWN, TruthState.UNKNOWN),
                preparation = null,
                optional = false,
                substituteFoodIds = emptyList(),
                source = source(),
                confidence = confirmedConfidence(),
                truthState = TruthState.USER_CONFIRMED,
            )
        }
        assertThrows(IllegalArgumentException::class.java) { recipeStep(order = -1) }
        assertThrows(IllegalArgumentException::class.java) { recipeStep(instruction = "") }
        assertThrows(IllegalArgumentException::class.java) { recipeStep(durationMinutes = -1) }
        assertThrows(IllegalArgumentException::class.java) {
            MealPlan(
                id = MealPlanId("plan-invalid"),
                pageId = PageId("page-plan"),
                name = "",
                startsOn = IsoDate("2026-01-15"),
                endsOn = IsoDate("2026-01-21"),
                status = MealPlanStatus.DRAFT,
                entries = emptyList(),
                source = source(),
                confidence = confirmedConfidence(),
                truthState = TruthState.USER_CONFIRMED,
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            Attachment(
                id = AttachmentId("attachment-invalid"),
                kind = AttachmentKind.IMAGE,
                uri = "",
                label = null,
                checksum = null,
                source = source(),
                confidence = confirmedConfidence(),
                truthState = TruthState.USER_CONFIRMED,
            )
        }
    }

    @Test
    fun domainEqualityIncludesTruthAndProvenance() {
        val baseFood = food()

        assertEquals(baseFood, food())
        assertNotEquals(baseFood, baseFood.copy(truthState = TruthState.ESTIMATED))
        assertNotEquals(baseFood, baseFood.copy(confidence = Confidence.UNKNOWN))
        assertNotEquals(baseFood, baseFood.copy(source = source(id = SourceId("source-label"))))
    }

    private fun source(
        id: SourceId = SourceId("source-user"),
        label: String = "Manual entry",
        truthState: TruthState = TruthState.USER_CONFIRMED,
    ): Source =
        Source(
            id = id,
            kind = SourceKind.USER,
            label = label,
            externalId = null,
            uri = null,
            capturedAt = IsoTimestamp("2026-01-15T12:00:00Z"),
            truthState = truthState,
        )

    private fun confirmedConfidence(): Confidence =
        Confidence(
            score = 0.97,
            state = TruthState.USER_CONFIRMED,
            rationale = "Confirmed by test user",
        )

    private fun page(title: String = "Generic Food"): Page =
        Page(
            id = PageId("page-generic-food"),
            title = title,
            kind = PageKind.FOOD,
            entity = EntityRef(EntityType.FOOD, "food-generic"),
            aliases = emptyList(),
            relationIds = emptyList(),
            attachmentIds = emptyList(),
            truthState = TruthState.USER_CONFIRMED,
            source = source(),
            confidence = confirmedConfidence(),
        )

    private fun food(
        name: String = "Generic Eggs",
        status: FoodStatus = FoodStatus.ACTIVE,
        aliasIds: List<FoodAliasId> = emptyList(),
        stockLotIds: List<StockLotId> = emptyList(),
        nutritionSnapshotIds: List<NutritionSnapshotId> = emptyList(),
        attachmentIds: List<AttachmentId> = emptyList(),
        source: Source = source(),
        confidence: Confidence = confirmedConfidence(),
        truthState: TruthState = TruthState.USER_CONFIRMED,
    ): Food =
        Food(
            id = FoodId("food-generic-eggs"),
            pageId = PageId("page-generic-eggs"),
            name = name,
            status = status,
            aliasIds = aliasIds,
            stockLotIds = stockLotIds,
            nutritionSnapshotIds = nutritionSnapshotIds,
            attachmentIds = attachmentIds,
            source = source,
            confidence = confidence,
            truthState = truthState,
        )

    private fun nutritionSnapshot(
        values: NutritionValues,
        confidence: Confidence,
        truthState: TruthState,
    ): NutritionSnapshot =
        NutritionSnapshot(
            id = NutritionSnapshotId("nutrition-generic-eggs"),
            subject = EntityRef(EntityType.FOOD, "food-generic-eggs"),
            basis = ServingBasis(
                type = NutritionBasisType.UNKNOWN,
                quantity = Quantity(null, FoodUnit.UNKNOWN, TruthState.UNKNOWN),
                description = null,
            ),
            values = values,
            source = source(truthState = truthState),
            confidence = confidence,
            capturedAt = null,
            truthState = truthState,
        )

    private fun recipe(
        title: String = "Generic Bowl",
        prepMinutes: Int? = null,
        cookMinutes: Int? = null,
    ): Recipe =
        Recipe(
            id = RecipeId("recipe-generic"),
            pageId = PageId("page-recipe-generic"),
            title = title,
            description = null,
            status = RecipeStatus.DRAFT,
            servings = Quantity(1.0, FoodUnit.SERVING, TruthState.USER_CONFIRMED),
            prepMinutes = prepMinutes,
            cookMinutes = cookMinutes,
            ingredients = emptyList(),
            steps = emptyList(),
            nutritionSnapshotIds = emptyList(),
            attachmentIds = emptyList(),
            source = source(),
            confidence = confirmedConfidence(),
            truthState = TruthState.USER_CONFIRMED,
        )

    private fun recipeStep(
        order: Int = 0,
        instruction: String = "Mix ingredients.",
        durationMinutes: Int? = null,
    ): RecipeStep =
        RecipeStep(
            id = RecipeStepId("step-generic"),
            recipeId = RecipeId("recipe-generic"),
            order = order,
            instruction = instruction,
            durationMinutes = durationMinutes,
            attachmentIds = emptyList(),
            source = source(),
            confidence = confirmedConfidence(),
            truthState = TruthState.USER_CONFIRMED,
        )
}
