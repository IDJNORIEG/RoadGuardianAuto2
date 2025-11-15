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

    companion object {
        private const val TAG = "DetectionManager"
        private const val CONFIDENCE_THRESHOLD = 0.25f
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
            
            // Listar archivos en assets
            val assetsList = context.assets.list("")
            Log.d(TAG, "📁 Archivos en assets/: ${assetsList?.joinToString(", ")}")
            
            // Intentar abrir el modelo
            Log.d(TAG, "📦 Intentando cargar: $MODEL_FILE")
            
            val modelBuffer = try {
                FileUtil.loadMappedFile(context, MODEL_FILE)
            } catch (e: Exception) {
                Log.e(TAG, "❌ ERROR: No se pudo cargar el archivo del modelo")
                Log.e(TAG, "   Excepción: ${e.javaClass.simpleName}")
                Log.e(TAG, "   Mensaje: ${e.message}")
                
                // Intentar buscar en subdirectorios
                try {
                    val subfolders = context.assets.list("")
                    subfolders?.forEach { folder ->
                        Log.d(TAG, "   Revisando carpeta: $folder")
                        val files = context.assets.list(folder)
                        files?.forEach { file ->
                            Log.d(TAG, "      - $file")
                        }
                    }
                } catch (ex: Exception) {
                    Log.e(TAG, "Error listando assets: ${ex.message}")
                }
                
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    Toast.makeText(context, "ERROR: Modelo no encontrado en assets", Toast.LENGTH_LONG).show()
                }
                return
            }
            
            Log.d(TAG, "✅ Archivo del modelo cargado: ${modelBuffer.capacity()} bytes")
            Log.d(TAG, "📊 Tamaño: ${modelBuffer.capacity() / 1024 / 1024}MB")
            
            val options = Interpreter.Options().apply {
                setNumThreads(4)
                setUseNNAPI(false)
            }
            
            interpreter = Interpreter(modelBuffer, options)
            
            // Verificar forma del modelo
            val inputTensor = interpreter?.getInputTensor(0)
            val outputTensor = interpreter?.getOutputTensor(0)
            
            val inputShape = inputTensor?.shape()
            val outputShape = outputTensor?.shape()
            
            Log.d(TAG, "✅ Intérprete creado correctamente")
            Log.d(TAG, "📊 Input shape: ${inputShape?.contentToString()}")
            Log.d(TAG, "📊 Output shape: ${outputShape?.contentToString()}")
            Log.d(TAG, "📊 Input dataType: ${inputTensor?.dataType()}")
            Log.d(TAG, "📊 Output dataType: ${outputTensor?.dataType()}")
            
            isModelLoaded = true
            
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                Toast.makeText(context, "✅ Modelo YOLOv8 cargado correctamente", Toast.LENGTH_SHORT).show()
            }
            
            Log.d(TAG, SEPARATOR)
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ ERROR CRÍTICO cargando modelo: ${e.message}", e)
            
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                Toast.makeText(context, "ERROR: ${e.message}", Toast.LENGTH_LONG).show()
            }
            
            isModelLoaded = false
        }
    }

    fun detectAnimals(
        bitmap: Bitmap,
        minConfidence: Float = CONFIDENCE_THRESHOLD
    ): List<Detection> {
        frameProcessed++
        
        if (!isModelLoaded || interpreter == null) {
            if (frameProcessed % 30 == 0) {
                Log.w(TAG, "⚠️ Modelo no cargado (frame $frameProcessed)")
            }
            return emptyList()
        }

        return try {
            val startTime = System.currentTimeMillis()
            
            if (frameProcessed % 10 == 0) {
                Log.d(TAG, "🖼️ Procesando frame $frameProcessed: ${bitmap.width}x${bitmap.height}")
            }
            
            // Preprocesar imagen
            val resizedBitmap = Bitmap.createScaledBitmap(bitmap, inputSize, inputSize, true)
            val inputBuffer = bitmapToByteBuffer(resizedBitmap)
            
            // Preparar output buffer
            val outputArray = Array(1) { Array(8400) { FloatArray(85) } }
            
            // Ejecutar inferencia
            interpreter?.run(inputBuffer, outputArray)
            
            val inferenceTime = System.currentTimeMillis() - startTime
            
            if (frameProcessed % 10 == 0) {
                Log.d(TAG, "⏱️ Inferencia: ${inferenceTime}ms")
            }
            
            // Procesar resultados
            val detections = processOutput(
                outputArray[0],
                minConfidence,
                bitmap.width.toFloat() / inputSize,
                bitmap.height.toFloat() / inputSize
            )
            
            if (detections.isNotEmpty()) {
                Log.i(TAG, SEPARATOR)
                Log.i(TAG, "🐾 ¡DETECCIÓN! Frame $frameProcessed")
                Log.i(TAG, "   Encontrados: ${detections.size} animales")
                detections.forEach { det ->
                    Log.i(TAG, "   - ${det.animal.getLocalizedName()}: ${det.getConfidencePercentage()}% a ${det.distance}m")
                }
                Log.i(TAG, SEPARATOR)
                
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    Toast.makeText(context, "🐾 ${detections.size} animal(es) detectado(s)", Toast.LENGTH_SHORT).show()
                }
            }
            
            detections
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error en detección (frame $frameProcessed): ${e.message}", e)
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

    private fun processOutput(
        output: Array<FloatArray>,
        minConfidence: Float,
        scaleX: Float,
        scaleY: Float
    ): List<Detection> {
        val detections = mutableListOf<Detection>()
        val animalIds = AnimalType.getAllIds()

        for (i in output.indices) {
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

            val cx = prediction[0]
            val cy = prediction[1]
            val w = prediction[2]
            val h = prediction[3]

            val left = (cx - w / 2f) * scaleX
            val top = (cy - h / 2f) * scaleY
            val right = (cx + w / 2f) * scaleX
            val bottom = (cy + h / 2f) * scaleY

            val boundingBox = RectF(left, top, right, bottom)
            val distance = estimateDistance(bottom - top)

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
            Log.i(TAG, "🔒 DetectionManager liberado (procesados $frameProcessed frames)")
        } catch (e: Exception) {
            Log.e(TAG, "Error al liberar: ${e.message}")
        }
    }
}