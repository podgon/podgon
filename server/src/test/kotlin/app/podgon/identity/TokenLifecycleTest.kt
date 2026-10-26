package app.podgon.identity

import app.podgon.IntegrationTest
import org.junit.jupiter.api.Test
import org.springframework.test.web.servlet.MvcResult
import org.springframework.test.web.servlet.get
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class TokenLifecycleTest : IntegrationTest() {
    private fun me(accessToken: String?): MvcResult =
        mockMvc
            .get("/api/v1/me") { accessToken?.let { header("Authorization", "Bearer $it") } }
            .andReturn()

    private fun refresh(token: String): MvcResult = post("/auth/refresh", mapOf("refreshToken" to token))

    @Test
    fun `без токена и с мусорным токеном - 401 в формате problem+json`() {
        for (token in listOf(null, "garbage", "a.b.c")) {
            val r = invalidRequests { me(token) }
            assertEquals(401, r.response.status)
            assertEquals("UNAUTHORIZED", body(r)["code"].asString())
            assertEquals("application/problem+json", r.response.contentType?.substringBefore(';'))
        }
    }

    @Test
    fun `подделанный токен отклоняется`() {
        val access = registerUser("t1@example.com", "tamper")["accessToken"].asString()
        val tampered = access.dropLast(4) + (if (access.endsWith("AAAA")) "BBBB" else "AAAA")
        assertEquals(401, me(tampered).response.status)
    }

    @Test
    fun `access-токен истекает через 15 минут`() {
        val access = registerUser("t2@example.com", "expiring")["accessToken"].asString()
        assertEquals(200, me(access).response.status)
        clock.advance(Duration.ofMinutes(14))
        assertEquals(200, me(access).response.status)
        clock.advance(Duration.ofMinutes(2))
        assertEquals(401, me(access).response.status)
    }

    @Test
    fun `регистрационный токен не годится как access-токен`() {
        requestCode("t3@example.com")
        val registration =
            body(
                post(
                    "/auth/otp/verify",
                    mapOf(
                        "email" to "t3@example.com",
                        "code" to latestCode("t3@example.com"),
                    ),
                ),
            )["registrationToken"].asString()
        assertEquals(401, me(registration).response.status)
    }

    @Test
    fun `refresh с ротацией - новая пара работает, старый refresh больше не принимается`() {
        val first = registerUser("t4@example.com", "rotating")
        val r = refresh(first["refreshToken"].asString())
        assertEquals(200, r.response.status)
        val second = body(r)
        assertNotEquals(first["refreshToken"].asString(), second["refreshToken"].asString())
        assertEquals(200, me(second["accessToken"].asString()).response.status)
        assertEquals(900, second["expiresIn"].asLong())

        val reuse = refresh(first["refreshToken"].asString())
        assertEquals(401, reuse.response.status)
        assertEquals("INVALID_REFRESH_TOKEN", body(reuse)["code"].asString())
    }

    @Test
    fun `повторное использование refresh отзывает всю сессию`() {
        val first = registerUser("t5@example.com", "reuse")
        val second = body(refresh(first["refreshToken"].asString()))
        // злоумышленник (или клиент с гонкой) предъявляет уже обменянный токен
        assertEquals(401, refresh(first["refreshToken"].asString()).response.status)
        // легитимная цепочка тоже закрыта
        assertEquals(401, refresh(second["refreshToken"].asString()).response.status)
    }

    @Test
    fun `неизвестный refresh отклоняется`() {
        assertEquals(401, refresh("unknown-token-value").response.status)
    }

    @Test
    fun `refresh истекает через 30 дней, при регулярном обновлении сессия живёт не дольше 90`() {
        val tokens = registerUser("t6@example.com", "ttl")
        clock.advance(Duration.ofDays(31))
        assertEquals(401, refresh(tokens["refreshToken"].asString()).response.status)

        var current = login("t6@example.com")
        repeat(3) {
            clock.advance(Duration.ofDays(29))
            val r = refresh(current["refreshToken"].asString())
            assertEquals(200, r.response.status)
            current = body(r)
        }
        // прошло 87 дней; ещё через 4 дня сессия достигает предела в 90
        clock.advance(Duration.ofDays(4))
        assertEquals(401, refresh(current["refreshToken"].asString()).response.status)
    }

    @Test
    fun `выход отзывает сессию и идемпотентен`() {
        val tokens = registerUser("t7@example.com", "leaving")
        val logout = post("/auth/logout", mapOf("refreshToken" to tokens["refreshToken"].asString()))
        assertEquals(204, logout.response.status)
        assertEquals(401, refresh(tokens["refreshToken"].asString()).response.status)
        assertEquals(
            204,
            post("/auth/logout", mapOf("refreshToken" to tokens["refreshToken"].asString())).response.status,
        )
        assertEquals(204, post("/auth/logout", mapOf("refreshToken" to "never-existed")).response.status)
    }

    @Test
    fun `заблокированному пользователю refresh не выдаётся`() {
        val tokens = registerUser("t8@example.com", "banned")
        jdbc.sql("update users set status = 'BLOCKED' where email = 't8@example.com'").update()
        assertEquals(401, refresh(tokens["refreshToken"].asString()).response.status)
    }

    @Test
    fun `access-токен перестаёт действовать сразу после выхода и блокировки`() {
        val loggedOut = registerUser("t11@example.com", "instant_logout")
        post("/auth/logout", mapOf("refreshToken" to loggedOut["refreshToken"].asString()))
        assertEquals(401, me(loggedOut["accessToken"].asString()).response.status)

        val blocked = registerUser("t12@example.com", "instant_block")["accessToken"].asString()
        assertEquals(200, me(blocked).response.status)
        jdbc.sql("update users set status = 'BLOCKED' where email = 't12@example.com'").update()
        assertEquals(401, me(blocked).response.status)
    }

    @Test
    fun `после обновления пары прежний access-токен действует до истечения`() {
        val first = registerUser("t13@example.com", "rotated_access")
        refresh(first["refreshToken"].asString())
        assertEquals(200, me(first["accessToken"].asString()).response.status)
    }

    @Test
    fun `refresh хранится только как хэш`() {
        val tokens = registerUser("t9@example.com", "hashed")
        val raw = tokens["refreshToken"].asString()
        val stored = jdbc.sql("select token_hash from refresh_tokens").query { rs, _ -> rs.getBytes(1) }.single()
        assertEquals(32, stored.size)
        assertEquals(
            false,
            java.util.Base64
                .getUrlEncoder()
                .withoutPadding()
                .encodeToString(stored) == raw,
        )
    }

    @Test
    fun `пустое тело запроса - 400 с кодом ошибки`() {
        val r = invalidRequests { post("/auth/refresh", null) }
        assertEquals(400, r.response.status)
        assertEquals("VALIDATION_FAILED", body(r)["code"].asString())
    }

    @Test
    fun `токен со старым ключом проверяется после ротации ключей`() {
        // t0 есть в конфигурации тестов как предыдущий ключ; активный — t1. Токены, выданные t1, проходят проверку.
        val access = registerUser("t10@example.com", "keys")["accessToken"].asString()
        val header =
            String(
                java.util.Base64
                    .getUrlDecoder()
                    .decode(access.substringBefore('.')),
            )
        assertEquals(true, header.contains("\"kid\":\"t1\""))
        assertEquals(200, me(access).response.status)
    }
}
