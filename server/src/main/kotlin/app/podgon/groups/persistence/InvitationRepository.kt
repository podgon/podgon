package app.podgon.groups.persistence

import app.podgon.common.instant
import app.podgon.common.instantOrNull
import app.podgon.common.toSql
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.time.Instant
import java.util.UUID

data class InvitationRow(
    val id: UUID,
    val groupId: UUID,
    val createdBy: UUID,
    val email: String?,
    val expiresAt: Instant,
    val maxUses: Int,
    val usedCount: Int,
    val revokedAt: Instant?,
    val createdAt: Instant,
) {
    /** Можно ли ещё вступить по приглашению. */
    fun isActive(now: Instant) = revokedAt == null && expiresAt.isAfter(now) && usedCount < maxUses
}

@Repository
class InvitationRepository(
    private val jdbc: JdbcClient,
) {
    private val columns =
        "id, group_id, created_by, email::text as email, expires_at, max_uses, used_count, revoked_at, created_at"

    private fun map(
        rs: ResultSet,
        @Suppress("UNUSED_PARAMETER") row: Int,
    ) = InvitationRow(
        id = rs.getObject("id", UUID::class.java),
        groupId = rs.getObject("group_id", UUID::class.java),
        createdBy = rs.getObject("created_by", UUID::class.java),
        email = rs.getString("email"),
        expiresAt = rs.instant("expires_at"),
        maxUses = rs.getInt("max_uses"),
        usedCount = rs.getInt("used_count"),
        revokedAt = rs.instantOrNull("revoked_at"),
        createdAt = rs.instant("created_at"),
    )

    fun insert(
        id: UUID,
        groupId: UUID,
        createdBy: UUID,
        email: String?,
        tokenHash: ByteArray,
        expiresAt: Instant,
        maxUses: Int,
        now: Instant,
    ) {
        jdbc
            .sql(
                """
                insert into invitations (id, group_id, created_by, email, token_hash, expires_at, max_uses, created_at)
                values (:id, :group, :by, :email, :hash, :expires, :maxUses, :now)
                """.trimIndent(),
            ).param("id", id)
            .param("group", groupId)
            .param("by", createdBy)
            .param("email", email)
            .param("hash", tokenHash)
            .param("expires", expiresAt.toSql())
            .param("maxUses", maxUses)
            .param("now", now.toSql())
            .update()
    }

    fun findByHash(tokenHash: ByteArray): InvitationRow? =
        jdbc
            .sql("select $columns from invitations where token_hash = :hash")
            .param("hash", tokenHash)
            .query(::map)
            .optional()
            .orElse(null)

    /** Строка приглашения, заблокированная до конца транзакции (защита счётчика использований). */
    fun findByHashForUpdate(tokenHash: ByteArray): InvitationRow? =
        jdbc
            .sql("select $columns from invitations where token_hash = :hash for update")
            .param("hash", tokenHash)
            .query(::map)
            .optional()
            .orElse(null)

    fun find(
        groupId: UUID,
        id: UUID,
    ): InvitationRow? =
        jdbc
            .sql("select $columns from invitations where group_id = :g and id = :id")
            .param("g", groupId)
            .param("id", id)
            .query(::map)
            .optional()
            .orElse(null)

    fun list(groupId: UUID): List<InvitationRow> =
        jdbc
            .sql("select $columns from invitations where group_id = :g order by created_at desc, id")
            .param("g", groupId)
            .query(::map)
            .list()

    fun incrementUsed(id: UUID) {
        jdbc.sql("update invitations set used_count = used_count + 1 where id = :id").param("id", id).update()
    }

    fun revoke(
        id: UUID,
        now: Instant,
    ) {
        jdbc
            .sql("update invitations set revoked_at = :now where id = :id and revoked_at is null")
            .param("id", id)
            .param("now", now.toSql())
            .update()
    }

    fun countActive(
        groupId: UUID,
        now: Instant,
    ): Int =
        jdbc
            .sql(
                """
                select count(*) from invitations
                where group_id = :g and revoked_at is null and expires_at > :now and used_count < max_uses
                """.trimIndent(),
            ).param("g", groupId)
            .param("now", now.toSql())
            .query(Int::class.java)
            .single()

    /** Сколько адресных приглашений пользователь создал начиная с [since]. */
    fun countEmailInvitationsSince(
        userId: UUID,
        since: Instant,
    ): Int =
        jdbc
            .sql("select count(*) from invitations where created_by = :u and email is not null and created_at > :since")
            .param("u", userId)
            .param("since", since.toSql())
            .query(Int::class.java)
            .single()
}
