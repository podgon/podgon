package app.podgon.mail

/** Типы писем. Тексты на ru/en лежат в [app.podgon.mail.internal.MailRenderer]. */
enum class MailTemplate {
    /** Одноразовый код входа. Параметры: `code`, `ttlMinutes`. */
    LOGIN_CODE,
}
