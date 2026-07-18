package com.wonderfood.app.ai

import com.wonderfood.app.testing.CommandEnvelopeFixtures
import com.wonderfood.app.testing.TestFixtureResources
import com.wonderfood.core.ai.CommandEnvelopeCodec
import com.wonderfood.core.ai.CommandEnvelopeValidator
import java.security.MessageDigest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CommandEnvelopeFixtureStabilityTest {
    @Test
    fun genericCommandEnvelopeFixturesDecodeValidateAndSerializeStably() {
        CommandEnvelopeFixtures.all.forEach { fixture ->
            val envelope = CommandEnvelopeCodec.decode(TestFixtureResources.readText(fixture.path))
            val encoded = CommandEnvelopeCodec.encode(envelope)

            assertEquals(fixture.expectedCommandCount, envelope.commands.size)
            assertTrue(
                "${fixture.path} validation errors: ${CommandEnvelopeValidator.validate(envelope).errors}",
                CommandEnvelopeValidator.validate(envelope).errors.isEmpty(),
            )
            assertEquals(encoded, CommandEnvelopeCodec.encode(CommandEnvelopeCodec.decode(encoded)))
            expectedCanonicalSha256[fixture.path]?.let { expected ->
                assertEquals(expected, sha256(encoded))
            }
        }
    }

    @Test
    fun nutritionCorrectionFixtureDoesNotDefaultUnknownMacros() {
        val encoded = CommandEnvelopeCodec.encode(
            CommandEnvelopeCodec.decode(
                TestFixtureResources.readText(CommandEnvelopeFixtures.NUTRITION_CORRECTION.path),
            ),
        )

        assertTrue(encoded.contains("\"carbs_g\":null"))
        assertTrue(encoded.contains("\"fat_g\":null"))
        assertTrue(encoded.contains("\"calories\":140"))
        assertTrue(encoded.contains("\"protein_g\":18"))
    }

    private fun sha256(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
        return digest.joinToString(separator = "") { "%02x".format(it) }
    }

    private companion object {
        val expectedCanonicalSha256 = mapOf(
            "fixtures/command-envelopes/inventory-add-generic.json" to
                "4b641abf15ed92e304c3f9f2723faf8dcbaddd2ccf9a095d24a4249676eec6e6",
            "fixtures/command-envelopes/receipt-parse-generic.json" to
                "9ebf24089e13a4b6ae591024d8353ae0f99457324044d2cb513b344919204a2c",
            "fixtures/command-envelopes/nutrition-correction-generic.json" to
                "410c983754beaf4940a24cf5df0f1343ff774b4243270b4ad15663b9bd1b9ba6",
        )
    }
}
