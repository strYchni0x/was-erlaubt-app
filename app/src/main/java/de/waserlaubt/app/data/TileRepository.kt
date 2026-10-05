package de.waserlaubt.app.data

import de.waserlaubt.app.geo.Geo
import de.waserlaubt.app.osm.LatLon
import de.waserlaubt.app.osm.OsmWay
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import android.util.Log
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs
import kotlin.math.floor

/**
 * Hält Straßendaten in Kacheln von ca. 2,2 × 2 km. Kacheln werden bei Bedarf über Overpass
 * geladen und eine Woche auf dem Gerät zwischengespeichert. Geladen wird immer nur eine
 * Kachel gleichzeitig, um die öffentlichen Overpass-Server zu schonen.
 */
class TileRepository(
    private val client: OverpassClient,
    private val cacheDir: File,
    private val scope: CoroutineScope,
) {
    data class TileKey(val y: Int, val x: Int)

    data class Status(val loading: Int = 0, val loaded: Int = 0, val error: String? = null)

    private val tiles = ConcurrentHashMap<TileKey, List<OsmWay>>()
    private val inFlight = ConcurrentHashMap.newKeySet<TileKey>()
    private val failedAt = ConcurrentHashMap<TileKey, Long>()
    private val fetchMutex = Mutex()

    private val _status = MutableStateFlow(Status())
    val status: StateFlow<Status> = _status

    init {
        File(cacheDir, DIR).mkdirs()
    }

    /** Sorgt dafür, dass die Kacheln rund um die Position und in Fahrtrichtung vorhanden sind. */
    fun ensureAround(p: LatLon, bearingDeg: Double?) {
        val center = keyOf(p)
        // Weit entfernte Kacheln aus dem Speicher werfen (bleiben im Datei-Cache)
        tiles.keys.removeAll { abs(it.y - center.y) > KEEP_RADIUS || abs(it.x - center.x) > KEEP_RADIUS }
        val keys = linkedSetOf(center)
        // Kachelrand in der Nähe → Nachbarkachel vorsorglich laden
        for (b in listOf(0.0, 90.0, 180.0, 270.0)) keys += keyOf(Geo.offset(p, b, EDGE_MARGIN_M))
        if (bearingDeg != null) {
            for (d in LOOKAHEAD_M) keys += keyOf(Geo.offset(p, bearingDeg, d))
        }
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
        if (tiles.containsKey(key) || !inFlight.add(key)) return
        val failed = failedAt[key]
        if (failed != null && System.currentTimeMillis() - failed < RETRY_AFTER_MS) {
            inFlight.remove(key)
            return
        }
        // Frischer Cache → sofort, ohne auf das Netz zu warten
        readCache(key, maxAgeMs = CACHE_MAX_AGE_MS)?.let {
            tiles[key] = it
            inFlight.remove(key)
            publish()
            return
        }
        publish()
        scope.launch(Dispatchers.IO) {
            fetchMutex.withLock {
                try {
                    val (s, w, n, e) = bounds(key)
                    val raw = client.fetchRaw(s, w, n, e)
                    val ways = OverpassClient.parse(raw)
                    cacheFile(key).writeText(raw)
                    Log.i(TAG, "Kachel $key geladen: ${ways.size} Straßen")
                    tiles[key] = ways
                    failedAt.remove(key)
                    _status.value = _status.value.copy(error = null)
                } catch (e: Exception) {
                    Log.w(TAG, "Kachel $key fehlgeschlagen", e)
                    failedAt[key] = System.currentTimeMillis()
                    // Notfalls veraltete Daten nehmen
                    readCache(key, maxAgeMs = Long.MAX_VALUE)?.let { tiles[key] = it }
                    _status.value = _status.value.copy(error = e.message ?: e.javaClass.simpleName)
                } finally {
                    inFlight.remove(key)
                    publish()
                }
            }
        }
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
        private const val KEEP_RADIUS = 2
        private val LOOKAHEAD_M = listOf(600.0, 1200.0)
        private const val CACHE_MAX_AGE_MS = 7L * 24 * 3600 * 1000
        private const val RETRY_AFTER_MS = 30_000L
    }
}
