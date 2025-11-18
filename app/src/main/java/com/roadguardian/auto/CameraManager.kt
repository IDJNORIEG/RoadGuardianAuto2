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
import java.util.concurrent.TimeUnit

/**
 * CameraManager - Versión corregida para reinicialización segura.
 * Cambios clave:
 *  - Se fuerza clearAnalyzer() antes de unbindAll()
 *  - Se usa shutdownNow() + awaitTermination() para liberar executor
 *  - Delays estratégicos (80ms, 300ms) para asegurar liberación HW
 *  - Evita race conditions al reiniciar la cámara (orden de limpieza)
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

    private var frameCount = 0

    companion object {
        private const val TAG = "CameraManager"
        private const val FRAME_SKIP = 2
        private const val SEPARATOR = "============================================================"
    }

    /**
     * Public: inicializa la cámara. Garantiza limpieza previa y espera mínima
     * para que CameraX libere recursos.
     */
    fun initializeCamera() {
        Log.d(TAG, SEPARATOR)
        Log.d(TAG, "📷 initializeCamera() - iniciando limpieza segura antes de bind")
        Log.d(TAG, SEPARATOR)

        // Hacer una limpieza segura antes de crear un nuevo executor
        safeCleanup()

        // Delay final para dar oportunidad al HW de liberarse
        try {
            Thread.sleep(300)
        } catch (ie: InterruptedException) {
            Thread.currentThread().interrupt()
        }

        // Crear executor nuevo
        cameraExecutor = Executors.newSingleThreadExecutor()
        Log.d(TAG, "✅ Nuevo Executor creado")

        frameCount = 0
        processingFrame = false
        isCameraActive = false

        val providerFuture = ProcessCameraProvider.getInstance(context)
        providerFuture.addListener({
            try {
                cameraProvider = providerFuture.get()
                Log.d(TAG, "✅ CameraProvider obtenido")
                startCamera()
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error obteniendo CameraProvider: ${e.message}", e)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    /**
     * startCamera() - bind a lifecycle. Supone que cameraProvider fue obtenido.
     */
    @SuppressLint("UnsafeOptInUsageError")
    private fun startCamera() {
        try {
            val provider = cameraProvider ?: run {
                Log.e(TAG, "❌ startCamera(): cameraProvider es null")
                return
            }

            // Asegurar estado limpio por si algo quedó
            try {
                // Llamar a unbindAll en el provider (es seguro aunque ya se haya llamado antes)
                provider.unbindAll()
                Log.d(TAG, "🔄 provider.unbindAll() ejecutado")
            } catch (e: Exception) {
                Log.w(TAG, "⚠️ provider.unbindAll() falló: ${e.message}")
            }

            val preview = Preview.Builder()
                .setTargetRotation(previewView.display.rotation)
                .build().also { it.setSurfaceProvider(previewView.surfaceProvider) }

            imageAnalyzer = ImageAnalysis.Builder()
                .setTargetResolution(android.util.Size(640, 480))
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                .build()

            // Establecer analyzer usando el executor recién creado
            cameraExecutor?.let { exec ->
                imageAnalyzer?.setAnalyzer(exec) { image ->
                    processImageProxy(image)
                }
            } ?: run {
                Log.e(TAG, "❌ startCamera(): cameraExecutor es null")
            }

            val cameraSelector = CameraSelector.Builder()
                .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                .build()

            // Bind final
            camera = provider.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                preview,
                imageAnalyzer
            )

            isCameraActive = true
            Log.i(TAG, SEPARATOR)
            Log.i(TAG, "✅ Cámara iniciada correctamente")
            Log.i(TAG, SEPARATOR)

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error iniciando cámara: ${e.message}", e)
            isCameraActive = false
        }
    }

    /**
     * processImageProxy: idéntico comportamiento, pero tenemos claro cuando cerrar.
     */
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
                return
            }

            val detections = detectionManager.detectAnimals(bitmap)

            android.os.Handler(android.os.Looper.getMainLooper()).post {
                if (detections.isNotEmpty()) {
                    val scaled = scaleDetectionsToView(detections, bitmap.width, bitmap.height)
                    updateDetections(scaled)
                } else {
                    // limpiar overlay ocasionalmente para evitar ghost boxes
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
     * safeCleanup(): limpieza segura y ordenada.
     * Orden:
     *  1) Detener procesamiento (flags)
     *  2) Quitar analyzer (clearAnalyzer)
     *  3) Espera corta
     *  4) unbindAll()
     *  5) shutdownNow() + awaitTermination()
     *  6) liberar referencias
     */
    private fun safeCleanup() {
        Log.d(TAG, "🧹 safeCleanup() - iniciando limpieza segura...")
        try {
            // 1) impedir nuevo procesamiento
            isCameraActive = false
            processingFrame = false

            // 2) quitar analyzer para que CameraX deje de enviar frames
            try {
                imageAnalyzer?.clearAnalyzer()
                Log.d(TAG, "✅ imageAnalyzer.clearAnalyzer() ejecutado")
            } catch (e: Exception) {
                Log.w(TAG, "⚠️ clearAnalyzer() falló: ${e.message}")
            }

            // Pequeña espera para que el analyzer termine de procesar el frame actual
            try {
                Thread.sleep(80)
            } catch (ie: InterruptedException) {
                Thread.currentThread().interrupt()
            }

            // 3) Desvincular casos de uso del provider
            try {
                cameraProvider?.unbindAll()
                Log.d(TAG, "✅ cameraProvider.unbindAll() ejecutado")
            } catch (e: Exception) {
                Log.w(TAG, "⚠️ unbindAll() falló: ${e.message}")
            }

            // 4) shutdownNow del executor y esperar terminación
            try {
                cameraExecutor?.let { exec ->
                    exec.shutdownNow()
                    if (!exec.awaitTermination(300, TimeUnit.MILLISECONDS)) {
                        Log.w(TAG, "⚠️ Executor no terminó en 300ms")
                    } else {
                        Log.d(TAG, "✅ Executor terminado")
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "⚠️ Error al shutdownNow/awaitTermination: ${e.message}")
            } finally {
                cameraExecutor = null
            }

            // 5) limpiar referencias locales
            try {
                camera = null
                imageAnalyzer = null
            } catch (e: Exception) {
                Log.w(TAG, "⚠️ Error liberando referencias: ${e.message}")
            }

            Log.d(TAG, "✅ safeCleanup() completado")

        } catch (e: Exception) {
            Log.e(TAG, "❌ safeCleanup() error general: ${e.message}", e)
        }
    }

    /**
     * stopCamera() público: limpia overlay y llama a safeCleanup() con un delay extra
     */
    fun stopCamera() {
        Log.i(TAG, "🛑 stopCamera() - deteniendo cámara...")
        try {
            isCameraActive = false
            overlayView.clearDetections()

            safeCleanup()

            // Espera final para liberar HW
            try {
                Thread.sleep(300)
            } catch (ie: InterruptedException) {
                Thread.currentThread().interrupt()
            }

            frameCount = 0
            processingFrame = false

            Log.i(TAG, "✅ stopCamera() completado")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error en stopCamera(): ${e.message}", e)
        }
    }

    fun isRunning(): Boolean = isCameraActive
}
