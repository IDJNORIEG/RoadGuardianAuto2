package com.roadguardian.auto

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.RectF
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
    private var processingFrame = false

    companion object {
        private const val TAG = "CameraManager"
        private const val FRAME_SKIP = 2
        private const val SEPARATOR = "============================================================"
    }

    fun initializeCamera() {
        Log.d(TAG, SEPARATOR)
        Log.d(TAG, "📷 INICIALIZANDO CÁMARA")
        Log.d(TAG, SEPARATOR)
        
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)

        cameraProviderFuture.addListener({
            try {
                cameraProvider = cameraProviderFuture.get()
                Log.d(TAG, "✅ CameraProvider obtenido")
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

            val preview = Preview.Builder()
                .setTargetRotation(previewView.display.rotation)
                .build()
                .also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

            Log.d(TAG, "✅ Preview configurado")

            imageAnalyzer = ImageAnalysis.Builder()
                .setTargetResolution(android.util.Size(640, 480))
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                .build()

            imageAnalyzer?.setAnalyzer(cameraExecutor) { image ->
                processImageProxy(image)
            }

            Log.d(TAG, "✅ ImageAnalyzer configurado")

            val cameraSelector = CameraSelector.Builder()
                .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                .build()

            cameraProvider.unbindAll()

            cameraProvider.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                preview,
                imageAnalyzer
            )

            isCameraActive = true
            
            Log.i(TAG, SEPARATOR)
            Log.i(TAG, "✅ CÁMARA INICIADA CORRECTAMENTE")
            Log.i(TAG, SEPARATOR)

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error iniciando cámara: ${e.message}", e)
            isCameraActive = false
        }
    }

    @SuppressLint("UnsafeOptInUsageError")
    private fun processImageProxy(imageProxy: ImageProxy) {
        frameCount++
        
        if (processingFrame) {
            imageProxy.close()
            return
        }
        
        if (frameCount % FRAME_SKIP != 0) {
            imageProxy.close()
            return
        }

        processingFrame = true

        try {
            if (frameCount % 30 == 0) {
                Log.d(TAG, "🎬 Frame $frameCount: ${imageProxy.width}x${imageProxy.height}")
            }

            val bitmap = imageProxyToBitmap(imageProxy)
            
            if (bitmap == null) {
                Log.w(TAG, "⚠️ Bitmap null")
                return
            }

            // Detectar con el bitmap original (antes de escalar)
            val detections = detectionManager.detectAnimals(bitmap)

            if (detections.isNotEmpty()) {
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    // Escalar las coordenadas de detección al tamaño de la vista
                    val scaledDetections = scaleDetectionsToView(
                        detections,
                        bitmap.width,
                        bitmap.height
                    )
                    updateDetections(scaledDetections)
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error procesando frame: ${e.message}", e)
        } finally {
            imageProxy.close()
            processingFrame = false
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
            Log.e(TAG, "❌ Error convirtiendo: ${e.message}", e)
            null
        }
    }

    private fun scaleDetectionsToView(
        detections: List<Detection>,
        sourceWidth: Int,
        sourceHeight: Int
    ): List<Detection> {
        val viewWidth = previewView.width.toFloat()
        val viewHeight = previewView.height.toFloat()
        
        if (viewWidth <= 0 || viewHeight <= 0) {
            Log.w(TAG, "⚠️ Vista sin dimensiones válidas")
            return detections
        }

        // Calcular escalas considerando el aspect ratio
        val scaleX = viewWidth / sourceWidth
        val scaleY = viewHeight / sourceHeight

        Log.d(TAG, "📐 Escalado: source=${sourceWidth}x${sourceHeight}, view=${viewWidth.toInt()}x${viewHeight.toInt()}")
        Log.d(TAG, "   Escalas: X=$scaleX, Y=$scaleY")

        return detections.map { detection ->
            val box = detection.boundingBox
            
            val scaledBox = RectF(
                box.left * scaleX,
                box.top * scaleY,
                box.right * scaleX,
                box.bottom * scaleY
            )
            
            Log.d(TAG, "   Original: [${box.left}, ${box.top}, ${box.right}, ${box.bottom}]")
            Log.d(TAG, "   Escalado: [${scaledBox.left}, ${scaledBox.top}, ${scaledBox.right}, ${scaledBox.bottom}]")
            
            detection.copy(boundingBox = scaledBox)
        }
    }

    private fun updateDetections(detections: List<Detection>) {
        try {
            Log.d(TAG, "🎨 Actualizando overlay: ${detections.size} detecciones")

            overlayView.updateDetections(detections, currentLanguageProvider.invoke())
            onDetectionsUpdate(detections)

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error actualizando UI: ${e.message}", e)
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
            Log.e(TAG, "❌ Error deteniendo: ${e.message}", e)
        }
    }

    fun isRunning(): Boolean = isCameraActive
}