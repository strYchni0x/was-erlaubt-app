package de.waserlaubt.app

import de.waserlaubt.app.osm.MaxspeedResolver
import de.waserlaubt.app.osm.SpeedValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class MaxspeedResolverTest {
    // Mittwoch, 2026-10-07
    private val wedMorning = LocalDateTime.of(2026, 10, 7, 8, 0)
    private val wedNight = LocalDateTime.of(2026, 10, 7, 23, 30)
    private val sunMorning = LocalDateTime.of(2026, 10, 11, 8, 0)

    private fun resolve(vararg tags: Pair<String, String>, forward: Boolean = true, now: LocalDateTime = wedMorning) =
        MaxspeedResolver.resolve(mapOf(*tags), forward, now)

    @Test fun plainNumber() {
        assertEquals(SpeedValue.Number(70), resolve("highway" to "primary", "maxspeed" to "70").value)
    }

    @Test fun directionSpecific() {
        val tags = arrayOf("highway" to "primary", "maxspeed:forward" to "50", "maxspeed:backward" to "100")
        assertEquals(SpeedValue.Number(50), resolve(*tags, forward = true).value)
        assertEquals(SpeedValue.Number(100), resolve(*tags, forward = false).value)
    }

    @Test fun directionFallsBackToGeneral() {
        val tags = arrayOf("maxspeed" to "70", "maxspeed:backward" to "50")
        assertEquals(SpeedValue.Number(70), resolve(*tags, forward = true).value)
        assertEquals(SpeedValue.Number(50), resolve(*tags, forward = false).value)
    }

    @Test fun implicitValues() {
        assertEquals(SpeedValue.Number(50), resolve("maxspeed" to "DE:urban").value)
        assertEquals(SpeedValue.Number(100), resolve("maxspeed:type" to "DE:rural").value)
        assertEquals(SpeedValue.Number(30), resolve("zone:maxspeed" to "DE:30").value)
        assertEquals(SpeedValue.Number(30), resolve("maxspeed:type" to "DE:zone30").value)
        assertEquals(SpeedValue.None, resolve("maxspeed" to "none").value)
        assertEquals(SpeedValue.Walk, resolve("maxspeed" to "walk").value)
        assertEquals(SpeedValue.Variable, resolve("maxspeed" to "signals").value)
        assertEquals(SpeedValue.Number(130), resolve("maxspeed" to "AT:motorway").value)
    }

    @Test fun mphAndMultipleValues() {
        assertEquals(SpeedValue.Number(30, mph = true), resolve("maxspeed" to "30 mph").value)
        assertEquals(SpeedValue.Number(30), resolve("maxspeed" to "50;30").value)
    }

    @Test fun estimatesFromHighwayType() {
        val r = resolve("highway" to "residential")
        assertEquals(SpeedValue.Number(50), r.value)
        assertTrue(r.estimated)
        assertEquals(SpeedValue.Unknown, resolve("highway" to "primary").value)
        assertFalse(resolve("highway" to "primary", "maxspeed" to "80").estimated)
    }

    @Test fun conditionalTimeWindow() {
        val tags = arrayOf("maxspeed" to "50", "maxspeed:conditional" to "30 @ (Mo-Fr 07:00-17:00)")
        val active = resolve(*tags, now = wedMorning)
        assertEquals(SpeedValue.Number(30), active.value)
        assertTrue(active.conditionalActive)
        assertEquals(SpeedValue.Number(50), resolve(*tags, now = wedNight).value)
        assertEquals(SpeedValue.Number(50), resolve(*tags, now = sunMorning).value)
    }

    @Test fun conditionalOverMidnightAndMultipleRules() {
        val tags = arrayOf("maxspeed" to "100", "maxspeed:conditional" to "none @ (Sa,Su); 60 @ (22:00-06:00)")
        assertEquals(SpeedValue.Number(60), resolve(*tags, now = wedNight).value)
        assertEquals(SpeedValue.Number(100), resolve(*tags, now = wedMorning).value)
        assertEquals(SpeedValue.None, resolve(*tags, now = sunMorning).value)
    }

    @Test fun unknownConditionBecomesNote() {
        val r = resolve("maxspeed" to "120", "maxspeed:conditional" to "80 @ wet")
        assertEquals(SpeedValue.Number(120), r.value)
        assertEquals("bei Nässe 80", r.note)
    }

    @Test fun directionalConditional() {
        val tags = arrayOf("maxspeed" to "50", "maxspeed:backward:conditional" to "30 @ (07:00-09:00)")
        assertEquals(SpeedValue.Number(50), resolve(*tags, forward = true).value)
        assertEquals(SpeedValue.Number(30), resolve(*tags, forward = false).value)
    }
}
