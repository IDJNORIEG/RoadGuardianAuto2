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
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val detections = CopyOnWriteArrayList<Detection>()
    private val boxPaint = Paint().apply {
        color = context.getColor(R.color.green_accent)
        style = Paint.Style.STROKE
        strokeWidth = 5f
        isAntiAlias = true
    }

    private val textPaint = Paint().apply {
        color = Color.WHITE
        textSize = 36f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        isAntiAlias = true
    }

    fun updateDetections(newDetections: List<Detection>, language: String) {
        detections.clear()
        detections.addAll(newDetections)
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
        return detections.map {
            it.copy() // En este caso, si tuvieras coordenadas, las escalarías aquí
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        for (detection in detections) {
            // Simulación: rectángulo central
            val left = width * 0.3f
            val top = height * 0.3f
            val right = width * 0.7f
            val bottom = height * 0.7f

            canvas.drawRect(left, top, right, bottom, boxPaint)

            val label = "${detection.animal.displayName} (${detection.getConfidencePercentage()}%)"
            canvas.drawText(label, left + 10, top - 10, textPaint)
        }
    }
}
