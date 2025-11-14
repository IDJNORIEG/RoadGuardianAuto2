package com.roadguardian.auto.ui

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
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

    private val boxPaint = Paint().apply {
        color = context.getColor(R.color.green_accent)
        style = Paint.Style.STROKE
        strokeWidth = 6f
        isAntiAlias = true
    }

    private val textPaint = Paint().apply {
        color = Color.WHITE
        textSize = 42f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        isAntiAlias = true
        setShadowLayer(4f, 2f, 2f, Color.BLACK)
    }

    private val backgroundPaint = Paint().apply {
        color = Color.argb(180, 0, 0, 0)
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    fun updateDetections(newDetections: List<Detection>, language: String) {
        detections.clear()
        detections.addAll(newDetections)
        currentLanguage = language
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
    ): List<Detection> {
        // En caso de tener coordenadas reales del modelo, se escalarían aquí
        return detections
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        if (detections.isEmpty()) return

        var yOffset = 100f
        val padding = 20f

        for (detection in detections) {
            val animalName = detection.animal.getLocalizedName(currentLanguage)
            val label = "$animalName (${detection.getConfidencePercentage()}%) - ${detection.distance}m"
            
            val textBounds = Rect()
            textPaint.getTextBounds(label, 0, label.length, textBounds)
            
            val rectLeft = padding
            val rectTop = yOffset - textBounds.height() - padding
            val rectRight = textBounds.width() + (padding * 3)
            val rectBottom = yOffset + padding
            
            canvas.drawRoundRect(
                rectLeft,
                rectTop,
                rectRight,
                rectBottom,
                15f,
                15f,
                backgroundPaint
            )
            
            canvas.drawText(label, padding * 2, yOffset, textPaint)
            
            yOffset += textBounds.height() + (padding * 3)
        }
    }
}