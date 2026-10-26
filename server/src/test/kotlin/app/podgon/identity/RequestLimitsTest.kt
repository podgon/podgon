package app.podgon.identity

import app.podgon.IntegrationTest
import org.junit.jupiter.api.Test
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MvcResult
import org.springframework.test.web.servlet.get
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Общий набор свойств с ClientVersionTest — один контекст Spring на оба класса. */
const val REQUEST_GUARDS_PROPERTIES =
    "podgon.request-limits.per-user-per-minute=5\npodgon.request-limits.search-per-user-per-minute=2\n" +
        "podgon.client.min-version.android=100"

/** Лимиты запросов авторизованного пользователя (замечание R5 ревью). */
@TestPropertySource(properties = [REQUEST_GUARDS_PROPERTIES])
class RequestLimitsTest : IntegrationTest() {
    private fun get(
        path: String,
        access: String,
    ): MvcResult = mockMvc.get("/api/v1$path") { header("Authorization", "Bearer $access") }.andReturn()

    @Test
    fun `общий лимит на пользователя - 429 с Retry-After, через минуту снова можно`() {
        val access = registerUser("limit1@example.com", "limited_one")["accessToken"].asString()
        repeat(5) { assertEquals(200, get("/me", access).response.status) }
        val limited = get("/me", access)
        assertEquals(429, limited.response.status)
        assertEquals("RATE_LIMITED", body(limited)["code"].asString())
        val retryAfter = limited.response.getHeader("Retry-After")!!.toLong()
        assertTrue(retryAfter in 1..60, "Retry-After = $retryAfter")

        // лимит личный: другой пользователь не затронут
        val other = registerUser("limit2@example.com", "limited_two")["accessToken"].asString()
        assertEquals(200, get("/me", other).response.status)

        clock.advance(Duration.ofSeconds(retryAfter))
        assertEquals(200, get("/me", access).response.status)
    }

    @Test
    fun `поиск ограничен отдельно и строже`() {
        val access = registerUser("limit3@example.com", "limited_three")["accessToken"].asString()
        repeat(2) { assertEquals(200, get("/reviews?q=pizza", access).response.status) }
        assertEquals(429, get("/reviews?q=pizza", access).response.status)
        // запрос без текста поиска идёт в общий лимит
        assertEquals(200, get("/reviews", access).response.status)
    }
}
