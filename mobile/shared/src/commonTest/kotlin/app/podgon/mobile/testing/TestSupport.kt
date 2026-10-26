package app.podgon.mobile.testing

import app.podgon.mobile.core.auth.SessionManager
import app.podgon.mobile.core.auth.StoredTokens
import app.podgon.mobile.core.auth.TokenStorage
import app.podgon.mobile.core.network.ApiConfig
import app.podgon.mobile.core.network.AuthApi
import app.podgon.mobile.core.network.createPublicClient
import app.podgon.mobile.feature.auth.data.AuthRepository
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode

val TestConfig = ApiConfig("https://api.test")

/** Хранилище токенов в памяти. */
class FakeTokenStorage(
    var tokens: StoredTokens? = null,
) : TokenStorage {
    override suspend fun read(): StoredTokens? = tokens

    override suspend fun write(tokens: StoredTokens) {
        this.tokens = tokens
    }

    override suspend fun clear() {
        tokens = null
    }
}

fun MockRequestHandleScope.json(
    body: String,
    status: HttpStatusCode = HttpStatusCode.OK,
    extra: Map<String, String> = emptyMap(),
): HttpResponseData {
    val headers =
        mutableMapOf(HttpHeaders.ContentType to listOf("application/json"))
            .also { map -> extra.forEach { (k, v) -> map[k] = listOf(v) } }
    return respond(
        body,
        status,
        io.ktor.http.Headers
            .build { headers.forEach { (k, v) -> appendAll(k, v) } },
    )
}

fun problem(
    status: Int,
    code: String,
    errors: String = "[]",
) = """{"status":$status,"code":"$code","errors":$errors}"""

const val TOKENS_JSON = """{"accessToken":"access-1","refreshToken":"refresh-1","expiresIn":900}"""

/** Репозиторий входа поверх [MockEngine]; запросы записываются в [requests]. */
class AuthFixture(
    val storage: FakeTokenStorage = FakeTokenStorage(),
    handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
) {
    val requests = mutableListOf<HttpRequestData>()
    val session = SessionManager(storage)
    private val engine =
        MockEngine { request ->
            requests += request
            handler(request)
        }
    val repository =
        AuthRepository(
            api = AuthApi(createPublicClient(engine, TestConfig)),
            session = session,
            captcha = { null },
            device = { "Test device" },
        )
}
