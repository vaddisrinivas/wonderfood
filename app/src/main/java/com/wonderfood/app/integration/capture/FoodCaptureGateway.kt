package com.wonderfood.app.integration.capture

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File

enum class FoodCaptureKind(val directoryName: String) {
    RECEIPT_PHOTO("receipts"),
    LABEL_PHOTO("labels"),
    MEAL_PHOTO("meals"),
    BARCODE("barcodes"),
}

enum class FoodCaptureStatus {
    STAGED,
    RECOVERABLE_INPUT_NEEDED,
}

enum class MetadataPolicy {
    STRIPPED_IMAGE_REENCODED,
    RAW_COPY_FALLBACK,
    NOT_APPLICABLE,
}

data class FoodCaptureRecord(
    val id: String,
    val kind: FoodCaptureKind,
    val originalUri: String?,
    val privateUri: Uri?,
    val evidenceText: String,
    val status: FoodCaptureStatus,
    val metadataPolicy: MetadataPolicy,
    val createdAtMillis: Long,
    val retryToken: String,
    val reviewRequired: Boolean,
) {
    val aiPrompt: String
        get() = when (kind) {
            FoodCaptureKind.RECEIPT_PHOTO -> "Extract grocery or pantry items from this private receipt image. Stage rows for review only."
            FoodCaptureKind.LABEL_PHOTO -> "Extract nutrition label facts from this private label image. Preserve unknown nutrition as unknown."
            FoodCaptureKind.MEAL_PHOTO -> "Describe this meal photo for a reviewable meal log. Do not write inventory directly."
            FoodCaptureKind.BARCODE -> "Look up barcode evidence for a reviewable grocery or pantry item: $evidenceText"
        }
}

class FoodCaptureGateway(
    private val context: Context,
    private val clockMillis: () -> Long = { System.currentTimeMillis() },
    private val barcodeLookupProvider: BarcodeLookupProvider = ProductionBarcodeLookupProvider(),
    private val servingPicker: ReceiptServingPicker = ProductionReceiptServingPicker(),
) {
    fun stageReceiptPhoto(uri: Uri): FoodCaptureRecord =
        stageImage(FoodCaptureKind.RECEIPT_PHOTO, uri)

    fun stageLabelPhoto(uri: Uri): FoodCaptureRecord =
        stageImage(FoodCaptureKind.LABEL_PHOTO, uri)

    fun stageMealPhoto(uri: Uri): FoodCaptureRecord =
        stageImage(FoodCaptureKind.MEAL_PHOTO, uri)

    fun stageBarcode(value: String, format: String? = null): FoodCaptureRecord {
        val cleanValue = value.trim()
        require(cleanValue.isNotBlank()) { "Barcode value must not be blank." }
        val now = clockMillis()
        val id = captureId(FoodCaptureKind.BARCODE, now, cleanValue)
        val lookup = barcodeLookupProvider.lookupBarcode(cleanValue)
            ?.let { candidate -> candidate.copy(servingText = servingPicker.pickServingText(cleanValue, candidate)) }
        val evidence = buildList {
            add(listOfNotNull(format?.trim()?.ifBlank { null }, cleanValue).joinToString(":"))
            if (lookup != null) {
                add("provider=${barcodeLookupProvider.sourceLabel}")
                add("item=${lookup.name}")
                add("serving=${lookup.servingText}")
                add("nutrition_source=${lookup.nutritionSource}")
            } else {
                add("provider=unknown")
            }
        }.joinToString("\n")
        return FoodCaptureRecord(
            id = id,
            kind = FoodCaptureKind.BARCODE,
            originalUri = null,
            privateUri = null,
            evidenceText = evidence,
            status = FoodCaptureStatus.STAGED,
            metadataPolicy = MetadataPolicy.NOT_APPLICABLE,
            createdAtMillis = now,
            retryToken = id,
            reviewRequired = true,
        )
    }

    fun deletePrivateCapture(record: FoodCaptureRecord): Boolean {
        val path = record.privateUri?.path ?: return false
        val file = File(path)
        return file.exists() && file.delete()
    }

    private fun stageImage(kind: FoodCaptureKind, uri: Uri): FoodCaptureRecord {
        val now = clockMillis()
        val id = captureId(kind, now, uri.toString())
        val directory = File(context.filesDir, "wonderfood_captures/${kind.directoryName}").apply { mkdirs() }
        val reencoded = File(directory, "$id.jpg")
        val metadataPolicy = if (decodeAndReencode(uri, reencoded)) {
            MetadataPolicy.STRIPPED_IMAGE_REENCODED
        } else {
            val fallback = File(directory, "$id.bin")
            copyRaw(uri, fallback)
            return imageRecord(
                id = id,
                kind = kind,
                originalUri = uri,
                privateFile = fallback,
                status = FoodCaptureStatus.RECOVERABLE_INPUT_NEEDED,
                metadataPolicy = MetadataPolicy.RAW_COPY_FALLBACK,
                createdAtMillis = now,
            )
        }
        return imageRecord(
            id = id,
            kind = kind,
            originalUri = uri,
            privateFile = reencoded,
            status = FoodCaptureStatus.STAGED,
            metadataPolicy = metadataPolicy,
            createdAtMillis = now,
        )
    }

    private fun imageRecord(
        id: String,
        kind: FoodCaptureKind,
        originalUri: Uri,
        privateFile: File,
        status: FoodCaptureStatus,
        metadataPolicy: MetadataPolicy,
        createdAtMillis: Long,
    ): FoodCaptureRecord =
        FoodCaptureRecord(
            id = id,
            kind = kind,
            originalUri = originalUri.toString(),
            privateUri = Uri.fromFile(privateFile),
            evidenceText = if (status == FoodCaptureStatus.STAGED) {
                "Private ${kind.name.lowercase()} image staged for review."
            } else {
                "Private image copy saved, but OCR or image decoding needs retry/manual text."
            },
            status = status,
            metadataPolicy = metadataPolicy,
            createdAtMillis = createdAtMillis,
            retryToken = id,
            reviewRequired = true,
        )

    private fun decodeAndReencode(uri: Uri, destination: File): Boolean {
        val bitmap = context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream)
        } ?: return false
        return destination.outputStream().use { output ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 92, output)
        }
    }

    private fun copyRaw(uri: Uri, destination: File) {
        context.contentResolver.openInputStream(uri)?.use { input ->
            destination.outputStream().use { output -> input.copyTo(output) }
        } ?: destination.writeBytes(ByteArray(0))
    }

    private fun captureId(kind: FoodCaptureKind, now: Long, seed: String): String {
        val safeHash = seed.hashCode().toUInt().toString(16)
        return "${kind.name.lowercase()}-$now-$safeHash"
    }
}
