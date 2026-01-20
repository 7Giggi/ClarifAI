package luigi.tirocinio.clarifai.ml.manager

import android.graphics.Bitmap
import android.util.Log
import luigi.tirocinio.clarifai.utils.Constants
import luigi.tirocinio.clarifai.utils.resize
import luigi.tirocinio.clarifai.utils.toBase64
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

// Classe per gestire le chiamate API a Gemini per la descrizione dell'ambiente
// Gestisce la costruzione delle richieste JSON, l'invio tramite HTTP POST e il parsing delle risposte
// Implementa retry automatico con backoff esponenziale in caso di fallimento
class GeminiManager {

    // Scope per le coroutine che eseguono le chiamate API in background
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Interfaccia per i callback di risposta dalle chiamate Gemini
    interface GeminiCallback {
        fun onSuccess(description: String)
        fun onError(error: String)
        fun onLoading()
    }

    // Funzione per eseguire la chiamata HTTP POST all'API di Gemini Vision
    private suspend fun callGeminiVisionAPI(
        base64Image: String,
        prompt: String
    ): String? = withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        try {
            val apiUrl = "${Constants.GEMINI_API_URL}${Constants.GEMINI_MODEL}:generateContent?key=${Constants.GEMINI_API_KEY}"
            val url = URL(apiUrl)
            connection = url.openConnection() as HttpURLConnection
            connection.apply {
                requestMethod = "POST"
                doOutput = true
                doInput = true
                setRequestProperty("Content-Type", "application/json")
                connectTimeout = (Constants.GEMINI_TIMEOUT_SECONDS * 1000).toInt()
                readTimeout = (Constants.GEMINI_TIMEOUT_SECONDS * 1000).toInt()
            }

            val requestBody = buildGeminiRequest(base64Image, prompt)
            if (Constants.DEBUG_MODE) {
                Log.d(Constants.LOG_TAG, "Gemini API Request: \${requestBody.toString(2)}")
            }

            val writer = OutputStreamWriter(connection.outputStream)
            writer.write(requestBody.toString())
            writer.flush()
            writer.close()

            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val reader = BufferedReader(InputStreamReader(connection.inputStream))
                val response = StringBuilder()
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    response.append(line)
                }
                reader.close()

                val responseJson = JSONObject(response.toString())
                if (Constants.DEBUG_MODE) {
                     Log.d(Constants.LOG_TAG, "Gemini API Response: \${responseJson.toString(2)}")
                }
                return@withContext parseGeminiResponse(responseJson)
            } else {
                val errorReader = BufferedReader(InputStreamReader(connection.errorStream))
                val errorResponse = StringBuilder()
                var line: String?
                while (errorReader.readLine().also { line = it } != null) {
                    errorResponse.append(line)
                }
                errorReader.close()
                Log.e(Constants.LOG_TAG, "Gemini API Error (\$responseCode): \$errorResponse")
                return@withContext null
            }
        } catch (e: Exception) {
            Log.e(Constants.LOG_TAG, "Errore connessione Gemini: \${e.message}", e)
            return@withContext null
        } finally {
            connection?.disconnect()
        }
    }

    // Funzione per costruire il corpo della richiesta JSON con immagine e prompt
    private fun buildGeminiRequest(base64Image: String, prompt: String): JSONObject {
        return JSONObject().apply {
            put("contents", JSONArray().apply {
                put(JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply {
                            put("text", prompt)
                        })
                        put(JSONObject().apply {
                            put("inline_data", JSONObject().apply {
                                put("mime_type", "image/jpeg")
                                put("data", base64Image)
                            })
                        })
                    })
                })
            })
            put("generationConfig", JSONObject().apply {
                put("temperature", 0.4)
                put("topK", 32)
                put("topP", 1)
                put("maxOutputTokens", 200)
            })
            put("safetySettings", JSONArray().apply {
                addSafetyFilter("HARM_CATEGORY_HARASSMENT", "BLOCK_MEDIUM_AND_ABOVE")
                addSafetyFilter("HARM_CATEGORY_HATE_SPEECH", "BLOCK_MEDIUM_AND_ABOVE")
                addSafetyFilter("HARM_CATEGORY_SEXUALLY_EXPLICIT", "BLOCK_MEDIUM_AND_ABOVE")
                addSafetyFilter("HARM_CATEGORY_DANGEROUS_CONTENT", "BLOCK_MEDIUM_AND_ABOVE")
            })
        }
    }

    // Funzione di estensione per aggiungere un filtro di sicurezza all'array JSON
    private fun JSONArray.addSafetyFilter(category: String, threshold: String) {
        put(JSONObject().apply {
            put("category", category)
            put("threshold", threshold)
        })
    }

    // Funzione per estrarre il testo della descrizione dalla risposta JSON di Gemini
    private fun parseGeminiResponse(responseJson: JSONObject): String? {
        return try {
            val candidates = responseJson.getJSONArray("candidates")
            if (candidates.length() > 0) {
                val firstCandidate = candidates.getJSONObject(0)
                val content = firstCandidate.getJSONObject("content")
                val parts = content.getJSONArray("parts")
                if (parts.length() > 0) {
                    val text = parts.getJSONObject(0).getString("text")
                    text.trim()
                        .replace("**", "")
                        .replace("*", "")
                } else {
                    null
                }
            } else {
                null
            }
        } catch (e: Exception) {
            // Log.e(Constants.LOG_TAG, "Errore parsing risposta Gemini: \${e.message}", e)
            null
        }
    }

    // Funzione per analizzare un'immagine con retry automatico in caso di fallimento
    fun describeSceneWithRetry(
        bitmap: Bitmap,
        maxRetries: Int = Constants.GEMINI_MAX_RETRIES,
        callback: GeminiCallback
    ) {
        scope.launch {
            callback.onLoading()
            var attemptCount = 0
            var success = false
            var lastError = "Errore sconosciuto"
            while (attemptCount < maxRetries && !success) {
                attemptCount++
                try {
                    val optimizedBitmap = bitmap.resize(1024, 1024)
                    val base64Image = optimizedBitmap.toBase64(quality = 80)
                    val response = callGeminiVisionAPI(base64Image, Constants.GEMINI_PROMPT_NAVIGATION)
                    if (response != null) {
                        withContext(Dispatchers.Main) {
                            callback.onSuccess(response)
                        }
                        success = true
                    } else {
                        lastError = "Nessuna risposta da Gemini (tentativo \$attemptCount/\$maxRetries)"
                        Log.w(Constants.LOG_TAG, lastError)
                        if (attemptCount < maxRetries) {
                            delay(1000L * attemptCount)
                        }
                    }
                } catch (e: Exception) {
                    lastError = "Errore: \${e.message} (tentativo \$attemptCount/\$maxRetries)"
                    // Log.e(Constants.LOG_TAG, lastError, e)
                    if (attemptCount < maxRetries) {
                        delay(1000L * attemptCount)
                    }
                }
            }
            if (!success) {
                withContext(Dispatchers.Main) {
                    callback.onError(lastError)
                }
            }
        }
    }

    // Funzione per rilasciare le risorse e cancellare lo scope delle coroutine
    fun shutdown() {
        scope.cancel()
        // Log.d(Constants.LOG_TAG, "Gemini Manager shutdown")
    }
}