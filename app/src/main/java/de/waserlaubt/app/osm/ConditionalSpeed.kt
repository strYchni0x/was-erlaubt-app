package de.waserlaubt.app.osm

import java.time.DayOfWeek
import java.time.LocalDateTime

/**
 * Auswertung von `maxspeed:conditional`, z. B. `30 @ (Mo-Fr 07:00-17:00)` oder `80 @ wet`.
 *
 * Unterstützt wird die gängige Teilmenge der opening_hours-Syntax: Wochentage
 * (Bereiche und Listen) und Uhrzeitspannen, auch über Mitternacht. Alles andere
 * (Nässe, Schnee, Feiertage …) gilt als nicht auswertbar und wird nur als Hinweis geliefert.
 */
object ConditionalSpeed {

    data class Rule(val rawValue: String, val condition: String)

    /** Zerlegt den Tag-Wert in einzelne Regeln; `;` innerhalb von Klammern trennt nicht. */
    fun parseRules(raw: String): List<Rule> =
        splitTopLevel(raw, ';').mapNotNull { part ->
            val at = part.indexOf('@')
            if (at < 0) return@mapNotNull null
            val value = part.substring(0, at).trim()
            val cond = part.substring(at + 1).trim().removeSurrounding("(", ")").trim()
            if (value.isEmpty() || cond.isEmpty()) null else Rule(value, cond)
        }

    /** true/false = Bedingung erfüllt/nicht erfüllt, null = nicht auswertbar. */
    fun evaluate(condition: String, now: LocalDateTime): Boolean? {
        val results = condition.split(';').map { it.trim() }.filter { it.isNotEmpty() }
            .map { evaluateRule(it, now) }
        if (results.isEmpty() || results.any { it == null }) return null
        return results.any { it == true }
    }

    /** Lesbarer Hinweis für nicht auswertbare Bedingungen, z. B. "bei Nässe 80". */
    fun describe(valueText: String, condition: String): String = when (condition.lowercase()) {
        "wet" -> "bei Nässe $valueText"
        "snow" -> "bei Schnee $valueText"
        "fog" -> "bei Nebel $valueText"
        else -> "$valueText bei $condition"
    }

    private val dayToken = "(Mo|Tu|We|Th|Fr|Sa|Su)"
    private val dayRegex = Regex("^$dayToken(-$dayToken)?(,$dayToken(-$dayToken)?)*$")
    private val timeSpan = "\\d{1,2}:\\d{2}-\\d{1,2}:\\d{2}"
    private val timeRegex = Regex("^$timeSpan(,$timeSpan)*$")

    private fun evaluateRule(rule: String, now: LocalDateTime): Boolean? {
        var days: String? = null
        var times: String? = null
        for (token in rule.split(Regex("\\s+"))) {
            when {
                days == null && dayRegex.matches(token) -> days = token
                times == null && timeRegex.matches(token) -> times = token
                else -> return null
            }
        }
        if (days == null && times == null) return null
        val dayOk = days?.let { matchesDays(it, now.dayOfWeek) } ?: true
        val timeOk = times?.let { matchesTimes(it, now.hour * 60 + now.minute) } ?: true
        return dayOk && timeOk
    }

    private val dayIndex = mapOf("Mo" to 1, "Tu" to 2, "We" to 3, "Th" to 4, "Fr" to 5, "Sa" to 6, "Su" to 7)

    private fun matchesDays(spec: String, day: DayOfWeek): Boolean = spec.split(',').any { part ->
        val range = part.split('-')
        val from = dayIndex.getValue(range[0])
        val to = dayIndex.getValue(range.getOrElse(1) { range[0] })
        val d = day.value
        if (from <= to) d in from..to else d >= from || d <= to
    }

    private fun matchesTimes(spec: String, minute: Int): Boolean = spec.split(',').any { span ->
        val (a, b) = span.split('-').map { t -> t.split(':').let { it[0].toInt() * 60 + it[1].toInt() } }
        if (a <= b) minute in a until b else minute >= a || minute < b
    }

    private fun splitTopLevel(s: String, sep: Char): List<String> {
        val out = mutableListOf<String>()
        var depth = 0
        val cur = StringBuilder()
        for (c in s) {
            when {
                c == '(' -> { depth++; cur.append(c) }
                c == ')' -> { depth--; cur.append(c) }
                c == sep && depth == 0 -> { out += cur.toString(); cur.clear() }
                else -> cur.append(c)
            }
        }
        out += cur.toString()
        return out.map { it.trim() }.filter { it.isNotEmpty() }
    }
}
