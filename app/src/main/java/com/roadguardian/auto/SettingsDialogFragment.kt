package com.roadguardian.auto

import android.app.AlertDialog
import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.*
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class SettingsDialogFragment : DialogFragment() {

    private lateinit var settingsManager: SettingsManager
    
    companion object {
        private const val TAG = "SettingsDialog"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            settingsManager = SettingsManager(requireContext())
            Log.d(TAG, "✅ SettingsManager creado")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error creando SettingsManager: ${e.message}", e)
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        Log.d(TAG, "📱 Creando diálogo...")
        
        return try {
            val inflater = LayoutInflater.from(requireContext())
            val view = inflater.inflate(R.layout.dialog_settings, null)
            
            setupViews(view)
            
            AlertDialog.Builder(requireContext())
                .setTitle("Configuración")
                .setView(view)
                .setPositiveButton("Aceptar") { dialog, _ ->
                    Log.d(TAG, "✅ Configuración guardada")
                    dialog.dismiss()
                }
                .setNegativeButton("Cancelar") { dialog, _ ->
                    Log.d(TAG, "❌ Configuración cancelada")
                    dialog.dismiss()
                }
                .create()
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error fatal creando diálogo: ${e.message}", e)
            AlertDialog.Builder(requireContext())
                .setTitle("Error")
                .setMessage("No se pudo abrir la configuración: ${e.message}")
                .setPositiveButton("OK", null)
                .create()
        }
    }

    private fun setupViews(view: View) {
        try {
            val sensitivitySeek = view.findViewById<SeekBar>(R.id.sensitivitySeek)
            val confidenceSeek = view.findViewById<SeekBar>(R.id.confidenceSeek)
            val soundSwitch = view.findViewById<Switch>(R.id.soundSwitch)
            val themeSwitch = view.findViewById<Switch>(R.id.themeSwitch)
            val languageSpinner = view.findViewById<Spinner>(R.id.languageSpinner)
            val sensitivityLabel = view.findViewById<TextView>(R.id.sensitivityValue)
            val confidenceLabel = view.findViewById<TextView>(R.id.confidenceValue)

            if (sensitivitySeek == null || confidenceSeek == null || soundSwitch == null ||
                themeSwitch == null || languageSpinner == null || sensitivityLabel == null ||
                confidenceLabel == null) {
                Log.e(TAG, "❌ Alguna vista es null")
                return
            }

            Log.d(TAG, "✅ Todas las vistas encontradas")

            // Configurar spinner de idiomas
            setupLanguageSpinner(languageSpinner)

            // Cargar configuración actual
            loadSettings(
                sensitivitySeek, confidenceSeek, soundSwitch, 
                themeSwitch, languageSpinner, sensitivityLabel, confidenceLabel
            )

            // Configurar listeners
            setupListeners(
                sensitivitySeek, confidenceSeek, soundSwitch,
                themeSwitch, languageSpinner, sensitivityLabel, confidenceLabel
            )

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error en setupViews: ${e.message}", e)
        }
    }

    private fun setupLanguageSpinner(spinner: Spinner) {
        try {
            val languages = arrayOf("Español", "English", "Français", "Italiano", "Deutsch", "Русский")
            val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, languages)
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            spinner.adapter = adapter
            Log.d(TAG, "✅ Spinner configurado")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error configurando spinner: ${e.message}", e)
        }
    }

    private fun loadSettings(
        sensitivitySeek: SeekBar,
        confidenceSeek: SeekBar,
        soundSwitch: Switch,
        themeSwitch: Switch,
        languageSpinner: Spinner,
        sensitivityLabel: TextView,
        confidenceLabel: TextView
    ) {
        lifecycleScope.launch {
            try {
                val settings = settingsManager.settingsFlow.first()
                
                val sensProgress = (settings.sensitivity * 100).toInt()
                val confProgress = (settings.minConfidence * 100).toInt()
                
                sensitivitySeek.progress = sensProgress
                confidenceSeek.progress = confProgress
                soundSwitch.isChecked = settings.soundEnabled
                
                sensitivityLabel.text = "$sensProgress%"
                confidenceLabel.text = "$confProgress%"
                
                val prefs = requireContext().getSharedPreferences("theme_prefs", Context.MODE_PRIVATE)
                themeSwitch.isChecked = prefs.getBoolean("dark_mode", false)

                val langIndex = when (settings.language) {
                    "en" -> 1
                    "fr" -> 2
                    "it" -> 3
                    "de" -> 4
                    "ru" -> 5
                    else -> 0
                }
                languageSpinner.setSelection(langIndex)

                Log.d(TAG, "✅ Configuración cargada: sens=$sensProgress%, conf=$confProgress%")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error cargando settings: ${e.message}", e)
            }
        }
    }

    private fun setupListeners(
        sensitivitySeek: SeekBar,
        confidenceSeek: SeekBar,
        soundSwitch: Switch,
        themeSwitch: Switch,
        languageSpinner: Spinner,
        sensitivityLabel: TextView,
        confidenceLabel: TextView
    ) {
        try {
            // Sensibilidad
            sensitivitySeek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser) {
                        sensitivityLabel.text = "$progress%"
                        val value = progress / 100f
                        lifecycleScope.launch {
                            try {
                                settingsManager.updateSensitivity(value)
                                Log.d(TAG, "📊 Sensibilidad: $progress%")
                            } catch (e: Exception) {
                                Log.e(TAG, "❌ Error actualizando sensibilidad: ${e.message}")
                            }
                        }
                    }
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })

            // Confianza
            confidenceSeek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser) {
                        confidenceLabel.text = "$progress%"
                        val value = progress / 100f
                        lifecycleScope.launch {
                            try {
                                settingsManager.updateMinConfidence(value)
                                Log.d(TAG, "📊 Confianza: $progress%")
                            } catch (e: Exception) {
                                Log.e(TAG, "❌ Error actualizando confianza: ${e.message}")
                            }
                        }
                    }
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })

            // Sonido
            soundSwitch.setOnCheckedChangeListener { _, isChecked ->
                lifecycleScope.launch {
                    try {
                        settingsManager.updateSoundEnabled(isChecked)
                        Log.d(TAG, "🔊 Sonido: $isChecked")
                    } catch (e: Exception) {
                        Log.e(TAG, "❌ Error actualizando sonido: ${e.message}")
                    }
                }
            }

            // Tema
            themeSwitch.setOnCheckedChangeListener { _, isChecked ->
                try {
                    val prefs = requireContext().getSharedPreferences("theme_prefs", Context.MODE_PRIVATE)
                    prefs.edit().putBoolean("dark_mode", isChecked).apply()
                    
                    Toast.makeText(requireContext(), "Reinicie la app para aplicar el tema", Toast.LENGTH_SHORT).show()
                    
                    Log.d(TAG, "🎨 Tema: $isChecked")
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Error actualizando tema: ${e.message}")
                }
            }

            // Idioma
            var isFirstSelection = true
            languageSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    if (isFirstSelection) {
                        isFirstSelection = false
                        return
                    }
                    
                    val langCode = when (position) {
                        1 -> "en"
                        2 -> "fr"
                        3 -> "it"
                        4 -> "de"
                        5 -> "ru"
                        else -> "es"
                    }
                    
                    lifecycleScope.launch {
                        try {
                            settingsManager.updateLanguage(langCode)
                            Log.d(TAG, "🌍 Idioma: $langCode")
                        } catch (e: Exception) {
                            Log.e(TAG, "❌ Error actualizando idioma: ${e.message}")
                        }
                    }
                }

                override fun onNothingSelected(parent: AdapterView<*>?) {}
            }

            Log.d(TAG, "✅ Listeners configurados")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error configurando listeners: ${e.message}", e)
        }
    }
}