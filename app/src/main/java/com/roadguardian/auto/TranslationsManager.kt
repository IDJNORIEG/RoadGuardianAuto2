package com.roadguardian.auto

object TranslationsManager {
    
    // Textos de la interfaz principal
    fun getStartDetection(lang: String): String = when(lang) {
        "es" -> "Iniciar Detección"
        "en" -> "Start Detection"
        "fr" -> "Démarrer Détection"
        "it" -> "Avvia Rilevamento"
        "de" -> "Erkennung Starten"
        "ru" -> "Начать Обнаружение"
        else -> "Iniciar Detección"
    }
    
    fun getStopDetection(lang: String): String = when(lang) {
        "es" -> "Detener"
        "en" -> "Stop"
        "fr" -> "Arrêter"
        "it" -> "Ferma"
        "de" -> "Stoppen"
        "ru" -> "Остановить"
        else -> "Detener"
    }
    
    fun getStatusReady(lang: String): String = when(lang) {
        "es" -> "Listo"
        "en" -> "Ready"
        "fr" -> "Prêt"
        "it" -> "Pronto"
        "de" -> "Bereit"
        "ru" -> "Готов"
        else -> "Listo"
    }
    
    fun getStatusDetecting(lang: String): String = when(lang) {
        "es" -> "Detectando..."
        "en" -> "Detecting..."
        "fr" -> "Détection..."
        "it" -> "Rilevamento..."
        "de" -> "Erkennung..."
        "ru" -> "Обнаружение..."
        else -> "Detectando..."
    }
    
    // Configuración
    fun getSettings(lang: String): String = when(lang) {
        "es" -> "Configuración"
        "en" -> "Settings"
        "fr" -> "Paramètres"
        "it" -> "Impostazioni"
        "de" -> "Einstellungen"
        "ru" -> "Настройки"
        else -> "Configuración"
    }
    
    fun getSensitivity(lang: String): String = when(lang) {
        "es" -> "Sensibilidad"
        "en" -> "Sensitivity"
        "fr" -> "Sensibilité"
        "it" -> "Sensibilità"
        "de" -> "Empfindlichkeit"
        "ru" -> "Чувствительность"
        else -> "Sensibilidad"
    }
    
    fun getConfidence(lang: String): String = when(lang) {
        "es" -> "Confianza mínima"
        "en" -> "Minimum Confidence"
        "fr" -> "Confiance minimale"
        "it" -> "Confidenza minima"
        "de" -> "Mindestvertrauen"
        "ru" -> "Минимальная уверенность"
        else -> "Confianza mínima"
    }
    
    fun getSoundAlert(lang: String): String = when(lang) {
        "es" -> "Alerta sonora"
        "en" -> "Sound Alert"
        "fr" -> "Alerte sonore"
        "it" -> "Allarme sonoro"
        "de" -> "Tonalarm"
        "ru" -> "Звуковое оповещение"
        else -> "Alerta sonora"
    }
    
    fun getDarkMode(lang: String): String = when(lang) {
        "es" -> "Modo oscuro"
        "en" -> "Dark Mode"
        "fr" -> "Mode sombre"
        "it" -> "Modalità scura"
        "de" -> "Dunkelmodus"
        "ru" -> "Темный режим"
        else -> "Modo oscuro"
    }
    
    fun getLanguage(lang: String): String = when(lang) {
        "es" -> "Idioma"
        "en" -> "Language"
        "fr" -> "Langue"
        "it" -> "Lingua"
        "de" -> "Sprache"
        "ru" -> "Язык"
        else -> "Idioma"
    }
    
    fun getAccept(lang: String): String = when(lang) {
        "es" -> "Aceptar"
        "en" -> "Accept"
        "fr" -> "Accepter"
        "it" -> "Accetta"
        "de" -> "Akzeptieren"
        "ru" -> "Принять"
        else -> "Aceptar"
    }
    
    fun getCancel(lang: String): String = when(lang) {
        "es" -> "Cancelar"
        "en" -> "Cancel"
        "fr" -> "Annuler"
        "it" -> "Annulla"
        "de" -> "Abbrechen"
        "ru" -> "Отмена"
        else -> "Cancelar"
    }
    
    // Mensajes
    fun getDetectionStarted(lang: String): String = when(lang) {
        "es" -> "Detección iniciada"
        "en" -> "Detection started"
        "fr" -> "Détection démarrée"
        "it" -> "Rilevamento avviato"
        "de" -> "Erkennung gestartet"
        "ru" -> "Обнаружение запущено"
        else -> "Detección iniciada"
    }
    
    fun getDetectionStopped(lang: String): String = when(lang) {
        "es" -> "Detección detenida"
        "en" -> "Detection stopped"
        "fr" -> "Détection arrêtée"
        "it" -> "Rilevamento fermato"
        "de" -> "Erkennung gestoppt"
        "ru" -> "Обнаружение остановлено"
        else -> "Detección detenida"
    }
    
    fun getCameraPermissionRequired(lang: String): String = when(lang) {
        "es" -> "Se requiere permiso de cámara"
        "en" -> "Camera permission required"
        "fr" -> "Permission caméra requise"
        "it" -> "Permesso fotocamera richiesto"
        "de" -> "Kameraberechtigung erforderlich"
        "ru" -> "Требуется разрешение камеры"
        else -> "Se requiere permiso de cámara"
    }
    
    fun getSoundEnabled(lang: String): String = when(lang) {
        "es" -> "Sonido activado"
        "en" -> "Sound enabled"
        "fr" -> "Son activé"
        "it" -> "Audio attivato"
        "de" -> "Ton aktiviert"
        "ru" -> "Звук включен"
        else -> "Sonido activado"
    }
    
    fun getSoundDisabled(lang: String): String = when(lang) {
        "es" -> "Sonido desactivado"
        "en" -> "Sound disabled"
        "fr" -> "Son désactivé"
        "it" -> "Audio disattivato"
        "de" -> "Ton deaktiviert"
        "ru" -> "Звук отключен"
        else -> "Sonido desactivado"
    }
    
    fun getRestartToApplyTheme(lang: String): String = when(lang) {
        "es" -> "Reinicie la app para aplicar el tema"
        "en" -> "Restart app to apply theme"
        "fr" -> "Redémarrez l'app pour appliquer le thème"
        "it" -> "Riavvia l'app per applicare il tema"
        "de" -> "App neu starten, um Design anzuwenden"
        "ru" -> "Перезапустите приложение для применения темы"
        else -> "Reinicie la app para aplicar el tema"
    }
}