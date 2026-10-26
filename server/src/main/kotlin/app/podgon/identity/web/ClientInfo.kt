package app.podgon.identity.web

import jakarta.servlet.http.HttpServletRequest
import java.util.Locale

/** Данные клиента из запроса. IP берётся из `remoteAddr`; за прокси включается `server.forward-headers-strategy`. */
internal fun HttpServletRequest.clientIp(): String? = remoteAddr?.take(45)

/** Язык интерфейса по `Accept-Language`: `ru` или `en` (по умолчанию `ru`). */
internal fun HttpServletRequest.clientLocale(): String {
    val header = getHeader("Accept-Language") ?: return "ru"
    val ranges = runCatching { Locale.LanguageRange.parse(header) }.getOrDefault(emptyList())
    val first =
        ranges
            .firstOrNull()
            ?.range
            ?.substringBefore('-')
            ?.lowercase()
    return if (first == "en") "en" else "ru"
}
