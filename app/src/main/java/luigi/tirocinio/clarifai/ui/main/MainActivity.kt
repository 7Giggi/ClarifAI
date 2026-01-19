package luigi.tirocinio.clarifai.ui.main

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import luigi.tirocinio.clarifai.databinding.ActivityMainBinding
import luigi.tirocinio.clarifai.ui.lettura.LetturaActivity
import luigi.tirocinio.clarifai.ui.ostacoli.OstacoliActivity
import luigi.tirocinio.clarifai.ui.descrizione.DescrizioneActivity
import luigi.tirocinio.clarifai.ml.manager.SpeechRecognizerManager
import luigi.tirocinio.clarifai.ml.manager.TTSManager
import luigi.tirocinio.clarifai.utils.Constants

// Activity principale dell'applicazione che funge da menu di selezione
// Permette all'utente di scegliere tra tre modalita operative tramite pulsanti o comandi vocali:
// - Modalita Descrizione: descrizione dell'ambiente circostante
// - Modalita Lettura: riconoscimento e lettura di testi
// - Modalita Ostacoli: rilevamento ostacoli e profondita
// Gestisce i permessi audio necessari per il riconoscimento vocale
class MainActivity : AppCompatActivity() {

    // Binding per accedere agli elementi dell'interfaccia grafica
    private lateinit var binding: ActivityMainBinding

    // Manager per il riconoscimento vocale, nullable per gestire correttamente il ciclo di vita
    private var speechManager: SpeechRecognizerManager? = null

    // Manager per il text-to-speech utilizzato per leggere il messaggio di aiuto
    private lateinit var ttsManager: TTSManager

    // Codice identificativo per la richiesta del permesso audio
    private val RECORD_AUDIO_PERMISSION_CODE = 1

    // Funzione per inizializzare l'activity, configura i pulsanti e verifica i permessi audio
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ttsManager = TTSManager(this)
        setupButtons()
        checkAudioPermission()
    }

    // Funzione per configurare i listener dei tre pulsanti per le diverse modalita operative
    private fun setupButtons() {
        binding.btnMode1.setOnClickListener {
            navigateToDescrizioneMode()
        }

        binding.btnMode2.setOnClickListener {
            navigateToLetturaMode()
        }

        binding.btnMode3.setOnClickListener {
            navigateToOstacoliMode()
        }
    }

    // Funzione per verificare se il permesso di registrazione audio e stato concesso
    // Se non e presente, lo richiede all'utente, altrimenti inizializza il riconoscimento vocale
    private fun checkAudioPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.RECORD_AUDIO),
                RECORD_AUDIO_PERMISSION_CODE
            )
        } else {
            setupSpeechRecognition()
        }
    }

    // Funzione per gestire il risultato della richiesta di permessi
    // Se il permesso audio viene concesso, inizializza il riconoscimento vocale
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == RECORD_AUDIO_PERMISSION_CODE &&
            grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            setupSpeechRecognition()
        }
    }

    // Funzione per inizializzare il sistema di riconoscimento vocale
    // Crea un nuovo SpeechRecognizerManager e configura i callback per gestire i comandi vocali ricevuti e gli eventuali errori
    private fun setupSpeechRecognition() {
        cleanupSpeechRecognition()
        // Log.d("MainActivity", "Inizializzazione speech recognition")
        speechManager = SpeechRecognizerManager(this)
        speechManager?.setCallbacks(
            onCommand = { command ->
                // Log.d("MainActivity", "Comando ricevuto: \$command")
                handleVoiceCommand(command)
            },
            onErr = { error ->
                // Log.e("MainActivity", "Errore speech: \$error")
            }
        )
        speechManager?.startListening()
        // Log.d("MainActivity", "Speech recognition avviato")
    }

    // Funzione per interpretare i comandi vocali ricevuti e naviga alla modalita corrispondente
    private fun handleVoiceCommand(command: String) {
        // Log.d("MainActivity", "Gestione comando: \$command")
        when (command) {
            "descrizione" -> {
                // Log.d("MainActivity", "Apertura modalita descrizione")
                navigateToDescrizioneMode()
            }
            "lettura" -> {
                // Log.d("MainActivity", "Apertura modalita lettura")
                navigateToLetturaMode()
            }
            "ostacoli" -> {
                // Log.d("MainActivity", "Apertura modalita ostacoli")
                navigateToOstacoliMode()
            }
            "aiuto" -> {
                ttsManager.speak(Constants.HELP_MESSAGE)
            }
        }
    }

    // Funzione per avviare l'activity per la modalita Descrizione Continua
    private fun navigateToDescrizioneMode() {
        val intent = Intent(this, DescrizioneActivity::class.java)
        startActivity(intent)
    }

    // Funzione per avviare l'activity per la modalita Lettura
    private fun navigateToLetturaMode() {
        val intent = Intent(this, LetturaActivity::class.java)
        startActivity(intent)
    }

    // Funzione per avviare l'activity per la modalita Ostacoli
    private fun navigateToOstacoliMode() {
        val intent = Intent(this, OstacoliActivity::class.java)
        startActivity(intent)
    }

    // Funzione per riprestinare il riconoscimento vocale quando l'activity torna in primo piano
    // Se il manager e stato distrutto, lo ricrea, altrimenti riavvia l'ascolto
    override fun onResume() {
        super.onResume()
        // Log.d("MainActivity", "onResume chiamato")
        if (speechManager == null) {
            setupSpeechRecognition()
        } else {
            speechManager?.startListening()
        }
    }

    // Funzione per fermare e distruggere il riconoscimento vocale quando l'activity va in background
    // Questo evita conflitti con altre activity che potrebbero usare il microfono
    override fun onPause() {
        super.onPause()
        // Log.d("MainActivity", "onPause chiamato - distruzione speech manager")
        cleanupSpeechRecognition()
    }

    // Funzione per rilasciare le risorse del SpeechRecognizerManager in modo sicuro
    // Chiama il metodo shutdown e imposta il riferimento a null
    private fun cleanupSpeechRecognition() {
        speechManager?.let {
            // Log.d("MainActivity", "Cleanup speech recognition")
            it.shutdown()
            speechManager = null
        }
    }

    // Funzione per pulire le risorse quando l'activity viene distrutta definitivamente
    override fun onDestroy() {
        super.onDestroy()
        cleanupSpeechRecognition()
        ttsManager.shutdown()
    }
}