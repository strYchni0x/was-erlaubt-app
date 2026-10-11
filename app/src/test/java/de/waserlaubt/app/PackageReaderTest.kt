package de.waserlaubt.app

import de.waserlaubt.app.offline.PackageReader
import de.waserlaubt.app.osm.MaxspeedResolver
import de.waserlaubt.app.osm.SpeedValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDateTime
import kotlin.math.floor

/** Liest das echte Bremen-Paket (gebaut mit tools/build_package.py). */
class PackageReaderTest {
    private val reader = PackageReader(File(javaClass.getResource("/bremen.wepk")!!.toURI()))

    // Bremen Hauptbahnhof
    private val lat = 53.0831
    private val lon = 8.8133
    private val y = floor(lat / 0.02).toInt()
    private val x = floor(lon / 0.03).toInt()

    @Test fun header() {
        assertEquals(20_000, reader.tileLatE6)
        assertEquals(30_000, reader.tileLonE6)
        assertTrue(reader.dataDate.matches(Regex("""\d{4}-\d{2}-\d{2}""")))
    }

    @Test fun cityCentreTile() {
        val tile = reader.tile(y, x)!!
        assertTrue(tile.complete)
        assertTrue("zu wenige Straßen: ${tile.ways.size}", tile.ways.size > 200)
        assertEquals(tile.ways.size, tile.ways.map { it.id }.toSet().size)
        for (w in tile.ways) {
            assertTrue(w.points.size >= 2)
            assertTrue(w.highway != null)
        }
        // Jeder Weg berührt die Kachel ungefähr (Toleranz: eine halbe Kachel)
        for (w in tile.ways) {
            assertTrue(w.points.any { p ->
                p.lat in (y - 0.5) * 0.02..(y + 1.5) * 0.02 && p.lon in (x - 0.5) * 0.03..(x + 1.5) * 0.03
            })
        }
        // Tempolimits sind lesbar
        val limits = tile.ways.map { MaxspeedResolver.resolve(it.tags, true, LocalDateTime.now()).value }
        assertTrue(limits.count { it is SpeedValue.Number } > 100)
    }

    @Test fun missingTile() {
        assertNull(reader.tile(0, 0))
        assertNull(reader.tile(y + 100, x))
    }
}
