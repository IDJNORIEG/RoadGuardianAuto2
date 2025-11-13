package com.roadguardian.auto

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.roadguardian.auto.models.AnimalType
import com.roadguardian.auto.models.Detection
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer
import java.util.concurrent.Executors
import kotlin.math.max
import kotlin.math.min

class DetectionManager(private val context: Context) {

    private var interpreter: Interpreter? = null
    private val inputSize = 640 // Entrada modelo YOLOv8n.tflite
    private var isModelLoaded = false
    private val executor = Executors.newSingleThreadExecutor()

    companion object {
        private const val TAG = "DetectionManager"
        private const val MODEL_FILE = "yolov8n.tflite"
        private const val CONFIDENCE_THRESHOLD = 0.45f
        private const val IOU_THRESHOLD = 0.45f
    }

    init {
        loadModel()
    }

    private fun loadModel() {
        executor.execute {
            try {
                val modelBuffer = FileUtil.loadMappedFile(context, MODEL_FILE)
                val options = Interpreter.Options().apply {
                    numThreads = 4
                    // GPU delegate opcional:
                    // addDelegate(GpuDelegate())
                }
                interpreter = Interpreter(modelBuffer, options)
                isModelLoaded = true
                Log.i(TAG, "✅ Modelo cargado correctamente (${MODEL_FILE})")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error cargando modelo: ${e.message}", e)
                isModelLoaded = false
            }
        }
    }

    fun detectAnimals(
        bitmap: Bitmap,
        minConfidence: Float = CONFIDENCE_THRESHOLD
    ): List<Detection> {
        if (!isModelLoaded || interpreter == null) {
            Log.w(TAG, "Modelo aún no cargado")
            return emptyList()
        }

        return try {
            val resized = Bitmap.createScaledBitmap(bitmap, inputSize, inputSize, true)
            val tensorImage = TensorImage.fromBitmap(resized)

            val outputShape = intArrayOf(1, 8400, 85) // formato YOLOv8 640x640
            val outputBuffer = TensorBuffer.createFixedSize(outputShape, org.tensorflow.lite.DataType.FLOAT32)

            interpreter?.run(tensorImage.buffer, outputBuffer.buffer.rewind())

            processDetections(
                outputBuffer.floatArray,
                minConfidence,
                bitmap.width.toFloat() / inputSize,
                bitmap.height.toFloat() / inputSize
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error en detección: ${e.message}", e)
            emptyList()
        }
    }

    private fun processDetections(
        output: FloatArray,
        minConfidence: Float,
        scaleX: Float,
        scaleY: Float
    ): List<Detection> {
        val detections = mutableListOf<Detection>()
        val animalIds = AnimalType.getAllIds()

        // 8400 predicciones, cada una con 85 valores [x, y, w, h, conf, clases...]
        val numPredictions = 8400
        val valuesPerPred = 85

        for (i in 0 until numPredictions) {
            val offset = i * valuesPerPred
            val objectness = output[offset + 4]

            if (objectness < minConfidence) continue

            // Clase con mayor confianza
            var bestClassId = -1
            var bestScore = 0f
            for (c in 0 until 80) {
                val classScore = output[offset + 5 + c]
                if (classScore > bestScore) {
                    bestScore = classScore
                    bestClassId = c
                }
            }

            val confidence = objectness * bestScore
            if (confidence < minConfidence || bestClassId !in animalIds) continue

            val cx = output[offset]
            val cy = output[offset + 1]
            val w = output[offset + 2]
            val h = output[offset + 3]

            val left = (cx - w / 2f) * scaleX
            val top = (cy - h / 2f) * scaleY
            val right = (cx + w / 2f) * scaleX
            val bottom = (cy + h / 2f) * scaleY

            val bboxHeight = (bottom - top)
            val distance = estimateDistance(bboxHeight)

            detections.add(
                Detection(
                    animal = AnimalType.fromId(bestClassId)!!,
                    confidence = confidence,
                    distance = distance
                )
            )
        }

        return applyNMS(detections)
    }

    private fun estimateDistance(objectHeight: Float): Int {
        // Aproximación calibrada a 640x640: más realista para animales medianos
        val normalized = max(0.1f, min(1f, objectHeight / 480f))
        val distance = 200 - (normalized * 190)
        return distance.toInt().coerceIn(10, 200)
    }

    private fun applyNMS(detections: List<Detection>): List<Detection> {
        // Non-Maximum Suppression básico (solo mantiene el más confiable por especie)
        val bestByAnimal = mutableMapOf<String, Detection>()

        for (d in detections) {
            val current = bestByAnimal[d.animal.name]
            if (current == null || d.confidence > current.confidence) {
                bestByAnimal[d.animal.name] = d
            }
        }

        return bestByAnimal.values.sortedByDescending { it.confidence }
    }

    fun release() {
        interpreter?.close()
        interpreter = null
        isModelLoaded = false
        executor.shutdownNow()
    }
}
