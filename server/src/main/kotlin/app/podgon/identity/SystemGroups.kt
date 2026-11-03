package app.podgon.identity

import java.util.UUID

/** Системные группы пользователя: «Личное» и «Избранное» (docs/design.md, «Системные группы»). */
data class SystemGroupIds(
    val personal: UUID,
    val favorites: UUID,
)

/**
 * Порт для модуля identity: системные группы создаются при регистрации и показываются в профиле.
 * Реализует модуль групп (он и так зависит от identity), поэтому зависимость не замыкается в цикл.
 */
interface SystemGroups {
    /** Создаёт обе группы; вызывать в транзакции регистрации. */
    fun create(userId: UUID): SystemGroupIds

    fun of(userId: UUID): SystemGroupIds?
}
