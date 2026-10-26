package app.podgon.identity.domain

import app.podgon.common.ApiException
import app.podgon.common.validationFailed
import app.podgon.contract.ErrorCode
import app.podgon.contract.FieldError
import app.podgon.contract.FieldErrorReason
import app.podgon.contract.UpdateProfileRequest
import app.podgon.contract.UserProfile
import app.podgon.contract.Validation
import app.podgon.identity.persistence.UserRepository
import app.podgon.identity.persistence.UserRow
import org.springframework.dao.DuplicateKeyException
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import java.time.Clock
import java.util.UUID

@Service
class ProfileService(
    private val users: UserRepository,
    private val clock: Clock,
) {
    fun get(userId: UUID): UserProfile =
        (
            users.findById(userId)
                ?: throw ApiException(ErrorCode.UNAUTHORIZED, HttpStatus.UNAUTHORIZED)
        ).toProfile()

    /** Частичное обновление: `null` в запросе — поле не меняется. */
    fun update(
        userId: UUID,
        request: UpdateProfileRequest,
    ): UserProfile {
        val errors =
            listOfNotNull(
                request.username?.let { v -> Validation.username(v)?.let { FieldError("username", it) } },
                request.displayName?.let { v -> Validation.displayName(v)?.let { FieldError("displayName", it) } },
                request.bio?.let { v -> Validation.bio(v)?.let { FieldError("bio", it) } },
                request.locale?.let { v ->
                    if (v in
                        LOCALES
                    ) {
                        null
                    } else {
                        FieldError("locale", FieldErrorReason.INVALID_FORMAT)
                    }
                },
            )
        if (errors.isNotEmpty()) throw validationFailed(errors)

        val current = users.findById(userId) ?: throw ApiException(ErrorCode.UNAUTHORIZED, HttpStatus.UNAUTHORIZED)
        try {
            users.updateProfile(
                id = userId,
                username = request.username ?: current.username,
                displayName = request.displayName?.trim() ?: current.displayName,
                bio = request.bio?.trim() ?: current.bio,
                locale = request.locale ?: current.locale,
                now = clock.instant(),
            )
        } catch (e: DuplicateKeyException) {
            throw ApiException(ErrorCode.USERNAME_TAKEN, HttpStatus.CONFLICT)
        }
        return get(userId)
    }

    private fun UserRow.toProfile() = UserProfile(id.toString(), email, username, displayName, bio, locale)

    private companion object {
        val LOCALES = setOf("ru", "en")
    }
}
