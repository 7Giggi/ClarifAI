package luigi.tirocinio.clarifai.ui.descrizione

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.view.PreviewView
import luigi.tirocinio.clarifai.R
import luigi.tirocinio.clarifai.ml.manager.CameraManager
import luigi.tirocinio.clarifai.ml.manager.SpeechRecognizerManager
import luigi.tirocinio.clarifai.utils.Constants
import luigi.tirocinio.clarifai.utils.PermissionHelper
import luigi.tirocinio.clarifai.BuildConfig

// Activity per la modalita descrizione che utilizza Gemini AI per analizzare l'ambiente
// Cattura frame dalla fotocamera e invia a Gemini per ottenere descrizioni vocali dell'ambiente circostante
// Supporta comandi vocali per avviare l'analisi, fermare la lettura o tornare alla home
class DescrizioneActivity : AppCompatActivity() {

    // ViewModel che gestisce le chiamate a Gemini e il Text-to-Speech per la descrizione
    private val viewModel: DescrizioneViewModel by viewModels()

    // Manager per la gestione della fotocamera
    private lateinit var cameraManager: CameraManager

    // Manager per il riconoscimento vocale dei comandi
    private lateinit var speechManager: SpeechRecognizerManager

    // View per mostrare la preview della fotocamera
    private lateinit var previewView: PreviewView

    // Indicatore di caricamento mostrato durante l'analisi di Gemini
    private lateinit var loadingIndicator: ProgressBar

    // TextView che mostra la descrizione ricevuta da Gemini
    private lateinit var descriptionText: TextView

    // Funzione per inizializzare l'activity, la chiave API Gemini, le view e i permessi
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_descrizionecontinua)
        Constants.initApiKey(BuildConfig.GEMINI_API_KEY)
        initViews()
        checkPermissionsAndStart()
    }

    // Funzione per inizializzare i riferimenti alle view e impostare i listener
    private fun initViews() {
        previewView = findViewById(R.id.previewView)
        loadingIndicator = findViewById(R.id.loadingIndicator)
        descriptionText = findViewById(R.id.descriptionText)
        previewView.setOnClickListener {
            captureAndAnalyze()
        }
        observeViewModel()
    }

    // Funzione per verificare se i permessi sono concessi e avviare fotocamera e riconoscimento vocale
    private fun checkPermissionsAndStart() {
        if (!PermissionHelper.hasCameraPermissions(this)) {
            PermissionHelper.requestPermissions(this)
        } else {
            startCamera()
            setupSpeechRecognition()
        }
    }

    // Funzione per avviare la fotocamera e collegarla alla PreviewView
    private fun startCamera() {
        cameraManager = CameraManager(this)
        cameraManager.startCamera(this, previewView)
    }

    // Funzione per configurare il riconoscimento vocale per i comandi analizza, stop e home
    private fun setupSpeechRecognition() {
        if (!PermissionHelper.hasVoicePermissions(this)) return
        // Log.d("DescrizioneContinuaActivity", "Inizializzazione speech recognition")
        speechManager = SpeechRecognizerManager(this)
        speechManager.setCallbacks(
            onCommand = { command ->
                // Log.d("DescrizioneContinuaActivity", "Comando ricevuto: $command")
                when (command) {
                    "analizza" -> captureAndAnalyze()
                    "stop" -> viewModel.stopTTS()
                    "aiuto" -> viewModel.speakHelpMessage(Constants.HELP_MESSAGE_DESCRIZIONE)
                    "home" -> {
                        // Log.d("DescrizioneContinuaActivity", "Esecuzione finish() per tornare alla MainActivity")
                        finish()
                    }
                }
            },
            onErr = { error ->
                // Log.e("DescrizioneContinuaActivity", "Errore speech: $error")
                    Toast.makeText(this, error, Toast.LENGTH_SHORT).show()

            }
        )
        speechManager.startListening()
        // Log.d("DescrizioneContinuaActivity", "Speech recognition avviato")
    }

    // Funzione per catturare un frame dalla fotocamera e inviarlo al ViewModel per l'analisi con Gemini
    private fun captureAndAnalyze() {
        cameraManager.captureFrame { bitmap, rotationDegrees ->
            if (bitmap != null) {
                viewModel.analyzeFrame(bitmap)
            } else {
                Toast.makeText(this, "Errore cattura immagine", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Funzione per osservare i cambiamenti di stato nel ViewModel e aggiornare l'interfaccia
    private fun observeViewModel() {
        viewModel.uiState.observe(this) { state ->
            when (state) {
                is DescrizioneViewModel.UiState.Idle -> {
                    loadingIndicator.visibility = View.GONE
                }
                is DescrizioneViewModel.UiState.Loading -> {
                    loadingIndicator.visibility = View.VISIBLE
                    descriptionText.text = "Analisi in corso..."
                }
                is DescrizioneViewModel.UiState.Success -> {
                    loadingIndicator.visibility = View.GONE
                    descriptionText.text = state.text
                }
                is DescrizioneViewModel.UiState.Error -> {
                    loadingIndicator.visibility = View.GONE
                    descriptionText.text = "Errore: ${state.message}"
                }
            }
        }
    }

    // Funzione per gestire il risultato della richiesta di permessi
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        PermissionHelper.handlePermissionResult(
            requestCode,
            permissions,
            grantResults,
            onAllGranted = {
                startCamera()
                setupSpeechRecognition()
            },
            onDenied = { denied ->
                Toast.makeText(this, "Permessi necessari negati", Toast.LENGTH_LONG).show()
                finish()
            }
        )
    }

    // Funzione per riprendere l'ascolto dei comandi vocali quando l'activity torna in primo piano
    override fun onResume() {
        super.onResume()
        if (::speechManager.isInitialized) {
            speechManager.startListening()
        }
    }

    // Funzione per fermare l'ascolto dei comandi vocali quando l'activity va in background
    override fun onPause() {
        super.onPause()
        if (::speechManager.isInitialized) {
            speechManager.stopListening()
        }
    }

    // Funzione per rilasciare le risorse quando l'activity viene distrutta
    override fun onDestroy() {
        super.onDestroy()
        if (::cameraManager.isInitialized) {
            cameraManager.shutdown()
        }
        if (::speechManager.isInitialized) {
            speechManager.shutdown()
        }
    }
}
