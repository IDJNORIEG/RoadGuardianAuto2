package com.roadguardian.auto

import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.textview.MaterialTextView
import com.roadguardian.auto.models.Detection
import com.roadguardian.auto.ui.DetectionOverlayView
import kotlinx.coroutines.*
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private lateinit var previewView: PreviewView
    private lateinit var overlayView: DetectionOverlayView
    private lateinit var counterText: TextView
    private lateinit var statusText: MaterialTextView
    private lateinit var stopButton: FloatingActionButton

    private lateinit var detectionManager: DetectionManager
    private lateinit var settingsManager: SettingsManager

    private var mediaPlayer: MediaPlayer? = null
    private var totalDetections = 0
    private var currentLanguage = "es"
    private var detecting = false

    private val cameraExecutor = Executors.newSingleThreadExecutor()
    private val mainScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.screen_detection)

        previewView = findViewById(R.id.detectionPreview)
        overlayView = findViewById(R.id.detectionOverlay)
        counterText = findViewById(R.id.detectionCountText)
        statusText = findViewById(R.id.statusTextDetection)
        stopButton = findViewById(R.id.stopButton)

        detectionManager = DetectionManager(this)
        settingsManager = SettingsManager(this)

        mediaPlayer = MediaPlayer.create(this, R.raw.alert_sound)

        stopButton.setOnClickListener { stopDetection() }

        // Verificar permisos de cámara
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }

        // Cargar idioma desde DataStore
        mainScope.launch {
            settingsManager.settingsFlow.collect {
                currentLanguage = it.language
            }
        }
    }

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) startCamera()
            else statusText.text = getString(R.string.camera_permission_required)
        }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

            val imageAnalyzer = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor) { image ->
                        processImage(image)
                    }
                }

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageAnalyzer
                )
                statusText.text = getString(R.string.status_detecting)
                detecting = true
            } catch (e: Exception) {
                Log.e("MainActivity", "Error iniciando cámara: ${e.message}", e)
            }

        }, ContextCompat.getMainExecutor(this))
    }

    private fun processImage(image: ImageProxy) {
        val bitmap = ImageUtils.imageToBitmap(image) ?: return
        val detections = detectionManager.detectAnimals(bitmap)
        image.close()

        runOnUiThread {
            if (detections.isNotEmpty()) {
                totalDetections += detections.size
                counterText.text = totalDetections.toString()
                playAlert()
            }

            // Escalado y actualización de overlay
            val scaledDetections = overlayView.scaleDetections(
                detections, previewView.width, previewView.height
            )
            overlayView.updateDetections(scaledDetections, currentLanguage)
        }
    }

    private fun playAlert() {
        if (mediaPlayer?.isPlaying == false) {
            mediaPlayer?.start()
        }
    }

    private fun stopDetection() {
        detecting = false
        statusText.text = getString(R.string.status_ready)
        overlayView.clearDetections()
        totalDetections = 0
        counterText.text = "0"
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaPlayer?.release()
        detectionManager.release()
        cameraExecutor.shutdown()
        mainScope.cancel()
    }
}
