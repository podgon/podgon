package app.podgon.groups.domain

import app.podgon.common.ApiException
import app.podgon.common.validationFailed
import app.podgon.contract.CreateRoleRequest
import app.podgon.contract.ErrorCode
import app.podgon.contract.FieldError
import app.podgon.contract.RoleInfo
import app.podgon.contract.UpdateRoleRequest
import app.podgon.contract.Validation
import app.podgon.groups.GroupsProperties
import app.podgon.groups.persistence.GroupRepository
import app.podgon.groups.persistence.RoleRow
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import java.time.Clock
import java.util.UUID

@Service
class RoleService(
    private val groups: GroupRepository,
    private val policy: GroupPolicy,
    private val admins: AdminGuard,
    private val props: GroupsProperties,
    private val clock: Clock,
    private val tx: TransactionTemplate,
) {
    fun list(
        userId: UUID,
        groupId: UUID,
    ): List<RoleInfo> {
        policy.member(userId, groupId)
        return groups.roles(groupId).map { it.toDto() }
    }

    fun create(
        adminId: UUID,
        groupId: UUID,
        request: CreateRoleRequest,
    ): RoleInfo {
        val errors =
            listOfNotNull(
                Validation.roleTitle(request.title)?.let { FieldError("title", it) },
                Validation.roleDescription(request.description)?.let { FieldError("description", it) },
            )
        if (errors.isNotEmpty()) throw validationFailed(errors)

        val role =
            tx.execute {
                lockAndCheckAdmin(adminId, groupId)
                if (groups.roles(groupId).size >= props.maxRolesPerGroup) {
                    throw ApiException(ErrorCode.LIMIT_EXCEEDED, HttpStatus.CONFLICT)
                }
                val created =
                    RoleRow(
                        id = UUID.randomUUID(),
                        groupId = groupId,
                        code =
                            "R" +
                                UUID
                                    .randomUUID()
                                    .toString()
                                    .replace("-", "")
                                    .take(12)
                                    .uppercase(),
                        title = request.title.trim(),
                        description = request.description.trim(),
                        admin = request.admin,
                        system = false,
                    )
                groups.insertRole(created, clock.instant())
                created
            }!!
        return role.toDto()
    }

    /** Меняет пользовательскую роль; системные роли (ADMIN, MEMBER) неизменяемы. */
    fun update(
        adminId: UUID,
        groupId: UUID,
        roleId: UUID,
        request: UpdateRoleRequest,
    ): RoleInfo {
        val errors =
            listOfNotNull(
                request.title?.let { v -> Validation.roleTitle(v)?.let { FieldError("title", it) } },
                request.description?.let { v -> Validation.roleDescription(v)?.let { FieldError("description", it) } },
            )
        if (errors.isNotEmpty()) throw validationFailed(errors)

        tx.executeWithoutResult {
            lockAndCheckAdmin(adminId, groupId)
            val current = groups.role(groupId, roleId) ?: throw notFound()
            if (current.system) throw forbidden()
            groups.updateRole(
                roleId,
                request.title?.trim() ?: current.title,
                request.description?.trim() ?: current.description,
                request.admin ?: current.admin,
            )
            admins.ensureAdminRemains(groupId)
        }
        return (groups.role(groupId, roleId) ?: throw notFound()).toDto()
    }

    /** Удаляет пользовательскую роль; её участники становятся обычными (MEMBER). */
    fun delete(
        adminId: UUID,
        groupId: UUID,
        roleId: UUID,
    ) {
        tx.executeWithoutResult {
            lockAndCheckAdmin(adminId, groupId)
            val current = groups.role(groupId, roleId) ?: throw notFound()
            if (current.system) throw forbidden()
            groups.reassignRole(groupId, roleId, groups.roleByCode(groupId, "MEMBER").id)
            groups.deleteRole(roleId)
            admins.ensureAdminRemains(groupId)
        }
    }

    private fun lockAndCheckAdmin(
        adminId: UUID,
        groupId: UUID,
    ) {
        if (!groups.lock(groupId)) throw notFound()
        policy.admin(adminId, groupId)
    }
}
