package app.podgon.groups.domain

import app.podgon.common.ApiException
import app.podgon.common.validationFailed
import app.podgon.contract.CreateGroupRequest
import app.podgon.contract.ErrorCode
import app.podgon.contract.FieldError
import app.podgon.contract.GroupSummary
import app.podgon.contract.UpdateGroupRequest
import app.podgon.contract.Validation
import app.podgon.groups.GroupCreated
import app.podgon.groups.GroupsProperties
import app.podgon.groups.persistence.GroupRepository
import app.podgon.groups.persistence.RoleRow
import app.podgon.identity.UserDirectory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import java.time.Clock
import java.util.UUID

@Service
class GroupService(
    private val groups: GroupRepository,
    private val policy: GroupPolicy,
    private val admins: AdminGuard,
    private val props: GroupsProperties,
    private val users: UserDirectory,
    private val events: ApplicationEventPublisher,
    private val clock: Clock,
    private val tx: TransactionTemplate,
) {
    /** Создаёт группу: сама группа, системные роли ADMIN и MEMBER и членство создателя с ролью ADMIN. */
    fun create(
        userId: UUID,
        request: CreateGroupRequest,
    ): GroupSummary {
        val errors =
            listOfNotNull(
                Validation.groupName(request.name)?.let { FieldError("name", it) },
                Validation.groupDescription(request.description)?.let { FieldError("description", it) },
            )
        if (errors.isNotEmpty()) throw validationFailed(errors)

        val groupId =
            tx.execute {
                if (groups.countGroupsOf(userId) >= props.maxGroupsPerUser) throw limitExceeded()
                val now = clock.instant()
                val id = UUID.randomUUID()
                groups.insertGroup(id, request.name.trim(), request.description.trim(), userId, now)
                val adminRole = systemRole(id, "ADMIN", "Администратор", admin = true)
                groups.insertRole(adminRole, now)
                groups.insertRole(systemRole(id, "MEMBER", "Участник", admin = false), now)
                groups.insertMember(id, userId, adminRole.id, now)
                events.publishEvent(GroupCreated(id, userId, users.find(userId)?.locale ?: "en"))
                id
            }!!
        return policy.member(userId, groupId).toDto()
    }

    fun list(userId: UUID): List<GroupSummary> = groups.listForUser(userId).map { it.toDto() }

    fun get(
        userId: UUID,
        groupId: UUID,
    ): GroupSummary = policy.member(userId, groupId).toDto()

    /** Частичное обновление: `null` — поле не менять. */
    fun update(
        userId: UUID,
        groupId: UUID,
        request: UpdateGroupRequest,
    ): GroupSummary {
        val errors =
            listOfNotNull(
                request.name?.let { v -> Validation.groupName(v)?.let { FieldError("name", it) } },
                request.description?.let { v -> Validation.groupDescription(v)?.let { FieldError("description", it) } },
            )
        if (errors.isNotEmpty()) throw validationFailed(errors)

        tx.executeWithoutResult {
            val current = policy.admin(userId, groupId).group
            groups.update(
                groupId,
                request.name?.trim() ?: current.name,
                request.description?.trim() ?: current.description,
                clock.instant(),
            )
        }
        return policy.member(userId, groupId).toDto()
    }

    /** Физическое удаление с каскадом; выбор судьбы отзывов появится вместе с отзывами (этап 6). */
    fun delete(
        userId: UUID,
        groupId: UUID,
        confirm: Boolean,
    ) {
        policy.admin(userId, groupId)
        if (!confirm) throw ApiException(ErrorCode.CONFIRMATION_REQUIRED, HttpStatus.BAD_REQUEST)
        tx.executeWithoutResult {
            if (!groups.lock(groupId)) throw notFound()
            policy.admin(userId, groupId)
            groups.delete(groupId)
        }
    }

    /** Единственный администратор выйти не может: сначала назначить другого или удалить группу. */
    fun leave(
        userId: UUID,
        groupId: UUID,
    ) {
        tx.executeWithoutResult {
            if (!groups.lock(groupId)) throw notFound()
            policy.member(userId, groupId)
            groups.deleteMember(groupId, userId)
            admins.ensureAdminRemains(groupId)
        }
    }

    private fun systemRole(
        groupId: UUID,
        code: String,
        title: String,
        admin: Boolean,
    ) = RoleRow(UUID.randomUUID(), groupId, code, title, "", admin = admin, system = true)

    private fun limitExceeded() = ApiException(ErrorCode.LIMIT_EXCEEDED, HttpStatus.CONFLICT)
}
