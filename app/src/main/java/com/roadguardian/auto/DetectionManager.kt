package com.roadguardian.auto

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import android.util.Log
import com.roadguardian.auto.models.AnimalType
import com.roadguardian.auto.models.Detection
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.max
import kotlin.math.min

class DetectionManager(private val context: Context) {

    private var interpreter: Interpreter? = null
    private val inputSize = 640
    private var isModelLoaded = false

    companion object {
        private const val TAG = "DetectionManager"
        private const val CONFIDENCE_THRESHOLD = 0.25f
        private const val IOU_THRESHOLD = 0.45f
        private const val MODEL_FILE = "yolov8n.tflite"
    }

    init {
        loadModel()
    }

    private fun loadModel() {
        try {
            Log.d(TAG, "📦 Intentando cargar modelo: $MODEL_FILE")
            val modelBuffer = FileUtil.loadMappedFile(context, MODEL_FILE)
            
            val options = Interpreter.Options().apply {
                setNumThreads(4)
                setUseNNAPI(false) // Desactivar NNAPI para mayor compatibilidad
            }
            
            interpreter = Interpreter(modelBuffer, options)
            
            // Verificar dimensiones del modelo
            val inputShape = interpreter?.getInputTensor(0)?.shape()
            val outputShape = interpreter?.getOutputTensor(0)?.shape()
            
            Log.i(TAG, "✅ Modelo cargado correctamente")
            Log.i(TAG, "📊 Input shape: ${inputShape?.contentToString()}")
            Log.i(TAG, "📊 Output shape: ${outputShape?.contentToString()}")
            
            isModelLoaded = true
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error cargando modelo: ${e.message}", e)
            isModelLoaded = false
        }
    }

    fun detectAnimals(
        bitmap: Bitmap,
        minConfidence: Float = CONFIDENCE_THRESHOLD
    ): List<Detection> {
        if (!isModelLoaded || interpreter == null) {
            Log.w(TAG, "⚠️ Modelo no cargado, retornando lista vacía")
            return emptyList()
        }

        return try {
            val startTime = System.currentTimeMillis()
            
            // Preprocesar imagen
            val resizedBitmap = Bitmap.createScaledBitmap(bitmap, inputSize, inputSize, true)
            val inputBuffer = bitmapToByteBuffer(resizedBitmap)
            
            // Preparar output buffer
            val outputArray = Array(1) { Array(8400) { FloatArray(85) } }
            
            // Ejecutar inferencia
            interpreter?.run(inputBuffer, outputArray)
            
            val inferenceTime = System.currentTimeMillis() - startTime
            Log.d(TAG, "⏱️ Inferencia completada en ${inferenceTime}ms")
            
            // Procesar resultados
            val detections = processOutput(
                outputArray[0],
                minConfidence,
                bitmap.width.toFloat() / inputSize,
                bitmap.height.toFloat() / inputSize
            )
            
            if (detections.isNotEmpty()) {
                Log.i(TAG, "🐾 Detectados ${detections.size} animales")
                detections.forEach { det ->
                    Log.d(TAG, "  - ${det.animal.getLocalizedName()}: ${det.getConfidencePercentage()}% a ${det.distance}m")
                }
            }
            
            detections
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error en detección: ${e.message}", e)
            emptyList()
        }
    }

    private fun bitmapToByteBuffer(bitmap: Bitmap): ByteBuffer {
        val byteBuffer = ByteBuffer.allocateDirect(4 * inputSize * inputSize * 3)
        byteBuffer.order(ByteOrder.nativeOrder())
        
        val intValues = IntArray(inputSize * inputSize)
        bitmap.getPixels(intValues, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        
        var pixel = 0
        for (i in 0 until inputSize) {
            for (j in 0 until inputSize) {
                val value = intValues[pixel++]
                
                // Normalizar a [0, 1] dividiendo por 255
                byteBuffer.putFloat(((value shr 16) and 0xFF) / 255.0f)
                byteBuffer.putFloat(((value shr 8) and 0xFF) / 255.0f)
                byteBuffer.putFloat((value and 0xFF) / 255.0f)
            }
        }
        
        byteBuffer.rewind()
        return byteBuffer
    }

    private fun processOutput(
        output: Array<FloatArray>,
        minConfidence: Float,
        scaleX: Float,
        scaleY: Float
    ): List<Detection> {
        val detections = mutableListOf<Detection>()
        val animalIds = AnimalType.getAllIds()

        // YOLOv8 formato: 8400 predicciones x 85 valores [x, y, w, h, conf, clases...]
        for (i in output.indices) {
            val prediction = output[i]
            
            if (prediction.size < 85) continue
            
            // Confianza de objeto (índice 4)
            val objectness = prediction[4]
            
            if (objectness < minConfidence) continue

            // Encontrar la clase con mayor probabilidad
            var bestClassId = -1
            var bestScore = 0f
            
            for (c in 0 until 80) {
                val classScore = prediction[5 + c]
                if (classScore > bestScore) {
                    bestScore = classScore
                    bestClassId = c
                }
            }

            val confidence = objectness * bestScore
            
            // Verificar si es un animal que detectamos
            if (confidence < minConfidence || bestClassId !in animalIds) continue

            // Coordenadas del bounding box
            val cx = prediction[0]
            val cy = prediction[1]
            val w = prediction[2]
            val h = prediction[3]

            val left = (cx - w / 2f) * scaleX
            val top = (cy - h / 2f) * scaleY
            val right = (cx + w / 2f) * scaleX
            val bottom = (cy + h / 2f) * scaleY

            val boundingBox = RectF(left, top, right, bottom)
            val bboxHeight = (bottom - top)
            val distance = estimateDistance(bboxHeight)

            detections.add(
                Detection(
                    animal = AnimalType.fromId(bestClassId)!!,
                    confidence = confidence,
                    distance = distance,
                    boundingBox = boundingBox
                )
            )
        }

        return applyNMS(detections)
    }

    private fun estimateDistance(objectHeight: Float): Int {
        // Estimación basada en altura del objeto en píxeles
        // Valores calibrados para detección a distancia
        val normalized = max(0.1f, min(1f, objectHeight / 400f))
        val distance = 150 - (normalized * 140)
        return distance.toInt().coerceIn(10, 150)
    }

    private fun applyNMS(detections: List<Detection>): List<Detection> {
        if (detections.isEmpty()) return emptyList()

        val sortedDetections = detections.sortedByDescending { it.confidence }
        val selectedDetections = mutableListOf<Detection>()

        for (detection in sortedDetections) {
            var shouldAdd = true
            
            for (selected in selectedDetections) {
                if (detection.animal == selected.animal) {
                    val iou = calculateIoU(detection.boundingBox, selected.boundingBox)
                    if (iou > IOU_THRESHOLD) {
                        shouldAdd = false
                        break
                    }
                }
            }
            
            if (shouldAdd) {
                selectedDetections.add(detection)
            }
        }

        return selectedDetections
    }

    private fun calculateIoU(box1: RectF, box2: RectF): Float {
        val intersectionLeft = max(box1.left, box2.left)
        val intersectionTop = max(box1.top, box2.top)
        val intersectionRight = min(box1.right, box2.right)
        val intersectionBottom = min(box1.bottom, box2.bottom)

        if (intersectionRight < intersectionLeft || intersectionBottom < intersectionTop) {
            return 0f
        }

        val intersectionArea = (intersectionRight - intersectionLeft) * (intersectionBottom - intersectionTop)
        val box1Area = box1.width() * box1.height()
        val box2Area = box2.width() * box2.height()
        val unionArea = box1Area + box2Area - intersectionArea

        return intersectionArea / unionArea
    }

    fun release() {
        try {
            interpreter?.close()
            interpreter = null
            isModelLoaded = false
            Log.i(TAG, "🔒 DetectionManager liberado")
        } catch (e: Exception) {
            Log.e(TAG, "Error al liberar recursos: ${e.message}")
        }
    }
}