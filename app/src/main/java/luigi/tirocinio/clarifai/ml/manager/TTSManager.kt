package luigi.tirocinio.clarifai.ml.manager

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import luigi.tirocinio.clarifai.utils.Constants
import java.util.Locale
import java.util.Queue
import java.util.LinkedList

//Classe per le gestione e configurazione del Text-to-Speech (TTS)
class TTSManager(private val context: Context) {
    private var tts: TextToSpeech? = null
    private var TAG = "TTSManager"
    private var isInitialized = false
    private val messageQueue: Queue<TTSMessage> = LinkedList()
    private var isSpeaking = false
    private var lastStoppedIndex = -1
    //Variabili per la gestione lettura sequenziale blocchi
    private var currentBlocks: List<String> = emptyList()
    private var currentBlockIndex = 0
    private var isReadingSequence = false

    // Callback per eventi TTS
    private var onSpeechStarted: (() -> Unit)? = null
    private var onSpeechCompleted: (() -> Unit)? = null
    private var onSpeechError: ((String) -> Unit)? = null
    private var onBlockCompleted: ((Int) -> Unit)? = null

    // Data class usata per memorizzare messaggi TTS
    private data class TTSMessage(
        val text: String,
        val priority: Priority = Priority.NORMAL, //La priorità determina l'ordine di lettura
        val isUrgent: Boolean = false,
        val utteranceId: String = System.currentTimeMillis().toString()
    )

    // Enumerazione usata per le priorità dei messaggi
    enum class Priority {
        LOW,
        NORMAL,
        HIGH
    }

    init {
        initializeTTS()
    }

    //Inizializzazione TTS
    private fun initializeTTS() {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val result = tts?.setLanguage(Locale.ITALIAN)

                if (result == TextToSpeech.LANG_MISSING_DATA ||
                    result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    //Log.e(Constants.LOG_TAG, "TTS: Lingua italiana non supportata")
                    // Usa l'inglese in caso di lingua italiana non supportata
                    tts?.setLanguage(Locale.US)
                }

                // Configura parametri TTS
                tts?.setSpeechRate(Constants.TTS_SPEECH_RATE)
                tts?.setPitch(Constants.TTS_PITCH)

                // Imposta listener per monitorare stato
                setupUtteranceListener()

                isInitialized = true
                //Log.d(Constants.LOG_TAG, "TTS inizializzato con successo")

                // Processa messaggi in coda (se ce ne sono)
                processQueue()

            } else {
                //Log.e(Constants.LOG_TAG, "TTS: Inizializzazione fallita")
                onSpeechError?.invoke("Impossibile inizializzare Text-to-Speech")
            }
        }
    }

    // Funzione per la configurazione del listener degli eventi TTS
    private fun setupUtteranceListener() {
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                isSpeaking = true
                onSpeechStarted?.invoke()
                //Log.d(Constants.LOG_TAG, "TTS: Inizio lettura - ID: $utteranceId")
            }

            override fun onDone(utteranceId: String?) {
                isSpeaking = false
                onSpeechCompleted?.invoke()
                //Log.d(Constants.LOG_TAG, "TTS: Lettura completata - ID: $utteranceId")

                if (isReadingSequence && currentBlockIndex < currentBlocks.size - 1) {
                    currentBlockIndex++
                    speakCurrentBlock()
                    onBlockCompleted?.invoke(currentBlockIndex)
                } else {
                    isReadingSequence = false
                    processQueue()
                }
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                isSpeaking = false
                val errorMsg = "Errore durante la lettura TTS - ID: $utteranceId"
                //Log.e(Constants.LOG_TAG, errorMsg)
                onSpeechError?.invoke(errorMsg)

                //Ferma sequenza in caso di errore
                isReadingSequence = false
                processQueue()
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                isSpeaking = false
                val errorMsg = "Errore TTS (code: $errorCode) - ID: $utteranceId"
                //Log.e(Constants.LOG_TAG, errorMsg)
                onSpeechError?.invoke(errorMsg)

                isReadingSequence = false
                processQueue()
            }
        })
    }

    //Funzione per leggere un testo subito anche se si sta leggendo un altro messaggio
    fun speakNow(text: String) {
        if (!isInitialized) {
            //Log.w(Constants.LOG_TAG, "TTS non inizializzato, messaggio accodato")
            messageQueue.offer(TTSMessage(text, Priority.HIGH))
            return
        }

        val utteranceId = "urgent_${System.currentTimeMillis()}"
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)

        if (Constants.DEBUG_MODE) {
            //Log.d(Constants.LOG_TAG, "TTS speakNow: $text")
        }
    }

    //Funzione per leggere un messaggio
    fun speak(text: String, priority: Priority = Priority.NORMAL) {
        if (!isInitialized) {
            //Log.w(Constants.LOG_TAG, "TTS non inizializzato, messaggio accodato")
            messageQueue.offer(TTSMessage(text, priority))
            return
        }

        if (isSpeaking) {
            // Accoda il messaggio se TTS sta già parlando
            messageQueue.offer(TTSMessage(text, priority))
        } else {
            // Leggi immediatamente se TTS è libero
            val utteranceId = "msg_${System.currentTimeMillis()}"
            tts?.speak(text, TextToSpeech.QUEUE_ADD, null, utteranceId)

            if (Constants.DEBUG_MODE) {
                //Log.d(Constants.LOG_TAG, "TTS speak: $text")
            }
        }
    }

    //Funzione per processare i messaggi in coda
    private fun processQueue() {
        if (!isInitialized || isSpeaking || messageQueue.isEmpty()) {
            return
        }

        // Ordina coda per priorità
        val sortedQueue = messageQueue.sortedByDescending { it.priority }
        messageQueue.clear()
        messageQueue.addAll(sortedQueue)

        // Prendi il prossimo messaggio
        val nextMessage = messageQueue.poll()
        nextMessage?.let {
            tts?.speak(it.text, TextToSpeech.QUEUE_ADD, null, it.utteranceId)
        }
    }

    //Funzione per avviare lettura sequenziale dei blocchi
    fun startReadingBlocks(blocks: List<String>, startIndex: Int = 0) {
        if (!isInitialized || blocks.isEmpty()) {
           //Log.w(Constants.LOG_TAG, "TTS: Non posso leggere blocchi")
            return
        }
        stopImmediately()

        currentBlocks = blocks
        currentBlockIndex = startIndex.coerceIn(0, blocks.size - 1)
        isReadingSequence = true

        //Log.d(Constants.LOG_TAG, "TTS: Inizio lettura sequenza: ${blocks.size} blocchi, partendo da $currentBlockIndex")

        speakCurrentBlock()
    }

    // Funzione per leggere il blocco corrente
    private fun speakCurrentBlock() {
        if (currentBlockIndex < currentBlocks.size) {
            val text = currentBlocks[currentBlockIndex]
            val utteranceId = "block_${currentBlockIndex}_${System.currentTimeMillis()}"

            //Log.d(Constants.LOG_TAG, "TTS: Leggo blocco $currentBlockIndex: $text")

            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
        }
    }

    //Funzione per fermare immediatamente il TTS
    fun stopImmediately() {
        //Log.d(TAG, "TTS: Stop immediato richiesto")

        //Salva la posizione corrente prima di fermare
        if (isReadingSequence) {
            lastStoppedIndex = currentBlockIndex
            //Log.d(TAG, "TTS: Salvato indice di stop: $lastStoppedIndex")
        }

        tts?.stop()
        messageQueue.clear()
        isReadingSequence = false
        isSpeaking = false
    }

    //Funzione per lo stop del TTS
    fun stop() {
        stopImmediately()
    }

    //Funzione per leggere il blocco successivo
    fun nextBlock(): Boolean {
        //Se ha un blocco corrente salvato
        if (!isReadingSequence && lastStoppedIndex >= 0 && currentBlocks.isNotEmpty()) {
            currentBlockIndex = lastStoppedIndex

            if (currentBlockIndex < currentBlocks.size - 1) {
                currentBlockIndex++
                //Log.d(TAG, "TTS: Riprendo lettura dal blocco successivo: $currentBlockIndex")

                isReadingSequence = true
                lastStoppedIndex = -1  // Reset
                speakCurrentBlock()
                onBlockCompleted?.invoke(currentBlockIndex)
                return true
            } else {
                //Log.d(TAG, "TTS: Già all'ultimo blocco")
                speak("Ultimo blocco raggiunto", Priority.HIGH)
                return false
            }
        }

        // Comportamento originale se sta già leggendo
        if (!isReadingSequence || currentBlocks.isEmpty()) {
            //Log.w(TAG, "TTS: Nessuna sequenza attiva per nextBlock")
            speak("Nessuna lettura in corso", Priority.HIGH)
            return false
        }

        if (currentBlockIndex < currentBlocks.size - 1) {
            currentBlockIndex++
            //Log.d(TAG, "TTS: Passo al blocco successivo: $currentBlockIndex")

            // Stop e leggi prossimo
            tts?.stop()
            speakCurrentBlock()
            onBlockCompleted?.invoke(currentBlockIndex)
            return true
        } else {
            //Log.d(TAG, "TTS: Già all'ultimo blocco")
            speak("Ultimo blocco raggiunto", Priority.HIGH)
            return false
        }
    }

    //Funzione per leggere il blocco precedente
    fun previousBlock(): Boolean {
        if (!isReadingSequence && lastStoppedIndex >= 0 && currentBlocks.isNotEmpty()) {
            currentBlockIndex = lastStoppedIndex

            if (currentBlockIndex > 0) {
                currentBlockIndex--
                //Log.d(TAG, "TTS: Riprendo lettura dal blocco precedente: $currentBlockIndex")

                isReadingSequence = true
                lastStoppedIndex = -1  // Reset
                speakCurrentBlock()
                onBlockCompleted?.invoke(currentBlockIndex)
                return true
            } else {
                //Log.d(TAG, "TTS: Già al primo blocco")
                speak("Primo blocco raggiunto", Priority.HIGH)
                return false
            }
        }

        if (!isReadingSequence || currentBlocks.isEmpty()) {
            //Log.w(TAG, "TTS: Nessuna sequenza attiva per previousBlock")
            speak("Nessuna lettura in corso", Priority.HIGH)
            return false
        }

        if (currentBlockIndex > 0) {
            currentBlockIndex--
            //Log.d(TAG, "TTS: Torno al blocco precedente: $currentBlockIndex")

            // Stop e leggi precedente
            tts?.stop()
            speakCurrentBlock()
            onBlockCompleted?.invoke(currentBlockIndex)
            return true
        } else {
            //Log.d(TAG, "TTS: Già al primo blocco")
            speak("Primo blocco raggiunto", Priority.HIGH)
            return false
        }
    }
    fun getCurrentBlockIndex(): Int = currentBlockIndex


    fun isReadingSequence(): Boolean = isReadingSequence


    fun setOnBlockCompletedListener(listener: (Int) -> Unit) {
        onBlockCompleted = listener
    }

    fun getLastStoppedIndex(): Int = lastStoppedIndex

    //Funzione per capire se il TTS sta leggendo un messaggio
    fun isSpeaking(): Boolean {
        return isSpeaking
    }

    //Funzione per cambiare la velocità di lettura
    fun setSpeechRate(rate: Float) {
        tts?.setSpeechRate(rate)
    }

    //Funzione per cambiare la tonalità della lettura
    fun setPitch(pitch: Float) {
        tts?.setPitch(pitch)
    }

    //Funzione per cambiare la lingua
    fun setLanguage(locale: Locale): Boolean {
        val result = tts?.setLanguage(locale)
        return result != TextToSpeech.LANG_MISSING_DATA &&
                result != TextToSpeech.LANG_NOT_SUPPORTED
    }

    //Funzione per mostrare le lingue supportate
    fun getAvailableLanguages(): Set<Locale>? {
        return tts?.availableLanguages
    }

    //Funzione per impostare i callback degli eventi TTS
    fun setCallbacks(
        onStarted: (() -> Unit)? = null,
        onCompleted: (() -> Unit)? = null,
        onError: ((String) -> Unit)? = null
    ) {
        this.onSpeechStarted = onStarted
        this.onSpeechCompleted = onCompleted
        this.onSpeechError = onError
    }

    //Funzione per rilasciare le risorse TTS
    fun shutdown() {
        stop()
        tts?.shutdown()
        tts = null
        isInitialized = false

        //Log.d(Constants.LOG_TAG, "TTS: Risorse rilasciate")
    }

    //Funzione per capire se il TTS è inizializzato
    fun isReady(): Boolean {
        return isInitialized && tts != null
    }
}
