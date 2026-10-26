package app.podgon.contract

import kotlinx.serialization.Serializable

/**
 * Ошибка API в формате application/problem+json (RFC 9457) с машинным кодом.
 * [code] оставлен строкой: клиент должен переживать коды, которых не знает (см. [ErrorCode.fromCode]).
 */
@Serializable
data class ApiProblem(
    val status: Int,
    val code: String? = null,
    val title: String? = null,
    val detail: String? = null,
    val errors: List<FieldError> = emptyList(),
)
