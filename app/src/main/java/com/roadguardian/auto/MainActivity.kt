package com.roadguardian.auto

import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.roadguardian.auto.models.Detection
import com.roadguardian.auto.ui.DetectionOverlayView
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var previewView: PreviewView
    private lateinit var overlayView: DetectionOverlayView
    private lateinit var counterText: TextView
    private lateinit var statusText: TextView
    private lateinit var startButton: Button
    private lateinit var settingsButton: Button

    private lateinit var cameraManager: CameraManager
    private lateinit var detectionManager: DetectionManager
    private lateinit var settingsManager: SettingsManager

    private var mediaPlayer: MediaPlayer? = null
    private var totalDetections = 0
    private var currentLanguage = "es"
    private var detecting = false

    companion object {
        private const val TAG = "MainActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        Log.i(TAG, "🚀 Iniciando MainActivity")
        
        // Aplicar tema según preferencias
        val isDarkMode = getSharedPreferences("theme_prefs", MODE_PRIVATE)
            .getBoolean("dark_mode", false)
        applyTheme(isDarkMode)
        
        setContentView(R.layout.activity_main)

        initializeViews()
        initializeManagers()
        setupListeners()
        
        try {
            mediaPlayer = MediaPlayer.create(this, R.raw.alert_sound)
            Log.d(TAG, "🔊 MediaPlayer inicializado")
        } catch (e: Exception) {
            Log.e(TAG, "⚠️ No se pudo cargar sonido de alerta: ${e.message}")
        }

        // Cargar idioma desde DataStore
        lifecycleScope.launch {
            settingsManager.settingsFlow.collect { settings ->
                currentLanguage = settings.language
                Log.d(TAG, "🌍 Idioma actualizado: $currentLanguage")
            }
        }

        // Verificar permisos de cámara
        checkCameraPermission()
    }

    private fun initializeViews() {
        try {
            previewView = findViewById(R.id.previewView)
            overlayView = findViewById(R.id.detectionOverlay)
            counterText = findViewById(R.id.counterText)
            statusText = findViewById(R.id.statusText)
            startButton = findViewById(R.id.startButton)
            settingsButton = findViewById(R.id.settingsButton)
            
            Log.d(TAG, "✅ Vistas inicializadas")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error inicializando vistas: ${e.message}", e)
        }
    }

    private fun initializeManagers() {
        try {
            detectionManager = DetectionManager(this)
            settingsManager = SettingsManager(this)
            
            cameraManager = CameraManager(
                context = this,
                lifecycleOwner = this,
                previewView = previewView,
                overlayView = overlayView,
                detectionManager = detectionManager,
                currentLanguageProvider = { currentLanguage },
                onDetectionsUpdate = { detections ->
                    runOnUiThread {
                        updateDetectionUI(detections)
                    }
                }
            )
            
            Log.d(TAG, "✅ Managers inicializados")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error inicializando managers: ${e.message}", e)
        }
    }

    private fun setupListeners() {
        startButton.setOnClickListener {
            Log.d(TAG, "🔘 Botón presionado: detecting=$detecting")
            if (!detecting) {
                startDetection()
            } else {
                stopDetection()
            }
        }

        settingsButton.setOnClickListener {
            Log.d(TAG, "⚙️ Abriendo configuración")
            showSettingsDialog()
        }
    }

    private fun checkCameraPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED) {
            statusText.text = getString(R.string.status_ready)
            Log.i(TAG, "✅ Permiso de cámara ya otorgado")
        } else {
            Log.w(TAG, "⚠️ Solicitando permiso de cámara")
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                statusText.text = getString(R.string.status_ready)
                Log.i(TAG, "✅ Permiso de cámara otorgado")
            } else {
                statusText.text = getString(R.string.camera_permission_required)
                Log.e(TAG, "❌ Permiso de cámara denegado")
            }
        }

    private fun startDetection() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED) {
            statusText.text = getString(R.string.camera_permission_required)
            Log.e(TAG, "❌ No hay permiso de cámara")
            return
        }
        
        Log.i(TAG, "▶️ Iniciando detección")
        
        try {
            cameraManager.initializeCamera()
            detecting = true
            statusText.text = getString(R.string.status_detecting)
            startButton.text = getString(R.string.stop_detection)
            totalDetections = 0
            counterText.text = "0"
            
            Log.i(TAG, "✅ Detección iniciada correctamente")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error iniciando detección: ${e.message}", e)
            statusText.text = "Error: ${e.message}"
        }
    }

    private fun stopDetection() {
        Log.i(TAG, "⏹️ Deteniendo detección")
        
        try {
            cameraManager.stopCamera()
            detecting = false
            statusText.text = getString(R.string.status_ready)
            startButton.text = getString(R.string.start_detection)
            overlayView.clearDetections()
            totalDetections = 0
            counterText.text = "0"
            
            Log.i(TAG, "✅ Detección detenida correctamente")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error deteniendo detección: ${e.message}", e)
        }
    }

    private fun updateDetectionUI(detections: List<Detection>) {
        if (detections.isNotEmpty()) {
            totalDetections += detections.size
            counterText.text = totalDetections.toString()
            
            Log.i(TAG, "📊 Total detecciones: $totalDetections")
            
            playAlert()
        }
    }

    private fun showSettingsDialog() {
        try {
            val dialog = SettingsDialogFragment()
            dialog.show(supportFragmentManager, "SettingsDialog")
            Log.d(TAG, "✅ Diálogo de configuración mostrado")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error mostrando diálogo: ${e.message}", e)
        }
    }

    private fun playAlert() {
        lifecycleScope.launch {
            try {
                settingsManager.settingsFlow.collect { settings ->
                    if (settings.soundEnabled && mediaPlayer?.isPlaying == false) {
                        mediaPlayer?.start()
                        Log.d(TAG, "🔊 Reproduciendo alerta sonora")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error reproduciendo alerta: ${e.message}")
            }
        }
    }

    fun applyTheme(darkMode: Boolean) {
        if (darkMode) {
            setTheme(R.style.Theme_RoadGuardianAuto_Dark)
            Log.d(TAG, "🌙 Tema oscuro aplicado")
        } else {
            setTheme(R.style.Theme_RoadGuardianAuto)
            Log.d(TAG, "☀️ Tema claro aplicado")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.w(TAG, "🔴 Destruyendo MainActivity")
        
        try {
            mediaPlayer?.release()
            detectionManager.release()
            if (detecting) {
                cameraManager.stopCamera()
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error en onDestroy: ${e.message}")
        }
    }

    override fun onPause() {
        super.onPause()
        Log.d(TAG, "⏸️ MainActivity pausada")
        if (detecting) {
            cameraManager.stopCamera()
        }
    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "▶️ MainActivity resumida")
        if (detecting) {
            cameraManager.initializeCamera()
        }
    }
}