package com.roadguardian.auto

import androidx.car.app.Screen
import androidx.car.app.CarContext
import androidx.car.app.model.*
import kotlinx.coroutines.launch
import androidx.lifecycle.lifecycleScope
import androidx.car.app.model.Template
import androidx.car.app.model.MessageTemplate
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip

class SettingsScreen(carContext: CarContext) : Screen(carContext) {

    private val settingsManager = SettingsManager(carContext)
    private var sensitivity = 0.5f
    private var confidence = 0.5f
    private var soundEnabled = true
    private var darkMode = false

    override fun onGetTemplate(): Template {
        val listBuilder = ItemList.Builder()

        listBuilder.addItem(
            Row.Builder()
                .setTitle("${carContext.getString(R.string.sensitivity)}: ${(sensitivity * 100).toInt()}%")
                .setOnClickListener {
                    updateSensitivity((sensitivity + 0.1f).coerceAtMost(1f))
                }
                .build()
        )

        listBuilder.addItem(
            Row.Builder()
                .setTitle("${carContext.getString(R.string.confidence)}: ${(confidence * 100).toInt()}%")
                .setOnClickListener {
                    updateConfidence((confidence + 0.1f).coerceAtMost(1f))
                }
                .build()
        )

        listBuilder.addItem(
            Row.Builder()
                .setTitle(
                    if (soundEnabled)
                        carContext.getString(R.string.sound_alert) + ": ON"
                    else
                        carContext.getString(R.string.sound_alert) + ": OFF"
                )
                .setOnClickListener {
                    soundEnabled = !soundEnabled
                    lifecycleScope.launch {
                        settingsManager.updateSoundEnabled(soundEnabled)
                        invalidate()
                    }
                }
                .build()
        )

        listBuilder.addItem(
            Row.Builder()
                .setTitle(if (darkMode) "🌙 Modo Oscuro" else "☀️ Modo Claro")
                .setOnClickListener {
                    darkMode = !darkMode
                    carContext.getSharedPreferences("theme_prefs", 0)
                        .edit().putBoolean("dark_mode", darkMode).apply()
                    (carContext as? MainActivity)?.applyTheme(darkMode)
                    invalidate()
                }
                .build()
        )

        return ListTemplate.Builder()
            .setTitle(carContext.getString(R.string.settings))
            .setSingleList(listBuilder.build())
            .setHeaderAction(Action.BACK)
            .build()
    }

    private fun updateSensitivity(value: Float) {
        sensitivity = value
        lifecycleScope.launch {
            settingsManager.updateSensitivity(value)
            invalidate()
        }
    }

    private fun updateConfidence(value: Float) {
        confidence = value
        lifecycleScope.launch {
            settingsManager.updateMinConfidence(value)
            invalidate()
        }
    }
}
