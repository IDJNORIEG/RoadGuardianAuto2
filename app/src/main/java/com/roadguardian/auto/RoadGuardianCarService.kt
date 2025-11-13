package com.roadguardian.auto

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.util.Log
import androidx.car.app.CarAppService
import androidx.car.app.Session
import androidx.car.app.Screen
import androidx.car.app.ScreenManager
import androidx.car.app.CarContext
import androidx.car.app.validation.HostValidator
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import com.roadguardian.auto.models.Detection
import com.roadguardian.auto.models.DetectionLog
import kotlinx.coroutines.*
import java.util.*

/**
 * 🚘 RoadGuardianCarService.kt
 * Servicio principal que conecta RoadGuardian con Android Auto / Automotive OS.
 * Gestiona la comunicación entre la IA de detección, las pantallas y el sistema del vehículo.
 *
 * Funciones clave:
 * - Detección con TensorFlow Lite (YOLOv8)
 * - Pantalla de detección (DetectionScreen)
 * - Pantalla de configuración (SettingsScreen)
 * - Soporte multilingüe
 * - Registro de sesiones y exportación de detecciones
 * - Control total de ciclo de vida (LifecycleOwner)
 *
 * @author RoadGuardian
 */
class RoadGuardianCarService : CarAppService() {

    override fun createHostValidator(): HostValidator {
        // 🔒 Durante el desarrollo permite todos los hosts, en producción usa lista segura.
        return if (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) {
            HostValidator.ALLOW_ALL_HOSTS_VALIDATOR
        } else {
            HostValidator.Builder(applicationContext)
                .addAllowedHosts(androidx.car.app.R.array.hosts_allowlist_sample)
                .build()
        }
    }

    override fun onCreateSession(): Session {
    return object : Session() {
        override fun onCreateScreen(intent: Intent): Screen {
            val screenManager = carContext.getCarService(ScreenManager::class.java)
            val mainScreen = DetectionScreen(carContext)
            return MainScreen(carContext) // o DetectionScreen si es tu pantalla principal
        }
    }
}
}

/**
 * 🎯 RoadGuardianSession
 * Administra las pantallas, configuración y detección dentro de Android Auto.
 */
class RoadGuardianSession : Session(), LifecycleOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val coroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private lateinit var detectionScreen: DetectionScreen
    private lateinit var settingsScreen: SettingsScreen
    private lateinit var detectionManager: DetectionManager
    private val detectionLog = DetectionLog()

    override fun getLifecycle(): Lifecycle = lifecycleRegistry

    override fun onCreateScreen(intent: Intent): Screen {
        lifecycleRegistry.currentState = Lifecycle.State.STARTED
        Log.i("RoadGuardianSession", "🚀 Inicializando pantalla principal de detección")

        detectionManager = DetectionManager(carContext)
        detectionScreen = DetectionScreen(carContext)
        settingsScreen = SettingsScreen(carContext)

        // Simulación de detecciones automáticas para pruebas (sin cámara)
        startSimulatedDetections()

        return detectionScreen
    }

    /**
     * 🧠 Inicia un flujo simulado de detecciones cada 5 segundos
     * (reemplazable por detección real usando la cámara del sistema).
     */
    private fun startSimulatedDetections() {
        coroutineScope.launch {
            val random = Random()
            while (isActive) {
                delay(5000L)
                val randomAnimal = com.roadguardian.auto.models.AnimalType.values().random()
                val fakeDetection = Detection(
                    animal = randomAnimal,
                    confidence = 0.6f + random.nextFloat() * 0.4f,
                    distance = 20 + random.nextInt(150)
                )
                detectionLog.addDetection(fakeDetection)

                withContext(Dispatchers.Main) {
                    detectionScreen.updateDetection(fakeDetection)
                    Log.d(
                        "RoadGuardianSession",
                        "🐾 Detección simulada: ${fakeDetection.animal.displayName} (${fakeDetection.distance}m)"
                    )
                }
            }
        }
    }

    /**
     * ⚙️ Abre el panel de configuración dentro de Android Auto.
     */
    fun openSettings() {
        screenManager.push(settingsScreen)
    }

    /**
     * 🗂️ Exporta automáticamente el registro de detecciones al cerrar sesión.
     */
    private fun exportSessionLog() {
        detectionLog.endTime = System.currentTimeMillis()
        val jsonFile = detectionLog.exportAsJson(carContext)
        val csvFile = detectionLog.exportAsCsv(carContext)

        if (jsonFile != null || csvFile != null) {
            Log.i("RoadGuardianSession", "📁 Sesión exportada correctamente a almacenamiento local")
        } else {
            Log.w("RoadGuardianSession", "⚠️ No se pudo exportar el registro de detecciones")
        }
    }

    override fun onCarConfigurationChanged() {
        super.onCarConfigurationChanged()
        Log.d("RoadGuardianSession", "⚙️ Configuración del vehículo cambiada (idioma, tema, etc.)")
    }

    override fun onDestroy() {
        super.onDestroy()
        coroutineScope.cancel()
        detectionManager.release()
        exportSessionLog()
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        Log.w("RoadGuardianSession", "🔴 Sesión finalizada y recursos liberados correctamente")
    }
}
