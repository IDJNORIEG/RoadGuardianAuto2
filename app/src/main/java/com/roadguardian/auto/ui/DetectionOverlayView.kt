package com.roadguardian.auto.ui

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.util.Log
import android.view.View
import com.roadguardian.auto.models.AnimalType
import com.roadguardian.auto.models.Detection
import java.util.concurrent.CopyOnWriteArrayList

class DetectionOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val detections = CopyOnWriteArrayList<Detection>()
    private var currentLanguage = "es"

    companion object {
        private const val TAG = "DetectionOverlay"
        
        private val ANIMAL_COLORS = mapOf(
            AnimalType.DOG to Color.parseColor("#FF5722"),
            AnimalType.CAT to Color.parseColor("#9C27B0"),
            AnimalType.HORSE to Color.parseColor("#795548"),
            AnimalType.COW to Color.parseColor("#FFEB3B"),
            AnimalType.SHEEP to Color.parseColor("#E0E0E0"),
            AnimalType.BIRD to Color.parseColor("#00BCD4"),
            AnimalType.ELEPHANT to Color.parseColor("#9E9E9E"),
            AnimalType.BEAR to Color.parseColor("#8D6E63"),
            AnimalType.ZEBRA to Color.parseColor("#000000"),
            AnimalType.GIRAFFE to Color.parseColor("#FF9800")
        )
    }

    private val textPaint = Paint().apply {
        color = Color.WHITE
        textSize = 40f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        isAntiAlias = true
        setShadowLayer(8f, 2f, 2f, Color.BLACK)
    }

    fun updateDetections(newDetections: List<Detection>, language: String) {
        detections.clear()
        detections.addAll(newDetections)
        currentLanguage = language
        
        Log.d(TAG, "📊 Overlay actualizado: ${newDetections.size} detecciones")
        
        // Forzar redibujo inmediato
        postInvalidate()
    }

    fun clearDetections() {
        detections.clear()
        postInvalidate()
    }

    fun scaleDetections(
        detections: List<Detection>,
        @Suppress("UNUSED_PARAMETER") viewWidth: Int,
        @Suppress("UNUSED_PARAMETER") viewHeight: Int
    ): List<Detection> = detections

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        if (detections.isEmpty()) return

        Log.d(TAG, "🎨 Dibujando ${detections.size} detecciones en canvas ${width}x${height}")

        for ((index, detection) in detections.withIndex()) {
            drawDetection(canvas, detection, index)
        }
    }

    private fun drawDetection(canvas: Canvas, detection: Detection, index: Int) {
        val box = detection.boundingBox
        
        // Validar dimensiones
        if (box.width() <= 0 || box.height() <= 0) {
            Log.w(TAG, "⚠️ BBox inválido: width=${box.width()}, height=${box.height()}")
            return
        }

        // Validar que esté dentro de los límites del canvas
        if (box.left < 0 || box.top < 0 || box.right > width || box.bottom > height) {
            Log.w(TAG, "⚠️ BBox fuera de límites: [${ box.left}, ${box.top}, ${box.right}, ${box.bottom}] en canvas ${width}x${height}")
            // Ajustar al canvas
            val adjustedBox = RectF(
                box.left.coerceAtLeast(0f),
                box.top.coerceAtLeast(0f),
                box.right.coerceAtMost(width.toFloat()),
                box.bottom.coerceAtMost(height.toFloat())
            )
            drawBoundingBox(canvas, adjustedBox, detection, index)
        } else {
            drawBoundingBox(canvas, box, detection, index)
        }
    }

    private fun drawBoundingBox(canvas: Canvas, box: RectF, detection: Detection, index: Int) {
        val animalColor = ANIMAL_COLORS[detection.animal] ?: Color.GREEN
        
        // Paint para borde (delgado)
        val boxPaint = Paint().apply {
            color = animalColor
            style = Paint.Style.STROKE
            strokeWidth = 5f
            isAntiAlias = true
        }

        // Paint para fondo de texto
        val textBackgroundPaint = Paint().apply {
            color = animalColor
            style = Paint.Style.FILL
            isAntiAlias = true
            alpha = 220
        }

        // Dibujar rectángulo principal
        canvas.drawRect(box, boxPaint)

        // Texto
        val animalName = detection.animal.getLocalizedName(currentLanguage)
        val label = "$animalName ${detection.getConfidencePercentage()}%"
        val distanceText = "${detection.distance}m"

        val labelBounds = Rect()
        textPaint.getTextBounds(label, 0, label.length, labelBounds)

        // Posición del texto
        val textX = box.left + 10f
        val textY = if (box.top > 100) {
            box.top - 15f
        } else {
            box.bottom + labelBounds.height() + 30f
        }

        // Fondo del texto
        val padding = 15f
        val backgroundRect = RectF(
            textX - padding,
            textY - labelBounds.height() - padding,
            textX + labelBounds.width() + padding * 2,
            textY + padding
        )
        canvas.drawRoundRect(backgroundRect, 10f, 10f, textBackgroundPaint)
        canvas.drawText(label, textX, textY, textPaint)

        // Distancia
        val distanceBounds = Rect()
        textPaint.getTextBounds(distanceText, 0, distanceText.length, distanceBounds)
        
        val distanceY = textY + distanceBounds.height() + padding * 1.5f
        val distanceBackgroundRect = RectF(
            textX - padding,
            distanceY - distanceBounds.height() - padding,
            textX + distanceBounds.width() + padding * 2,
            distanceY + padding
        )
        
        canvas.drawRoundRect(distanceBackgroundRect, 10f, 10f, textBackgroundPaint)
        canvas.drawText(distanceText, textX, distanceY, textPaint)

        // Esquinas mejoradas
        val cornerLength = 50f
        val cornerPaint = Paint().apply {
            color = animalColor
            style = Paint.Style.STROKE
            strokeWidth = 8f
            strokeCap = Paint.Cap.ROUND
            isAntiAlias = true
        }

        // 4 esquinas
        canvas.drawLine(box.left, box.top, box.left + cornerLength, box.top, cornerPaint)
        canvas.drawLine(box.left, box.top, box.left, box.top + cornerLength, cornerPaint)

        canvas.drawLine(box.right, box.top, box.right - cornerLength, box.top, cornerPaint)
        canvas.drawLine(box.right, box.top, box.right, box.top + cornerLength, cornerPaint)

        canvas.drawLine(box.left, box.bottom, box.left + cornerLength, box.bottom, cornerPaint)
        canvas.drawLine(box.left, box.bottom, box.left, box.bottom - cornerLength, cornerPaint)

        canvas.drawLine(box.right, box.bottom, box.right - cornerLength, box.bottom, cornerPaint)
        canvas.drawLine(box.right, box.bottom, box.right, box.bottom - cornerLength, cornerPaint)

        Log.d(TAG, "   ✅ Dibujado #${index + 1}: $animalName en [${box.left.toInt()}, ${box.top.toInt()}, ${box.right.toInt()}, ${box.bottom.toInt()}]")
    }
}