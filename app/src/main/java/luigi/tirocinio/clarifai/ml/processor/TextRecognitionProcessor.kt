package luigi.tirocinio.clarifai.ml.processor

import android.graphics.Rect
import com.google.mlkit.vision.text.Text
import luigi.tirocinio.clarifai.data.model.TextBlock
import luigi.tirocinio.clarifai.utils.Constants
import kotlin.math.abs

//Classe usata per, estrarre, ordinare e raggruppare il testo riconosciuto
class TextRecognitionProcessor {

    //private val TAG = "TextRecognitionProcessor"
    private val VERTICAL_GROUPING_THRESHOLD = 50
    private val HORIZONTAL_GAP_THRESHOLD = 100

    //Funzione per estrarre e ordinare il testo
    fun processTextResult(visionText: Text): List<TextBlock> {
        //Log.d(TAG, "INIZIO PROCESSING ")

        val allBlocks = mutableListOf<TextBlock>()

        for (block in visionText.textBlocks) {
            val boundingBox = block.boundingBox

            if (boundingBox != null && boundingBox.height() >= Constants.TEXT_MIN_HEIGHT_PX) {
                val textBlock = TextBlock(
                    text = block.text,
                    boundingBox = boundingBox,
                    confidence = 1.0f,
                    index = 0,
                    language = block.recognizedLanguage
                )
                allBlocks.add(textBlock)

                //Log.d(TAG, "Blocco estratto: '${block.text}' - Top: ${boundingBox.top}, Left: ${boundingBox.left}")
            }
        }

        //Log.d(TAG, "Totale blocchi estratti: ${allBlocks.size}")

        val groupedBlocks = groupCloseBlocks(allBlocks)

        //Log.d(TAG, "Blocchi dopo raggruppamento: ${groupedBlocks.size}")
        
       // Log.d(TAG, "ORDINE FINALE")
        groupedBlocks.forEachIndexed { index, block ->
            //Log.d(TAG, "[$index] '${block.text.take(30)}...' - Top: ${block.boundingBox?.top}, Left: ${block.boundingBox?.left}")
        }

        return groupedBlocks.mapIndexed { index, block ->
            block.copy(index = index)
        }
    }

    //Funzione per ordinare i blocchi di testo vicini
    private fun groupCloseBlocks(blocks: List<TextBlock>): List<TextBlock> {
        if (blocks.isEmpty()) return emptyList()

        val sortedBlocks = blocks.sortedWith(
            compareBy<TextBlock> { it.boundingBox?.top ?: 0 }
                .thenBy { it.boundingBox?.left ?: 0 }
        )

       // Log.d(TAG, "BLOCCHI ORDINATI PRE-RAGGRUPPAMENTO")
        sortedBlocks.forEachIndexed { index, block ->
            //Log.d(TAG, "[$index] '${block.text}' - Top: ${block.boundingBox?.top}, Left: ${block.boundingBox?.left}")
        }

        val result = mutableListOf<TextBlock>()
        val visited = mutableSetOf<Int>()

        for (i in sortedBlocks.indices) {
            if (i in visited) continue

            val currentGroup = mutableListOf(sortedBlocks[i])
            visited.add(i)

            for (j in i + 1 until sortedBlocks.size) {
                if (j in visited) continue

                if (shouldMerge(currentGroup.last(), sortedBlocks[j])) {
                   // Log.d(TAG, "MERGE: '${currentGroup.last().text}' + '${sortedBlocks[j].text}'")
                    currentGroup.add(sortedBlocks[j])
                    visited.add(j)
                } else {
                    val lastBox = currentGroup.last().boundingBox
                    val nextBox = sortedBlocks[j].boundingBox
                    if (lastBox != null && nextBox != null) {
                        if (abs(lastBox.top - nextBox.top) > VERTICAL_GROUPING_THRESHOLD) {
                            break
                        }
                    }
                }
            }

            val merged = mergeBlocks(currentGroup)
           // Log.d(TAG, "GRUPPO CREATO: '${merged.text}' - Top: ${merged.boundingBox?.top}")
            result.add(merged)
        }

        return result
    }

    //Funzione per capire, in base alla posizione, se due blocchi devono essere raggruppati
    private fun shouldMerge(block1: TextBlock, block2: TextBlock): Boolean {
        val box1 = block1.boundingBox ?: return false
        val box2 = block2.boundingBox ?: return false

        val verticalDistance = abs(box1.top - box2.top)
        if (verticalDistance > VERTICAL_GROUPING_THRESHOLD) {
            return false
        }

        val horizontalGap = box2.left - box1.right
        return horizontalGap in 0..HORIZONTAL_GAP_THRESHOLD
    }

    //Funzione per unire i blocchi di testo vicini
    private fun mergeBlocks(blocks: List<TextBlock>): TextBlock {
        if (blocks.size == 1) return blocks.first()

        val mergedText = blocks.joinToString(" ") { it.text }

        val allBoxes = blocks.mapNotNull { it.boundingBox }
        val mergedBox = if (allBoxes.isNotEmpty()) {
            val firstBox = blocks.first().boundingBox!!
            val left = allBoxes.minOf { it.left }
            val right = allBoxes.maxOf { it.right }
            val bottom = allBoxes.maxOf { it.bottom }

            Rect(left, firstBox.top, right, bottom)
        } else {
            null
        }

        return TextBlock(
            text = mergedText,
            boundingBox = mergedBox,
            confidence = blocks.map { it.confidence }.average().toFloat(),
            index = 0,
            language = blocks.firstOrNull()?.language
        )
    }

    fun getAllText(blocks: List<TextBlock>): String {
        return blocks.joinToString("\n") { it.text }
    }
}
