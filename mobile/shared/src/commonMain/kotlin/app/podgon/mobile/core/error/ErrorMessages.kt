package app.podgon.mobile.core.error

import androidx.compose.runtime.Composable
import app.podgon.contract.ErrorCode
import app.podgon.mobile.resources.Res
import app.podgon.mobile.resources.error_already_member
import app.podgon.mobile.resources.error_blocked
import app.podgon.mobile.resources.error_captcha
import app.podgon.mobile.resources.error_email_taken
import app.podgon.mobile.resources.error_forbidden
import app.podgon.mobile.resources.error_invalid_code
import app.podgon.mobile.resources.error_invalid_invitation
import app.podgon.mobile.resources.error_last_admin
import app.podgon.mobile.resources.error_limit
import app.podgon.mobile.resources.error_network
import app.podgon.mobile.resources.error_not_found
import app.podgon.mobile.resources.error_rate_limited
import app.podgon.mobile.resources.error_registration_expired
import app.podgon.mobile.resources.error_unknown
import app.podgon.mobile.resources.error_username_taken
import org.jetbrains.compose.resources.stringResource

/** Локализованный текст ошибки. Тексты не приходят с сервера: он отдаёт только машинный код. */
@Composable
fun AppError.message(): String =
    when (this) {
        AppError.Network -> stringResource(Res.string.error_network)
        AppError.Unknown -> stringResource(Res.string.error_unknown)
        is AppError.Api ->
            when (code) {
                ErrorCode.INVALID_CODE -> stringResource(Res.string.error_invalid_code)
                ErrorCode.RATE_LIMITED -> stringResource(Res.string.error_rate_limited, retryAfterSeconds ?: 60L)
                ErrorCode.CAPTCHA_FAILED -> stringResource(Res.string.error_captcha)
                ErrorCode.USERNAME_TAKEN -> stringResource(Res.string.error_username_taken)
                ErrorCode.EMAIL_ALREADY_REGISTERED -> stringResource(Res.string.error_email_taken)
                ErrorCode.INVALID_REGISTRATION_TOKEN -> stringResource(Res.string.error_registration_expired)
                ErrorCode.USER_BLOCKED -> stringResource(Res.string.error_blocked)
                ErrorCode.LAST_ADMIN -> stringResource(Res.string.error_last_admin)
                ErrorCode.INVALID_INVITATION -> stringResource(Res.string.error_invalid_invitation)
                ErrorCode.ALREADY_MEMBER -> stringResource(Res.string.error_already_member)
                ErrorCode.LIMIT_EXCEEDED -> stringResource(Res.string.error_limit)
                ErrorCode.FORBIDDEN -> stringResource(Res.string.error_forbidden)
                ErrorCode.NOT_FOUND -> stringResource(Res.string.error_not_found)
                else -> stringResource(Res.string.error_unknown)
            }
    }
