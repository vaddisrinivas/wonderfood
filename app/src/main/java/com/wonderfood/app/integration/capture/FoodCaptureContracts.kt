package com.wonderfood.app.integration.capture

import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts

object FoodCaptureContracts {
    fun imageOnlyRequest(): PickVisualMediaRequest =
        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)

    val photoPicker: ActivityResultContracts.PickVisualMedia
        get() = ActivityResultContracts.PickVisualMedia()

    val cameraImage: ActivityResultContracts.TakePicture
        get() = ActivityResultContracts.TakePicture()
}
