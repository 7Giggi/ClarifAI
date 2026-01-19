package luigi.tirocinio.clarifai.ml.manager

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import kotlinx.coroutines.*
import luigi.tirocinio.clarifai.data.model.ZoneInfo
import luigi.tirocinio.clarifai.utils.Constants
import kotlin.math.abs
import kotlin.math.sin

// Classe per gestire il feedback nella modalita ostacoli
// Genera beep stereo con frequenza e intervallo variabili in base alla distanza dall'ostacolo
class AudioFeedbackManager(private val context: Context) {

    // private val TAG = "AudioFeedbackManager"

    // AudioTrack per la generazione di toni audio personalizzati
    private var audioTrack: AudioTrack? = null

    // Vibrator per feedback tattile nei casi di pericolo
    private val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator

    // Ultima distanza elaborata per evitare aggiornamenti inutili se la variazione e minima
    private var lastDistance = Float.MAX_VALUE

    // Flag che indica se il feedback audio e attualmente attivo
    private var isPlaying = false

    // Job della coroutine che gestisce il loop continuo del feedback
    private var feedbackJob: Job? = null

    // Frequenza di campionamento audio standard in Hz
    private val sampleRate = 44100

    // Scope delle coroutine per operazioni audio in background
    private val scope = CoroutineScope(Dispatchers.Default + Job())

    // Funzione per aggiornare il feedback audio basandosi sulle informazioni della zona rilevata
    fun updateFeedback(zoneInfo: ZoneInfo) {
        // Aggiorna solo se la distanza cambia significativamente
        if (abs(zoneInfo.minDistance - lastDistance) < 0.30f && isPlaying) {
            return
        }

        lastDistance = zoneInfo.minDistance

        // Calcola parametri audio in base alla distanza e zona
        val audioParams = calculateAudioParams(zoneInfo)

        if (audioParams.shouldPlay) {
            startFeedbackLoop(audioParams, zoneInfo)
        } else {
            stopFeedback()
        }
    }

    // Funzione per calcolare i parametri audio in base al livello di pericolo
    private fun calculateAudioParams(zoneInfo: ZoneInfo): AudioParams {
        val dangerLevel = zoneInfo.dangerLevel

        return when (dangerLevel) {
            Constants.DangerZone.SAFE -> {
                // Nessun suono
                AudioParams(
                    shouldPlay = false,
                    frequency = 400,
                    intervalMs = 3000,
                    panning = zoneInfo.zone.getAudioPanning(),
                    durationMs = Constants.BEEP_DURATION_MS
                )
            }

            Constants.DangerZone.WARNING -> {
                // BEEP LENTO
                AudioParams(
                    shouldPlay = true,
                    frequency = 500,
                    intervalMs = 800,
                    panning = zoneInfo.zone.getAudioPanning(),
                    durationMs = 100
                )
            }

            Constants.DangerZone.CAUTION -> {
                // BEEP MEDIO
                AudioParams(
                    shouldPlay = true,
                    frequency = 700,
                    intervalMs = 400,
                    panning = zoneInfo.zone.getAudioPanning(),
                    durationMs = 120
                )
            }

            Constants.DangerZone.DANGER -> {
                // BEEP VELOCE
                AudioParams(
                    shouldPlay = true,
                    frequency = 1000,
                    intervalMs = 150,
                    panning = zoneInfo.zone.getAudioPanning(),
                    durationMs = 180
                )
            }
        }
    }

    // Funzione per avviare il loop di feedback audio continuo
    private fun startFeedbackLoop(params: AudioParams, zoneInfo: ZoneInfo) {
        // Cancella loop precedente
        feedbackJob?.cancel()
        stopCurrentAudio()

        isPlaying = true

        feedbackJob = scope.launch {
            while (isActive && isPlaying) {
                // Genera e riproduci beep
                playBeep(params)

                // Vibrazione sincronizzata
                triggerVibration(zoneInfo.dangerLevel)

                // Aspetta intervallo tra beep
                delay(params.intervalMs)
            }
        }
    }

    // Funzione per generare e riprodurre un singolo beep stereo con panning
    private fun playBeep(params: AudioParams) {
        try {
            stopCurrentAudio()

            val numSamples = ((params.durationMs / 1000.0) * sampleRate).toInt()
            val buffer = ShortArray(numSamples * 2)

            // Genera onda sinusoidale per il beep
            for (i in 0 until numSamples) {
                val angle = 2.0 * Math.PI * i / (sampleRate / params.frequency)
                val sample = (sin(angle) * Short.MAX_VALUE * 0.6).toInt().toShort()

                // Applica panning stereo
                val leftVolume = if (params.panning <= 0) 1.0f else (1.0f - params.panning)
                val rightVolume = if (params.panning >= 0) 1.0f else (1.0f + params.panning)

                buffer[i * 2] = (sample * leftVolume).toInt().toShort()
                buffer[i * 2 + 1] = (sample * rightVolume).toInt().toShort()
            }

            // Crea e riproduci AudioTrack
            audioTrack = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                        .build()
                )
                .setBufferSizeInBytes(buffer.size * 2)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()

            audioTrack?.write(buffer, 0, buffer.size)
            audioTrack?.play()

        } catch (e: Exception) {
            // Log.e(TAG, "Errore durante playback beep", e)
            e.printStackTrace()
        }
    }

    // Funzione per attivare la vibrazione in base al livello di pericolo
    private fun triggerVibration(dangerLevel: Constants.DangerZone) {
        if (!vibrator.hasVibrator()) {
            return
        }

        try {
            val pattern = when (dangerLevel) {
                Constants.DangerZone.SAFE -> return
                Constants.DangerZone.WARNING -> Constants.VIBRATION_PATTERN_WARNING
                Constants.DangerZone.CAUTION -> Constants.VIBRATION_PATTERN_CAUTION
                Constants.DangerZone.DANGER -> Constants.VIBRATION_PATTERN_DANGER
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(
                    VibrationEffect.createWaveform(pattern, -1)
                )
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(pattern, -1)
            }

        } catch (e: Exception) {
            // Log.e(TAG, "Errore vibrazione", e)
            e.printStackTrace()
        }
    }

    // Funzione per fermare l'audio corrente e rilasciare le risorse AudioTrack
    private fun stopCurrentAudio() {
        try {
            audioTrack?.stop()
            audioTrack?.release()
            audioTrack = null
        } catch (e: Exception) {
            // Log.e(TAG, "Errore durante stop audio", e)
            e.printStackTrace()
        }
    }

    // Funzione per fermare completamente il feedback audio e vibrazione
    fun stopFeedback() {
        isPlaying = false
        feedbackJob?.cancel()
        stopCurrentAudio()
        vibrator.cancel()
        lastDistance = Float.MAX_VALUE
    }

    // Funzione per rilasciare tutte le risorse quando il manager non e piu necessario
    fun release() {
        stopFeedback()
        scope.cancel()
        // Log.d(TAG, "AudioFeedbackManager rilasciato")
    }

    // Data class per contenere tutti i parametri del feedback audio
    private data class AudioParams(
        val shouldPlay: Boolean,
        val frequency: Int,
        val intervalMs: Long,
        val panning: Float,
        val durationMs: Int
    )
}