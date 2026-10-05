package de.waserlaubt.app.osm

/** Was auf dem Display angezeigt wird. */
sealed interface SpeedValue {
    /** Konkretes Limit, z. B. 50 km/h oder 30 mph. */
    data class Number(val value: Int, val mph: Boolean = false) : SpeedValue
    /** Keine Begrenzung (z. B. deutsche Autobahn). */
    data object None : SpeedValue
    /** Schrittgeschwindigkeit (verkehrsberuhigter Bereich). */
    data object Walk : SpeedValue
    /** Wechselverkehrszeichen – Limit nur vor Ort ablesbar. */
    data object Variable : SpeedValue
    /** Keine Daten vorhanden. */
    data object Unknown : SpeedValue
}

data class SpeedLimit(
    val value: SpeedValue,
    /** true = nicht in OSM erfasst, sondern aus dem Straßentyp geschätzt. */
    val estimated: Boolean = false,
    /** true = ein zeitabhängiges Limit (maxspeed:conditional) ist gerade aktiv. */
    val conditionalActive: Boolean = false,
    /** Zusatzhinweis, z. B. "bei Nässe 80". */
    val note: String? = null,
)
