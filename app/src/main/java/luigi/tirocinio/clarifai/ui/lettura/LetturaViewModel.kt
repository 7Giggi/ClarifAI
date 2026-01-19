package luigi.tirocinio.clarifai.ui.lettura

import android.app.Application
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import luigi.tirocinio.clarifai.data.model.TextBlock
import luigi.tirocinio.clarifai.ml.manager.MLKitManager
import luigi.tirocinio.clarifai.ml.manager.TTSManager

// ViewModel per la modalita Lettura che gestisce il riconoscimento del testo tramite ML Kit, la traduzione automatica in italiano e la lettura tramite Text-to-Speech
// Coordina le operazioni tra MLKitManager per il riconoscimento testo e TTSManager per la sintesi vocale
class LetturaViewModel(application: Application) : AndroidViewModel(application) {

    // Manager per il riconoscimento testo e traduzione tramite ML Kit
    private val mlKitManager = MLKitManager(application)

    // Manager per la sintesi vocale Text-to-Speech
    private val ttsManager = TTSManager(application)

    // Lista dei blocchi di testo riconosciuti dall'immagine
    private val _textBlocks = MutableLiveData<List<TextBlock>>()
    val textBlocks: LiveData<List<TextBlock>> = _textBlocks

    // Indica se e in corso un'operazione di elaborazione
    private val _isProcessing = MutableLiveData<Boolean>()
    val isProcessing: LiveData<Boolean> = _isProcessing

    // Indice del blocco di testo attualmente in lettura
    private val _currentReadingIndex = MutableLiveData<Int>()
    val currentReadingIndex: LiveData<Int> = _currentReadingIndex

    init {
        // Funzione per aggiornare l'indice corrente quando un blocco viene completato
        ttsManager.setOnBlockCompletedListener { index ->
            _currentReadingIndex.postValue(index)
        }
    }

    // Funzione per analizzare un frame catturato dalla fotocamera e riconoscere il testo presente
    fun analyzeFrame(bitmap: Bitmap, rotationDegrees: Int) {
        _isProcessing.postValue(true)
        _textBlocks.postValue(emptyList())
        // Log.d(TAG, "Analisi frame con rotazione: $rotationDegrees")
        mlKitManager.recognizeText(
            bitmap = bitmap,
            rotationDegrees = rotationDegrees,
            onSuccess = { blocks ->
                _textBlocks.postValue(blocks)
                _isProcessing.postValue(false)
                // Log.d(TAG, "Rilevati ${blocks.size} blocchi di testo")
                if (blocks.isNotEmpty()) {
                    ttsManager.speak("${blocks.size} blocchi di testo rilevati", TTSManager.Priority.HIGH)
                } else {
                    ttsManager.speak("Nessun testo rilevato", TTSManager.Priority.HIGH)
                }
            },
            onFailure = { exception ->
                // Log.e(TAG, "Errore riconoscimento testo", exception)
                _textBlocks.postValue(emptyList())
                _isProcessing.postValue(false)
                ttsManager.speak("Errore riconoscimento testo", TTSManager.Priority.HIGH)
            }
        )
    }

    // Funzione per leggere sequenzialmente tutti i blocchi di testo riconosciuti dopo averli tradotti in italiano
    fun readAllText() {
        val blocks = _textBlocks.value
        if (blocks.isNullOrEmpty()) {
            ttsManager.speak("Nessun testo rilevato", TTSManager.Priority.NORMAL)
            return
        }

        // Log.d(TAG, "Inizio lettura sequenziale di ${blocks.size} blocchi con traduzione")
        val texts = blocks.map { it.text }
        translateAllBlocks(texts) { translatedTexts ->
            ttsManager.startReadingBlocks(translatedTexts, startIndex = 0)
            _currentReadingIndex.postValue(0)
        }
    }

    // Funzione per tradurre tutti i blocchi di testo in italiano prima della lettura
    private fun translateAllBlocks(texts: List<String>, onComplete: (List<String>) -> Unit) {
        val translatedTexts = mutableListOf<String>()
        var completedCount = 0
        texts.forEach { text ->
            mlKitManager.getTranslationManager().translateToItalian(
                text = text,
                onSuccess = { translatedText, detectedLanguage ->
                    translatedTexts.add(translatedText)
                    completedCount++
                    if (completedCount == texts.size) {
                        onComplete(translatedTexts)
                    }
                },
                onFailure = { exception ->
                    // Log.e(TAG, "Errore traduzione blocco, uso originale", exception)
                    translatedTexts.add(text)
                    completedCount++
                    if (completedCount == texts.size) {
                        onComplete(translatedTexts)
                    }
                }
            )
        }
    }

    // Funzione per leggere un singolo blocco di testo specificato dall'indice dopo averlo tradotto
    fun readBlock(index: Int) {
        val block = _textBlocks.value?.getOrNull(index)
        if (block == null) {
            // Log.w(TAG, "Blocco $index non trovato")
            ttsManager.speak("Blocco non trovato", TTSManager.Priority.HIGH)
            return
        }

        // Log.d(TAG, "Lettura blocco $index con traduzione")
        mlKitManager.getTranslationManager().translateToItalian(
            text = block.text,
            onSuccess = { translatedText, detectedLanguage ->
                if (detectedLanguage != "it" && detectedLanguage != "und") {
                    // Log.d(TAG, "Blocco tradotto da ${getLanguageName(detectedLanguage)}")
                }
                ttsManager.speak(translatedText, TTSManager.Priority.NORMAL)
            },
            onFailure = { exception ->
                // Log.e(TAG, "Errore traduzione blocco, leggo originale", exception)
                ttsManager.speak(block.text, TTSManager.Priority.NORMAL)
            }
        )
    }

    // Funzione per pre-caricare i modelli di traduzione delle lingue piu comuni per migliorare le prestazioni
    fun preloadTranslationModels() {
        // Log.d(TAG, "Pre-caricamento modelli traduzione")
        mlKitManager.getTranslationManager().preloadCommonLanguages {
            // Log.d(TAG, "Modelli traduzione pronti")
        }
    }

    // Funzione per interrompere immediatamente la lettura vocale in corso
    fun stopReading() {
        // Log.d(TAG, "Stop lettura richiesto")
        ttsManager.stopImmediately()
        val currentIndex = ttsManager.getCurrentBlockIndex()
        _currentReadingIndex.postValue(currentIndex)
        // Log.d(TAG, "Fermato al blocco: $currentIndex")
    }

    // Funzione per riprendere la lettura dall'ultimo blocco interrotto
    fun resumeReading() {
        val lastIndex = ttsManager.getLastStoppedIndex()
        if (lastIndex >= 0) {
            // Log.d(TAG, "Riprendo lettura dal blocco: $lastIndex")
            val blocks = _textBlocks.value
            if (!blocks.isNullOrEmpty() && lastIndex < blocks.size) {
                val texts = blocks.map { it.text }
                translateAllBlocks(texts) { translatedTexts ->
                    ttsManager.startReadingBlocks(translatedTexts, startIndex = lastIndex)
                    _currentReadingIndex.postValue(lastIndex)
                }
            }
        } else {
            // Log.w(TAG, "Nessuna posizione salvata per riprendere")
            ttsManager.speak("Nessuna lettura da riprendere", TTSManager.Priority.HIGH)
        }
    }

    // Funzione per passare alla lettura del blocco di testo successivo
    fun nextBlock() {
        // Log.d(TAG, "Prossimo blocco richiesto")
        val success = ttsManager.nextBlock()
        if (success) {
            _currentReadingIndex.postValue(ttsManager.getCurrentBlockIndex())
        }
    }

    // Funzione per tornare alla lettura del blocco di testo precedente
    fun previousBlock() {
        // Log.d(TAG, "Blocco precedente richiesto")
        val success = ttsManager.previousBlock()
        if (success) {
            _currentReadingIndex.postValue(ttsManager.getCurrentBlockIndex())
        }
    }

    // Funzione per elaborare i comandi vocali ricevuti dall'utente e eseguire l'azione corrispondente
    fun processVoiceCommand(command: String) {
        // Log.d(TAG, "Comando vocale ricevuto: $command")
        val normalizedCommand = command.lowercase().trim()
        when {
            normalizedCommand.contains("stop") ||
                    normalizedCommand.contains("ferma") ||
                    normalizedCommand.contains("fermati") -> {
                stopReading()
                ttsManager.speak("Lettura fermata", TTSManager.Priority.HIGH)
            }
            normalizedCommand.contains("prossimo") ||
                    normalizedCommand.contains("successivo") ||
                    normalizedCommand.contains("avanti") ||
                    normalizedCommand.contains("next") -> {
                nextBlock()
            }
            normalizedCommand.contains("precedente") ||
                    normalizedCommand.contains("indietro") ||
                    normalizedCommand.contains("back") ||
                    normalizedCommand.contains("previous") -> {
                previousBlock()
            }
            normalizedCommand.contains("riprendi") ||
                    normalizedCommand.contains("continua") ||
                    normalizedCommand.contains("resume") -> {
                resumeReading()
            }
            normalizedCommand.contains("leggi tutto") ||
                    normalizedCommand.contains("inizia lettura") ||
                    normalizedCommand.contains("read all") -> {
                readAllText()
            }
            else -> {
                // Log.d(TAG, "Comando non riconosciuto: $command")
            }
        }
    }
    // Funzione per leggere il messaggio di aiuto tramite TTS
    fun speakHelpMessage(message: String) {
        ttsManager.speak(message)
    }

    // Funzione per rilasciare tutte le risorse quando il ViewModel viene distrutto
    override fun onCleared() {
        super.onCleared()
        mlKitManager.shutdown()
        ttsManager.shutdown()
    }
}
