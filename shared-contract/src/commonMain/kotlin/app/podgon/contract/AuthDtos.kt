package app.podgon.contract

import kotlinx.serialization.Serializable

/** Запрос одноразового кода входа. [captchaToken] выдаёт виджет SmartCaptcha на клиенте. */
@Serializable
data class OtpRequest(
    val email: String,
    val captchaToken: String? = null,
)

@Serializable
data class OtpVerifyRequest(
    val email: String,
    val code: String,
    /** Описание устройства для списка сессий (например, «Pixel 8, Android 15»). */
    val deviceInfo: String? = null,
)

/**
 * Результат проверки кода: либо [tokens] (аккаунт существует), либо [registrationToken]
 * (нужно завершить регистрацию). Ровно одно из полей заполнено.
 */
@Serializable
data class OtpVerifyResponse(
    val tokens: TokenPair? = null,
    val registrationToken: String? = null,
)

@Serializable
data class RegisterRequest(
    val registrationToken: String,
    val username: String,
    val displayName: String,
    val deviceInfo: String? = null,
)

@Serializable
data class TokenPair(
    val accessToken: String,
    val refreshToken: String,
    /** Срок жизни access-токена в секундах. */
    val expiresIn: Long,
)

@Serializable
data class RefreshRequest(
    val refreshToken: String,
)

@Serializable
data class LogoutRequest(
    val refreshToken: String,
)
