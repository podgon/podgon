package app.podgon.groups

import app.podgon.contract.GroupRef
import java.util.UUID

/**
 * Публичный порт модуля групп для других модулей (категории, рекомендации): проверка членства и прав.
 * Нет доступа — исключение 404 (существование чужой группы не раскрываем), нет прав админа — 403.
 */
interface GroupAccess {
    fun requireMember(
        userId: UUID,
        groupId: UUID,
    )

    fun requireAdmin(
        userId: UUID,
        groupId: UUID,
    )

    fun isMember(
        userId: UUID,
        groupId: UUID,
    ): Boolean

    /** Все группы пользователя (для проверки видимости чужих рекомендаций). */
    fun memberGroupIds(userId: UUID): Set<UUID>

    /** Группы, где пользователь единственный администратор, а участников больше одного: из них нельзя просто уйти. */
    fun soleAdminGroups(userId: UUID): List<GroupRef>
}
