package app.podgon.groups

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
}
