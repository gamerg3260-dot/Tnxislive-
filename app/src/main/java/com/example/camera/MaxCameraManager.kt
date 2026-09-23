package com.example.camera

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.coroutines.resume

class MaxCameraManager(
    private val context: Context
) {
    private val tag = "MaxCameraManager"

    fun hasCameraPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Checks if the voice command relates to photo/selfie or scene analysis.
     */
    fun isCameraCommand(rawCommand: String): Boolean {
        val lower = rawCommand.lowercase().trim()
        val keywords = listOf(
            "selfie", "सेल्फी", "फोटो", "photo", "picture", "तस्वीर",
            "camera", "कैमरा", "खींच", "kheecho", "kheencho", "pic lo",
            "saamne kya", "samne kya", "सामने क्या", "सामने देखो",
            "yeh kya hai", "ye kya hai", "यह क्या है", "dekho kya",
            "scene", "दृश्य", "kya dikh raha hai", "क्या दिख रहा है"
        )
        return keywords.any { lower.contains(it) }
    }

    /**
     * Checks if the user is asking to analyze what is in front of the camera.
     */
    fun isSceneAnalysisCommand(rawCommand: String): Boolean {
        val lower = rawCommand.lowercase().trim()
        val analysisPhrases = listOf(
            "saamne kya hai", "samne kya hai", "सामने क्या है", "सामने क्या दिख रहा",
            "yeh kya hai", "ye kya hai", "यह क्या है", "ये क्या है",
            "dekho aur batao", "camera se dekho", "कैमरा से देखो",
            "kya dikh raha", "क्या दिख रहा", "scene analyze", "सामने का दृश्य",
            "kya rakha hai", "koun hai samne", "पहचानो क्या है"
        )
        return analysisPhrases.any { lower.contains(it) }
    }

    /**
     * Determines if front camera is requested (Selfie) or back camera.
     */
    fun isFrontCamera(rawCommand: String): Boolean {
        val lower = rawCommand.lowercase().trim()
        val frontKeywords = listOf(
            "selfie", "सेल्फी", "meri photo", "मेरी फोटो", "front", "फ्रंट",
            "aage wala", "आगे वाला"
        )
        val backKeywords = listOf(
            "peeche", "पीछे", "back camera", "बैक", "saamne", "सामने"
        )

        if (backKeywords.any { lower.contains(it) }) return false
        if (frontKeywords.any { lower.contains(it) }) return true

        // Default: If "selfie" -> true, else general "photo" -> false (back camera)
        return lower.contains("selfie") || lower.contains("सेल्फी")
    }

    /**
     * Captures a photo using CameraX in headless mode.
     * Can either save to gallery (DCIM/Max) or return in-memory Bitmap for Scene Analysis.
     */
    suspend fun capturePhoto(
        isFrontCamera: Boolean,
        saveToGallery: Boolean = true
    ): Result<CameraCaptureResult> = withContext(Dispatchers.IO) {
        if (!hasCameraPermission()) {
            return@withContext Result.failure(SecurityException("CAMERA permission not granted"))
        }

        try {
            val bitmap = captureImageProxyBitmap(isFrontCamera)
                ?: return@withContext Result.failure(IllegalStateException("Failed to capture image frame from camera"))

            var savedUri: Uri? = null
            var savedPath: String? = null

            if (saveToGallery) {
                val saveResult = saveBitmapToGallery(bitmap, isFrontCamera)
                savedUri = saveResult.first
                savedPath = saveResult.second
            }

            val result = CameraCaptureResult(
                bitmap = bitmap,
                uri = savedUri,
                filePath = savedPath,
                isFrontCamera = isFrontCamera,
                isSavedToGallery = saveToGallery
            )
            Result.success(result)
        } catch (e: Exception) {
            Log.e(tag, "Camera capture error", e)
            Result.failure(e)
        }
    }

    /**
     * PART 3: Background / Silent photo capture (ready for anti-theft & background surveillance).
     * Takes photo without showing any camera UI preview.
     */
    suspend fun captureSilentPhoto(isFrontCamera: Boolean = true): Result<Bitmap> = withContext(Dispatchers.IO) {
        if (!hasCameraPermission()) {
            return@withContext Result.failure(SecurityException("CAMERA permission not granted"))
        }
        try {
            val bitmap = captureImageProxyBitmap(isFrontCamera)
                ?: return@withContext Result.failure(IllegalStateException("Silent capture failed"))
            Result.success(bitmap)
        } catch (e: Exception) {
            Log.e(tag, "Silent photo capture error", e)
            Result.failure(e)
        }
    }

    /**
     * Binds CameraX ImageCapture to a standalone LifecycleOwner and captures a frame.
     */
    private suspend fun captureImageProxyBitmap(isFrontCamera: Boolean): Bitmap? =
        suspendCancellableCoroutine { continuation ->
            val mainExecutor = ContextCompat.getMainExecutor(context)

            val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
            cameraProviderFuture.addListener({
                try {
                    val cameraProvider = cameraProviderFuture.get()
                    val cameraSelector = if (isFrontCamera) {
                        CameraSelector.DEFAULT_FRONT_CAMERA
                    } else {
                        CameraSelector.DEFAULT_BACK_CAMERA
                    }

                    // Check if requested camera is available
                    val hasCamera = try {
                        cameraProvider.hasCamera(cameraSelector)
                    } catch (e: Exception) {
                        false
                    }

                    val finalSelector = if (hasCamera) {
                        cameraSelector
                    } else {
                        CameraSelector.DEFAULT_BACK_CAMERA
                    }

                    val imageCapture = ImageCapture.Builder()
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                        .setTargetRotation(android.view.Surface.ROTATION_0)
                        .build()

                    val lifecycleOwner = HeadlessLifecycleOwner()
                    lifecycleOwner.start()

                    try {
                        cameraProvider.unbindAll()
                        cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            finalSelector,
                            imageCapture
                        )
                    } catch (e: Exception) {
                        Log.e(tag, "Failed to bind camera lifecycle", e)
                        lifecycleOwner.stop()
                        continuation.resume(null)
                        return@addListener
                    }

                    // Wait a brief moment for camera AE/AF to stabilize before capture
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        imageCapture.takePicture(
                            mainExecutor,
                            object : ImageCapture.OnImageCapturedCallback() {
                                override fun onCaptureSuccess(imageProxy: ImageProxy) {
                                    val rotationDegrees = imageProxy.imageInfo.rotationDegrees
                                    val originalBitmap = imageProxy.toBitmap()
                                    imageProxy.close()

                                    val finalBitmap = if (rotationDegrees != 0 || isFrontCamera) {
                                        val matrix = Matrix()
                                        if (rotationDegrees != 0) {
                                            matrix.postRotate(rotationDegrees.toFloat())
                                        }
                                        if (isFrontCamera) {
                                            // Mirror front camera to match selfie expectation
                                            matrix.postScale(-1f, 1f)
                                        }
                                        Bitmap.createBitmap(
                                            originalBitmap,
                                            0,
                                            0,
                                            originalBitmap.width,
                                            originalBitmap.height,
                                            matrix,
                                            true
                                        )
                                    } else {
                                        originalBitmap
                                    }

                                    // Cleanup Camera binding
                                    try {
                                        cameraProvider.unbindAll()
                                        lifecycleOwner.stop()
                                    } catch (ignored: Exception) {}

                                    continuation.resume(finalBitmap)
                                }

                                override fun onError(exception: ImageCaptureException) {
                                    Log.e(tag, "Camera takePicture failed", exception)
                                    try {
                                        cameraProvider.unbindAll()
                                        lifecycleOwner.stop()
                                    } catch (ignored: Exception) {}
                                    continuation.resume(null)
                                }
                            }
                        )
                    }, 250) // 250ms AE settling time

                } catch (e: Exception) {
                    Log.e(tag, "Exception during CameraX snapshot setup", e)
                    continuation.resume(null)
                }
            }, mainExecutor)
        }

    /**
     * Saves bitmap to MediaStore under DCIM/Max folder.
     */
    private fun saveBitmapToGallery(bitmap: Bitmap, isFrontCamera: Boolean): Pair<Uri?, String?> {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val prefix = if (isFrontCamera) "SELFIE" else "PHOTO"
        val fileName = "MAX_${prefix}_$timeStamp.jpg"

        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                    put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_DCIM + File.separator + "Max")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }

                val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                if (uri != null) {
                    context.contentResolver.openOutputStream(uri)?.use { stream ->
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 95, stream)
                    }
                    values.clear()
                    values.put(MediaStore.Images.Media.IS_PENDING, 0)
                    context.contentResolver.update(uri, values, null, null)
                    Pair(uri, "DCIM/Max/$fileName")
                } else {
                    Pair(null, null)
                }
            } else {
                val dcimDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM)
                val maxDir = File(dcimDir, "Max")
                if (!maxDir.exists()) maxDir.mkdirs()
                val imageFile = File(maxDir, fileName)

                FileOutputStream(imageFile).use { stream ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 95, stream)
                }
                val uri = Uri.fromFile(imageFile)
                Pair(uri, imageFile.absolutePath)
            }
        } catch (e: Exception) {
            Log.e(tag, "Error saving bitmap to gallery", e)
            Pair(null, null)
        }
    }

    /**
     * Standalone LifecycleOwner for running CameraX in headless/background operations.
     */
    private class HeadlessLifecycleOwner : LifecycleOwner {
        private val lifecycleRegistry = LifecycleRegistry(this)

        override val lifecycle: Lifecycle
            get() = lifecycleRegistry

        fun start() {
            lifecycleRegistry.currentState = Lifecycle.State.CREATED
            lifecycleRegistry.currentState = Lifecycle.State.STARTED
            lifecycleRegistry.currentState = Lifecycle.State.RESUMED
        }

        fun stop() {
            lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        }
    }
}
