package app.podgon.mobile.feature.home

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Дата в строке рекомендации: сегодня — время «14:05», в этом году — «27 окт.», раньше — «27.10.2025»
 * (по часовому поясу устройства). [months] — сокращённые названия месяцев на языке интерфейса.
 */
fun shortDate(
    iso: String,
    months: List<String>,
    now: Instant = Clock.System.now(),
    zone: TimeZone = TimeZone.currentSystemDefault(),
): String {
    val at = runCatching { Instant.parse(iso) }.getOrNull() ?: return ""
    val date = at.toLocalDateTime(zone)
    val today = now.toLocalDateTime(zone)
    return when {
        date.date == today.date -> "${two(date.hour)}:${two(date.minute)}"
        date.year == today.year -> "${date.day} ${months[date.month.ordinal]}"
        else -> "${two(date.day)}.${two(date.month.ordinal + 1)}.${date.year}"
    }
}

/** Полная дата для карточки: «27 окт. 2025, 14:05». */
fun fullDate(
    iso: String,
    months: List<String>,
    zone: TimeZone = TimeZone.currentSystemDefault(),
): String {
    val at = runCatching { Instant.parse(iso) }.getOrNull() ?: return ""
    val d: LocalDateTime = at.toLocalDateTime(zone)
    return "${d.day} ${months[d.month.ordinal]} ${d.year}, ${two(d.hour)}:${two(d.minute)}"
}

private fun two(value: Int) = value.toString().padStart(2, '0')
