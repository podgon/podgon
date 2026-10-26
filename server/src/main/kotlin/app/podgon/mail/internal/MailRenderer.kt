package app.podgon.mail.internal

import app.podgon.mail.MailTemplate
import org.springframework.stereotype.Component

data class RenderedMail(
    val subject: String,
    val body: String,
)

/** Простые текстовые шаблоны писем на русском и английском; незнакомая локаль → английский. */
@Component
class MailRenderer {
    fun render(
        template: MailTemplate,
        locale: String,
        params: Map<String, String>,
    ): RenderedMail =
        when (template) {
            MailTemplate.LOGIN_CODE -> loginCode(locale, params.getValue("code"), params.getValue("ttlMinutes"))
        }

    private fun loginCode(
        locale: String,
        code: String,
        ttlMinutes: String,
    ): RenderedMail =
        if (locale == "ru") {
            RenderedMail(
                subject = "Код входа в Podgon",
                body =
                    "Ваш код входа: $code\n\n" +
                        "Он действует $ttlMinutes минут. Никому его не сообщайте.\n" +
                        "Если вы не запрашивали код, просто проигнорируйте это письмо.",
            )
        } else {
            RenderedMail(
                subject = "Your Podgon sign-in code",
                body =
                    "Your sign-in code: $code\n\n" +
                        "It is valid for $ttlMinutes minutes. Do not share it with anyone.\n" +
                        "If you did not request a code, just ignore this email.",
            )
        }
}
