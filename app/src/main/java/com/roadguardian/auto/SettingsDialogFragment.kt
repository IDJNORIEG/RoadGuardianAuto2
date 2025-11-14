package com.roadguardian.auto

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.widget.*
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class SettingsDialogFragment : DialogFragment() {

    private lateinit var settingsManager: SettingsManager
    private var sensitivitySeek: SeekBar? = null
    private var confidenceSeek: SeekBar? = null
    private var soundSwitch: Switch? = null
    private var themeSwitch: Switch? = null
    private var languageSpinner: Spinner? = null
    private var sensitivityLabel: TextView? = null
    private var confidenceLabel: TextView? = null

    companion object {
        private const val TAG = "SettingsDialog"
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        settingsManager = SettingsManager(context)
        Log.d(TAG, "✅ SettingsDialogFragment attached")
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        Log.d(TAG, "📱 Creando diálogo de configuración")
        
        val view = try {
            LayoutInflater.from(requireContext()).inflate(R.layout.dialog_settings, null)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error inflando layout: ${e.message}", e)
            throw e
        }

        try {
            initializeViews(view)
            setupLanguageSpinner()
            loadCurrentSettings()
            setupListeners()
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error inicializando diálogo: ${e.message}", e)
        }

        return MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.settings))
            .setView(view)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                Log.d(TAG, "✅ Configuración guardada")
            }
            .setNegativeButton(android.R.string.cancel, null)
            .create()
    }

    private fun initializeViews(view: android.view.View) {
        try {
            sensitivitySeek = view.findViewById(R.id.sensitivitySeek)
            confidenceSeek = view.findViewById(R.id.confidenceSeek)
            soundSwitch = view.findViewById(R.id.soundSwitch)
            themeSwitch = view.findViewById(R.id.themeSwitch)
            languageSpinner = view.findViewById(R.id.languageSpinner)
            sensitivityLabel = view.findViewById(R.id.sensitivityValue)
            confidenceLabel = view.findViewById(R.id.confidenceValue)
            
            Log.d(TAG, "✅ Vistas inicializadas correctamente")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error inicializando vistas: ${e.message}", e)
            throw e
        }
    }

    private fun setupLanguageSpinner() {
        try {
            val languages = listOf("Español", "English", "Français", "Italiano", "Deutsch", "Русский")
            val adapter = ArrayAdapter(
                requireContext(),
                android.R.layout.simple_spinner_item,
                languages
            )
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            languageSpinner?.adapter = adapter
            
            Log.d(TAG, "✅ Spinner de idiomas configurado")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error configurando spinner: ${e.message}", e)
        }
    }

    private fun loadCurrentSettings() {
        lifecycleScope.launch {
            try {
                val settings = settingsManager.settingsFlow.first()
                
                sensitivitySeek?.progress = (settings.sensitivity * 100).toInt()
                confidenceSeek?.progress = (settings.minConfidence * 100).toInt()
                soundSwitch?.isChecked = settings.soundEnabled
                
                val prefs = requireContext().getSharedPreferences("theme_prefs", Context.MODE_PRIVATE)
                themeSwitch?.isChecked = prefs.getBoolean("dark_mode", false)

                val languageIndex = when (settings.language) {
                    "en" -> 1
                    "fr" -> 2
                    "it" -> 3
                    "de" -> 4
                    "ru" -> 5
                    else -> 0
                }
                languageSpinner?.setSelection(languageIndex)

                sensitivityLabel?.text = "${(settings.sensitivity * 100).toInt()}%"
                confidenceLabel?.text = "${(settings.minConfidence * 100).toInt()}%"
                
                Log.d(TAG, "✅ Configuración cargada: sens=${settings.sensitivity}, conf=${settings.minConfidence}")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error cargando configuración: ${e.message}", e)
            }
        }
    }

    private fun setupListeners() {
        try {
            // Sensibilidad
            sensitivitySeek?.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser) {
                        val value = progress / 100f
                        lifecycleScope.launch {
                            settingsManager.updateSensitivity(value)
                        }
                        sensitivityLabel?.text = "$progress%"
                    }
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })

            // Confianza
            confidenceSeek?.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser) {
                        val value = progress / 100f
                        lifecycleScope.launch {
                            settingsManager.updateMinConfidence(value)
                        }
                        confidenceLabel?.text = "$progress%"
                    }
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })

            // Sonido
            soundSwitch?.setOnCheckedChangeListener { _, isChecked ->
                lifecycleScope.launch {
                    settingsManager.updateSoundEnabled(isChecked)
                    Log.d(TAG, "🔊 Sonido: $isChecked")
                }
            }

            // Tema
            themeSwitch?.setOnCheckedChangeListener { _, isChecked ->
                try {
                    val prefs = requireContext().getSharedPreferences("theme_prefs", Context.MODE_PRIVATE)
                    prefs.edit().putBoolean("dark_mode", isChecked).apply()
                    
                    (activity as? MainActivity)?.let { mainActivity ->
                        mainActivity.applyTheme(isChecked)
                        mainActivity.recreate()
                    }
                    
                    Log.d(TAG, "🎨 Tema cambiado: dark=$isChecked")
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Error cambiando tema: ${e.message}", e)
                }
            }

            // Idioma
            languageSpinner?.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: android.view.View?, pos: Int, id: Long) {
                    val langCode = when (pos) {
                        1 -> "en"
                        2 -> "fr"
                        3 -> "it"
                        4 -> "de"
                        5 -> "ru"
                        else -> "es"
                    }
                    lifecycleScope.launch {
                        settingsManager.updateLanguage(langCode)
                        Log.d(TAG, "🌍 Idioma cambiado: $langCode")
                    }
                }

                override fun onNothingSelected(parent: AdapterView<*>?) {}
            }
            
            Log.d(TAG, "✅ Listeners configurados")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error configurando listeners: ${e.message}", e)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // Limpiar referencias
        sensitivitySeek = null
        confidenceSeek = null
        soundSwitch = null
        themeSwitch = null
        languageSpinner = null
        sensitivityLabel = null
        confidenceLabel = null
        
        Log.d(TAG, "🗑️ Vista destruida")
    }
}