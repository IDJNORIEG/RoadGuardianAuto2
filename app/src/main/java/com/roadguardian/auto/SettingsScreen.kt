package com.roadguardian.auto

import androidx.car.app.Screen
import androidx.car.app.CarContext
import androidx.car.app.model.*
import kotlinx.coroutines.launch
import androidx.lifecycle.lifecycleScope
import android.util.Log

class SettingsScreen(carContext: CarContext) : Screen(carContext) {

    private val settingsManager = SettingsManager(carContext)
    private var sensitivity = 0.5f
    private var confidence = 0.5f
    private var soundEnabled = true

    companion object {
        private const val TAG = "SettingsScreen"
    }

    init {
        // Cargar configuración actual
        lifecycleScope.launch {
            settingsManager.settingsFlow.collect { settings ->
                sensitivity = settings.sensitivity
                confidence = settings.minConfidence
                soundEnabled = settings.soundEnabled
                invalidate()
            }
        }
    }

    override fun onGetTemplate(): Template {
        val listBuilder = ItemList.Builder()

        // Sensibilidad
        listBuilder.addItem(
            Row.Builder()
                .setTitle("${carContext.getString(R.string.sensitivity)}: ${(sensitivity * 100).toInt()}%")
                .addText("Ajustar sensibilidad de detección")
                .setOnClickListener {
                    updateSensitivity((sensitivity + 0.1f).coerceAtMost(1f))
                }
                .build()
        )

        // Confianza
        listBuilder.addItem(
            Row.Builder()
                .setTitle("${carContext.getString(R.string.confidence)}: ${(confidence * 100).toInt()}%")
                .addText("Confianza mínima para alertas")
                .setOnClickListener {
                    updateConfidence((confidence + 0.1f).coerceAtMost(1f))
                }
                .build()
        )

        // Sonido
        listBuilder.addItem(
            Row.Builder()
                .setTitle(
                    if (soundEnabled)
                        "${carContext.getString(R.string.sound_alert)}: Activado"
                    else
                        "${carContext.getString(R.string.sound_alert)}: Desactivado"
                )
                .addText("Tocar para cambiar")
                .setOnClickListener {
                    soundEnabled = !soundEnabled
                    lifecycleScope.launch {
                        try {
                            settingsManager.updateSoundEnabled(soundEnabled)
                            invalidate()
                            Log.d(TAG, "🔊 Sonido: $soundEnabled")
                        } catch (e: Exception) {
                            Log.e(TAG, "Error actualizando sonido: ${e.message}")
                        }
                    }
                }
                .build()
        )

        // Reset
        listBuilder.addItem(
            Row.Builder()
                .setTitle("Restaurar valores predeterminados")
                .addText("Sensibilidad: 50%, Confianza: 50%")
                .setOnClickListener {
                    resetToDefaults()
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
            try {
                settingsManager.updateSensitivity(value)
                invalidate()
                Log.d(TAG, "📊 Sensibilidad: ${(value * 100).toInt()}%")
            } catch (e: Exception) {
                Log.e(TAG, "Error actualizando sensibilidad: ${e.message}")
            }
        }
    }

    private fun updateConfidence(value: Float) {
        confidence = value
        lifecycleScope.launch {
            try {
                settingsManager.updateMinConfidence(value)
                invalidate()
                Log.d(TAG, "📊 Confianza: ${(value * 100).toInt()}%")
            } catch (e: Exception) {
                Log.e(TAG, "Error actualizando confianza: ${e.message}")
            }
        }
    }

    private fun resetToDefaults() {
        sensitivity = 0.5f
        confidence = 0.5f
        soundEnabled = true
        
        lifecycleScope.launch {
            try {
                settingsManager.updateSensitivity(0.5f)
                settingsManager.updateMinConfidence(0.5f)
                settingsManager.updateSoundEnabled(true)
                invalidate()
                Log.d(TAG, "🔄 Configuración restaurada a valores predeterminados")
            } catch (e: Exception) {
                Log.e(TAG, "Error restaurando configuración: ${e.message}")
            }
        }
    }
}