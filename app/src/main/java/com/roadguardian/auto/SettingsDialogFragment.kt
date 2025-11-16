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
    private var currentLanguage = "es"
    
    // Referencias a labels
    private var sensitivityLabelView: TextView? = null
    private var confidenceLabelView: TextView? = null
    private var soundLabelView: TextView? = null
    private var themeLabelView: TextView? = null
    private var languageLabelView: TextView? = null
    
    companion object {
        private const val TAG = "SettingsDialog"
        
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
            lifecycleScope.launch {
                currentLanguage = settingsManager.settingsFlow.first().language
            }
            Log.d(TAG, "✅ SettingsManager creado")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error: ${e.message}", e)
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        Log.d(TAG, "📱 Creando diálogo...")
        
        return try {
            val inflater = LayoutInflater.from(requireContext())
            val view = inflater.inflate(R.layout.dialog_settings, null)
            
            setupViews(view)
            
            AlertDialog.Builder(requireContext())
                .setTitle(TranslationsManager.getSettings(currentLanguage))
                .setView(view)
                .setPositiveButton(TranslationsManager.getAccept(currentLanguage)) { dialog, _ ->
                    Log.d(TAG, "✅ Configuración guardada")
                    dialog.dismiss()
                }
                .setNegativeButton(TranslationsManager.getCancel(currentLanguage)) { dialog, _ ->
                    Log.d(TAG, "❌ Configuración cancelada")
                    dialog.dismiss()
                }
                .create()
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error: ${e.message}", e)
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
            
            // Referencias a los labels traducibles
            sensitivityLabelView = view.findViewById(R.id.sensitivityLabel)
            confidenceLabelView = view.findViewById(R.id.confidenceLabel)
            soundLabelView = view.findViewById(R.id.soundLabel)
            themeLabelView = view.findViewById(R.id.themeLabel)
            languageLabelView = view.findViewById(R.id.languageLabel)

            Log.d(TAG, "✅ Vistas encontradas")

            setupLanguageSpinner(languageSpinner)
            updateLabels() // Actualizar labels con idioma actual
            
            loadSettings(
                sensitivitySeek, confidenceSeek, soundSwitch, 
                themeSwitch, languageSpinner, sensitivityLabel, confidenceLabel
            )
            
            setupListeners(
                sensitivitySeek, confidenceSeek, soundSwitch,
                themeSwitch, languageSpinner, sensitivityLabel, confidenceLabel
            )

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error: ${e.message}", e)
        }
    }

    private fun updateLabels() {
        try {
            sensitivityLabelView?.text = TranslationsManager.getSensitivity(currentLanguage)
            confidenceLabelView?.text = TranslationsManager.getConfidence(currentLanguage)
            soundLabelView?.text = TranslationsManager.getSoundAlert(currentLanguage)
            themeLabelView?.text = TranslationsManager.getDarkMode(currentLanguage)
            languageLabelView?.text = TranslationsManager.getLanguage(currentLanguage)
            
            Log.d(TAG, "✅ Labels actualizados a: $currentLanguage")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error actualizando labels: ${e.message}")
        }
    }

    private fun setupLanguageSpinner(spinner: Spinner) {
        try {
            val adapter = ArrayAdapter(
                requireContext(),
                android.R.layout.simple_spinner_item,
                LANGUAGE_NAMES
            )
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            spinner.adapter = adapter
            
            Log.d(TAG, "✅ Spinner configurado")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error: ${e.message}", e)
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

                val currentLangCode = settings.language
                currentLanguage = currentLangCode
                val langIndex = LANGUAGE_CODES.indexOf(currentLangCode)
                if (langIndex >= 0) {
                    languageSpinner.setSelection(langIndex)
                }

                Log.d(TAG, "✅ Configuración cargada")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error: ${e.message}", e)
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
                            } catch (e: Exception) {
                                Log.e(TAG, "❌ Error: ${e.message}")
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
                            } catch (e: Exception) {
                                Log.e(TAG, "❌ Error: ${e.message}")
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
                        val message = if (isChecked) {
                            TranslationsManager.getSoundEnabled(currentLanguage)
                        } else {
                            TranslationsManager.getSoundDisabled(currentLanguage)
                        }
                        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        Log.e(TAG, "❌ Error: ${e.message}")
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
                        TranslationsManager.getRestartToApplyTheme(currentLanguage),
                        Toast.LENGTH_SHORT
                    ).show()
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Error: ${e.message}")
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
                        currentLanguage = langCode
                        
                        lifecycleScope.launch {
                            try {
                                settingsManager.updateLanguage(langCode)
                                Toast.makeText(requireContext(), langName, Toast.LENGTH_SHORT).show()
                                
                                // Actualizar labels del diálogo
                                updateLabels()
                                
                                // Actualizar título y botones
                                (dialog as? AlertDialog)?.let { alertDialog ->
                                    alertDialog.setTitle(TranslationsManager.getSettings(currentLanguage))
                                    alertDialog.getButton(AlertDialog.BUTTON_POSITIVE)?.text = 
                                        TranslationsManager.getAccept(currentLanguage)
                                    alertDialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.text = 
                                        TranslationsManager.getCancel(currentLanguage)
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "❌ Error: ${e.message}")
                            }
                        }
                    }
                }

                override fun onNothingSelected(parent: AdapterView<*>?) {}
            }

            Log.d(TAG, "✅ Listeners configurados")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error: ${e.message}", e)
        }
    }
}