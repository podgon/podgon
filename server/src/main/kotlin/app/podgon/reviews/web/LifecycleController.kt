package app.podgon.reviews.web

import app.podgon.common.ApiException
import app.podgon.contract.AccountDeleteImpact
import app.podgon.contract.ErrorCode
import app.podgon.contract.GroupDeleteImpact
import app.podgon.contract.LeaveImpact
import app.podgon.contract.SharePreview
import app.podgon.contract.SharePreviewRequest
import app.podgon.identity.AuthenticatedUser
import app.podgon.reviews.domain.LifecycleService
import app.podgon.reviews.domain.SharingService
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/** Оценка последствий (выход, удаление группы и аккаунта) и предпросмотр переноса при вступлении. */
@RestController
@RequestMapping("/api/v1")
class LifecycleController(
    private val lifecycle: LifecycleService,
    private val sharing: SharingService,
) {
    @GetMapping("/groups/{groupId}/delete-impact")
    fun groupDeleteImpact(
        @AuthenticationPrincipal user: AuthenticatedUser,
        @PathVariable groupId: String,
    ): GroupDeleteImpact = lifecycle.groupDeleteImpact(user.userId, groupId.uuid())

    @GetMapping("/groups/{groupId}/leave-impact")
    fun leaveImpact(
        @AuthenticationPrincipal user: AuthenticatedUser,
        @PathVariable groupId: String,
    ): LeaveImpact = lifecycle.leaveImpact(user.userId, groupId.uuid())

    @GetMapping("/me/delete-impact")
    fun accountDeleteImpact(
        @AuthenticationPrincipal user: AuthenticatedUser,
    ): AccountDeleteImpact = lifecycle.accountDeleteImpact(user.userId)

    @PostMapping("/invitations/share-preview")
    fun sharePreview(
        @AuthenticationPrincipal user: AuthenticatedUser,
        @RequestBody body: SharePreviewRequest,
    ): SharePreview = sharing.preview(user.userId, body)

    private fun String.uuid(): UUID =
        runCatching { UUID.fromString(this) }.getOrNull()
            ?: throw ApiException(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND)
}
