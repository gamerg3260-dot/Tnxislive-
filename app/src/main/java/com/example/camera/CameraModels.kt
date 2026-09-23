package com.example.camera

import android.graphics.Bitmap
import android.net.Uri

data class CameraCaptureResult(
    val bitmap: Bitmap,
    val uri: Uri? = null,
    val filePath: String? = null,
    val isFrontCamera: Boolean = false,
    val isSavedToGallery: Boolean = true,
    val timestamp: Long = System.currentTimeMillis()
)

sealed class CameraActionResult {
    data class PhotoSaved(
        val result: CameraCaptureResult,
        val messageHindi: String,
        val voiceResponseHindi: String
    ) : CameraActionResult()

    data class SceneAnalyzed(
        val bitmap: Bitmap,
        val analysisHindi: String,
        val voiceResponseHindi: String
    ) : CameraActionResult()

    data class PermissionRequired(
        val messageHindi: String,
        val voiceResponseHindi: String
    ) : CameraActionResult()

    data class Error(
        val messageHindi: String,
        val voiceResponseHindi: String
    ) : CameraActionResult()
}
