package com.roadguardian.auto

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

// Extensión global para acceder al DataStore
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "roadguardian_settings")

/**
 * SettingsManager
 * Clase encargada de guardar y recuperar las configuraciones del usuario.
 * Incluye sensibilidad, confianza, sonido, idioma y modo de tema.
 */
class SettingsManager(private val context: Context) {

    private object PreferencesKeys {
        val SENSITIVITY = floatPreferencesKey("sensitivity")
        val MIN_CONFIDENCE = floatPreferencesKey("min_confidence")
        val SOUND_ENABLED = booleanPreferencesKey("sound_enabled")
        val LANGUAGE = stringPreferencesKey("language")
        val THEME_MODE = stringPreferencesKey("theme_mode") // "light" | "dark" | "system"
    }

    /**
     * Flujo que emite los ajustes actuales en tiempo real
     */
    val settingsFlow: Flow<Settings> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences())
            else throw exception
        }
        .map { preferences ->
            Settings(
                sensitivity = preferences[PreferencesKeys.SENSITIVITY] ?: 0.5f,
                minConfidence = preferences[PreferencesKeys.MIN_CONFIDENCE] ?: 0.5f,
                soundEnabled = preferences[PreferencesKeys.SOUND_ENABLED] ?: true,
                language = preferences[PreferencesKeys.LANGUAGE] ?: "es",
                themeMode = preferences[PreferencesKeys.THEME_MODE] ?: "light"
            )
        }

    // === Actualizadores individuales ===
    suspend fun updateSensitivity(sensitivity: Float) {
        context.dataStore.edit { it[PreferencesKeys.SENSITIVITY] = sensitivity }
    }

    suspend fun updateMinConfidence(confidence: Float) {
        context.dataStore.edit { it[PreferencesKeys.MIN_CONFIDENCE] = confidence }
    }

    suspend fun updateSoundEnabled(enabled: Boolean) {
        context.dataStore.edit { it[PreferencesKeys.SOUND_ENABLED] = enabled }
    }

    suspend fun updateLanguage(language: String) {
        context.dataStore.edit { it[PreferencesKeys.LANGUAGE] = language }
    }

    suspend fun updateThemeMode(mode: String) {
        // Valores esperados: "light", "dark", "system"
        context.dataStore.edit { it[PreferencesKeys.THEME_MODE] = mode }
    }
}

/**
 * Data class que representa la configuración completa de la app.
 */
data class Settings(
    val sensitivity: Float = 0.5f,
    val minConfidence: Float = 0.5f,
    val soundEnabled: Boolean = true,
    val language: String = "es",
    val themeMode: String = "light" // "light", "dark" o "system"
)
