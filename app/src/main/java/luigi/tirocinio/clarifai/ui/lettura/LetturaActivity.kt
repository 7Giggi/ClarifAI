package luigi.tirocinio.clarifai.ui.lettura

import android.os.Bundle
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
import luigi.tirocinio.clarifai.utils.PermissionHelper

class LetturaActivity : AppCompatActivity() {

    private val viewModel: LetturaViewModel by viewModels()

    private lateinit var cameraManager: CameraManager
    private lateinit var speechManager: SpeechRecognizerManager

    private lateinit var previewView: PreviewView
    private lateinit var loadingIndicator: ProgressBar
    private lateinit var textBlocksRecycler: RecyclerView

    //Flag per evitare che ci possano essere più analisi nello stesso momento
    private var isAnalyzing = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_lettura)

        initViews()
        checkPermissionsAndStart()
    }

    //Funzione per inizializzare le view
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

    //Funzione per controllare i permessi e avviare la fotocamera
    private fun checkPermissionsAndStart() {
        if (!PermissionHelper.hasCameraPermissions(this)) {
            PermissionHelper.requestPermissions(this)
        } else {
            startCamera()
            setupSpeechRecognition()
        }
    }
    //Funzione per avviare la fotocamera
    private fun startCamera() {
        cameraManager = CameraManager(this)
        cameraManager.startCamera(this, previewView)
    }

    //Funzione per avviare la rilevazione vocale
    private fun setupSpeechRecognition() {
        if (!PermissionHelper.hasVoicePermissions(this)) return

        speechManager = SpeechRecognizerManager(this)
        speechManager.setCallbacks(
            onCommand = { command ->
                when (command) {
                    "leggi" -> viewModel.readAllText()
                    "stop" -> viewModel.stopReading()
                }
            }
        )

        speechManager.startListening()
    }

    //Funzione per catturare l'immagine e analizzarla
    private fun captureAndAnalyze() {
        if (isAnalyzing) return

        isAnalyzing = true
        cameraManager.captureFrame { bitmap ->
            bitmap?.let {
                viewModel.analyzeFrame(it)
            }
            isAnalyzing = false
        }
    }

    //Funzione per aggiornare lo stato della UI in base al viewModel
    private fun observeViewModel() {
        viewModel.textBlocks.observe(this) { blocks ->
            textBlocksRecycler.adapter = TextBlockAdapter(blocks) { block ->
                viewModel.readBlock(block.index)
            }
        }

        viewModel.isProcessing.observe(this) { processing ->
            loadingIndicator.visibility = if (processing) View.VISIBLE else View.GONE
        }
    }

    //Funzione per gestire i risultati dei permessi
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
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

    //Serie di funzioni per pausa/riprendere la fotocamera e la rilevazione vocale
    override fun onResume() {
        super.onResume()
        if (::speechManager.isInitialized) {
            speechManager.startListening()
        }
    }

    override fun onPause() {
        super.onPause()
        if (::speechManager.isInitialized) {
            speechManager.stopListening()
        }
    }

    //Funzione per rilasciare le risorse
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

