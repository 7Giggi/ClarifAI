package luigi.tirocinio.clarifai.ui.lettura

import android.app.Application
import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import luigi.tirocinio.clarifai.data.model.TextBlock
import luigi.tirocinio.clarifai.ml.manager.MLKitManager
import luigi.tirocinio.clarifai.ml.manager.TTSManager

class LetturaViewModel(application: Application) : AndroidViewModel(application) {

    //private val TAG = "LetturaViewModel"

    private val mlKitManager = MLKitManager(application)
    private val ttsManager = TTSManager(application)

    private val _textBlocks = MutableLiveData<List<TextBlock>>()
    val textBlocks: LiveData<List<TextBlock>> = _textBlocks

    private val _isProcessing = MutableLiveData<Boolean>()
    val isProcessing: LiveData<Boolean> = _isProcessing

    private val _currentReadingIndex = MutableLiveData<Int>()
    val currentReadingIndex: LiveData<Int> = _currentReadingIndex

    init {
        //Listener per aggiornare indice corrente
        ttsManager.setOnBlockCompletedListener { index ->
            _currentReadingIndex.postValue(index)
        }
    }

    fun analyzeFrame(bitmap: Bitmap, rotationDegrees: Int) {
        _isProcessing.postValue(true)
        _textBlocks.postValue(emptyList())

        //Log.d(TAG, "Analisi frame con rotazione: $rotationDegrees")

        mlKitManager.recognizeText(
            bitmap = bitmap,
            rotationDegrees = rotationDegrees,
            onSuccess = { blocks ->
                _textBlocks.postValue(blocks)
                _isProcessing.postValue(false)

                //Log.d(TAG, "Rilevati ${blocks.size} blocchi di testo")

                if (blocks.isNotEmpty()) {
                    ttsManager.speak("${blocks.size} blocchi di testo rilevati", TTSManager.Priority.HIGH)
                } else {
                    ttsManager.speak("Nessun testo rilevato", TTSManager.Priority.HIGH)
                }
            },
            onFailure = { exception ->
                //Log.e(TAG, "Errore riconoscimento testo", exception)
                _textBlocks.postValue(emptyList())
                _isProcessing.postValue(false)
                ttsManager.speak("Errore riconoscimento testo", TTSManager.Priority.HIGH)
            }
        )
    }

    //Funzione per leggere con il TTS il contenuto di tutti i blocchi,tradotti,attivata tramite comando vocale
    fun readAllText() {
        val blocks = _textBlocks.value

        if (blocks.isNullOrEmpty()) {
            ttsManager.speak("Nessun testo rilevato", TTSManager.Priority.NORMAL)
            return
        }

        //Log.d(TAG, "Inizio lettura sequenziale di ${blocks.size} blocchi con traduzione")

        // Estrai testi dai blocchi
        val texts = blocks.map { it.text }

        // Traduci ogni blocco e poi avvia lettura sequenziale
        translateAllBlocks(texts) { translatedTexts ->
            // Avvia lettura sequenziale con blocchi tradotti
            ttsManager.startReadingBlocks(translatedTexts, startIndex = 0)
            _currentReadingIndex.postValue(0)
        }
    }

    //Funzione per tradurre tutti i blocchi
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
                    //Log.e(TAG, "Errore traduzione blocco, uso originale", exception)
                    translatedTexts.add(text) // Usa originale in caso di errore
                    completedCount++

                    if (completedCount == texts.size) {
                        onComplete(translatedTexts)
                    }
                }
            )
        }
    }

    //Funzione per leggere con il TTS il contenuto di un blocco
    fun readBlock(index: Int) {
        val block = _textBlocks.value?.getOrNull(index)

        if (block == null) {
            //Log.w(TAG, "Blocco $index non trovato")
            ttsManager.speak("Blocco non trovato", TTSManager.Priority.HIGH)
            return
        }

        //Log.d(TAG, "Lettura blocco $index con traduzione")

        // Traduci anche il singolo blocco
        mlKitManager.getTranslationManager().translateToItalian(
            text = block.text,
            onSuccess = { translatedText, detectedLanguage ->
                if (detectedLanguage != "it" && detectedLanguage != "und") {
                    //Log.d(TAG, "Blocco tradotto da ${getLanguageName(detectedLanguage)}")
                }
                ttsManager.speak(translatedText, TTSManager.Priority.NORMAL)
            },
            onFailure = { exception ->
                //Log.e(TAG, "Errore traduzione blocco, leggo originale", exception)
                ttsManager.speak(block.text, TTSManager.Priority.NORMAL)
            }
        )
    }

    //Funzione per pre-caricare i linguaggi più utilizzati
    fun preloadTranslationModels() {
        //Log.d(TAG, "Pre-caricamento modelli traduzione")
        mlKitManager.getTranslationManager().preloadCommonLanguages {
            //Log.d(TAG, "Modelli traduzione pronti")
        }
    }
    //Funzione per smettere di leggere un blocco
    fun stopReading() {
        //Log.d(TAG, "Stop lettura richiesto")
        ttsManager.stopImmediately()
        val currentIndex = ttsManager.getCurrentBlockIndex()
        _currentReadingIndex.postValue(currentIndex)
        //Log.d(TAG, "Fermato al blocco: $currentIndex")
    }
    //Funzione per tornare a leggere da dove abbiamo fermato il TTS
    fun resumeReading() {
        val lastIndex = ttsManager.getLastStoppedIndex()

        if (lastIndex >= 0) {
            //Log.d(TAG, "Riprendo lettura dal blocco: $lastIndex")

            val blocks = _textBlocks.value
            if (!blocks.isNullOrEmpty() && lastIndex < blocks.size) {
                val texts = blocks.map { it.text }
                translateAllBlocks(texts) { translatedTexts ->
                    ttsManager.startReadingBlocks(translatedTexts, startIndex = lastIndex)
                    _currentReadingIndex.postValue(lastIndex)
                }
            }
        } else {
            //Log.w(TAG, "Nessuna posizione salvata per riprendere")
            ttsManager.speak("Nessuna lettura da riprendere", TTSManager.Priority.HIGH)
        }
    }

    //Funzione per leggere il testo successivo
    fun nextBlock() {
        //Log.d(TAG, "Prossimo blocco richiesto")
        val success = ttsManager.nextBlock()

        if (success) {
            _currentReadingIndex.postValue(ttsManager.getCurrentBlockIndex())
        }
    }

    //Funzione per leggere il blocco precedente
    fun previousBlock() {
        //Log.d(TAG, "Blocco precedente richiesto")
        val success = ttsManager.previousBlock()

        if (success) {
            _currentReadingIndex.postValue(ttsManager.getCurrentBlockIndex())
        }
    }

    //Funzione per processare il comando vocale ricevuto
    fun processVoiceCommand(command: String) {
        //Log.d(TAG, "Comando vocale ricevuto: $command")

        val normalizedCommand = command.lowercase().trim()

        when {
            //Stoppare il TTS
            normalizedCommand.contains("stop") ||
                    normalizedCommand.contains("ferma") ||
                    normalizedCommand.contains("fermati") -> {
                stopReading()
                ttsManager.speak("Lettura fermata", TTSManager.Priority.HIGH)
            }
            //Legggere il prossimo blocco
            normalizedCommand.contains("prossimo") ||
                    normalizedCommand.contains("successivo") ||
                    normalizedCommand.contains("avanti") ||
                    normalizedCommand.contains("next") -> {
                nextBlock()
            }
            //Leggere il blocco precedente
            normalizedCommand.contains("precedente") ||
                    normalizedCommand.contains("indietro") ||
                    normalizedCommand.contains("back") ||
                    normalizedCommand.contains("previous") -> {
                previousBlock()
            }

            //Riprendere
            normalizedCommand.contains("riprendi") ||
                    normalizedCommand.contains("continua") ||
                    normalizedCommand.contains("resume") -> {
                resumeReading()
            }
            //Leggere tutti i blocchi
            normalizedCommand.contains("leggi tutto") ||
                    normalizedCommand.contains("inizia lettura") ||
                    normalizedCommand.contains("read all") -> {
                readAllText()
            }

            else -> {
                //Log.d(TAG, "Comando non riconosciuto: $command")
            }
        }
    }

    fun clearBlocks() {
        _textBlocks.postValue(emptyList())
        _currentReadingIndex.postValue(-1)
    }

    // Funzione per ottenere il nome del linguaggio in base al codice
    private fun getLanguageName(code: String): String {
        return when (code) {
            "en" -> "inglese"
            "fr" -> "francese"
            "de" -> "tedesco"
            "es" -> "spagnolo"
            "pt" -> "portoghese"
            "zh" -> "cinese"
            "ja" -> "giapponese"
            "ar" -> "arabo"
            "ru" -> "russo"
            "nl" -> "olandese"
            "it" -> "italiano"
            "und" -> "sconosciuta"
            else -> "sconosciuta"
        }
    }
    //Funzione per liberare le risorse quando il ViewModel viene distrutto
    override fun onCleared() {
        super.onCleared()
        mlKitManager.shutdown()
        ttsManager.shutdown()
    }
}
