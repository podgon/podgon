package app.podgon.common

import app.podgon.contract.ErrorCode
import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.HttpStatusCode
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.context.request.WebRequest
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler

/** Преобразует исключения в ответы application/problem+json с полем `code`. */
@RestControllerAdvice
class ProblemAdvice : ResponseEntityExceptionHandler() {
    private val log = LoggerFactory.getLogger(ProblemAdvice::class.java)

    @ExceptionHandler(ApiException::class)
    fun handleApi(ex: ApiException): ResponseEntity<ProblemDetail> {
        val body = problem(ex.status, ex.code)
        if (ex.fieldErrors.isNotEmpty()) {
            body.setProperty("errors", ex.fieldErrors.map { mapOf("field" to it.field, "reason" to it.reason.name) })
        }
        val response = ResponseEntity.status(ex.status)
        if (ex is RateLimitedException) response.header(HttpHeaders.RETRY_AFTER, ex.retryAfterSeconds.toString())
        return response.body(body)
    }

    @ExceptionHandler(Exception::class)
    fun handleUnexpected(ex: Exception): ResponseEntity<ProblemDetail> {
        log.error("Необработанная ошибка", ex)
        return ResponseEntity
            .status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(problem(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.INTERNAL_ERROR))
    }

    /** Стандартные ошибки Spring MVC (неверный JSON, метод, путь) получают тот же формат с кодом. */
    override fun handleExceptionInternal(
        ex: Exception,
        body: Any?,
        headers: HttpHeaders,
        statusCode: HttpStatusCode,
        request: WebRequest,
    ): ResponseEntity<Any>? {
        val response = super.handleExceptionInternal(ex, body, headers, statusCode, request)
        val detail = response?.body as? ProblemDetail
        if (detail != null && detail.properties?.containsKey("code") != true) {
            detail.setProperty("code", codeFor(statusCode).name)
        }
        return response
    }

    private fun codeFor(status: HttpStatusCode): ErrorCode =
        when (status.value()) {
            400 -> ErrorCode.VALIDATION_FAILED
            404 -> ErrorCode.NOT_FOUND
            401 -> ErrorCode.UNAUTHORIZED
            403 -> ErrorCode.FORBIDDEN
            else -> ErrorCode.INTERNAL_ERROR
        }
}
