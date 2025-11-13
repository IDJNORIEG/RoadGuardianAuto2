package com.roadguardian.auto

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import android.view.SurfaceHolder
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.roadguardian.auto.models.Detection
import com.roadguardian.auto.ui.DetectionOverlayView
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Administra la cámara, el flujo de video y la conexión con el modelo YOLOv8.
 */
class CameraManager(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val previewView: PreviewView,
    private val overlayView: DetectionOverlayView,
    private val detectionManager: DetectionManager,
    private val currentLanguageProvider: () -> String, // lambda para idioma actual dinámico
    private val onDetectionsUpdate: (List<Detection>) -> Unit // callback detecciones nuevas
) {

    private var cameraProvider: ProcessCameraProvider? = null
    private var cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private var imageAnalyzer: ImageAnalysis? = null
    private var isCameraActive = false

    companion object {
        private const val TAG = "CameraManager"
    }

    /**
     * Inicializa la cámara con el proveedor de CameraX
     */
    fun initializeCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)

        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()
            startCamera()
        }, ContextCompat.getMainExecutor(context))
    }

    /**
     * Inicia la cámara y establece el analizador de imágenes
     */
    @SuppressLint("UnsafeOptInUsageError")
    private fun startCamera() {
        try {
            val cameraProvider = cameraProvider ?: return

            val preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

            imageAnalyzer = ImageAnalysis.Builder()
                .setTargetResolution(android.util.Size(640, 640))
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()

            imageAnalyzer?.setAnalyzer(cameraExecutor) { imageProxy ->
                val bitmap = imageProxy.toBitmap() ?: run {
                    imageProxy.close()
                    return@setAnalyzer
                }

                try {
                    // Detectar animales con el modelo YOLOv8
                    val results = detectionManager.detectAnimals(bitmap)

                    // 🔹 Escalar detecciones al tamaño de la vista
                    val scaledDetections = overlayView.scaleDetections(
                        results,
                        previewView.width,
                        previewView.height
                    )

                    // 🔹 Actualizar la superposición con el idioma actual
                    overlayView.updateDetections(
                        scaledDetections,
                        currentLanguageProvider.invoke()
                    )

                    // 🔹 Callback opcional (contador o log)
                    onDetectionsUpdate(scaledDetections)

                } catch (e: Exception) {
                    Log.e(TAG, "Error procesando detección: ${e.message}")
                } finally {
                    imageProxy.close()
                }
            }

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
            Log.i(TAG, "📷 Cámara iniciada correctamente")

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error iniciando cámara: ${e.message}")
        }
    }

    /**
     * Convierte el frame actual a Bitmap para pasar al modelo
     */
    private fun ImageProxy.toBitmap(): Bitmap? {
        return try {
            val yBuffer = planes[0].buffer
            val uBuffer = planes[1].buffer
            val vBuffer = planes[2].buffer

            val ySize = yBuffer.remaining()
            val uSize = uBuffer.remaining()
            val vSize = vBuffer.remaining()

            val nv21 = ByteArray(ySize + uSize + vSize)
            yBuffer.get(nv21, 0, ySize)
            vBuffer.get(nv21, ySize, vSize)
            uBuffer.get(nv21, ySize + vSize, uSize)

            val yuvImage = android.graphics.YuvImage(
                nv21,
                android.graphics.ImageFormat.NV21,
                width, height, null
            )

            val out = java.io.ByteArrayOutputStream()
            yuvImage.compressToJpeg(android.graphics.Rect(0, 0, width, height), 80, out)
            val imageBytes = out.toByteArray()
            android.graphics.BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
        } catch (e: Exception) {
            Log.e(TAG, "Error convirtiendo a Bitmap: ${e.message}")
            null
        }
    }

    /**
     * Detiene la cámara y libera los recursos
     */
    fun stopCamera() {
        try {
            cameraProvider?.unbindAll()
            cameraExecutor.shutdownNow()
            isCameraActive = false
            Log.i(TAG, "🛑 Cámara detenida")
        } catch (e: Exception) {
            Log.e(TAG, "Error al detener cámara: ${e.message}")
        }
    }

    /**
     * Verifica si la cámara está activa
     */
    fun isRunning(): Boolean = isCameraActive
}
