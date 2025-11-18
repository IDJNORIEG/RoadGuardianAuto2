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
 * ✅ CORREGIDO: Banner visible por defecto, oculto solo durante detección
 */
class AdManager(private val context: Context) {

    private var interstitialAd: InterstitialAd? = null
    private var bannerAd: AdView? = null
    private var isDetecting = false
    private var hasShownWelcomeAd = false

    companion object {
        private const val TAG = "AdManager"
        
        // IDs REALES de producción
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
     * ✅ CORREGIDO: Carga el banner y lo hace VISIBLE inmediatamente
     */
    fun loadBanner(adView: AdView) {
        try {
            bannerAd = adView
            
            // ✅ Banner VISIBLE por defecto
            adView.visibility = View.VISIBLE
            
            val adRequest = AdRequest.Builder().build()
            adView.loadAd(adRequest)
            
            adView.adListener = object : AdListener() {
                override fun onAdLoaded() {
                    Log.d(TAG, "📢 Banner cargado exitosamente")
                    // ✅ Mantener visible solo si NO está detectando
                    adView.visibility = if (isDetecting) View.GONE else View.VISIBLE
                    Log.d(TAG, if (isDetecting) "👻 Banner oculto (detectando)" else "👁️ Banner visible")
                }

                override fun onAdFailedToLoad(adError: LoadAdError) {
                    Log.e(TAG, "❌ Banner falló: Code=${adError.code}, Message=${adError.message}")
                    Log.e(TAG, "   Domain: ${adError.domain}, ResponseInfo: ${adError.responseInfo}")
                    // ✅ Mantener visible aunque falle la carga
                    adView.visibility = View.VISIBLE
                }

                override fun onAdClicked() {
                    Log.d(TAG, "👆 Banner clickeado")
                }

                override fun onAdImpression() {
                    Log.d(TAG, "👀 Banner impresión registrada")
                }
            }
            
            Log.d(TAG, "🎯 Banner cargando con ID: $BANNER_AD_UNIT_ID")
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
                        Log.w(TAG, "⚠️ Intersticial falló al cargar: Code=${adError.code}, Message=${adError.message}")
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
     */
    fun showWelcomeAd(activity: Activity) {
        if (hasShownWelcomeAd) {
            Log.d(TAG, "ℹ️ Ya se mostró el anuncio de bienvenida")
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
     * ✅ CORREGIDO: Oculta banner durante detección
     */
    fun onDetectionStarted() {
        isDetecting = true
        bannerAd?.visibility = View.GONE
        Log.i(TAG, "🚗 Detección iniciada - Banner OCULTO")
    }

    /**
     * ✅ CORREGIDO: Muestra banner al terminar detección
     */
    fun onDetectionStopped() {
        isDetecting = false
        bannerAd?.visibility = View.VISIBLE
        Log.i(TAG, "🛑 Detección detenida - Banner VISIBLE")
    }

    /**
     * Pausa los anuncios
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
     * Resume los anuncios
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
     * Destruye los anuncios
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