package luigi.tirocinio.clarifai.ml.manager

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import android.util.Size
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import luigi.tirocinio.clarifai.utils.Constants
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

// File Manager per la gestione della fotocamera con la libreria CameraX
class CameraManager(private val context: Context) {
    private var cameraProvider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private var preview: Preview? = null
    private var imageAnalysis: ImageAnalysis? = null
    private var imageCapture: ImageCapture? = null

    // Executor per operazioni camera in background
    private val cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()

    // Callback per frame analysis
    private var onFrameAnalyzed: ((Bitmap) -> Unit)? = null
    private var isAnalyzing = false

    // Configurazione camera attuale
    private var currentResolution = Size(
        Constants.CAMERA_PREVIEW_WIDTH,
        Constants.CAMERA_PREVIEW_HEIGHT
    )

    // Funzione per l'inizializzazione della fotocamera posteriore
    fun startCamera(
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView,
        lensFacing: Int = CameraSelector.LENS_FACING_BACK
    ) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)

        cameraProviderFuture.addListener({
            try {
                cameraProvider = cameraProviderFuture.get()
                bindCameraUseCases(lifecycleOwner, previewView, lensFacing)

                Log.d(Constants.LOG_TAG, "Camera avviata con successo")
            } catch (e: Exception) {
                Log.e(Constants.LOG_TAG, "Errore avvio camera: ${e.message}", e)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    //Funzione per il binding degli use cases della camera
    private fun bindCameraUseCases(
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView,
        lensFacing: Int
    ) {
        // Use case per la preview dell'immagine
        preview = Preview.Builder()
            .setTargetResolution(currentResolution)
            .build()
            .also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

        //Use case per l'analisi dell'immagine
        imageAnalysis = ImageAnalysis.Builder()
            .setTargetResolution(currentResolution)
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
            .build()

        //Use case per la cattura dell'immagine
        imageCapture = ImageCapture.Builder()
            .setTargetResolution(currentResolution)
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .build()

        // Selettore camera (posteriore/frontale)
        val cameraSelector = CameraSelector.Builder()
            .requireLensFacing(lensFacing)
            .build()

        try {
            // Scollega eventuali use case precedenti
            cameraProvider?.unbindAll()

            // Collega i nuovi use case
            camera = cameraProvider?.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                preview,
                imageAnalysis,
                imageCapture
            )

            // Abilita tap-to-focus (opzionale, utile per accessibilità)
            setupTapToFocus(previewView)

        } catch (e: Exception) {
            Log.e(Constants.LOG_TAG, "Errore binding camera use cases: ${e.message}", e)
        }
    }

    // Funzione per far funzionare il taptofocus
    private fun setupTapToFocus(previewView: PreviewView) {
        previewView.setOnTouchListener { _, event ->
            val meteringPointFactory = previewView.meteringPointFactory
            val meteringPoint = meteringPointFactory.createPoint(event.x, event.y)

            val action = FocusMeteringAction.Builder(meteringPoint)
                .setAutoCancelDuration(3, TimeUnit.SECONDS)
                .build()

            camera?.cameraControl?.startFocusAndMetering(action)

            false
        }
    }

    //Funzione per l'analisi dei frame catturati
    fun startFrameAnalysis(analyzer: (Bitmap) -> Unit) {
        onFrameAnalyzed = analyzer
        isAnalyzing = true

        imageAnalysis?.setAnalyzer(cameraExecutor) { imageProxy ->
            if (isAnalyzing) {
                // Converti ImageProxy in Bitmap
                val bitmap = imageProxy.toBitmap()

                if (bitmap != null) {
                    // Invia al callback
                    onFrameAnalyzed?.invoke(bitmap)
                } else {
                    Log.w(Constants.LOG_TAG, "Frame conversion fallita")
                }
            }
            imageProxy.close()
        }

        Log.d(Constants.LOG_TAG, "Frame analysis avviata")
    }

    // Funzione per fermare l'analisi continua dei frame
    fun stopFrameAnalysis() {
        isAnalyzing = false
        imageAnalysis?.clearAnalyzer()

        Log.d(Constants.LOG_TAG, "Frame analysis fermata")
    }

    //Funzione per catturare un solo frame (Usata per limitare gli invii a Gemini, per evitare di saturare le RPM di Gemini)
    fun captureFrame(onFrameCaptured: (Bitmap?) -> Unit) {
        // Usa ImageAnalysis per catturare il prossimo frame disponibile
        imageAnalysis?.setAnalyzer(cameraExecutor) { imageProxy ->
            val bitmap = imageProxy.toBitmap()

            // Restituisci il frame
            onFrameCaptured(bitmap)

            // Rimuovi l'analyzer per non catturare ulteriori frame
            imageAnalysis?.clearAnalyzer()

            imageProxy.close()
        }
    }

    //Funzione per cambiare la risoluzione della camera
    fun changeResolution(
        width: Int,
        height: Int,
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView
    ) {
        currentResolution = Size(width, height)

        // Riavvia camera con nuova risoluzione
        cameraProvider?.unbindAll()
        bindCameraUseCases(lifecycleOwner, previewView, CameraSelector.LENS_FACING_BACK)

        Log.d(Constants.LOG_TAG, "Risoluzione camera cambiata: ${width}x${height}")
    }



    //Funzione per ottenere le informazioni della camera
    fun getCameraInfo(): CameraInfo? {
        return camera?.cameraInfo
    }

    //Funzione per mettere in pausa la fotocamera (per evitare lo spreco di risorse)
    fun pause() {
        stopFrameAnalysis()
        Log.d(Constants.LOG_TAG, "Camera in pausa")
    }

    //Funzione per riprendere la fotocamera dopo averla messa in pausa
    fun resume(analyzer: ((Bitmap) -> Unit)? = null) {
        analyzer?.let { startFrameAnalysis(it) }
        Log.d(Constants.LOG_TAG, "Camera ripresa")
    }

    //Funzione per rilasciare le risorse
    fun shutdown() {
        stopFrameAnalysis()
        cameraProvider?.unbindAll()
        cameraExecutor.shutdown()

        camera = null
        cameraProvider = null
        preview = null
        imageAnalysis = null
        imageCapture = null

        Log.d(Constants.LOG_TAG, "Camera shutdown completato")
    }

    //Funzione per capire se la camera è attiva
    fun isActive(): Boolean {
        return camera != null && cameraProvider != null
    }
}
