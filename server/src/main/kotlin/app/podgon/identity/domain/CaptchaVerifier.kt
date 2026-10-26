package app.podgon.identity.domain

import app.podgon.identity.AuthProperties
import org.slf4j.LoggerFactory
import org.springframework.http.client.JdkClientHttpRequestFactory
import org.springframework.web.client.HttpServerErrorException
import org.springframework.web.client.ResourceAccessException
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import java.net.http.HttpClient

/** Проверка токена CAPTCHA, полученного клиентом. */
interface CaptchaVerifier {
    fun verify(
        token: String?,
        ip: String?,
    ): Boolean
}

/** Заглушка для локальной разработки и тестов (`podgon.auth.captcha.mode=disabled`). */
class DisabledCaptchaVerifier : CaptchaVerifier {
    override fun verify(
        token: String?,
        ip: String?,
    ): Boolean = true
}

/**
 * Яндекс SmartCaptcha (ADR-010). Явный отказ сервиса = ошибка; при недоступности самого сервиса
 * (сеть, 5xx) пропускаем запрос, как рекомендует провайдер: защиту продолжают обеспечивать лимиты.
 */
class SmartCaptchaVerifier(
    private val props: AuthProperties.Captcha,
) : CaptchaVerifier {
    private val log = LoggerFactory.getLogger(SmartCaptchaVerifier::class.java)

    private val client: RestClient =
        RestClient
            .builder()
            .requestFactory(
                JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(props.timeout).build()).apply {
                    setReadTimeout(props.timeout)
                },
            ).build()

    override fun verify(
        token: String?,
        ip: String?,
    ): Boolean {
        if (token.isNullOrBlank() || token.length > MAX_TOKEN_LENGTH) return false
        return try {
            // значения передаются как uri-переменные: они кодируются и не интерпретируются как шаблон
            val response =
                client
                    .get()
                    .uri(
                        "${props.validateUrl}?secret={secret}&token={token}&ip={ip}",
                        mapOf("secret" to props.secret, "token" to token, "ip" to (ip ?: "")),
                    ).retrieve()
                    .body(Map::class.java)
            response?.get("status") == "ok"
        } catch (e: ResourceAccessException) {
            failOpen(e)
        } catch (e: HttpServerErrorException) {
            failOpen(e)
        } catch (e: RestClientException) {
            // остальные ошибки (в том числе из-за пользовательского токена) — отказ, а не пропуск
            log.warn("SmartCaptcha: ошибка проверки ({}), запрос отклонён", e.javaClass.simpleName)
            false
        }
    }

    /** Сбой самого сервиса (сеть, 5xx): пропускаем, защиту продолжают обеспечивать лимиты. Токен и секрет в лог не пишем. */
    private fun failOpen(e: Exception): Boolean {
        log.warn("SmartCaptcha недоступна ({}), запрос пропущен", e.javaClass.simpleName)
        return true
    }

    private companion object {
        const val MAX_TOKEN_LENGTH = 4096
    }
}
