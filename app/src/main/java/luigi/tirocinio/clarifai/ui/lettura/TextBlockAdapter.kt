package luigi.tirocinio.clarifai.ui.lettura

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import luigi.tirocinio.clarifai.R
import luigi.tirocinio.clarifai.data.model.TextBlock

// Adapter per visualizzare una lista di blocchi di testo in una RecyclerView.
// Utilizzato nella modalita Lettura per mostrare i diversi blocchi di testo riconosciuti
class TextBlockAdapter(
    // Lista dei blocchi di testo da visualizzare
    private val blocks: List<TextBlock>,
    // Callback invocato quando l'utente clicca su un blocco di testo
    private val onBlockClick: (TextBlock) -> Unit
) : RecyclerView.Adapter<TextBlockAdapter.TextBlockViewHolder>() {

    // ViewHolder che contiene i riferimenti agli elementi grafici di ogni elemento della lista
    inner class TextBlockViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        // TextView che mostra il contenuto del testo riconosciuto
        val textContent: TextView = view.findViewById(R.id.textContent)
        // TextView che mostra il numero progressivo del blocco
        val textIndex: TextView = view.findViewById(R.id.textIndex)

        // Collega i dati del blocco di testo agli elementi grafici.
        // Imposta il testo, il numero progressivo e gestisce il click.
        fun bind(block: TextBlock, position: Int) {
            textContent.text = block.text
            textIndex.text = "${position + 1}"
            itemView.setOnClickListener {
                onBlockClick(blocks[position])
            }
        }
    }

    // Crea un nuovo ViewHolder usando il layout dell'elemento della lista
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TextBlockViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_text_block, parent, false)
        return TextBlockViewHolder(view)
    }

    // Collega i dati del blocco alla posizione specificata al ViewHolder corrispondente
    override fun onBindViewHolder(holder: TextBlockViewHolder, position: Int) {
        holder.bind(blocks[position], position)
    }

    // Restituisce il numero totale di blocchi di testo nella lista
    override fun getItemCount(): Int = blocks.size
}
