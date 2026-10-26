package app.podgon.groups.domain

import app.podgon.common.ApiException
import app.podgon.contract.ErrorCode
import app.podgon.groups.GroupAccess
import app.podgon.groups.persistence.GroupRepository
import app.podgon.groups.persistence.MyGroupRow
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import java.util.UUID

fun notFound() = ApiException(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND)

fun forbidden() = ApiException(ErrorCode.FORBIDDEN, HttpStatus.FORBIDDEN)

/**
 * Единственное место проверки прав в группе. Не участник → 404 (существование группы не раскрываем),
 * участник без роли администратора → 403.
 */
@Service
class GroupPolicy(
    private val groups: GroupRepository,
) : GroupAccess {
    fun member(
        userId: UUID,
        groupId: UUID,
    ): MyGroupRow = groups.findForMember(groupId, userId) ?: throw notFound()

    fun admin(
        userId: UUID,
        groupId: UUID,
    ): MyGroupRow {
        val mine = member(userId, groupId)
        if (!mine.role.admin) throw forbidden()
        return mine
    }

    override fun requireMember(
        userId: UUID,
        groupId: UUID,
    ) {
        member(userId, groupId)
    }

    override fun isMember(
        userId: UUID,
        groupId: UUID,
    ): Boolean = groups.findForMember(groupId, userId) != null

    override fun memberGroupIds(userId: UUID): Set<UUID> = groups.groupIdsOf(userId)

    override fun requireAdmin(
        userId: UUID,
        groupId: UUID,
    ) {
        admin(userId, groupId)
    }
}
