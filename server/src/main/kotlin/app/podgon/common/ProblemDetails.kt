package app.podgon.common

import app.podgon.contract.ErrorCode
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.HttpStatusCode
import org.springframework.http.MediaType
import org.springframework.http.ProblemDetail
import tools.jackson.databind.json.JsonMapper

/** Единый формат ошибки (RFC 9457) с машинным кодом, не привязанным к языку. */
fun problem(
    status: HttpStatusCode,
    code: ErrorCode,
): ProblemDetail {
    val problem = ProblemDetail.forStatus(status)
    HttpStatus.resolve(status.value())?.let { problem.title = it.reasonPhrase }
    problem.setProperty("code", code.name)
    return problem
}

/** Ответ-ошибка из фильтра (вне Spring MVC, где работает [ProblemAdvice]). */
fun HttpServletResponse.writeProblem(
    json: JsonMapper,
    status: HttpStatus,
    code: ErrorCode,
    retryAfterSeconds: Long? = null,
) {
    this.status = status.value()
    contentType = MediaType.APPLICATION_PROBLEM_JSON_VALUE
    characterEncoding = "UTF-8"
    retryAfterSeconds?.let { setHeader(HttpHeaders.RETRY_AFTER, it.toString()) }
    json.writeValue(outputStream, problem(status, code))
}
