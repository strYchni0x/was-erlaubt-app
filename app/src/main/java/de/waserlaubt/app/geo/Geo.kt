package de.waserlaubt.app.geo

import de.waserlaubt.app.osm.LatLon
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/** Einfache Geometrie in einer lokalen ebenen Projektion – auf wenigen Kilometern genau genug. */
object Geo {
    private const val M_PER_DEG_LAT = 110_540.0
    private const val M_PER_DEG_LON_EQUATOR = 111_320.0

    /** Ergebnis der Projektion eines Punktes auf ein Segment. */
    data class SegmentHit(val distanceM: Double, val bearingDeg: Double)

    /** Abstand von [p] zum Segment [a]–[b] sowie Richtung des Segments von a nach b. */
    fun distanceToSegment(p: LatLon, a: LatLon, b: LatLon): SegmentHit {
        val kx = M_PER_DEG_LON_EQUATOR * cos(p.lat * PI / 180)
        val ax = (a.lon - p.lon) * kx
        val ay = (a.lat - p.lat) * M_PER_DEG_LAT
        val bx = (b.lon - p.lon) * kx
        val by = (b.lat - p.lat) * M_PER_DEG_LAT
        val dx = bx - ax
        val dy = by - ay
        val len2 = dx * dx + dy * dy
        // Projektion des Ursprungs (= p) auf das Segment, auf [0,1] begrenzt
        val t = if (len2 == 0.0) 0.0 else (-(ax * dx + ay * dy) / len2).coerceIn(0.0, 1.0)
        val dist = hypot(ax + t * dx, ay + t * dy)
        return SegmentHit(dist, normalize(atan2(dx, dy) * 180 / PI))
    }

    /** Punkt, der [distanceM] Meter von [p] in Richtung [bearingDeg] liegt. */
    fun offset(p: LatLon, bearingDeg: Double, distanceM: Double): LatLon {
        val rad = bearingDeg * PI / 180
        val dLat = distanceM * cos(rad) / M_PER_DEG_LAT
        val dLon = distanceM * sin(rad) / (M_PER_DEG_LON_EQUATOR * cos(p.lat * PI / 180))
        return LatLon(p.lat + dLat, p.lon + dLon)
    }

    /** Kleinster Winkel zwischen zwei Richtungen, 0..180°. */
    fun angleDiff(a: Double, b: Double): Double {
        val d = abs(normalize(a) - normalize(b))
        return if (d > 180) 360 - d else d
    }

    fun normalize(deg: Double): Double = ((deg % 360) + 360) % 360
}
