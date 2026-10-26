package app.podgon.identity

import app.podgon.IntegrationTest
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.web.servlet.get
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OtpFlowTest : IntegrationTest() {
    @Autowired
    lateinit var outbox: app.podgon.mail.internal.OutboxProcessor

    @Test
    fun `новый пользователь - код, проверка, регистрация, профиль`() {
        val request = requestCode("New.User@Example.com")
        assertEquals(202, request.response.status)
        assertEquals("", request.response.contentAsString)

        // письмо уходит из outbox фоновой отправкой; здесь запускаем её вручную
        assertEquals(1, outbox.processBatch())
        val mail = mailSender.sent.single()
        assertEquals("new.user@example.com", mail.to)
        assertTrue(Regex("\\d{6}").containsMatchIn(mail.body))

        val code = Regex("\\d{6}").find(mail.body)!!.value
        val verify = post("/auth/otp/verify", mapOf("email" to "new.user@example.com", "code" to code))
        assertEquals(200, verify.response.status)
        val registrationToken = body(verify)["registrationToken"].asString()
        assertNull(body(verify)["tokens"])

        val register =
            post(
                "/auth/register",
                mapOf(
                    "registrationToken" to registrationToken,
                    "username" to "Ivan_P",
                    "displayName" to "Иван Петров",
                ),
                acceptLanguage = "en-US,en;q=0.9",
            )
        assertEquals(201, register.response.status)
        val access = body(register)["accessToken"].asString()

        val me = mockMvcGet("/me", access)
        assertEquals("new.user@example.com", me["email"].asString())
        assertEquals("Ivan_P", me["username"].asString())
        assertEquals("Иван Петров", me["displayName"].asString())
        assertEquals("en", me["locale"].asString())
    }

    @Test
    fun `существующий пользователь получает токены сразу`() {
        registerUser("a@example.com", "alice")
        val tokens = login("a@example.com")
        assertNotNull(tokens["accessToken"])
        assertNotNull(tokens["refreshToken"])
    }

    @Test
    fun `ответ на запрос кода одинаков для существующего и несуществующего email`() {
        registerUser("known@example.com", "known")
        clock.advance(Duration.ofMinutes(5))
        val known = requestCode("known@example.com")
        val unknown = requestCode("unknown@example.com")
        assertEquals(known.response.status, unknown.response.status)
        assertEquals(known.response.contentAsString, unknown.response.contentAsString)
        assertEquals(known.response.headerNames.toSet(), unknown.response.headerNames.toSet())
        // для обоих адресов код создан и письмо поставлено в очередь
        assertEquals(1, outboxCount("unknown@example.com"))
        assertEquals(2, outboxCount("known@example.com"))
    }

    @Test
    fun `неверный код - попытки ограничены, после лимита верный код тоже не принимается`() {
        requestCode("b@example.com")
        val good = latestCode("b@example.com")
        val wrong = if (good == "000000") "111111" else "000000"
        repeat(5) {
            val r = post("/auth/otp/verify", mapOf("email" to "b@example.com", "code" to wrong))
            assertEquals(400, r.response.status)
            assertEquals("INVALID_CODE", body(r)["code"].asString())
        }
        val late = post("/auth/otp/verify", mapOf("email" to "b@example.com", "code" to good))
        assertEquals(400, late.response.status)
        assertEquals("INVALID_CODE", body(late)["code"].asString())
    }

    @Test
    fun `верный код принимается, пока попытки не исчерпаны`() {
        requestCode("c@example.com")
        val good = latestCode("c@example.com")
        val wrong = if (good == "000000") "111111" else "000000"
        repeat(4) {
            assertEquals(
                400,
                post("/auth/otp/verify", mapOf("email" to "c@example.com", "code" to wrong)).response.status,
            )
        }
        assertEquals(200, post("/auth/otp/verify", mapOf("email" to "c@example.com", "code" to good)).response.status)
    }

    @Test
    fun `просроченный код отклоняется`() {
        requestCode("d@example.com")
        val code = latestCode("d@example.com")
        clock.advance(Duration.ofMinutes(10).plusSeconds(1))
        val r = post("/auth/otp/verify", mapOf("email" to "d@example.com", "code" to code))
        assertEquals(400, r.response.status)
        assertEquals("INVALID_CODE", body(r)["code"].asString())
    }

    @Test
    fun `использованный код нельзя применить повторно`() {
        requestCode("e@example.com")
        val code = latestCode("e@example.com")
        assertEquals(200, post("/auth/otp/verify", mapOf("email" to "e@example.com", "code" to code)).response.status)
        assertEquals(400, post("/auth/otp/verify", mapOf("email" to "e@example.com", "code" to code)).response.status)
    }

    @Test
    fun `новый запрос аннулирует предыдущий код`() {
        requestCode("f@example.com")
        val first = latestCode("f@example.com")
        clock.advance(Duration.ofSeconds(61))
        requestCode("f@example.com")
        val second = latestCode("f@example.com")
        if (first != second) {
            assertEquals(
                400,
                post("/auth/otp/verify", mapOf("email" to "f@example.com", "code" to first)).response.status,
            )
        }
        assertEquals(200, post("/auth/otp/verify", mapOf("email" to "f@example.com", "code" to second)).response.status)
    }

    @Test
    fun `код чужого адреса не подходит`() {
        requestCode("g@example.com")
        requestCode("h@example.com", ip = "10.0.0.2")
        val codeG = latestCode("g@example.com")
        val codeH = latestCode("h@example.com")
        if (codeG != codeH) {
            assertEquals(
                400,
                post("/auth/otp/verify", mapOf("email" to "h@example.com", "code" to codeG)).response.status,
            )
        }
    }

    @Test
    fun `код в базе хранится только как HMAC`() {
        requestCode("i@example.com")
        val code = latestCode("i@example.com")
        val stored = jdbc.sql("select code_hmac from email_codes").query { rs, _ -> rs.getBytes(1) }.single()
        assertEquals(32, stored.size)
        assertTrue(!String(stored, Charsets.ISO_8859_1).contains(code))
    }

    @Test
    fun `повторный запрос раньше интервала даёт 429 с Retry-After`() {
        assertEquals(202, requestCode("j@example.com").response.status)
        clock.advance(Duration.ofSeconds(20))
        val r = requestCode("j@example.com")
        assertEquals(429, r.response.status)
        assertEquals("RATE_LIMITED", body(r)["code"].asString())
        assertEquals("40", r.response.getHeader("Retry-After"))
        clock.advance(Duration.ofSeconds(41))
        assertEquals(202, requestCode("j@example.com").response.status)
    }

    @Test
    fun `не более пяти кодов в час на email`() {
        repeat(5) {
            assertEquals(202, requestCode("k@example.com").response.status)
            clock.advance(Duration.ofSeconds(61))
        }
        val r = requestCode("k@example.com")
        assertEquals(429, r.response.status)
        // самый ранний код вышел из окна через час от первого запроса
        assertTrue(r.response.getHeader("Retry-After")!!.toLong() in 1..3600)
        clock.advance(Duration.ofHours(1))
        assertEquals(202, requestCode("k@example.com").response.status)
    }

    @Test
    fun `не более двадцати кодов в час с одного IP`() {
        repeat(20) { assertEquals(202, requestCode("user$it@example.com", ip = "203.0.113.5").response.status) }
        assertEquals(429, requestCode("another@example.com", ip = "203.0.113.5").response.status)
        assertEquals(202, requestCode("another@example.com", ip = "203.0.113.6").response.status)
    }

    @Test
    fun `некорректные данные - 400 с описанием полей`() {
        val badEmail = post("/auth/otp/request", mapOf("email" to "not-an-email"))
        assertEquals(400, badEmail.response.status)
        assertEquals("VALIDATION_FAILED", body(badEmail)["code"].asString())
        assertEquals("email", body(badEmail)["errors"][0]["field"].asString())

        val badCode = invalidRequests { post("/auth/otp/verify", mapOf("email" to "a@example.com", "code" to "12ab")) }
        assertEquals(400, badCode.response.status)
        assertEquals("code", body(badCode)["errors"][0]["field"].asString())
    }

    @Test
    fun `регистрация - проверка username и displayName`() {
        requestCode("m@example.com")
        val token =
            body(
                post(
                    "/auth/otp/verify",
                    mapOf(
                        "email" to "m@example.com",
                        "code" to latestCode("m@example.com"),
                    ),
                ),
            )["registrationToken"].asString()
        for ((username, name) in listOf(
            "ab" to "Имя",
            "bad name" to "Имя",
            "deleted_x1" to "Имя",
            "valid_name" to " ",
        )) {
            val r =
                invalidRequests {
                    post(
                        "/auth/register",
                        mapOf(
                            "registrationToken" to token,
                            "username" to username,
                            "displayName" to name,
                        ),
                    )
                }
            assertEquals(400, r.response.status, "username=$username name=$name")
            assertEquals("VALIDATION_FAILED", body(r)["code"].asString())
        }
    }

    @Test
    fun `username уникален без учёта регистра`() {
        registerUser("n1@example.com", "SamePerson")
        requestCode("n2@example.com", ip = "10.0.0.9")
        val token =
            body(
                post(
                    "/auth/otp/verify",
                    mapOf(
                        "email" to "n2@example.com",
                        "code" to latestCode("n2@example.com"),
                    ),
                ),
            )["registrationToken"].asString()
        val r =
            post(
                "/auth/register",
                mapOf(
                    "registrationToken" to token,
                    "username" to "sameperson",
                    "displayName" to "Второй",
                ),
            )
        assertEquals(409, r.response.status)
        assertEquals("USERNAME_TAKEN", body(r)["code"].asString())
    }

    @Test
    fun `регистрационный токен - только подписанный, просроченный отклоняется, повторная регистрация невозможна`() {
        val garbage =
            post(
                "/auth/register",
                mapOf(
                    "registrationToken" to "abc.def.ghi",
                    "username" to "someone",
                    "displayName" to "Имя",
                ),
            )
        assertEquals(401, garbage.response.status)
        assertEquals("INVALID_REGISTRATION_TOKEN", body(garbage)["code"].asString())

        requestCode("o@example.com")
        val token =
            body(
                post(
                    "/auth/otp/verify",
                    mapOf(
                        "email" to "o@example.com",
                        "code" to latestCode("o@example.com"),
                    ),
                ),
            )["registrationToken"].asString()
        assertEquals(
            201,
            post(
                "/auth/register",
                mapOf(
                    "registrationToken" to token,
                    "username" to "first_one",
                    "displayName" to "Имя",
                ),
            ).response.status,
        )
        val again =
            post(
                "/auth/register",
                mapOf(
                    "registrationToken" to token,
                    "username" to "second_one",
                    "displayName" to "Имя",
                ),
            )
        assertEquals(409, again.response.status)
        assertEquals("EMAIL_ALREADY_REGISTERED", body(again)["code"].asString())

        requestCode("p@example.com", ip = "10.0.0.7")
        val t2 =
            body(
                post("/auth/otp/verify", mapOf("email" to "p@example.com", "code" to latestCode("p@example.com"))),
            )["registrationToken"].asString()
        clock.advance(Duration.ofMinutes(16))
        assertEquals(
            401,
            post(
                "/auth/register",
                mapOf(
                    "registrationToken" to t2,
                    "username" to "late_one",
                    "displayName" to "Имя",
                ),
            ).response.status,
        )
    }

    @Test
    fun `заблокированный пользователь не входит`() {
        registerUser("q@example.com", "blocked_one")
        jdbc.sql("update users set status = 'BLOCKED' where email = 'q@example.com'").update()
        clock.advance(Duration.ofSeconds(61))
        requestCode("q@example.com")
        val r = post("/auth/otp/verify", mapOf("email" to "q@example.com", "code" to latestCode("q@example.com")))
        assertEquals(403, r.response.status)
        assertEquals("USER_BLOCKED", body(r)["code"].asString())
    }

    @Test
    fun `письмо на языке запроса`() {
        post("/auth/otp/request", mapOf("email" to "en@example.com"), acceptLanguage = "en-GB,en;q=0.8")
        post("/auth/otp/request", mapOf("email" to "ru@example.com"), acceptLanguage = "ru-RU")
        outbox.processBatch()
        val byTo = mailSender.sent.associateBy { it.to }
        assertTrue(byTo.getValue("en@example.com").subject.startsWith("Your Podgon"))
        assertTrue(byTo.getValue("ru@example.com").subject.startsWith("Код входа"))
    }

    private fun outboxCount(email: String): Int =
        jdbc
            .sql(
                "select count(*) from mail_outbox where to_address = :e",
            ).param("e", email)
            .query(Int::class.java)
            .single()

    private fun mockMvcGet(
        path: String,
        bearer: String,
    ) = body(
        mockMvc
            .get("/api/v1$path") { header("Authorization", "Bearer $bearer") }
            .andReturn(),
    )
}
