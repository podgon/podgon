package app.podgon.mobile

import app.podgon.mobile.core.auth.AuthState
import app.podgon.mobile.core.auth.SessionManager
import app.podgon.mobile.core.auth.StoredTokens
import app.podgon.mobile.core.network.ApiConfig
import app.podgon.mobile.core.network.AuthApi
import app.podgon.mobile.core.network.MeApi
import app.podgon.mobile.core.network.createAuthorizedClient
import app.podgon.mobile.core.network.createPublicClient
import app.podgon.mobile.feature.auth.data.AuthRepository
import app.podgon.mobile.feature.auth.data.VerifyOutcome
import app.podgon.mobile.testing.FakeTokenStorage
import io.ktor.client.engine.okhttp.OkHttp
import kotlinx.coroutines.runBlocking
import java.io.File
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Сквозной сценарий клиентского стека против реального сервера: email → код → регистрация → профиль →
 * автообновление токена → выход. Запускается только при заданных переменных окружения:
 * `PODGON_E2E_API` (адрес сервера) и `PODGON_E2E_MAIL_FILE` (файл, куда SMTP-приёмник складывает письма;
 * в docker-compose вместо него Mailpit). В обычной сборке и в CI тест пропускается.
 */
class EndToEndTest {
    private val api = System.getenv("PODGON_E2E_API")
    private val mailFile = System.getenv("PODGON_E2E_MAIL_FILE")?.let(::File)

    @Test
    fun `вход, регистрация, профиль, обновление токена и выход`() {
        if (api == null || mailFile == null) return
        runBlocking {
            val config = ApiConfig(api)
            val engine = OkHttp.create()
            val storage = FakeTokenStorage()
            val session = SessionManager(storage).also { it.restore() }
            val publicClient = createPublicClient(engine, config)
            val authApi = AuthApi(publicClient)
            val repository = AuthRepository(authApi, session, captcha = { null }, device = { "E2E test device" })
            val me = MeApi(createAuthorizedClient(engine, config, session, publicClient))

            val email = "e2e.${System.currentTimeMillis()}@example.com"
            repository.requestCode(email).getOrThrow()
            val code = awaitCode(email)

            val outcome = repository.verify(email, code).getOrThrow()
            assertTrue(outcome is VerifyOutcome.RegistrationRequired, "новый email → нужна регистрация")
            assertEquals(AuthState.SignedOut, session.state.value)

            repository
                .register(
                    outcome.registrationToken,
                    "e2e_${System.currentTimeMillis()}",
                    "E2E Пользователь",
                ).getOrThrow()
            assertEquals(AuthState.SignedIn, session.state.value)

            val profile = me.me().getOrThrow()
            assertEquals(email, profile.email)
            assertEquals("E2E Пользователь", profile.displayName)
            val sessions = me.sessions().getOrThrow()
            assertEquals(1, sessions.size)
            assertTrue(sessions.single().current)
            assertEquals("E2E test device", sessions.single().deviceInfo)

            // протухший access-токен: клиент сам обновляет пару через refresh и повторяет запрос
            val before = checkNotNull(storage.tokens)
            storage.tokens = StoredTokens("expired.access.token", before.refreshToken)
            // (новый клиент: Ktor кэширует загруженные токены внутри экземпляра, как после перезапуска приложения)
            val restarted = MeApi(createAuthorizedClient(engine, config, session, publicClient))
            assertEquals(email, restarted.me().getOrThrow().email)
            val after = checkNotNull(storage.tokens)
            assertNotEquals(before.refreshToken, after.refreshToken)

            // старый refresh после ротации недействителен (и отзывает сессию)
            assertTrue(authApi.refresh(before.refreshToken).isFailure)

            repository.logout()
            assertEquals(AuthState.SignedOut, session.state.value)
            assertTrue(authApi.refresh(after.refreshToken).isFailure, "после выхода refresh отозван")
        }
    }

    /** Ждёт письмо с кодом для [email] и достаёт шестизначный код (тело письма может быть в base64). */
    private fun awaitCode(email: String): String {
        val file = checkNotNull(mailFile)
        repeat(60) {
            if (file.exists()) {
                val mail = file.readText().split("=====").lastOrNull { it.contains("TO:$email") }
                if (mail != null) {
                    val normalized = mail.replace("\r\n", "\n")
                    val headers = normalized.substringBefore("\n\n")
                    val rawBody = normalized.substringAfter("\n\n").trim()
                    val body =
                        if (headers.contains("base64", ignoreCase = true)) {
                            String(Base64.getMimeDecoder().decode(rawBody), Charsets.UTF_8)
                        } else {
                            rawBody
                        }
                    Regex("\\b(\\d{6})\\b").find(body)?.let { return it.groupValues[1] }
                }
            }
            Thread.sleep(500)
        }
        fail("письмо с кодом для $email не пришло")
    }
}
