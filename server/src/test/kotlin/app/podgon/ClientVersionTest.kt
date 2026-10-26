package app.podgon

import app.podgon.identity.REQUEST_GUARDS_PROPERTIES
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MvcResult
import org.springframework.test.web.servlet.post
import kotlin.test.assertEquals

/** Принудительное обновление устаревших сборок (замечание R1 ревью); минимальная версия Android здесь — 100. */
@TestPropertySource(properties = [REQUEST_GUARDS_PROPERTIES])
class ClientVersionTest : IntegrationTest() {
    private fun requestCode(
        email: String,
        platform: String?,
        version: String?,
    ): MvcResult =
        mockMvc
            .post("/api/v1/auth/otp/request") {
                contentType = MediaType.APPLICATION_JSON
                content = json.writeValueAsString(mapOf("email" to email, "captchaToken" to "t"))
                platform?.let { header("X-Client-Platform", it) }
                version?.let { header("X-Client-Version", it) }
            }.andReturn()

    @Test
    fun `устаревшая сборка Android получает 426 UPGRADE_REQUIRED`() {
        val old = requestCode("cv1@example.com", "android", "99")
        assertEquals(426, old.response.status)
        assertEquals("UPGRADE_REQUIRED", body(old)["code"].asString())
        assertEquals(0, jdbc.sql("select count(*) from email_codes").query(Int::class.java).single())
    }

    @Test
    fun `актуальная сборка, другая платформа и запрос без заголовков проходят`() {
        assertEquals(202, requestCode("cv2@example.com", "android", "100").response.status)
        assertEquals(202, requestCode("cv3@example.com", "ios", "1").response.status)
        assertEquals(202, requestCode("cv4@example.com", null, null).response.status)
    }
}
