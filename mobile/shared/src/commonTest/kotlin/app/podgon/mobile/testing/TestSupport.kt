package app.podgon.mobile.testing

import app.podgon.mobile.core.auth.SessionManager
import app.podgon.mobile.core.auth.StoredTokens
import app.podgon.mobile.core.auth.TokenStorage
import app.podgon.mobile.core.network.ApiConfig
import app.podgon.mobile.core.network.AuthApi
import app.podgon.mobile.core.network.GroupsApi
import app.podgon.mobile.core.network.MeApi
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

/** API групп и профиля поверх [MockEngine]; запросы записываются в [requests]. */
class GroupsFixture(
    handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
) {
    val requests = mutableListOf<HttpRequestData>()
    private val engine =
        MockEngine { request ->
            requests += request
            handler(request)
        }
    private val client = createPublicClient(engine, TestConfig)
    val api = GroupsApi(client)
    val me = MeApi(client)

    /**
     * Строки вида `GET /api/v1/groups` в порядке поступления. MockEngine отвечает на своём потоке,
     * поэтому при гонке с записью снимок повторяется.
     */
    fun calls(): List<String> {
        repeat(SNAPSHOT_RETRIES) {
            try {
                return requests.toList().map { "${it.method.value} ${it.url.encodedPath}" }
            } catch (
                @Suppress("SwallowedException") e: ConcurrentModificationException,
            ) {
                // повторяем снимок
            }
        }
        error("не удалось снять копию списка запросов")
    }

    private companion object {
        const val SNAPSHOT_RETRIES = 100
    }
}

const val ADMIN_ROLE =
    """{"id":"r-admin","code":"ADMIN","title":"Администратор","description":"","admin":true,"system":true}"""
const val MEMBER_ROLE =
    """{"id":"r-member","code":"MEMBER","title":"Участник","description":"","admin":false,"system":true}"""

fun groupJson(
    id: String = "g1",
    name: String = "Книжный клуб",
    role: String = ADMIN_ROLE,
    members: Int = 2,
) = """{"id":"$id","name":"$name","description":"Читаем вместе","memberCount":$members,"myRole":$role}"""

fun memberJson(
    userId: String,
    username: String,
    role: String = MEMBER_ROLE,
) =
    """{"userId":"$userId","username":"$username","displayName":"Имя $username","role":$role,"joinedAt":"2026-06-01T10:00:00Z"}"""

const val ME_JSON =
    """{"id":"u-me","email":"me@example.com","username":"me","displayName":"Я","bio":"","locale":"ru"}"""
