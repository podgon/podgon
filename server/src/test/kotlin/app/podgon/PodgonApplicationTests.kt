package app.podgon

import org.junit.jupiter.api.Test
import org.springframework.test.web.servlet.get
import kotlin.test.assertEquals

class PodgonApplicationTests : IntegrationTest() {
    @Test
    fun `миграции применяются и расширения созданы`() {
        val extensions =
            jdbc
                .sql("select extname from pg_extension where extname in ('citext','pg_trgm') order by 1")
                .query(String::class.java)
                .list()
        assertEquals(listOf("citext", "pg_trgm"), extensions)
    }

    @Test
    fun `база в кодировке UTF8`() {
        val encoding = jdbc.sql("show server_encoding").query(String::class.java).single()
        assertEquals("UTF8", encoding)
    }

    @Test
    fun `служебный пользователь для удалённых аккаунтов создан миграцией`() {
        val name =
            jdbc
                .sql(
                    "select display_name from users where id = '00000000-0000-0000-0000-000000000001' and status = 'SYSTEM'",
                ).query(String::class.java)
                .single()
        assertEquals("Удалённый пользователь", name)
    }

    @Test
    fun `health доступен без авторизации и контракт публикуется`() {
        mockMvc.get("/actuator/health").andExpect { status { isOk() } }
        mockMvc.get("/openapi.yaml").andExpect { status { isOk() } }
    }
}
