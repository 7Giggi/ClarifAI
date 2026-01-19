package luigi.tirocinio.clarifai.ml.manager

import luigi.tirocinio.clarifai.data.model.ObstacleZone
import luigi.tirocinio.clarifai.data.model.ZoneInfo
import luigi.tirocinio.clarifai.utils.Constants

// Classe per analizzare le mappe di profondita e rilevare ostacoli
// Divide l'immagine in 9 zone e calcola distanze minime e medie per ogni zona
// Applica temporal filtering per ridurre il rumore e stabilizzare le letture tra frame consecutivi
class ObstacleAnalyzer {

    // Mappa di profondita del frame precedente usata per il temporal filtering
    private var previousDepthMap: Array<FloatArray>? = null

    // Peso assegnato al frame corrente nel temporal filtering
    private val temporalWeight = 0.7f

    // Funzione per analizzare una mappa di profondita e restituire la zona piu pericolosa
    fun analyzeDepthMap(depthMap: Array<FloatArray>): ZoneInfo? {
        if (depthMap.isEmpty() || depthMap[0].isEmpty()) {
            return null
        }

        try {
            val stabilizedDepthMap = applyTemporalFiltering(depthMap)
            val zonesData = divideIntoZones(stabilizedDepthMap)
            val zoneInfoList = zonesData.map { (zone, depthValues) ->
                createZoneInfoWithPercentile(zone, depthValues)
            }
            val mostDangerousZone = findMostDangerousZone(zoneInfoList)
            return mostDangerousZone
        } catch (e: Exception) {
            // Log.e("ObstacleAnalyzer", "Errore durante analisi", e)
            return null
        }
    }

    // Funzione per applicare un filtro temporale che media i valori tra il frame corrente e quello precedente
    private fun applyTemporalFiltering(currentDepth: Array<FloatArray>): Array<FloatArray> {
        val height = currentDepth.size
        val width = currentDepth[0].size
        if (previousDepthMap == null ||
            previousDepthMap!!.size != height ||
            previousDepthMap!![0].size != width) {
            previousDepthMap = currentDepth.map { it.clone() }.toTypedArray()
            return currentDepth
        }

        val stabilized = Array(height) { FloatArray(width) }
        for (y in 0 until height) {
            for (x in 0 until width) {
                val current = currentDepth[y][x]
                val previous = previousDepthMap!![y][x]
                stabilized[y][x] = if (current in Constants.DEPTH_MIN_METERS..Constants.DEPTH_MAX_METERS && previous in Constants.DEPTH_MIN_METERS..Constants.DEPTH_MAX_METERS) {
                    temporalWeight * current + (1 - temporalWeight) * previous
                } else {
                    current
                }
            }
        }
        previousDepthMap = stabilized.map { it.clone() }.toTypedArray()
        return stabilized
    }

    // Funzione per resettare il filtro temporale
    fun reset() {
        previousDepthMap = null
    }

    // Funzione per dividere la mappa di profondita in 9 zone e raccogliere i valori di profondita per ciascuna
// Esclude i margini esterni dove i modelli di depth estimation sono meno accurati
// Crea una griglia 3x3 (TOP, MIDDLE, BOTTOM x LEFT, CENTER, RIGHT) per analisi zonale
    private fun divideIntoZones(depthMap: Array<FloatArray>): Map<ObstacleZone, List<Float>> {
        val height = depthMap.size
        val width = depthMap[0].size
        // Margini da escludere sui bordi
        val marginY = (height * 0.03f).toInt()
        val marginX = (width * 0.03f).toInt()
        // Altezza della regione valida dopo aver escluso i margini
        val validHeight = height - (2 * marginY)
        val validWidth = width - (2 * marginX)

        // Misure di ciascuna delle 3 righe di zone (TOP, MIDDLE, BOTTOM)
        val zoneHeight = validHeight / 3
        val zoneWidth = validWidth / 3

        // Mappa che associa ogni zona a una lista di valori di profondita rilevati in quella zona
        val zonesMap = mutableMapOf<ObstacleZone, MutableList<Float>>()
        ObstacleZone.values().forEach { zonesMap[it] = mutableListOf() }

        // Itera solo sulla regione valida escludendo i margini
        for (row in marginY until (height - marginY)) {
            for (col in marginX until (width - marginX)) {
                // Coordinate relative alla regione valida (partendo da 0)
                val relativeRow = row - marginY
                val relativeCol = col - marginX

                // Determina a quale delle 9 zone appartiene questo pixel
                val zone = getZoneForPixel(relativeRow, relativeCol, zoneHeight, zoneWidth)

                // Valore di profondita in metri per questo pixel
                val value = depthMap[row][col]

                // Aggiunge il valore solo se rientra nel range valido di profondita
                if (value in Constants.DEPTH_MIN_METERS..Constants.DEPTH_MAX_METERS) {
                    zonesMap[zone]?.add(value)
                }
            }
        }
        return zonesMap
    }


    // Funzione per creare un ZoneInfo calcolando distanza minima tramite percentile e distanza media
    private fun createZoneInfoWithPercentile(
        zone: ObstacleZone,
        depthValues: List<Float>
    ): ZoneInfo {
        if (depthValues.isEmpty() || depthValues.size < 50) {
            return ZoneInfo(
                zone = zone,
                avgDistance = Float.MAX_VALUE,
                minDistance = Float.MAX_VALUE,
                pixelCount = 0,
                dangerLevel = Constants.DangerZone.SAFE
            )
        }

        val validValues = depthValues.filter { it in Constants.DEPTH_MIN_METERS..Constants.DEPTH_MAX_METERS }
        if (validValues.isEmpty()) {
            return ZoneInfo(
                zone = zone,
                avgDistance = Float.MAX_VALUE,
                minDistance = Float.MAX_VALUE,
                pixelCount = 0,
                dangerLevel = Constants.DangerZone.SAFE
            )
        }

        val sortedValues = validValues.sorted()
        val percentile = when (zone) {
            ObstacleZone.MIDDLE_CENTER -> 0.01f
            ObstacleZone.BOTTOM_CENTER -> 0.015f
            ObstacleZone.MIDDLE_LEFT,
            ObstacleZone.MIDDLE_RIGHT -> 0.02f
            else -> 0.03f
        }

        val percentileIndex = (sortedValues.size * percentile).toInt().coerceIn(0, sortedValues.size - 1)
        val minDistance = sortedValues[percentileIndex]
        val avgDistance = sortedValues.average().toFloat()
        val dangerLevel = ZoneInfo.calculateDangerLevel(minDistance)

        return ZoneInfo(
            zone = zone,
            avgDistance = avgDistance,
            minDistance = minDistance,
            pixelCount = validValues.size,
            dangerLevel = dangerLevel
        )
    }

    // Funzione per determinare in quale zona si trova un pixel in base alla sua posizione
    private fun getZoneForPixel(row: Int, col: Int, zoneHeight: Int, zoneWidth: Int): ObstacleZone {
        val zoneRow = minOf(row / zoneHeight, 2)
        val zoneCol = minOf(col / zoneWidth, 2)
        return when (zoneRow * 3 + zoneCol) {
            0 -> ObstacleZone.TOP_LEFT
            1 -> ObstacleZone.TOP_CENTER
            2 -> ObstacleZone.TOP_RIGHT
            3 -> ObstacleZone.MIDDLE_LEFT
            4 -> ObstacleZone.MIDDLE_CENTER
            5 -> ObstacleZone.MIDDLE_RIGHT
            6 -> ObstacleZone.BOTTOM_LEFT
            7 -> ObstacleZone.BOTTOM_CENTER
            8 -> ObstacleZone.BOTTOM_RIGHT
            else -> ObstacleZone.MIDDLE_CENTER
        }
    }

    // Funzione per trovare la zona piu pericolosa applicando penalita di priorita in base alla posizione
    private fun findMostDangerousZone(zones: List<ZoneInfo>): ZoneInfo {
        val dangerousZones = zones.filter { it.requiresAttention() }
        return if (dangerousZones.isNotEmpty()) {
            dangerousZones.minByOrNull {
                val priorityPenalty = when (it.zone) {
                    ObstacleZone.MIDDLE_CENTER -> 0.7f
                    ObstacleZone.BOTTOM_CENTER -> 0.75f
                    ObstacleZone.TOP_CENTER -> 0.85f
                    ObstacleZone.MIDDLE_LEFT, ObstacleZone.MIDDLE_RIGHT -> 0.9f
                    ObstacleZone.BOTTOM_LEFT, ObstacleZone.BOTTOM_RIGHT -> 1.3f
                    else -> 1.2f
                }
                it.getRiskScore() * priorityPenalty
            } ?: zones.first()
        } else {
            zones.firstOrNull { it.zone == ObstacleZone.MIDDLE_CENTER } ?: zones.first()
        }
    }

}