package app.podgon.identity

import app.podgon.IntegrationTest
import com.sun.net.httpserver.HttpServer
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Test
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import java.net.InetSocketAddress
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Проверка интеграции с SmartCaptcha на локальном HTTP-сервере, имитирующем сервис Яндекса. */
class CaptchaTest : IntegrationTest() {
    companion object {
        private val queries = CopyOnWriteArrayList<String>()

        @Volatile
        private var mode = "ok"

        private val server: HttpServer =
            HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
                createContext("/validate") { exchange ->
                    queries += exchange.requestURI.rawQuery.orEmpty()
                    if (mode == "down") {
                        exchange.sendResponseHeaders(503, -1)
                    } else {
                        val bytes =
                            """{"status":"${if (mode == "ok") "ok" else "failed"}","message":""}"""
                                .toByteArray()
                        exchange.responseHeaders.add("Content-Type", "application/json")
                        exchange.sendResponseHeaders(200, bytes.size.toLong())
                        exchange.responseBody.use { it.write(bytes) }
                    }
                    exchange.close()
                }
                start()
            }

        @JvmStatic
        @DynamicPropertySource
        fun props(registry: DynamicPropertyRegistry) {
            registry.add("podgon.auth.captcha.mode") { "smartcaptcha" }
            registry.add("podgon.auth.captcha.secret") { "server-secret" }
            registry.add("podgon.auth.captcha.validate-url") { "http://127.0.0.1:${server.address.port}/validate" }
        }

        @JvmStatic
        @AfterAll
        fun stop() {
            server.stop(0)
        }
    }

    @Test
    fun `токен проверяется в SmartCaptcha, при успехе код отправляется`() {
        mode = "ok"
        queries.clear()
        assertEquals(
            202,
            post("/auth/otp/request", mapOf("email" to "c1@example.com", "captchaToken" to "tok+/=1")).response.status,
        )
        val query = queries.single()
        assertTrue(query.contains("secret=server-secret"))
        assertTrue(query.contains("token=tok%2B/%3D1") || query.contains("token=tok"), query)
        assertTrue(query.contains("ip="))
    }

    @Test
    fun `отказ SmartCaptcha - 400, письмо не ставится в очередь`() {
        mode = "failed"
        val r = post("/auth/otp/request", mapOf("email" to "c2@example.com", "captchaToken" to "bad"))
        assertEquals(400, r.response.status)
        assertEquals("CAPTCHA_FAILED", body(r)["code"].asString())
        assertEquals(0, jdbc.sql("select count(*) from mail_outbox").query(Int::class.java).single())
    }

    @Test
    fun `без токена запрос отклоняется, сервис не вызывается`() {
        mode = "ok"
        queries.clear()
        val r = post("/auth/otp/request", mapOf("email" to "c3@example.com"))
        assertEquals("CAPTCHA_FAILED", body(r)["code"].asString())
        assertTrue(queries.isEmpty())
    }

    @Test
    fun `недоступность SmartCaptcha не блокирует вход`() {
        mode = "down"
        assertEquals(
            202,
            post("/auth/otp/request", mapOf("email" to "c4@example.com", "captchaToken" to "t")).response.status,
        )
    }

    @Test
    fun `токены со спецсимволами не обходят проверку и не ломают запрос`() {
        mode = "failed"
        for (token in listOf("{a}", "{", "}}{", "%zz", "a b&secret=x", "x".repeat(5000))) {
            val r = post("/auth/otp/request", mapOf("email" to "c5@example.com", "captchaToken" to token))
            assertEquals(400, r.response.status, "token=${token.take(10)}")
            assertEquals("CAPTCHA_FAILED", body(r)["code"].asString())
        }
        // спецсимволы передаются сервису корректно закодированными, а не обрывают запрос
        mode = "ok"
        queries.clear()
        assertEquals(
            202,
            post("/auth/otp/request", mapOf("email" to "c6@example.com", "captchaToken" to "{a}&x=1")).response.status,
        )
        val query = queries.single()
        assertTrue(query.contains("token=%7Ba%7D%26x%3D1"), query)
    }
}
