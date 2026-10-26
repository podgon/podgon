package app.podgon.identity

import app.podgon.IntegrationTest
import org.junit.jupiter.api.Test
import org.springframework.test.context.TestPropertySource
import kotlin.test.assertEquals

/** Управляемый тестовый аккаунт для модерации магазинов (ADR-006): фиксированный код, письма не отправляются. */
@TestPropertySource(
    properties = [
        "podgon.auth.test-account.enabled=true",
        "podgon.auth.test-account.email=Review@Example.com",
        "podgon.auth.test-account.code=424242",
    ],
)
class TestAccountTest : IntegrationTest() {
    @Test
    fun `для тестового email действует фиксированный код и письмо не создаётся`() {
        assertEquals(202, requestCode("review@example.com").response.status)
        assertEquals(0, jdbc.sql("select count(*) from mail_outbox").query(Int::class.java).single())
        assertEquals(
            200,
            post("/auth/otp/verify", mapOf("email" to "review@example.com", "code" to "424242")).response.status,
        )
    }

    @Test
    fun `для остальных адресов код случайный`() {
        requestCode("other@example.com")
        val code = latestCode("other@example.com")
        if (code != "424242") {
            assertEquals(
                400,
                post("/auth/otp/verify", mapOf("email" to "other@example.com", "code" to "424242")).response.status,
            )
        }
    }

    @Test
    fun `фиксированный код подчиняется общим ограничениям попыток`() {
        requestCode("review@example.com")
        repeat(5) { post("/auth/otp/verify", mapOf("email" to "review@example.com", "code" to "000001")) }
        assertEquals(
            400,
            post("/auth/otp/verify", mapOf("email" to "review@example.com", "code" to "424242")).response.status,
        )
    }
}
