package com.roadguardian.auto

import android.Manifest
import android.content.pm.PackageManager
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
import com.google.android.gms.ads.AdView
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
    private lateinit var adView: AdView

    private lateinit var cameraManager: CameraManager
    private lateinit var detectionManager: DetectionManager
    private lateinit var settingsManager: SettingsManager
    private lateinit var adManager: AdManager

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
            initializeAds()
            setupListeners()
            setupLanguageSpinner()
            checkCameraPermission()
            
            // Mostrar anuncio de bienvenida después de 2 segundos
            lifecycleScope.launch {
                delay(2000)
                adManager.showWelcomeAd(this@MainActivity)
            }
            
            Log.i(TAG, "✅ MainActivity inicializada completamente")
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

    private fun initializeAds() {
        try {
            Log.d(TAG, "📢 Inicializando sistema de anuncios...")
            
            // Crear AdManager
            adManager = AdManager(this)
            adManager.initialize()
            
            // Obtener y configurar AdView
            adView = findViewById(R.id.adView)
            adView.apply {
                setAdSize(com.google.android.gms.ads.AdSize.BANNER)
                // ⚠️ ID de prueba - Reemplaza con tu ID real de producción
                adUnitId = "ca-app-pub-8690577445002348/8703720200"
                // Para producción: adUnitId = "TU_BANNER_ID_REAL"
            }
            
            // Cargar banner
            adManager.loadBanner(adView)
            
            Log.d(TAG, "✅ Sistema de anuncios inicializado correctamente")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error inicializando ads: ${e.message}", e)
            // La app continúa funcionando sin anuncios
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
                        Log.d(TAG, "🌍 Idioma cambiado a: $currentLanguage")
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
            Log.w(TAG, "⚠️ Solicitando permiso de cámara")
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                statusText.text = TranslationsManager.getStatusReady(currentLanguage)
                Toast.makeText(this, "Permiso otorgado", Toast.LENGTH_SHORT).show()
                Log.i(TAG, "✅ Permiso de cámara concedido")
            } else {
                statusText.text = TranslationsManager.getCameraPermissionRequired(currentLanguage)
                Toast.makeText(this, TranslationsManager.getCameraPermissionRequired(currentLanguage), Toast.LENGTH_LONG).show()
                Log.w(TAG, "⚠️ Permiso de cámara denegado")
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
            // Limpiar completamente antes de iniciar
            overlayView.clearDetections()
            previewView.removeAllViews()
            
            // Actualizar UI inmediatamente
            detecting = true
            statusText.text = TranslationsManager.getStatusDetecting(currentLanguage)
            startButton.text = TranslationsManager.getStopDetection(currentLanguage)
            totalDetections = 0
            counterText.text = "0"
            
            // Iniciar cámara
            cameraManager.initializeCamera()
            
            // ⚠️ IMPORTANTE: Ocultar anuncios durante detección
            adManager.onDetectionStarted()
            
            Toast.makeText(this, TranslationsManager.getDetectionStarted(currentLanguage), Toast.LENGTH_SHORT).show()
            Log.i(TAG, "✅ Detección iniciada - Anuncios OCULTOS")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error iniciando detección: ${e.message}", e)
            detecting = false
            updateUILanguage()
            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun stopDetection() {
        Log.i(TAG, "⏹️ Deteniendo detección")
        
        try {
            // Actualizar UI primero
            detecting = false
            statusText.text = TranslationsManager.getStatusReady(currentLanguage)
            startButton.text = TranslationsManager.getStartDetection(currentLanguage)
            
            // Detener cámara
            cameraManager.stopCamera()
            
            // ⚠️ IMPORTANTE: Mostrar anuncios al terminar detección
            adManager.onDetectionStopped()
            
            // Limpiar UI con delay para asegurar que todo se detenga
            lifecycleScope.launch {
                delay(300)
                
                runOnUiThread {
                    // Limpiar overlay
                    overlayView.clearDetections()
                    
                    // Resetear contador
                    totalDetections = 0
                    counterText.text = "0"
                    
                    // Forzar redibujo limpio del preview
                    previewView.removeAllViews()
                    previewView.invalidate()
                    
                    Log.i(TAG, "✅ Detección detenida y UI limpia")
                }
                
                // Mostrar anuncio intersticial al finalizar detección (después de 500ms)
                delay(500)
                adManager.showDetectionEndAd(this@MainActivity)
                
                Toast.makeText(
                    this@MainActivity, 
                    TranslationsManager.getDetectionStopped(currentLanguage), 
                    Toast.LENGTH_SHORT
                ).show()
                
                Log.i(TAG, "✅ Detección finalizada - Anuncios VISIBLES")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error deteniendo detección: ${e.message}", e)
        }
    }

    private fun updateDetectionUI(detections: List<Detection>) {
        if (detections.isNotEmpty()) {
            totalDetections += detections.size
            counterText.text = totalDetections.toString()
            
            Log.i(TAG, "📊 Total detecciones: $totalDetections")
            
            if (soundEnabled) {
                playAlert()
            }
            
            try {
                val vibrator = getSystemService(VIBRATOR_SERVICE) as? android.os.Vibrator
                vibrator?.vibrate(200)
            } catch (e: Exception) {
                Log.w(TAG, "⚠️ No se pudo vibrar: ${e.message}")
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
                Log.d(TAG, "🔊 Alerta sonora reproducida")
            } else {
                val toneGen = android.media.ToneGenerator(
                    android.media.AudioManager.STREAM_ALARM,
                    100
                )
                toneGen.startTone(android.media.ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 200)
                toneGen.release()
                Log.d(TAG, "🔊 Tono de alerta reproducido")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error reproduciendo alerta: ${e.message}")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        
        Log.i(TAG, "🔴 Destruyendo MainActivity")
        
        // Quitar flag de pantalla encendida
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        
        try {
            soundPool?.release()
            soundPool = null
            
            detectionManager.release()
            
            if (detecting) {
                cameraManager.stopCamera()
            }
            
            // Destruir anuncios
            adManager.destroy()
            
            Log.i(TAG, "✅ Recursos liberados correctamente")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error en onDestroy: ${e.message}", e)
        }
    }

    override fun onPause() {
        super.onPause()
        
        // Pausar anuncios
        adManager.pause()
        
        Log.d(TAG, "⏸️ Activity pausada - Anuncios pausados")
    }

    override fun onResume() {
        super.onResume()
        
        // Mantener pantalla encendida al resumir
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        
        // Resumir anuncios
        adManager.resume()
        
        lifecycleScope.launch {
            val settings = settingsManager.settingsFlow.first()
            soundEnabled = settings.soundEnabled
            currentLanguage = settings.language
            updateUILanguage()
        }
        
        Log.d(TAG, "▶️ Activity resumida - Anuncios resumidos")
    }
}