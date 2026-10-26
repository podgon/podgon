package app.podgon

import com.zaxxer.hikari.HikariDataSource
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.nio.file.Path
import java.sql.DriverManager
import java.sql.SQLException
import javax.sql.DataSource
import kotlin.io.path.readText
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * Роль приложения без DDL (замечание R6 ревью): скрипт `deploy/db-init/app-role.sql` даёт `podgon_app`
 * только работу с данными — и на существующие таблицы, и на те, что создадут следующие миграции.
 */
class DbRolesTest : IntegrationTest() {
    @Autowired
    lateinit var dataSource: DataSource

    @Test
    fun `роль приложения меняет данные, но не схему, и получает права на новые таблицы`() {
        val owner = jdbc.sql("select current_user").query(String::class.java).single()
        val script =
            Path
                .of("../deploy/db-init/app-role.sql")
                .readText()
                .replace("__MIGRATOR__", owner)
                .replace("__APP_PASSWORD__", "app-test-password")
        jdbc.sql(script).update()
        // «следующая миграция» владельца схемы
        jdbc.sql("create table roles_probe (id int primary key)").update()
        try {
            val url = (dataSource as HikariDataSource).jdbcUrl
            DriverManager.getConnection(url, "podgon_app", "app-test-password").use { app ->
                app.createStatement().use { st ->
                    st.executeQuery("select count(*) from users").use { assertEquals(true, it.next()) }
                    assertEquals(1, st.executeUpdate("insert into tags (name) values ('roles-probe')"))
                    assertEquals(1, st.executeUpdate("insert into roles_probe values (1)"))
                    assertFailsWith<SQLException> { st.execute("create table app_ddl (id int)") }
                    assertFailsWith<SQLException> { st.execute("drop table roles_probe") }
                    assertFailsWith<SQLException> { st.execute("alter table users add column x int") }
                }
            }
        } finally {
            jdbc.sql("drop table roles_probe").update()
        }
    }
}
