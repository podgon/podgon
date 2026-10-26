package app.podgon.common

import app.podgon.contract.ErrorCode
import app.podgon.contract.FieldError
import org.springframework.http.HttpStatus

/** Ожидаемая ошибка API: превращается в ответ application/problem+json с машинным кодом [code]. */
open class ApiException(
    val code: ErrorCode,
    val status: HttpStatus,
    message: String? = null,
    val fieldErrors: List<FieldError> = emptyList(),
) : RuntimeException(message ?: code.name)

/** Ошибки валидации полей запроса. */
fun validationFailed(errors: List<FieldError>): ApiException =
    ApiException(ErrorCode.VALIDATION_FAILED, HttpStatus.BAD_REQUEST, fieldErrors = errors)

/** Превышен лимит запросов; клиенту сообщается, через сколько секунд повторить. */
class RateLimitedException(
    val retryAfterSeconds: Long,
) : ApiException(ErrorCode.RATE_LIMITED, HttpStatus.TOO_MANY_REQUESTS)
