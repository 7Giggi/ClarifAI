package luigi.tirocinio.clarifai.ml.manager

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import luigi.tirocinio.clarifai.utils.Constants
// Classe per la gestione dei comandi vocali
class SpeechRecognizerManager(private val context: Context) {

    //private val TAG = "SpeechRecognizerManager"

    private var speechRecognizer: SpeechRecognizer? = null
    private var isListening = false
    private var onCommandDetected: ((String) -> Unit)? = null
    private var onError: ((String) -> Unit)? = null

    private val handler = Handler(Looper.getMainLooper())

    init {
        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
            setupListener()
            //Log.d(TAG, "SpeechRecognizer inizializzato")
        } else {
            //Log.e(TAG, "Speech recognition NON disponibile su questo dispositivo")
        }
    }

    private fun setupListener() {
        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                isListening = true
                //Log.d(TAG, "Pronto per ascoltare")
            }

            override fun onBeginningOfSpeech() {
                //Log.d(TAG, "Inizio parlato rilevato")
            }

            override fun onRmsChanged(rmsdB: Float) {}

            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {
                isListening = false
                //Log.d(TAG, "Fine parlato")
            }
            //Funzione per gli errori
            override fun onError(error: Int) {
                val errorMessage = when (error) {
                    SpeechRecognizer.ERROR_AUDIO -> "Errore registrazione audio"
                    SpeechRecognizer.ERROR_CLIENT -> "Errore client"
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Permessi insufficienti - RECORD_AUDIO"
                    SpeechRecognizer.ERROR_NETWORK -> "Errore di rete"
                    SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Timeout rete"
                    SpeechRecognizer.ERROR_NO_MATCH -> "Nessuna corrispondenza vocale"
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognizer occupato"
                    SpeechRecognizer.ERROR_SERVER -> "Errore server Google"
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Timeout parlato - non hai detto nulla"
                    else -> "Errore sconosciuto ($error)"
                }

                //Log.e(TAG, "Errore speech: $errorMessage (codice: $error)")
                isListening = false

                onError?.invoke(errorMessage)

                if (error != SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) {
                    handler.postDelayed({
                        if (!isListening) {
                            //Log.d(TAG, "Riavvio ascolto dopo errore")
                            startListening()
                        }
                    }, 1000)
                }
            }

            override fun onResults(results: Bundle?) {
                isListening = false
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)

                matches?.firstOrNull()?.let { spokenText ->
                    //Log.d(TAG, "Riconosciuto: '$spokenText'")
                    processCommand(spokenText.lowercase())
                }

                handler.postDelayed({
                    if (!isListening) {
                        //Log.d(TAG, "Riavvio ascolto continuo")
                        startListening()
                    }
                }, 500)
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                matches?.firstOrNull()?.let {
                    //Log.d(TAG, "Parziale: '$it'")
                }
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
    }

    // Funzione per processare i comandi in base a quello che viene detto dall'utente
    private fun processCommand(spokenText: String) {
        //Log.d(TAG, "Processando comando: '$spokenText'")

        when {
            spokenText.contains("analizza") || spokenText.contains("scansiona") -> {
                //Log.d(TAG, "COMANDO: ANALIZZA")
                onCommandDetected?.invoke("analizza")
            }
            spokenText.contains("leggi") || spokenText.contains("leggere") -> {
                //Log.d(TAG, "COMANDO: LEGGI")
                onCommandDetected?.invoke("leggi")
            }
            spokenText.contains("traduci") || spokenText.contains("traduzione") -> {
                //Log.d(TAG, "COMANDO: TRADUCI")
                onCommandDetected?.invoke("traduci")
            }
            spokenText.contains("ferma") || spokenText.contains("stop") || spokenText.contains("basta") || spokenText.contains("fermati") -> {
                //Log.d(TAG, "COMANDO: STOP")
                onCommandDetected?.invoke("stop")
            }
            // NUOVO: Comandi navigazione blocchi
            spokenText.contains("prossimo") || spokenText.contains("successivo") || spokenText.contains("avanti") || spokenText.contains("next") -> {
                //Log.d(TAG, "COMANDO: PROSSIMO")
                onCommandDetected?.invoke("prossimo")
            }
            spokenText.contains("precedente") || spokenText.contains("indietro") || spokenText.contains("back") || spokenText.contains("previous") -> {
                //Log.d(TAG, "COMANDO: PRECEDENTE")
                onCommandDetected?.invoke("precedente")
            }
            spokenText.contains("riprendi") || spokenText.contains("continua") || spokenText.contains("resume") -> {
                //Log.d(TAG, "COMANDO: RIPRENDI")
                onCommandDetected?.invoke("riprendi")
            }
            else -> {
                //Log.d(TAG, "Comando non riconosciuto: '$spokenText'")
            }
        }
    }
    //Funzione per l'inizio del riconoscimento vocale
    fun startListening() {
        if (isListening) {
            //Log.d(TAG, "Gia in ascolto, ignoro richiesta")
            return
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "it-IT")
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }

        try {
            speechRecognizer?.startListening(intent)
        } catch (e: Exception) {
            onError?.invoke("Impossibile avviare riconoscimento vocale")
        }
    }

    fun stopListening() {
        speechRecognizer?.stopListening()
        isListening = false
        handler.removeCallbacksAndMessages(null)
    }

    fun setCallbacks(onCommand: (String) -> Unit, onErr: (String) -> Unit = {}) {
        onCommandDetected = onCommand
        onError = onErr
    }

    fun shutdown() {
        stopListening()
        speechRecognizer?.destroy()
        speechRecognizer = null
    }
}
