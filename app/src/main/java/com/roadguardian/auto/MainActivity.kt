package com.roadguardian.auto

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.RectF
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.roadguardian.auto.models.AnimalType
import com.roadguardian.auto.models.Detection
import com.roadguardian.auto.ui.DetectionOverlayView
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var previewView: PreviewView
    private lateinit var overlayView: DetectionOverlayView
    private lateinit var counterText: TextView
    private lateinit var statusText: TextView
    private lateinit var startButton: Button
    private lateinit var settingsButton: Button
    private lateinit var languageSpinner: Spinner

    private lateinit var cameraManager: CameraManager
    private lateinit var detectionManager: DetectionManager
    private lateinit var settingsManager: SettingsManager

    private var soundPool: SoundPool? = null
    private var alertSoundId: Int = 0
    private var lastAlertTime: Long = 0
    private val ALERT_COOLDOWN = 2000L

    private var totalDetections = 0
    private var currentLanguage = "es"
    private var detecting = false
    private var soundEnabled = true

    companion object {
        private const val TAG = "MainActivity"
        
        private val LANGUAGE_MAP = mapOf(
            "🇪🇸 ES" to "es",
            "🇬🇧 EN" to "en",
            "🇫🇷 FR" to "fr",
            "🇮🇹 IT" to "it",
            "🇩🇪 DE" to "de",
            "🇷🇺 RU" to "ru"
        )
        
        private val LANGUAGE_NAMES = LANGUAGE_MAP.keys.toList()
        private val LANGUAGE_CODES = LANGUAGE_MAP.values.toList()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        Log.i(TAG, "🚀 Iniciando MainActivity")
        
        // Mantener pantalla encendida
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        Log.d(TAG, "💡 Pantalla configurada para permanecer encendida")
        
        applyTheme()
        setContentView(R.layout.activity_main)

        try {
            initializeViews()
            initializeManagers()
            initializeSoundPool()
            setupListeners()
            setupLanguageSpinner()
            checkCameraPermission()
            testOverlay()
            
            Log.i(TAG, "✅ MainActivity inicializada")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error en onCreate: ${e.message}", e)
            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun initializeSoundPool() {
        try {
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()

            soundPool = SoundPool.Builder()
                .setMaxStreams(1)
                .setAudioAttributes(audioAttributes)
                .build()

            try {
                alertSoundId = soundPool?.load(this, R.raw.alert_sound, 1) ?: 0
                Log.d(TAG, "🔊 Sonido cargado")
            } catch (e: Exception) {
                Log.w(TAG, "⚠️ No se pudo cargar alert_sound.mp3")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error inicializando SoundPool: ${e.message}")
        }
    }

    private fun testOverlay() {
        lifecycleScope.launch {
            delay(2000)
            val testDetection = Detection(
                animal = AnimalType.DOG,
                confidence = 0.85f,
                distance = 45,
                boundingBox = RectF(100f, 100f, 500f, 500f)
            )
            overlayView.updateDetections(listOf(testDetection), currentLanguage)
            delay(3000)
            overlayView.clearDetections()
        }
    }

    private fun applyTheme() {
        try {
            val prefs = getSharedPreferences("theme_prefs", MODE_PRIVATE)
            val isDarkMode = prefs.getBoolean("dark_mode", false)
            
            if (isDarkMode) {
                setTheme(R.style.Theme_RoadGuardianAuto_Dark)
            } else {
                setTheme(R.style.Theme_RoadGuardianAuto)
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
        languageSpinner = findViewById(R.id.languageSpinner)
        
        overlayView.bringToFront()
        
        Log.d(TAG, "✅ Vistas inicializadas")
    }

    private fun setupLanguageSpinner() {
        try {
            val adapter = ArrayAdapter(
                this,
                android.R.layout.simple_spinner_item,
                LANGUAGE_NAMES
            )
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            languageSpinner.adapter = adapter
            
            lifecycleScope.launch {
                val settings = settingsManager.settingsFlow.first()
                currentLanguage = settings.language
                val index = LANGUAGE_CODES.indexOf(currentLanguage)
                if (index >= 0) {
                    languageSpinner.setSelection(index)
                }
                updateUILanguage()
            }
            
            var isFirstSelection = true
            languageSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    if (isFirstSelection) {
                        isFirstSelection = false
                        return
                    }
                    
                    if (position >= 0 && position < LANGUAGE_CODES.size) {
                        currentLanguage = LANGUAGE_CODES[position]
                        
                        lifecycleScope.launch {
                            settingsManager.updateLanguage(currentLanguage)
                        }
                        
                        updateUILanguage()
                        Log.d(TAG, "🌍 Idioma: $currentLanguage")
                    }
                }

                override fun onNothingSelected(parent: AdapterView<*>?) {}
            }
            
            Log.d(TAG, "✅ Selector de idioma configurado")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error configurando selector: ${e.message}")
        }
    }

    private fun updateUILanguage() {
        try {
            if (detecting) {
                startButton.text = TranslationsManager.getStopDetection(currentLanguage)
                statusText.text = TranslationsManager.getStatusDetecting(currentLanguage)
            } else {
                startButton.text = TranslationsManager.getStartDetection(currentLanguage)
                statusText.text = TranslationsManager.getStatusReady(currentLanguage)
            }
            
            Log.d(TAG, "✅ UI actualizada al idioma: $currentLanguage")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error actualizando idioma UI: ${e.message}")
        }
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
        
        lifecycleScope.launch {
            settingsManager.settingsFlow.collect { settings ->
                soundEnabled = settings.soundEnabled
            }
        }
        
        Log.d(TAG, "✅ Managers inicializados")
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
            try {
                val dialog = SettingsDialogFragment()
                dialog.show(supportFragmentManager, "SettingsDialog")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error mostrando diálogo: ${e.message}", e)
                Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun checkCameraPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED) {
            statusText.text = TranslationsManager.getStatusReady(currentLanguage)
            Log.i(TAG, "✅ Permiso de cámara otorgado")
        } else {
            Log.w(TAG, "⚠️ Solicitando permiso")
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                statusText.text = TranslationsManager.getStatusReady(currentLanguage)
                Toast.makeText(this, "Permiso otorgado", Toast.LENGTH_SHORT).show()
            } else {
                statusText.text = TranslationsManager.getCameraPermissionRequired(currentLanguage)
                Toast.makeText(this, TranslationsManager.getCameraPermissionRequired(currentLanguage), Toast.LENGTH_LONG).show()
            }
        }

    private fun startDetection() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, TranslationsManager.getCameraPermissionRequired(currentLanguage), Toast.LENGTH_SHORT).show()
            return
        }
        
        Log.i(TAG, "▶️ Iniciando detección")
        
        try {
            // Limpiar overlay antes de iniciar
            overlayView.clearDetections()
            
            cameraManager.initializeCamera()
            detecting = true
            statusText.text = TranslationsManager.getStatusDetecting(currentLanguage)
            startButton.text = TranslationsManager.getStopDetection(currentLanguage)
            totalDetections = 0
            counterText.text = "0"
            
            Toast.makeText(this, TranslationsManager.getDetectionStarted(currentLanguage), Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error: ${e.message}", e)
            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun stopDetection() {
        Log.i(TAG, "⏹️ Deteniendo")
        
        try {
            // Detener cámara primero
            cameraManager.stopCamera()
            
            // Dar tiempo para que se detenga completamente
            lifecycleScope.launch {
                delay(200)
                
                // Limpiar completamente la UI
                runOnUiThread {
                    detecting = false
                    statusText.text = TranslationsManager.getStatusReady(currentLanguage)
                    startButton.text = TranslationsManager.getStartDetection(currentLanguage)
                    overlayView.clearDetections()
                    totalDetections = 0
                    counterText.text = "0"
                    
                    // Forzar redibujo del preview para limpiar imagen residual
                    previewView.invalidate()
                }
                
                Toast.makeText(this@MainActivity, TranslationsManager.getDetectionStopped(currentLanguage), Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error: ${e.message}", e)
        }
    }

    private fun updateDetectionUI(detections: List<Detection>) {
        if (detections.isNotEmpty()) {
            totalDetections += detections.size
            counterText.text = totalDetections.toString()
            
            Log.i(TAG, "📊 Total: $totalDetections")
            
            if (soundEnabled) {
                playAlert()
            }
            
            try {
                val vibrator = getSystemService(VIBRATOR_SERVICE) as? android.os.Vibrator
                vibrator?.vibrate(200)
            } catch (e: Exception) {
                Log.w(TAG, "No se pudo vibrar: ${e.message}")
            }
        }
    }

    private fun playAlert() {
        val currentTime = System.currentTimeMillis()
        
        if (currentTime - lastAlertTime < ALERT_COOLDOWN) {
            return
        }
        
        lastAlertTime = currentTime
        
        try {
            if (soundPool != null && alertSoundId > 0) {
                soundPool?.play(alertSoundId, 1.0f, 1.0f, 1, 0, 1.0f)
                Log.d(TAG, "🔊 Alerta reproducida")
            } else {
                val toneGen = android.media.ToneGenerator(
                    android.media.AudioManager.STREAM_ALARM,
                    100
                )
                toneGen.startTone(android.media.ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 200)
                toneGen.release()
                Log.d(TAG, "🔊 Tono reproducido")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error reproduciendo alerta: ${e.message}")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        
        // Quitar flag de pantalla encendida
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        
        try {
            soundPool?.release()
            soundPool = null
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
        // No detener cámara en onPause para mantener detección activa
        Log.d(TAG, "⏸️ Activity pausada")
    }

    override fun onResume() {
        super.onResume()
        
        lifecycleScope.launch {
            val settings = settingsManager.settingsFlow.first()
            soundEnabled = settings.soundEnabled
            currentLanguage = settings.language
            updateUILanguage()
        }
        
        Log.d(TAG, "▶️ Activity resumida")
    }
}