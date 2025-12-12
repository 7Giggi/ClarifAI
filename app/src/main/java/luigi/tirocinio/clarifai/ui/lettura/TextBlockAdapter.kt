package luigi.tirocinio.clarifai.ui.lettura

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import luigi.tirocinio.clarifai.R
import luigi.tirocinio.clarifai.data.model.TextBlock

class TextBlockAdapter(
    private val blocks: List<TextBlock>,
    private val onBlockClick: (TextBlock) -> Unit
) : RecyclerView.Adapter<TextBlockAdapter.TextBlockViewHolder>() {

    inner class TextBlockViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val textContent: TextView = view.findViewById(R.id.textContent)
        val textIndex: TextView = view.findViewById(R.id.textIndex)

        fun bind(block: TextBlock, position: Int) {
            textContent.text = block.text
            textIndex.text = "${position + 1}"

            itemView.setOnClickListener {
                // Usa il blocco specifico, non la posizione
                onBlockClick(blocks[position])
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TextBlockViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_text_block, parent, false)
        return TextBlockViewHolder(view)
    }

    override fun onBindViewHolder(holder: TextBlockViewHolder, position: Int) {
        holder.bind(blocks[position], position)
    }

    override fun getItemCount(): Int = blocks.size
}
