package luigi.tirocinio.clarifai.ui.lettura

import android.app.Application
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import luigi.tirocinio.clarifai.data.model.TextBlock
import luigi.tirocinio.clarifai.ml.manager.MLKitManager
import luigi.tirocinio.clarifai.ml.manager.TTSManager

class LetturaViewModel(application: Application) : AndroidViewModel(application) {

    private val mlKitManager = MLKitManager(application)
    private val ttsManager = TTSManager(application)

    private val _textBlocks = MutableLiveData<List<TextBlock>>()
    val textBlocks: LiveData<List<TextBlock>> = _textBlocks

    private val _isProcessing = MutableLiveData<Boolean>()
    val isProcessing: LiveData<Boolean> = _isProcessing

    fun analyzeFrame(bitmap: Bitmap) {
        _isProcessing.postValue(true)

        mlKitManager.recognizeText(
            bitmap = bitmap,
            onSuccess = { blocks ->
                _textBlocks.postValue(blocks)
                _isProcessing.postValue(false)
            },
            onFailure = { exception ->
                _textBlocks.postValue(emptyList())
                _isProcessing.postValue(false)
                ttsManager.speak("Errore riconoscimento testo")
            }
        )
    }

    //Funzione per leggere con il TTS il contenuto di tutti i blocchi, attivata tramite comando vocale
    fun readAllText() {
        val allText = _textBlocks.value?.joinToString("\n") { it.text } ?: ""
        if (allText.isNotBlank()) {
            ttsManager.speak(allText, TTSManager.Priority.NORMAL)
        } else {
            ttsManager.speak("Nessun testo rilevato")
        }
    }

    //Funzione per leggere con il TTS il contenuto di un blocco
    fun readBlock(index: Int) {
        val block = _textBlocks.value?.getOrNull(index)
        if (block != null) {
            ttsManager.speak(block.text, TTSManager.Priority.NORMAL)
        }
    }
    //Funzione per smettere di leggere un blocco
    fun stopReading() {
        ttsManager.stop()
    }
    //Funzione per liberare le risorse quando il ViewModel viene distrutto
    override fun onCleared() {
        super.onCleared()
        mlKitManager.shutdown()
        ttsManager.shutdown()
    }
}
