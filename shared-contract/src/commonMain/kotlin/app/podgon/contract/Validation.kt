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
    const val GROUP_NAME_MAX = 100
    const val GROUP_DESCRIPTION_MAX = 500
    const val ROLE_TITLE_MAX = 60
    const val ROLE_DESCRIPTION_MAX = 255
    const val CATEGORY_NAME_MAX = 100
    const val CATEGORY_DESCRIPTION_MAX = 500
    const val REVIEW_TITLE_MAX = 200
    const val REVIEW_DESCRIPTION_MAX = 5000
    const val RATING_MIN = 1
    const val RATING_MAX = 10
    const val TAGS_MAX = 10
    const val TAG_MAX = 30
    const val SEARCH_QUERY_MAX = 100
    const val SEARCH_CATEGORIES_MAX = 20
    const val SEARCH_GROUPS_MAX = 50
    const val INVITATION_MAX_USES = 100
    const val INVITATION_TTL_HOURS_DEFAULT = 168
    const val INVITATION_TTL_HOURS_MAX = 24 * 30

    /** Префикс зарезервирован для анонимизированных (удалённых) пользователей. */
    const val RESERVED_USERNAME_PREFIX = "deleted_"

    private val emailRegex = Regex("^[^\\s@]+@[^\\s@.]+(\\.[^\\s@.]+)+$")
    private val tagRegex = Regex("^[\\p{L}\\p{N}_-]+$")
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

    fun groupName(value: String): FieldErrorReason? = title(value, GROUP_NAME_MAX)

    fun groupDescription(value: String): FieldErrorReason? = text(value, GROUP_DESCRIPTION_MAX)

    fun categoryName(value: String): FieldErrorReason? = title(value, CATEGORY_NAME_MAX)

    fun categoryDescription(value: String): FieldErrorReason? = text(value, CATEGORY_DESCRIPTION_MAX)

    fun reviewTitle(value: String): FieldErrorReason? = title(value, REVIEW_TITLE_MAX)

    fun reviewDescription(value: String): FieldErrorReason? = text(value, REVIEW_DESCRIPTION_MAX)

    fun rating(value: Int): FieldErrorReason? =
        when {
            value < RATING_MIN -> FieldErrorReason.TOO_SHORT
            value > RATING_MAX -> FieldErrorReason.TOO_LONG
            else -> null
        }

    /** Тег хранится без `#`, в нижнем регистре, без пробелов по краям. */
    fun normalizeTag(value: String): String = value.trim().removePrefix("#").lowercase()

    /** Проверяет уже нормализованный ([normalizeTag]) тег. */
    fun tag(value: String): FieldErrorReason? =
        when {
            value.isEmpty() -> FieldErrorReason.REQUIRED
            value.length > TAG_MAX -> FieldErrorReason.TOO_LONG
            !tagRegex.matches(value) -> FieldErrorReason.INVALID_FORMAT
            else -> null
        }

    /** Проверяет набор тегов (после нормализации и удаления повторов): число и каждый тег. */
    fun tags(values: List<String>): FieldErrorReason? =
        when {
            values.size > TAGS_MAX -> FieldErrorReason.TOO_LONG
            else -> values.firstNotNullOfOrNull { tag(it) }
        }

    /** Поисковый запрос необязателен; пустой после обрезки считается отсутствующим. */
    fun searchQuery(value: String): FieldErrorReason? =
        when {
            value.trim().length > SEARCH_QUERY_MAX -> FieldErrorReason.TOO_LONG
            value.any { it.isISOControl() } -> FieldErrorReason.INVALID_FORMAT
            else -> null
        }

    fun roleTitle(value: String): FieldErrorReason? = title(value, ROLE_TITLE_MAX)

    fun roleDescription(value: String): FieldErrorReason? = text(value, ROLE_DESCRIPTION_MAX)

    fun maxUses(value: Int): FieldErrorReason? =
        when {
            value < 1 -> FieldErrorReason.TOO_SHORT
            value > INVITATION_MAX_USES -> FieldErrorReason.TOO_LONG
            else -> null
        }

    fun ttlHours(value: Int): FieldErrorReason? =
        when {
            value < 1 -> FieldErrorReason.TOO_SHORT
            value > INVITATION_TTL_HOURS_MAX -> FieldErrorReason.TOO_LONG
            else -> null
        }

    /** Однострочное обязательное название. */
    private fun title(
        value: String,
        max: Int,
    ): FieldErrorReason? =
        when {
            value.isBlank() -> FieldErrorReason.REQUIRED
            value.trim().length > max -> FieldErrorReason.TOO_LONG
            value.any { it.isISOControl() } -> FieldErrorReason.INVALID_FORMAT
            else -> null
        }

    /** Необязательный многострочный текст. */
    private fun text(
        value: String,
        max: Int,
    ): FieldErrorReason? =
        when {
            value.trim().length > max -> FieldErrorReason.TOO_LONG
            value.any { it.isISOControl() && it != '\n' } -> FieldErrorReason.INVALID_FORMAT
            else -> null
        }
}
