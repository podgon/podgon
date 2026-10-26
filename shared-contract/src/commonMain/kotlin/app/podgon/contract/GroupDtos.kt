package app.podgon.contract

import kotlinx.serialization.Serializable

/** Роль в группе. `admin` даёт права администратора; `system` — встроенные роли ADMIN и MEMBER (не меняются и не удаляются). */
@Serializable
data class RoleInfo(
    val id: String,
    val code: String,
    val title: String,
    val description: String,
    val admin: Boolean,
    val system: Boolean,
)

/** Группа глазами текущего пользователя: с его ролью и числом участников. */
@Serializable
data class GroupSummary(
    val id: String,
    val name: String,
    val description: String,
    val memberCount: Int,
    val myRole: RoleInfo,
)

@Serializable
data class CreateGroupRequest(
    val name: String,
    val description: String = "",
)

/** Частичное обновление: `null` — поле не менять. */
@Serializable
data class UpdateGroupRequest(
    val name: String? = null,
    val description: String? = null,
)

@Serializable
data class MemberInfo(
    val userId: String,
    val username: String,
    val displayName: String,
    val role: RoleInfo,
    /** Момент вступления, ISO-8601 в UTC. */
    val joinedAt: String,
)

@Serializable
data class SetMemberRoleRequest(
    val roleId: String,
)

@Serializable
data class CreateRoleRequest(
    val title: String,
    val description: String = "",
    val admin: Boolean = false,
)

/** Частичное обновление пользовательской роли: `null` — поле не менять. */
@Serializable
data class UpdateRoleRequest(
    val title: String? = null,
    val description: String? = null,
    val admin: Boolean? = null,
)

@Serializable
data class CreateInvitationRequest(
    /** Адресное приглашение: письмо получит этот email, принять сможет только его владелец. `null` — приглашение по коду. */
    val email: String? = null,
    val maxUses: Int = 1,
    val ttlHours: Int = Validation.INVITATION_TTL_HOURS_DEFAULT,
)

/** Созданное приглашение. [token] показывается только в этом ответе: в БД хранится его хэш. */
@Serializable
data class InvitationCreated(
    val id: String,
    val token: String,
    val email: String?,
    val expiresAt: String,
    val maxUses: Int,
)

@Serializable
data class InvitationInfo(
    val id: String,
    val email: String?,
    val createdBy: String,
    val createdAt: String,
    val expiresAt: String,
    val maxUses: Int,
    val usedCount: Int,
    val revoked: Boolean,
    /** Можно ли ещё вступить по этому приглашению (не отозвано, не истекло, не исчерпано). */
    val active: Boolean,
)

@Serializable
data class InvitationTokenRequest(
    val token: String,
)

/** Что увидит приглашённый до вступления. */
@Serializable
data class InvitationPreview(
    val groupName: String,
    val groupDescription: String,
    val memberCount: Int,
    val invitedBy: String,
)
