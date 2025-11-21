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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * ✅ VERSIÓN FINAL: Basada en la versión original que funcionaba
 * 
 * Cambios respecto a la original:
 * 1. Agregado stopCameraSuspend() para coordinación con MainActivity
 * 2. Agregado scaleDetectionsToView() para detecciones correctas
 * 3. Mejorados los logs para debugging
 * 4. Mantenido el ExecutorService constante (NO se destruye)
 */
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
    private var camera: Camera? = null
    
    private var isCameraActive = false
    private var processingFrame = false
    private var frameCount = 0

    companion object {
        private const val TAG = "CameraManager"
        private const val FRAME_SKIP = 2
        private const val SEPARATOR = "============================================================"
    }

    /**
     * ✅ Inicialización simple y efectiva (como la versión original)
     */
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
            val provider = cameraProvider ?: run {
                Log.e(TAG, "❌ CameraProvider es null")
                return
            }

            Log.d(TAG, "📐 PreviewView: ${previewView.width}x${previewView.height}")

            // Crear Preview
            val preview = Preview.Builder()
                .setTargetRotation(previewView.display.rotation)
                .build()
                .also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

            Log.d(TAG, "✅ Preview configurado")

            // Crear ImageAnalyzer
            imageAnalyzer = ImageAnalysis.Builder()
                .setTargetResolution(android.util.Size(640, 480))
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                .build()

            imageAnalyzer?.setAnalyzer(cameraExecutor) { image ->
                processImageProxy(image)
            }

            Log.d(TAG, "✅ ImageAnalyzer configurado")
            Log.d(TAG, "   Executor activo: ${!cameraExecutor.isShutdown}")
            Log.d(TAG, "   Executor terminado: ${cameraExecutor.isTerminated}")

            // Selector de cámara trasera
            val cameraSelector = CameraSelector.Builder()
                .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                .build()

            // Desconectar casos de uso anteriores
            provider.unbindAll()
            Log.d(TAG, "🔄 unbindAll() ejecutado")

            // Conectar a lifecycle
            camera = provider.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                preview,
                imageAnalyzer
            )

            isCameraActive = true
            frameCount = 0
            
            Log.i(TAG, SEPARATOR)
            Log.i(TAG, "✅ CÁMARA INICIADA CORRECTAMENTE")
            Log.i(TAG, "   Resolución: 640x480")
            Log.i(TAG, "   Camera bound: ${camera != null}")
            Log.i(TAG, "   ImageAnalyzer bound: ${imageAnalyzer != null}")
            Log.i(TAG, "   isCameraActive: $isCameraActive")
            Log.i(TAG, SEPARATOR)

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error iniciando cámara: ${e.message}", e)
            isCameraActive = false
        }
    }

    @SuppressLint("UnsafeOptInUsageError")
    private fun processImageProxy(imageProxy: ImageProxy) {
        // ✅ DEBUG: Log cada 10 frames para ver si llegan
        if (frameCount % 10 == 0) {
            Log.d(TAG, "📥 Frame recibido #$frameCount (isCameraActive=$isCameraActive)")
        }

        if (!isCameraActive) {
            imageProxy.close()
            return
        }

        frameCount++
        
        if (processingFrame || frameCount % FRAME_SKIP != 0) {
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
                Log.w(TAG, "⚠️ Bitmap null en frame $frameCount")
                return
            }

            val detections = detectionManager.detectAnimals(bitmap)

            android.os.Handler(android.os.Looper.getMainLooper()).post {
                if (detections.isNotEmpty()) {
                    val scaled = scaleDetectionsToView(detections, bitmap.width, bitmap.height)
                    updateDetections(scaled)
                } else {
                    if (frameCount % 10 == 0) {
                        overlayView.clearDetections()
                    }
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error procesando frame $frameCount: ${e.message}", e)
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
            Log.e(TAG, "❌ Error convirtiendo YUV a Bitmap: ${e.message}", e)
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
        
        if (viewWidth <= 0f || viewHeight <= 0f) return detections

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

    /**
     * ✅ Detención simple (sin destruir executor)
     */
    fun stopCamera() {
        try {
            isCameraActive = false
            cameraProvider?.unbindAll()
            overlayView.clearDetections()
            frameCount = 0
            Log.i(TAG, "🛑 Cámara detenida")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error deteniendo: ${e.message}", e)
        }
    }

    /**
     * ✅ Versión suspendible para coordinación con MainActivity
     */
    suspend fun stopCameraSuspend() = withContext(Dispatchers.Main) {
        Log.i(TAG, "🛑 stopCameraSuspend() - INICIO")
        
        stopCamera()
        
        // Pequeña espera para asegurar que unbindAll() complete
        delay(300)
        
        Log.i(TAG, "✅ stopCameraSuspend() - COMPLETADO")
    }

    /**
     * ✅ Liberar recursos al destruir (llamar en onDestroy)
     */
    fun release() {
        try {
            stopCamera()
            cameraExecutor.shutdown()
            Log.i(TAG, "🗑️ Recursos liberados")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error liberando: ${e.message}", e)
        }
    }

    fun isRunning(): Boolean = isCameraActive
}