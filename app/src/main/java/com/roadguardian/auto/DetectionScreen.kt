package com.roadguardian.auto

import androidx.car.app.Screen
import androidx.car.app.CarContext
import androidx.car.app.model.*
import androidx.lifecycle.lifecycleScope
import com.roadguardian.auto.models.Detection
import com.roadguardian.auto.models.AnimalType
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collect
import java.util.*

class DetectionScreen(carContext: CarContext) : Screen(carContext) {

    private val settingsManager = SettingsManager(carContext)
    private var detectionCount = 0
    private var lastDetection: Detection? = null
    private var currentLanguage: String = Locale.getDefault().language

    override fun onGetTemplate(): Template {
        val title = when (currentLanguage) {
            "es" -> "Detección en curso"
            "en" -> "Detection Running"
            "fr" -> "Détection en cours"
            "it" -> "Rilevamento in corso"
            "de" -> "Erkennung läuft"
            "ru" -> "Обнаружение запущено"
            else -> "Detection"
        }

        val alertText = lastDetection?.let {
            "${carContext.getString(R.string.alert_animal)}: ${it.animal.displayName} (${it.getConfidencePercentage()}%)"
        } ?: carContext.getString(R.string.status_detecting)

        val listBuilder = ItemList.Builder().apply {
            addItem(
                Row.Builder()
                    .setTitle("${carContext.getString(R.string.detections_count)}: $detectionCount")
                    .setImage(CarIcon.APP_ICON)
                    .build()
            )

            lastDetection?.let { detection ->
                addItem(
                    Row.Builder()
                        .setTitle("${carContext.getString(R.string.distance)}: ${detection.distance} m")
                        .addText("${carContext.getString(R.string.reduce_speed)} ⚠️")
                        .build()
                )
            }
        }

        val templateBuilder = PaneTemplate.Builder(
            Pane.Builder()
                .addRow(Row.Builder().setTitle(alertText).build())
                .build()
        )

        templateBuilder.setHeaderAction(Action.APP_ICON)
        templateBuilder.setTitle(title)
        templateBuilder.setActionStrip(
            ActionStrip.Builder()
                .addAction(
                    Action.Builder()
                        .setTitle(carContext.getString(R.string.stop_detection))
                        .setOnClickListener { finish() }
                        .build()
                )
                .build()
        )
        return templateBuilder.build()
    }

    fun updateDetection(detection: Detection) {
        detectionCount++
        lastDetection = detection
        invalidate()
    }
}
