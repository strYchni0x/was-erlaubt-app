package de.waserlaubt.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.waserlaubt.app.ui.Disclaimer
import de.waserlaubt.app.ui.MainScreen
import de.waserlaubt.app.ui.MainViewModel
import de.waserlaubt.app.ui.PermissionScreen
import de.waserlaubt.app.ui.SettingsScreen

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()
    private var hasPermission by mutableStateOf(false)

    private val permissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        hasPermission = result[Manifest.permission.ACCESS_FINE_LOCATION] == true
        if (hasPermission) viewModel.start()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Während der Fahrt soll das Display anbleiben
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        hasPermission = checkPermission()

        val prefs = getSharedPreferences("settings", MODE_PRIVATE)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                var disclaimerAccepted by rememberSaveable { mutableStateOf(prefs.getBoolean(KEY_DISCLAIMER, false)) }
                var showDebug by rememberSaveable { mutableStateOf(false) }
                var showSettings by rememberSaveable { mutableStateOf(false) }
                val state by viewModel.state.collectAsStateWithLifecycle()

                if (showSettings) {
                    val offline by viewModel.offline.state.collectAsStateWithLifecycle()
                    SettingsScreen(offline, viewModel.offline, onBack = { showSettings = false })
                } else if (hasPermission) {
                    MainScreen(
                        state, showDebug,
                        onToggleDebug = { showDebug = !showDebug },
                        onOpenSettings = { showSettings = true },
                    )
                } else {
                    PermissionScreen(onRequest = ::requestPermission)
                }
                if (!disclaimerAccepted) {
                    Disclaimer(onAccept = {
                        prefs.edit().putBoolean(KEY_DISCLAIMER, true).apply()
                        disclaimerAccepted = true
                    })
                }
            }
        }
        if (!hasPermission) requestPermission()
    }

    override fun onStart() {
        super.onStart()
        hasPermission = checkPermission()
        if (hasPermission) viewModel.start()
    }

    override fun onStop() {
        super.onStop()
        viewModel.stop()
    }

    private fun checkPermission() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun requestPermission() = permissionRequest.launch(
        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
    )

    companion object {
        private const val KEY_DISCLAIMER = "disclaimer_accepted"
    }
}
