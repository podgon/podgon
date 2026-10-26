package app.podgon.identity.persistence

import app.podgon.common.toSql
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.time.Instant
import java.util.UUID

data class EmailCodeRow(
    val id: UUID,
    val codeHmac: ByteArray,
    val attempts: Int,
)

/** Окно подсчёта отправленных кодов: сколько кодов и когда был создан самый ранний из них. */
data class CodeWindow(
    val count: Int,
    val oldest: Instant?,
)

@Repository
class EmailCodeRepository(
    private val jdbc: JdbcClient,
) {
    /** Сериализует операции с кодами одного email внутри транзакции (защита от гонок лимитов и попыток). */
    fun lockEmail(email: String) {
        jdbc
            .sql("select pg_advisory_xact_lock(hashtext(:email))")
            .param("email", email)
            .query()
            .singleRow()
    }

    fun latestCreatedAt(email: String): Instant? =
        jdbc
            .sql("select max(created_at) as created_at from email_codes where email = :email")
            .param("email", email)
            .query { rs, _ -> rs.getTimestamp("created_at")?.toInstant() }
            .list()
            .first()

    fun windowByEmail(
        email: String,
        since: Instant,
    ): CodeWindow =
        jdbc
            .sql(
                "select count(*) as c, min(created_at) as oldest from email_codes where email = :email and created_at > :since",
            ).param("email", email)
            .param("since", since.toSql())
            .query { rs, _ -> CodeWindow(rs.getInt("c"), rs.getTimestamp("oldest")?.toInstant()) }
            .single()

    fun windowByIp(
        ip: String,
        since: Instant,
    ): CodeWindow =
        jdbc
            .sql(
                "select count(*) as c, min(created_at) as oldest from email_codes where ip = :ip and created_at > :since",
            ).param("ip", ip)
            .param("since", since.toSql())
            .query { rs, _ -> CodeWindow(rs.getInt("c"), rs.getTimestamp("oldest")?.toInstant()) }
            .single()

    /** Аннулирует действующие коды email: активен всегда только последний. */
    fun consumeActive(
        email: String,
        now: Instant,
    ) {
        jdbc
            .sql("update email_codes set consumed_at = :now where email = :email and consumed_at is null")
            .param("email", email)
            .param("now", now.toSql())
            .update()
    }

    fun insert(
        id: UUID,
        email: String,
        codeHmac: ByteArray,
        now: Instant,
        expiresAt: Instant,
        ip: String?,
    ) {
        jdbc
            .sql(
                """
                insert into email_codes (id, email, code_hmac, created_at, expires_at, ip)
                values (:id, :email, :hmac, :now, :expires, :ip)
                """.trimIndent(),
            ).param("id", id)
            .param("email", email)
            .param("hmac", codeHmac)
            .param("now", now.toSql())
            .param("expires", expiresAt.toSql())
            .param("ip", ip)
            .update()
    }

    /** Действующий код email: не использован, не просрочен, попытки не исчерпаны. Строка блокируется. */
    fun findActiveForUpdate(
        email: String,
        now: Instant,
        maxAttempts: Int,
    ): EmailCodeRow? =
        jdbc
            .sql(
                """
                select id, code_hmac, attempts from email_codes
                where email = :email and consumed_at is null and expires_at > :now and attempts < :max
                order by created_at desc
                limit 1
                for update
                """.trimIndent(),
            ).param("email", email)
            .param("now", now.toSql())
            .param("max", maxAttempts)
            .query {
                rs,
                _,
                ->
                EmailCodeRow(rs.getObject("id", UUID::class.java), rs.getBytes("code_hmac"), rs.getInt("attempts"))
            }.optional()
            .orElse(null)

    fun consume(
        id: UUID,
        now: Instant,
    ) {
        jdbc
            .sql("update email_codes set consumed_at = :now where id = :id")
            .param("id", id)
            .param("now", now.toSql())
            .update()
    }

    fun incrementAttempts(id: UUID) {
        jdbc.sql("update email_codes set attempts = attempts + 1 where id = :id").param("id", id).update()
    }

    fun deleteCreatedBefore(before: Instant): Int =
        jdbc.sql("delete from email_codes where created_at < :before").param("before", before.toSql()).update()
}
