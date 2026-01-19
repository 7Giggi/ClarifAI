package luigi.tirocinio.clarifai.utils

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

// Helper per i permessi richiesti dall'app
object PermissionHelper {
    // Funzione per verificare se un singolo permesso è stato concesso
    fun hasPermission(context: Context, permission: String): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            permission
        ) == PackageManager.PERMISSION_GRANTED
    }

    // Funzione per richiedere i permessi mancanti
    fun requestPermissions(activity: Activity) {
        ActivityCompat.requestPermissions(
            activity,
            Constants.REQUIRED_PERMISSIONS,
            Constants.PERMISSION_REQUEST_CODE
        )
    }

    // Funzione per gestire il risultato della richiesta permessi
    fun handlePermissionResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
        onAllGranted: () -> Unit,
        onDenied: (List<String>) -> Unit
    ) {
        if (requestCode == Constants.PERMISSION_REQUEST_CODE) {
            val deniedPermissions = permissions.filterIndexed { index, _ ->
                grantResults[index] != PackageManager.PERMISSION_GRANTED
            }

            if (deniedPermissions.isEmpty()) {
                onAllGranted()
            } else {
                onDenied(deniedPermissions)
            }
        }
    }

    // Funzione per verificare i permessi sulla fotocamera
    fun hasCameraPermissions(context: Context): Boolean {
        return hasPermission(context, Manifest.permission.CAMERA) &&
                hasPermission(context, Manifest.permission.INTERNET)
    }

    // Funzione per verificare i permessi sul microfono
    fun hasVoicePermissions(context: Context): Boolean {
        return hasPermission(context, Manifest.permission.RECORD_AUDIO)
    }
}
