package luigi.tirocinio.clarifai.ml.manager

import android.content.Context
import android.util.Log
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions

// Classe per gestire la traduzione automatica di testi utilizzando ML Kit Translation
// Identifica automaticamente la lingua del testo e lo traduce in italiano
// Riutilizza i translator gia creati per ottimizzare le prestazioni
class TranslationManager(private val context: Context) {

    // Client ML Kit per identificare la lingua del testo
    private val languageIdentifier = LanguageIdentification.getClient()

    // Mappa che memorizza i translator gia creati per evitare duplicazioni
    private val translators = mutableMapOf<String, Translator>()

    // Lingua di destinazione per tutte le traduzioni
    private val targetLanguage = TranslateLanguage.ITALIAN

    // Funzione per tradurre un testo in italiano identificando automaticamente la lingua sorgente
    fun translateToItalian(
        text: String,
        onSuccess: (translatedText: String, detectedLanguage: String) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        if (text.isBlank()) {
            onSuccess(text, "unknown")
            return
        }

        // Log.d(TAG, "Identificazione lingua per testo: \${text.take(50)}...")
        languageIdentifier.identifyLanguage(text)
            .addOnSuccessListener { languageCode ->
                // Log.d(TAG, "Lingua identificata: \$languageCode")
                if (languageCode == TranslateLanguage.ITALIAN || languageCode == "und") {
                    // Log.d(TAG, "Testo gia in italiano o lingua non identificata")
                    onSuccess(text, languageCode)
                    return@addOnSuccessListener
                }
                translate(text, languageCode, onSuccess, onFailure)
            }
            .addOnFailureListener { exception ->
                // Log.e(TAG, "Errore identificazione lingua", exception)
                onFailure(exception)
            }
    }

    // Funzione per tradurre un testo da una lingua specifica all'italiano
    private fun translate(
        text: String,
        sourceLanguage: String,
        onSuccess: (translatedText: String, detectedLanguage: String) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        val translatorKey = "\$sourceLanguage-\$targetLanguage"
        val translator = translators.getOrPut(translatorKey) {
            val options = TranslatorOptions.Builder()
                .setSourceLanguage(sourceLanguage)
                .setTargetLanguage(targetLanguage)
                .build()
            Translation.getClient(options)
        }

        val conditions = DownloadConditions.Builder()
            .requireWifi()
            .build()

        translator.downloadModelIfNeeded(conditions)
            .addOnSuccessListener {
                // Log.d(TAG, "Modello traduzione \$sourceLanguage->IT pronto")
                translator.translate(text)
                    .addOnSuccessListener { translatedText ->
                        // Log.d(TAG, "Traduzione completata: \${translatedText.take(50)}...")
                        onSuccess(translatedText, sourceLanguage)
                    }
                    .addOnFailureListener { exception ->
                        // Log.e(TAG, "Errore traduzione", exception)
                        onFailure(exception)
                    }
            }
            .addOnFailureListener { exception ->
                // Log.e(TAG, "Errore download modello traduzione", exception)
                onFailure(exception)
            }
    }

    // Funzione per pre-scaricare i modelli di traduzione delle lingue piu comuni
    fun preloadCommonLanguages(
        languages: List<String> = listOf(
            TranslateLanguage.ENGLISH,
            TranslateLanguage.FRENCH,
            TranslateLanguage.GERMAN,
            TranslateLanguage.SPANISH
        ),
        onComplete: () -> Unit = {}
    ) {
        val conditions = DownloadConditions.Builder()
            .requireWifi()
            .build()

        var completed = 0
        val total = languages.size

        languages.forEach { lang ->
            val options = TranslatorOptions.Builder()
                .setSourceLanguage(lang)
                .setTargetLanguage(targetLanguage)
                .build()

            val translator = Translation.getClient(options)
            translator.downloadModelIfNeeded(conditions)
                .addOnSuccessListener {
                    // Log.d(TAG, "Modello \$lang->IT scaricato")
                    completed++
                    if (completed == total) onComplete()
                }
                .addOnFailureListener { e ->
                    // Log.e(TAG, "Errore download modello \$lang->IT", e)
                    completed++
                    if (completed == total) onComplete()
                }
        }
    }

    // Funzione per rilasciare tutte le risorse dei translator e chiudere il language identifier
    fun shutdown() {
        languageIdentifier.close()
        translators.values.forEach { it.close() }
        translators.clear()
        // Log.d(TAG, "TranslationManager chiuso")
    }
}