package luigi.tirocinio.clarifai.ml.manager

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import luigi.tirocinio.clarifai.utils.Constants

// Classe per la gestione dei comandi vocali
class SpeechRecognizerManager(private val context: Context) {
    private var speechRecognizer: SpeechRecognizer? = null
    private var isListening = false
    private var onCommandDetected: ((String) -> Unit)? = null
    private var onError: ((String) -> Unit)? = null
    init {
        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
            setupListener()
        } else {
            Log.e(Constants.LOG_TAG, "Speech recognition non disponibile")
        }
    }
    private fun setupListener() {
        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                isListening = true
            }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {
                isListening = false
            }
            //Funzione per gli errori
            override fun onError(error: Int) {
                val errorMessage = when (error) {
                    SpeechRecognizer.ERROR_AUDIO -> "Errore registrazione audio (3)"
                    SpeechRecognizer.ERROR_CLIENT -> "Errore client (5)"
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Permessi insufficienti (9)"
                    SpeechRecognizer.ERROR_NETWORK -> "Errore di rete (2)"
                    SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Timeout rete (1)"
                    SpeechRecognizer.ERROR_NO_MATCH -> "Nessun riconoscimento (7)"
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognizer occupato (8)"
                    SpeechRecognizer.ERROR_SERVER -> "Errore server (4)"
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Timeout parlato (6)"
                    else -> "Errore sconosciuto ($error)"
                }
            }
            override fun onResults(results: Bundle?) {
                isListening = false
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)

                matches?.firstOrNull()?.let { spokenText ->
                    processCommand(spokenText.lowercase())
                }
            }
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
    }
    // Funzione per processare i comandi in base a quello che viene detto dall'utente (per ora solo comandi base)
    private fun processCommand(spokenText: String) {
        when {
            spokenText.contains("analizza") -> onCommandDetected?.invoke("analizza")
            spokenText.contains("leggi") -> onCommandDetected?.invoke("leggi")
            spokenText.contains("traduci") -> onCommandDetected?.invoke("traduci")
            spokenText.contains("ferma") || spokenText.contains("stop") -> onCommandDetected?.invoke("stop")
            else -> {
                if (Constants.DEBUG_MODE) {
                    Log.d(Constants.LOG_TAG, "Comando non riconosciuto: $spokenText")
                }
            }
        }
    }
    //Funzione per l'inizio del riconoscimento vocale
    fun startListening() {
        if (isListening) return
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "it-IT")
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        speechRecognizer?.startListening(intent)
    }
    fun stopListening() {
        speechRecognizer?.stopListening()
        isListening = false
    }
    fun setCallbacks(onCommand: (String) -> Unit, onErr: (String) -> Unit = {}) {
        onCommandDetected = onCommand
        onError = onErr
    }
    fun shutdown() {
        speechRecognizer?.destroy()
        speechRecognizer = null
    }
}
