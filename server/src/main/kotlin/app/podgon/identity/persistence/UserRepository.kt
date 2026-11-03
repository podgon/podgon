package app.podgon.identity.persistence

import app.podgon.common.toSql
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.time.Instant
import java.util.UUID

data class UserRow(
    val id: UUID,
    val email: String,
    val username: String,
    val displayName: String,
    val bio: String,
    val status: String,
    val locale: String,
    val avatarId: UUID? = null,
)

@Repository
class UserRepository(
    private val jdbc: JdbcClient,
) {
    private val columns =
        "id, email::text as email, username::text as username, " +
            "coalesce(display_name, '') as display_name, bio, status, locale, avatar_id"

    private fun map(
        rs: ResultSet,
        @Suppress("UNUSED_PARAMETER") row: Int,
    ) = UserRow(
        id = rs.getObject("id", UUID::class.java),
        email = rs.getString("email"),
        username = rs.getString("username"),
        displayName = rs.getString("display_name"),
        bio = rs.getString("bio"),
        status = rs.getString("status"),
        locale = rs.getString("locale"),
        avatarId = rs.getObject("avatar_id", UUID::class.java),
    )

    /** Пользователь, который может входить: не удалён и не служебный. */
    fun findLoginableByEmail(email: String): UserRow? =
        jdbc
            .sql(
                "select $columns from users where email = :email and deleted_at is null and status in ('ACTIVE', 'BLOCKED')",
            ).param("email", email)
            .query(::map)
            .optional()
            .orElse(null)

    fun findById(id: UUID): UserRow? =
        jdbc
            .sql("select $columns from users where id = :id and deleted_at is null")
            .param("id", id)
            .query(::map)
            .optional()
            .orElse(null)

    fun findByIds(ids: Collection<UUID>): List<UserRow> =
        if (ids.isEmpty()) {
            emptyList()
        } else {
            jdbc
                .sql("select $columns from users where id in (:ids)")
                .param("ids", ids)
                .query(::map)
                .list()
        }

    /** @throws org.springframework.dao.DuplicateKeyException при занятых email или username */
    fun insert(
        user: UserRow,
        now: Instant,
    ) {
        jdbc
            .sql(
                """
                insert into users (id, email, username, display_name, bio, status, locale, created_at, updated_at)
                values (:id, :email, :username, :displayName, :bio, :status, :locale, :now, :now)
                """.trimIndent(),
            ).param("id", user.id)
            .param("email", user.email)
            .param("username", user.username)
            .param("displayName", user.displayName)
            .param("bio", user.bio)
            .param("status", user.status)
            .param("locale", user.locale)
            .param("now", now.toSql())
            .update()
    }

    /**
     * Мягкое удаление: адрес и имя освобождаются, личные данные очищаются, строка остаётся
     * (на неё ссылаются группы, приглашения и категории).
     */
    fun anonymize(
        id: UUID,
        now: Instant,
    ) {
        jdbc
            .sql(
                """
                update users set email = 'deleted+' || id || '@invalid', username = 'deleted_' || replace(id::text, '-', ''),
                       display_name = null, bio = '', avatar_id = null, status = 'DELETED', deleted_at = :now, updated_at = :now
                where id = :id and deleted_at is null
                """.trimIndent(),
            ).param("id", id)
            .param("now", now.toSql())
            .update()
    }

    /** Новый аватар ([avatarId] `null` — убрать); файл прежнего удаляет ночная чистка или сервис. */
    fun setAvatar(
        id: UUID,
        avatarId: UUID?,
        now: Instant,
    ) {
        jdbc
            .sql("update users set avatar_id = :avatar, updated_at = :now where id = :id and deleted_at is null")
            .param("id", id)
            .param("avatar", avatarId)
            .param("now", now.toSql())
            .update()
    }

    /** Аватары, на которые есть ссылки (из [ids]). */
    fun usedAvatars(ids: Collection<UUID>): Set<UUID> =
        if (ids.isEmpty()) {
            emptySet()
        } else {
            jdbc
                .sql("select avatar_id from users where avatar_id in (:ids)")
                .param("ids", ids)
                .query { rs, _ -> rs.getObject("avatar_id", UUID::class.java) }
                .set()
        }

    /** @throws org.springframework.dao.DuplicateKeyException при занятом username */
    fun updateProfile(
        id: UUID,
        username: String,
        displayName: String,
        bio: String,
        locale: String,
        now: Instant,
    ) {
        jdbc
            .sql(
                """
                update users set username = :username, display_name = :displayName, bio = :bio,
                       locale = :locale, updated_at = :now
                where id = :id and deleted_at is null
                """.trimIndent(),
            ).param("id", id)
            .param("username", username)
            .param("displayName", displayName)
            .param("bio", bio)
            .param("locale", locale)
            .param("now", now.toSql())
            .update()
    }
}
