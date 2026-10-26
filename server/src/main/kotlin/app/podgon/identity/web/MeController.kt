package app.podgon.identity.web

import app.podgon.common.ApiException
import app.podgon.contract.DeleteAccountRequest
import app.podgon.contract.ErrorCode
import app.podgon.contract.SessionInfo
import app.podgon.contract.UpdateProfileRequest
import app.podgon.contract.UserProfile
import app.podgon.identity.AuthenticatedUser
import app.podgon.identity.domain.AccountService
import app.podgon.identity.domain.ProfileService
import app.podgon.identity.domain.SessionService
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/** Профиль, сессии и удаление аккаунта текущего пользователя. */
@RestController
@RequestMapping("/api/v1/me")
class MeController(
    private val profiles: ProfileService,
    private val sessions: SessionService,
    private val accounts: AccountService,
) {
    @GetMapping
    fun me(
        @AuthenticationPrincipal user: AuthenticatedUser,
    ): UserProfile = profiles.get(user.userId)

    @PatchMapping
    fun update(
        @AuthenticationPrincipal user: AuthenticatedUser,
        @RequestBody body: UpdateProfileRequest,
    ): UserProfile = profiles.update(user.userId, body)

    @GetMapping("/sessions")
    fun sessions(
        @AuthenticationPrincipal user: AuthenticatedUser,
    ): List<SessionInfo> = sessions.list(user.userId, user.sessionId)

    @DeleteMapping("/sessions/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun revokeSession(
        @AuthenticationPrincipal user: AuthenticatedUser,
        @PathVariable id: String,
    ) {
        val sessionId =
            runCatching { UUID.fromString(id) }.getOrNull()
                ?: throw ApiException(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND)
        sessions.revoke(user.userId, sessionId)
    }

    @PostMapping("/deletion/code")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun requestDeletionCode(
        @AuthenticationPrincipal user: AuthenticatedUser,
    ) = accounts.requestCode(user.userId)

    @PostMapping("/deletion")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteAccount(
        @AuthenticationPrincipal user: AuthenticatedUser,
        @RequestBody body: DeleteAccountRequest,
    ) = accounts.delete(user.userId, body)
}
