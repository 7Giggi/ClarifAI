package luigi.tirocinio.clarifai.ui.ostacoli

import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.view.PreviewView
import luigi.tirocinio.clarifai.R
import luigi.tirocinio.clarifai.ml.manager.CameraManager
import luigi.tirocinio.clarifai.ml.manager.SpeechRecognizerManager
import luigi.tirocinio.clarifai.utils.Constants
import luigi.tirocinio.clarifai.utils.PermissionHelper

// Activity per la modalita Ostacoli che rileva la profondita dell'ambiente tramite modello DepthAnythingV2
// Analizza continuamente i frame della fotocamera per calcolare le distanze degli oggetti,
// mostra una mappa di calore visiva della profondita e fornisce feedback in base al livello di pericolo rilevato
class OstacoliActivity : AppCompatActivity() {

    // ViewModel che gestisce il rilevamento profondita e il feedback audio-tattile
    private val viewModel: OstacoliViewModel by viewModels()

    // Manager per la gestione della fotocamera con analisi continua
    private lateinit var cameraManager: CameraManager

    // View per mostrare il preview della fotocamera
    private lateinit var previewView: PreviewView

    // ImageView che visualizza la mappa di calore della profondita
    private lateinit var depthMapImageView: ImageView

    // TextView che mostra informazioni su zona e distanza rilevate
    private lateinit var infoTextView: TextView

    // Manager per il riconoscimento vocale del comando home
    private lateinit var speechManager: SpeechRecognizerManager

    // Funzione per inizializzare l'activity, le view e avviare fotocamera e riconoscimento vocale
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ostacoli)
        setupSpeechForHome()
        initViews()
        observeViewModel()
        checkPermissionsAndStart()
    }

    // Funzione per configurare il riconoscimento vocale per il comando home
    private fun setupSpeechForHome() {
        // Log.d("OstacoliActivity", "Inizializzazione speech per comando home")
        speechManager = SpeechRecognizerManager(this)
        speechManager.setCallbacks(
            onCommand = { command ->
                // Log.d("OstacoliActivity", "Comando ricevuto: $command")
                if (command == "home") {
                    // Log.d("OstacoliActivity", "Esecuzione finish() per tornare alla MainActivity")
                    finish()
                }
            },
            onErr = { error ->
                // Log.e("OstacoliActivity", "Errore speech: $error")
            }
        )
        speechManager.startListening()
        // Log.d("OstacoliActivity", "Speech recognition avviato")
    }

    // Funzione per inizializzare i riferimenti alle view e impostare il listener per toggle del rilevamento
    private fun initViews() {
        previewView = findViewById(R.id.previewView)
        depthMapImageView = findViewById(R.id.depthMapImageView)
        infoTextView = findViewById(R.id.infoTextView)
        depthMapImageView.setOnClickListener {
            toggleDetection()
        }
    }

    // Funzione per verificare se i permessi sono concessi e avviare la fotocamera
    private fun checkPermissionsAndStart() {
        if (!PermissionHelper.hasCameraPermissions(this)) {
            PermissionHelper.requestPermissions(this)
        } else {
            startCamera()
        }
    }

    // Funzione per avviare la fotocamera con analisi continua dei frame per il rilevamento profondita
    private fun startCamera() {
        cameraManager = CameraManager(this)
        cameraManager.startCameraWithAnalysis(
            lifecycleOwner = this,
            previewView = previewView,
            analyzer = createImageAnalyzer()
        )
        viewModel.startDetection()
    }

    // Funzione per creare l'analyzer che processa ogni frame catturato dalla fotocamera
    private fun createImageAnalyzer(): ImageAnalysis.Analyzer {
        return ImageAnalysis.Analyzer { imageProxy ->
            processImageProxy(imageProxy)
        }
    }

    // Funzione per elaborare ogni frame convertendolo in bitmap e ruotandolo in portrait
    private fun processImageProxy(imageProxy: ImageProxy) {
        try {
            //val activityStart = System.currentTimeMillis()
            val bitmapStart = System.currentTimeMillis()
            val bitmap = imageProxy.toBitmap()
            //val bitmapTime = System.currentTimeMillis() - bitmapStart
            if (bitmap != null) {
                //val rotateStart = System.currentTimeMillis()
                val rotatedBitmap = rotateBitmapToPortrait(bitmap)
                //val rotateTime = System.currentTimeMillis() - rotateStart
                //val activityTime = System.currentTimeMillis() - activityStart
                // Log.d("OstacoliActivity", "ACTIVITY | Bitmap: ${bitmapTime}ms | Rotate: ${rotateTime}ms | Total: ${activityTime}ms")
                processFrameWithDepthMap(rotatedBitmap)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            imageProxy.close()
        }
    }

    // Funzione per ruotare la bitmap da landscape a portrait se necessario
    private fun rotateBitmapToPortrait(bitmap: Bitmap): Bitmap {
        if (bitmap.height > bitmap.width) {
            return bitmap
        }
        val matrix = android.graphics.Matrix()
        matrix.postRotate(90f)
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, false)
    }

    // Funzione per inviare il frame al ViewModel per l'analisi di profondita e visualizzare la mappa risultante
    private fun processFrameWithDepthMap(bitmap: Bitmap) {
        viewModel.processFrameWithDepthMap(bitmap) { depthMap ->
            if (depthMap != null) {
                val depthBitmap = depthMapToHeatmap(depthMap)
                runOnUiThread {
                    depthMapImageView.setImageBitmap(depthBitmap)
                }
            }
        }
    }
    // Funzione per convertire la mappa di profondita in una bitmap heatmap colorata
    private fun depthMapToHeatmap(depthMap: Array<FloatArray>): Bitmap {
        val height = depthMap.size
        val width = depthMap[0].size
        val marginY = (height * 0.1f).toInt()
        val marginX = (width * 0.1f).toInt()
        val croppedHeight = height - (2 * marginY)
        val croppedWidth = width - (2 * marginX)
        val bitmap = Bitmap.createBitmap(croppedWidth, croppedHeight, Bitmap.Config.ARGB_8888)

        var minDepth = Float.MAX_VALUE
        var maxDepth = Float.MIN_VALUE
        for (y in marginY until (height - marginY)) {
            for (x in marginX until (width - marginX)) {
                val value = depthMap[y][x]
                if (value.isFinite() && value > Constants.DEPTH_MIN_METERS && value < Constants.DEPTH_MAX_METERS) {
                    if (value < minDepth) minDepth = value
                    if (value > maxDepth) maxDepth = value
                }
            }
        }
        // Log.d("OstacoliActivity", "Heatmap depth range: ${String.format("%.2f", minDepth)} - ${String.format("%.2f", maxDepth)} meters")
        var range = maxDepth - minDepth
        if (range < 0.5f) {
            val center = (minDepth + maxDepth) / 2f
            minDepth = (center - 0.5f).coerceAtLeast(Constants.DEPTH_MIN_METERS)
            maxDepth = (center + 0.5f).coerceAtMost(Constants.DEPTH_MAX_METERS)
            range = maxDepth - minDepth
        }

        for (y in 0 until croppedHeight) {
            for (x in 0 until croppedWidth) {
                val depth = depthMap[y + marginY][x + marginX]
                val normalized = if (range > 0.001f && depth.isFinite()) {
                    ((depth - minDepth) / range).coerceIn(0f, 1f)
                } else {
                    0.5f
                }
                val color = getHeatmapColor(normalized)
                bitmap.setPixel(x, y, color)
            }
        }
        return bitmap
    }

    // Funzione per ottenere il colore della heatmap in base al valore normalizzato di profondita
    private fun getHeatmapColor(value: Float): Int {
        val v = value.coerceIn(0f, 1f)
        return when {
            v < 0.25f -> {
                val factor = v / 0.25f
                Color.rgb(255, (128 * factor).toInt(), 0)
            }
            v < 0.5f -> {
                val factor = (v - 0.25f) / 0.25f
                Color.rgb(255, (128 + 127 * factor).toInt(), 0)
            }
            v < 0.75f -> {
                val factor = (v - 0.5f) / 0.25f
                Color.rgb((255 * (1 - factor)).toInt(), 255, 0)
            }
            else -> {
                val factor = (v - 0.75f) / 0.25f
                Color.rgb(0, (255 * (1 - factor)).toInt(), (255 * factor).toInt())
            }
        }
    }

    // Funzione per attivare o disattivare il rilevamento degli ostacoli
    private fun toggleDetection() {
        if (viewModel.isDetecting()) {
            viewModel.stopDetection()
            Toast.makeText(this, "Rilevamento fermato", Toast.LENGTH_SHORT).show()
        } else {
            viewModel.startDetection()
            Toast.makeText(this, "Rilevamento avviato", Toast.LENGTH_SHORT).show()
        }
    }

    // Funzione per osservare i cambiamenti nel ViewModel e aggiornare l'interfaccia
    private fun observeViewModel() {
        viewModel.currentZone.observe(this) { zoneInfo ->
            if (zoneInfo != null) {
                updateInfoDisplay(zoneInfo)
            } else {
                infoTextView.text = "In attesa..."
                infoTextView.visibility = View.VISIBLE
            }
        }

        viewModel.isDetectionActive.observe(this) { isActive ->
            if (isActive) {
                infoTextView.visibility = View.VISIBLE
            } else {
                infoTextView.text = "Tocca lo schermo per avviare"
                infoTextView.visibility = View.VISIBLE
            }
        }

        viewModel.errorMessage.observe(this) { errorMsg ->
            if (errorMsg != null) {
                Toast.makeText(this, errorMsg, Toast.LENGTH_LONG).show()
            }
        }
    }

    // Funzione per aggiornare il display con le informazioni sulla zona e distanza rilevate
    private fun updateInfoDisplay(zoneInfo: luigi.tirocinio.clarifai.data.model.ZoneInfo) {
        val distanceText = "%.2f m".format(zoneInfo.minDistance)
        val zoneText = zoneInfo.zone.getDescription()
        val dangerText = when (zoneInfo.dangerLevel) {
            Constants.DangerZone.SAFE -> "Sicuro"
            Constants.DangerZone.WARNING -> "Attenzione"
            Constants.DangerZone.CAUTION -> "Cautela"
            Constants.DangerZone.DANGER -> "PERICOLO"
        }

        val displayText = """
            $dangerText
            Zona: $zoneText
            Distanza: $distanceText
        """.trimIndent()

        infoTextView.text = displayText
        infoTextView.visibility = View.VISIBLE

        val textColor = when (zoneInfo.dangerLevel) {
            Constants.DangerZone.SAFE -> Color.GREEN
            Constants.DangerZone.WARNING -> Color.YELLOW
            Constants.DangerZone.CAUTION -> Color.parseColor("#FFA500")
            Constants.DangerZone.DANGER -> Color.RED
        }
        infoTextView.setTextColor(textColor)
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
            onAllGranted = { startCamera() },
            onDenied = {
                Toast.makeText(this, "Permessi necessari negati", Toast.LENGTH_LONG).show()
                finish()
            }
        )
    }

    // Funzione per fermare il rilevamento quando l'activity va in background
    override fun onPause() {
        super.onPause()
        viewModel.stopDetection()
    }

    // Funzione per rilasciare le risorse quando l'activity viene distrutta
    override fun onDestroy() {
        super.onDestroy()
        if (::speechManager.isInitialized) {
            speechManager.shutdown()
        }
        if (::cameraManager.isInitialized) {
            cameraManager.stopCamera()
            cameraManager.shutdown()
        }
    }
}
