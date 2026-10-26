package app.podgon.identity.security

import app.podgon.identity.AuthProperties
import app.podgon.identity.domain.CaptchaVerifier
import app.podgon.identity.domain.DisabledCaptchaVerifier
import app.podgon.identity.domain.SmartCaptchaVerifier
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AuthProperties::class, RequestLimitProperties::class)
class IdentityConfig {
    @Bean
    fun captchaVerifier(props: AuthProperties): CaptchaVerifier =
        when (props.captcha.mode) {
            "disabled" -> DisabledCaptchaVerifier()
            "smartcaptcha" -> {
                require(props.captcha.secret.isNotBlank()) { "podgon.auth.captcha.secret не задан" }
                SmartCaptchaVerifier(props.captcha)
            }
            else -> error("Неизвестный podgon.auth.captcha.mode: ${props.captcha.mode}")
        }

    /** Проверка обязательных секретов при старте: приложение не запускается с пустыми или слабыми значениями. */
    @Bean
    fun authConfigurationCheck(props: AuthProperties): AuthConfigurationCheck {
        require(props.codeHmacSecret.length >= 32) { "podgon.auth.code-hmac-secret должен быть не короче 32 символов" }
        with(props.testAccount) {
            if (enabled) {
                require(email.isNotBlank() && code.length == 6 && code.all { it.isDigit() }) {
                    "podgon.auth.test-account: нужны email и 6-значный код"
                }
            }
        }
        return AuthConfigurationCheck
    }

    object AuthConfigurationCheck
}
