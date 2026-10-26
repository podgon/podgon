package app.podgon.mail.internal

import app.podgon.mail.MailSender
import org.springframework.mail.SimpleMailMessage
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.stereotype.Component

/** Отправка через SMTP (локально — Mailpit; в продакшене — выбранный почтовый провайдер). */
@Component
class SmtpMailSender(
    private val mailSender: JavaMailSender,
    private val props: MailProperties,
) : MailSender {
    override fun send(
        to: String,
        subject: String,
        body: String,
    ) {
        val message = SimpleMailMessage()
        message.from = props.from
        message.setTo(to)
        message.subject = subject
        message.text = body
        mailSender.send(message)
    }
}
