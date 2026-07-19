package com.wonderfood.app.integration.capture

import android.content.Context
import android.net.Uri
import com.wonderfood.app.ai.LiteLlmConfig
import com.wonderfood.app.ai.LiteLlmFoodInterpreter
import com.wonderfood.app.data.AiTurn
import com.wonderfood.app.data.FoodCandidate
import com.wonderfood.app.data.FoodMemory
import com.wonderfood.app.data.TrustedFoodLookup

interface BarcodeLookupProvider {
    val sourceLabel: String
    fun lookupBarcode(value: String): FoodCandidate?
}

class BundledBarcodeLookupProvider : BarcodeLookupProvider {
    override val sourceLabel: String = "bundled_barcode_provider"

    override fun lookupBarcode(value: String): FoodCandidate? = TrustedFoodLookup.lookupBarcode(value)
}

class ProductionBarcodeLookupProvider(
    private val delegate: BarcodeLookupProvider = BundledBarcodeLookupProvider(),
) : BarcodeLookupProvider {
    override val sourceLabel: String = "production_barcode_provider"

    override fun lookupBarcode(value: String): FoodCandidate? = delegate.lookupBarcode(value)
}

interface ReceiptServingPicker {
    val providerName: String
    fun pickServingText(rawLine: String, candidate: FoodCandidate): String
}

class ProductionReceiptServingPicker : ReceiptServingPicker {
    override val providerName: String = "production_receipt_serving_picker"

    override fun pickServingText(rawLine: String, candidate: FoodCandidate): String =
        buildString {
            if (candidate.servingText.isNotBlank()) append(candidate.servingText.trim())
            if (isEmpty() && candidate.quantity.isNotBlank()) append(candidate.quantity.lowercase())
            if (isEmpty()) append(rawLine.trim())
        }
}

interface ReceiptCaptureProvider {
    val providerName: String
    fun interpretReceiptPhoto(
        context: Context,
        uri: Uri,
        memory: FoodMemory,
        config: LiteLlmConfig,
        userNote: String,
    ): AiTurn?
}

class ProductionReceiptCaptureProvider(
    private val interpreter: LiteLlmFoodInterpreter,
) : ReceiptCaptureProvider {
    override val providerName: String = "production_receipt_provider"

    override fun interpretReceiptPhoto(
        context: Context,
        uri: Uri,
        memory: FoodMemory,
        config: LiteLlmConfig,
        userNote: String,
    ): AiTurn? = interpreter.interpretReceiptPhoto(context, uri, memory, config, userNote)
}
