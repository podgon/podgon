package app.podgon.mobile

import app.podgon.mobile.core.auth.AuthState
import app.podgon.mobile.core.auth.SessionManager
import app.podgon.mobile.core.auth.StoredTokens
import app.podgon.mobile.core.network.MeApi
import app.podgon.mobile.core.network.createAuthorizedClient
import app.podgon.mobile.core.network.createPublicClient
import app.podgon.mobile.testing.FakeTokenStorage
import app.podgon.mobile.testing.TestConfig
import app.podgon.mobile.testing.json
import app.podgon.mobile.testing.problem
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.post
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TokenRefreshTest {
    private val profileJson =
        """{"id":"u1","email":"a@example.com","username":"alice","displayName":"Alice","bio":"","locale":"ru"}"""

    private class Setup(
        val storage: FakeTokenStorage,
        val session: SessionManager,
        val me: MeApi,
        val requests: MutableList<HttpRequestData>,
    )

    private suspend fun setup(
        handler:
            suspend io.ktor.client.engine.mock.MockRequestHandleScope.(
                HttpRequestData,
            ) -> io.ktor.client.request.HttpResponseData,
    ): Setup {
        val storage = FakeTokenStorage(StoredTokens("access-old", "refresh-old"))
        val session = SessionManager(storage).also { it.restore() }
        val requests = mutableListOf<HttpRequestData>()
        val engine =
            MockEngine { request ->
                requests += request
                handler(request)
            }
        val publicClient = createPublicClient(engine, TestConfig)
        val me = MeApi(createAuthorizedClient(engine, TestConfig, session, publicClient))
        return Setup(storage, session, me, requests)
    }

    private fun HttpRequestData.bearer() = headers[HttpHeaders.Authorization]

    private fun io.ktor.client.engine.mock.MockRequestHandleScope.unauthorized() =
        respond(
            problem(401, "UNAUTHORIZED"),
            HttpStatusCode.Unauthorized,
            headersOf(
                HttpHeaders.ContentType to listOf("application/problem+json"),
                HttpHeaders.WWWAuthenticate to listOf("Bearer"),
            ),
        )

    @Test
    fun `запрос идёт с access-токеном`() =
        runTest {
            val s = setup { json(profileJson) }
            assertEquals(
                "alice",
                s.me
                    .me()
                    .getOrThrow()
                    .username,
            )
            assertEquals("Bearer access-old", s.requests.single().bearer())
        }

    @Test
    fun `на 401 токены обновляются один раз и запрос повторяется с новой парой`() =
        runTest {
            val s =
                setup { request ->
                    when {
                        request.url.encodedPath.endsWith("/auth/refresh") ->
                            json("""{"accessToken":"access-new","refreshToken":"refresh-new","expiresIn":900}""")
                        request.bearer() == "Bearer access-new" -> json(profileJson)
                        else -> unauthorized()
                    }
                }
            assertEquals(
                "alice",
                s.me
                    .me()
                    .getOrThrow()
                    .username,
            )
            assertEquals(
                listOf("/api/v1/me", "/api/v1/auth/refresh", "/api/v1/me"),
                s.requests.map { it.url.encodedPath },
            )
            assertEquals("refresh-new", s.storage.tokens?.refreshToken)
            assertEquals(AuthState.SignedIn, s.session.state.value)
        }

    @Test
    fun `если refresh отклонён - выход на экран входа`() =
        runTest {
            val s =
                setup { request ->
                    if (request.url.encodedPath.endsWith("/auth/refresh")) {
                        json(problem(401, "INVALID_REFRESH_TOKEN"), HttpStatusCode.Unauthorized)
                    } else {
                        unauthorized()
                    }
                }
            assertEquals(true, s.me.me().isFailure)
            assertNull(s.storage.tokens)
            assertEquals(AuthState.SignedOut, s.session.state.value)
        }

    @Test
    fun `токен не отправляется на публичные эндпоинты входа`() =
        runTest {
            val s = setup { json(profileJson) }
            // клиент с авторизацией не должен подставлять Bearer на /auth/*
            val engine =
                MockEngine { request ->
                    s.requests += request
                    json("{}")
                }
            val client = createAuthorizedClient(engine, TestConfig, s.session, createPublicClient(engine, TestConfig))
            client.post("api/v1/auth/otp/request")
            assertNull(s.requests.last().bearer())
        }
}
