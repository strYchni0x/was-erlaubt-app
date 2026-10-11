package de.waserlaubt.app.offline

import de.waserlaubt.app.osm.LatLon
import de.waserlaubt.app.osm.OsmWay
import java.io.Closeable
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.channels.FileChannel

/**
 * Liest ein Offline-Paket (.wepk, erzeugt von tools/build_package.py).
 *
 * Aufbau: "WEPK", Version, Datenstand, Kachelgröße, Texttabelle, Kachelindex (je 16 Byte:
 * y, x, Listen-Offset, Anzahl·2+vollständig), Wegelisten je Kachel (Offsets delta-kodiert),
 * Wege (id, Tags als Text-IDs, Punkte in Mikrograd delta-kodiert). Zahlen sind Varints.
 */
class PackageReader(file: File) : Closeable {

    /** Wege einer Kachel; [complete] = Kachel liegt ganz im Gebiet des Pakets. */
    class Tile(val ways: List<OsmWay>, val complete: Boolean)

    private val raf = RandomAccessFile(file, "r")
    private val buf: ByteBuffer = raf.channel.map(FileChannel.MapMode.READ_ONLY, 0, raf.length())

    val dataDate: String
    val tileLatE6: Int
    val tileLonE6: Int
    private val strings: Array<String>
    private val tileCount: Int
    private val indexStart: Int
    private val listsStart: Int
    private val waysStart: Int

    init {
        try {
            val c = Cursor(0)
            val magic = ByteArray(4) { c.byte().toByte() }
            if (String(magic, Charsets.US_ASCII) != MAGIC) throw IOException("Kein Offline-Paket")
            val version = c.byte()
            if (version != VERSION) throw IOException("Paketversion $version wird nicht unterstützt")
            dataDate = c.string()
            tileLatE6 = c.varint().toInt()
            tileLonE6 = c.varint().toInt()
            strings = Array(c.varint().toInt()) { c.string() }
            tileCount = c.int()
            val listsLength = c.int()
            indexStart = c.pos
            listsStart = indexStart + tileCount * INDEX_ENTRY
            waysStart = listsStart + listsLength
            if (waysStart > buf.limit()) throw IOException("Paket unvollständig")
        } catch (e: Exception) {
            raf.close()
            throw if (e is IOException) e else IOException("Paket beschädigt", e)
        }
    }

    fun tile(y: Int, x: Int): Tile? {
        val i = findTile(y, x)
        if (i < 0) return null
        val entry = indexStart + i * INDEX_ENTRY
        val listOffset = buf.getInt(entry + 8)
        val countAndFlag = buf.getInt(entry + 12)
        val count = countAndFlag ushr 1
        val c = Cursor(listsStart + listOffset)
        var offset = 0
        val ways = ArrayList<OsmWay>(count)
        repeat(count) {
            offset += c.varint().toInt()
            ways += readWay(waysStart + offset)
        }
        return Tile(ways, countAndFlag and 1 == 1)
    }

    /** Binäre Suche im nach (y, x) sortierten Index. */
    private fun findTile(y: Int, x: Int): Int {
        var lo = 0
        var hi = tileCount - 1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            val e = indexStart + mid * INDEX_ENTRY
            val cmp = compareValues(buf.getInt(e), y).takeIf { it != 0 } ?: compareValues(buf.getInt(e + 4), x)
            when {
                cmp < 0 -> lo = mid + 1
                cmp > 0 -> hi = mid - 1
                else -> return mid
            }
        }
        return -1
    }

    private fun readWay(pos: Int): OsmWay {
        val c = Cursor(pos)
        val id = c.varint()
        val tags = HashMap<String, String>()
        repeat(c.varint().toInt()) {
            val k = strings[c.varint().toInt()]
            tags[k] = strings[c.varint().toInt()]
        }
        val n = c.varint().toInt()
        val points = ArrayList<LatLon>(n)
        var lat = 0L
        var lon = 0L
        repeat(n) {
            lat += c.zigzag()
            lon += c.zigzag()
            points += LatLon(lat / 1e6, lon / 1e6)
        }
        return OsmWay(id, tags, points)
    }

    override fun close() = raf.close()

    /** Lesezeiger mit absoluten Zugriffen – dadurch kann [tile] aus mehreren Threads laufen. */
    private inner class Cursor(var pos: Int) {
        fun byte(): Int = buf.get(pos++).toInt() and 0xFF

        fun int(): Int = buf.getInt(pos).also { pos += 4 }

        fun varint(): Long {
            var result = 0L
            var shift = 0
            while (true) {
                val b = byte()
                result = result or ((b and 0x7F).toLong() shl shift)
                if (b and 0x80 == 0) return result
                shift += 7
            }
        }

        fun zigzag(): Long = varint().let { (it ushr 1) xor -(it and 1) }

        fun string(): String {
            val len = varint().toInt()
            val bytes = ByteArray(len)
            for (i in 0 until len) bytes[i] = buf.get(pos + i)
            pos += len
            return String(bytes, Charsets.UTF_8)
        }
    }

    companion object {
        private const val MAGIC = "WEPK"
        private const val VERSION = 1
        private const val INDEX_ENTRY = 16
    }
}
