package de.waserlaubt.app.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import de.waserlaubt.app.osm.SpeedValue

private val Muted = Color(0xFFB0B0B0)
private val Warn = Color(0xFFFF5252)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MainScreen(
    state: UiState,
    showDebug: Boolean,
    onToggleDebug: () -> Unit,
    onOpenSettings: () -> Unit,
    compact: Boolean = false,
) {
    if (compact) {
        CompactScreen(state)
        return
    }
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .safeDrawingPadding()
            .combinedClickable(onClick = {}, onLongClick = onToggleDebug),
    ) {
        val landscape = maxWidth > maxHeight
        val signSize = if (landscape) min(maxHeight * 0.85f, maxWidth * 0.5f) else min(maxWidth * 0.85f, maxHeight * 0.55f)

        if (landscape) {
            Row(Modifier.fillMaxSize().padding(16.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly) {
                SignArea(state, signSize)
                InfoArea(state, showDebug, signSize, Modifier.weight(1f))
            }
        } else {
            Column(Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center) {
                SignArea(state, signSize)
                Spacer(Modifier.height(24.dp))
                InfoArea(state, showDebug, signSize, Modifier)
            }
        }
        IconButton(onClick = onOpenSettings, modifier = Modifier.align(Alignment.TopEnd)) {
            Icon(Icons.Outlined.Settings, contentDescription = "Optionen", tint = Color(0xFF606060))
        }
        Text(
            "Daten © OpenStreetMap-Mitwirkende",
            color = Color(0xFF707070), fontSize = 11.sp,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 4.dp),
        )
    }
}

/** Bild-im-Bild-Fenster über der Navigation: nur Schild und eigene Geschwindigkeit. */
@Composable
private fun CompactScreen(state: UiState) {
    BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
        val signSize = min(maxWidth, maxHeight * 0.74f) * 0.92f
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            val limit = state.limit
            SpeedSign(limit?.value ?: SpeedValue.Unknown, signSize, dimmed = limit == null || limit.estimated)
            val kmh = state.speedKmh
            if (kmh != null) {
                val max = (limit?.value as? SpeedValue.Number)?.value
                val tooFast = max != null && limit.estimated == false && kmh > max + 3
                Text("$kmh", color = if (tooFast) Warn else Muted, fontSize = (signSize.value * 0.24f).sp,
                    fontWeight = FontWeight.Bold, maxLines = 1)
            }
        }
    }
}

@Composable
private fun SignArea(state: UiState, signSize: Dp) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        val limit = state.limit
        SpeedSign(limit?.value ?: SpeedValue.Unknown, signSize, dimmed = limit == null || limit.estimated)
        val tag = when {
            limit == null -> null
            limit.estimated -> "geschätzt"
            limit.conditionalActive -> "zeitlich begrenzt"
            else -> null
        }
        Text(tag ?: "", color = Muted, fontSize = 18.sp, modifier = Modifier.padding(top = 8.dp))
    }
}

@Composable
private fun InfoArea(state: UiState, showDebug: Boolean, signSize: Dp, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        state.limit?.note?.let {
            Text(it, color = Color.White, fontSize = 20.sp, textAlign = TextAlign.Center)
        }
        state.roadName?.let {
            Text(it, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp))
        }
        state.speedKmh?.let { kmh ->
            val limit = (state.limit?.value as? SpeedValue.Number)?.value
            val tooFast = limit != null && state.limit?.estimated == false && kmh > limit + 3
            // Eigene Geschwindigkeit wächst mit dem Schild: auf dem Handy ca. 60 sp statt 28 sp
            val size = (signSize.value * 0.17f).coerceIn(28f, 96f)
            Text(
                buildAnnotatedString {
                    append("$kmh")
                    withStyle(SpanStyle(fontSize = (size * 0.45f).sp, fontWeight = FontWeight.Medium)) { append(" km/h") }
                },
                color = if (tooFast) Warn else Muted, fontSize = size.sp,
                fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp),
            )
        }
        statusText(state)?.let {
            Text(it, color = Muted, fontSize = 16.sp, textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp))
        }
        if (showDebug && state.debug.isNotEmpty()) {
            Text(state.debug, color = Color(0xFF80CBC4), fontSize = 12.sp, fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(top = 12.dp))
        }
    }
}

private fun statusText(s: UiState): String? {
    val gps = when (s.gps) {
        GpsState.SEARCHING -> "GPS wird gesucht …"
        GpsState.LOST -> "GPS-Signal verloren – letzter Wert"
        GpsState.OK -> null
    }
    val data = when {
        s.loadingData && s.limit == null -> "Straßendaten werden geladen …"
        s.dataError != null && s.limit == null -> "Keine Verbindung zum Kartendienst"
        s.noRoad -> "Keine Straße erkannt"
        else -> null
    }
    return listOfNotNull(gps, data).joinToString("\n").ifEmpty { null }
}

@Composable
fun PermissionScreen(onRequest: () -> Unit) {
    Column(
        Modifier.fillMaxSize().background(Color.Black).safeDrawingPadding().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
    ) {
        Text("„was erlaubt“ braucht deinen Standort, um die Straße und das Tempolimit zu erkennen.",
            color = Color.White, fontSize = 20.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(24.dp))
        Button(onClick = onRequest) { Text("Standort freigeben") }
    }
}

@Composable
fun Disclaimer(onAccept: () -> Unit) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = {},
        confirmButton = { Button(onClick = onAccept) { Text("Verstanden") } },
        title = { Text("Ohne Gewähr") },
        text = {
            Text(
                "Die angezeigten Tempolimits stammen aus OpenStreetMap und können fehlen, veraltet " +
                    "oder falsch sein. Baustellen und Wechselverkehrszeichen sind nicht enthalten.\n\n" +
                    "Es gilt immer die Beschilderung vor Ort. Bediene das Handy nicht während der Fahrt.",
            )
        },
    )
}
