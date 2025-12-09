package luigi.tirocinio.clarifai.ui.descContinua

import android.app.Application
import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import luigi.tirocinio.clarifai.ml.manager.GeminiManager
import luigi.tirocinio.clarifai.ml.manager.TTSManager
import luigi.tirocinio.clarifai.utils.Constants

class DescrizioneContinuaViewModel(application: Application) : AndroidViewModel(application) {

    private val geminiManager = GeminiManager()
    private val ttsManager = TTSManager(application)

    private val _uiState = MutableLiveData<UiState>()
    val uiState: LiveData<UiState> = _uiState

    private val _description = MutableLiveData<String>()
    val description: LiveData<String> = _description

    // Variabile per tenere traccia di è stata fatta l'ultima analisi
    private var lastAnalysisTime = 0L

    // Classe per i possibili stati dell'interfaccia durante l'analisi
    sealed class UiState {
        object Idle : UiState()
        object Loading : UiState()
        data class Success(val text: String) : UiState()
        data class Error(val message: String) : UiState()
    }

    init {
        _uiState.value = UiState.Idle

        // Configura i callback per il text-to-speech
        ttsManager.setCallbacks(
            onStarted = {
                if (Constants.DEBUG_MODE) {
                    Log.d(Constants.LOG_TAG, "TTS started")
                }
            },
            onCompleted = {
                if (Constants.DEBUG_MODE) {
                    Log.d(Constants.LOG_TAG, "TTS completed")
                }
            }
        )
    }

    fun analyzeFrame(bitmap: Bitmap) {
        val currentTime = System.currentTimeMillis()

        // Utilizza un debounce per evitare troppe richieste in poco tempo
        if (currentTime - lastAnalysisTime < Constants.DESCRIPTION_DEBOUNCE_MS) {
            return
        }

        lastAnalysisTime = currentTime
        _uiState.postValue(UiState.Loading)

        // Richiede a Gemini di descrivere la scena catturata
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


    fun stopTTS() {
        ttsManager.stop()
    }

    // Funzione per liberare le risorse quando il ViewModel viene distrutto
    override fun onCleared() {
        super.onCleared()
        geminiManager.shutdown()
        ttsManager.shutdown()
    }
}
