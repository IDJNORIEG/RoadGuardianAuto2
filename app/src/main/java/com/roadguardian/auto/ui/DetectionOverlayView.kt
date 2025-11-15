package com.roadguardian.auto.ui

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.util.Log
import android.view.View
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
    }

    private val boxPaint = Paint().apply {
        color = Color.parseColor("#00FF00")
        style = Paint.Style.STROKE
        strokeWidth = 10f
        isAntiAlias = true
    }

    private val textPaint = Paint().apply {
        color = Color.WHITE
        textSize = 50f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        isAntiAlias = true
        setShadowLayer(10f, 2f, 2f, Color.BLACK)
    }

    private val textBackgroundPaint = Paint().apply {
        color = Color.parseColor("#00FF00")
        style = Paint.Style.FILL
        isAntiAlias = true
        alpha = 200
    }

    private val alertBoxPaint = Paint().apply {
        color = Color.RED
        style = Paint.Style.STROKE
        strokeWidth = 12f
        isAntiAlias = true
    }

    fun updateDetections(newDetections: List<Detection>, language: String) {
        detections.clear()
        detections.addAll(newDetections)
        currentLanguage = language
        
        Log.d(TAG, "📊 Actualizando: ${newDetections.size} detecciones")
        
        // Forzar redibujo INMEDIATO
        invalidate()
        requestLayout()
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
        
        if (detections.isEmpty()) {
            return
        }

        Log.d(TAG, "🎨 Dibujando ${detections.size} detecciones")

        // Fondo semi-transparente si hay detecciones
        val bgPaint = Paint().apply {
            color = Color.argb(50, 255, 0, 0)
            style = Paint.Style.FILL
        }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

        for ((index, detection) in detections.withIndex()) {
            drawDetection(canvas, detection, index)
        }
    }

    private fun drawDetection(canvas: Canvas, detection: Detection, index: Int) {
        val box = detection.boundingBox
        
        // Verificar dimensiones
        if (box.width() <= 0 || box.height() <= 0) {
            Log.w(TAG, "⚠️ BBox inválido: $box")
            return
        }

        val isCritical = detection.distance < 30
        val currentBoxPaint = if (isCritical) alertBoxPaint else boxPaint

        // Dibujar bounding box MÁS GRANDE Y VISIBLE
        canvas.drawRect(box, currentBoxPaint)
        
        // Dibujar líneas cruzadas para hacerlo más visible
        canvas.drawLine(box.left, box.top, box.right, box.bottom, currentBoxPaint)
        canvas.drawLine(box.right, box.top, box.left, box.bottom, currentBoxPaint)

        // Texto
        val animalName = detection.animal.getLocalizedName(currentLanguage)
        val label = "$animalName ${detection.getConfidencePercentage()}%"
        val distanceText = "${detection.distance}m"

        val labelBounds = Rect()
        textPaint.getTextBounds(label, 0, label.length, labelBounds)

        // Posición del texto
        val textX = box.left + 20f
        val textY = if (box.top > 100) box.top - 20f else box.bottom + labelBounds.height() + 40f

        // Fondo del texto
        val padding = 20f
        val backgroundRect = RectF(
            textX - padding,
            textY - labelBounds.height() - padding,
            textX + labelBounds.width() + padding * 2,
            textY + padding
        )
        canvas.drawRoundRect(backgroundRect, 15f, 15f, textBackgroundPaint)

        // Texto principal
        canvas.drawText(label, textX, textY, textPaint)

        // Distancia
        val distanceY = textY + labelBounds.height() + padding * 2
        val distanceBounds = Rect()
        textPaint.getTextBounds(distanceText, 0, distanceText.length, distanceBounds)
        
        val distanceBackgroundRect = RectF(
            textX - padding,
            distanceY - distanceBounds.height() - padding,
            textX + distanceBounds.width() + padding * 2,
            distanceY + padding
        )
        
        canvas.drawRoundRect(distanceBackgroundRect, 15f, 15f, textBackgroundPaint)
        canvas.drawText(distanceText, textX, distanceY, textPaint)

        // Indicador de número de detección
        val numberPaint = Paint().apply {
            color = Color.YELLOW
            textSize = 60f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        canvas.drawText("#${index + 1}", box.right - 80f, box.top + 80f, numberPaint)

        Log.d(TAG, "   Dibujado: $animalName en [${box.left}, ${box.top}, ${box.right}, ${box.bottom}]")
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        Log.d(TAG, "📐 Tamaño cambiado: ${w}x${h}")
    }
}