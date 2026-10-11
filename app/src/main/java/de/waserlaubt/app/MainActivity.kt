package de.waserlaubt.app

import android.Manifest
import android.app.PictureInPictureParams
import android.content.res.Configuration
import android.os.Build
import android.util.Rational
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
import androidx.compose.runtime.LaunchedEffect
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
    private var showSettings by mutableStateOf(false)
    private var inPip by mutableStateOf(false)
    private var pipEnabled by mutableStateOf(true)
    private var disclaimerAccepted by mutableStateOf(false)
    private val pipSupported by lazy {
        packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)
    }

    /** Bild-im-Bild nur vom Hauptbildschirm aus und wenn es in den Optionen nicht abgeschaltet ist. */
    private val pipAllowed get() = pipSupported && pipEnabled && hasPermission && disclaimerAccepted && !showSettings

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
        disclaimerAccepted = prefs.getBoolean(KEY_DISCLAIMER, false)
        pipEnabled = prefs.getBoolean(KEY_PIP, true)
        showSettings = savedInstanceState?.getBoolean(KEY_SHOW_SETTINGS) ?: false
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                var showDebug by rememberSaveable { mutableStateOf(false) }
                val state by viewModel.state.collectAsStateWithLifecycle()
                LaunchedEffect(pipAllowed) { updatePipParams() }

                if (inPip) {
                    MainScreen(state, showDebug = false, onToggleDebug = {}, onOpenSettings = {}, compact = true)
                } else if (showSettings) {
                    val offline by viewModel.offline.state.collectAsStateWithLifecycle()
                    SettingsScreen(
                        offline, viewModel.offline,
                        pipEnabled = pipEnabled,
                        pipSupported = pipSupported,
                        onPipChange = {
                            pipEnabled = it
                            prefs.edit().putBoolean(KEY_PIP, it).apply()
                        },
                        onBack = { showSettings = false },
                    )
                } else if (hasPermission) {
                    MainScreen(
                        state, showDebug,
                        onToggleDebug = { showDebug = !showDebug },
                        onOpenSettings = { showSettings = true },
                    )
                } else {
                    PermissionScreen(onRequest = ::requestPermission)
                }
                if (!disclaimerAccepted && !inPip) {
                    Disclaimer(onAccept = {
                        prefs.edit().putBoolean(KEY_DISCLAIMER, true).apply()
                        disclaimerAccepted = true
                    })
                }
            }
        }
        if (!hasPermission) requestPermission()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(KEY_SHOW_SETTINGS, showSettings)
    }

    private fun pipParams(): PictureInPictureParams {
        val builder = PictureInPictureParams.Builder().setAspectRatio(Rational(3, 4))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Android 12+: beim Wechsel in eine andere App automatisch und mit Animation
            builder.setAutoEnterEnabled(pipAllowed).setSeamlessResizeEnabled(true)
        }
        return builder.build()
    }

    private fun updatePipParams() {
        if (pipSupported) setPictureInPictureParams(pipParams())
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        // Bis Android 11 gibt es kein automatisches Bild-im-Bild
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S && pipAllowed) {
            runCatching { enterPictureInPictureMode(pipParams()) }
        }
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        inPip = isInPictureInPictureMode
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
        private const val KEY_PIP = "pip_enabled"
        private const val KEY_SHOW_SETTINGS = "show_settings"
    }
}
