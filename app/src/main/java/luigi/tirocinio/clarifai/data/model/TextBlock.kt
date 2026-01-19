package luigi.tirocinio.clarifai.data.model

import android.graphics.Rect

//Classe usata per rappresentare un blocco di testo rilevato da ML Recognition Kit
data class TextBlock(
    val text: String,
    val boundingBox: Rect?,
    val confidence: Float,
    val index: Int,
    val language: String? = null
) {
}
