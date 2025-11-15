package com.roadguardian.auto

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.RectF
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Bundle
import android.util.Log
import android.view.View
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
                Log.d(TAG, "🔊 Sonido cargado: ID=$alertSoundId")
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
            
            Log.d(TAG, "🧪 Test de overlay...")
            
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
        overlayView.invalidate()
        
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
            
            // Cargar idioma guardado
            lifecycleScope.launch {
                val settings = settingsManager.settingsFlow.first()
                val index = LANGUAGE_CODES.indexOf(settings.language)
                if (index >= 0) {
                    languageSpinner.setSelection(index)
                }
            }
            
            // Listener para cambios
            var isFirstSelection = true
            languageSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    if (isFirstSelection) {
                        isFirstSelection = false
                        return
                    }
                    
                    if (position >= 0 && position < LANGUAGE_CODES.size) {
                        val langCode = LANGUAGE_CODES[position]
                        currentLanguage = langCode
                        
                        lifecycleScope.launch {
                            settingsManager.updateLanguage(langCode)
                            Log.d(TAG, "🌍 Idioma: $langCode")
                        }
                    }
                }

                override fun onNothingSelected(parent: AdapterView<*>?) {}
            }
            
            Log.d(TAG, "✅ Selector de idioma configurado")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error configurando selector: ${e.message}")
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
                currentLanguage = settings.language
                soundEnabled = settings.soundEnabled
                Log.d(TAG, "⚙️ Config: idioma=$currentLanguage, sonido=$soundEnabled")
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
            statusText.text = getString(R.string.status_ready)
            Log.i(TAG, "✅ Permiso de cámara otorgado")
        } else {
            Log.w(TAG, "⚠️ Solicitando permiso")
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                statusText.text = getString(R.string.status_ready)
                Toast.makeText(this, "Permiso otorgado", Toast.LENGTH_SHORT).show()
            } else {
                statusText.text = getString(R.string.camera_permission_required)
                Toast.makeText(this, "Permiso requerido", Toast.LENGTH_LONG).show()
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
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error: ${e.message}", e)
            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun stopDetection() {
        Log.i(TAG, "⏹️ Deteniendo")
        
        try {
            cameraManager.stopCamera()
            detecting = false
            statusText.text = getString(R.string.status_ready)
            startButton.text = getString(R.string.start_detection)
            overlayView.clearDetections()
            totalDetections = 0
            counterText.text = "0"
            
            Toast.makeText(this, "Detección detenida", Toast.LENGTH_SHORT).show()
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
        if (detecting) {
            try {
                cameraManager.stopCamera()
            } catch (e: Exception) {
                Log.e(TAG, "Error: ${e.message}")
            }
        }
    }

    override fun onResume() {
        super.onResume()
        
        lifecycleScope.launch {
            val settings = settingsManager.settingsFlow.first()
            soundEnabled = settings.soundEnabled
            currentLanguage = settings.language
        }
        
        if (detecting) {
            try {
                cameraManager.initializeCamera()
            } catch (e: Exception) {
                Log.e(TAG, "Error: ${e.message}")
            }
        }
    }
}