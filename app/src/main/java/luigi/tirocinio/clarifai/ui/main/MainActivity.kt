package luigi.tirocinio.clarifai.ui.main

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import luigi.tirocinio.clarifai.databinding.ActivityMainBinding
import luigi.tirocinio.clarifai.ui.lettura.LetturaActivity
import luigi.tirocinio.clarifai.ui.ostacoli.ostacoliActivity
import luigi.tirocinio.clarifai.ui.descContinua.DescrizioneContinuaActivity
import kotlin.jvm.java


class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Modalità descrizione continua
        binding.btnMode1.setOnClickListener {
            val intent = Intent(this, DescrizioneContinuaActivity::class.java)
            startActivity(intent)
        }

        // Modalità lettura (ancora da implementare)
        binding.btnMode2.setOnClickListener {
            val intent = Intent(this, LetturaActivity::class.java)
            startActivity(intent)
        }

        // Modalità ostacoli (ancora da implementare)
        binding.btnMode3.setOnClickListener {
            val intent = Intent(this, ostacoliActivity::class.java)
            startActivity(intent)
        }
    }
}