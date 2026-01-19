// File di utility per la manipolazione di immagini e bitmap.
// Contiene funzioni di estensione per ridimensionare e convertire bitmap utilizzate nelle varie modalita dell'app.

package luigi.tirocinio.clarifai.utils

import android.graphics.Bitmap
import android.util.Base64
import java.io.ByteArrayOutputStream

// Funzione per ridimensionare una bitmap mantenendo le proporzioni originali.
// Calcola il rapporto di scala in base ai limiti di larghezza e altezza specificati e crea una nuova bitmap scalata senza distorcere l'immagine.
fun Bitmap.resize(maxWidth: Int, maxHeight: Int): Bitmap {
    val ratio = minOf(
        maxWidth.toFloat() / width,
        maxHeight.toFloat() / height
    )

    val newWidth = (width * ratio).toInt()
    val newHeight = (height * ratio).toInt()

    return Bitmap.createScaledBitmap(this, newWidth, newHeight, true)
}

// Funzione per convertire una bitmap in stringa Base64 per l'invio alle API di Gemini.
// Comprime l'immagine in formato JPEG con la qualita specificata (default 85%) e restituisce la rappresentazione Base64 senza caratteri di interruzione riga.
fun Bitmap.toBase64(quality: Int = 85): String {
    val outputStream = ByteArrayOutputStream()
    compress(Bitmap.CompressFormat.JPEG, quality, outputStream)
    val byteArray = outputStream.toByteArray()
    return Base64.encodeToString(byteArray, Base64.NO_WRAP)
}






