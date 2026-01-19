package luigi.tirocinio.clarifai.ui.descrizione

import android.app.Application
import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import luigi.tirocinio.clarifai.ml.manager.GeminiManager
import luigi.tirocinio.clarifai.ml.manager.TTSManager
import luigi.tirocinio.clarifai.utils.Constants

// ViewModel per la modalita Descrizione che gestisce le chiamate API a Gemini e la lettura TTS
// Coordina l'invio di immagini a Gemini AI per ottenere descrizioni dell'ambiente
// e la successiva lettura tramite Text-to-Speech
class DescrizioneViewModel(application: Application) : AndroidViewModel(application) {

    // Manager per le chiamate API a Gemini AI
    private val geminiManager = GeminiManager()

    // Manager per la sintesi vocale Text-to-Speech
    private val ttsManager = TTSManager(application)

    // Stato corrente dell'interfaccia utente durante l'analisi
    private val _uiState = MutableLiveData<UiState>()
    val uiState: LiveData<UiState> = _uiState

    // Ultima descrizione ricevuta da Gemini
    private val _description = MutableLiveData<String>()
    val description: LiveData<String> = _description

    // Timestamp dell'ultima analisi per implementare il debounce
    private var lastAnalysisTime = 0L

    // Sealed class che definisce i possibili stati dell'interfaccia durante l'analisi
    sealed class UiState {
        object Idle : UiState()
        object Loading : UiState()
        data class Success(val text: String) : UiState()
        data class Error(val message: String) : UiState()
    }

    init {
        _uiState.value = UiState.Idle
        ttsManager.setCallbacks(
            onStarted = {
                // if (Constants.DEBUG_MODE) {
                //     Log.d(Constants.LOG_TAG, "TTS started")
                // }
            },
            onCompleted = {
                // if (Constants.DEBUG_MODE) {
                //     Log.d(Constants.LOG_TAG, "TTS completed")
                // }
            }
        )
    }
    // Funzione per leggere un messaggio di aiuto tramite TTS
    fun speakHelpMessage(message: String) {
        ttsManager.speak(message)
    }

    // Funzione per analizzare un frame catturato inviandolo a Gemini per ottenere una descrizione vocale
    fun analyzeFrame(bitmap: Bitmap) {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastAnalysisTime < Constants.DESCRIPTION_DEBOUNCE_MS) {
            return
        }

        lastAnalysisTime = currentTime
        _uiState.postValue(UiState.Loading)
        geminiManager.describeSceneWithRetry(
            bitmap = bitmap,
            callback = object : GeminiManager.GeminiCallback {
                override fun onSuccess(description: String) {
                    _description.postValue(description)
                    _uiState.postValue(UiState.Success(description))
                    ttsManager.speak(description, TTSManager.Priority.NORMAL)
                }

                override fun onError(error: String) {
                    _uiState.postValue(UiState.Error(error))
                    ttsManager.speak("Impossibile analizzare l'immagine", TTSManager.Priority.HIGH)
                }

                override fun onLoading() {
                    _uiState.postValue(UiState.Loading)
                }
            }
        )
    }

    // Funzione per fermare immediatamente la lettura TTS in corso
    fun stopTTS() {
        ttsManager.stop()
    }

    // Funzione per rilasciare tutte le risorse quando il ViewModel viene distrutto
    override fun onCleared() {
        super.onCleared()
        geminiManager.shutdown()
        ttsManager.shutdown()
    }
}
