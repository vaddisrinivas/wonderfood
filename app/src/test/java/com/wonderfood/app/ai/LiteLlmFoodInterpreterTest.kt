package com.wonderfood.app.ai

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import com.wonderfood.app.data.HouseholdUiMemory
import com.wonderfood.app.data.FoodDraft
import com.wonderfood.app.data.FoodDraftNormalizer
import com.wonderfood.app.data.GroceryDraft
import com.wonderfood.app.data.MealPlanDraft
import com.wonderfood.app.data.ReceiptDraft
import com.wonderfood.app.data.ReceiptItemDisposition
import java.net.InetSocketAddress
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LiteLlmFoodInterpreterTest {
    private val servers = mutableListOf<HttpServer>()

    private data class CapturedRequest(
        val path: String,
        val query: String?,
        val apiKey: String?,
        val body: String,
    )

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
            memory = HouseholdUiMemory(),
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
            memory = HouseholdUiMemory(),
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
            memory = HouseholdUiMemory(),
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
            memory = HouseholdUiMemory(),
            config = server.config(apiKey = apiKey),
        )

        assertTrue(result is LiteLlmInterpretation.Failure)
        assertFalse(result.diagnostic.contains(apiKey))
        assertTrue(result.diagnostic.contains("[redacted]"))
    }

    @Test
    fun connectionTestReportsProviderHttpFailureInsteadOfFalseSuccess() {
        val apiKey = "connection-test-key"
        val server = startServer(
            status = 401,
            body = """{"error":"invalid key $apiKey"}""",
        )

        val result = LiteLlmFoodInterpreter().testConnection(server.config(apiKey = apiKey))

        assertTrue(result.isFailure)
        val message = result.exceptionOrNull()?.message.orEmpty()
        assertTrue(message.contains("HTTP 401"))
        assertFalse(message.contains(apiKey))
        assertTrue(message.contains("[redacted]"))
    }

    @Test
    fun azureResponsesEndpointIsUsedDirectlyAndParsesOutputText() {
        var captured: CapturedRequest? = null
        val server = startServer(
            path = "/openai/v1/responses",
            status = 200,
            body = responsesResponse("""{"reply":"Azure Responses works.","draft":null}"""),
            onRequest = { captured = it },
        )
        val config = server.config(
            basePath = "/openai/v1/responses",
            provider = AiProvider.AZURE_OPENAI,
        )

        val result = LiteLlmFoodInterpreter().interpretWithDiagnostics(
            text = "What can I cook?",
            memory = HouseholdUiMemory(),
            config = config,
        )

        assertTrue(result is LiteLlmInterpretation.Success)
        assertEquals("Azure Responses works.", (result as LiteLlmInterpretation.Success).turn.reply)
        val request = captured
        assertNotNull(request)
        assertEquals("/openai/v1/responses", request?.path)
        assertEquals("test-key", request?.apiKey)
        val body = JSONObject(request?.body.orEmpty())
        assertEquals("test-model", body.optString("model"))
        assertTrue(body.has("instructions"))
        assertTrue(body.has("input"))
        assertFalse(body.has("messages"))
        assertFalse(body.has("response_format"))
    }

    @Test
    fun responsesUrlCitationsAreReturnedAsChatSources() {
        val server = startServer(
            path = "/v1/responses",
            status = 200,
            body = responsesResponseWithUrlCitation("""{"reply":"Use the FDA guidance.","draft":null}"""),
        )

        val result = LiteLlmFoodInterpreter().interpretWithDiagnostics(
            text = "Can I store cooked rice safely?",
            memory = HouseholdUiMemory(),
            config = server.config(basePath = "/v1/responses"),
        )

        assertTrue(result is LiteLlmInterpretation.Success)
        val sources = (result as LiteLlmInterpretation.Success).sources
        assertEquals(2, sources.size)
        assertEquals("USDA Ask Extension", sources.first().title)
        assertEquals("FDA Food Safety", sources.last().title)
        assertEquals("https://www.fda.gov/food", sources.last().uri)
        assertTrue(sources.last().quote.contains("FDA"))
    }

    @Test
    fun azureV1ChatEndpointKeepsModelInRequestBody() {
        var captured: CapturedRequest? = null
        val server = startServer(
            path = "/openai/v1/chat/completions",
            status = 200,
            body = chatResponse("""{"reply":"Azure chat works.","draft":null}"""),
            onRequest = { captured = it },
        )
        val config = server.config(
            basePath = "/openai/v1/chat/completions",
            provider = AiProvider.AZURE_OPENAI,
        )

        val result = LiteLlmFoodInterpreter().interpretWithDiagnostics(
            text = "What can I cook?",
            memory = HouseholdUiMemory(),
            config = config,
        )

        assertTrue(result is LiteLlmInterpretation.Success)
        val request = captured
        assertEquals("/openai/v1/chat/completions", request?.path)
        assertEquals("test-model", JSONObject(request?.body.orEmpty()).optString("model"))
    }

    @Test
    fun legacyAzureBaseBuildsDeploymentPathAndApiVersion() {
        var captured: CapturedRequest? = null
        val server = startServer(
            path = "/openai/deployments/test-model/chat/completions",
            status = 200,
            body = chatResponse("""{"reply":"Legacy Azure works.","draft":null}"""),
            onRequest = { captured = it },
        )
        val config = server.config(
            provider = AiProvider.AZURE_OPENAI,
            apiVersion = "2024-10-21",
        )

        val result = LiteLlmFoodInterpreter().interpretWithDiagnostics(
            text = "What can I cook?",
            memory = HouseholdUiMemory(),
            config = config,
        )

        assertTrue(result is LiteLlmInterpretation.Success)
        val request = captured
        assertEquals("/openai/deployments/test-model/chat/completions", request?.path)
        assertEquals("api-version=2024-10-21", request?.query)
        assertFalse(JSONObject(request?.body.orEmpty()).has("model"))
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
            memory = HouseholdUiMemory(),
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

    @Test
    fun deterministicAiDraftMatchesLocalGroceryParserForEquivalentInput() {
        val server = startServer(
            status = 200,
            body = chatResponse(
                """
                {
                  "reply": "I drafted the grocery request.",
                  "draft": {
                    "type": "grocery",
                    "items": [
                      {
                        "name": "Oats",
                        "quantity": "2",
                        "zone": "PANTRY",
                        "category": "grain"
                      }
                    ]
                  }
                }
                """.trimIndent(),
            ),
        )

        val text = "Need 2 oats"
        val aiResult = LiteLlmFoodInterpreter().interpretWithDiagnostics(
            text = text,
            memory = HouseholdUiMemory(),
            config = server.config(),
        )
        val localResult = FoodInterpreter().interpret(
            text = text,
            memory = HouseholdUiMemory(),
            promptContext = "Current WonderFood section: Shop.",
        )

        assertTrue(aiResult is LiteLlmInterpretation.Success)
        val aiDraft = FoodDraftNormalizer.normalize(requireNotNull((aiResult as LiteLlmInterpretation.Success).turn.draft)) as GroceryDraft
        val localDraft = FoodDraftNormalizer.normalize(requireNotNull(localResult.draft)) as GroceryDraft
        assertEquals(localDraft.toCanonicalShopping(), aiDraft.toCanonicalShopping())
    }

    private fun startServer(
        status: Int,
        body: String,
        path: String = "/chat/completions",
        onRequest: (CapturedRequest) -> Unit = {},
    ): HttpServer {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext(path) { exchange ->
            val requestBody = exchange.requestBody.use { it.readBytes().toString(Charsets.UTF_8) }
            onRequest(
                CapturedRequest(
                    path = exchange.requestURI.path,
                    query = exchange.requestURI.rawQuery,
                    apiKey = exchange.requestHeaders.getFirst("api-key"),
                    body = requestBody,
                ),
            )
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

    private fun GroceryDraft.toCanonicalShopping(): List<String> =
        items.map {
            listOf(
                it.name.lowercase(),
                it.quantity,
                it.zone.name,
                it.category.lowercase(),
            ).joinToString("|")
        }.sorted()

    private fun HttpServer.config(
        apiKey: String = "test-key",
        basePath: String = "",
        provider: AiProvider = AiProvider.OPENAI_COMPATIBLE,
        apiVersion: String = "",
    ): LiteLlmConfig =
        LiteLlmConfig(
            baseUrl = "http://127.0.0.1:${address.port}$basePath",
            apiKey = apiKey,
            model = "test-model",
            provider = provider,
            apiVersion = apiVersion,
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

    private fun responsesResponse(content: String): String =
        """
        {
          "output": [
            {
              "type": "message",
              "role": "assistant",
              "content": [
                {
                  "type": "output_text",
                  "text": ${content.jsonQuoted()}
                }
              ]
            }
          ]
        }
        """.trimIndent()

    private fun responsesResponseWithUrlCitation(content: String): String =
        """
        {
          "output": [
            {
              "type": "web_search_call",
              "action": {
                "sources": [
                  {
                    "title": "USDA Ask Extension",
                    "url": "https://ask.usda.gov/",
                    "snippet": "Food safety answers from USDA."
                  }
                ]
              }
            },
            {
              "type": "message",
              "role": "assistant",
              "content": [
                {
                  "type": "output_text",
                  "text": ${content.jsonQuoted()},
                  "annotations": [
                    {
                      "type": "url_citation",
                      "title": "FDA Food Safety",
                      "url": "https://www.fda.gov/food",
                      "start_index": 15,
                      "end_index": 27
                    }
                  ]
                }
              ]
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
