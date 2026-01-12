package luigi.tirocinio.clarifai.ml.processor

import android.graphics.Rect
import android.util.Log
import com.google.mlkit.vision.text.Text
import luigi.tirocinio.clarifai.data.model.TextBlock
import luigi.tirocinio.clarifai.utils.Constants
import kotlin.math.abs

class TextRecognitionProcessor {

    //private val TAG = "TextRecognitionProcessor"

    private val VERTICAL_GROUPING_THRESHOLD = 55
    private val HORIZONTAL_GAP_THRESHOLD = 80

    private val MIN_BLOCK_HEIGHT = 15

    private val VERTICAL_OVERLAP_THRESHOLD = 0.5
    //Funzione per estrarre e ordinare il testo
    fun processTextResult(visionText: Text): List<TextBlock> {
        //Log.d(TAG, "INIZIO PROCESSING")

        val allBlocks = extractValidBlocks(visionText)
        //Log.d(TAG, "Blocchi estratti: ${allBlocks.size}")

        if (allBlocks.isEmpty()) {
            //Log.w(TAG, "Nessun blocco di testo rilevato")
            return emptyList()
        }

        //Clustering per righe basato su overlap verticale
        val groupedBlocks = clusterBlocksIntoRows(allBlocks)
        //Log.d(TAG, "Blocchi dopo clustering: ${groupedBlocks.size}")

        //Ordina dall'alto verso il basso
        val sortedBlocks = sortBlocksTopToBottom(groupedBlocks)

        //logFinalOrder(sortedBlocks)

        return sortedBlocks.mapIndexed { index, block ->
            block.copy(index = index)
        }
    }

    //Funzione per estrarre i blocchi di testo
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

            //Log.d(TAG, "Estratto: '${text.take(30)}...' | Top:${boundingBox.top} Left:${boundingBox.left} Height:${boundingBox.height()}")
        }

        return blocks
    }

    //Funzione per raggruppare i blocchi in righe basate su overlap verticale
    private fun clusterBlocksIntoRows(blocks: List<TextBlock>): List<TextBlock> {
        if (blocks.isEmpty()) return emptyList()

        //Ordina per top crescente (dall'alto verso il basso)
        val sorted = blocks.sortedBy { it.boundingBox?.top ?: Int.MAX_VALUE }

        val rows = mutableListOf<MutableList<TextBlock>>()

        for (block in sorted) {
            var addedToRow = false

            //Cerca una riga esistente compatibile
            for (row in rows) {
                //Prendi il primo blocco della riga come riferimento
                val referenceBlock = row.first()

                if (shouldBeInSameRow(referenceBlock, block)) {
                    row.add(block)
                    addedToRow = true
                    break
                }
            }

            //Se non appartiene a nessuna riga esistente, crea nuova riga
            if (!addedToRow) {
                rows.add(mutableListOf(block))
            }
        }

        //Log.d(TAG, "Numero di righe identificate: ${rows.size}")

        //Unisci ogni riga in un singolo TextBlock
        return rows.map { row ->
            mergeRowBlocks(row.sortedBy { it.boundingBox?.left ?: Int.MAX_VALUE })
        }
    }

    //Funzione per verificare se due blocchi devono essere considerati sulla stessa riga
    private fun shouldBeInSameRow(block1: TextBlock, block2: TextBlock): Boolean {
        val box1 = block1.boundingBox ?: return false
        val box2 = block2.boundingBox ?: return false

        // Calcola overlap verticale
        val overlapTop = maxOf(box1.top, box2.top)
        val overlapBottom = minOf(box1.bottom, box2.bottom)
        val overlapHeight = maxOf(0, overlapBottom - overlapTop)

        // Calcola altezza minima dei due blocchi
        val minHeight = minOf(box1.height(), box2.height())

        // Se c'è overlap verticale significativo, sono sulla stessa riga
        if (minHeight > 0) {
            val overlapRatio = overlapHeight.toFloat() / minHeight.toFloat()

            if (overlapRatio >= VERTICAL_OVERLAP_THRESHOLD) {
                // Controlla anche il gap orizzontale
                val horizontalGap = abs(box2.left - box1.right)
                return horizontalGap < HORIZONTAL_GAP_THRESHOLD
            }
        }

        // Fallback: usa distanza dei centri verticali (metodo precedente)
        val center1Y = (box1.top + box1.bottom) / 2
        val center2Y = (box2.top + box2.bottom) / 2
        val verticalDistance = abs(center1Y - center2Y)

        if (verticalDistance <= VERTICAL_GROUPING_THRESHOLD) {
            val horizontalGap = abs(box2.left - box1.right)
            return horizontalGap < HORIZONTAL_GAP_THRESHOLD
        }

        return false
    }

    //Funzione per unire i blocchi sulla stessa riga
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

    //Funzione per ordinare i blocchi in base alla posizione top-left
    private fun sortBlocksTopToBottom(blocks: List<TextBlock>): List<TextBlock> {
        return blocks.sortedWith(
            compareBy<TextBlock> { it.boundingBox?.top ?: Int.MAX_VALUE }
                .thenBy { it.boundingBox?.left ?: Int.MAX_VALUE }
        )
    }

    private fun logFinalOrder(blocks: List<TextBlock>) {
        //Log.d(TAG, "========== ORDINE FINALE ==========")
        blocks.forEachIndexed { index, block ->
            val preview = block.text.take(50).replace("\n", " ")
            val box = block.boundingBox
           //Log.d(TAG, "[$index] '$preview' | Top:${box?.top} Bottom:${box?.bottom} Left:${box?.left}")
        }
    }

    fun getAllText(blocks: List<TextBlock>): String {
        return blocks.joinToString("\n") { it.text }
    }
}
