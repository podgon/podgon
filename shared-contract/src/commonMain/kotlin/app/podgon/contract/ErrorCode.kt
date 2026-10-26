package app.podgon.contract

/**
 * Машинные коды ошибок API (поле `code` в ответе application/problem+json).
 * Не привязаны к языку: текст для пользователя клиент берёт из своей локализации.
 */
enum class ErrorCode {
    /** Тело или параметры запроса не прошли проверку; детали — в `errors`. */
    VALIDATION_FAILED,

    /** Не пройдена CAPTCHA. */
    CAPTCHA_FAILED,

    /** Слишком частые запросы; повторить можно через `Retry-After` секунд. */
    RATE_LIMITED,

    /** Код неверен, просрочен, уже использован или исчерпаны попытки. */
    INVALID_CODE,

    /** Регистрационный токен неверен или просрочен. */
    INVALID_REGISTRATION_TOKEN,

    /** Refresh-токен неверен, просрочен или отозван. */
    INVALID_REFRESH_TOKEN,

    /** Нет или недействителен access-токен. */
    UNAUTHORIZED,

    /** Действие запрещено. */
    FORBIDDEN,

    /** Пользователь заблокирован. */
    USER_BLOCKED,

    /** Ресурс не найден (в том числе чужой — существование не раскрываем). */
    NOT_FOUND,

    /** Имя пользователя занято. */
    USERNAME_TAKEN,

    /** Пользователь с таким email уже зарегистрирован. */
    EMAIL_ALREADY_REGISTERED,

    /** Непредвиденная ошибка сервера. */
    INTERNAL_ERROR,
}
