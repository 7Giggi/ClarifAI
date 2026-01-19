package luigi.tirocinio.clarifai.ml.manager

import android.content.Context
import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import luigi.tirocinio.clarifai.data.model.TextBlock
import luigi.tirocinio.clarifai.ml.processor.TextRecognitionProcessor

// Classe per gestire il riconoscimento testo tramite ML Kit Text Recognition
// Coordina il text recognizer, il processor per ordinare i blocchi e il translation manager
class MLKitManager(context: Context) {

    // Client ML Kit per il riconoscimento ottico dei caratteri
    private val textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    // Processor che ordina i blocchi di testo rilevati in ordine di lettura naturale
    private val processor = TextRecognitionProcessor()
    // Manager per la traduzione automatica del testo rilevato
    private val translationManager = TranslationManager(context)

    // Funzione per riconoscere il testo in una bitmap con gestione della rotazione della fotocamera
    fun recognizeText(
        bitmap: Bitmap,
        rotationDegrees: Int,
        onSuccess: (List<TextBlock>) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        // Log.d(TAG, "Recognizing text with rotation: \$rotationDegrees")
        val image = InputImage.fromBitmap(bitmap, rotationDegrees)
        textRecognizer.process(image)
            .addOnSuccessListener { visionText ->
                val blocks = processor.processTextResult(visionText)
                onSuccess(blocks)
            }
            .addOnFailureListener { exception ->
                // Log.e(TAG, "Text recognition failed", exception)
                onFailure(exception)
            }
    }

    // Funzione per ottenere il translation manager
    fun getTranslationManager(): TranslationManager {
        return translationManager
    }

    // Funzione per rilasciare le risorse del text recognizer e del translation manager
    fun shutdown() {
        textRecognizer.close()
        translationManager.shutdown()
        // Log.d(TAG, "MLKitManager chiuso")
    }
}