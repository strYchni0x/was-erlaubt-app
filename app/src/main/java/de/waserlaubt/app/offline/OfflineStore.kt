package de.waserlaubt.app.offline

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.util.Log
import de.waserlaubt.app.BuildConfig
import de.waserlaubt.app.data.TileRepository
import de.waserlaubt.app.osm.OsmWay
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

data class Region(val id: String, val name: String)

/** Die 16 Bundesländer, Dateinamen wie bei Geofabrik. */
val REGIONS = listOf(
    Region("baden-wuerttemberg", "Baden-Württemberg"),
    Region("bayern", "Bayern"),
    Region("berlin", "Berlin"),
    Region("brandenburg", "Brandenburg"),
    Region("bremen", "Bremen"),
    Region("hamburg", "Hamburg"),
    Region("hessen", "Hessen"),
    Region("mecklenburg-vorpommern", "Mecklenburg-Vorpommern"),
    Region("niedersachsen", "Niedersachsen"),
    Region("nordrhein-westfalen", "Nordrhein-Westfalen"),
    Region("rheinland-pfalz", "Rheinland-Pfalz"),
    Region("saarland", "Saarland"),
    Region("sachsen", "Sachsen"),
    Region("sachsen-anhalt", "Sachsen-Anhalt"),
    Region("schleswig-holstein", "Schleswig-Holstein"),
    Region("thueringen", "Thüringen"),
)

data class RemotePackage(val size: Long, val date: String)

data class RegionState(
    val region: Region,
    val installedDate: String? = null,
    val installedSize: Long = 0,
    val remote: RemotePackage? = null,
    /** 0..1 während des Downloads, -1 = wartet (z. B. auf WLAN), null = kein Download. */
    val progress: Float? = null,
    val error: String? = null,
) {
    val installed get() = installedDate != null
    val updateAvailable get() = installedDate != null && remote != null && remote.date > installedDate
}

data class OfflineState(
    val regions: List<RegionState> = REGIONS.map { RegionState(it) },
    val catalogLoading: Boolean = false,
    val catalogError: String? = null,
    val wifiOnly: Boolean = true,
)

/**
 * Offline-Pakete je Bundesland: Katalog laden, über den System-Downloadmanager herunterladen
 * (läuft auch weiter, wenn die App im Hintergrund ist), prüfen und als Datenquelle bereitstellen.
 */
class OfflineStore(context: Context, private val scope: CoroutineScope) : TileRepository.OfflineSource {

    private val app = context.applicationContext
    private val downloads = app.getSystemService(DownloadManager::class.java)
    private val prefs = app.getSharedPreferences("offline", Context.MODE_PRIVATE)
    private val packageDir = File(app.getExternalFilesDir(null) ?: app.filesDir, "packages").apply { mkdirs() }
    private val downloadDir get() = File(app.getExternalFilesDir(null) ?: app.filesDir, "downloads")

    private val readers = ConcurrentHashMap<String, PackageReader>()
    private val _state = MutableStateFlow(OfflineState(wifiOnly = prefs.getBoolean(KEY_WIFI_ONLY, true)))
    val state: StateFlow<OfflineState> = _state
    private var poller: Job? = null
    /** Pakete werden im Hintergrund geöffnet; Kachelanfragen warten so lange. */
    private val opened = CountDownLatch(1)

    init {
        scope.launch(Dispatchers.IO) {
            try {
                for (r in REGIONS) openInstalled(r.id)
            } finally {
                opened.countDown()
            }
            publishInstalled()
            if (pendingDownloads().isNotEmpty()) startPolling()
        }
    }

    // --- Datenquelle für TileRepository ---

    override fun tile(y: Int, x: Int): TileRepository.OfflineTile? {
        opened.await(5, TimeUnit.SECONDS)
        var complete = false
        var found = false
        val ways = HashMap<Long, OsmWay>()
        for (reader in readers.values) {
            val t = runCatching { reader.tile(y, x) }.getOrNull() ?: continue
            found = true
            complete = complete || t.complete
            t.ways.forEach { ways[it.id] = it }
        }
        return if (found) TileRepository.OfflineTile(ways.values.toList(), complete) else null
    }

    // --- Bedienung aus den Optionen ---

    fun loadCatalog() {
        if (_state.value.catalogLoading) return
        _state.update { it.copy(catalogLoading = true, catalogError = null) }
        scope.launch(Dispatchers.IO) {
            try {
                val json = JSONObject(httpGet(BuildConfig.PACKAGE_BASE_URL + "index.json"))
                val packages = json.getJSONObject("packages")
                _state.update { s ->
                    s.copy(catalogLoading = false, regions = s.regions.map { r ->
                        val p = packages.optJSONObject(r.region.id)
                        r.copy(remote = p?.let { RemotePackage(it.getLong("size"), it.getString("date")) })
                    })
                }
            } catch (e: Exception) {
                Log.w(TAG, "Katalog nicht ladbar", e)
                _state.update { it.copy(catalogLoading = false, catalogError = "Paketliste nicht erreichbar") }
            }
        }
    }

    fun setWifiOnly(value: Boolean) {
        prefs.edit().putBoolean(KEY_WIFI_ONLY, value).apply()
        _state.update { it.copy(wifiOnly = value) }
    }

    fun download(ids: Collection<String>) {
        downloadDir.mkdirs()
        for (id in ids) {
            if (prefs.contains(KEY_DL + id)) continue
            val region = REGIONS.first { it.id == id }
            val target = File(downloadDir, "$id.wepk")
            target.delete()
            val request = DownloadManager.Request(Uri.parse(BuildConfig.PACKAGE_BASE_URL + "$id.wepk"))
                .setTitle("was erlaubt: ${region.name}")
                .setDescription("Offline-Straßendaten")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
                .setAllowedOverMetered(!_state.value.wifiOnly)
                .setAllowedOverRoaming(false)
                .setDestinationUri(Uri.fromFile(target))
            prefs.edit().putLong(KEY_DL + id, downloads.enqueue(request)).apply()
            setRegion(id) { it.copy(progress = -1f, error = null) }
        }
        startPolling()
    }

    fun cancel(id: String) {
        val dl = prefs.getLong(KEY_DL + id, -1)
        if (dl >= 0) downloads.remove(dl)
        prefs.edit().remove(KEY_DL + id).apply()
        setRegion(id) { it.copy(progress = null) }
    }

    fun delete(id: String) {
        readers.remove(id)?.close()
        File(packageDir, "$id.wepk").delete()
        setRegion(id) { it.copy(installedDate = null, installedSize = 0) }
    }

    // --- intern ---

    private fun pendingDownloads(): Map<String, Long> =
        REGIONS.mapNotNull { r -> prefs.getLong(KEY_DL + r.id, -1).takeIf { it >= 0 }?.let { r.id to it } }.toMap()

    private fun startPolling() {
        if (poller?.isActive == true) return
        poller = scope.launch(Dispatchers.IO) {
            while (true) {
                val pending = pendingDownloads()
                if (pending.isEmpty()) break
                for ((id, dl) in pending) checkDownload(id, dl)
                delay(1000)
            }
        }
    }

    private fun checkDownload(id: String, dl: Long) {
        downloads.query(DownloadManager.Query().setFilterById(dl)).use { c ->
            if (!c.moveToFirst()) {
                prefs.edit().remove(KEY_DL + id).apply()
                setRegion(id) { it.copy(progress = null) }
                return
            }
            val status = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
            val done = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
            val total = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
            when (status) {
                DownloadManager.STATUS_SUCCESSFUL -> {
                    prefs.edit().remove(KEY_DL + id).apply()
                    install(id)
                }
                DownloadManager.STATUS_FAILED -> {
                    val reason = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))
                    Log.w(TAG, "Download $id fehlgeschlagen: $reason")
                    prefs.edit().remove(KEY_DL + id).apply()
                    downloads.remove(dl)
                    setRegion(id) { it.copy(progress = null, error = "Download fehlgeschlagen") }
                }
                DownloadManager.STATUS_RUNNING ->
                    setRegion(id) { it.copy(progress = if (total > 0) done.toFloat() / total else 0f) }
                else -> setRegion(id) { it.copy(progress = -1f) } // wartet / pausiert
            }
        }
    }

    private fun install(id: String) {
        val downloaded = File(downloadDir, "$id.wepk")
        try {
            // Erst prüfen, dann das alte Paket ersetzen
            PackageReader(downloaded).use { check(it.tileLatE6 == TILE_LAT_E6 && it.tileLonE6 == TILE_LON_E6) }
            readers.remove(id)?.close()
            val target = File(packageDir, "$id.wepk")
            target.delete()
            if (!downloaded.renameTo(target)) {
                downloaded.copyTo(target, overwrite = true)
                downloaded.delete()
            }
            openInstalled(id)
            setRegion(id) { it.copy(progress = null, error = null) }
            publishInstalled()
            Log.i(TAG, "Paket $id installiert")
        } catch (e: Exception) {
            Log.w(TAG, "Paket $id ungültig", e)
            downloaded.delete()
            setRegion(id) { it.copy(progress = null, error = "Paket beschädigt") }
        }
    }

    private fun openInstalled(id: String) {
        val f = File(packageDir, "$id.wepk")
        if (!f.exists()) return
        try {
            readers[id] = PackageReader(f)
        } catch (e: Exception) {
            Log.w(TAG, "Paket $id nicht lesbar – wird entfernt", e)
            f.delete()
        }
    }

    private fun publishInstalled() {
        _state.update { s ->
            s.copy(regions = s.regions.map { r ->
                val reader = readers[r.region.id]
                r.copy(installedDate = reader?.dataDate, installedSize = File(packageDir, "${r.region.id}.wepk").length())
            })
        }
    }

    private fun setRegion(id: String, change: (RegionState) -> RegionState) {
        _state.update { s -> s.copy(regions = s.regions.map { if (it.region.id == id) change(it) else it }) }
    }

    private fun httpGet(url: String): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 10_000
            conn.readTimeout = 20_000
            if (conn.responseCode != 200) throw java.io.IOException("HTTP ${conn.responseCode}")
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    companion object {
        private const val TAG = "OfflineStore"
        private const val KEY_WIFI_ONLY = "wifi_only"
        private const val KEY_DL = "download_"
        private const val TILE_LAT_E6 = 20_000
        private const val TILE_LON_E6 = 30_000
    }
}
