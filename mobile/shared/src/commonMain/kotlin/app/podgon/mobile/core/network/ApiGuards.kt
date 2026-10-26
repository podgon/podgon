package app.podgon.mobile.core.network

import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpSend
import io.ktor.client.plugins.plugin
import io.ktor.client.plugins.timeout
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.url
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.URLBuilder
import io.ktor.http.Url
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.concurrent.Volatile

/**
 * Адреса API: основной и запасные (замечание R2 ревью). Все запросы идут на текущий адрес; если он
 * недоступен, клиент проверяет запасные и переключается на первый отвечающий до перезапуска приложения.
 */
class ApiEndpoints(
    config: ApiConfig,
) {
    val all: List<Url> =
        (listOf(config.baseUrl) + config.fallbackBaseUrls)
            .map { Url(it.trimEnd('/')) }
            .distinctBy { "${it.protocol.name}://${it.host}:${it.port}" }

    @Volatile
    private var currentIndex = 0

    val current: Url get() = all[currentIndex]

    fun switchTo(url: Url) {
        all.indexOf(url).takeIf { it >= 0 }?.let { currentIndex = it }
    }
}

/** Сервер сообщил, что версия приложения устарела (426 `UPGRADE_REQUIRED`); приложение показывает экран обновления. */
class UpdateGate {
    private val _required = MutableStateFlow(false)
    val required: StateFlow<Boolean> = _required.asStateFlow()

    fun require() {
        _required.value = true
    }
}

private const val PROBE_PATH = "actuator/health"
private const val PROBE_TIMEOUT_MS = 5_000L

/**
 * Подменяет адрес запроса на текущий из [endpoints], отмечает ответ 426 в [gate] и при сетевом сбое
 * ищет живой запасной адрес. Повторяется автоматически только GET (идемпотентный); изменяющий запрос
 * возвращает ошибку сети, а повтор пользователя уйдёт уже на новый адрес — так нет риска выполнить действие дважды.
 */
internal fun HttpClient.installApiGuards(
    endpoints: ApiEndpoints,
    gate: UpdateGate,
) {
    plugin(HttpSend).intercept { request ->
        val base = endpoints.current
        request.url.rebase(base)
        val call =
            try {
                execute(request)
            } catch (e: CancellationException) {
                throw e
            } catch (
                @Suppress("TooGenericExceptionCaught") e: Exception,
            ) {
                val alive =
                    endpoints.all.firstOrNull { candidate ->
                        candidate != base &&
                            runCatching {
                                val probe =
                                    HttpRequestBuilder().apply {
                                        method = HttpMethod.Get
                                        url(candidate.toString().trimEnd('/') + "/" + PROBE_PATH)
                                        timeout { requestTimeoutMillis = PROBE_TIMEOUT_MS }
                                    }
                                execute(probe).response.status == HttpStatusCode.OK
                            }.getOrDefault(false)
                    } ?: throw e
                endpoints.switchTo(alive)
                if (request.method != HttpMethod.Get) throw e
                request.url.rebase(alive)
                execute(request)
            }
        if (call.response.status == HttpStatusCode.UpgradeRequired) gate.require()
        call
    }
}

private fun URLBuilder.rebase(base: Url) {
    protocol = base.protocol
    host = base.host
    port = base.specifiedPort
}
