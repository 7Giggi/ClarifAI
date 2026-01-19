package luigi.tirocinio.clarifai.utils

import android.Manifest

// Classe che contiene tutte le costanti utilizzate nell'applicazione.
// Centralizza i valori di configurazione per facilitare la manutenzione e modifiche future.
// Include costanti per API Gemini, modalita operative, TTS, permessi e feedback audio-tattile.
object Constants {

    // Configurazione API Gemini
    // La chiave API viene inizializzata a runtime per motivi di sicurezza
    lateinit var GEMINI_API_KEY: String
        private set

    // Inizializza la chiave API di Gemini in modo sicuro
    fun initApiKey(apiKey: String) {
        GEMINI_API_KEY = apiKey
    }

    // Modello di intelligenza artificiale Gemini utilizzato per l'analisi delle immagini
    const val GEMINI_MODEL = "gemini-2.0-flash"

    // URL base per le chiamate API a Gemini
    const val GEMINI_API_URL = "https://generativelanguage.googleapis.com/v1beta/models/"

    // Configurazione Modalita 1 - Descrizione ambiente
    // Tempo minimo in millisecondi tra due descrizioni automatiche consecutive per evitare sovrapposizioni
    const val DESCRIPTION_DEBOUNCE_MS = 3000L

    // Numero massimo di tentativi per chiamata API in caso di errore
    const val GEMINI_MAX_RETRIES = 3

    // Timeout in secondi per ogni chiamata API
    const val GEMINI_TIMEOUT_SECONDS = 15L

    // Prompt utilizzato per Gemini in modalita navigazione
    val GEMINI_PROMPT_NAVIGATION = """
        Sei un assistente per persone ipovedenti. Analizza questa immagine e fornisci una descrizione breve e utile dell'ambiente circostante.
        Concentrati su:
        - Oggetti principali e loro posizione relativa (sinistra, centro, destra, vicino, lontano)
        - Potenziali ostacoli o pericoli
        - Elementi utili per la navigazione (porte, scale, corridoi)
        - Persone presenti e loro posizione approssimativa
        Rispondi in italiano con massimo 3-4 frasi concise e informative.
    """.trimIndent()

    // Messaggio di aiuto per l'utente
    const val HELP_MESSAGE = "Benvenuto nell'app di assistenza. Puoi dire: Descrizione, per descrivere l'ambiente circostante. Lettura, per leggere il testo davanti a te. Ostacoli, per rilevare ostacoli sul percorso. Home, per tornare al menu principale. Dì aiuto in qualsiasi momento per riascoltare queste istruzioni."
    // Messaggio di aiuto per la modalita Lettura
    const val HELP_MESSAGE_LETTURA = "Modalità Lettura. Comandi disponibili: Analizza, per catturare e riconoscere il testo. Leggi, per leggere tutto il testo riconosciuto. Prossimo, per il blocco successivo. Precedente, per il blocco precedente. Stop, per interrompere la lettura. Riprendi, per riprendere la lettura. Home, per tornare al menu principale. Aiuto, per riascoltare questi comandi."
    // Messaggio di aiuto per la modalita Descrizione
    const val HELP_MESSAGE_DESCRIZIONE = "Modalità Descrizione. Comandi disponibili: Analizza, per ottenere una descrizione dell'ambiente circostante tramite intelligenza artificiale. Stop, per interrompere la lettura della descrizione. Home, per tornare al menu principale. Aiuto, per riascoltare questi comandi."


    // Configurazione Modalita 3 - Rilevamento profondita

    // Percorso del modello ONNX per l'analisi della profondità
    const val DEPTH_MODEL_PATH = "models/depth_anything_v2_vits_indoor_int8.onnx"

    // Distanza minima rilevabile dal sensore in metri
    const val DEPTH_MIN_METERS = 0.2f

    // Distanza massima rilevabile dal sensore in metri
    const val DEPTH_MAX_METERS = 6.0f

    // Larghezza dell'immagine in input per il modello di profondita
    const val DEPTH_INPUT_SIZE = 252

    // Soglie di distanza per i diversi livelli di allerta

    // Distanza di attenzione (tra 1.2 e 2.5 metri)
    const val DISTANCE_WARNING = 1.2f

    // Distanza di cautela (tra 0.6 e 1.2 metri)
    const val DISTANCE_CAUTION = 0.6f

    // Distanza di pericolo (sotto 0.5 metri)
    const val DISTANCE_DANGER = 0.5f

    // Enum che definisce le zone di pericolo in base alla distanza rilevata
    enum class DangerZone {
        SAFE,
        WARNING,
        CAUTION,
        DANGER
    }

    // Configurazione feedback audio
    // Durata in millisecondi di ogni beep sonoro
    const val BEEP_DURATION_MS = 100

    // Costanti per il bilanciamento dell'audio in base a dove si trova l'ostacolo
    const val AUDIO_PAN_LEFT = -1.0f
    const val AUDIO_PAN_CENTER = 0.0f
    const val AUDIO_PAN_RIGHT = 1.0f

    // Pattern di vibrazione per diversi livelli di allerta
    // Formato: ritardo iniziale, poi alternanza pausa/vibrazione

    val VIBRATION_PATTERN_WARNING = longArrayOf(0, 100, 600)
    val VIBRATION_PATTERN_CAUTION = longArrayOf(0, 150, 450)
    val VIBRATION_PATTERN_DANGER = longArrayOf(0, 200, 100)

    // Configurazione Text-To-Speech
    // Velocita di lettura
    const val TTS_SPEECH_RATE = 1.0f

    // Tonalita della voce
    const val TTS_PITCH = 1.0f

    // Permessi Android richiesti dall'applicazione
    val REQUIRED_PERMISSIONS = arrayOf(
        Manifest.permission.CAMERA,        // Necessario per catturare immagini
        Manifest.permission.RECORD_AUDIO,  // Necessario per comandi vocali
        Manifest.permission.INTERNET,      // Necessario per chiamate API Gemini
        Manifest.permission.VIBRATE        // Necessario per feedback tattile
    )

    // Codice identificativo per la richiesta dei permessi
    const val PERMISSION_REQUEST_CODE = 1001

    // Flag per abilitare/disabilitare log di debug
    const val DEBUG_MODE = true

    // Tag utilizzato per i log dell'applicazione
    const val LOG_TAG = "ClarifAI"
}