package com.wonderfood.app.integration.capture

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class FoodCaptureGatewayTest {
    private lateinit var context: Context
    private lateinit var gateway: FoodCaptureGateway

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        File(context.filesDir, "wonderfood_captures").deleteRecursively()
        gateway = FoodCaptureGateway(context, clockMillis = { 1_784_224_800_000L })
    }

    @Test
    fun receiptPhotoIsCopiedToPrivateStorageAndReencoded() {
        val source = writeBitmap("generic-receipt-source.png")

        val record = gateway.stageReceiptPhoto(Uri.fromFile(source))

        assertEquals(FoodCaptureKind.RECEIPT_PHOTO, record.kind)
        assertEquals(FoodCaptureStatus.STAGED, record.status)
        assertEquals(MetadataPolicy.STRIPPED_IMAGE_REENCODED, record.metadataPolicy)
        assertNotEquals(source.toURI().toString(), record.privateUri.toString())
        assertTrue(record.privateUri?.path.orEmpty().contains("/wonderfood_captures/receipts/"))
        assertTrue(File(record.privateUri?.path.orEmpty()).exists())
        assertTrue(record.reviewRequired)
        assertTrue(record.aiPrompt.contains("review", ignoreCase = true))
    }

    @Test
    fun unreadableImageIsStillRecoverableAsPrivateCopy() {
        val source = File(context.cacheDir, "not-an-image.txt").apply {
            writeText("generic receipt text fallback")
        }

        val record = gateway.stageReceiptPhoto(Uri.fromFile(source))

        assertEquals(FoodCaptureStatus.RECOVERABLE_INPUT_NEEDED, record.status)
        assertEquals(MetadataPolicy.RAW_COPY_FALLBACK, record.metadataPolicy)
        assertTrue(File(record.privateUri?.path.orEmpty()).exists())
        assertTrue(record.evidenceText.contains("retry", ignoreCase = true))
        assertTrue(record.reviewRequired)
    }

    @Test
    fun barcodeCaptureCreatesReviewableEvidenceWithoutImageStorage() {
        val record = gateway.stageBarcode("012345678905", format = "UPC_A")

        assertEquals(FoodCaptureKind.BARCODE, record.kind)
        assertEquals(FoodCaptureStatus.STAGED, record.status)
        assertEquals(MetadataPolicy.NOT_APPLICABLE, record.metadataPolicy)
        assertEquals(null, record.privateUri)
        assertTrue(record.evidenceText.contains("UPC_A:012345678905"))
        assertTrue(record.evidenceText.contains("Generic Rolled Oats"))
        assertTrue(record.evidenceText.contains("bundled_barcode_provider"))
        assertTrue(record.aiPrompt.contains("barcode", ignoreCase = true))
        assertTrue(record.reviewRequired)
    }

    private fun writeBitmap(name: String): File {
        val file = File(context.cacheDir, name)
        val bitmap = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)
        file.outputStream().use { output ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
        }
        return file
    }
}
