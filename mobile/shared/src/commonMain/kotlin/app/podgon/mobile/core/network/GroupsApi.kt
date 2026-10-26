package app.podgon.mobile.core.network

import app.podgon.contract.CategoryDeleteImpact
import app.podgon.contract.CategoryInfo
import app.podgon.contract.CreateCategoryRequest
import app.podgon.contract.CreateGroupRequest
import app.podgon.contract.CreateInvitationRequest
import app.podgon.contract.CreateRoleRequest
import app.podgon.contract.GroupSummary
import app.podgon.contract.InvitationCreated
import app.podgon.contract.InvitationInfo
import app.podgon.contract.InvitationPreview
import app.podgon.contract.InvitationTokenRequest
import app.podgon.contract.MemberInfo
import app.podgon.contract.MoveCategoryRequest
import app.podgon.contract.RoleInfo
import app.podgon.contract.SetMemberRoleRequest
import app.podgon.contract.UpdateCategoryRequest
import app.podgon.contract.UpdateGroupRequest
import app.podgon.contract.UpdateRoleRequest
import io.ktor.client.HttpClient
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody

/** Группы, участники, роли, приглашения и категории (`api/openapi.yaml`, теги groups, invitations, categories; клиент с авторизацией). */
class GroupsApi(
    private val client: HttpClient,
) {
    suspend fun create(request: CreateGroupRequest): Result<GroupSummary> =
        apiCall { client.post("api/v1/groups") { setBody(request) }.bodyOrThrow() }

    suspend fun list(): Result<List<GroupSummary>> = apiCall { client.get("api/v1/groups").bodyOrThrow() }

    suspend fun get(groupId: String): Result<GroupSummary> =
        apiCall { client.get("api/v1/groups/$groupId").bodyOrThrow() }

    suspend fun update(
        groupId: String,
        request: UpdateGroupRequest,
    ): Result<GroupSummary> = apiCall { client.patch("api/v1/groups/$groupId") { setBody(request) }.bodyOrThrow() }

    suspend fun delete(groupId: String): Result<Unit> =
        apiCall { client.delete("api/v1/groups/$groupId") { parameter("confirm", true) }.bodyOrThrow<Unit>() }

    suspend fun leave(groupId: String): Result<Unit> =
        apiCall { client.post("api/v1/groups/$groupId/leave").bodyOrThrow<Unit>() }

    suspend fun members(groupId: String): Result<List<MemberInfo>> =
        apiCall { client.get("api/v1/groups/$groupId/members").bodyOrThrow() }

    suspend fun removeMember(
        groupId: String,
        userId: String,
    ): Result<Unit> = apiCall { client.delete("api/v1/groups/$groupId/members/$userId").bodyOrThrow<Unit>() }

    suspend fun setMemberRole(
        groupId: String,
        userId: String,
        roleId: String,
    ): Result<MemberInfo> =
        apiCall {
            client
                .put(
                    "api/v1/groups/$groupId/members/$userId/role",
                ) { setBody(SetMemberRoleRequest(roleId)) }
                .bodyOrThrow()
        }

    suspend fun roles(groupId: String): Result<List<RoleInfo>> =
        apiCall { client.get("api/v1/groups/$groupId/roles").bodyOrThrow() }

    suspend fun createRole(
        groupId: String,
        request: CreateRoleRequest,
    ): Result<RoleInfo> = apiCall { client.post("api/v1/groups/$groupId/roles") { setBody(request) }.bodyOrThrow() }

    suspend fun updateRole(
        groupId: String,
        roleId: String,
        request: UpdateRoleRequest,
    ): Result<RoleInfo> =
        apiCall {
            client.patch("api/v1/groups/$groupId/roles/$roleId") { setBody(request) }.bodyOrThrow()
        }

    suspend fun deleteRole(
        groupId: String,
        roleId: String,
    ): Result<Unit> = apiCall { client.delete("api/v1/groups/$groupId/roles/$roleId").bodyOrThrow<Unit>() }

    suspend fun createInvitation(
        groupId: String,
        request: CreateInvitationRequest,
    ): Result<InvitationCreated> =
        apiCall { client.post("api/v1/groups/$groupId/invitations") { setBody(request) }.bodyOrThrow() }

    suspend fun invitations(groupId: String): Result<List<InvitationInfo>> =
        apiCall { client.get("api/v1/groups/$groupId/invitations").bodyOrThrow() }

    suspend fun revokeInvitation(
        groupId: String,
        invitationId: String,
    ): Result<Unit> = apiCall { client.delete("api/v1/groups/$groupId/invitations/$invitationId").bodyOrThrow<Unit>() }

    suspend fun previewInvitation(token: String): Result<InvitationPreview> =
        apiCall { client.post("api/v1/invitations/preview") { setBody(InvitationTokenRequest(token)) }.bodyOrThrow() }

    suspend fun acceptInvitation(token: String): Result<GroupSummary> =
        apiCall { client.post("api/v1/invitations/accept") { setBody(InvitationTokenRequest(token)) }.bodyOrThrow() }

    suspend fun categories(groupId: String): Result<List<CategoryInfo>> =
        apiCall { client.get("api/v1/groups/$groupId/categories").bodyOrThrow() }

    suspend fun createCategory(
        groupId: String,
        request: CreateCategoryRequest,
    ): Result<CategoryInfo> =
        apiCall { client.post("api/v1/groups/$groupId/categories") { setBody(request) }.bodyOrThrow() }

    suspend fun updateCategory(
        groupId: String,
        categoryId: String,
        request: UpdateCategoryRequest,
    ): Result<CategoryInfo> =
        apiCall { client.patch("api/v1/groups/$groupId/categories/$categoryId") { setBody(request) }.bodyOrThrow() }

    suspend fun moveCategory(
        groupId: String,
        categoryId: String,
        request: MoveCategoryRequest,
    ): Result<CategoryInfo> =
        apiCall {
            client
                .put(
                    "api/v1/groups/$groupId/categories/$categoryId/parent",
                ) { setBody(request) }
                .bodyOrThrow()
        }

    suspend fun categoryDeleteImpact(
        groupId: String,
        categoryId: String,
    ): Result<CategoryDeleteImpact> =
        apiCall { client.get("api/v1/groups/$groupId/categories/$categoryId/delete-impact").bodyOrThrow() }

    suspend fun deleteCategory(
        groupId: String,
        categoryId: String,
    ): Result<Unit> =
        apiCall {
            client
                .delete(
                    "api/v1/groups/$groupId/categories/$categoryId",
                ) { parameter("confirm", true) }
                .bodyOrThrow<Unit>()
        }
}
