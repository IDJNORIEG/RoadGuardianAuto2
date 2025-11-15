package com.roadguardian.auto

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import android.util.Log
import android.widget.Toast
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
    private var frameProcessed = 0
    private var outputFormat = OutputFormat.UNKNOWN

    enum class OutputFormat {
        UNKNOWN,
        FORMAT_8400x85,  // [1, 8400, 85]
        FORMAT_84x8400   // [1, 84, 8400]
    }

    companion object {
        private const val TAG = "DetectionManager"
        private const val CONFIDENCE_THRESHOLD = 0.30f
        private const val IOU_THRESHOLD = 0.45f
        private const val MODEL_FILE = "yolov8n.tflite"
        private const val SEPARATOR = "============================================================"
    }

    init {
        loadModel()
    }

    private fun loadModel() {
        try {
            Log.d(TAG, SEPARATOR)
            Log.d(TAG, "🔍 DIAGNÓSTICO DE CARGA DEL MODELO")
            Log.d(TAG, SEPARATOR)
            
            val assetsList = context.assets.list("")
            Log.d(TAG, "📁 Archivos en assets/: ${assetsList?.joinToString(", ")}")
            
            if (assetsList?.contains(MODEL_FILE) != true) {
                Log.e(TAG, "❌ $MODEL_FILE NO está en assets")
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    Toast.makeText(context, "ERROR: Modelo no encontrado", Toast.LENGTH_LONG).show()
                }
                return
            }
            
            Log.d(TAG, "📦 Cargando: $MODEL_FILE")
            val modelBuffer = FileUtil.loadMappedFile(context, MODEL_FILE)
            
            Log.d(TAG, "✅ Archivo cargado: ${modelBuffer.capacity()} bytes (${modelBuffer.capacity() / 1024 / 1024}MB)")
            
            val options = Interpreter.Options().apply {
                setNumThreads(4)
                setUseNNAPI(false)
            }
            
            interpreter = Interpreter(modelBuffer, options)
            
            val inputShape = interpreter?.getInputTensor(0)?.shape()
            val outputShape = interpreter?.getOutputTensor(0)?.shape()
            
            Log.d(TAG, "✅ Intérprete creado")
            Log.d(TAG, "📊 Input shape: ${inputShape?.contentToString()}")
            Log.d(TAG, "📊 Output shape: ${outputShape?.contentToString()}")
            
            // Detectar formato de salida
            outputFormat = when {
                outputShape?.size == 3 && outputShape[1] == 8400 && outputShape[2] == 85 -> {
                    Log.i(TAG, "✅ Formato detectado: [1, 8400, 85]")
                    OutputFormat.FORMAT_8400x85
                }
                outputShape?.size == 3 && outputShape[1] == 84 && outputShape[2] == 8400 -> {
                    Log.i(TAG, "✅ Formato detectado: [1, 84, 8400]")
                    OutputFormat.FORMAT_84x8400
                }
                else -> {
                    Log.w(TAG, "⚠️ Formato desconocido: ${outputShape?.contentToString()}")
                    OutputFormat.UNKNOWN
                }
            }
            
            isModelLoaded = true
            
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                Toast.makeText(context, "✅ Modelo cargado", Toast.LENGTH_SHORT).show()
            }
            
            Log.d(TAG, SEPARATOR)
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ ERROR cargando modelo: ${e.message}", e)
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                Toast.makeText(context, "ERROR: ${e.message}", Toast.LENGTH_LONG).show()
            }
            isModelLoaded = false
        }
    }

    fun detectAnimals(bitmap: Bitmap, minConfidence: Float = CONFIDENCE_THRESHOLD): List<Detection> {
        frameProcessed++
        
        if (!isModelLoaded || interpreter == null) {
            if (frameProcessed == 1) {
                Log.e(TAG, "❌ Modelo no cargado")
            }
            return emptyList()
        }

        return try {
            val startTime = System.currentTimeMillis()
            
            val resizedBitmap = Bitmap.createScaledBitmap(bitmap, inputSize, inputSize, true)
            val inputBuffer = bitmapToByteBuffer(resizedBitmap)
            
            // Ejecutar inferencia según formato
            val detections = when (outputFormat) {
                OutputFormat.FORMAT_8400x85 -> {
                    val outputArray = Array(1) { Array(8400) { FloatArray(85) } }
                    interpreter?.run(inputBuffer, outputArray)
                    processOutput8400x85(outputArray[0], minConfidence, 
                        bitmap.width.toFloat() / inputSize, bitmap.height.toFloat() / inputSize)
                }
                OutputFormat.FORMAT_84x8400 -> {
                    val outputArray = Array(1) { Array(84) { FloatArray(8400) } }
                    interpreter?.run(inputBuffer, outputArray)
                    processOutput84x8400(outputArray[0], minConfidence,
                        bitmap.width.toFloat() / inputSize, bitmap.height.toFloat() / inputSize)
                }
                else -> {
                    Log.e(TAG, "❌ Formato desconocido")
                    emptyList()
                }
            }
            
            val inferenceTime = System.currentTimeMillis() - startTime
            
            if (frameProcessed % 30 == 0) {
                Log.d(TAG, "⏱️ Frame $frameProcessed: ${inferenceTime}ms, ${detections.size} detecciones")
            }
            
            if (detections.isNotEmpty()) {
                Log.i(TAG, "🐾 DETECCIÓN Frame $frameProcessed: ${detections.size} animales")
                detections.forEach { det ->
                    Log.i(TAG, "   ${det.animal.getLocalizedName()}: ${det.getConfidencePercentage()}%")
                }
            }
            
            detections
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error frame $frameProcessed: ${e.message}", e)
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
                byteBuffer.putFloat(((value shr 16) and 0xFF) / 255.0f)
                byteBuffer.putFloat(((value shr 8) and 0xFF) / 255.0f)
                byteBuffer.putFloat((value and 0xFF) / 255.0f)
            }
        }
        
        byteBuffer.rewind()
        return byteBuffer
    }

    private fun processOutput8400x85(
        output: Array<FloatArray>,
        minConfidence: Float,
        scaleX: Float,
        scaleY: Float
    ): List<Detection> {
        val detections = mutableListOf<Detection>()
        val animalIds = AnimalType.getAllIds()

        for (i in 0 until 8400) {
            val prediction = output[i]
            if (prediction.size < 85) continue
            
            val objectness = prediction[4]
            if (objectness < minConfidence) continue

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
            if (confidence < minConfidence || bestClassId !in animalIds) continue

            val cx = prediction[0] * scaleX
            val cy = prediction[1] * scaleY
            val w = prediction[2] * scaleX
            val h = prediction[3] * scaleY

            detections.add(createDetection(bestClassId, confidence, cx, cy, w, h))
        }

        return applyNMS(detections)
    }

    private fun processOutput84x8400(
        output: Array<FloatArray>,
        minConfidence: Float,
        scaleX: Float,
        scaleY: Float
    ): List<Detection> {
        val detections = mutableListOf<Detection>()
        val animalIds = AnimalType.getAllIds()

        for (i in 0 until 8400) {
            val cx = output[0][i]
            val cy = output[1][i]
            val w = output[2][i]
            val h = output[3][i]
            
            var bestClassId = -1
            var bestScore = 0f
            
            for (c in 0 until 80) {
                val classScore = output[4 + c][i]
                if (classScore > bestScore) {
                    bestScore = classScore
                    bestClassId = c
                }
            }

            if (bestScore < minConfidence || bestClassId !in animalIds) continue

            detections.add(createDetection(
                bestClassId, bestScore,
                cx * scaleX, cy * scaleY,
                w * scaleX, h * scaleY
            ))
        }

        return applyNMS(detections)
    }

    private fun createDetection(
        classId: Int,
        confidence: Float,
        cx: Float,
        cy: Float,
        w: Float,
        h: Float
    ): Detection {
        val left = cx - w / 2f
        val top = cy - h / 2f
        val right = cx + w / 2f
        val bottom = cy + h / 2f

        val boundingBox = RectF(left, top, right, bottom)
        val distance = estimateDistance(h)

        return Detection(
            animal = AnimalType.fromId(classId)!!,
            confidence = confidence,
            distance = distance,
            boundingBox = boundingBox
        )
    }

    private fun estimateDistance(objectHeight: Float): Int {
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

        return if (unionArea > 0) intersectionArea / unionArea else 0f
    }

    fun release() {
        try {
            interpreter?.close()
            interpreter = null
            isModelLoaded = false
            Log.i(TAG, "🔒 Liberado ($frameProcessed frames procesados)")
        } catch (e: Exception) {
            Log.e(TAG, "Error liberando: ${e.message}")
        }
    }
}