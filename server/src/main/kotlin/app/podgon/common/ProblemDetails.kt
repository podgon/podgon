package app.podgon.common

import app.podgon.contract.ErrorCode
import org.springframework.http.HttpStatus
import org.springframework.http.HttpStatusCode
import org.springframework.http.ProblemDetail

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
