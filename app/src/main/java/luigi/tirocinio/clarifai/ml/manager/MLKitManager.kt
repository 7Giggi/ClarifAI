package luigi.tirocinio.clarifai.ml.manager

import android.content.Context
import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import luigi.tirocinio.clarifai.data.model.TextBlock
import luigi.tirocinio.clarifai.ml.processor.TextRecognitionProcessor

//Manager usato per gestire il riconoscimento del testo tramite ML Kit
class MLKitManager(context: Context) {

    private val textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private val processor = TextRecognitionProcessor()
    //Funzione per riconoscere il testo
    fun recognizeText(
        bitmap: Bitmap,
        onSuccess: (List<TextBlock>) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        val image = InputImage.fromBitmap(bitmap, 0)

        textRecognizer.process(image)
            .addOnSuccessListener { visionText ->
                val blocks = processor.processTextResult(visionText)
                onSuccess(blocks)
            }
            .addOnFailureListener { exception ->
                onFailure(exception)
            }
    }
    fun shutdown() {
        textRecognizer.close()
    }
}
