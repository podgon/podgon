package app.podgon.identity

import java.util.UUID

/** Публичные сведения о пользователе для других модулей (участники групп, приглашения). */
data class UserSummary(
    val id: UUID,
    val email: String,
    val username: String,
    val displayName: String,
    val locale: String,
    /** Заблокированный пользователь не может вступать в группы. */
    val blocked: Boolean,
)

/**
 * Порт модуля identity: другие модули получают пользователей через него и не читают таблицы
 * `users` напрямую.
 */
interface UserDirectory {
    fun find(id: UUID): UserSummary?

    fun findAll(ids: Collection<UUID>): Map<UUID, UserSummary>

    /** Зарегистрированный пользователь с этим email (в нижнем регистре), если есть. */
    fun findByEmail(email: String): UserSummary?
}
