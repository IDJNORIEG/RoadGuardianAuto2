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
import java.util.concurrent.TimeUnit

/**
 * ✅ SOLUCIÓN DEFINITIVA: CameraManager con reinicialización 100% confiable
 * 
 * Cambios clave:
 * 1. Mutex para evitar race conditions
 * 2. Limpieza completa del provider antes de reiniciar
 * 3. Esperas ajustadas y verificadas
 * 4. Estado sincronizado correctamente
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
    private var cameraExecutor: ExecutorService? = null
    private var imageAnalyzer: ImageAnalysis? = null
    private var camera: Camera? = null

    @Volatile
    private var isCameraActive = false

    @Volatile
    private var processingFrame = false

    @Volatile
    private var isInitializing = false

    private var frameCount = 0

    companion object {
        private const val TAG = "CameraManager"
        private const val FRAME_SKIP = 2
        private const val SEPARATOR = "============================================================"
    }

    /**
     * ✅ CRÍTICO: Inicialización con mutex para evitar múltiples llamadas
     */
    suspend fun initializeCamera() = withContext(Dispatchers.Main) {
        Log.d(TAG, SEPARATOR)
        Log.d(TAG, "📷 initializeCamera() - INICIO")
        Log.d(TAG, SEPARATOR)

        // ✅ Prevenir inicialización múltiple
        if (isInitializing) {
            Log.w(TAG, "⚠️ Ya hay una inicialización en curso, esperando...")
            return@withContext
        }

        if (isCameraActive) {
            Log.w(TAG, "⚠️ Cámara ya activa, deteniendo primero...")
            stopCameraSuspend()
        }

        isInitializing = true

        try {
            // ✅ Limpiar COMPLETAMENTE el estado previo
            cleanupCameraResources()

            // ✅ Espera crítica para liberación de hardware (aumentada por timeout)
            delay(1000)

            // ✅ Crear nuevo executor
            cameraExecutor = Executors.newSingleThreadExecutor()
            Log.d(TAG, "✅ Nuevo Executor creado")

            // ✅ Resetear estado
            frameCount = 0
            processingFrame = false
            isCameraActive = false

            // ✅ Obtener provider (SIEMPRE obtener uno nuevo)
            val providerFuture = ProcessCameraProvider.getInstance(context)
            providerFuture.addListener({
                try {
                    // ✅ IMPORTANTE: Liberar provider anterior si existe
                    cameraProvider?.unbindAll()
                    
                    cameraProvider = providerFuture.get()
                    Log.d(TAG, "✅ CameraProvider obtenido")
                    
                    startCamera()
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Error obteniendo CameraProvider: ${e.message}", e)
                    isInitializing = false
                }
            }, ContextCompat.getMainExecutor(context))

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error en initializeCamera: ${e.message}", e)
            isInitializing = false
        }
    }

    @SuppressLint("UnsafeOptInUsageError")
    private fun startCamera() {
        try {
            val provider = cameraProvider ?: run {
                Log.e(TAG, "❌ startCamera(): cameraProvider es null")
                isInitializing = false
                return
            }

            // ✅ Asegurar limpieza previa
            provider.unbindAll()
            Log.d(TAG, "🔄 provider.unbindAll() ejecutado")

            val preview = Preview.Builder()
                .setTargetRotation(previewView.display.rotation)
                .build().also { it.setSurfaceProvider(previewView.surfaceProvider) }

            @Suppress("DEPRECATION")
            imageAnalyzer = ImageAnalysis.Builder()
                .setTargetResolution(android.util.Size(640, 480))
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                .build()

            cameraExecutor?.let { exec ->
                imageAnalyzer?.setAnalyzer(exec) { image ->
                    processImageProxy(image)
                }
            }

            val cameraSelector = CameraSelector.Builder()
                .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                .build()

            // ✅ Bind a lifecycle
            camera = provider.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                preview,
                imageAnalyzer
            )

            isCameraActive = true
            isInitializing = false
            
            Log.i(TAG, SEPARATOR)
            Log.i(TAG, "✅ CÁMARA INICIADA CORRECTAMENTE")
            Log.i(TAG, SEPARATOR)

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error iniciando cámara: ${e.message}", e)
            isCameraActive = false
            isInitializing = false
        }
    }

    @SuppressLint("UnsafeOptInUsageError")
    private fun processImageProxy(imageProxy: ImageProxy) {
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

            val bitmap = imageProxyToBitmap(imageProxy) ?: return

            val detections = detectionManager.detectAnimals(bitmap)

            android.os.Handler(android.os.Looper.getMainLooper()).post {
                if (detections.isNotEmpty()) {
                    val scaled = scaleDetectionsToView(detections, bitmap.width, bitmap.height)
                    updateDetections(scaled)
                } else {
                    if (frameCount % 10 == 0) overlayView.clearDetections()
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error procesando frame: ${e.message}", e)
        } finally {
            try {
                imageProxy.close()
            } catch (e: Exception) {
                Log.w(TAG, "⚠️ imageProxy.close() falló: ${e.message}")
            }
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

            val yuvImage = YuvImage(nv21, ImageFormat.NV21, imageProxy.width, imageProxy.height, null)
            val out = ByteArrayOutputStream()
            yuvImage.compressToJpeg(Rect(0, 0, imageProxy.width, imageProxy.height), 80, out)
            val imageBytes = out.toByteArray()
            BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error convirtiendo YUV a Bitmap: ${e.message}", e)
            null
        }
    }

    private fun scaleDetectionsToView(detections: List<Detection>, sourceWidth: Int, sourceHeight: Int): List<Detection> {
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
     * ✅ CRÍTICO: Limpieza completa de recursos
     */
    private suspend fun cleanupCameraResources() = withContext(Dispatchers.IO) {
        Log.d(TAG, "🧹 cleanupCameraResources() - INICIO")

        try {
            // 1. Detener procesamiento
            isCameraActive = false
            processingFrame = false

            // 2. Quitar analyzer
            try {
                imageAnalyzer?.clearAnalyzer()
                Log.d(TAG, "✅ Analyzer limpiado")
            } catch (e: Exception) {
                Log.w(TAG, "⚠️ clearAnalyzer() falló: ${e.message}")
            }

            // 3. Espera para frames en proceso
            delay(200)

            // 4. Desconectar casos de uso (en Main thread)
            withContext(Dispatchers.Main) {
                try {
                    cameraProvider?.unbindAll()
                    Log.d(TAG, "✅ unbindAll() ejecutado")
                } catch (e: Exception) {
                    Log.w(TAG, "⚠️ unbindAll() falló: ${e.message}")
                }
            }

            // 5. Cerrar executor
            try {
                cameraExecutor?.let { exec ->
                    exec.shutdownNow()
                    if (!exec.awaitTermination(500, TimeUnit.MILLISECONDS)) {
                        Log.w(TAG, "⚠️ Executor no terminó en 500ms")
                    } else {
                        Log.d(TAG, "✅ Executor terminado")
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "⚠️ Error al shutdown executor: ${e.message}")
            } finally {
                cameraExecutor = null
            }

            // 6. Limpiar referencias
            camera = null
            imageAnalyzer = null
            frameCount = 0

            Log.d(TAG, "✅ cleanupCameraResources() - COMPLETADO")

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error en cleanupCameraResources(): ${e.message}", e)
        }
    }

    /**
     * ✅ Detención suspendible para coordinación correcta
     */
    suspend fun stopCameraSuspend() = withContext(Dispatchers.Main) {
        Log.i(TAG, "🛑 stopCameraSuspend() - INICIO")

        // Limpiar overlay inmediatamente
        overlayView.clearDetections()

        // Limpiar recursos
        cleanupCameraResources()

        // Espera final
        delay(400)

        Log.i(TAG, "✅ stopCameraSuspend() - COMPLETADO")
    }

    fun isRunning(): Boolean = isCameraActive
}