package com.wonderfood.app.testing

import com.wonderfood.app.data.InventoryDraft
import java.time.Duration
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TestHarnessTest {
    @Test
    fun deterministicClockStartsFixedAndAdvancesOnlyWhenAsked() {
        val clock = DeterministicTestClock()

        assertEquals(Instant.parse("2026-01-15T12:00:00Z"), clock.instant())
        assertEquals(Instant.parse("2026-01-15T12:05:00Z"), clock.advanceBy(Duration.ofMinutes(5)))
        assertEquals(Instant.parse("2026-01-15T12:05:00Z"), clock.instant())
    }

    @Test
    fun deterministicUuidSourceHasStableSequenceAndLabelIds() {
        val ids = DeterministicUuidSource()

        assertEquals("00000000-0000-d002-0000-000000000001", ids.nextString())
        assertEquals("00000000-0000-d002-0000-000000000002", ids.nextString())
        assertEquals(ids.uuidFor("receipt"), ids.uuidFor("receipt"))
        assertNotEquals(ids.uuidFor("receipt"), ids.uuidFor("inventory"))
    }

    @Test
    fun seedBuildersCreateGenericOfflineMemory() {
        val memory = TestFoodSeeds.memory()

        assertEquals("Generic Eggs", memory.inventory.single().name)
        assertEquals("Generic Rice Bowl", memory.recipes.single().title)
        assertEquals(TestFoodSeeds.TEST_SOURCE, memory.inventory.single().source)
        assertTrue(memory.preferences.customAiInstructions.isBlank())
    }

    @Test
    fun fakeAiGatewayReturnsQueuedTurnsAndRecordsRequests() {
        val gateway = FakeAiGateway()
            .enqueue("add staples", FakeAiTurns.inventoryAdded("Generic Eggs", "Generic Rice"))

        val turn = gateway.interpret("add staples", memory = TestFoodSeeds.memory())
        val draft = turn.draft as InventoryDraft

        assertEquals(listOf("Generic Eggs", "Generic Rice"), draft.items.map { it.name })
        assertEquals("add staples", gateway.requests.single().text)
    }

    @Test
    fun commandEnvelopeFixturesAreAvailableAndParsable() {
        CommandEnvelopeFixtures.all.forEach { fixture ->
            val probe = TestFixtureResources.probe(fixture.path)

            assertTrue("${fixture.path} should be JSON object", probe.isLikelyJsonObject())
            assertEquals("wf.ai.command-envelope.v1", probe.stringValue("schema_version"))
            assertEquals(fixture.skillId, probe.stringValue("skill_id"))
            assertEquals(fixture.expectedCommandCount, probe.countKey("command_id"))
            assertFalse("${fixture.path} contains forbidden data", probe.containsForbiddenTestData())
        }
    }

    @Test
    fun nutritionAndReceiptFixturesAreAvailableAndGeneric() {
        val nutrition = TestFixtureResources.probe("fixtures/nutrition/generic-yogurt-label.json")
        val receipt = TestFixtureResources.probe("fixtures/receipts/generic-market-receipt.json")

        assertTrue(nutrition.isLikelyJsonObject())
        assertEquals("generic_yogurt_label", nutrition.stringValue("fixture_id"))
        assertTrue(nutrition.containsKey("protein_g"))
        assertFalse(nutrition.containsForbiddenTestData())

        assertTrue(receipt.isLikelyJsonObject())
        assertEquals("generic_market_receipt", receipt.stringValue("fixture_id"))
        assertEquals(3, receipt.countKey("description"))
        assertFalse(receipt.containsForbiddenTestData())
    }
}
