package luigi.tirocinio.clarifai.ml.manager

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

// Classe per gestire la fotocamera con CameraX per cattura singola e analisi continua
// Supporta due modalita: capture mode per scatti singoli e analysis mode per stream continuo
class CameraManager(private val context: Context) {

    // Istanza della fotocamera CameraX
    private var camera: Camera? = null

    // Use case per catturare singoli frame su richiesta
    private var imageCapture: ImageCapture? = null

    // Use case per analizzare il feed continuo della fotocamera
    private var imageAnalysis: ImageAnalysis? = null

    // Executor dedicato per eseguire operazioni della fotocamera in background
    private val cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()

    // Funzione per avviare la fotocamera posteriore in modalita capture per scatti singoli
    fun startCamera(lifecycleOwner: LifecycleOwner, previewView: PreviewView) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            // Configura la preview con aspect ratio 4:3
            val preview = Preview.Builder()
                .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                .build()
                .also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

            // Configura la cattura immagine con latenza minima per scatti rapidi
            imageCapture = ImageCapture.Builder()
                .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build()

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                // Scollega eventuali use case precedenti prima di collegare i nuovi
                cameraProvider.unbindAll()
                camera = cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    preview,
                    imageCapture
                )
                // Log.d(TAG, "Camera avviata (modalita capture)")
            } catch (e: Exception) {
                // Log.e(TAG, "Errore avvio camera", e)
                e.printStackTrace()
            }
        }, ContextCompat.getMainExecutor(context))
    }

    // Funzione per avviare la fotocamera in modalita analysis per stream continuo
    fun startCameraWithAnalysis(
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView,
        analyzer: ImageAnalysis.Analyzer
    ) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            // Configura il preview per mostrare il feed della fotocamera
            val preview = Preview.Builder()
                .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                .build()
                .also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

            // Gestisce la rotazione del display in modo sicuro con fallback a ROTATION_0
            val rotation = try {
                previewView.display?.rotation ?: android.view.Surface.ROTATION_0
            } catch (e: Exception) {
                android.view.Surface.ROTATION_0
            }

            // Configura l'analisi continua dei frame con strategia che mantiene solo l'ultimo frame
            imageAnalysis = ImageAnalysis.Builder()
                .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setTargetRotation(rotation)
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor, analyzer)
                }

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                // Scollega use case precedenti e collega preview e analysis
                cameraProvider.unbindAll()
                camera = cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    preview,
                    imageAnalysis
                )
                // Log.d(TAG, "Camera avviata con analisi (rotation: \$rotation)")
            } catch (e: Exception) {
                // Log.e(TAG, "Errore avvio camera con analisi", e)
                e.printStackTrace()
            }
        }, ContextCompat.getMainExecutor(context))
    }

    // Funzione per catturare un singolo frame dalla fotocamera con callback asincrono
    fun captureFrame(onFrameCaptured: (Bitmap?, Int) -> Unit) {
        imageCapture?.let { capture ->
            capture.takePicture(
                cameraExecutor,
                object : ImageCapture.OnImageCapturedCallback() {
                    override fun onCaptureSuccess(image: ImageProxy) {
                        // Converte l'ImageProxy in Bitmap
                        val bitmap = imageProxyToBitmap(image)

                        // Ottiene i gradi di rotazione per correggere l'orientamento
                        val rotationDegrees = image.imageInfo.rotationDegrees

                        // Log.d(TAG, "Frame catturato con rotation: \$rotationDegrees°")

                        // Chiude l'immagine per liberare risorse
                        image.close()

                        // Restituisce bitmap e rotazione tramite callback
                        onFrameCaptured(bitmap, rotationDegrees)
                    }

                    override fun onError(exception: ImageCaptureException) {
                        // Log.e(TAG, "Errore cattura frame", exception)
                        exception.printStackTrace()
                        onFrameCaptured(null, 0)
                    }
                }
            )
        } ?: run {
            // Se ImageCapture non e stato inizializzato, restituisce null
            // Log.w(TAG, "ImageCapture non inizializzato")
            onFrameCaptured(null, 0)
        }
    }

    // Funzione per fermare la fotocamera e scollegare tutti gli use case
    fun stopCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            cameraProvider.unbindAll()
            // Log.d(TAG, "Camera fermata")
        }, ContextCompat.getMainExecutor(context))
    }

    // Funzione per convertire un ImageProxy in Bitmap estraendo i byte dal buffer
    private fun imageProxyToBitmap(image: ImageProxy): Bitmap {
        // Ottiene il buffer del primo piano dell'immagine
        val buffer = image.planes[0].buffer

        // Crea un array di byte della dimensione del buffer
        val bytes = ByteArray(buffer.remaining())

        // Copia i byte dal buffer all'array
        buffer.get(bytes)

        // Decodifica i byte in una Bitmap
        return android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }

    // Funzione per rilasciare le risorse dell'executor quando non piu necessario
    fun shutdown() {
        cameraExecutor.shutdown()
        // Log.d(TAG, "CameraExecutor chiuso")
    }
}