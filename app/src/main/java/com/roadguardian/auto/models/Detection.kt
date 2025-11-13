package com.roadguardian.auto.models

import android.graphics.RectF

enum class AnimalType(val displayName: String) {
    DOG("Perro"),
    CAT("Gato"),
    COW("Vaca"),
    HORSE("Caballo"),
    UNKNOWN("Desconocido")
}

data class Detection(
    val animal: AnimalType,
    val confidence: Float,
    val boundingBox: RectF,
    val distance: Float
) {
    fun getConfidencePercentage(): Int = (confidence * 100).toInt()
}
