package com.roadguardian.auto

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.util.Log
import androidx.car.app.CarAppService
import androidx.car.app.Session
import androidx.car.app.Screen
import androidx.car.app.validation.HostValidator
import com.roadguardian.auto.models.Detection
import com.roadguardian.auto.models.DetectionLog
import kotlinx.coroutines.*
import java.util.*

/**
 * Servicio principal que conecta RoadGuardian con Android Auto / Automotive OS
 */
class RoadGuardianCarService : CarAppService() {

    override fun createHostValidator(): HostValidator {
        return if (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) {
            HostValidator.ALLOW_ALL_HOSTS_VALIDATOR
        } else {
            HostValidator.Builder(applicationContext)
                .addAllowedHosts(androidx.car.app.R.array.hosts_allowlist_sample)
                .build()
        }
    }

    override fun onCreateSession(): Session {
        return RoadGuardianSession()
    }
}

/**
 * Sesión principal de Android Auto
 */
class RoadGuardianSession : Session() {

    private val coroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private lateinit var detectionScreen: DetectionScreen
    private lateinit var detectionManager: DetectionManager
    private val detectionLog = DetectionLog()

    override fun onCreateScreen(intent: Intent): Screen {
        Log.i("RoadGuardianSession", "🚀 Inicializando pantalla principal")

        detectionManager = DetectionManager(carContext)
        detectionScreen = DetectionScreen(carContext)

        // Iniciar simulación de detecciones para pruebas
        startSimulatedDetections()

        return detectionScreen
    }

    private fun startSimulatedDetections() {
        coroutineScope.launch {
            val random = Random()
            while (isActive) {
                delay(8000L) // Cada 8 segundos
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
                        "🐾 Detección: ${fakeDetection.animal.getLocalizedName()} (${fakeDetection.distance}m)"
                    )
                }
            }
        }
    }

    private fun exportSessionLog() {
        detectionLog.endTime = System.currentTimeMillis()
        val jsonFile = detectionLog.exportAsJson(carContext)
        val csvFile = detectionLog.exportAsCsv(carContext)

        if (jsonFile != null || csvFile != null) {
            Log.i("RoadGuardianSession", "📁 Sesión exportada correctamente")
        } else {
            Log.w("RoadGuardianSession", "⚠️ No se pudo exportar el registro")
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        Log.d("RoadGuardianSession", "📱 Nuevo intent recibido")
    }

    override fun onCarConfigurationChanged(newConfiguration: android.content.res.Configuration) {
        super.onCarConfigurationChanged(newConfiguration)
        Log.d("RoadGuardianSession", "⚙️ Configuración del vehículo cambiada")
    }

    override fun onDestroy(owner: androidx.lifecycle.LifecycleOwner) {
        super.onDestroy(owner)
        coroutineScope.cancel()
        detectionManager.release()
        exportSessionLog()
        Log.w("RoadGuardianSession", "🔴 Sesión finalizada")
    }
}