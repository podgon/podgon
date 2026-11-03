package app.podgon.reviews.persistence

import app.podgon.common.toSql
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.time.Instant
import java.util.UUID

/** Запись сохранённого фильтра (`FILTER`) или закреплённой категории (`CATEGORY`); [definition] — JSON. */
data class SavedFilterRow(
    val id: UUID,
    val userId: UUID,
    val kind: String,
    val name: String?,
    val categoryId: UUID?,
    val definition: String?,
    val inFeed: Boolean,
    val position: Int?,
    val subscribed: Boolean = false,
)

@Repository
class SavedFilterRepository(
    private val jdbc: JdbcClient,
) {
    private val columns =
        "id, user_id, kind, name, category_id, definition::text as definition, in_feed, position, subscribed"

    fun list(userId: UUID): List<SavedFilterRow> =
        jdbc
            .sql("select $columns from saved_filters where user_id = :user")
            .param("user", userId)
            .query { rs, _ -> row(rs) }
            .list()

    fun find(
        userId: UUID,
        id: UUID,
    ): SavedFilterRow? =
        jdbc
            .sql("select $columns from saved_filters where user_id = :user and id = :id")
            .param("user", userId)
            .param("id", id)
            .query { rs, _ -> row(rs) }
            .optional()
            .orElse(null)

    /** Сериализует изменения фильтров пользователя (лимит, позиции) до конца транзакции. */
    fun lock(userId: UUID) {
        jdbc
            .sql("select pg_advisory_xact_lock(hashtextextended(:key, 0))")
            .param("key", "saved_filters:$userId")
            .query()
            .singleRow()
    }

    fun count(userId: UUID): Int =
        jdbc
            .sql("select count(*) from saved_filters where user_id = :user")
            .param("user", userId)
            .query(Int::class.java)
            .single()

    /** Следующая позиция среди закреплённых записей вида [kind]. */
    fun nextPosition(
        userId: UUID,
        kind: String,
    ): Int =
        jdbc
            .sql(
                "select coalesce(max(position), -1) + 1 from saved_filters where user_id = :user and kind = :kind" +
                    " and in_feed",
            ).param("user", userId)
            .param("kind", kind)
            .query(Int::class.java)
            .single()

    fun insert(
        row: SavedFilterRow,
        now: Instant,
    ) {
        jdbc
            .sql(
                """
                insert into saved_filters (id, user_id, kind, name, category_id, definition, in_feed, position,
                                           subscribed, created_at, updated_at)
                values (:id, :user, :kind, :name, :category, cast(:definition as jsonb), :inFeed, :position,
                        :subscribed, :now, :now)
                """.trimIndent(),
            ).params(params(row))
            .param("now", now.toSql())
            .update()
    }

    fun update(
        row: SavedFilterRow,
        now: Instant,
    ) {
        jdbc
            .sql(
                """
                update saved_filters set name = :name, definition = cast(:definition as jsonb), in_feed = :inFeed,
                                         position = :position, subscribed = :subscribed, updated_at = :now
                where id = :id and user_id = :user
                """.trimIndent(),
            ).params(params(row))
            .param("now", now.toSql())
            .update()
    }

    fun setPosition(
        id: UUID,
        position: Int,
    ) {
        jdbc
            .sql("update saved_filters set position = :position where id = :id")
            .param("id", id)
            .param("position", position)
            .update()
    }

    fun delete(
        userId: UUID,
        id: UUID,
    ): Boolean =
        jdbc
            .sql("delete from saved_filters where user_id = :user and id = :id")
            .param("user", userId)
            .param("id", id)
            .update() > 0

    /** Фильтры с подпиской у пользователей [userIds] (этап 8.3.5). */
    fun subscribed(userIds: Collection<UUID>): List<SavedFilterRow> =
        if (userIds.isEmpty()) {
            emptyList()
        } else {
            jdbc
                .sql("select $columns from saved_filters where subscribed and user_id in (:users)")
                .param("users", userIds)
                .query { rs, _ -> row(rs) }
                .list()
        }

    fun deleteAll(userId: UUID) {
        jdbc.sql("delete from saved_filters where user_id = :user").param("user", userId).update()
    }

    private fun params(row: SavedFilterRow) =
        mapOf(
            "id" to row.id,
            "user" to row.userId,
            "kind" to row.kind,
            "name" to row.name,
            "category" to row.categoryId,
            "definition" to row.definition,
            "inFeed" to row.inFeed,
            "position" to row.position,
            "subscribed" to row.subscribed,
        )

    private fun row(rs: ResultSet) =
        SavedFilterRow(
            id = rs.getObject("id", UUID::class.java),
            userId = rs.getObject("user_id", UUID::class.java),
            kind = rs.getString("kind"),
            name = rs.getString("name"),
            categoryId = rs.getObject("category_id", UUID::class.java),
            definition = rs.getString("definition"),
            inFeed = rs.getBoolean("in_feed"),
            position = rs.getObject("position") as Int?,
            subscribed = rs.getBoolean("subscribed"),
        )
}
