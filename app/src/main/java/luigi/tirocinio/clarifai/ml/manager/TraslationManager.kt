package luigi.tirocinio.clarifai.ml.manager

import android.content.Context
import android.util.Log
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions

class TranslationManager(private val context: Context) {

    //private val TAG = "TranslationManager"

    private val languageIdentifier = LanguageIdentification.getClient()
    private val translators = mutableMapOf<String, Translator>()
    private val targetLanguage = TranslateLanguage.ITALIAN

    //Funzione per tradurre il testo in italiano
    fun translateToItalian(
        text: String,
        onSuccess: (translatedText: String, detectedLanguage: String) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        if (text.isBlank()) {
            onSuccess(text, "unknown")
            return
        }

        //Log.d(TAG, "Identificazione lingua per testo: ${text.take(50)}...")

        languageIdentifier.identifyLanguage(text)
            .addOnSuccessListener { languageCode ->
                //Log.d(TAG, "Lingua identificata: $languageCode")

                //Se è già italiano o non riconosce la linga, non tradurre
                if (languageCode == TranslateLanguage.ITALIAN ||
                    languageCode == "und") {
                    //Log.d(TAG, "Testo gia in italiano o lingua non identificata")
                    onSuccess(text, languageCode)
                    return@addOnSuccessListener
                }

                //Altrimenti traduci
                translate(text, languageCode, onSuccess, onFailure)
            }
            .addOnFailureListener { exception ->
                //Log.e(TAG, "Errore identificazione lingua", exception)
                onFailure(exception)
            }
    }

    //
    private fun translate(
        text: String,
        sourceLanguage: String,
        onSuccess: (translatedText: String, detectedLanguage: String) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        val translatorKey = "$sourceLanguage-$targetLanguage"

        //Riusa translator se già esistente
        val translator = translators.getOrPut(translatorKey) {
            val options = TranslatorOptions.Builder()
                .setSourceLanguage(sourceLanguage)
                .setTargetLanguage(targetLanguage)
                .build()
            Translation.getClient(options)
        }

        //Scarica modello se necessario
        val conditions = DownloadConditions.Builder()
            .requireWifi()
            .build()

        translator.downloadModelIfNeeded(conditions)
            .addOnSuccessListener {
                //Log.d(TAG, "Modello traduzione $sourceLanguage->IT pronto")

                //Esegui traduzione
                translator.translate(text)
                    .addOnSuccessListener { translatedText ->
                        //Log.d(TAG, "Traduzione completata: ${translatedText.take(50)}...")
                        onSuccess(translatedText, sourceLanguage)
                    }
                    .addOnFailureListener { exception ->
                        //Log.e(TAG, "Errore traduzione", exception)
                        onFailure(exception)
                    }
            }
            .addOnFailureListener { exception ->
                //Log.e(TAG, "Errore download modello traduzione", exception)
                onFailure(exception)
            }
    }

    //Funzione per pre-scaricare i linguaggi più comuni
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
                    //Log.d(TAG, "Modello $lang->IT scaricato")
                    completed++
                    if (completed == total) onComplete()
                }
                .addOnFailureListener { e ->
                    //Log.e(TAG, "Errore download modello $lang->IT", e)
                    completed++
                    if (completed == total) onComplete()
                }
        }
    }

    //Funzione per rilasciare le risorse
    fun shutdown() {
        languageIdentifier.close()
        translators.values.forEach { it.close() }
        translators.clear()
        //Log.d(TAG, "TranslationManager chiuso")
    }
}
