package com.roadguardian.auto.models

import android.graphics.RectF

/**
 * Representa una detección de animal con toda su información
 */
data class Detection(
    val animal: AnimalType,
    val confidence: Float,
    val distance: Int = 0,
    val boundingBox: RectF = RectF(),
    val timestamp: Long = System.currentTimeMillis()
) {
    fun getConfidencePercentage(): Int = (confidence * 100).toInt()
    
    fun getDistanceInMeters(): String = "${distance}m"
}
