package de.waserlaubt.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.waserlaubt.app.offline.OfflineState
import de.waserlaubt.app.offline.OfflineStore
import de.waserlaubt.app.offline.RegionState
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val Dim = Color(0xFF9E9E9E)
private val ErrorColor = Color(0xFFFF8A80)

@Composable
fun SettingsScreen(state: OfflineState, store: OfflineStore, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    LaunchedEffect(Unit) { store.loadCatalog() }

    LazyColumn(
        Modifier.fillMaxSize().background(Color.Black).safeDrawingPadding(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 8.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück", tint = Color.White)
                }
                Text("Optionen", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Medium)
            }
            Spacer(Modifier.padding(4.dp))
            Text("Offline-Karten", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Medium)
            Text(
                "Mit einer Offline-Karte braucht die App unterwegs kein Netz. Wo keine installiert ist, " +
                    "werden die Straßendaten wie bisher online nachgeladen.",
                color = Dim, fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
            )
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Nur über WLAN herunterladen", color = Color.White, fontSize = 16.sp, modifier = Modifier.weight(1f))
                Switch(checked = state.wifiOnly, onCheckedChange = store::setWifiOnly)
            }
            when {
                state.catalogLoading -> Text("Paketliste wird geladen …", color = Dim, fontSize = 14.sp)
                state.catalogError != null -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(state.catalogError, color = ErrorColor, fontSize = 14.sp, modifier = Modifier.weight(1f))
                    TextButton(onClick = store::loadCatalog) { Text("Erneut") }
                }
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp), color = Color(0xFF333333))
            GermanyRow(state, store)
            HorizontalDivider(Modifier.padding(vertical = 8.dp), color = Color(0xFF333333))
        }
        items(state.regions, key = { it.region.id }) { RegionRow(it, store) }
        item {
            val used = state.regions.sumOf { it.installedSize }
            if (used > 0) {
                Text("Belegter Speicher: ${formatSize(used)}", color = Dim, fontSize = 14.sp,
                    modifier = Modifier.padding(top = 12.dp))
            }
            Text("Daten © OpenStreetMap-Mitwirkende, ODbL", color = Color(0xFF707070), fontSize = 12.sp,
                modifier = Modifier.padding(top = 12.dp, bottom = 16.dp))
        }
    }
}

@Composable
private fun GermanyRow(state: OfflineState, store: OfflineStore) {
    val available = state.regions.filter { it.remote != null }
    val missing = available.filter { (!it.installed || it.updateAvailable) && it.progress == null }
    val allInstalled = available.isNotEmpty() && available.all { it.installed }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("Deutschland gesamt", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Medium)
            val total = available.sumOf { it.remote!!.size }
            if (total > 0) Text("alle Bundesländer · ${formatSize(total)}", color = Dim, fontSize = 13.sp)
        }
        when {
            missing.isNotEmpty() -> Button(onClick = { store.download(missing.map { it.region.id }) }) {
                Text(if (allInstalled) "Alle aktualisieren" else "Alle laden")
            }
            allInstalled -> Text("installiert", color = Dim, fontSize = 14.sp)
        }
    }
}

@Composable
private fun RegionRow(r: RegionState, store: OfflineStore) {
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(r.region.name, color = Color.White, fontSize = 16.sp)
                Text(subtitle(r), color = if (r.error != null) ErrorColor else Dim, fontSize = 13.sp)
            }
            Spacer(Modifier.width(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                when {
                    r.progress != null -> TextButton(onClick = { store.cancel(r.region.id) }) { Text("Abbrechen") }
                    r.updateAvailable -> Button(onClick = { store.download(listOf(r.region.id)) }) { Text("Aktualisieren") }
                    r.installed -> OutlinedButton(onClick = { store.delete(r.region.id) }) { Text("Löschen") }
                    r.remote != null -> Button(onClick = { store.download(listOf(r.region.id)) }) { Text("Laden") }
                }
            }
        }
        r.progress?.let { p ->
            if (p < 0) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 4.dp))
            else LinearProgressIndicator(progress = { p }, modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
        }
    }
}

private fun subtitle(r: RegionState): String = when {
    r.error != null -> r.error
    r.progress != null && r.progress < 0 -> "wartet auf Download"
    r.progress != null -> "wird geladen · ${(r.progress * 100).toInt()} %"
    r.updateAvailable -> "Stand ${formatDate(r.installedDate!!)} · neuer Stand ${formatDate(r.remote!!.date)}"
    r.installed -> "installiert · Stand ${formatDate(r.installedDate!!)} · ${formatSize(r.installedSize)}"
    r.remote != null -> "${formatSize(r.remote.size)} · Stand ${formatDate(r.remote.date)}"
    else -> "nicht verfügbar"
}

private fun formatDate(iso: String): String =
    runCatching { LocalDate.parse(iso).format(DateTimeFormatter.ofPattern("dd.MM.yyyy")) }.getOrDefault(iso)

private fun formatSize(bytes: Long): String =
    if (bytes >= 1_000_000) String.format(Locale.GERMANY, "%.1f MB", bytes / 1e6)
    else String.format(Locale.GERMANY, "%d kB", (bytes / 1000).coerceAtLeast(1))
