package luigi.tirocinio.clarifai.ml.manager

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import luigi.tirocinio.clarifai.utils.Constants
import java.util.Locale
import java.util.Queue
import java.util.LinkedList

// Classe per la gestione del Text-to-Speech utilizzato per leggere testi e descrizioni
// Gestisce code di messaggi con priorita, lettura sequenziale di blocchi di testo
// e callback per monitorare lo stato della sintesi vocale
class TTSManager(private val context: Context) {

    // Istanza del TTS di android
    private var tts: TextToSpeech? = null

    //private var TAG = "TTSManager"

    // Flag che indica se il TTS e stato inizializzato correttamente
    private var isInitialized = false

    // Coda per gestire messaggi in attesa di essere letti
    private val messageQueue: Queue<TTSMessage> = LinkedList()

    // Flag che indica se il TTS sta attualmente leggendo
    private var isSpeaking = false

    // Indice dell'ultimo blocco dove la lettura e stata fermata
    private var lastStoppedIndex = -1

    // Lista dei blocchi di testo da leggere in sequenza
    private var currentBlocks: List<String> = emptyList()

    // Indice del blocco attualmente in lettura
    private var currentBlockIndex = 0

    // Flag che indica se e in corso una lettura sequenziale
    private var isReadingSequence = false

    // Callback invocato quando inizia la lettura
    private var onSpeechStarted: (() -> Unit)? = null

    // Callback invocato quando la lettura viene completata
    private var onSpeechCompleted: (() -> Unit)? = null

    // Callback invocato in caso di errore durante la lettura
    private var onSpeechError: ((String) -> Unit)? = null

    // Callback invocato quando un blocco di testo viene completato
    private var onBlockCompleted: ((Int) -> Unit)? = null

    // Data class per rappresentare un messaggio TTS con priorita
    private data class TTSMessage(
        val text: String,
        val priority: Priority = Priority.NORMAL,
        val isUrgent: Boolean = false,
        val utteranceId: String = System.currentTimeMillis().toString()
    )

    // Enum per definire i livelli di priorita dei messaggi
    enum class Priority {
        LOW,
        NORMAL,
        HIGH
    }

    init {
        initializeTTS()
    }

    // Funzione per inizializzare il motore TTS con lingua italiana e parametri da Constants
    private fun initializeTTS() {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val result = tts?.setLanguage(Locale.ITALIAN)
                if (result == TextToSpeech.LANG_MISSING_DATA ||
                    result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    // Log.e(Constants.LOG_TAG, "TTS: Lingua italiana non supportata")
                    tts?.setLanguage(Locale.US)
                }

                tts?.setSpeechRate(Constants.TTS_SPEECH_RATE)
                tts?.setPitch(Constants.TTS_PITCH)
                setupUtteranceListener()
                isInitialized = true
                // Log.d(Constants.LOG_TAG, "TTS inizializzato con successo")
                processQueue()
            } else {
                // Log.e(Constants.LOG_TAG, "TTS: Inizializzazione fallita")
                onSpeechError?.invoke("Impossibile inizializzare Text-to-Speech")
            }
        }
    }

    // Funzione per configurare il listener che monitora gli eventi di inizio, completamento ed errore
    private fun setupUtteranceListener() {
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                isSpeaking = true
                onSpeechStarted?.invoke()
                // Log.d(Constants.LOG_TAG, "TTS: Inizio lettura - ID: \$utteranceId")
            }

            // Funzione invocata automaticamente quando il TTS completa la lettura
            override fun onDone(utteranceId: String?) {
                isSpeaking = false
                // Notifica tramite callback che la lettura e completata
                onSpeechCompleted?.invoke()
                // Log.d(Constants.LOG_TAG, "TTS: Lettura completata - ID: $utteranceId")
                // Verifica se siamo in modalita lettura sequenziale e se ci sono ancora blocchi da leggere
                if (isReadingSequence && currentBlockIndex < currentBlocks.size - 1) {
                    // Passa al blocco successivo della sequenza
                    currentBlockIndex++
                    // Avvia la lettura del nuovo blocco corrente
                    speakCurrentBlock()
                    // Notifica che un blocco e stato completato e si e passati al successivo
                    onBlockCompleted?.invoke(currentBlockIndex)
                } else {
                    isReadingSequence = false
                    // Processa eventuali messaggi in coda con priorita
                    processQueue()
                }
            }


            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                isSpeaking = false
                val errorMsg = "Errore durante la lettura TTS - ID: \$utteranceId"
                // Log.e(Constants.LOG_TAG, errorMsg)
                onSpeechError?.invoke(errorMsg)
                isReadingSequence = false
                processQueue()
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                isSpeaking = false
                val errorMsg = "Errore TTS (code: \$errorCode) - ID: \$utteranceId"
                // Log.e(Constants.LOG_TAG, errorMsg)
                onSpeechError?.invoke(errorMsg)
                isReadingSequence = false
                processQueue()
            }
        })
    }

    fun speak(text: String, priority: Priority = Priority.NORMAL) {
        if (!isInitialized) {
            // Log.w(Constants.LOG_TAG, "TTS non inizializzato, messaggio accodato")
            messageQueue.offer(TTSMessage(text, priority))
            return
        }

        if (isSpeaking) {
            messageQueue.offer(TTSMessage(text, priority))
        } else {
            val utteranceId = "msg_\${System.currentTimeMillis()}"
            tts?.speak(text, TextToSpeech.QUEUE_ADD, null, utteranceId)
            // if (Constants.DEBUG_MODE) {
            //     Log.d(Constants.LOG_TAG, "TTS speak: \$text")
            // }
        }
    }

    // Funzione per processare la coda di messaggi ordinandoli per priorita
    private fun processQueue() {
        if (!isInitialized || isSpeaking || messageQueue.isEmpty()) {
            return
        }

        val sortedQueue = messageQueue.sortedByDescending { it.priority }
        messageQueue.clear()
        messageQueue.addAll(sortedQueue)
        val nextMessage = messageQueue.poll()
        nextMessage?.let {
            tts?.speak(it.text, TextToSpeech.QUEUE_ADD, null, it.utteranceId)
        }
    }

    // Funzione per avviare la lettura sequenziale di una lista di blocchi di testo
    fun startReadingBlocks(blocks: List<String>, startIndex: Int = 0) {
        if (!isInitialized || blocks.isEmpty()) {
            // Log.w(Constants.LOG_TAG, "TTS: Non posso leggere blocchi")
            return
        }

        stopImmediately()
        currentBlocks = blocks
        currentBlockIndex = startIndex.coerceIn(0, blocks.size - 1)
        isReadingSequence = true
        // Log.d(Constants.LOG_TAG, "TTS: Inizio lettura sequenza: \${blocks.size} blocchi, partendo da \$currentBlockIndex")
        speakCurrentBlock()
    }

    // Funzione per leggere il blocco di testo corrente nella sequenza
    private fun speakCurrentBlock() {
        if (currentBlockIndex < currentBlocks.size) {
            val text = currentBlocks[currentBlockIndex]
            val utteranceId = "block_\${currentBlockIndex}_\${System.currentTimeMillis()}"
            // Log.d(Constants.LOG_TAG, "TTS: Leggo blocco \$currentBlockIndex: \$text")
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
        }
    }

    // Funzione per fermare immediatamente la lettura salvando la posizione corrente
    fun stopImmediately() {
        // Log.d(TAG, "TTS: Stop immediato richiesto")
        if (isReadingSequence) {
            lastStoppedIndex = currentBlockIndex
            // Log.d(TAG, "TTS: Salvato indice di stop: \$lastStoppedIndex")
        }

        tts?.stop()
        messageQueue.clear()
        isReadingSequence = false
        isSpeaking = false
    }

    // Funzione per fermare la lettura TTS
    fun stop() {
        stopImmediately()
    }

    // Funzione per passare al blocco successivo nella sequenza di lettura
    fun nextBlock(): Boolean {
        if (!isReadingSequence && lastStoppedIndex >= 0 && currentBlocks.isNotEmpty()) {
            currentBlockIndex = lastStoppedIndex
            if (currentBlockIndex < currentBlocks.size - 1) {
                currentBlockIndex++
                // Log.d(TAG, "TTS: Riprendo lettura dal blocco successivo: \$currentBlockIndex")
                isReadingSequence = true
                lastStoppedIndex = -1
                speakCurrentBlock()
                onBlockCompleted?.invoke(currentBlockIndex)
                return true
            } else {
                // Log.d(TAG, "TTS: Gia all'ultimo blocco")
                speak("Ultimo blocco raggiunto", Priority.HIGH)
                return false
            }
        }

        if (!isReadingSequence || currentBlocks.isEmpty()) {
            // Log.w(TAG, "TTS: Nessuna sequenza attiva per nextBlock")
            speak("Nessuna lettura in corso", Priority.HIGH)
            return false
        }

        if (currentBlockIndex < currentBlocks.size - 1) {
            currentBlockIndex++
            // Log.d(TAG, "TTS: Passo al blocco successivo: \$currentBlockIndex")
            tts?.stop()
            speakCurrentBlock()
            onBlockCompleted?.invoke(currentBlockIndex)
            return true
        } else {
            // Log.d(TAG, "TTS: Gia all'ultimo blocco")
            speak("Ultimo blocco raggiunto", Priority.HIGH)
            return false
        }
    }

    // Funzione per tornare al blocco precedente nella sequenza di lettura
    fun previousBlock(): Boolean {
        if (!isReadingSequence && lastStoppedIndex >= 0 && currentBlocks.isNotEmpty()) {
            currentBlockIndex = lastStoppedIndex
            if (currentBlockIndex > 0) {
                currentBlockIndex--
                // Log.d(TAG, "TTS: Riprendo lettura dal blocco precedente: \$currentBlockIndex")
                isReadingSequence = true
                lastStoppedIndex = -1
                speakCurrentBlock()
                onBlockCompleted?.invoke(currentBlockIndex)
                return true
            } else {
                // Log.d(TAG, "TTS: Gia al primo blocco")
                speak("Primo blocco raggiunto", Priority.HIGH)
                return false
            }
        }

        if (!isReadingSequence || currentBlocks.isEmpty()) {
            // Log.w(TAG, "TTS: Nessuna sequenza attiva per previousBlock")
            speak("Nessuna lettura in corso", Priority.HIGH)
            return false
        }

        if (currentBlockIndex > 0) {
            currentBlockIndex--
            // Log.d(TAG, "TTS: Torno al blocco precedente: \$currentBlockIndex")
            tts?.stop()
            speakCurrentBlock()
            onBlockCompleted?.invoke(currentBlockIndex)
            return true
        } else {
            // Log.d(TAG, "TTS: Gia al primo blocco")
            speak("Primo blocco raggiunto", Priority.HIGH)
            return false
        }
    }

    // Funzione per ottenere l'indice del blocco attualmente in lettura
    fun getCurrentBlockIndex(): Int = currentBlockIndex

    fun setOnBlockCompletedListener(listener: (Int) -> Unit) {
        onBlockCompleted = listener
    }

    // Funzione per ottenere l'indice dell'ultimo blocco dove la lettura e stata fermata
    fun getLastStoppedIndex(): Int = lastStoppedIndex

    // Funzione per impostare i callback personalizzati che verranno invocati durante gli eventi TTS
    // Permette al chiamante di registrare funzioni da eseguire quando il TTS inizia, completa o incontra errori
    fun setCallbacks(
        // Funzione invocata quando il TTS inizia a leggere un testo
        onStarted: (() -> Unit)? = null,

        // Funzione invocata quando il TTS termina di leggere un testo
        onCompleted: (() -> Unit)? = null,

        // Funzione invocata in caso di errore, riceve un messaggio di errore come parametro
        onError: ((String) -> Unit)? = null
    ) {
        // Assegna i callback ricevuti alle variabili di classe per uso interno
        this.onSpeechStarted = onStarted
        this.onSpeechCompleted = onCompleted
        this.onSpeechError = onError
    }


    // Funzione per rilasciare tutte le risorse TTS
    fun shutdown() {
        stop()
        tts?.shutdown()
        tts = null
        isInitialized = false
        // Log.d(Constants.LOG_TAG, "TTS: Risorse rilasciate")
    }

}