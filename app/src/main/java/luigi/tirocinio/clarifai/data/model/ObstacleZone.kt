package luigi.tirocinio.clarifai.data.model

import luigi.tirocinio.clarifai.utils.Constants

// Enum che rappresenta le 9 zone della griglia 3x3 in cui viene diviso lo schermo per il rilevamento ostacoli
// Ogni zona ha priorita, panning audio e descrizione specifici per fornire feedback contestuale all'utente
enum class ObstacleZone {
    TOP_LEFT,
    TOP_CENTER,
    TOP_RIGHT,
    MIDDLE_LEFT,
    MIDDLE_CENTER,
    MIDDLE_RIGHT,
    BOTTOM_LEFT,
    BOTTOM_CENTER,
    BOTTOM_RIGHT;

    // Funzione per ottenere la priorita della zona dove 1 e massima priorita e 6 e minima
    fun getPriority(): Int {
        return when (this) {
            MIDDLE_CENTER -> 1
            MIDDLE_LEFT, MIDDLE_RIGHT -> 2
            BOTTOM_CENTER -> 3
            BOTTOM_LEFT, BOTTOM_RIGHT -> 4
            TOP_CENTER -> 5
            TOP_LEFT, TOP_RIGHT -> 6
        }
    }

    // Funzione per ottenere il valore di panning audio stereo in base alla posizione orizzontale della zona
    fun getAudioPanning(): Float {
        return when (this) {
            TOP_LEFT, MIDDLE_LEFT, BOTTOM_LEFT -> Constants.AUDIO_PAN_LEFT
            TOP_CENTER, MIDDLE_CENTER, BOTTOM_CENTER -> Constants.AUDIO_PAN_CENTER
            TOP_RIGHT, MIDDLE_RIGHT, BOTTOM_RIGHT -> Constants.AUDIO_PAN_RIGHT
        }
    }

    // Funzione per ottenere la descrizione testuale della zona in italiano
    fun getDescription(): String {
        return when (this) {
            TOP_LEFT -> "Alto Sinistra"
            TOP_CENTER -> "Alto Centro"
            TOP_RIGHT -> "Alto Destra"
            MIDDLE_LEFT -> "Centro Sinistra"
            MIDDLE_CENTER -> "Centro"
            MIDDLE_RIGHT -> "Centro Destra"
            BOTTOM_LEFT -> "Basso Sinistra"
            BOTTOM_CENTER -> "Basso Centro"
            BOTTOM_RIGHT -> "Basso Destra"
        }
    }
}

// Data class per contenere tutte le informazioni di una zona analizzata dalla depth map
// Include distanze, numero di pixel e livello di pericolo calcolato
data class ZoneInfo(
    val zone: ObstacleZone,
    val avgDistance: Float,
    val minDistance: Float,
    val pixelCount: Int,
    val dangerLevel: Constants.DangerZone
) {
    companion object {
        // Funzione per calcolare il livello di pericolo in base alla distanza minima rilevata
        fun calculateDangerLevel(distance: Float): Constants.DangerZone {
            return when {
                distance <= Constants.DISTANCE_DANGER -> Constants.DangerZone.DANGER
                distance <= Constants.DISTANCE_CAUTION -> Constants.DangerZone.CAUTION
                distance <= Constants.DISTANCE_WARNING -> Constants.DangerZone.WARNING
                else -> Constants.DangerZone.SAFE
            }
        }

    }

    // Funzione per calcolare il punteggio di rischio combinando distanza e priorita della zona
    fun getRiskScore(): Float {
        val priorityWeight = zone.getPriority().toFloat()
        return minDistance * priorityWeight
    }

    // Funzione per verificare se la zona richiede attenzione dell'utente
    fun requiresAttention(): Boolean {
        return dangerLevel != Constants.DangerZone.SAFE
    }
}