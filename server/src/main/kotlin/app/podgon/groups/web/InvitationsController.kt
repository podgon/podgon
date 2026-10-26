package app.podgon.groups.web

import app.podgon.contract.AcceptInvitationRequest
import app.podgon.contract.CreateInvitationRequest
import app.podgon.contract.GroupSummary
import app.podgon.contract.InvitationCreated
import app.podgon.contract.InvitationInfo
import app.podgon.contract.InvitationPreview
import app.podgon.contract.InvitationTokenRequest
import app.podgon.groups.domain.InvitationService
import app.podgon.identity.AuthenticatedUser
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1")
class InvitationsController(
    private val invitations: InvitationService,
) {
    @PostMapping("/groups/{groupId}/invitations")
    @ResponseStatus(HttpStatus.CREATED)
    fun create(
        @AuthenticationPrincipal user: AuthenticatedUser,
        @PathVariable groupId: String,
        @RequestBody body: CreateInvitationRequest,
    ): InvitationCreated = invitations.create(user.userId, groupId.toUuidOrNotFound(), body)

    @GetMapping("/groups/{groupId}/invitations")
    fun list(
        @AuthenticationPrincipal user: AuthenticatedUser,
        @PathVariable groupId: String,
    ): List<InvitationInfo> = invitations.list(user.userId, groupId.toUuidOrNotFound())

    @DeleteMapping("/groups/{groupId}/invitations/{invitationId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun revoke(
        @AuthenticationPrincipal user: AuthenticatedUser,
        @PathVariable groupId: String,
        @PathVariable invitationId: String,
    ) = invitations.revoke(user.userId, groupId.toUuidOrNotFound(), invitationId.toUuidOrNotFound())

    @PostMapping("/invitations/preview")
    fun preview(
        @AuthenticationPrincipal user: AuthenticatedUser,
        @RequestBody body: InvitationTokenRequest,
    ): InvitationPreview = invitations.preview(user.userId, body.token)

    @PostMapping("/invitations/accept")
    fun accept(
        @AuthenticationPrincipal user: AuthenticatedUser,
        @RequestBody body: AcceptInvitationRequest,
    ): GroupSummary = invitations.accept(user.userId, body)
}
