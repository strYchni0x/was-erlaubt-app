package de.waserlaubt.app.osm

/** Ein Punkt in WGS84-Koordinaten. */
data class LatLon(val lat: Double, val lon: Double)

/** Eine OSM-Straße (way) mit Geometrie in Knotenreihenfolge und allen Tags. */
data class OsmWay(
    val id: Long,
    val tags: Map<String, String>,
    val points: List<LatLon>,
) {
    val highway: String? get() = tags["highway"]
    val name: String? get() = tags["name"] ?: tags["ref"]

    /**
     * Einbahnrichtung: +1 = nur in Zeichenrichtung, -1 = nur entgegen, 0 = beide.
     * Autobahnen und Kreisverkehre gelten implizit als Einbahnstraße.
     */
    val onewayDirection: Int
        get() = when (tags["oneway"]) {
            "yes", "true", "1" -> 1
            "-1", "reverse" -> -1
            "no", "false", "0" -> 0
            else -> when {
                highway == "motorway" || highway == "motorway_link" -> 1
                tags["junction"] == "roundabout" || tags["junction"] == "circular" -> 1
                else -> 0
            }
        }
}
