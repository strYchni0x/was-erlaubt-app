package de.waserlaubt.app.osm

import java.time.LocalDateTime

/**
 * Ermittelt aus den OSM-Tags einer Straße das Tempolimit für eine Fahrtrichtung.
 *
 * Reihenfolge: zeitabhängiges Limit → richtungsbezogenes/allgemeines `maxspeed`
 * → implizite Werte (`maxspeed:type`, `zone:maxspeed`, …) → Schätzung aus dem Straßentyp.
 */
object MaxspeedResolver {

    /**
     * @param builtUp ob die Straße vermutlich innerorts liegt; null = unbekannt, dann wird für
     *   Landstraßen ohne Tag nicht geschätzt.
     */
    fun resolve(
        tags: Map<String, String>,
        forward: Boolean,
        now: LocalDateTime,
        builtUp: Boolean? = null,
    ): SpeedLimit {
        val dir = if (forward) "forward" else "backward"
        var note: String? = null

        // 1. Zeitabhängige Limits
        for (key in listOf("maxspeed:$dir:conditional", "maxspeed:conditional")) {
            val raw = tags[key] ?: continue
            for (rule in ConditionalSpeed.parseRules(raw)) {
                val value = parseValue(rule.rawValue) ?: continue
                when (ConditionalSpeed.evaluate(rule.condition, now)) {
                    true -> return SpeedLimit(value, conditionalActive = true)
                    null -> if (note == null) note = ConditionalSpeed.describe(rule.rawValue, rule.condition)
                    false -> Unit
                }
            }
        }

        // 2. Explizit erfasstes Limit
        for (key in listOf("maxspeed:$dir", "maxspeed")) {
            val value = tags[key]?.let(::parseValue) ?: continue
            return SpeedLimit(value, note = note)
        }

        // 3. Implizite Limits über Typ-/Zonen-Tags
        for (key in listOf(
            "maxspeed:type:$dir", "maxspeed:type", "source:maxspeed:$dir", "source:maxspeed",
            "zone:maxspeed", "zone:traffic",
        )) {
            val value = tags[key]?.let(::implicitValue) ?: continue
            return SpeedLimit(value, note = note)
        }

        // 4. Schätzung aus dem Straßentyp (Annahme: deutsche Regeln)
        val estimate = estimate(tags, builtUp)
        return if (estimate != null) SpeedLimit(estimate, estimated = true, note = note)
        else SpeedLimit(SpeedValue.Unknown, note = note)
    }

    private val openRoads = setOf(
        "trunk", "trunk_link", "primary", "primary_link", "secondary", "secondary_link",
        "tertiary", "tertiary_link", "unclassified", "road",
    )

    /**
     * Allgemeine Tempolimits nach StVO § 3: innerorts 50, außerorts 100, ohne Limit auf
     * Autobahnen und außerorts auf Straßen mit getrennten Fahrbahnen (Kraftfahrstraßen).
     */
    private fun estimate(tags: Map<String, String>, builtUp: Boolean?): SpeedValue? {
        val highway = tags["highway"]
        val separated = tags["oneway"] == "yes" || (tags["lanes"]?.toIntOrNull() ?: 0) >= 4
        return when {
            highway == "motorway" -> SpeedValue.None
            highway == "living_street" -> SpeedValue.Walk
            highway == "residential" -> SpeedValue.Number(50)
            highway !in openRoads || builtUp == null -> null
            builtUp -> SpeedValue.Number(50)
            separated && (tags["motorroad"] == "yes" || highway == "trunk") -> SpeedValue.None
            else -> SpeedValue.Number(100)
        }
    }

    private val numberRegex = Regex("^(\\d{1,3})\\s*(mph|km/h|kmh|kph)?$")

    /** Wert eines `maxspeed`-Tags; bei mehreren Werten (`50;30`) gilt der kleinste. */
    fun parseValue(raw: String): SpeedValue? {
        val values = raw.split(';').mapNotNull { parseSingle(it.trim()) }
        if (values.isEmpty()) return null
        val numbers = values.filterIsInstance<SpeedValue.Number>()
        return if (numbers.size == values.size) numbers.minBy { it.value } else values.first()
    }

    private fun parseSingle(raw: String): SpeedValue? {
        when (raw.lowercase()) {
            "none", "unlimited" -> return SpeedValue.None
            "walk" -> return SpeedValue.Walk
            "signals", "variable" -> return SpeedValue.Variable
        }
        numberRegex.matchEntire(raw.lowercase())?.let { m ->
            return SpeedValue.Number(m.groupValues[1].toInt(), mph = m.groupValues[2] == "mph")
        }
        return implicitValue(raw)
    }

    private val zoneRegex = Regex("^([A-Z]{2}):(?:zone:?)?(\\d{1,3})$")
    private val namedRegex = Regex("^([A-Z]{2}):([a-z_]+)$")

    private val implicitTable: Map<String, Map<String, SpeedValue>> = mapOf(
        "DE" to mapOf(
            "urban" to SpeedValue.Number(50), "rural" to SpeedValue.Number(100),
            "motorway" to SpeedValue.None, "living_street" to SpeedValue.Walk,
            "bicycle_road" to SpeedValue.Number(30),
        ),
        "AT" to mapOf(
            "urban" to SpeedValue.Number(50), "rural" to SpeedValue.Number(100),
            "motorway" to SpeedValue.Number(130), "trunk" to SpeedValue.Number(100),
            "living_street" to SpeedValue.Walk, "bicycle_road" to SpeedValue.Number(30),
        ),
        "CH" to mapOf(
            "urban" to SpeedValue.Number(50), "rural" to SpeedValue.Number(80),
            "motorway" to SpeedValue.Number(120), "trunk" to SpeedValue.Number(100),
            "living_street" to SpeedValue.Number(20),
        ),
        "NL" to mapOf(
            "urban" to SpeedValue.Number(50), "rural" to SpeedValue.Number(80),
            "motorway" to SpeedValue.Number(100), "living_street" to SpeedValue.Walk,
        ),
        "FR" to mapOf(
            "urban" to SpeedValue.Number(50), "rural" to SpeedValue.Number(80),
            "motorway" to SpeedValue.Number(130), "trunk" to SpeedValue.Number(110),
        ),
    )

    /** Implizite Werte wie `DE:urban` oder `DE:zone30`; `sign` und Unbekanntes → null. */
    fun implicitValue(raw: String): SpeedValue? {
        zoneRegex.matchEntire(raw)?.let { return SpeedValue.Number(it.groupValues[2].toInt()) }
        val m = namedRegex.matchEntire(raw) ?: return null
        return implicitTable[m.groupValues[1]]?.get(m.groupValues[2])
    }
}
