package app.podgon.mobile.feature.auth.data

import app.podgon.mobile.core.auth.SessionManager
import app.podgon.mobile.core.network.AuthApi

/** Результат проверки кода. */
sealed interface VerifyOutcome {
    /** Аккаунт существует, вход выполнен. */
    data object SignedIn : VerifyOutcome

    /** Email подтверждён, но аккаунта нет: нужно завершить регистрацию. */
    data class RegistrationRequired(
        val registrationToken: String,
    ) : VerifyOutcome
}

/** Описание устройства для списка сессий («Pixel 8, Android 15»). */
fun interface DeviceInfoProvider {
    fun deviceInfo(): String?
}

/**
 * Токен CAPTCHA для запроса кода. Виджет Яндекс SmartCaptcha (WebView) подключается на этапе 9
 * вместе с финальными лимитами антибота; до этого сервер в dev-окружении работает с отключённой проверкой.
 */
fun interface CaptchaProvider {
    suspend fun token(): String?
}

class AuthRepository(
    private val api: AuthApi,
    private val session: SessionManager,
    private val captcha: CaptchaProvider,
    private val device: DeviceInfoProvider,
) {
    suspend fun requestCode(email: String): Result<Unit> = api.requestOtp(email.trim(), captcha.token())

    suspend fun verify(
        email: String,
        code: String,
    ): Result<VerifyOutcome> =
        api.verifyOtp(email.trim(), code, device.deviceInfo()).mapCatching { response ->
            val tokens = response.tokens
            if (tokens != null) {
                session.signIn(tokens)
                VerifyOutcome.SignedIn
            } else {
                VerifyOutcome.RegistrationRequired(checkNotNull(response.registrationToken) { "Пустой ответ сервера" })
            }
        }

    suspend fun register(
        registrationToken: String,
        username: String,
        displayName: String,
    ): Result<Unit> =
        api.register(registrationToken, username.trim(), displayName.trim(), device.deviceInfo()).mapCatching {
            session.signIn(it)
        }

    /** Выход: сервер уведомляется по возможности, локальная сессия закрывается в любом случае. */
    suspend fun logout() {
        session.tokens()?.let { api.logout(it.refreshToken) }
        session.signOut()
    }
}
