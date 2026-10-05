package de.waserlaubt.app

import de.waserlaubt.app.geo.Geo
import de.waserlaubt.app.matching.Fix
import de.waserlaubt.app.matching.RoadMatcher
import de.waserlaubt.app.osm.LatLon
import de.waserlaubt.app.osm.OsmWay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RoadMatcherTest {
    private val origin = LatLon(52.6, 8.37)

    /** Gerade Straße durch [origin] mit Zeichenrichtung [bearing]. */
    private fun road(id: Long, bearing: Double, offsetM: Double = 0.0, vararg tags: Pair<String, String>): OsmWay {
        val c = if (offsetM == 0.0) origin else Geo.offset(origin, bearing + 90, offsetM)
        return OsmWay(id, mapOf("highway" to "primary", *tags), listOf(
            Geo.offset(c, bearing + 180, 500.0), Geo.offset(c, bearing, 500.0),
        ))
    }

    private fun fix(bearing: Double?, p: LatLon = origin, speed: Double = 15.0) = Fix(p, bearing, speed, 5.0)

    @Test fun directionForwardAndBackward() {
        val north = road(1, 0.0)
        assertTrue(RoadMatcher().update(fix(5.0), listOf(north))!!.forward)
        assertFalse(RoadMatcher().update(fix(178.0), listOf(north))!!.forward)
    }

    @Test fun crossingRoadIsDecidedByBearing() {
        val ns = road(1, 0.0)
        val ew = road(2, 90.0)
        assertEquals(2L, RoadMatcher().update(fix(268.0), listOf(ns, ew))!!.way.id)
        assertEquals(1L, RoadMatcher().update(fix(2.0), listOf(ns, ew))!!.way.id)
    }

    @Test fun onewayAgainstDirectionIsIgnored() {
        val oneway = road(1, 0.0, 0.0, "oneway" to "yes")
        val parallel = road(2, 0.0, offsetM = 15.0)
        // Fährt nach Süden, direkt auf der Einbahnstraße → muss die Parallelstraße sein
        assertEquals(2L, RoadMatcher().update(fix(180.0), listOf(oneway, parallel))!!.way.id)
    }

    @Test fun nothingNearby() {
        assertNull(RoadMatcher().update(fix(0.0), listOf(road(1, 0.0, offsetM = 200.0))))
    }

    @Test fun switchNeedsConfirmation() {
        val a = road(1, 0.0)
        val b = road(2, 0.0, offsetM = 25.0)
        val m = RoadMatcher()
        assertEquals(1L, m.update(fix(0.0), listOf(a, b))!!.way.id)
        val nearB = Geo.offset(origin, 90.0, 24.0)
        assertEquals("erste Abweichung wird ignoriert", 1L, m.update(fix(0.0, nearB), listOf(a, b))!!.way.id)
        assertEquals(2L, m.update(fix(0.0, nearB), listOf(a, b))!!.way.id)
    }

    @Test fun standingStillKeepsDirection() {
        val m = RoadMatcher()
        val r = road(1, 0.0)
        assertFalse(m.update(fix(180.0), listOf(r))!!.forward)
        assertFalse(m.update(fix(null, speed = 0.0), listOf(r))!!.forward)
    }
}
