package com.roadguardian.auto.ui

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.util.Log
import android.view.View
import com.roadguardian.auto.R
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
        private const val TAG = "DetectionOverlayView"
    }

    // Paint para los bounding boxes
    private val boxPaint = Paint().apply {
        color = Color.parseColor("#4CAF50") // Verde
        style = Paint.Style.STROKE
        strokeWidth = 8f
        isAntiAlias = true
    }

    // Paint para el texto
    private val textPaint = Paint().apply {
        color = Color.WHITE
        textSize = 48f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        isAntiAlias = true
        setShadowLayer(8f, 2f, 2f, Color.BLACK)
    }

    // Paint para el fondo del texto
    private val textBackgroundPaint = Paint().apply {
        color = Color.parseColor("#4CAF50") // Verde
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    // Paint para alerta de distancia crítica
    private val alertBoxPaint = Paint().apply {
        color = Color.RED
        style = Paint.Style.STROKE
        strokeWidth = 10f
        isAntiAlias = true
    }

    private val alertTextBackgroundPaint = Paint().apply {
        color = Color.RED
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    fun updateDetections(newDetections: List<Detection>, language: String) {
        detections.clear()
        detections.addAll(newDetections)
        currentLanguage = language
        
        Log.d(TAG, "📊 Actualizando overlay con ${newDetections.size} detecciones")
        
        // Forzar redibujo
        postInvalidate()
    }

    fun clearDetections() {
        detections.clear()
        Log.d(TAG, "🧹 Limpiando detecciones del overlay")
        postInvalidate()
    }

    fun scaleDetections(
        detections: List<Detection>,
        viewWidth: Int,
        viewHeight: Int
    ): List<Detection> {
        if (viewWidth <= 0 || viewHeight <= 0) return detections
        
        return detections.map { detection ->
            val scaledBox = RectF(
                detection.boundingBox.left,
                detection.boundingBox.top,
                detection.boundingBox.right,
                detection.boundingBox.bottom
            )
            detection.copy(boundingBox = scaledBox)
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        if (detections.isEmpty()) {
            return
        }

        Log.d(TAG, "🎨 Dibujando ${detections.size} detecciones en canvas")

        for (detection in detections) {
            drawDetection(canvas, detection)
        }
    }

    private fun drawDetection(canvas: Canvas, detection: Detection) {
        val box = detection.boundingBox
        
        // Verificar que el bounding box tenga dimensiones válidas
        if (box.width() <= 0 || box.height() <= 0) {
            Log.w(TAG, "⚠️ Bounding box inválido: ${box}")
            return
        }

        // Decidir si es alerta crítica (distancia < 30m)
        val isCritical = detection.distance < 30
        val currentBoxPaint = if (isCritical) alertBoxPaint else boxPaint
        val currentTextBgPaint = if (isCritical) alertTextBackgroundPaint else textBackgroundPaint

        // Dibujar bounding box
        canvas.drawRect(box, currentBoxPaint)

        // Preparar texto
        val animalName = detection.animal.getLocalizedName(currentLanguage)
        val label = "$animalName ${detection.getConfidencePercentage()}%"
        val distanceText = "${detection.distance}m"

        // Medir texto
        val labelBounds = Rect()
        textPaint.getTextBounds(label, 0, label.length, labelBounds)
        
        val distanceBounds = Rect()
        textPaint.getTextBounds(distanceText, 0, distanceText.length, distanceBounds)

        // Calcular posición del texto (arriba del bounding box)
        val textX = box.left + 10f
        val textY = if (box.top > 100) {
            box.top - 20f
        } else {
            box.bottom + labelBounds.height() + 30f
        }

        // Dibujar fondo del texto
        val padding = 16f
        val backgroundRect = RectF(
            textX - padding,
            textY - labelBounds.height() - padding,
            textX + labelBounds.width() + padding * 2,
            textY + padding
        )
        canvas.drawRoundRect(backgroundRect, 12f, 12f, currentTextBgPaint)

        // Dibujar texto
        canvas.drawText(label, textX, textY, textPaint)

        // Dibujar distancia debajo
        val distanceY = textY + distanceBounds.height() + padding * 2
        val distanceBackgroundRect = RectF(
            textX - padding,
            distanceY - distanceBounds.height() - padding,
            textX + distanceBounds.width() + padding * 2,
            distanceY + padding
        )
        
        canvas.drawRoundRect(distanceBackgroundRect, 12f, 12f, currentTextBgPaint)
        canvas.drawText(distanceText, textX, distanceY, textPaint)

        // Si es crítico, agregar parpado (efecto de alerta)
        if (isCritical) {
            val alertPaint = Paint().apply {
                color = Color.argb(100, 255, 0, 0)
                style = Paint.Style.FILL
            }
            canvas.drawRect(box, alertPaint)
        }
    }
}