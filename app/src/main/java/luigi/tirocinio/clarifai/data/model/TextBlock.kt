package luigi.tirocinio.clarifai.data.model

import android.graphics.Rect
import luigi.tirocinio.clarifai.utils.Constants.TEXT_MIN_CONFIDENCE

//Classe usata per rappresentare un blocco di testo rilevato da ML Recognition Kit
data class TextBlock(
    val text: String,
    val boundingBox: Rect?,
    val confidence: Float,
    val index: Int,
    val language: String? = null
) {
    fun isValid(): Boolean {
        return text.isNotBlank() &&
                confidence >= TEXT_MIN_CONFIDENCE
    }
}
