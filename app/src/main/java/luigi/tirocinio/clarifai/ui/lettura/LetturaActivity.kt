package luigi.tirocinio.clarifai.ui.lettura

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ProgressBar
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.view.PreviewView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import luigi.tirocinio.clarifai.R
import luigi.tirocinio.clarifai.ml.manager.CameraManager
import luigi.tirocinio.clarifai.ml.manager.SpeechRecognizerManager
import luigi.tirocinio.clarifai.utils.Constants
import luigi.tirocinio.clarifai.utils.PermissionHelper

// Activity per la modalita Lettura che permette di riconoscere e leggere testi nelle immagini
// Utilizza la fotocamera per catturare frame, ML Kit per riconoscere il testo e Text-to-Speech
// per leggerlo all'utente. Supporta comandi vocali per navigare tra i blocchi di testo riconosciuti
class LetturaActivity : AppCompatActivity() {

    // ViewModel che gestisce la logica di riconoscimento testo e lettura TTS
    private val viewModel: LetturaViewModel by viewModels()

    // Manager per la gestione della fotocamera
    private lateinit var cameraManager: CameraManager

    // Manager per il riconoscimento vocale dei comandi
    private lateinit var speechManager: SpeechRecognizerManager

    // View per mostrare il preview della fotocamera
    private lateinit var previewView: PreviewView

    // Indicatore di caricamento mostrato durante l'analisi
    private lateinit var loadingIndicator: ProgressBar

    // RecyclerView che mostra la lista dei blocchi di testo riconosciuti
    private lateinit var textBlocksRecycler: RecyclerView

    // Flag per evitare analisi multiple simultanee della stessa immagine
    private var isAnalyzing = false

    // Funzione per inizializzare l'activity, le view e verifica i permessi necessari
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_lettura)
        initViews()
        checkPermissionsAndStart()
    }

    // Funzione per inizializzare tutti i riferimenti alle view e configura i listener
    // Imposta il click sulla preview per catturare e analizzare un frame
    private fun initViews() {
        previewView = findViewById(R.id.previewView)
        loadingIndicator = findViewById(R.id.loadingIndicator)
        textBlocksRecycler = findViewById(R.id.textBlocksRecycler)
        textBlocksRecycler.layoutManager = LinearLayoutManager(this)
        previewView.setOnClickListener {
            captureAndAnalyze()
        }
        observeViewModel()
    }

    // Funzione per verificare se i permessi necessari sono stati concessi
    // Se mancano, li richiede, altrimenti avvia fotocamera e riconoscimento vocale
    private fun checkPermissionsAndStart() {
        if (!PermissionHelper.hasCameraPermissions(this)) {
            PermissionHelper.requestPermissions(this)
        } else {
            startCamera()
            setupSpeechRecognition()
        }
    }

    // Funzione per avviare la fotocamera e la collega alla PreviewView per mostrare il feed live
    private fun startCamera() {
        cameraManager = CameraManager(this)
        cameraManager.startCamera(this, previewView)
    }

    // Funzione per configurare il sistema di riconoscimento vocale per i comandi dell'utente, delega il lavoro al ViewModel
    private fun setupSpeechRecognition() {
        if (!PermissionHelper.hasVoicePermissions(this)) {
            // Log.e("LetturaActivity", "Permesso RECORD_AUDIO non concesso")
            return
        }

        // Log.d("LetturaActivity", "Inizializzazione speech recognition")
        speechManager = SpeechRecognizerManager(this)
        speechManager.setCallbacks(
            onCommand = { command ->
                // Log.d("LetturaActivity", "Comando ricevuto: $command")
                val normalizedCommand = command.lowercase().trim()
                when {
                    normalizedCommand == "home" -> {
                        // Log.d("LetturaActivity", "Esecuzione finish() per tornare alla MainActivity")
                        finish()
                    }
                    normalizedCommand == "analizza" -> {
                        // Log.d("LetturaActivity", "Eseguo captureAndAnalyze")
                        captureAndAnalyze()
                    }
                    normalizedCommand == "leggi" -> {
                        // Log.d("LetturaActivity", "Eseguo readAllText")
                        viewModel.readAllText()
                    }
                    normalizedCommand == "aiuto" -> {
                        viewModel.speakHelpMessage(Constants.HELP_MESSAGE_LETTURA)
                    }

                    normalizedCommand.contains("stop") ||
                            normalizedCommand.contains("ferma") ||
                            normalizedCommand.contains("prossimo") ||
                            normalizedCommand.contains("successivo") ||
                            normalizedCommand.contains("precedente") ||
                            normalizedCommand.contains("indietro") ||
                            normalizedCommand.contains("riprendi") ||
                            normalizedCommand.contains("continua") -> {
                        viewModel.processVoiceCommand(command)
                    }
                    else -> {
                        // Log.d("LetturaActivity", "Comando non riconosciuto: $command")
                        Toast.makeText(this, "Comando non riconosciuto:", Toast.LENGTH_SHORT).show()

                    }
                }
            },
            onErr = { error ->
                // Log.e("LetturaActivity", "Errore speech: $error")
                if (Constants.DEBUG_MODE) {
                    Toast.makeText(this, error, Toast.LENGTH_SHORT).show()
                }
            }
        )
        speechManager.startListening()
        // Log.d("LetturaActivity", "Speech recognition configurato e avviato")
    }

    // Funzione per catturare un frame dalla fotocamera e lo invia al ViewModel per l'analisi del testo
    // Utilizza il flag isAnalyzing per prevenire catture multiple simultanee
    private fun captureAndAnalyze() {
        if (isAnalyzing) return
        isAnalyzing = true
        cameraManager.captureFrame { bitmap, rotationDegrees ->
            if (bitmap != null) {
                viewModel.analyzeFrame(bitmap, rotationDegrees)
            } else {
                Toast.makeText(this, "Errore cattura immagine", Toast.LENGTH_SHORT).show()
            }
            isAnalyzing = false
        }
    }

    // Funzione per osservare i cambiamenti nel ViewModel per aggiornare l'interfaccia utente
    // Aggiorna la lista dei blocchi di testo, l'indicatore di caricamento e l'indice di lettura corrente
    private fun observeViewModel() {
        viewModel.textBlocks.observe(this) { blocks ->
            textBlocksRecycler.adapter = TextBlockAdapter(blocks) { block ->
                viewModel.readBlock(block.index)
            }
        }

        viewModel.isProcessing.observe(this) { processing ->
            loadingIndicator.visibility = if (processing) View.VISIBLE else View.GONE
        }

        viewModel.currentReadingIndex.observe(this) { index ->
            if (index >= 0) {
                // Log.d("LetturaActivity", "Lettura blocco: $index")
            }
        }
    }

    // Funzione per gestire il risultato della richiesta di permessi
    // Se concessi, avvia fotocamera e riconoscimento vocale, altrimenti chiude l'activity
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
            onDenied = {
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

    // Funzione per rilasciare tutte le risorse quando l'activity viene distrutta
    override fun onDestroy() {
        super.onDestroy()
        if (::cameraManager.isInitialized) {
            cameraManager.stopCamera()
            cameraManager.shutdown()
        }
        if (::speechManager.isInitialized) {
            speechManager.shutdown()
        }
    }
}
