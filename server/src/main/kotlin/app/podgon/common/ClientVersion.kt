package app.podgon.common

import app.podgon.contract.ErrorCode
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import tools.jackson.databind.json.JsonMapper

/**
 * Минимальные поддерживаемые версии приложения (номер сборки: `versionCode` на Android, build number на iOS).
 * 0 — ограничения нет. Поднимается при несовместимом изменении API, чтобы старые сборки
 * показали экран обновления, а не ломались непонятными ошибками (замечание R1 ревью).
 */
@ConfigurationProperties("podgon.client")
data class ClientProperties(
    val minVersion: MinVersion = MinVersion(),
) {
    data class MinVersion(
        val android: Int = 0,
        val ios: Int = 0,
    )
}

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ClientProperties::class)
class ClientConfig

/**
 * Проверка версии клиента по заголовкам `X-Client-Platform` (`android`, `ios`) и `X-Client-Version`.
 * Запросы без заголовков (страницы, служебные вызовы) пропускаются: мобильные сборки отправляют их всегда.
 * Устаревшая сборка получает 426 `UPGRADE_REQUIRED` на любой запрос API, включая вход.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
class ClientVersionFilter(
    private val props: ClientProperties,
    private val json: JsonMapper,
) : OncePerRequestFilter() {
    override fun shouldNotFilter(request: HttpServletRequest): Boolean = !request.requestURI.startsWith("/api/")

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        chain: FilterChain,
    ) {
        val version = request.getHeader(VERSION_HEADER)?.trim()?.toIntOrNull()
        val min =
            when (request.getHeader(PLATFORM_HEADER)?.trim()?.lowercase()) {
                "android" -> props.minVersion.android
                "ios" -> props.minVersion.ios
                else -> 0
            }
        if (version != null && version < min) {
            response.writeProblem(json, HttpStatus.UPGRADE_REQUIRED, ErrorCode.UPGRADE_REQUIRED)
            return
        }
        chain.doFilter(request, response)
    }

    companion object {
        const val PLATFORM_HEADER = "X-Client-Platform"
        const val VERSION_HEADER = "X-Client-Version"
    }
}
