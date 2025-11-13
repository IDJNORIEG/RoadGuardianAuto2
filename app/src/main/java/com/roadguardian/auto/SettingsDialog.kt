package com.roadguardian.auto

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.ArrayAdapter
import android.widget.CompoundButton
import android.widget.SeekBar
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.textview.MaterialTextView
import com.google.android.material.slider.Slider

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class SettingsDialog : DialogFragment() {

    private var _binding: DialogSettingsBinding? = null
    private val binding get() = _binding!!
    private lateinit var settingsManager: SettingsManager

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        _binding = DialogSettingsBinding.inflate(LayoutInflater.from(context))
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_settings, null)
            builder.setView(view)
        
        settingsManager = SettingsManager(requireContext())

        val builder = AlertDialog.Builder(requireContext(), R.style.RoadGuardianDialog)
            .setView(binding.root)
            .setTitle(getString(R.string.settings))
            .setPositiveButton(getString(R.string.close_settings)) { dialog, _ -> dialog.dismiss() }

        initUI()

        return builder.create()
    }

    private fun initUI() {
        lifecycleScope.launch {
            val settings = settingsManager.settingsFlow.first()

            // Sensibilidad
            binding.sensitivitySlider.apply {
                value = settings.sensitivity
                addOnChangeListener { _, newValue, _ ->
                    lifecycleScope.launch {
                        settingsManager.updateSensitivity(newValue)
                        binding.sensitivityValue.text = String.format("%.1f", newValue)
                    }
                }
            }

            // Confianza
            binding.confidenceSlider.apply {
                value = settings.minConfidence
                addOnChangeListener { _, newValue, _ ->
                    lifecycleScope.launch {
                        settingsManager.updateMinConfidence(newValue)
                        binding.confidenceValue.text = String.format("%.1f", newValue)
                    }
                }
            }

            // Sonido
            binding.soundSwitch.isChecked = settings.soundEnabled
            binding.soundSwitch.setOnCheckedChangeListener { _: CompoundButton, isChecked ->
                lifecycleScope.launch { settingsManager.updateSoundEnabled(isChecked) }
            }

            // Idioma
            val languageMap = mapOf(
                "Español" to "es",
                "English" to "en",
                "Italiano" to "it",
                "Français" to "fr",
                "Deutsch" to "de",
                "Русский" to "ru"
            )
            val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, languageMap.keys.toList())
            (binding.languageDropdown as MaterialAutoCompleteTextView).setAdapter(adapter)
            (binding.languageDropdown as MaterialAutoCompleteTextView).setText(
                languageMap.entries.firstOrNull { it.value == settings.language }?.key ?: "Español",
                false
            )
            (binding.languageDropdown as MaterialAutoCompleteTextView).setOnItemClickListener { _, _, position, _ ->
                val lang = languageMap.values.toList()[position]
                lifecycleScope.launch { settingsManager.updateLanguage(lang) }
            }

            // Tema (Claro / Oscuro)
            val themeOptions = mapOf(
                getString(R.string.light_mode) to "light",
                getString(R.string.dark_mode) to "dark"
            )
            val themeAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, themeOptions.keys.toList())
            (binding.themeDropdown as MaterialAutoCompleteTextView).setAdapter(themeAdapter)
            (binding.themeDropdown as MaterialAutoCompleteTextView).setText(
                themeOptions.entries.firstOrNull { it.value == settings.themeMode }?.key ?: getString(R.string.light_mode),
                false
            )
            (binding.themeDropdown as MaterialAutoCompleteTextView).setOnItemClickListener { _, _, position, _ ->
                val selectedMode = themeOptions.values.toList()[position]
                lifecycleScope.launch { settingsManager.updateThemeMode(selectedMode) }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
