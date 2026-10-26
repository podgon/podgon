package app.podgon.contract

import kotlinx.serialization.Serializable

/** Ошибка проверки одного поля: имя поля и причина. */
@Serializable
data class FieldError(
    val field: String,
    val reason: FieldErrorReason,
)

enum class FieldErrorReason { REQUIRED, TOO_SHORT, TOO_LONG, INVALID_FORMAT, RESERVED }

/**
 * Правила валидации, общие для сервера и клиента. Сервер — источник правды,
 * клиент использует те же функции для быстрой подсказки в форме.
 * Каждая функция возвращает причину ошибки либо `null`, если значение допустимо.
 */
object Validation {
    const val EMAIL_MAX = 254
    const val USERNAME_MIN = 3
    const val USERNAME_MAX = 30
    const val DISPLAY_NAME_MAX = 60
    const val BIO_MAX = 120
    const val CODE_LENGTH = 6

    /** Префикс зарезервирован для анонимизированных (удалённых) пользователей. */
    const val RESERVED_USERNAME_PREFIX = "deleted_"

    private val emailRegex = Regex("^[^\\s@]+@[^\\s@.]+(\\.[^\\s@.]+)+$")
    private val usernameRegex = Regex("^[A-Za-z0-9_.]+$")

    /** Приводит email к виду для хранения и сравнения: без пробелов по краям, в нижнем регистре. */
    fun normalizeEmail(email: String): String = email.trim().lowercase()

    fun email(value: String): FieldErrorReason? {
        val v = value.trim()
        return when {
            v.isEmpty() -> FieldErrorReason.REQUIRED
            v.length > EMAIL_MAX -> FieldErrorReason.TOO_LONG
            !emailRegex.matches(v) -> FieldErrorReason.INVALID_FORMAT
            else -> null
        }
    }

    fun username(value: String): FieldErrorReason? =
        when {
            value.isEmpty() -> FieldErrorReason.REQUIRED
            value.length < USERNAME_MIN -> FieldErrorReason.TOO_SHORT
            value.length > USERNAME_MAX -> FieldErrorReason.TOO_LONG
            !usernameRegex.matches(value) -> FieldErrorReason.INVALID_FORMAT
            value.startsWith(RESERVED_USERNAME_PREFIX, ignoreCase = true) -> FieldErrorReason.RESERVED
            else -> null
        }

    fun displayName(value: String): FieldErrorReason? =
        when {
            value.isBlank() -> FieldErrorReason.REQUIRED
            value.length > DISPLAY_NAME_MAX -> FieldErrorReason.TOO_LONG
            value.any { it.isISOControl() } -> FieldErrorReason.INVALID_FORMAT
            else -> null
        }

    /** Bio необязательно: пустая строка допустима. */
    fun bio(value: String): FieldErrorReason? =
        when {
            value.length > BIO_MAX -> FieldErrorReason.TOO_LONG
            value.any { it.isISOControl() && it != '\n' } -> FieldErrorReason.INVALID_FORMAT
            else -> null
        }

    fun code(value: String): FieldErrorReason? =
        when {
            value.isEmpty() -> FieldErrorReason.REQUIRED
            value.length != CODE_LENGTH || !value.all { it in '0'..'9' } -> FieldErrorReason.INVALID_FORMAT
            else -> null
        }
}
