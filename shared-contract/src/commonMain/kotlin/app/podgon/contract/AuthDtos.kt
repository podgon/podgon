package app.podgon.contract

/** Запрос одноразового кода входа. [captchaToken] выдаёт виджет SmartCaptcha на клиенте. */
data class OtpRequest(
    val email: String,
    val captchaToken: String? = null,
)

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
data class OtpVerifyResponse(
    val tokens: TokenPair? = null,
    val registrationToken: String? = null,
)

data class RegisterRequest(
    val registrationToken: String,
    val username: String,
    val displayName: String,
    val deviceInfo: String? = null,
)

data class TokenPair(
    val accessToken: String,
    val refreshToken: String,
    /** Срок жизни access-токена в секундах. */
    val expiresIn: Long,
)

data class RefreshRequest(
    val refreshToken: String,
)

data class LogoutRequest(
    val refreshToken: String,
)
