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
        
        // Mapeo de idiomas
        private val LANGUAGE_MAP = mapOf(
            "Español" to "es",
            "English" to "en",
            "Français" to "fr",
            "Italiano" to "it",
            "Deutsch" to "de",
            "Русский" to "ru"
        )
        
        private val LANGUAGE_CODES = LANGUAGE_MAP.values.toList()
        private val LANGUAGE_NAMES = LANGUAGE_MAP.keys.toList()
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
            // Usar nombres de idiomas en su idioma nativo
            val adapter = ArrayAdapter(
                requireContext(),
                android.R.layout.simple_spinner_item,
                LANGUAGE_NAMES
            )
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            spinner.adapter = adapter
            
            Log.d(TAG, "✅ Spinner de idiomas configurado con ${LANGUAGE_NAMES.size} opciones")
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

                // Establecer idioma actual en el spinner
                val currentLangCode = settings.language
                val langIndex = LANGUAGE_CODES.indexOf(currentLangCode)
                if (langIndex >= 0) {
                    languageSpinner.setSelection(langIndex)
                    Log.d(TAG, "✅ Idioma actual: ${LANGUAGE_NAMES[langIndex]} ($currentLangCode)")
                }

                Log.d(TAG, "✅ Configuración cargada")
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
                        Toast.makeText(
                            requireContext(),
                            if (isChecked) "Sonido activado" else "Sonido desactivado",
                            Toast.LENGTH_SHORT
                        ).show()
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
                    
                    Toast.makeText(
                        requireContext(),
                        "Reinicie la app para aplicar el tema",
                        Toast.LENGTH_SHORT
                    ).show()
                    
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
                    
                    if (position >= 0 && position < LANGUAGE_CODES.size) {
                        val langCode = LANGUAGE_CODES[position]
                        val langName = LANGUAGE_NAMES[position]
                        
                        lifecycleScope.launch {
                            try {
                                settingsManager.updateLanguage(langCode)
                                Log.d(TAG, "🌍 Idioma cambiado: $langName ($langCode)")
                                
                                Toast.makeText(
                                    requireContext(),
                                    "Idioma: $langName",
                                    Toast.LENGTH_SHORT
                                ).show()
                            } catch (e: Exception) {
                                Log.e(TAG, "❌ Error actualizando idioma: ${e.message}")
                            }
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