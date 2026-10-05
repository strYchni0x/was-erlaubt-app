package de.waserlaubt.app.matching

import de.waserlaubt.app.geo.Geo
import de.waserlaubt.app.osm.LatLon
import de.waserlaubt.app.osm.OsmWay

/** Eine GPS-Position. [bearingDeg] ist null, wenn kein verlässlicher Kurs vorliegt. */
data class Fix(
    val position: LatLon,
    val bearingDeg: Double?,
    val speedMps: Double?,
    val accuracyM: Double,
)

/** Zuordnung einer Position zu einer Straße. [forward] = Fahrt in Zeichenrichtung des Ways. */
data class Match(
    val way: OsmWay,
    val forward: Boolean,
    val distanceM: Double,
    val score: Double,
)

/**
 * Ordnet GPS-Positionen der passendsten Straße zu.
 *
 * Bewertung = Abstand zur Straße + Strafpunkte für Abweichung zwischen Fahrtkurs und
 * Straßenrichtung. Einbahnstraßen entgegen der Fahrtrichtung werden verworfen. Damit die
 * Anzeige an Kreuzungen und bei parallelen Straßen nicht flackert, wird ein Wechsel erst
 * übernommen, wenn er [switchConfirmations] Positionen in Folge bestätigt wird.
 */
class RoadMatcher(
    private val switchConfirmations: Int = 2,
    private val minSpeedForBearingMps: Double = 2.5,
) {
    private var current: Match? = null
    private var pendingKey: Pair<Long, Boolean>? = null
    private var pendingCount = 0

    fun reset() {
        current = null
        pendingKey = null
        pendingCount = 0
    }

    /** Liefert die (geglättete) aktuelle Zuordnung oder null, wenn keine Straße in der Nähe ist. */
    fun update(fix: Fix, ways: Collection<OsmWay>): Match? {
        val prev = current
        val bearing = fix.bearingDeg?.takeIf { (fix.speedMps ?: 0.0) >= minSpeedForBearingMps }
        val candidates = ways.mapNotNull { score(it, fix, bearing, prev) }
        val best = candidates.minByOrNull { it.score }
        if (best == null) {
            reset()
            return null
        }
        val prevNow = prev?.let { p -> candidates.firstOrNull { it.way.id == p.way.id } }

        // Erste Zuordnung oder alte Straße nicht mehr in Reichweite → sofort wechseln
        if (prev == null || prevNow == null) return accept(best)

        if (best.way.id == prev.way.id && best.forward == prev.forward) return accept(best)

        val key = best.way.id to best.forward
        if (key == pendingKey) pendingCount++ else { pendingKey = key; pendingCount = 1 }
        if (pendingCount >= switchConfirmations) return accept(best)

        // Noch nicht bestätigt: bei der bisherigen Straße bleiben
        current = prevNow
        return prevNow
    }

    private fun accept(m: Match): Match {
        current = m
        pendingKey = null
        pendingCount = 0
        return m
    }

    private fun score(way: OsmWay, fix: Fix, bearing: Double?, prev: Match?): Match? {
        val maxDist = (fix.accuracyM * 1.5).coerceIn(MIN_RADIUS_M, MAX_RADIUS_M)
        var best: Match? = null
        val pts = way.points
        for (i in 0 until pts.size - 1) {
            val hit = Geo.distanceToSegment(fix.position, pts[i], pts[i + 1])
            if (hit.distanceM > maxDist) continue

            val forward: Boolean
            var penalty = 0.0
            if (bearing != null) {
                val diff = Geo.angleDiff(bearing, hit.bearingDeg)
                forward = diff <= 90
                val oneway = way.onewayDirection
                if ((oneway == 1 && !forward) || (oneway == -1 && forward)) continue
                penalty = (if (forward) diff else 180 - diff) * ANGLE_WEIGHT
            } else {
                // Ohne Kurs (Stillstand): bisherige Richtung beibehalten, sonst Einbahnrichtung
                forward = if (prev != null && prev.way.id == way.id) prev.forward else way.onewayDirection != -1
            }
            var s = hit.distanceM + penalty
            if (prev != null && prev.way.id == way.id) s -= CONTINUITY_BONUS_M
            if (best == null || s < best.score) best = Match(way, forward, hit.distanceM, s)
        }
        return best
    }

    companion object {
        private const val MIN_RADIUS_M = 30.0
        private const val MAX_RADIUS_M = 60.0
        /** Meter Strafe pro Grad Richtungsabweichung (30° ≈ 15 m). */
        private const val ANGLE_WEIGHT = 0.5
        /** Bonus für die bisher befahrene Straße. */
        private const val CONTINUITY_BONUS_M = 8.0
    }
}
