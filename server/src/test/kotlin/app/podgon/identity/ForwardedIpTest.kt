package app.podgon.identity

import app.podgon.PostgresTestcontainersConfig
import app.podgon.TestSupportConfig
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import kotlin.test.assertEquals

/**
 * IP клиента за обратным прокси (замечание R4 ревью): лимит выдачи кодов по IP считается по адресу из
 * `X-Forwarded-For`, который добавил доверенный прокси (здесь — loopback), а не по адресу самого прокси.
 * Нужен настоящий Tomcat: MockMvc не проходит через RemoteIpValve.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(PostgresTestcontainersConfig::class, TestSupportConfig::class)
class ForwardedIpTest {
    @LocalServerPort
    private var port: Int = 0

    private val http = HttpClient.newHttpClient()

    private fun requestCode(
        email: String,
        forwardedFor: String,
    ): Int {
        val request =
            HttpRequest
                .newBuilder(URI("http://127.0.0.1:$port/api/v1/auth/otp/request"))
                .header("Content-Type", "application/json")
                .header("X-Forwarded-For", forwardedFor)
                .POST(HttpRequest.BodyPublishers.ofString("""{"email":"$email","captchaToken":"t"}"""))
                .build()
        return http.send(request, HttpResponse.BodyHandlers.discarding()).statusCode()
    }

    @Test
    fun `лимит по IP считается по адресу клиента от прокси, а не по адресу прокси`() {
        repeat(20) { assertEquals(202, requestCode("fwd$it@example.com", "203.0.113.7")) }
        assertEquals(429, requestCode("fwd20@example.com", "203.0.113.7"))
        // другой клиент за тем же прокси не страдает от чужого лимита
        assertEquals(202, requestCode("fwd21@example.com", "203.0.113.8"))
    }

    @Test
    fun `подставленный клиентом X-Forwarded-For не обходит лимит`() {
        // прокси дописывает настоящий адрес клиента справа; левые значения клиент выдумал
        repeat(20) { assertEquals(202, requestCode("spoof$it@example.com", "198.51.100.$it, 203.0.113.9")) }
        assertEquals(429, requestCode("spoof20@example.com", "198.51.100.99, 203.0.113.9"))
    }
}
