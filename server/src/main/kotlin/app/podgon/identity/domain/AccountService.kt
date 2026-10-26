package app.podgon.identity.domain

import app.podgon.common.ApiException
import app.podgon.common.validationFailed
import app.podgon.contract.DeleteAccountRequest
import app.podgon.contract.ErrorCode
import app.podgon.contract.FieldError
import app.podgon.contract.Validation
import app.podgon.identity.UserDeleting
import app.podgon.identity.persistence.RefreshTokenRepository
import app.podgon.identity.persistence.UserRepository
import app.podgon.mail.MailTemplate
import org.springframework.context.ApplicationEventPublisher
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import java.time.Clock
import java.util.UUID

/**
 * Удаление аккаунта. Подтверждение — одноразовый код из письма (тот же механизм, что и вход). Связи
 * пользователя снимают обработчики события [UserDeleting] (группы, рекомендации) в той же транзакции:
 * если любой из них откажет (например, единственный администратор), удаление откатывается целиком.
 */
@Service
class AccountService(
    private val users: UserRepository,
    private val otp: OtpService,
    private val tokens: RefreshTokenRepository,
    private val events: ApplicationEventPublisher,
    private val clock: Clock,
    private val tx: TransactionTemplate,
) {
    fun requestCode(userId: UUID) {
        val user = users.findById(userId) ?: throw unauthorized()
        otp.requestForSignedIn(user.email, user.locale, MailTemplate.ACCOUNT_DELETION_CODE)
    }

    fun delete(
        userId: UUID,
        request: DeleteAccountRequest,
    ) {
        Validation.code(request.code)?.let { throw validationFailed(listOf(FieldError("code", it))) }
        val user = users.findById(userId) ?: throw unauthorized()
        if (!otp.verify(user.email, request.code)) throw ApiException(ErrorCode.INVALID_CODE, HttpStatus.BAD_REQUEST)
        tx.executeWithoutResult {
            val now = clock.instant()
            events.publishEvent(UserDeleting(userId, request.deleteReviews))
            tokens.revokeAllOfUser(userId, now)
            users.anonymize(userId, now)
        }
    }

    private fun unauthorized() = ApiException(ErrorCode.UNAUTHORIZED, HttpStatus.UNAUTHORIZED)
}
