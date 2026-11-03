package app.podgon.groups.domain

import app.podgon.groups.persistence.GroupRepository
import app.podgon.identity.SystemGroupIds
import app.podgon.identity.SystemGroups
import org.springframework.stereotype.Service
import java.time.Clock
import java.util.UUID

/**
 * Системные группы «Личное» и «Избранное»: строка в `groups` с типом и владельцем (`created_by`), без участников
 * и ролей. Поэтому они не попадают в списки групп, лимит групп и ленту «Все», а управление ими отклоняет
 * [GroupPolicy]. Удаляются только вместе с аккаунтом ([AccountMemberships]).
 */
@Service
class SystemGroupsService(
    private val groups: GroupRepository,
    private val clock: Clock,
) : SystemGroups {
    override fun create(userId: UUID): SystemGroupIds {
        val now = clock.instant()
        val ids = SystemGroupIds(UUID.randomUUID(), UUID.randomUUID())
        groups.insertGroup(ids.personal, "", "", userId, now, kind = PERSONAL)
        groups.insertGroup(ids.favorites, "", "", userId, now, kind = FAVORITES)
        return ids
    }

    override fun of(userId: UUID): SystemGroupIds? {
        val mine = groups.systemGroupsOf(userId)
        val personal = mine[PERSONAL] ?: return null
        val favorites = mine[FAVORITES] ?: return null
        return SystemGroupIds(personal, favorites)
    }

    private companion object {
        const val PERSONAL = "PERSONAL"
        const val FAVORITES = "FAVORITES"
    }
}
