package app.podgon.groups

import app.podgon.contract.GroupRef
import java.util.UUID

/**
 * Публичный порт модуля групп для других модулей (рекомендации): проверка членства и прав в обычных группах.
 * Нет доступа — исключение 404 (существование чужой группы не раскрываем), нет прав админа — 403, своя
 * системная группа — 409 `SYSTEM_GROUP`. Системные группы пользователя — через `identity.SystemGroups`.
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

    /** Все обычные группы пользователя (для проверки видимости чужих рекомендаций); системные не входят. */
    fun memberGroupIds(userId: UUID): Set<UUID>

    /** Группы, где пользователь единственный администратор, а участников больше одного: из них нельзя просто уйти. */
    fun soleAdminGroups(userId: UUID): List<GroupRef>
}
