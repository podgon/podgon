package app.podgon.mobile.core.network

import app.podgon.contract.ApiProblem
import app.podgon.contract.ErrorCode
import app.podgon.contract.TokenPair
import app.podgon.mobile.core.auth.SessionManager
import app.podgon.mobile.core.error.AppError
import app.podgon.mobile.core.error.AppException
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.DefaultRequest
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.auth.providers.BearerTokens
import io.ktor.client.plugins.auth.providers.bearer
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json

/** JSON-настройки клиента: сервер может добавлять поля, клиент их игнорирует. */
val ApiJson: Json =
    Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

private const val AUTH_PATH = "/api/v1/auth/"
const val CLIENT_PLATFORM_HEADER = "X-Client-Platform"
const val CLIENT_VERSION_HEADER = "X-Client-Version"
private const val REQUEST_TIMEOUT_MS = 20_000L

/** Клиент без авторизации: для входа, регистрации и обновления токена. */
fun createPublicClient(
    engine: HttpClientEngine,
    config: ApiConfig,
    endpoints: ApiEndpoints = ApiEndpoints(config),
    gate: UpdateGate = UpdateGate(),
): HttpClient =
    HttpClient(engine) {
        install(ContentNegotiation) { json(ApiJson) }
        install(HttpTimeout) { requestTimeoutMillis = REQUEST_TIMEOUT_MS }
        defaultRequest { apiDefaults(config) }
        expectSuccess = false
    }.apply { installApiGuards(endpoints, gate) }

/**
 * Клиент для запросов с авторизацией: подставляет access-токен и на 401 один раз обновляет пару
 * через refresh (параллельные 401 Ktor обрабатывает последовательно). Если refresh отклонён — выход.
 */
fun createAuthorizedClient(
    engine: HttpClientEngine,
    config: ApiConfig,
    session: SessionManager,
    publicClient: HttpClient,
    endpoints: ApiEndpoints = ApiEndpoints(config),
    gate: UpdateGate = UpdateGate(),
): HttpClient =
    HttpClient(engine) {
        install(ContentNegotiation) { json(ApiJson) }
        install(HttpTimeout) { requestTimeoutMillis = REQUEST_TIMEOUT_MS }
        install(Auth) {
            bearer {
                loadTokens { session.tokens()?.let { BearerTokens(it.accessToken, it.refreshToken) } }
                refreshTokens {
                    val refresh = oldTokens?.refreshToken ?: return@refreshTokens null
                    val fresh = refreshPair(publicClient, refresh)
                    if (fresh == null) {
                        session.signOut()
                        null
                    } else {
                        session.update(fresh).let { BearerTokens(it.accessToken, it.refreshToken) }
                    }
                }
                // не отправлять токен на публичные эндпоинты входа
                sendWithoutRequest {
                    !it.url
                        .build()
                        .encodedPath
                        .contains(AUTH_PATH)
                }
            }
        }
        defaultRequest { apiDefaults(config) }
        expectSuccess = false
    }.apply { installApiGuards(endpoints, gate) }

/** Общие для всех запросов адрес, тип тела и версия клиента (по ней сервер требует обновления — R1). */
private fun DefaultRequest.DefaultRequestBuilder.apiDefaults(config: ApiConfig) {
    url(config.baseUrl.trimEnd('/') + "/")
    contentType(ContentType.Application.Json)
    if (config.platform.isNotBlank()) header(CLIENT_PLATFORM_HEADER, config.platform)
    if (config.appVersion > 0) header(CLIENT_VERSION_HEADER, config.appVersion.toString())
}

/** Обмен refresh-токена; `null`, если сервер его отклонил (токен недействителен). Сетевые сбои пробрасываются. */
private suspend fun refreshPair(
    client: HttpClient,
    refreshToken: String,
): TokenPair? {
    val response =
        client.post("api/v1/auth/refresh") {
            setBody(app.podgon.contract.RefreshRequest(refreshToken))
        }
    return if (response.status.isSuccess()) response.body<TokenPair>() else null
}

/** Разбирает ответ: тело при успехе либо [AppException] с [AppError.Api]. */
internal suspend inline fun <reified T> HttpResponse.bodyOrThrow(): T {
    if (!status.isSuccess()) throw toAppException()
    return body()
}

internal suspend fun HttpResponse.toAppException(): AppException {
    val problem =
        try {
            body<ApiProblem>()
        } catch (e: CancellationException) {
            throw e
        } catch (
            @Suppress("TooGenericExceptionCaught") e: Exception,
        ) {
            null
        }
    return AppException(
        AppError.Api(
            status = status.value,
            code = ErrorCode.fromCode(problem?.code),
            fieldErrors = problem?.errors.orEmpty(),
            retryAfterSeconds = headers[HttpHeaders.RetryAfter]?.toLongOrNull(),
        ),
    )
}

/** Выполняет сетевой вызов, превращая сбои связи в [AppError.Network] (отмена корутины не глушится). */
internal suspend inline fun <T> apiCall(block: () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: AppException) {
        Result.failure(e)
    } catch (
        @Suppress("TooGenericExceptionCaught") e: Exception,
    ) {
        Result.failure(AppException(AppError.Network))
    }
