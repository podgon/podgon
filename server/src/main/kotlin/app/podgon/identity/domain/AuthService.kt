package app.podgon.identity.domain

import app.podgon.common.ApiException
import app.podgon.common.validationFailed
import app.podgon.contract.ErrorCode
import app.podgon.contract.FieldError
import app.podgon.contract.OtpVerifyResponse
import app.podgon.contract.TokenPair
import app.podgon.contract.Validation
import app.podgon.identity.persistence.UserRepository
import app.podgon.identity.persistence.UserRow
import org.springframework.dao.DuplicateKeyException
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import java.time.Clock
import java.util.UUID

/** Сценарии входа и регистрации поверх проверки кода и выдачи сессий. */
@Service
class AuthService(
    private val otp: OtpService,
    private val users: UserRepository,
    private val sessions: SessionService,
    private val tokenService: TokenService,
    private val clock: Clock,
) {
    /** Код верен → токены существующего пользователя либо токен для завершения регистрации. */
    fun verifyOtp(
        email: String,
        code: String,
        deviceInfo: String?,
    ): OtpVerifyResponse {
        if (!otp.verify(email, code)) throw ApiException(ErrorCode.INVALID_CODE, HttpStatus.BAD_REQUEST)
        val normalized = Validation.normalizeEmail(email)
        val user = users.findLoginableByEmail(normalized)
        return when {
            user == null -> OtpVerifyResponse(registrationToken = tokenService.issueRegistrationToken(normalized))
            user.status == "BLOCKED" -> throw ApiException(ErrorCode.USER_BLOCKED, HttpStatus.FORBIDDEN)
            else -> OtpVerifyResponse(tokens = sessions.start(user.id, deviceInfo))
        }
    }

    fun register(
        registrationToken: String,
        username: String,
        displayName: String,
        locale: String,
        deviceInfo: String?,
    ): TokenPair {
        val errors =
            listOfNotNull(
                Validation.username(username)?.let { FieldError("username", it) },
                Validation.displayName(displayName)?.let { FieldError("displayName", it) },
            )
        if (errors.isNotEmpty()) throw validationFailed(errors)
        val email =
            tokenService.parseRegistrationToken(registrationToken)
                ?: throw ApiException(ErrorCode.INVALID_REGISTRATION_TOKEN, HttpStatus.UNAUTHORIZED)

        val user =
            UserRow(
                id = UUID.randomUUID(),
                email = email,
                username = username,
                displayName = displayName.trim(),
                bio = "",
                status = "ACTIVE",
                locale = locale,
            )
        try {
            users.insert(user, clock.instant())
        } catch (e: DuplicateKeyException) {
            throw duplicate(e)
        }
        return sessions.start(user.id, deviceInfo)
    }

    private fun duplicate(e: DuplicateKeyException): ApiException =
        if (e.message.orEmpty().contains("users_email_uq")) {
            ApiException(ErrorCode.EMAIL_ALREADY_REGISTERED, HttpStatus.CONFLICT)
        } else {
            ApiException(ErrorCode.USERNAME_TAKEN, HttpStatus.CONFLICT)
        }
}
