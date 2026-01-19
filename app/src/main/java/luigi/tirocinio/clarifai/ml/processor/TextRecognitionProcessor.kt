package luigi.tirocinio.clarifai.ml.processor

import android.graphics.Rect
import com.google.mlkit.vision.text.Text
import luigi.tirocinio.clarifai.data.model.TextBlock
import kotlin.math.abs

// Classe che processa i risultati del riconoscimento testo di ML Kit
// Organizza i blocchi di testo rilevati in un ordine di lettura e raggruppa i blocchi vicini
class TextRecognitionProcessor {
    // private val TAG = "TextRecognitionProcessor"

    // Soglia di distanza verticale massima per considerare due blocchi sulla stessa riga
    private val VERTICAL_GROUPING_THRESHOLD = 55

    // Soglia di distanza orizzontale massima tra blocchi consecutivi sulla stessa riga
    private val HORIZONTAL_GAP_THRESHOLD = 80

    // Altezza minima che un blocco deve avere per essere considerato valido
    private val MIN_BLOCK_HEIGHT = 15

    // Percentuale minima di overlap verticale per considerare due blocchi sulla stessa riga
    private val VERTICAL_OVERLAP_THRESHOLD = 0.5

    // Funzione per processare il risultato del riconoscimento testo e restituire i blocchi ordinati
    fun processTextResult(visionText: Text): List<TextBlock> {
        // Log.d(TAG, "INIZIO PROCESSING")
        val allBlocks = extractValidBlocks(visionText)
        // Log.d(TAG, "Blocchi estratti: ${allBlocks.size}")
        if (allBlocks.isEmpty()) {
            // Log.w(TAG, "Nessun blocco di testo rilevato")
            return emptyList()
        }

        val groupedBlocks = mergeBlocksIntoRows(allBlocks)
        // Log.d(TAG, "Blocchi dopo merge: ${groupedBlocks.size}")
        val sortedBlocks = sortBlocks(groupedBlocks)
        // logFinalOrder(sortedBlocks)
        return sortedBlocks.mapIndexed { index, block ->
            block.copy(index = index)
        }
    }

    // Funzione per estrarre i blocchi di testo validi dal risultato di ML Kit
    private fun extractValidBlocks(visionText: Text): List<TextBlock> {
        val blocks = mutableListOf<TextBlock>()
        for (block in visionText.textBlocks) {
            val boundingBox = block.boundingBox
            val text = block.text.trim()
            if (boundingBox == null || text.isEmpty()) continue
            if (boundingBox.height() < MIN_BLOCK_HEIGHT) continue
            if (boundingBox.top < 0 || boundingBox.left < 0) continue
            blocks.add(
                TextBlock(
                    text = text,
                    boundingBox = boundingBox,
                    confidence = 1.0f,
                    index = 0,
                    language = block.recognizedLanguage
                )
            )
            // Log.d(TAG, "Estratto: '${text.take(30)}...' | Top:${boundingBox.top} Left:${boundingBox.left} Height:${boundingBox.height()}")
        }
        return blocks
    }

    // Funzione per raggruppare i blocchi in righe in base all'overlap verticale
    private fun mergeBlocksIntoRows(blocks: List<TextBlock>): List<TextBlock> {
        if (blocks.isEmpty()) return emptyList()
        val sorted = blocks.sortedBy { it.boundingBox?.top ?: Int.MAX_VALUE }
        val rows = mutableListOf<MutableList<TextBlock>>()
        for (block in sorted) {
            var addedToRow = false
            for (row in rows) {
                val referenceBlock = row.first()
                if (shouldBeInSameRow(referenceBlock, block)) {
                    row.add(block)
                    addedToRow = true
                    break
                }
            }
            if (!addedToRow) {
                rows.add(mutableListOf(block))
            }
        }
        // Log.d(TAG, "Numero di righe identificate: ${rows.size}")
        return rows.map { row ->
            mergeRowBlocks(row.sortedBy { it.boundingBox?.left ?: Int.MAX_VALUE })
        }
    }

    // Funzione per verificare se due blocchi appartengono alla stessa riga di testo
    private fun shouldBeInSameRow(block1: TextBlock, block2: TextBlock): Boolean {
        val box1 = block1.boundingBox ?: return false
        val box2 = block2.boundingBox ?: return false

        val overlapTop = maxOf(box1.top, box2.top)
        val overlapBottom = minOf(box1.bottom, box2.bottom)
        val overlapHeight = maxOf(0, overlapBottom - overlapTop)
        val minHeight = minOf(box1.height(), box2.height())

        if (minHeight > 0) {
            val overlapRatio = overlapHeight.toFloat() / minHeight.toFloat()
            if (overlapRatio >= VERTICAL_OVERLAP_THRESHOLD) {
                val horizontalGap = abs(box2.left - box1.right)
                return horizontalGap < HORIZONTAL_GAP_THRESHOLD
            }
        }

        val center1Y = (box1.top + box1.bottom) / 2
        val center2Y = (box2.top + box2.bottom) / 2
        val verticalDistance = abs(center1Y - center2Y)
        if (verticalDistance <= VERTICAL_GROUPING_THRESHOLD) {
            val horizontalGap = abs(box2.left - box1.right)
            return horizontalGap < HORIZONTAL_GAP_THRESHOLD
        }

        return false
    }

    // Funzione per unire piu blocchi della stessa riga in un unico TextBlock
    private fun mergeRowBlocks(blocks: List<TextBlock>): TextBlock {
        if (blocks.size == 1) return blocks.first()
        val mergedText = blocks.joinToString(" ") { it.text }
        val allBoxes = blocks.mapNotNull { it.boundingBox }
        val mergedBox = Rect(
            allBoxes.minOf { it.left },
            allBoxes.minOf { it.top },
            allBoxes.maxOf { it.right },
            allBoxes.maxOf { it.bottom }
        )
        return TextBlock(
            text = mergedText,
            boundingBox = mergedBox,
            confidence = blocks.map { it.confidence }.average().toFloat(),
            index = 0,
            language = blocks.firstOrNull()?.language
        )
    }

    // Funzione per ordinare i blocchi dall'alto verso il basso e da sinistra a destra
    private fun sortBlocks(blocks: List<TextBlock>): List<TextBlock> {
        return blocks.sortedWith(
            compareBy<TextBlock> { it.boundingBox?.top ?: Int.MAX_VALUE }
                .thenBy { it.boundingBox?.left ?: Int.MAX_VALUE }
        )
    }

    // Funzione per ottenere tutto il testo concatenato dei blocchi
    fun getAllText(blocks: List<TextBlock>): String {
        return blocks.joinToString("\n") { it.text }
    }
}
