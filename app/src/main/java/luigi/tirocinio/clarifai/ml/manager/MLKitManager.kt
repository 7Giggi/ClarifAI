package luigi.tirocinio.clarifai.ml.manager

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import luigi.tirocinio.clarifai.data.model.TextBlock
import luigi.tirocinio.clarifai.ml.processor.TextRecognitionProcessor

class MLKitManager(context: Context) {

    //private val TAG = "MLKitManager"
    private val textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private val processor = TextRecognitionProcessor()
    private val translationManager = TranslationManager(context)

    //Funzione per riconoscere il testo
    fun recognizeText(
        bitmap: Bitmap,
        //Rotation degrees usata per girare correttamente l'immagine in base alla rotazione della fotocamera
        rotationDegrees: Int,
        onSuccess: (List<TextBlock>) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        //Log.d(TAG, "Recognizing text with rotation: $rotationDegrees")

        val image = InputImage.fromBitmap(bitmap, rotationDegrees)

        textRecognizer.process(image)
            .addOnSuccessListener { visionText ->
                val blocks = processor.processTextResult(visionText)
                onSuccess(blocks)
            }
            .addOnFailureListener { exception ->
                //Log.e(TAG, "Text recognition failed", exception)
                onFailure(exception)
            }
    }

    fun getProcessor(): TextRecognitionProcessor {
        return processor
    }
    fun getTranslationManager(): TranslationManager {
        return translationManager
    }
    fun shutdown() {
        textRecognizer.close()
        translationManager.shutdown()
        //Log.d(TAG, "MLKitManager chiuso")
    }
}
