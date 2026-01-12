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
// File Manager per la gestione della fotocamera con la libreria CameraX
class CameraManager(private val context: Context) {

    //private val TAG = "CameraManager"
    private var camera: Camera? = null
    private var imageCapture: ImageCapture? = null
    private var imageAnalysis: ImageAnalysis? = null
    // Executor per operazioni camera in background
    private val cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()

    // Funzione per l'inizializzazione della fotocamera posteriore
    fun startCamera(lifecycleOwner: LifecycleOwner, previewView: PreviewView) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)

        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder()
                .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                .build()
                .also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

            imageCapture = ImageCapture.Builder()
                .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build()

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()
                camera = cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    preview,
                    imageCapture
                )
                //Log.d(TAG, "Camera avviata (modalità capture)")
            } catch (e: Exception) {
                //Log.e(TAG, "Errore avvio camera", e)
                e.printStackTrace()
            }

        }, ContextCompat.getMainExecutor(context))
    }

    //Funzione per il binding degli use cases della camera
    fun startCameraWithAnalysis(
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView,
        analyzer: ImageAnalysis.Analyzer
    ) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)

        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            // Use case per la preview dell'immagine
            val preview = Preview.Builder()
                .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                .build()
                .also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

            //Use case per l'analisi dell'immagine
            imageAnalysis = ImageAnalysis.Builder()
                .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                //target rotation per gestire orientamento
                .setTargetRotation(previewView.display.rotation)
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor, analyzer)
                }
            // Selettore camera (posteriore/frontale)
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                // Collega i nuovi use case
                cameraProvider.unbindAll()
                camera = cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    preview,
                    imageAnalysis
                )
                //Log.d(TAG, "Camera avviata con analisi (rotation: ${previewView.display.rotation})")
            } catch (e: Exception) {
                //Log.e(TAG, "Errore avvio camera con analisi", e)
                e.printStackTrace()
            }

        }, ContextCompat.getMainExecutor(context))
    }

    //Funzione per catturare un solo frame
    fun captureFrame(onFrameCaptured: (Bitmap?, Int) -> Unit) {
        imageCapture?.let { capture ->
            capture.takePicture(
                cameraExecutor,
                object : ImageCapture.OnImageCapturedCallback() {
                    override fun onCaptureSuccess(image: ImageProxy) {
                        val bitmap = imageProxyToBitmap(image)
                        val rotationDegrees = image.imageInfo.rotationDegrees

                        //Log.d(TAG, "Frame catturato con rotation: $rotationDegrees°")

                        image.close()
                        // Restituisci il frame
                        onFrameCaptured(bitmap, rotationDegrees)
                    }

                    override fun onError(exception: ImageCaptureException) {
                        //Log.e(TAG, "Errore cattura frame", exception)
                        exception.printStackTrace()
                        onFrameCaptured(null, 0)
                    }
                }
            )
        } ?: run {
            //Log.w(TAG, "ImageCapture non inizializzato")
            onFrameCaptured(null, 0)
        }
    }

    //Funzione per l'interruzione della fotocamera
    fun stopCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            cameraProvider.unbindAll()
            //Log.d(TAG, "Camera fermata")
        }, ContextCompat.getMainExecutor(context))
    }

    //Funzione per trasformare l'immagine in bitmap
    private fun imageProxyToBitmap(image: ImageProxy): Bitmap {
        val buffer = image.planes[0].buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)
        return android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }
    //Funzione per rilasciare le risorse
    fun shutdown() {
        cameraExecutor.shutdown()
        //Log.d(TAG, "CameraExecutor chiuso")
    }
}
