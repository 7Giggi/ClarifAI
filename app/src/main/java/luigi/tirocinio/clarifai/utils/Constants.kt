package luigi.tirocinio.clarifai.utils

import android.Manifest

object Constants {

    // costanti per Gemini
    lateinit var GEMINI_API_KEY: String
        private set

    fun initApiKey(apiKey: String) {
        GEMINI_API_KEY = apiKey
    }    const val GEMINI_MODEL = "gemini-2.0-flash" // Modello di gemini usato
    const val GEMINI_API_URL = "https://generativelanguage.googleapis.com/v1beta/models/"


    // costanti per mod 1
    const val DESCRIPTION_DEBOUNCE_MS = 3000L // 3 secondi tra descrizioni automatiche (non ancora implementate)
    const val GEMINI_MAX_RETRIES = 3
    const val GEMINI_TIMEOUT_SECONDS = 15L

    // Prompt Gemini per mod1
     val GEMINI_PROMPT_NAVIGATION = """
        Sei un assistente per persone ipovedenti. Analizza questa immagine e fornisci una descrizione breve e utile dell'ambiente circostante.
        Concentrati su:
        - Oggetti principali e loro posizione relativa (sinistra, centro, destra, vicino, lontano)
        - Potenziali ostacoli o pericoli
        - Elementi utili per la navigazione (porte, scale, corridoi)
        - Persone presenti e loro posizione approssimativa
        
        Rispondi in italiano con massimo 3-4 frasi concise e informative.
    """.trimIndent()

    // costanti mod2 (non ancora implementate)
    const val TEXT_MIN_CONFIDENCE = 0.9f // Confidence minima per considerare testo valido


    // costanti per comandi vocali (non funzionano)
    val VOICE_COMMANDS_READ = listOf(
        "leggi tutto",
        "leggi",
        "inizia lettura",
        "traduci",
        "traduzione"
    )

    // costanti per mod3 (non ancora implementate)



    const val MIDAS_MODEL_PATH = "models/midas_v21_small_256.tflite"
    const val MIDAS_INPUT_SIZE = 256


    const val OBJECT_DETECTION_CONFIDENCE = 0.5f
    const val MAX_OBJECTS_TRACKED = 5


    const val DISTANCE_SAFE = 2.0f
    const val DISTANCE_WARNING = 1.0f
    const val DISTANCE_CAUTION = 0.5f
    const val DISTANCE_DANGER = 0.5f


    const val BEEP_FREQUENCY_BASE = 440f
    const val BEEP_FREQUENCY_MAX = 880f
    const val BEEP_DURATION_MS = 100


    const val BEEP_INTERVAL_SAFE = 2000L
    const val BEEP_INTERVAL_WARNING = 700L
    const val BEEP_INTERVAL_CAUTION = 300L
    const val BEEP_INTERVAL_DANGER = 100L


    const val AUDIO_PAN_LEFT = -1.0f
    const val AUDIO_PAN_CENTER = 0.0f
    const val AUDIO_PAN_RIGHT = 1.0f


     val VIBRATION_PATTERN_SAFE = longArrayOf(0, 50, 950)
     val VIBRATION_PATTERN_WARNING = longArrayOf(0, 100, 600)
     val VIBRATION_PATTERN_CAUTION = longArrayOf(0, 150, 450)
     val VIBRATION_PATTERN_DANGER = longArrayOf(0, 200, 100)

    // costanti per il TTS
    const val TTS_SPEECH_RATE = 1.0f
    const val TTS_PITCH = 1.0f
    const val TTS_QUEUE_MODE_FLUSH = 0
    const val TTS_QUEUE_MODE_ADD = 1

    // costanti per i permessi
    val REQUIRED_PERMISSIONS = arrayOf(
        Manifest.permission.CAMERA,
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.INTERNET,
        Manifest.permission.VIBRATE
    )

    const val PERMISSION_REQUEST_CODE = 1001


    const val ML_KIT_EXECUTOR_THREADS = 2


    enum class ScreenZone {
        TOP,      // Per attivare modalità 1
        CENTER,   // Area preview
        BOTTOM    // Controlli
    }

    enum class ObjectPosition {
        LEFT,
        CENTER,
        RIGHT,
        UNKNOWN
    }

    enum class DangerZone {
        SAFE,       // Verde
        WARNING,    // Gialla
        CAUTION,    // Arancione
        DANGER      // Rossa
    }


    const val BUTTON_MIN_TOUCH_TARGET_DP = 48
    const val HIGH_CONTRAST_RATIO = 7.0f


    const val DEBUG_MODE = true
    const val LOG_TAG = "ClarifAI"
}
