package de.waserlaubt.app.data

import de.waserlaubt.app.osm.LatLon
import de.waserlaubt.app.osm.OsmWay
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Lädt befahrbare Straßen mit Tags und Geometrie über die Overpass-API. */
class OverpassClient(private val userAgent: String) {

    private val endpoints = listOf(
        "https://overpass-api.de/api/interpreter",
        "https://overpass.private.coffee/api/interpreter",
        "https://overpass.kumi.systems/api/interpreter",
    )

    /** Zuletzt erfolgreicher Server wird zuerst gefragt. */
    @Volatile private var preferred = 0

    /** Lädt die Rohdaten (JSON) für eine Bounding-Box; probiert nacheinander alle Server. */
    fun fetchRaw(south: Double, west: Double, north: Double, east: Double): String {
        val query = "[out:json][timeout:25];" +
            "way[\"highway\"~\"^($HIGHWAYS)$\"]($south,$west,$north,$east);" +
            "out tags geom;"
        val body = "data=" + URLEncoder.encode(query, "UTF-8")
        var lastError: Exception? = null
        for (i in endpoints.indices) {
            val index = (preferred + i) % endpoints.size
            try {
                return post(endpoints[index], body).also { preferred = index }
            } catch (e: Exception) {
                lastError = e
            }
        }
        throw IOException("Overpass nicht erreichbar", lastError)
    }

    private fun post(endpoint: String, body: String): String {
        val conn = URL(endpoint).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.connectTimeout = 8_000
            conn.readTimeout = 30_000
            conn.doOutput = true
            conn.setRequestProperty("User-Agent", userAgent)
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            conn.outputStream.use { it.write(body.toByteArray()) }
            val code = conn.responseCode
            if (code != 200) throw IOException("HTTP $code von $endpoint")
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    companion object {
        /** Für Autos relevante Straßentypen; service (Parkplätze, Zufahrten) bewusst ausgelassen. */
        private const val HIGHWAYS = "motorway|motorway_link|trunk|trunk_link|primary|primary_link|" +
            "secondary|secondary_link|tertiary|tertiary_link|unclassified|residential|living_street|road"

        fun parse(json: String): List<OsmWay> {
            val elements = JSONObject(json).optJSONArray("elements") ?: return emptyList()
            val ways = ArrayList<OsmWay>(elements.length())
            for (i in 0 until elements.length()) {
                val el = elements.getJSONObject(i)
                if (el.optString("type") != "way") continue
                val geom = el.optJSONArray("geometry") ?: continue
                val points = ArrayList<LatLon>(geom.length())
                for (j in 0 until geom.length()) {
                    val p = geom.optJSONObject(j) ?: continue // null-Einträge bei Kachelrand möglich
                    points += LatLon(p.getDouble("lat"), p.getDouble("lon"))
                }
                if (points.size < 2) continue
                val tagsJson = el.optJSONObject("tags")
                val tags = HashMap<String, String>()
                tagsJson?.keys()?.forEach { k -> tags[k] = tagsJson.getString(k) }
                ways += OsmWay(el.getLong("id"), tags, points)
            }
            return ways
        }
    }
}
