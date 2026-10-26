package app.podgon.groups.domain

import app.podgon.contract.MemberInfo
import app.podgon.groups.persistence.GroupRepository
import app.podgon.groups.persistence.MemberRow
import app.podgon.identity.UserDirectory
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import java.util.UUID

@Service
class MemberService(
    private val groups: GroupRepository,
    private val policy: GroupPolicy,
    private val admins: AdminGuard,
    private val users: UserDirectory,
    private val tx: TransactionTemplate,
) {
    fun list(
        userId: UUID,
        groupId: UUID,
    ): List<MemberInfo> {
        policy.member(userId, groupId)
        val members = groups.members(groupId)
        val profiles = users.findAll(members.map { it.userId })
        return members.map { it.toDto(profiles) }
    }

    /** Администратор удаляет участника; себя удалить нельзя — для этого есть выход из группы. */
    fun remove(
        adminId: UUID,
        groupId: UUID,
        userId: UUID,
    ) {
        tx.executeWithoutResult {
            lockAndCheckAdmin(adminId, groupId)
            if (userId == adminId) throw forbidden()
            if (!groups.deleteMember(groupId, userId)) throw notFound()
            admins.ensureAdminRemains(groupId)
        }
    }

    fun setRole(
        adminId: UUID,
        groupId: UUID,
        userId: UUID,
        roleId: UUID,
    ): MemberInfo {
        tx.executeWithoutResult {
            lockAndCheckAdmin(adminId, groupId)
            groups.member(groupId, userId) ?: throw notFound()
            groups.role(groupId, roleId) ?: throw notFound()
            groups.setMemberRole(groupId, userId, roleId)
            admins.ensureAdminRemains(groupId)
        }
        val member = groups.member(groupId, userId) ?: throw notFound()
        return member.toDto(users.findAll(listOf(userId)))
    }

    private fun lockAndCheckAdmin(
        adminId: UUID,
        groupId: UUID,
    ) {
        if (!groups.lock(groupId)) throw notFound()
        policy.admin(adminId, groupId)
    }

    private fun MemberRow.toDto(profiles: Map<UUID, app.podgon.identity.UserSummary>): MemberInfo {
        val profile = profiles[userId]
        return MemberInfo(
            userId = userId.toString(),
            username = profile?.username ?: "deleted",
            displayName = profile?.displayName ?: "",
            role = role.toDto(),
            joinedAt = joinedAt.toString(),
        )
    }
}
