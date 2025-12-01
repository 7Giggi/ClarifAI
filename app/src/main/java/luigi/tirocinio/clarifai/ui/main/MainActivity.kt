package luigi.tirocinio.clarifai.ui.main

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import luigi.tirocinio.clarifai.databinding.ActivityMainBinding
import luigi.tirocinio.clarifai.ui.lettura.letturaActivity
import luigi.tirocinio.clarifai.ui.ostacoli.ostacoliActivity
import luigi.tirocinio.clarifai.ui.descContinua.descrizioneContinuaActivity
import kotlin.jvm.java


class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Click modalità 1 - Descrizione Continua
        binding.btnMode1.setOnClickListener {
            val intent = Intent(this, descrizioneContinuaActivity::class.java)
            startActivity(intent)
        }

        // Click modalità 2 - Lettura Testi
        binding.btnMode2.setOnClickListener {
            val intent = Intent(this, letturaActivity::class.java)
            startActivity(intent)
        }

        // Click modalità 3 - Rilevamento Ostacoli
        binding.btnMode3.setOnClickListener {
            val intent = Intent(this, ostacoliActivity::class.java)
            startActivity(intent)
        }
    }
}