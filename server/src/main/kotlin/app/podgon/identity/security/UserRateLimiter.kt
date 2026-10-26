package app.podgon.identity.security

import app.podgon.common.writeProblem
import app.podgon.contract.ErrorCode
import app.podgon.identity.AuthenticatedUser
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.http.HttpStatus
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import tools.jackson.databind.json.JsonMapper
import java.time.Clock
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Лимиты запросов авторизованного пользователя в минуту (замечание R5 ревью): общий и отдельный — на поиск
 * (запросы с текстом `q`, самые дорогие для БД). Первая линия защиты — `limit_req` на обратном прокси.
 */
@ConfigurationProperties("podgon.request-limits")
data class RequestLimitProperties(
    val perUserPerMinute: Int = 300,
    val searchPerUserPerMinute: Int = 60,
)

/**
 * Счётчики фиксированного окна в памяти процесса: для одного инстанса этого достаточно,
 * при нескольких инстансах счётчики переезжают в Redis (docs/server.md).
 */
@Component
class UserRateLimiter(
    private val props: RequestLimitProperties,
    private val clock: Clock,
) {
    private class Window(
        val start: Long,
        var count: Int,
    )

    private val windows = ConcurrentHashMap<String, Window>()

    /** Учитывает запрос; возвращает `null`, если он в лимите, иначе — через сколько секунд повторить. */
    fun acquire(
        userId: UUID,
        search: Boolean,
    ): Long? {
        val minute = clock.millis() / WINDOW_MS
        val general = hit("$userId", minute, props.perUserPerMinute)
        val searchHit = if (search) hit("$userId:q", minute, props.searchPerUserPerMinute) else true
        if (general && searchHit) return null
        return (WINDOW_MS - clock.millis() % WINDOW_MS + 999) / 1000
    }

    private fun hit(
        key: String,
        minute: Long,
        limit: Int,
    ): Boolean {
        val window =
            windows.compute(key) { _, current ->
                if (current == null || current.start != minute) Window(minute, 1) else current.apply { count++ }
            }!!
        return window.count <= limit
    }

    /** Убирает окна прошлых минут, чтобы память не росла с числом пользователей. */
    @Scheduled(fixedDelay = WINDOW_MS)
    fun evictOld() {
        val minute = clock.millis() / WINDOW_MS
        windows.entries.removeIf { it.value.start < minute }
    }

    private companion object {
        const val WINDOW_MS = 60_000L
    }
}

/** Фильтр в цепочке безопасности после проверки токена: превышение лимита → 429 `RATE_LIMITED` + `Retry-After`. */
class UserRateLimitFilter(
    private val limiter: UserRateLimiter,
    private val json: JsonMapper,
) : OncePerRequestFilter() {
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        chain: FilterChain,
    ) {
        val user = SecurityContextHolder.getContext().authentication?.principal as? AuthenticatedUser
        if (user != null) {
            val search = request.method == "GET" && !request.getParameter("q").isNullOrBlank()
            val retryAfter = limiter.acquire(user.userId, search)
            if (retryAfter != null) {
                response.writeProblem(json, HttpStatus.TOO_MANY_REQUESTS, ErrorCode.RATE_LIMITED, retryAfter)
                return
            }
        }
        chain.doFilter(request, response)
    }
}
