package de.waserlaubt.app

import de.waserlaubt.app.data.OverpassClient
import de.waserlaubt.app.geo.Geo
import de.waserlaubt.app.matching.Fix
import de.waserlaubt.app.matching.RoadMatcher
import de.waserlaubt.app.osm.LatLon
import de.waserlaubt.app.osm.MaxspeedResolver
import de.waserlaubt.app.osm.SpeedValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import kotlin.math.atan2
import kotlin.math.cos

/** Simulierte Fahrt über echte Overpass-Daten aus Diepholz (Kachel 52.60–52.62 / 8.35–8.38). */
class RealDataDriveTest {
    private val ways = OverpassClient.parse(javaClass.getResource("/diepholz.json")!!.readText())

    private fun bearing(a: LatLon, b: LatLon): Double {
        val dx = (b.lon - a.lon) * cos(Math.toRadians(a.lat))
        return Geo.normalize(Math.toDegrees(atan2(dx, b.lat - a.lat)))
    }

    /** Fährt in 10-m-Schritten über die Way-Geometrie und liefert das Limit je Position. */
    private fun drive(path: List<LatLon>): List<Pair<Long?, SpeedValue?>> {
        val matcher = RoadMatcher()
        val out = mutableListOf<Pair<Long?, SpeedValue?>>()
        for (i in 0 until path.size - 1) {
            val a = path[i]; val b = path[i + 1]
            val brg = bearing(a, b)
            val len = Geo.distanceToSegment(b, a, a).distanceM // Abstand a–b
            var d = 0.0
            while (d < len) {
                val p = Geo.offset(a, brg, d)
                val m = matcher.update(Fix(p, brg, 14.0, 5.0), ways)
                out += m?.way?.id to m?.let { MaxspeedResolver.resolve(it.way.tags, it.forward, NOON).value }
                d += 10.0
            }
        }
        return out
    }

    @Test fun parsesTile() {
        assertEquals(352, ways.size)
    }

    @Test fun steinfelderStrasseBothDirections() {
        val way = ways.first { it.id == 258184129L }
        val forward = drive(way.points).filter { it.first == way.id }
        val backward = drive(way.points.reversed()).filter { it.first == way.id }
        assertTrue("Way vorwärts erkannt", forward.size > 3)
        assertTrue("Way rückwärts erkannt", backward.size > 3)
        assertTrue(forward.all { it.second == SpeedValue.Number(50) })
        assertTrue(backward.all { it.second == SpeedValue.Number(70) })
    }

    @Test fun ruralRoadBothDirections() {
        val way = ways.first { it.id == 418796663L }
        val fwd = drive(way.points).filter { it.first == way.id }.map { it.second }.toSet()
        val bwd = drive(way.points.reversed()).filter { it.first == way.id }.map { it.second }.toSet()
        assertEquals(setOf(SpeedValue.Number(70)), fwd)
        assertEquals(setOf(SpeedValue.Number(100)), bwd)
    }

    companion object {
        private val NOON = LocalDateTime.of(2026, 10, 7, 12, 0)
    }
}
