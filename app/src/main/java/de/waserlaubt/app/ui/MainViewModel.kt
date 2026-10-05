package de.waserlaubt.app.ui

import android.annotation.SuppressLint
import android.app.Application
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import de.waserlaubt.app.data.OverpassClient
import de.waserlaubt.app.data.TileRepository
import de.waserlaubt.app.matching.Fix
import de.waserlaubt.app.matching.RoadMatcher
import de.waserlaubt.app.osm.LatLon
import de.waserlaubt.app.osm.MaxspeedResolver
import de.waserlaubt.app.osm.SpeedLimit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDateTime

enum class GpsState { SEARCHING, OK, LOST }

data class UiState(
    val limit: SpeedLimit? = null,
    val roadName: String? = null,
    val speedKmh: Int? = null,
    val gps: GpsState = GpsState.SEARCHING,
    val loadingData: Boolean = false,
    val dataError: String? = null,
    val noRoad: Boolean = false,
    val debug: String = "",
)

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val locationManager = app.getSystemService(LocationManager::class.java)
    private val tiles = TileRepository(
        OverpassClient("WasErlaubt/${app.packageManager.getPackageInfo(app.packageName, 0).versionName} (Android)"),
        app.cacheDir,
        viewModelScope,
    )
    private val matcher = RoadMatcher()
    private val processMutex = Mutex()

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state

    private var lastGpsFixAt = 0L
    private var missedMatches = 0
    private var watchdog: Job? = null

    init {
        viewModelScope.launch {
            tiles.status.collect { s ->
                _state.update { it.copy(loadingData = s.loading > 0, dataError = s.error) }
            }
        }
    }

    // Explizites Objekt statt Lambda: onStatusChanged ist unter API 29 abstrakt
    private val gpsListener = object : LocationListener {
        override fun onLocationChanged(location: Location) = onGpsFix(location)
        override fun onProviderEnabled(provider: String) {}
        override fun onProviderDisabled(provider: String) {}
        @Deprecated("Deprecated in Java")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
    }

    /** Grobe Netzwerkposition: nur zum frühen Vorladen der Straßendaten. */
    private val networkListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            tiles.ensureAround(LatLon(location.latitude, location.longitude), null)
        }
        override fun onProviderEnabled(provider: String) {}
        override fun onProviderDisabled(provider: String) {}
        @Deprecated("Deprecated in Java")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
    }

    @SuppressLint("MissingPermission") // Activity prüft die Berechtigung vorher
    fun start() {
        val lm = locationManager ?: return
        if (lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 0f, gpsListener, Looper.getMainLooper())
        }
        if (lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
            lm.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 10_000L, 50f, networkListener, Looper.getMainLooper())
        }
        lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)?.let {
            tiles.ensureAround(LatLon(it.latitude, it.longitude), null)
        }
        watchdog?.cancel()
        watchdog = viewModelScope.launch {
            while (isActive) {
                delay(2000)
                if (lastGpsFixAt != 0L && System.currentTimeMillis() - lastGpsFixAt > GPS_LOST_MS) {
                    // Letztes Limit stehen lassen (z. B. im Tunnel), nur den Status ändern
                    _state.update { it.copy(gps = GpsState.LOST, speedKmh = null) }
                }
            }
        }
    }

    fun stop() {
        locationManager?.removeUpdates(gpsListener)
        locationManager?.removeUpdates(networkListener)
        watchdog?.cancel()
    }

    private fun onGpsFix(location: Location) {
        lastGpsFixAt = System.currentTimeMillis()
        viewModelScope.launch(Dispatchers.Default) {
            processMutex.withLock { process(location) }
        }
    }

    private fun process(location: Location) {
        val pos = LatLon(location.latitude, location.longitude)
        val bearing = if (location.hasBearing()) location.bearing.toDouble() else null
        val speed = if (location.hasSpeed()) location.speed.toDouble() else null
        tiles.ensureAround(pos, bearing)

        val speedKmh = speed?.let { (it * 3.6).toInt() }
        if (location.accuracy > MAX_ACCURACY_M) {
            _state.update { it.copy(gps = GpsState.SEARCHING, speedKmh = speedKmh) }
            return
        }

        val fix = Fix(pos, bearing, speed, location.accuracy.toDouble())
        val match = matcher.update(fix, tiles.waysNear(pos))
        if (match == null) {
            missedMatches++
            _state.update {
                it.copy(
                    gps = GpsState.OK, speedKmh = speedKmh,
                    // Kurze Aussetzer überbrücken, danach "keine Straße"
                    limit = if (missedMatches > 5) null else it.limit,
                    roadName = if (missedMatches > 5) null else it.roadName,
                    noRoad = missedMatches > 5 && tiles.isLoaded(pos),
                    debug = "keine Straße im Umkreis · ±${location.accuracy.toInt()} m",
                )
            }
            return
        }
        missedMatches = 0
        val limit = MaxspeedResolver.resolve(match.way.tags, match.forward, LocalDateTime.now())
        val debug = buildString {
            append("way ${match.way.id} · ${match.way.highway} · ")
            append(if (match.forward) "vorwärts" else "rückwärts")
            append(" · ${match.distanceM.toInt()} m · ±${location.accuracy.toInt()} m")
            bearing?.let { append(" · Kurs ${it.toInt()}°") }
            match.way.tags.filterKeys { "maxspeed" in it || it.startsWith("zone:") || it == "oneway" }
                .forEach { (k, v) -> append("\n$k=$v") }
        }
        _state.update {
            it.copy(
                limit = limit, roadName = match.way.name, speedKmh = speedKmh,
                gps = GpsState.OK, noRoad = false, debug = debug,
            )
        }
    }

    override fun onCleared() {
        stop()
    }

    companion object {
        private const val GPS_LOST_MS = 8_000L
        private const val MAX_ACCURACY_M = 40f
    }
}
