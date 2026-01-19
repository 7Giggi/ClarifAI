package luigi.tirocinio.clarifai.ml.manager

import android.content.Context
import android.graphics.Bitmap
import ai.onnxruntime.*
import luigi.tirocinio.clarifai.utils.Constants

// Classe per gestire l'inferenza del modello DepthAnythingV2 tramite ONNX Runtime
// Carica il modello lo esegue su CPU con ottimizzazioni
// Produce mappe di profondità
class DepthAnythingManager(private val context: Context) {

    //private val TAG = "DepthAnythingManager"
    // Sessione ONNX Runtime per l'esecuzione del modello
    private var ortSession: OrtSession? = null
    // Environment ONNX Runtime condiviso per tutte le sessioni
    private var ortEnvironment: OrtEnvironment? = null
    // Flag che indica se il modello e stato caricato e inizializzato correttamente
    private var isInitialized = false

    init {
        initializeModel()
    }

    // Funzione per caricare il modello ONNX dagli assets e configurare le opzioni di esecuzione
    private fun initializeModel() {
        try {
            // Log.d(TAG, "Initializing INT8 model...")
            ortEnvironment = OrtEnvironment.getEnvironment()
            val modelBytes = context.assets.open(Constants.DEPTH_MODEL_PATH).readBytes()
            // Log.d(TAG, "Model loaded: \${modelBytes.size / 1024} KB")

            val sessionOptions = OrtSession.SessionOptions().apply {
                setIntraOpNumThreads(4)
                setInterOpNumThreads(2)
                setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
                setExecutionMode(OrtSession.SessionOptions.ExecutionMode.PARALLEL)
                // Log.d(TAG, "CPU-only mode (4 threads, \${Runtime.getRuntime().availableProcessors()} cores available)")
            }

            ortSession = ortEnvironment?.createSession(modelBytes, sessionOptions)
            isInitialized = true
            //val inputInfo = ortSession?.inputInfo?.entries?.firstOrNull()
            //val outputInfo = ortSession?.outputInfo?.entries?.firstOrNull()
            // Log.d(TAG, "Depth Anything V2 Small INT8 initialized")
            // Log.d(TAG, " Input: \${inputInfo?.key} - \${inputInfo?.value?.info}")
            // Log.d(TAG, " Output: \${outputInfo?.key} - \${outputInfo?.value?.info}")
        } catch (e: Exception) {
            // Log.e(TAG, "Error initializing model", e)
            e.printStackTrace()
        }
    }

    // Funzione per stimare la profondita da una bitmap eseguendo preprocessing, inferenza ONNX e postprocessing
// Restituisce una matrice di valori float dove ogni elemento rappresenta la distanza in metri per quel pixel
// Ritorna null se il modello non e inizializzato o se si verifica un errore durante l'elaborazione
    fun estimateDepth(bitmap: Bitmap): Array<FloatArray>? {
        // Verifica che il modello ONNX sia stato caricato correttamente prima di procedere
        if (!isInitialized || ortSession == null) {
            // Log.w(TAG, "Model not initialized")
            return null
        }

        return try {
            // FASE 1: PREPROCESSING
            // Converte la bitmap in un tensore ONNX normalizzato con layout NCHW
            // Ridimensiona l'immagine a DEPTH_INPUT_SIZE x DEPTH_INPUT_SIZE
            // Applica normalizzazione ImageNet (mean/std per ogni canale RGB)
            val inputTensor = preprocessBitmap(bitmap)

            // FASE 2: INFERENZA
            // Ottiene il nome dell'input del modello ONNX
            val inputName = ortSession?.inputNames?.firstOrNull() ?: "image"

            // Crea una mappa con il tensore di input associato al nome corretto
            val inputs = mapOf(inputName to inputTensor)

            // Esegue l'inferenza del modello DepthAnythingV2 sul tensore di input
            // Il modello produce una mappa di profondita raw con valori non normalizzati
            val outputs = ortSession?.run(inputs)

            // FASE 3: ESTRAZIONE OUTPUT
            // Estrae il primo tensore di output che contiene la mappa di profondita raw
            val depthTensor = outputs?.get(0) as? OnnxTensor
            if (depthTensor == null) {
                // Se il tensore di output non e valido, rilascia le risorse e ritorna null
                // Log.w(TAG, "Failed to get output tensor")
                outputs?.close()
                return null
            }

            // FASE 4: POSTPROCESSING
            // Normalizza i valori raw della depth map nel range DEPTH_MIN_METERS - DEPTH_MAX_METERS
            // Estrae la matrice di profondita dal tensore ONNX
            val depthMap = extractDepthMap(depthTensor)

            // Rilascia le risorse ONNX allocate per l'inferenza
            outputs.close()

            // Ritorna la mappa di profondita normalizzata in metri
            depthMap
        } catch (e: Exception) {
            // In caso di errore durante qualsiasi fase, logga l'eccezione e ritorna null
            // Log.e(TAG, "Error during depth estimation", e)
            e.printStackTrace()
            null
        }
    }

    // Funzione per preprocessare una bitmap in un tensore ONNX normalizzato con layout NCHW
    // Ridimensiona l'immagine, estrae i valori RGB e applica normalizzazione ImageNet
    // Il layout NCHW significa: [Batch, Channels, Height, Width]
    private fun preprocessBitmap(bitmap: Bitmap): OnnxTensor {
        // Dimensione di input
        val inputSize = Constants.DEPTH_INPUT_SIZE
        // Ridimensiona la bitmap alla dimensione richiesta dal modello usando interpolazione bilineare
        val resizedBitmap = Bitmap.createScaledBitmap(bitmap, inputSize, inputSize, true)
        // Array per contenere i valori ARGB dei pixel in formato intero
        val intValues = IntArray(inputSize * inputSize)
        // Estrae tutti i pixel dalla bitmap ridimensionata nell'array intValues
        resizedBitmap.getPixels(intValues, 0, inputSize, 0, 0, inputSize, inputSize)
        // Calcola il numero totale di float necessari: 3 canali RGB x altezza x larghezza
        val totalFloats = 3 * inputSize * inputSize
        // Alloca un buffer diretto in memoria nativa per prestazioni ottimali con ONNX
        // Ogni float occupa 4 bytes, quindi totalFloats * 4
        val byteBuffer = java.nio.ByteBuffer.allocateDirect(totalFloats * 4)
        // Imposta l'ordine dei byte a quello nativo del sistema per compatibilita
        byteBuffer.order(java.nio.ByteOrder.nativeOrder())
        // Crea una vista float del buffer per scrivere i valori normalizzati
        val floatBuffer = byteBuffer.asFloatBuffer()
        // Valori medi ImageNet per normalizzazione (RGB)
        val mean = floatArrayOf(123.675f, 116.28f, 103.53f)
        // Deviazioni standard ImageNet per normalizzazione (RGB)
        val std = floatArrayOf(58.395f, 57.12f, 57.375f)

        // Itera sui canali in ordine NCHW
        for (c in 0..2) {
            // Itera sulle righe
            for (y in 0 until inputSize) {
                // Itera sulle colonne
                for (x in 0 until inputSize) {
                    // Ottiene il valore ARGB del pixel alla posizione (x, y)
                    val pixel = intValues[y * inputSize + x]

                    // Estrae il canale specifico dal valore ARGB usando bit shifting
                    val value = when (c) {
                        0 -> ((pixel shr 16) and 0xFF).toFloat()  // Rosso
                        1 -> ((pixel shr 8) and 0xFF).toFloat()   // Verde
                        else -> (pixel and 0xFF).toFloat()        // Blu
                    }

                    // Applica normalizzazione ImageNet
                    floatBuffer.put((value - mean[c]) / std[c])
                }
            }
        }

        // Riporta la posizione del buffer all'inizio per la lettura da parte di ONNX
        byteBuffer.rewind()

        // Definisce la forma del tensore: [batch_size=1, channels=3, height, width]
        val shape = longArrayOf(1, 3, inputSize.toLong(), inputSize.toLong())

        // Crea e restituisce il tensore ONNX dal buffer con la forma specificata
        return OnnxTensor.createTensor(ortEnvironment, byteBuffer, shape, OnnxJavaType.FLOAT)
    }


    // Funzione per estrarre e normalizzare la mappa di profondita dal tensore di output ONNX
    // Converte i valori raw del modello (range arbitrario) in distanze metriche
    private fun extractDepthMap(tensor: OnnxTensor): Array<FloatArray> {
        // Ottiene la forma del tensore di output dal modello ONNX
        val shape = tensor.info.shape

        // Estrae altezza e larghezza gestendo due possibili formati del tensore
        // Formato 4D: [batch=1, channels=1, height, width] → shape[2] e shape[3]
        // Formato 3D: [batch=1, height, width] → shape[1] e shape[2]
        val height = if (shape.size == 4) shape[2].toInt() else shape[1].toInt()
        val width = if (shape.size == 4) shape[3].toInt() else shape[2].toInt()
        // Log.d(TAG, "Output shape: ${shape.contentToString()} → ${height}x${width}")

        // Ottiene il buffer di float contenente i valori raw di profondita
        val floatBuffer = tensor.floatBuffer

        // Crea la matrice per contenere i valori raw estratti dal tensore
        val rawDepth = Array(height) { FloatArray(width) }

        // Riporta il buffer all'inizio per iniziare la lettura
        floatBuffer.rewind()

        // FASE 1: ESTRAZIONE DEI VALORI RAW
        // Copia tutti i valori dal buffer ONNX alla matrice rawDepth
        for (y in 0 until height) {
            for (x in 0 until width) {
                rawDepth[y][x] = floatBuffer.get()
            }
        }

        // FASE 2: CALCOLO MIN-MAX DEI VALORI RAW
        // Trova il valore minimo e massimo nella depth map raw per normalizzare
        var minRaw = Float.MAX_VALUE
        var maxRaw = Float.MIN_VALUE
        for (y in 0 until height) {
            for (x in 0 until width) {
                val value = rawDepth[y][x]
                // Controlla che il valore sia finito (non NaN o infinito)
                if (value.isFinite()) {
                    if (value < minRaw) minRaw = value
                    if (value > maxRaw) maxRaw = value
                }
            }
        }

        // Log.d(TAG, "RAW output: min=${formatFloat(minRaw, 4)}, max=${formatFloat(maxRaw, 4)}")

        // Crea la matrice finale che conterra i valori normalizzati in metri
        val depthMap = Array(height) { FloatArray(width) }

        // Calcola il range dei valori raw (differenza tra max e min)
        val range = maxRaw - minRaw

        // FASE 3: NORMALIZZAZIONE IN METRI
        if (range < 0.0001f) {
            // Se il range e troppo piccolo (immagine uniforme o errore), usa un valore fisso di sicurezza
            // Questo previene divisioni per zero o valori instabili
            // Log.w(TAG, "Range troppo piccolo, uso depth fisso")
            for (y in 0 until height) {
                for (x in 0 until width) {
                    depthMap[y][x] = 0.3f  // Distanza di default a 30cm
                }
            }
        } else {
            // Normalizzazione min-max seguita da mapping al range metrico desiderato
            for (y in 0 until height) {
                for (x in 0 until width) {
                    // Normalizza il valore raw nell'intervallo [0, 1]
                    val normalized = (rawDepth[y][x] - minRaw) / range

                    // Mappa il valore normalizzato [0, 1] al range metrico [DEPTH_MIN_METERS, DEPTH_MAX_METERS]
                    val depth = Constants.DEPTH_MIN_METERS + normalized * (Constants.DEPTH_MAX_METERS - Constants.DEPTH_MIN_METERS)
                    depthMap[y][x] = depth.coerceIn(Constants.DEPTH_MIN_METERS, Constants.DEPTH_MAX_METERS)
                }
            }
        }

        // Restituisce la mappa di profondita normalizzata in metri
        return depthMap
    }


    // Funzione per verificare se il modello e pronto per l'uso
    fun isReady(): Boolean = isInitialized && ortSession != null

    // Funzione per rilasciare le risorse ONNX Runtime
    fun shutdown() {
        try {
            ortSession?.close()
            ortSession = null
            isInitialized = false
            // Log.d(TAG, "DepthAnythingManager closed")
        } catch (e: Exception) {
            // Log.e(TAG, "Error during shutdown", e)
            e.printStackTrace()
        }
    }
}