package app.podgon

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.simple.JdbcClient
import kotlin.test.assertEquals

@SpringBootTest
@Import(PostgresTestcontainersConfig::class)
class PodgonApplicationTests(
    @Autowired private val jdbc: JdbcClient,
) {
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
}
