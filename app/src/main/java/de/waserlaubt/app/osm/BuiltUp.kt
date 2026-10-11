package de.waserlaubt.app.osm

import de.waserlaubt.app.geo.Geo

/**
 * Grobe Schätzung „innerorts“: OSM kennt die Ortstafeln meist nicht als Fläche, aber
 * mehrere Wohnstraßen in der Nähe sind ein guter Hinweis auf eine geschlossene Ortschaft.
 */
object BuiltUp {
    private val residential = setOf("residential", "living_street")

    fun estimate(p: LatLon, ways: Collection<OsmWay>, radiusM: Double = 150.0, minWays: Int = 3): Boolean {
        var count = 0
        for (way in ways) {
            if (way.highway !in residential) continue
            if (way.points.zipWithNext().any { (a, b) -> Geo.distanceToSegment(p, a, b).distanceM <= radiusM }) {
                if (++count >= minWays) return true
            }
        }
        return false
    }
}
