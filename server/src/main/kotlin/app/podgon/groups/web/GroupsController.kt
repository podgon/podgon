package app.podgon.groups.web

import app.podgon.contract.CreateGroupRequest
import app.podgon.contract.CreateRoleRequest
import app.podgon.contract.GroupSummary
import app.podgon.contract.MemberInfo
import app.podgon.contract.ReviewsFate
import app.podgon.contract.RoleInfo
import app.podgon.contract.SetMemberRoleRequest
import app.podgon.contract.UpdateGroupRequest
import app.podgon.contract.UpdateRoleRequest
import app.podgon.groups.domain.GroupService
import app.podgon.groups.domain.MemberService
import app.podgon.groups.domain.RoleService
import app.podgon.groups.domain.notFound
import app.podgon.identity.AuthenticatedUser
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

/** Группы, участники и роли. Права проверяются в сервисах (`GroupPolicy`), контроллер только разбирает запрос. */
@RestController
@RequestMapping("/api/v1/groups")
class GroupsController(
    private val groups: GroupService,
    private val members: MemberService,
    private val roles: RoleService,
) {
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(
        @AuthenticationPrincipal user: AuthenticatedUser,
        @RequestBody body: CreateGroupRequest,
    ): GroupSummary = groups.create(user.userId, body)

    @GetMapping
    fun list(
        @AuthenticationPrincipal user: AuthenticatedUser,
    ): List<GroupSummary> = groups.list(user.userId)

    @GetMapping("/{groupId}")
    fun get(
        @AuthenticationPrincipal user: AuthenticatedUser,
        @PathVariable groupId: String,
    ): GroupSummary = groups.get(user.userId, groupId.toUuidOrNotFound())

    @PatchMapping("/{groupId}")
    fun update(
        @AuthenticationPrincipal user: AuthenticatedUser,
        @PathVariable groupId: String,
        @RequestBody body: UpdateGroupRequest,
    ): GroupSummary = groups.update(user.userId, groupId.toUuidOrNotFound(), body)

    @DeleteMapping("/{groupId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(
        @AuthenticationPrincipal user: AuthenticatedUser,
        @PathVariable groupId: String,
        @RequestParam(defaultValue = "false") confirm: Boolean,
        @RequestParam(defaultValue = "RETURN_TO_PERSONAL") reviews: ReviewsFate,
    ) = groups.delete(user.userId, groupId.toUuidOrNotFound(), confirm, reviews)

    @PostMapping("/{groupId}/leave")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun leave(
        @AuthenticationPrincipal user: AuthenticatedUser,
        @PathVariable groupId: String,
        @RequestParam(defaultValue = "false") deleteMyReviews: Boolean,
    ) = groups.leave(user.userId, groupId.toUuidOrNotFound(), deleteMyReviews)

    @GetMapping("/{groupId}/members")
    fun members(
        @AuthenticationPrincipal user: AuthenticatedUser,
        @PathVariable groupId: String,
    ): List<MemberInfo> = members.list(user.userId, groupId.toUuidOrNotFound())

    @DeleteMapping("/{groupId}/members/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun removeMember(
        @AuthenticationPrincipal user: AuthenticatedUser,
        @PathVariable groupId: String,
        @PathVariable userId: String,
    ) = members.remove(user.userId, groupId.toUuidOrNotFound(), userId.toUuidOrNotFound())

    @PutMapping("/{groupId}/members/{userId}/role")
    fun setRole(
        @AuthenticationPrincipal user: AuthenticatedUser,
        @PathVariable groupId: String,
        @PathVariable userId: String,
        @RequestBody body: SetMemberRoleRequest,
    ): MemberInfo {
        val roleId = runCatching { java.util.UUID.fromString(body.roleId) }.getOrNull() ?: throw notFound()
        return members.setRole(user.userId, groupId.toUuidOrNotFound(), userId.toUuidOrNotFound(), roleId)
    }

    @GetMapping("/{groupId}/roles")
    fun roles(
        @AuthenticationPrincipal user: AuthenticatedUser,
        @PathVariable groupId: String,
    ): List<RoleInfo> = roles.list(user.userId, groupId.toUuidOrNotFound())

    @PostMapping("/{groupId}/roles")
    @ResponseStatus(HttpStatus.CREATED)
    fun createRole(
        @AuthenticationPrincipal user: AuthenticatedUser,
        @PathVariable groupId: String,
        @RequestBody body: CreateRoleRequest,
    ): RoleInfo = roles.create(user.userId, groupId.toUuidOrNotFound(), body)

    @PatchMapping("/{groupId}/roles/{roleId}")
    fun updateRole(
        @AuthenticationPrincipal user: AuthenticatedUser,
        @PathVariable groupId: String,
        @PathVariable roleId: String,
        @RequestBody body: UpdateRoleRequest,
    ): RoleInfo = roles.update(user.userId, groupId.toUuidOrNotFound(), roleId.toUuidOrNotFound(), body)

    @DeleteMapping("/{groupId}/roles/{roleId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteRole(
        @AuthenticationPrincipal user: AuthenticatedUser,
        @PathVariable groupId: String,
        @PathVariable roleId: String,
    ) = roles.delete(user.userId, groupId.toUuidOrNotFound(), roleId.toUuidOrNotFound())
}
