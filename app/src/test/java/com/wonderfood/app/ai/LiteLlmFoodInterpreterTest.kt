package com.wonderfood.app.ai

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import com.wonderfood.app.data.FoodMemory
import com.wonderfood.app.data.MealPlanDraft
import com.wonderfood.app.data.ReceiptDraft
import com.wonderfood.app.data.ReceiptItemDisposition
import java.net.InetSocketAddress
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LiteLlmFoodInterpreterTest {
    private val servers = mutableListOf<HttpServer>()

    @After
    fun tearDown() {
        servers.forEach { it.stop(0) }
        servers.clear()
    }

    @Test
    fun legacyMealPlanResponseDoesNotInventMissingRecipeDraft() {
        val server = startServer(
            status = 200,
            body = chatResponse(
                """
                {
                  "reply": "I drafted the plan for review.",
                  "draft": {
                    "type": "meal_plan",
                    "title": "Tomorrow lunch",
                    "entries": [
                      {"day_offset": 1, "slot": "LUNCH", "title": "Tomato peanut curry"}
                    ]
                  }
                }
                """.trimIndent(),
            ),
        )
        val result = LiteLlmFoodInterpreter().interpretWithDiagnostics(
            text = "Plan tomato peanut curry for tomorrow lunch and save the recipe with tomatoes and peanuts.",
            memory = FoodMemory(),
            config = server.config(),
        )

        assertTrue(result is LiteLlmInterpretation.Success)
        val draft = (result as LiteLlmInterpretation.Success).turn.draft
        assertTrue(draft is MealPlanDraft)
    }

    @Test
    fun invalidProviderContentReturnsDiagnosticFailure() {
        val server = startServer(
            status = 200,
            body = chatResponse("not WonderFood JSON"),
        )
        val result = LiteLlmFoodInterpreter().interpretWithDiagnostics(
            text = "What can I cook?",
            memory = FoodMemory(),
            config = server.config(),
        )

        assertTrue(result is LiteLlmInterpretation.Failure)
        assertTrue(result.diagnostic.contains("invalid WonderFood JSON"))
    }

    @Test
    fun invalidProviderDraftFailsBeforeReview() {
        val server = startServer(
            status = 200,
            body = chatResponse(
                """
                {
                  "reply": "I drafted something.",
                  "draft": {
                    "type": "inventory",
                    "items": []
                  }
                }
                """.trimIndent(),
            ),
        )
        val result = LiteLlmFoodInterpreter().interpretWithDiagnostics(
            text = "Add pantry stuff",
            memory = FoodMemory(),
            config = server.config(),
        )

        assertTrue(result is LiteLlmInterpretation.Failure)
        assertTrue(result.diagnostic.contains("invalid draft"))
        assertTrue(result.diagnostic.contains("inventory draft must include at least one item"))
    }

    @Test
    fun providerErrorDiagnosticRedactsApiKey() {
        val apiKey = "test-key"
        val server = startServer(
            status = 401,
            body = """{"error":"bad key $apiKey"}""",
        )
        val result = LiteLlmFoodInterpreter().interpretWithDiagnostics(
            text = "What can I cook?",
            memory = FoodMemory(),
            config = server.config(apiKey = apiKey),
        )

        assertTrue(result is LiteLlmInterpretation.Failure)
        assertFalse(result.diagnostic.contains(apiKey))
        assertTrue(result.diagnostic.contains("[redacted]"))
    }

    @Test
    fun receiptDraftKeepsEvidenceInferenceAndNonFoodDisposition() {
        val server = startServer(
            status = 200,
            body = chatResponse(
                """
                {
                  "reply": "I extracted every visible receipt line for review.",
                  "draft": {
                    "type": "receipt",
                    "merchant": "Generic Market",
                    "store_location": "Main Street",
                    "currency_code": "USD",
                    "subtotal_cents": 848,
                    "tax_cents": 0,
                    "total_cents": 848,
                    "items": [
                      {
                        "name": "Mini Cucumbers",
                        "receipt_line": "MINI CUCUMBERS 3.49",
                        "line_price_cents": 349,
                        "disposition": "INVENTORY",
                        "zone": "FRIDGE",
                        "category": "vegetable",
                        "confidence": 0.88,
                        "best_before_date": "2026-07-24",
                        "expiry_source": "ai_shelf_life_estimate",
                        "warnings": ["Check the package date"]
                      },
                      {
                        "name": "Oven Cleaner Foam",
                        "receipt_line": "OVEN CLEANER 4.99",
                        "disposition": "HOUSEHOLD",
                        "zone": "PANTRY",
                        "category": "cleaning",
                        "confidence": 0.96
                      }
                    ]
                  }
                }
                """.trimIndent(),
            ),
        )

        val result = LiteLlmFoodInterpreter().interpretWithDiagnostics(
            text = "Review this receipt",
            memory = FoodMemory(),
            config = server.config(),
        ) as LiteLlmInterpretation.Success
        val draft = result.turn.draft as ReceiptDraft

        assertTrue(draft.items.first().food.evidence.contains("CUCUMBERS"))
        assertTrue(draft.items.first().food.expiresAtMillis != null)
        assertTrue(draft.items.first().food.category == "produce")
        assertTrue(draft.items.last().disposition == ReceiptItemDisposition.HOUSEHOLD)
        assertTrue(draft.storeLocation == "Main Street")
        assertTrue(draft.items.first().linePriceCents == 349L)
        assertTrue(draft.totalCents == 848L)
    }

    private fun startServer(status: Int, body: String): HttpServer {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/chat/completions") { exchange ->
            exchange.requestBody.use { it.readBytes() }
            exchange.respond(status, body)
        }
        server.start()
        servers += server
        return server
    }

    private fun HttpExchange.respond(status: Int, body: String) {
        val bytes = body.toByteArray(Charsets.UTF_8)
        sendResponseHeaders(status, bytes.size.toLong())
        responseBody.use { it.write(bytes) }
    }

    private fun HttpServer.config(apiKey: String = "test-key"): LiteLlmConfig =
        LiteLlmConfig(
            baseUrl = "http://127.0.0.1:${address.port}",
            apiKey = apiKey,
            model = "test-model",
            provider = AiProvider.OPENAI_COMPATIBLE,
        )

    private fun chatResponse(content: String): String =
        """
        {
          "choices": [
            {
              "message": {
                "content": ${content.jsonQuoted()}
              }
            }
          ]
        }
        """.trimIndent()

    private fun String.jsonQuoted(): String =
        buildString {
            append('"')
            this@jsonQuoted.forEach { char ->
                when (char) {
                    '\\' -> append("\\\\")
                    '"' -> append("\\\"")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
                    else -> append(char)
                }
            }
            append('"')
        }
}
