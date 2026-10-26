package app.podgon.identity

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

/** Настройки входа по коду (ADR-006). Секреты задаются только через окружение. */
@ConfigurationProperties("podgon.auth")
data class AuthProperties(
    /** Секрет HMAC для хранения кодов; не менее 32 символов. */
    val codeHmacSecret: String = "",
    val codeTtl: Duration = Duration.ofMinutes(10),
    val codeMaxAttempts: Int = 5,
    val resendInterval: Duration = Duration.ofSeconds(60),
    val maxCodesPerEmailPerHour: Int = 5,
    val maxCodesPerIpPerHour: Int = 20,
    val issuer: String = "podgon",
    val accessTtl: Duration = Duration.ofMinutes(15),
    val refreshTtl: Duration = Duration.ofDays(30),
    /** Предельный возраст сессии: после него нужен новый вход по коду, даже если refresh регулярно обновлялся. */
    val sessionMaxAge: Duration = Duration.ofDays(90),
    val registrationTtl: Duration = Duration.ofMinutes(15),
    /** Идентификатор ключа подписи JWT, которым подписываются новые токены. */
    val jwtActiveKid: String = "",
    /** Ключи подписи: kid → секрет в Base64 (не менее 32 байт). Старые ключи остаются для проверки при ротации. */
    val jwtKeys: Map<String, String> = emptyMap(),
    val captcha: Captcha = Captcha(),
    val testAccount: TestAccount = TestAccount(),
) {
    data class Captcha(
        /** `smartcaptcha` — проверка через Яндекс SmartCaptcha; `disabled` — только для локальной разработки и тестов. */
        val mode: String = "smartcaptcha",
        val secret: String = "",
        val validateUrl: String = "https://smartcaptcha.cloud.yandex.ru/validate",
        val timeout: Duration = Duration.ofSeconds(3),
    )

    /** Тестовый аккаунт для модерации магазинов: для одного email код фиксирован. По умолчанию выключен. */
    data class TestAccount(
        val enabled: Boolean = false,
        val email: String = "",
        val code: String = "",
    )
}
