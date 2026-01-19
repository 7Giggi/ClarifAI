package luigi.tirocinio.clarifai.ui.ostacoli

import android.app.Application
import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import luigi.tirocinio.clarifai.data.model.ZoneInfo
import luigi.tirocinio.clarifai.ml.manager.AudioFeedbackManager
import luigi.tirocinio.clarifai.ml.manager.DepthAnythingManager
import luigi.tirocinio.clarifai.ml.manager.ObstacleAnalyzer

// ViewModel per la modalita Ostacoli che coordina il rilevamento di profondita tramite DepthAnythingV2
// Gestisce l'analisi dei frame, il calcolo delle distanze, lo smoothing dei risultati e il feedback audio
// Utilizza coroutine per elaborare i frame in background senza bloccare l'interfaccia utente
class OstacoliViewModel(application: Application) : AndroidViewModel(application) {

    //private val TAG = "OstacoliViewModel"

    // Manager per l'inferenza del modello DepthAnythingV2
    private val depthManager = DepthAnythingManager(application)

    // Analizzatore che converte la mappa di profondita in informazioni su zone e ostacoli
    private val obstacleAnalyzer = ObstacleAnalyzer()

    // Manager per il feedback audio e vibrazione in base al pericolo rilevato
    private val audioManager = AudioFeedbackManager(application)

    // Informazioni sulla zona attualmente rilevata con distanza minima e livello di pericolo
    private val _currentZone = MutableLiveData<ZoneInfo?>()
    val currentZone: LiveData<ZoneInfo?> = _currentZone

    // Indica se e in corso l'elaborazione di un frame
    private val _isProcessing = MutableLiveData<Boolean>(false)
    val isProcessing: LiveData<Boolean> = _isProcessing

    // Indica se il rilevamento ostacoli e attivo
    private val _isDetectionActive = MutableLiveData<Boolean>(false)
    val isDetectionActive: LiveData<Boolean> = _isDetectionActive

    // Messaggi di errore da mostrare all'utente
    private val _errorMessage = MutableLiveData<String?>()
    val errorMessage: LiveData<String?> = _errorMessage

    // Lista usata per tenere in memoria le distanze per lo smoothing
    private val distanceHistory = mutableListOf<Float>()

    // Numero di campioni da tenere in memoria per lo smoothing
    private val historySize = 3

    // Contatore per saltare frame
    private var frameCounter = 0

    // Numero di frame da saltare tra un'elaborazione e l'altra
    private val frameSkip = 3

    // Contatore dei frame elaborati per calcolo FPS
    //private var processedFrames = 0

    // Timestamp dell'ultimo calcolo FPS
    //private var lastFpsTime = System.currentTimeMillis()

    init {
        // Funzione per verificare che il modello sia stato caricato correttamente
        if (!depthManager.isReady()) {
            _errorMessage.postValue("Errore caricamento modello depth estimation")
        }
    }

    // Funzione per elaborare un frame della fotocamera calcolando la mappa di profondita
    fun processFrameWithDepthMap(bitmap: Bitmap, onDepthMapReady: (Array<FloatArray>?) -> Unit) {
        frameCounter++
        if (frameCounter % (frameSkip + 1) != 0) {
            onDepthMapReady(null)
            return
        }

        if (_isProcessing.value == true || _isDetectionActive.value == false) {
            onDepthMapReady(null)
            return
        }

        viewModelScope.launch(Dispatchers.Default) {
            _isProcessing.postValue(true)
            try {
                //val vmStart = System.currentTimeMillis()

                val depthMap = depthManager.estimateDepth(bitmap)
                if (depthMap == null) {
                    onDepthMapReady(null)
                    _isProcessing.postValue(false)
                    return@launch
                }

                //val analysisStart = System.currentTimeMillis()
                val zoneInfo = obstacleAnalyzer.analyzeDepthMap(depthMap)
                //val analysisTime = System.currentTimeMillis() - analysisStart

                if (zoneInfo != null) {
                    //val smoothStart = System.currentTimeMillis()
                    distanceHistory.add(zoneInfo.minDistance)
                    if (distanceHistory.size > historySize) {
                        distanceHistory.removeAt(0)
                    }

                    val smoothedDistance = distanceHistory.average().toFloat()
                    val smoothedZone = zoneInfo.copy(
                        minDistance = smoothedDistance,
                        dangerLevel = ZoneInfo.calculateDangerLevel(smoothedDistance)
                    )
                    //val smoothTime = System.currentTimeMillis() - smoothStart

                    //val audioStart = System.currentTimeMillis()
                    _currentZone.postValue(smoothedZone)
                    if (_isDetectionActive.value == true) {
                        audioManager.updateFeedback(smoothedZone)
                    }
                    //val audioTime = System.currentTimeMillis() - audioStart
                    //val vmTotal = System.currentTimeMillis() - vmStart

                    // Log.d(TAG, "VIEWMODEL | Analysis: ${analysisTime}ms | Smooth: ${smoothTime}ms | Audio: ${audioTime}ms | TOTAL: ${vmTotal}ms")
                    // Log.d(TAG, "ZONE: ${smoothedZone.zone.name} | Dist: ${String.format("%.2f", smoothedZone.minDistance)}m | Danger: ${smoothedZone.dangerLevel}")
                }

                onDepthMapReady(depthMap)
                //updateFpsTracking()
            } catch (e: Exception) {
                e.printStackTrace()
                onDepthMapReady(null)
            } finally {
                _isProcessing.postValue(false)
            }
        }
    }

    // Funzione per calcolare e tracciare gli FPS di elaborazione
   /* private fun updateFpsTracking() {
        processedFrames++
        val currentTime = System.currentTimeMillis()
        val elapsed = currentTime - lastFpsTime
        if (elapsed >= 3000) {
            val fps = (processedFrames * 1000f) / elapsed
            val frameTime = elapsed.toFloat() / processedFrames
            // Log.i(TAG, "PERFORMANCE | Processed FPS: ${String.format("%.1f", fps)} | Avg frame time: ${String.format("%.0f", frameTime)}ms | Frames: $processedFrames in ${elapsed}ms")
            processedFrames = 0
            lastFpsTime = currentTime
        }
    }*/

    // Funzione per avviare il rilevamento continuo degli ostacoli
    fun startDetection() {
        if (_isDetectionActive.value == true) {
            return
        }

        if (!depthManager.isReady()) {
            _errorMessage.postValue("Modello non caricato correttamente")
            return
        }

        _isDetectionActive.postValue(true)
        // Log.d(TAG, "Rilevamento ostacoli avviato")
    }

    // Funzione per fermare il rilevamento degli ostacoli e il feedback audio
    fun stopDetection() {
        if (_isDetectionActive.value == false) {
            return
        }

        _isDetectionActive.postValue(false)
        audioManager.stopFeedback()
        _currentZone.postValue(null)
        // Log.d(TAG, "Rilevamento ostacoli fermato")
    }

    // Funzione per verificare se il rilevamento e attualmente attivo
    fun isDetecting(): Boolean {
        return _isDetectionActive.value == true
    }

    // Funzione per rilasciare tutte le risorse quando il ViewModel viene distrutto
    override fun onCleared() {
        super.onCleared()
        stopDetection()
        depthManager.shutdown()
        audioManager.release()
        //Log.d(TAG, "OstacoliViewModel risorse rilasciate")
    }
}
