package com.roadguardian.auto

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
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

    private var totalDetections = 0
    private var currentLanguage = "es"
    private var detecting = false

    companion object {
        private const val TAG = "MainActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        Log.i(TAG, "🚀 Iniciando MainActivity")
        
        // Aplicar tema
        applyTheme()
        
        setContentView(R.layout.activity_main)

        try {
            initializeViews()
            initializeManagers()
            setupListeners()
            checkCameraPermission()
            
            Log.i(TAG, "✅ MainActivity inicializada correctamente")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error en onCreate: ${e.message}", e)
            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun applyTheme() {
        try {
            val prefs = getSharedPreferences("theme_prefs", MODE_PRIVATE)
            val isDarkMode = prefs.getBoolean("dark_mode", false)
            
            if (isDarkMode) {
                setTheme(R.style.Theme_RoadGuardianAuto_Dark)
                Log.d(TAG, "🌙 Tema oscuro aplicado")
            } else {
                setTheme(R.style.Theme_RoadGuardianAuto)
                Log.d(TAG, "☀️ Tema claro aplicado")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error aplicando tema: ${e.message}")
        }
    }

    private fun initializeViews() {
        previewView = findViewById(R.id.previewView)
        overlayView = findViewById(R.id.detectionOverlay)
        counterText = findViewById(R.id.counterText)
        statusText = findViewById(R.id.statusText)
        startButton = findViewById(R.id.startButton)
        settingsButton = findViewById(R.id.settingsButton)
        
        Log.d(TAG, "✅ Vistas inicializadas")
    }

    private fun initializeManagers() {
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
        
        // Cargar idioma
        lifecycleScope.launch {
            settingsManager.settingsFlow.collect { settings ->
                currentLanguage = settings.language
                Log.d(TAG, "🌍 Idioma: $currentLanguage")
            }
        }
        
        Log.d(TAG, "✅ Managers inicializados")
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
            try {
                val dialog = SettingsDialogFragment()
                dialog.show(supportFragmentManager, "SettingsDialog")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error mostrando diálogo: ${e.message}", e)
                Toast.makeText(this, "Error abriendo configuración: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
        
        Log.d(TAG, "✅ Listeners configurados")
    }

    private fun checkCameraPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED) {
            statusText.text = getString(R.string.status_ready)
            Log.i(TAG, "✅ Permiso de cámara otorgado")
        } else {
            Log.w(TAG, "⚠️ Solicitando permiso de cámara")
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                statusText.text = getString(R.string.status_ready)
                Log.i(TAG, "✅ Permiso concedido")
            } else {
                statusText.text = getString(R.string.camera_permission_required)
                Log.e(TAG, "❌ Permiso denegado")
                Toast.makeText(this, "Se requiere permiso de cámara", Toast.LENGTH_LONG).show()
            }
        }

    private fun startDetection() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Permiso de cámara requerido", Toast.LENGTH_SHORT).show()
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
            
            Toast.makeText(this, "Detección iniciada", Toast.LENGTH_SHORT).show()
            Log.i(TAG, "✅ Detección iniciada")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error iniciando: ${e.message}", e)
            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
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
            
            Toast.makeText(this, "Detección detenida", Toast.LENGTH_SHORT).show()
            Log.i(TAG, "✅ Detección detenida")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error deteniendo: ${e.message}", e)
        }
    }

    private fun updateDetectionUI(detections: List<Detection>) {
        if (detections.isNotEmpty()) {
            totalDetections += detections.size
            counterText.text = totalDetections.toString()
            
            Log.i(TAG, "📊 Total: $totalDetections")
            
            // Vibrar en vez de sonido
            try {
                val vibrator = getSystemService(VIBRATOR_SERVICE) as? android.os.Vibrator
                vibrator?.vibrate(200)
            } catch (e: Exception) {
                Log.w(TAG, "No se pudo vibrar: ${e.message}")
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.w(TAG, "🔴 Destruyendo MainActivity")
        
        try {
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
        Log.d(TAG, "⏸️ Pausada")
        if (detecting) {
            try {
                cameraManager.stopCamera()
            } catch (e: Exception) {
                Log.e(TAG, "Error pausando: ${e.message}")
            }
        }
    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "▶️ Resumida")
        if (detecting) {
            try {
                cameraManager.initializeCamera()
            } catch (e: Exception) {
                Log.e(TAG, "Error resumiendo: ${e.message}")
            }
        }
    }
}