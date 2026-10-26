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
            MailTemplate.GROUP_INVITATION ->
                groupInvitation(
                    locale,
                    params.getValue("groupName"),
                    params.getValue("inviter"),
                    params.getValue("token"),
                    params.getValue("ttlHours"),
                )
        }

    private fun groupInvitation(
        locale: String,
        groupName: String,
        inviter: String,
        token: String,
        ttlHours: String,
    ): RenderedMail =
        if (locale == "ru") {
            RenderedMail(
                subject = "Приглашение в группу «$groupName» в Podgon",
                body =
                    "$inviter приглашает вас в группу «$groupName».\n\n" +
                        "Код приглашения: $token\n\n" +
                        "В приложении Podgon откройте «Группы» → «Вступить по коду» и введите этот код. " +
                        "Он действует $ttlHours ч. и подходит только для этого адреса почты.",
            )
        } else {
            RenderedMail(
                subject = "You are invited to the group \"$groupName\" on Podgon",
                body =
                    "$inviter invites you to the group \"$groupName\".\n\n" +
                        "Invitation code: $token\n\n" +
                        "In the Podgon app open Groups → Join by code and enter this code. " +
                        "It is valid for $ttlHours hours and works only for this email address.",
            )
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
