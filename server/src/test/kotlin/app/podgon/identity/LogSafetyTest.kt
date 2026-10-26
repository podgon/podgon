package app.podgon.identity

import app.podgon.IntegrationTest
import app.podgon.mail.internal.OutboxProcessor
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.system.CapturedOutput
import org.springframework.boot.test.system.OutputCaptureExtension
import java.time.Duration
import kotlin.test.assertFalse

/** Код входа, токены и email не должны попадать в логи (DoD этапа 2). */
@ExtendWith(OutputCaptureExtension::class)
class LogSafetyTest : IntegrationTest() {
    @Autowired
    lateinit var outbox: OutboxProcessor

    @Test
    fun `полный сценарий входа не оставляет секретов в логах`(output: CapturedOutput) {
        val email = "secret.person@example.com"
        requestCode(email)
        val code = latestCode(email)
        mailSender.failWith = RuntimeException("SMTP отказал для $email, код $code")
        outbox.processBatch() // сбой отправки тоже логируется
        mailSender.failWith = null
        clock.advance(Duration.ofMinutes(1))
        outbox.processBatch()

        post("/auth/otp/verify", mapOf("email" to email, "code" to if (code == "000000") "111111" else "000000"))
        val verify = body(post("/auth/otp/verify", mapOf("email" to email, "code" to code)))
        val registration = verify["registrationToken"].asString()
        val tokens =
            body(
                post(
                    "/auth/register",
                    mapOf(
                        "registrationToken" to registration,
                        "username" to "log_check",
                        "displayName" to "Имя",
                    ),
                ),
            )
        val refreshed = body(post("/auth/refresh", mapOf("refreshToken" to tokens["refreshToken"].asString())))
        post("/auth/refresh", mapOf("refreshToken" to tokens["refreshToken"].asString())) // повторное использование
        post("/auth/logout", mapOf("refreshToken" to refreshed["refreshToken"].asString()))
        post(
            "/auth/register",
            mapOf(
                "registrationToken" to "broken.token.value",
                "username" to "x_y_z",
                "displayName" to "Имя",
            ),
        )

        val log = output.all
        for (secret in listOf(
            email,
            code,
            registration,
            tokens["accessToken"].asString(),
            tokens["refreshToken"].asString(),
            refreshed["refreshToken"].asString(),
        )) {
            assertFalse(log.contains(secret), "В логах найден секрет: ${secret.take(6)}…")
        }
    }
}
