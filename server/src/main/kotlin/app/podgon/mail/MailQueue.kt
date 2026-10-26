package app.podgon.mail

/**
 * Публичный вход модуля почты: письмо кладётся в очередь (outbox) в той же транзакции,
 * что и бизнес-операция, а отправляется фоновым обработчиком с повторами.
 */
interface MailQueue {
    /** [params] могут содержать секреты (код входа): после успешной отправки они стираются. */
    fun enqueue(
        to: String,
        template: MailTemplate,
        locale: String,
        params: Map<String, String>,
    )
}

/** Порт отправки одного письма (SMTP или API провайдера); в тестах подменяется. */
interface MailSender {
    fun send(
        to: String,
        subject: String,
        body: String,
    )
}
