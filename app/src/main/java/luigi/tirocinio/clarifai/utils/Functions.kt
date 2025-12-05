package luigi.tirocinio.clarifai.utils

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.YuvImage
import android.util.Base64
import android.util.Log
import androidx.camera.core.ImageProxy
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer

// file per le funzioni di utilità per le immagini e i bitmap usate nelle varie modalità

// funzione per conversione da image proxy a bitmap
fun ImageProxy.toBitmap(): Bitmap? {
    val buffer = planes[0].buffer
    val bytes = ByteArray(buffer.remaining())
    buffer.get(bytes)

    return try {
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)

        // Applica rotazione se necessaria
        if (imageInfo.rotationDegrees != 0) {
            bitmap.rotate(imageInfo.rotationDegrees.toFloat())
        } else {
            bitmap
        }
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

// funzione per la conversione da yuv420p a bitmap
fun ImageProxy.yuv420ToBitmap(): Bitmap? {
    val yBuffer = planes[0].buffer
    val vuBuffer = planes[2].buffer

    val ySize = yBuffer.remaining()
    val vuSize = vuBuffer.remaining()

    val nv21 = ByteArray(ySize + vuSize)

    yBuffer.get(nv21, 0, ySize)
    vuBuffer.get(nv21, ySize, vuSize)

    val yuvImage = YuvImage(nv21, ImageFormat.NV21, width, height, null)
    val out = ByteArrayOutputStream()
    yuvImage.compressToJpeg(Rect(0, 0, width, height), 100, out)

    val imageBytes = out.toByteArray()
    return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
}

// funzione per la rotazione della bitmap
fun Bitmap.rotate(degrees: Float): Bitmap {
    val matrix = Matrix().apply {
        postRotate(degrees)
    }
    return Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
}

// funzione per il resize della bitmap
fun Bitmap.resize(maxWidth: Int, maxHeight: Int): Bitmap {
    val ratio = minOf(
        maxWidth.toFloat() / width,
        maxHeight.toFloat() / height
    )

    val newWidth = (width * ratio).toInt()
    val newHeight = (height * ratio).toInt()

    return Bitmap.createScaledBitmap(this, newWidth, newHeight, true)
}

// funzione per la conversione di bitmap in stringa base64 (Per Gemini)
fun Bitmap.toBase64(quality: Int = 85): String {
    val outputStream = ByteArrayOutputStream()
    compress(Bitmap.CompressFormat.JPEG, quality, outputStream)
    val byteArray = outputStream.toByteArray()
    return Base64.encodeToString(byteArray, Base64.NO_WRAP)
}

// funzione per la conversione di bitmap in array di byte (Per TensorFlow Lite)
fun Bitmap.toByteArray(quality: Int = 90): ByteArray {
    val outputStream = ByteArrayOutputStream()
    compress(Bitmap.CompressFormat.JPEG, quality, outputStream)
    return outputStream.toByteArray()
}

// funzione per la riconversione da base64 a bitmap
fun String.base64ToBitmap(): Bitmap? {
    return try {
        val decodedBytes = Base64.decode(this, Base64.DEFAULT)
        BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

//funzione per convertire Bitmap in formato normalizzato per TensorFlow Lite (mod 3)
fun Bitmap.toNormalizedFloatArray(inputSize: Int): FloatArray {
    val resizedBitmap = Bitmap.createScaledBitmap(this, inputSize, inputSize, true)
    val floatArray = FloatArray(inputSize * inputSize * 3)

    var index = 0
    for (y in 0 until inputSize) {
        for (x in 0 until inputSize) {
            val pixel = resizedBitmap.getPixel(x, y)

            // Normalizza RGB a [0.0, 1.0]
            floatArray[index++] = ((pixel shr 16) and 0xFF) / 255.0f  // R
            floatArray[index++] = ((pixel shr 8) and 0xFF) / 255.0f   // G
            floatArray[index++] = (pixel and 0xFF) / 255.0f           // B
        }
    }

    return floatArray
}

// funzione per convertire Bitmap in ByteBuffer TensorFlow Lite (mod 3)
fun Bitmap.toTensorFlowBuffer(inputSize: Int): ByteBuffer {
    val resizedBitmap = Bitmap.createScaledBitmap(this, inputSize, inputSize, true)
    val byteBuffer = ByteBuffer.allocateDirect(4 * inputSize * inputSize * 3) // 4 bytes per float
    byteBuffer.rewind()

    for (y in 0 until inputSize) {
        for (x in 0 until inputSize) {
            val pixel = resizedBitmap.getPixel(x, y)

            // Normalizza e inserisci nel buffer
            byteBuffer.putFloat(((pixel shr 16) and 0xFF) / 255.0f)  // R
            byteBuffer.putFloat(((pixel shr 8) and 0xFF) / 255.0f)   // G
            byteBuffer.putFloat((pixel and 0xFF) / 255.0f)           // B
        }
    }

    return byteBuffer
}

// copia bitmap
fun Bitmap.copy(): Bitmap {
    return copy(config ?: Bitmap.Config.ARGB_8888, isMutable)
}
// bitmap in scala di grigi
fun Bitmap.toGrayscale(): Bitmap {
    val width = width
    val height = height
    val grayscaleBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

    for (x in 0 until width) {
        for (y in 0 until height) {
            val pixel = getPixel(x, y)

            val r = (pixel shr 16) and 0xFF
            val g = (pixel shr 8) and 0xFF
            val b = pixel and 0xFF

            // Formula luminosità standard
            val gray = (0.299 * r + 0.587 * g + 0.114 * b).toInt()

            val newPixel = (0xFF shl 24) or (gray shl 16) or (gray shl 8) or gray
            grayscaleBitmap.setPixel(x, y, newPixel)
        }
    }

    return grayscaleBitmap
}


fun Bitmap.crop(rect: Rect): Bitmap {
    return Bitmap.createBitmap(
        this,
        rect.left,
        rect.top,
        rect.width(),
        rect.height()
    )
}

fun Bitmap?.isValid(): Boolean {
    return this != null && !isRecycled && width > 0 && height > 0
}


fun Bitmap.getSizeInMB(): Float {
    return (byteCount / (1024f * 1024f))
}


fun ImageProxy.logInfo(tag: String = Constants.LOG_TAG) {
    if (Constants.DEBUG_MODE) {
        Log.d(tag, """
            ImageProxy Info:
            - Format: $format
            - Width: $width
            - Height: $height
            - Rotation: ${imageInfo.rotationDegrees}°
            - Timestamp: ${imageInfo.timestamp}
        """.trimIndent())
    }
}
