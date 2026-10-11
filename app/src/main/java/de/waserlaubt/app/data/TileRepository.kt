package de.waserlaubt.app.data

import de.waserlaubt.app.geo.Geo
import de.waserlaubt.app.osm.LatLon
import de.waserlaubt.app.osm.OsmWay
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import android.util.Log
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs
import kotlin.math.floor

/**
 * Hält Straßendaten in Kacheln von ca. 2,2 × 2 km. Kacheln kommen aus installierten
 * Offline-Paketen oder werden bei Bedarf über Overpass geladen und 30 Tage auf dem Gerät
 * zwischengespeichert. Höchstens zwei Kacheln werden gleichzeitig online geladen, um die
 * öffentlichen Overpass-Server zu schonen.
 */
class TileRepository(
    private val client: OverpassClient,
    private val cacheDir: File,
    private val scope: CoroutineScope,
    private val offlineSource: OfflineSource? = null,
) {
    data class TileKey(val y: Int, val x: Int)

    /** Wege einer Kachel aus einem Offline-Paket; [complete] = ganz im Gebiet des Pakets. */
    class OfflineTile(val ways: List<OsmWay>, val complete: Boolean)

    fun interface OfflineSource {
        fun tile(y: Int, x: Int): OfflineTile?
    }

    data class Status(val loading: Int = 0, val loaded: Int = 0, val error: String? = null)

    private val tiles = ConcurrentHashMap<TileKey, List<OsmWay>>()
    private val inFlight = ConcurrentHashMap.newKeySet<TileKey>()
    private val failedAt = ConcurrentHashMap<TileKey, Long>()
    private val failures = ConcurrentHashMap<TileKey, Int>()
    /** Kacheln, die nur teilweise aus einem Offline-Paket stammen. */
    private val partial = ConcurrentHashMap.newKeySet<TileKey>()
    private val fetchSlots = Semaphore(MAX_PARALLEL)

    @Volatile private var center = TileKey(0, 0)

    private val _status = MutableStateFlow(Status())
    val status: StateFlow<Status> = _status

    init {
        File(cacheDir, DIR).mkdirs()
    }

    /** Sorgt dafür, dass die Kacheln rund um die Position und in Fahrtrichtung vorhanden sind. */
    fun ensureAround(p: LatLon, bearingDeg: Double?) {
        val center = keyOf(p)
        this.center = center
        // Weit entfernte Kacheln aus dem Speicher werfen (bleiben im Datei-Cache)
        tiles.keys.removeAll { !isNear(it, center) }
        partial.removeAll { !isNear(it, center) }
        // Reihenfolge = Priorität: eigene Kachel, dann in Fahrtrichtung, dann seitliche Ränder
        val keys = linkedSetOf(center)
        if (bearingDeg != null) {
            for (d in LOOKAHEAD_M) keys += keyOf(Geo.offset(p, bearingDeg, d))
        }
        for (b in listOf(0.0, 90.0, 180.0, 270.0)) keys += keyOf(Geo.offset(p, b, EDGE_MARGIN_M))
        keys.forEach(::ensure)
    }

    /** Alle geladenen Straßen der Kachel von [p] und ihrer Nachbarn, ohne Duplikate. */
    fun waysNear(p: LatLon): Collection<OsmWay> {
        val k = keyOf(p)
        val result = HashMap<Long, OsmWay>()
        for (dy in -1..1) for (dx in -1..1) {
            tiles[TileKey(k.y + dy, k.x + dx)]?.forEach { result[it.id] = it }
        }
        return result.values
    }

    fun isLoaded(p: LatLon): Boolean = tiles.containsKey(keyOf(p))

    private fun ensure(key: TileKey) {
        if ((tiles.containsKey(key) && key !in partial) || !inFlight.add(key)) return
        publish()
        scope.launch(Dispatchers.IO) {
            try {
                load(key)
            } finally {
                inFlight.remove(key)
                publish()
            }
        }
    }

    private suspend fun load(key: TileKey) {
        // 1. Offline-Paket: sofort und ohne Netz
        val offline = runCatching { offlineSource?.tile(key.y, key.x) }.getOrNull()
        if (offline != null) {
            tiles[key] = offline.ways
            Log.i(TAG, "Kachel $key offline: ${offline.ways.size} Straßen${if (offline.complete) "" else " (Rand)"}")
            if (offline.complete) {
                partial.remove(key)
                return
            }
            // Kachel am Rand des Pakets: Rest der Kachel online ergänzen
            partial += key
        }
        // 2. Frischer Online-Cache
        readCache(key, maxAgeMs = CACHE_MAX_AGE_MS)?.let {
            tiles[key] = it
            partial.remove(key)
            return
        }
        // 3. Overpass
        val failed = failedAt[key]
        if (failed != null && System.currentTimeMillis() - failed < retryDelay(key)) return
        fetchSlots.withPermit {
            // Inzwischen vorbeigefahren → nicht mehr laden
            if (!isNear(key, center)) return
            try {
                val (s, w, n, e) = bounds(key)
                val raw = client.fetchRaw(s, w, n, e)
                val ways = OverpassClient.parse(raw)
                cacheFile(key).writeText(raw)
                Log.i(TAG, "Kachel $key geladen: ${ways.size} Straßen")
                tiles[key] = ways
                partial.remove(key)
                failedAt.remove(key)
                failures.remove(key)
                _status.value = _status.value.copy(error = null)
            } catch (e: Exception) {
                Log.w(TAG, "Kachel $key fehlgeschlagen", e)
                failedAt[key] = System.currentTimeMillis()
                failures.merge(key, 1, Int::plus)
                // Notfalls veraltete Daten nehmen (aber keine Teilkachel aus dem Paket überschreiben)
                if (key !in partial) readCache(key, maxAgeMs = Long.MAX_VALUE)?.let { tiles[key] = it }
                if (!tiles.containsKey(key)) _status.value = _status.value.copy(error = e.message ?: e.javaClass.simpleName)
            }
        }
    }

    private fun isNear(k: TileKey, c: TileKey) = abs(k.y - c.y) <= KEEP_RADIUS && abs(k.x - c.x) <= KEEP_RADIUS

    /** 5 s, 10 s, 20 s, 40 s, dann jede Minute – Funklöcher sind meist kurz. */
    private fun retryDelay(key: TileKey): Long {
        val n = (failures[key] ?: 1).coerceIn(1, 5)
        return (RETRY_BASE_MS shl (n - 1)).coerceAtMost(RETRY_MAX_MS)
    }

    private fun publish() {
        _status.value = _status.value.copy(loading = inFlight.size, loaded = tiles.size)
    }

    private fun readCache(key: TileKey, maxAgeMs: Long): List<OsmWay>? {
        val f = cacheFile(key)
        if (!f.exists() || System.currentTimeMillis() - f.lastModified() > maxAgeMs) return null
        return try {
            OverpassClient.parse(f.readText())
        } catch (e: Exception) {
            f.delete()
            null
        }
    }

    private fun cacheFile(key: TileKey) = File(cacheDir, "$DIR/${key.y}_${key.x}.json")

    private fun keyOf(p: LatLon) = TileKey(floor(p.lat / TILE_LAT).toInt(), floor(p.lon / TILE_LON).toInt())

    private fun bounds(k: TileKey) = listOf(
        k.y * TILE_LAT, k.x * TILE_LON, (k.y + 1) * TILE_LAT, (k.x + 1) * TILE_LON,
    ).map { "%.5f".format(java.util.Locale.ROOT, it).toDouble() }

    companion object {
        private const val TAG = "TileRepository"
        private const val DIR = "tiles"
        private const val TILE_LAT = 0.02
        private const val TILE_LON = 0.03
        private const val EDGE_MARGIN_M = 300.0
        private const val KEEP_RADIUS = 3
        private const val MAX_PARALLEL = 2
        // Bei 100 km/h reichen 4,5 km für knapp drei Minuten
        private val LOOKAHEAD_M = listOf(500.0, 1500.0, 3000.0, 4500.0)
        private const val CACHE_MAX_AGE_MS = 30L * 24 * 3600 * 1000
        private const val RETRY_BASE_MS = 5_000L
        private const val RETRY_MAX_MS = 60_000L
    }
}
