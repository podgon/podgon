package app.podgon.mail.internal

import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.time.Instant
import java.util.UUID

data class OutboxMessage(
    val id: UUID,
    val to: String,
    val template: String,
    val locale: String,
    val payloadJson: String,
    val attempts: Int,
)

@Repository
class OutboxRepository(
    private val jdbc: JdbcClient,
) {
    fun insert(
        id: UUID,
        to: String,
        template: String,
        locale: String,
        payloadJson: String,
        now: Instant,
    ) {
        jdbc
            .sql(
                """
                insert into mail_outbox (id, to_address, template, locale, payload, status, next_attempt_at, created_at)
                values (:id, :to, :template, :locale, cast(:payload as jsonb), 'PENDING', :now, :now)
                """.trimIndent(),
            ).param("id", id)
            .param("to", to)
            .param("template", template)
            .param("locale", locale)
            .param("payload", payloadJson)
            .param("now", java.sql.Timestamp.from(now))
            .update()
    }

    /** Берёт следующее готовое к отправке письмо и блокирует строку до конца транзакции. */
    fun claimNext(now: Instant): OutboxMessage? =
        jdbc
            .sql(
                """
                select id, to_address, template, locale, payload::text as payload, attempts
                from mail_outbox
                where status = 'PENDING' and next_attempt_at <= :now
                order by next_attempt_at
                limit 1
                for update skip locked
                """.trimIndent(),
            ).param("now", java.sql.Timestamp.from(now))
            .query { rs, _ ->
                OutboxMessage(
                    id = rs.getObject("id", UUID::class.java),
                    to = rs.getString("to_address"),
                    template = rs.getString("template"),
                    locale = rs.getString("locale"),
                    payloadJson = rs.getString("payload"),
                    attempts = rs.getInt("attempts"),
                )
            }.optional()
            .orElse(null)

    /** Письмо отправлено: параметры (в том числе код) стираются. */
    fun markSent(
        id: UUID,
        now: Instant,
    ) {
        jdbc
            .sql(
                "update mail_outbox set status = 'SENT', payload = '{}'::jsonb, sent_at = :now, attempts = attempts + 1 where id = :id",
            ).param("now", java.sql.Timestamp.from(now))
            .param("id", id)
            .update()
    }

    fun markRetry(
        id: UUID,
        nextAttemptAt: Instant,
    ) {
        jdbc
            .sql("update mail_outbox set attempts = attempts + 1, next_attempt_at = :next where id = :id")
            .param("next", java.sql.Timestamp.from(nextAttemptAt))
            .param("id", id)
            .update()
    }

    fun markFailed(id: UUID) {
        jdbc
            .sql(
                "update mail_outbox set status = 'FAILED', payload = '{}'::jsonb, attempts = attempts + 1 where id = :id",
            ).param("id", id)
            .update()
    }

    /** Удаляет обработанные письма старше [before]. */
    fun deleteFinishedBefore(before: Instant): Int =
        jdbc
            .sql("delete from mail_outbox where status in ('SENT', 'FAILED') and created_at < :before")
            .param("before", java.sql.Timestamp.from(before))
            .update()
}
