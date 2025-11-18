package com.roadguardian.auto

import android.app.Activity
import android.content.Context
import android.util.Log
import android.view.View
import com.google.android.gms.ads.*
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback

/**
 * Gestor centralizado de anuncios AdMob
 * Garantiza que NO se muestren anuncios durante la detección activa
 */
class AdManager(private val context: Context) {

    private var interstitialAd: InterstitialAd? = null
    private var bannerAd: AdView? = null
    private var isDetecting = false
    private var hasShownWelcomeAd = false

    companion object {
        private const val TAG = "AdManager"
        
        // ⚠️ IDs de prueba - REEMPLAZA CON TUS IDs REALES DE ADMOB
        // private const val BANNER_AD_UNIT_ID = "ca-app-pub-3940256099942544/6300978111" // Test ID
        // private const val INTERSTITIAL_AD_UNIT_ID = "ca-app-pub-3940256099942544/1033173712" // Test ID
        
        // ⚠️ Para producción, reemplaza con tus IDs reales:
        private const val BANNER_AD_UNIT_ID = "ca-app-pub-8690577445002348/8703720200"
        private const val INTERSTITIAL_AD_UNIT_ID = "ca-app-pub-8690577445002348/4251956414"
        
        private const val MIN_TIME_BETWEEN_INTERSTITIALS = 60000L // 1 minuto
    }

    private var lastInterstitialTime = 0L

    /**
     * Inicializa AdMob SDK
     */
    fun initialize() {
        try {
            MobileAds.initialize(context) { initStatus ->
                Log.i(TAG, "✅ AdMob inicializado: ${initStatus.adapterStatusMap}")
            }
            
            // Pre-cargar anuncio intersticial
            loadInterstitialAd()
            
            Log.i(TAG, "✅ AdManager inicializado")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error inicializando AdMob: ${e.message}", e)
        }
    }

    /**
     * Carga el banner en un AdView
     * SOLO se muestra cuando NO está detectando
     */
    fun loadBanner(adView: AdView) {
        try {
            bannerAd = adView
            
            // Asegurarse de que el AdView esté visible inicialmente
            adView.visibility = View.VISIBLE
            
            val adRequest = AdRequest.Builder().build()
            adView.loadAd(adRequest)
            
            adView.adListener = object : AdListener() {
                override fun onAdLoaded() {
                    Log.d(TAG, "📢 Banner cargado exitosamente")
                    // Solo mostrar si NO está detectando
                    if (!isDetecting) {
                        adView.visibility = View.VISIBLE
                        Log.d(TAG, "👁️ Banner visible")
                    } else {
                        adView.visibility = View.GONE
                        Log.d(TAG, "👻 Banner oculto (detectando)")
                    }
                }

                override fun onAdFailedToLoad(adError: LoadAdError) {
                    Log.e(TAG, "❌ Banner falló: Code=${adError.code}, Message=${adError.message}")
                    Log.e(TAG, "   Domain: ${adError.domain}, Cause: ${adError.cause}")
                    adView.visibility = View.GONE
                }

                override fun onAdClicked() {
                    Log.d(TAG, "👆 Banner clickeado")
                }

                override fun onAdOpened() {
                    Log.d(TAG, "📖 Banner abierto")
                }

                override fun onAdClosed() {
                    Log.d(TAG, "📕 Banner cerrado")
                }
            }
            
            Log.d(TAG, "🎯 Banner cargando...")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error cargando banner: ${e.message}", e)
        }
    }

    /**
     * Pre-carga un anuncio intersticial
     */
    private fun loadInterstitialAd() {
        try {
            val adRequest = AdRequest.Builder().build()

            InterstitialAd.load(
                context,
                INTERSTITIAL_AD_UNIT_ID,
                adRequest,
                object : InterstitialAdLoadCallback() {
                    override fun onAdLoaded(ad: InterstitialAd) {
                        interstitialAd = ad
                        Log.d(TAG, "✅ Intersticial cargado")
                        
                        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                            override fun onAdDismissedFullScreenContent() {
                                Log.d(TAG, "📴 Intersticial cerrado")
                                interstitialAd = null
                                // Cargar siguiente anuncio
                                loadInterstitialAd()
                            }

                            override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                                Log.w(TAG, "⚠️ Intersticial falló al mostrar: ${adError.message}")
                                interstitialAd = null
                            }

                            override fun onAdShowedFullScreenContent() {
                                Log.d(TAG, "📺 Intersticial mostrado")
                            }
                        }
                    }

                    override fun onAdFailedToLoad(adError: LoadAdError) {
                        Log.w(TAG, "⚠️ Intersticial falló al cargar: ${adError.message}")
                        interstitialAd = null
                        
                        // Reintentar después de 30 segundos
                        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                            loadInterstitialAd()
                        }, 30000)
                    }
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error cargando intersticial: ${e.message}", e)
        }
    }

    /**
     * Muestra anuncio intersticial de bienvenida
     * Solo se muestra UNA VEZ por sesión al iniciar la app
     */
    fun showWelcomeAd(activity: Activity) {
        if (hasShownWelcomeAd) {
            Log.d(TAG, "ℹ️ Ya se mostró el anuncio de bienvenida en esta sesión")
            return
        }
        
        if (isDetecting) {
            Log.w(TAG, "⚠️ NO se puede mostrar anuncio durante detección")
            return
        }
        
        showInterstitialAd(activity, "bienvenida")
        hasShownWelcomeAd = true
    }

    /**
     * Muestra anuncio intersticial al finalizar detección
     * Solo si ha pasado suficiente tiempo desde el último
     */
    fun showDetectionEndAd(activity: Activity) {
        val currentTime = System.currentTimeMillis()
        
        if (currentTime - lastInterstitialTime < MIN_TIME_BETWEEN_INTERSTITIALS) {
            Log.d(TAG, "ℹ️ Muy pronto para otro anuncio intersticial")
            return
        }
        
        if (isDetecting) {
            Log.w(TAG, "⚠️ NO se puede mostrar anuncio durante detección")
            return
        }
        
        showInterstitialAd(activity, "fin_detección")
        lastInterstitialTime = currentTime
    }

    /**
     * Muestra el anuncio intersticial si está disponible
     */
    private fun showInterstitialAd(activity: Activity, context: String) {
        try {
            if (interstitialAd != null) {
                Log.i(TAG, "📺 Mostrando intersticial: $context")
                interstitialAd?.show(activity)
            } else {
                Log.w(TAG, "⚠️ Intersticial no disponible para: $context")
                // Intentar cargar uno nuevo
                loadInterstitialAd()
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error mostrando intersticial: ${e.message}", e)
        }
    }

    /**
     * Notifica que la detección ha iniciado
     * OCULTA todos los anuncios automáticamente
     */
    fun onDetectionStarted() {
        isDetecting = true
        hideBanner()
        Log.i(TAG, "🚗 Detección iniciada - Anuncios OCULTOS")
    }

    /**
     * Notifica que la detección ha terminado
     * MUESTRA el banner nuevamente
     */
    fun onDetectionStopped() {
        isDetecting = false
        showBanner()
        Log.i(TAG, "🛑 Detección detenida - Banner VISIBLE")
    }

    /**
     * Oculta el banner
     */
    private fun hideBanner() {
        try {
            bannerAd?.visibility = View.GONE
            Log.d(TAG, "👻 Banner ocultado")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error ocultando banner: ${e.message}")
        }
    }

    /**
     * Muestra el banner (solo si NO está detectando)
     */
    private fun showBanner() {
        try {
            if (!isDetecting) {
                bannerAd?.visibility = View.VISIBLE
                Log.d(TAG, "👁️ Banner visible")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error mostrando banner: ${e.message}")
        }
    }

    /**
     * Pausa los anuncios (llamar en onPause)
     */
    fun pause() {
        try {
            bannerAd?.pause()
            Log.d(TAG, "⏸️ Anuncios pausados")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error pausando: ${e.message}")
        }
    }

    /**
     * Resume los anuncios (llamar en onResume)
     */
    fun resume() {
        try {
            bannerAd?.resume()
            Log.d(TAG, "▶️ Anuncios resumidos")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error resumiendo: ${e.message}")
        }
    }

    /**
     * Destruye los anuncios (llamar en onDestroy)
     */
    fun destroy() {
        try {
            bannerAd?.destroy()
            interstitialAd = null
            Log.i(TAG, "🗑️ Anuncios destruidos")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error destruyendo: ${e.message}")
        }
    }
}