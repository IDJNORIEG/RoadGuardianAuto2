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
        
        // Colores únicos por tipo de animal
        private val ANIMAL_COLORS = mapOf(
            AnimalType.DOG to Color.parseColor("#FF5722"),      // Naranja rojizo
            AnimalType.CAT to Color.parseColor("#9C27B0"),      // Púrpura
            AnimalType.HORSE to Color.parseColor("#795548"),    // Marrón
            AnimalType.COW to Color.parseColor("#FFEB3B"),      // Amarillo
            AnimalType.SHEEP to Color.parseColor("#E0E0E0"),    // Gris claro
            AnimalType.BIRD to Color.parseColor("#00BCD4"),     // Cian
            AnimalType.ELEPHANT to Color.parseColor("#9E9E9E"), // Gris
            AnimalType.BEAR to Color.parseColor("#8D6E63"),     // Marrón oscuro
            AnimalType.ZEBRA to Color.parseColor("#000000"),    // Negro
            AnimalType.GIRAFFE to Color.parseColor("#FF9800")   // Naranja
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
        
        Log.d(TAG, "📊 Actualizando: ${newDetections.size} detecciones")
        invalidate()
    }

    fun clearDetections() {
        detections.clear()
        invalidate()
    }

    fun scaleDetections(
        detections: List<Detection>,
        viewWidth: Int,
        viewHeight: Int
    ): List<Detection> = detections

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        if (detections.isEmpty()) return

        Log.d(TAG, "🎨 Dibujando ${detections.size} detecciones")

        for (detection in detections) {
            drawDetection(canvas, detection)
        }
    }

    private fun drawDetection(canvas: Canvas, detection: Detection) {
        val box = detection.boundingBox
        
        if (box.width() <= 0 || box.height() <= 0) {
            Log.w(TAG, "⚠️ BBox inválido: $box")
            return
        }

        // Obtener color según el tipo de animal
        val animalColor = ANIMAL_COLORS[detection.animal] ?: Color.GREEN
        
        // Paint para el borde (delgado)
        val boxPaint = Paint().apply {
            color = animalColor
            style = Paint.Style.STROKE
            strokeWidth = 4f // Línea delgada
            isAntiAlias = true
        }

        // Paint para el fondo del texto
        val textBackgroundPaint = Paint().apply {
            color = animalColor
            style = Paint.Style.FILL
            isAntiAlias = true
            alpha = 220
        }

        // Dibujar bounding box
        canvas.drawRect(box, boxPaint)

        // Preparar texto
        val animalName = detection.animal.getLocalizedName(currentLanguage)
        val label = "$animalName ${detection.getConfidencePercentage()}%"
        val distanceText = "${detection.distance}m"

        val labelBounds = Rect()
        textPaint.getTextBounds(label, 0, label.length, labelBounds)

        // Posición del texto (arriba del box)
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

        // Dibujar texto
        canvas.drawText(label, textX, textY, textPaint)

        // Distancia debajo
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

        // Esquinas para mejor visibilidad
        val cornerLength = 40f
        val cornerPaint = Paint().apply {
            color = animalColor
            style = Paint.Style.STROKE
            strokeWidth = 6f
            isAntiAlias = true
        }

        // Esquina superior izquierda
        canvas.drawLine(box.left, box.top, box.left + cornerLength, box.top, cornerPaint)
        canvas.drawLine(box.left, box.top, box.left, box.top + cornerLength, cornerPaint)

        // Esquina superior derecha
        canvas.drawLine(box.right, box.top, box.right - cornerLength, box.top, cornerPaint)
        canvas.drawLine(box.right, box.top, box.right, box.top + cornerLength, cornerPaint)

        // Esquina inferior izquierda
        canvas.drawLine(box.left, box.bottom, box.left + cornerLength, box.bottom, cornerPaint)
        canvas.drawLine(box.left, box.bottom, box.left, box.bottom - cornerLength, cornerPaint)

        // Esquina inferior derecha
        canvas.drawLine(box.right, box.bottom, box.right - cornerLength, box.bottom, cornerPaint)
        canvas.drawLine(box.right, box.bottom, box.right, box.bottom - cornerLength, cornerPaint)

        Log.d(TAG, "   Dibujado: $animalName (${String.format("#%06X", 0xFFFFFF and animalColor)})")
    }
}