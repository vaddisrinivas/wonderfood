package com.wonderfood.core.model

import java.util.UUID
import org.junit.Assert.assertEquals
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
}
