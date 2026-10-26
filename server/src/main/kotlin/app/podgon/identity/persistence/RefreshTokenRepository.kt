package app.podgon.identity.persistence

import app.podgon.common.instant
import app.podgon.common.toSql
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.time.Instant
import java.util.UUID

data class RefreshTokenRow(
    val id: UUID,
    val userId: UUID,
    val familyId: UUID,
    val deviceInfo: String?,
    val sessionStartedAt: Instant,
    val expiresAt: Instant,
    val rotatedAt: Instant?,
    val revokedAt: Instant?,
)

data class SessionRow(
    val familyId: UUID,
    val deviceInfo: String?,
    val startedAt: Instant,
    val lastUsedAt: Instant,
)

@Repository
class RefreshTokenRepository(
    private val jdbc: JdbcClient,
) {
    fun insert(
        id: UUID,
        userId: UUID,
        familyId: UUID,
        tokenHash: ByteArray,
        deviceInfo: String?,
        sessionStartedAt: Instant,
        now: Instant,
        expiresAt: Instant,
    ) {
        jdbc
            .sql(
                """
                insert into refresh_tokens (id, user_id, family_id, token_hash, device_info, session_started_at, created_at, expires_at)
                values (:id, :userId, :familyId, :hash, :device, :started, :now, :expires)
                """.trimIndent(),
            ).param("id", id)
            .param("userId", userId)
            .param("familyId", familyId)
            .param("hash", tokenHash)
            .param("device", deviceInfo)
            .param("started", sessionStartedAt.toSql())
            .param("now", now.toSql())
            .param("expires", expiresAt.toSql())
            .update()
    }

    fun findByHashForUpdate(tokenHash: ByteArray): RefreshTokenRow? =
        jdbc
            .sql(
                """
                select id, user_id, family_id, device_info, session_started_at, expires_at, rotated_at, revoked_at
                from refresh_tokens where token_hash = :hash for update
                """.trimIndent(),
            ).param("hash", tokenHash)
            .query { rs, _ ->
                RefreshTokenRow(
                    id = rs.getObject("id", UUID::class.java),
                    userId = rs.getObject("user_id", UUID::class.java),
                    familyId = rs.getObject("family_id", UUID::class.java),
                    deviceInfo = rs.getString("device_info"),
                    sessionStartedAt = rs.instant("session_started_at"),
                    expiresAt = rs.instant("expires_at"),
                    rotatedAt = rs.getTimestamp("rotated_at")?.toInstant(),
                    revokedAt = rs.getTimestamp("revoked_at")?.toInstant(),
                )
            }.optional()
            .orElse(null)

    fun markRotated(
        id: UUID,
        now: Instant,
    ) {
        jdbc
            .sql(
                "update refresh_tokens set rotated_at = :now where id = :id",
            ).param("id", id)
            .param("now", now.toSql())
            .update()
    }

    /** Отзывает всю сессию (цепочку токенов). Возвращает число затронутых токенов. */
    fun revokeFamily(
        familyId: UUID,
        now: Instant,
    ): Int =
        jdbc
            .sql("update refresh_tokens set revoked_at = :now where family_id = :family and revoked_at is null")
            .param("family", familyId)
            .param("now", now.toSql())
            .update()

    /** Отзывает все сессии пользователя (при удалении аккаунта). */
    fun revokeAllOfUser(
        userId: UUID,
        now: Instant,
    ): Int =
        jdbc
            .sql("update refresh_tokens set revoked_at = :now where user_id = :user and revoked_at is null")
            .param("user", userId)
            .param("now", now.toSql())
            .update()

    /** Отзывает сессию пользователя; 0 — если такой сессии у него нет. */
    fun revokeUserFamily(
        userId: UUID,
        familyId: UUID,
        now: Instant,
    ): Int =
        jdbc
            .sql(
                "update refresh_tokens set revoked_at = :now where user_id = :user and family_id = :family and revoked_at is null",
            ).param("user", userId)
            .param("family", familyId)
            .param("now", now.toSql())
            .update()

    /** Живые сессии пользователя: у каждой ровно один непросроченный, не использованный и не отозванный токен. */
    fun findActiveSessions(
        userId: UUID,
        now: Instant,
    ): List<SessionRow> =
        jdbc
            .sql(
                """
                select family_id, device_info, session_started_at, created_at
                from refresh_tokens
                where user_id = :user and revoked_at is null and rotated_at is null and expires_at > :now
                order by session_started_at desc
                """.trimIndent(),
            ).param("user", userId)
            .param("now", now.toSql())
            .query { rs, _ ->
                SessionRow(
                    familyId = rs.getObject("family_id", UUID::class.java),
                    deviceInfo = rs.getString("device_info"),
                    startedAt = rs.instant("session_started_at"),
                    lastUsedAt = rs.instant("created_at"),
                )
            }.list()

    fun deleteExpiredBefore(before: Instant): Int =
        jdbc.sql("delete from refresh_tokens where expires_at < :before").param("before", before.toSql()).update()
}
