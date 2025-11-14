package com.roadguardian.auto

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import android.util.Log
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.roadguardian.auto.models.Detection
import com.roadguardian.auto.ui.DetectionOverlayView
import java.io.ByteArrayOutputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class CameraManager(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val previewView: PreviewView,
    private val overlayView: DetectionOverlayView,
    private val detectionManager: DetectionManager,
    private val currentLanguageProvider: () -> String,
    private val onDetectionsUpdate: (List<Detection>) -> Unit
) {

    private var cameraProvider: ProcessCameraProvider? = null
    private var cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private var imageAnalyzer: ImageAnalysis? = null
    private var isCameraActive = false
    private var frameCount = 0

    companion object {
        private const val TAG = "CameraManager"
        private const val FRAME_SKIP = 3 // Procesar 1 de cada 3 frames para rendimiento
    }

    fun initializeCamera() {
        Log.d(TAG, "🎥 Inicializando cámara...")
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)

        cameraProviderFuture.addListener({
            try {
                cameraProvider = cameraProviderFuture.get()
                startCamera()
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error obteniendo CameraProvider: ${e.message}", e)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    @SuppressLint("UnsafeOptInUsageError")
    private fun startCamera() {
        try {
            val cameraProvider = cameraProvider ?: run {
                Log.e(TAG, "❌ CameraProvider es null")
                return
            }

            // Preview
            val preview = Preview.Builder()
                .setTargetRotation(previewView.display.rotation)
                .build()
                .also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

            // Image Analysis
            imageAnalyzer = ImageAnalysis.Builder()
                .setTargetResolution(android.util.Size(640, 480))
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                .build()

            imageAnalyzer?.setAnalyzer(cameraExecutor) { imageProxy ->
                processImageProxy(imageProxy)
            }

            // Camera selector
            val cameraSelector = CameraSelector.Builder()
                .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                .build()

            // Unbind previous use cases
            cameraProvider.unbindAll()

            // Bind use cases
            cameraProvider.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                preview,
                imageAnalyzer
            )

            isCameraActive = true
            Log.i(TAG, "✅ Cámara iniciada correctamente")

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error iniciando cámara: ${e.message}", e)
            isCameraActive = false
        }
    }

    @SuppressLint("UnsafeOptInUsageError")
    private fun processImageProxy(imageProxy: ImageProxy) {
        frameCount++
        
        // Saltar frames para mejorar rendimiento
        if (frameCount % FRAME_SKIP != 0) {
            imageProxy.close()
            return
        }

        try {
            val bitmap = imageProxyToBitmap(imageProxy)
            
            if (bitmap == null) {
                Log.w(TAG, "⚠️ Bitmap es null, saltando frame")
                imageProxy.close()
                return
            }

            Log.d(TAG, "🖼️ Procesando frame ${frameCount}: ${bitmap.width}x${bitmap.height}")

            // Detectar animales
            val detections = detectionManager.detectAnimals(bitmap)

            // Actualizar UI en el hilo principal
            if (detections.isNotEmpty()) {
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    updateDetections(detections)
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error procesando frame: ${e.message}", e)
        } finally {
            imageProxy.close()
        }
    }

    private fun imageProxyToBitmap(imageProxy: ImageProxy): Bitmap? {
        return try {
            val yBuffer = imageProxy.planes[0].buffer
            val uBuffer = imageProxy.planes[1].buffer
            val vBuffer = imageProxy.planes[2].buffer

            val ySize = yBuffer.remaining()
            val uSize = uBuffer.remaining()
            val vSize = vBuffer.remaining()

            val nv21 = ByteArray(ySize + uSize + vSize)
            
            yBuffer.get(nv21, 0, ySize)
            vBuffer.get(nv21, ySize, vSize)
            uBuffer.get(nv21, ySize + vSize, uSize)

            val yuvImage = YuvImage(
                nv21,
                ImageFormat.NV21,
                imageProxy.width,
                imageProxy.height,
                null
            )

            val out = ByteArrayOutputStream()
            yuvImage.compressToJpeg(
                Rect(0, 0, imageProxy.width, imageProxy.height),
                80,
                out
            )
            
            val imageBytes = out.toByteArray()
            BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error convirtiendo ImageProxy a Bitmap: ${e.message}", e)
            null
        }
    }

    private fun updateDetections(detections: List<Detection>) {
        try {
            val viewWidth = previewView.width
            val viewHeight = previewView.height
            
            if (viewWidth <= 0 || viewHeight <= 0) {
                Log.w(TAG, "⚠️ Vista no tiene dimensiones válidas")
                return
            }

            // Escalar detecciones al tamaño de la vista
            val scaledDetections = detections.map { detection ->
                detection.copy(
                    boundingBox = android.graphics.RectF(
                        detection.boundingBox.left * viewWidth / 640f,
                        detection.boundingBox.top * viewHeight / 480f,
                        detection.boundingBox.right * viewWidth / 640f,
                        detection.boundingBox.bottom * viewHeight / 480f
                    )
                )
            }

            // Actualizar overlay
            overlayView.updateDetections(
                scaledDetections,
                currentLanguageProvider.invoke()
            )

            // Callback
            onDetectionsUpdate(scaledDetections)

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error actualizando detecciones: ${e.message}", e)
        }
    }

    fun stopCamera() {
        try {
            cameraProvider?.unbindAll()
            cameraExecutor.shutdown()
            isCameraActive = false
            frameCount = 0
            overlayView.clearDetections()
            Log.i(TAG, "🛑 Cámara detenida")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error al detener cámara: ${e.message}", e)
        }
    }

    fun isRunning(): Boolean = isCameraActive
}