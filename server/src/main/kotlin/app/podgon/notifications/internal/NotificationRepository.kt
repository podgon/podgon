package app.podgon.notifications.internal

import app.podgon.common.instant
import app.podgon.common.instantOrNull
import app.podgon.common.toSql
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.time.Instant
import java.util.UUID

data class NotificationRow(
    val id: UUID,
    val kind: String,
    val refId: UUID,
    val data: String,
    val createdAt: Instant,
    val readAt: Instant?,
)

data class SettingsRow(
    val invitations: Boolean,
    val filterMatches: Boolean,
)

@Repository
class NotificationRepository(
    private val jdbc: JdbcClient,
) {
    /** Повторное уведомление об одном объекте ничего не меняет. */
    @Suppress("LongParameterList")
    fun insert(
        id: UUID,
        userId: UUID,
        kind: String,
        refId: UUID,
        data: String,
        now: Instant,
    ) {
        jdbc
            .sql(
                """
                insert into notifications (id, user_id, kind, ref_id, data, created_at)
                values (:id, :user, :kind, :ref, cast(:data as jsonb), :now)
                on conflict (user_id, kind, ref_id) do nothing
                """.trimIndent(),
            ).param("id", id)
            .param("user", userId)
            .param("kind", kind)
            .param("ref", refId)
            .param("data", data)
            .param("now", now.toSql())
            .update()
    }

    /** Новые сверху; [afterAt]/[afterId] — позиция последней строки предыдущей страницы. */
    fun page(
        userId: UUID,
        afterAt: Instant?,
        afterId: UUID?,
        limit: Int,
    ): List<NotificationRow> =
        jdbc
            .sql(
                """
                select id, kind, ref_id, data::text as data, created_at, read_at from notifications
                where user_id = :user
                  and (cast(:afterAt as timestamptz) is null or (created_at, id) < (:afterAt, :afterId))
                order by created_at desc, id desc limit :limit
                """.trimIndent(),
            ).param("user", userId)
            .param("afterAt", afterAt?.toSql())
            .param("afterId", afterId ?: UUID(0, 0))
            .param("limit", limit)
            .query { rs, _ ->
                NotificationRow(
                    id = rs.getObject("id", UUID::class.java),
                    kind = rs.getString("kind"),
                    refId = rs.getObject("ref_id", UUID::class.java),
                    data = rs.getString("data"),
                    createdAt = rs.instant("created_at"),
                    readAt = rs.instantOrNull("read_at"),
                )
            }.list()

    fun unread(userId: UUID): Int =
        jdbc
            .sql("select count(*) from notifications where user_id = :user and read_at is null")
            .param("user", userId)
            .query(Int::class.java)
            .single()

    /** [ids] `null` — все непрочитанные пользователя. */
    fun markRead(
        userId: UUID,
        ids: Collection<UUID>?,
        now: Instant,
    ) {
        if (ids != null && ids.isEmpty()) return
        val filter = if (ids == null) "" else " and id in (:ids)"
        jdbc
            .sql("update notifications set read_at = :now where user_id = :user and read_at is null$filter")
            .param("user", userId)
            .param("now", now.toSql())
            .apply { if (ids != null) param("ids", ids) }
            .update()
    }

    fun deleteAll(userId: UUID) {
        jdbc.sql("delete from notifications where user_id = :user").param("user", userId).update()
        jdbc.sql("delete from notification_settings where user_id = :user").param("user", userId).update()
    }

    fun deleteOlderThan(moment: Instant) {
        jdbc.sql("delete from notifications where created_at < :moment").param("moment", moment.toSql()).update()
    }

    fun settings(userId: UUID): SettingsRow =
        jdbc
            .sql("select invitations, filter_matches from notification_settings where user_id = :user")
            .param("user", userId)
            .query { rs, _ -> SettingsRow(rs.getBoolean("invitations"), rs.getBoolean("filter_matches")) }
            .optional()
            .orElse(SettingsRow(invitations = true, filterMatches = true))

    fun saveSettings(
        userId: UUID,
        settings: SettingsRow,
    ) {
        jdbc
            .sql(
                """
                insert into notification_settings (user_id, invitations, filter_matches) values (:user, :inv, :flt)
                on conflict (user_id) do update set invitations = :inv, filter_matches = :flt
                """.trimIndent(),
            ).param("user", userId)
            .param("inv", settings.invitations)
            .param("flt", settings.filterMatches)
            .update()
    }
}
