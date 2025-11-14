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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Aplicar tema según preferencias
        val isDarkMode = getSharedPreferences("theme_prefs", MODE_PRIVATE)
            .getBoolean("dark_mode", false)
        applyTheme(isDarkMode)
        
        setContentView(R.layout.activity_main)

        initializeViews()
        initializeManagers()
        setupListeners()
        
        mediaPlayer = MediaPlayer.create(this, R.raw.alert_sound)

        // Cargar idioma desde DataStore
        lifecycleScope.launch {
            settingsManager.settingsFlow.collect { settings ->
                currentLanguage = settings.language
            }
        }

        // Verificar permisos de cámara
        checkCameraPermission()
    }

    private fun initializeViews() {
        previewView = findViewById(R.id.previewView)
        overlayView = findViewById(R.id.detectionOverlay)
        counterText = findViewById(R.id.counterText)
        statusText = findViewById(R.id.statusText)
        startButton = findViewById(R.id.startButton)
        settingsButton = findViewById(R.id.settingsButton)
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
                    if (detections.isNotEmpty()) {
                        totalDetections += detections.size
                        counterText.text = totalDetections.toString()
                        playAlert()
                    }
                }
            }
        )
    }

    private fun setupListeners() {
        startButton.setOnClickListener {
            if (!detecting) {
                startDetection()
            } else {
                stopDetection()
            }
        }

        settingsButton.setOnClickListener {
            showSettingsDialog()
        }
    }

    private fun checkCameraPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED) {
            // Permiso ya otorgado, no iniciar automáticamente
            statusText.text = getString(R.string.status_ready)
        } else {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                statusText.text = getString(R.string.status_ready)
            } else {
                statusText.text = getString(R.string.camera_permission_required)
            }
        }

    private fun startDetection() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED) {
            statusText.text = getString(R.string.camera_permission_required)
            return
        }
        
        cameraManager.initializeCamera()
        detecting = true
        statusText.text = getString(R.string.status_detecting)
        startButton.text = getString(R.string.stop_detection)
    }

    private fun stopDetection() {
        cameraManager.stopCamera()
        detecting = false
        statusText.text = getString(R.string.status_ready)
        startButton.text = getString(R.string.start_detection)
        overlayView.clearDetections()
        totalDetections = 0
        counterText.text = "0"
    }

    private fun showSettingsDialog() {
        val dialog = SettingsDialogFragment()
        dialog.show(supportFragmentManager, "SettingsDialog")
    }

    private fun playAlert() {
        lifecycleScope.launch {
            settingsManager.settingsFlow.collect { settings ->
                if (settings.soundEnabled && mediaPlayer?.isPlaying == false) {
                    mediaPlayer?.start()
                }
            }
        }
    }

    fun applyTheme(darkMode: Boolean) {
        if (darkMode) {
            setTheme(R.style.Theme_RoadGuardianAuto_Dark)
        } else {
            setTheme(R.style.Theme_RoadGuardianAuto)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaPlayer?.release()
        detectionManager.release()
        if (detecting) {
            cameraManager.stopCamera()
        }
    }

    override fun onPause() {
        super.onPause()
        if (detecting) {
            cameraManager.stopCamera()
        }
    }

    override fun onResume() {
        super.onResume()
        if (detecting) {
            cameraManager.initializeCamera()
        }
    }
}