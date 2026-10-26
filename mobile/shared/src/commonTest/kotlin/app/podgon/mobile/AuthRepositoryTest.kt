package app.podgon.mobile

import app.podgon.contract.ErrorCode
import app.podgon.mobile.core.auth.AuthState
import app.podgon.mobile.core.error.AppError
import app.podgon.mobile.core.error.appError
import app.podgon.mobile.feature.auth.data.VerifyOutcome
import app.podgon.mobile.testing.AuthFixture
import app.podgon.mobile.testing.TOKENS_JSON
import app.podgon.mobile.testing.json
import app.podgon.mobile.testing.problem
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class AuthRepositoryTest {
    @Test
    fun `верный код существующего пользователя открывает сессию и сохраняет токены`() =
        runTest {
            val fixture = AuthFixture { json("""{"tokens":$TOKENS_JSON}""") }
            val outcome = fixture.repository.verify(" a@example.com ", "123456").getOrThrow()
            assertEquals(VerifyOutcome.SignedIn, outcome)
            assertEquals(AuthState.SignedIn, fixture.session.state.value)
            assertEquals("refresh-1", fixture.storage.tokens?.refreshToken)
            assertEquals(
                "https://api.test/api/v1/auth/otp/verify",
                fixture.requests
                    .single()
                    .url
                    .toString(),
            )
        }

    @Test
    fun `для нового пользователя возвращается токен регистрации и сессия не открывается`() =
        runTest {
            val fixture = AuthFixture { json("""{"registrationToken":"reg-token"}""") }
            val outcome = fixture.repository.verify("new@example.com", "123456").getOrThrow()
            assertEquals(VerifyOutcome.RegistrationRequired("reg-token"), outcome)
            assertNull(fixture.storage.tokens)
        }

    @Test
    fun `регистрация сохраняет токены`() =
        runTest {
            val fixture = AuthFixture { json(TOKENS_JSON, HttpStatusCode.Created) }
            fixture.repository.register("reg", " user_1 ", " Имя ").getOrThrow()
            assertEquals(AuthState.SignedIn, fixture.session.state.value)
            val body = (fixture.requests.single().body as TextContent).text
            assertEquals(true, body.contains("\"username\":\"user_1\""), body)
            assertEquals(true, body.contains("\"displayName\":\"Имя\""), body)
            assertEquals(true, body.contains("\"deviceInfo\":\"Test device\""), body)
        }

    @Test
    fun `ошибка API превращается в типизированную ошибку с кодом`() =
        runTest {
            val fixture = AuthFixture { json(problem(400, "INVALID_CODE"), HttpStatusCode.BadRequest) }
            val error = fixture.repository.verify("a@example.com", "123456").appError()
            assertIs<AppError.Api>(error)
            assertEquals(ErrorCode.INVALID_CODE, error.code)
            assertEquals(400, error.status)
            assertEquals(AuthState.Loading, fixture.session.state.value)
        }

    @Test
    fun `429 несёт Retry-After`() =
        runTest {
            val fixture =
                AuthFixture {
                    json(problem(429, "RATE_LIMITED"), HttpStatusCode.TooManyRequests, mapOf("Retry-After" to "42"))
                }
            val error = fixture.repository.requestCode("a@example.com").appError()
            assertIs<AppError.Api>(error)
            assertEquals(ErrorCode.RATE_LIMITED, error.code)
            assertEquals(42L, error.retryAfterSeconds)
        }

    @Test
    fun `неизвестный код ошибки не ломает разбор`() =
        runTest {
            val fixture = AuthFixture { json(problem(409, "SOMETHING_NEW"), HttpStatusCode.Conflict) }
            val error = fixture.repository.requestCode("a@example.com").appError()
            assertIs<AppError.Api>(error)
            assertNull(error.code)
            assertEquals(409, error.status)
        }

    @Test
    fun `сбой связи - ошибка Network`() =
        runTest {
            val fixture = AuthFixture { error("нет сети") }
            assertEquals(AppError.Network, fixture.repository.requestCode("a@example.com").appError())
        }

    @Test
    fun `выход закрывает локальную сессию даже без сети`() =
        runTest {
            val fixture =
                AuthFixture(
                    storage =
                        app.podgon.mobile.testing.FakeTokenStorage(
                            app.podgon.mobile.core.auth
                                .StoredTokens("a", "r"),
                        ),
                ) {
                    error("нет сети")
                }
            fixture.repository.logout()
            assertNull(fixture.storage.tokens)
            assertEquals(AuthState.SignedOut, fixture.session.state.value)
        }
}
