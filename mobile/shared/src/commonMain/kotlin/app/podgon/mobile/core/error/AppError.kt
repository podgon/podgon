package app.podgon.mobile.core.error

import app.podgon.contract.ErrorCode
import app.podgon.contract.FieldError

/** Типизированная ошибка приложения: из неё UI выбирает локализованное сообщение. */
sealed interface AppError {
    /** Нет связи с сервером или он не ответил вовремя. */
    data object Network : AppError

    /** Ошибка API с машинным кодом; [code] = `null`, если клиент такого кода не знает. */
    data class Api(
        val status: Int,
        val code: ErrorCode?,
        val fieldErrors: List<FieldError> = emptyList(),
        /** Через сколько секунд можно повторить запрос (заголовок Retry-After при 429). */
        val retryAfterSeconds: Long? = null,
    ) : AppError

    data object Unknown : AppError
}

/** Исключение-носитель [AppError] для слоя репозиториев (внутри — `Result`). */
class AppException(
    val error: AppError,
) : RuntimeException(error.toString())

fun Result<*>.appError(): AppError? =
    (exceptionOrNull() as? AppException)?.error ?: exceptionOrNull()?.let { AppError.Unknown }
