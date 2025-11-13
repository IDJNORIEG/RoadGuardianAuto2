package com.roadguardian.auto

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.*
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch

class SettingsDialogFragment : DialogFragment() {

    private lateinit var settingsManager: SettingsManager
    private lateinit var sensitivitySeek: SeekBar
    private lateinit var confidenceSeek: SeekBar
    private lateinit var soundSwitch: Switch
    private lateinit var themeSwitch: Switch
    private lateinit var languageSpinner: Spinner
    private lateinit var sensitivityLabel: TextView
    private lateinit var confidenceLabel: TextView

    override fun onAttach(context: Context) {
        super.onAttach(context)
        settingsManager = SettingsManager(context)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val view = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_settings, null)

        sensitivitySeek = view.findViewById(R.id.sensitivitySeek)
        confidenceSeek = view.findViewById(R.id.confidenceSeek)
        soundSwitch = view.findViewById(R.id.soundSwitch)
        themeSwitch = view.findViewById(R.id.themeSwitch)
        languageSpinner = view.findViewById(R.id.languageSpinner)
        sensitivityLabel = view.findViewById(R.id.sensitivityValue)
        confidenceLabel = view.findViewById(R.id.confidenceValue)

        val languages = listOf("EspaÃ±ol", "English", "FranÃ§ais", "Italiano", "Deutsch", "Ð ÑƒÑÑÐºÐ¸Ð¹")
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, languages)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        languageSpinner.adapter = adapter

        lifecycleScope.launch {
            settingsManager.settingsFlow.collect { settings ->
                sensitivitySeek.progress = (settings.sensitivity * 100).toInt()
                confidenceSeek.progress = (settings.minConfidence * 100).toInt()
                soundSwitch.isChecked = settings.soundEnabled
                themeSwitch.isChecked = requireContext().isDarkThemeEnabled()

                val index = when (settings.language) {
                    "en" -> 1; "fr" -> 2; "it" -> 3; "de" -> 4; "ru" -> 5; else -> 0
                }
                languageSpinner.setSelection(index)

                sensitivityLabel.text = "${(settings.sensitivity * 100).toInt()}%"
                confidenceLabel.text = "${(settings.minConfidence * 100).toInt()}%"
            }
        }

        // Listeners
        sensitivitySeek.setOnSeekBarChangeListener(simpleSeekBarChange { value ->
            lifecycleScope.launch { settingsManager.updateSensitivity(value) }
            sensitivityLabel.text = "${(value * 100).toInt()}%"
        })

        confidenceSeek.setOnSeekBarChangeListener(simpleSeekBarChange { value ->
            lifecycleScope.launch { settingsManager.updateMinConfidence(value) }
            confidenceLabel.text = "${(value * 100).toInt()}%"
        })

        soundSwitch.setOnCheckedChangeListener { _, isChecked ->
            lifecycleScope.launch { settingsManager.updateSoundEnabled(isChecked) }
        }

        themeSwitch.setOnCheckedChangeListener { _, isChecked ->
            val prefs = requireContext().getSharedPreferences("theme_prefs", Context.MODE_PRIVATE)
            prefs.edit().putBoolean("dark_mode", isChecked).apply()
            (requireActivity() as MainActivity).applyTheme(isChecked)
        }

        languageSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>, view: android.view.View?, pos: Int, id: Long
            ) {
                val langCode = when (pos) {
                    1 -> "en"; 2 -> "fr"; 3 -> "it"; 4 -> "de"; 5 -> "ru"; else -> "es"
                }
                lifecycleScope.launch { settingsManager.updateLanguage(langCode) }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        return MaterialAlertDialogBuilder(
            requireContext(),
            com.google.android.material.R.style.ThemeOverlay_MaterialComponents_MaterialAlertDialog
            )

            .setTitle(getString(R.string.settings))
            .setView(view)
            .setPositiveButton(android.R.string.ok, null)
            .create()
    }

    private fun simpleSeekBarChange(onChange: (Float) -> Unit) =
        object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) onChange(progress / 100f)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        }
}

// ðŸ”§ Modo oscuro
fun Context.isDarkThemeEnabled(): Boolean {
    val prefs = getSharedPreferences("theme_prefs", Context.MODE_PRIVATE)
    return prefs.getBoolean("dark_mode", false)
}

