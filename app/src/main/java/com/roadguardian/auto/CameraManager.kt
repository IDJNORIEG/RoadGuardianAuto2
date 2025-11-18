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
    private var cameraExecutor: ExecutorService? = null
    private var imageAnalyzer: ImageAnalysis? = null
    private var camera: Camera? = null
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
        
        // Limpiar estado previo completamente
        cleanupCamera()
        
        // Crear nuevo executor
        cameraExecutor = Executors.newSingleThreadExecutor()
        Log.d(TAG, "✅ Nuevo executor creado")
        
        // Resetear variables de control
        frameCount = 0
        processingFrame = false
        isCameraActive = false
        
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

            // Desconectar todo antes de reconectar
            cameraProvider.unbindAll()
            Log.d(TAG, "🔄 Casos de uso anteriores desconectados")

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

            imageAnalyzer?.setAnalyzer(cameraExecutor!!) { image ->
                processImageProxy(image)
            }

            Log.d(TAG, "✅ ImageAnalyzer configurado")

            val cameraSelector = CameraSelector.Builder()
                .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                .build()

            // Guardar referencia a la cámara
            camera = cameraProvider.bindToLifecycle(
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
        if (!isCameraActive) {
            imageProxy.close()
            return
        }
        
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

            val detections = detectionManager.detectAnimals(bitmap)

            if (detections.isNotEmpty()) {
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    val scaledDetections = scaleDetectionsToView(
                        detections,
                        bitmap.width,
                        bitmap.height
                    )
                    updateDetections(scaledDetections)
                }
            } else {
                // Limpiar overlay si no hay detecciones
                if (frameCount % 10 == 0) {
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        overlayView.clearDetections()
                    }
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

        val scaleX = viewWidth / sourceWidth
        val scaleY = viewHeight / sourceHeight

        return detections.map { detection ->
            val box = detection.boundingBox
            
            val scaledBox = RectF(
                box.left * scaleX,
                box.top * scaleY,
                box.right * scaleX,
                box.bottom * scaleY
            )
            
            detection.copy(boundingBox = scaledBox)
        }
    }

    private fun updateDetections(detections: List<Detection>) {
        try {
            overlayView.updateDetections(detections, currentLanguageProvider.invoke())
            onDetectionsUpdate(detections)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error actualizando UI: ${e.message}", e)
        }
    }

    private fun cleanupCamera() {
        try {
            Log.d(TAG, "🧹 Limpiando recursos previos...")
            
            // Detener procesamiento
            isCameraActive = false
            processingFrame = false
            
            // Desconectar casos de uso
            cameraProvider?.unbindAll()
            
            // Liberar executor
            cameraExecutor?.shutdown()
            cameraExecutor = null
            
            // Limpiar referencias
            camera = null
            imageAnalyzer = null
            
            Log.d(TAG, "✅ Recursos limpiados")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error limpiando: ${e.message}", e)
        }
    }

    fun stopCamera() {
        try {
            Log.i(TAG, "🛑 Deteniendo cámara...")
            
            // Marcar como inactiva primero
            isCameraActive = false
            
            // Limpiar overlay inmediatamente
            overlayView.clearDetections()
            
            // Desconectar casos de uso
            cameraProvider?.unbindAll()
            
            // Cerrar executor
            cameraExecutor?.shutdown()
            cameraExecutor = null
            
            // Resetear variables
            frameCount = 0
            processingFrame = false
            camera = null
            imageAnalyzer = null
            
            Log.i(TAG, "✅ Cámara detenida completamente")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error deteniendo: ${e.message}", e)
        }
    }

    fun isRunning(): Boolean = isCameraActive
}