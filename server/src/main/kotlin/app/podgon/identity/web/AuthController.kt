package app.podgon.identity.web

import app.podgon.contract.LogoutRequest
import app.podgon.contract.OtpRequest
import app.podgon.contract.OtpVerifyRequest
import app.podgon.contract.OtpVerifyResponse
import app.podgon.contract.RefreshRequest
import app.podgon.contract.RegisterRequest
import app.podgon.contract.TokenPair
import app.podgon.identity.domain.AuthService
import app.podgon.identity.domain.OtpService
import app.podgon.identity.domain.SessionService
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

/** Публичные операции входа. Контракт — `api/openapi.yaml`. */
@RestController
@RequestMapping("/api/v1/auth")
class AuthController(
    private val otp: OtpService,
    private val auth: AuthService,
    private val sessions: SessionService,
) {
    /** Всегда 202 для корректного запроса — независимо от того, есть ли аккаунт с таким email. */
    @PostMapping("/otp/request")
    @ResponseStatus(HttpStatus.ACCEPTED)
    fun requestCode(
        @RequestBody body: OtpRequest,
        request: HttpServletRequest,
    ) {
        otp.request(body.email, body.captchaToken, request.clientIp(), request.clientLocale())
    }

    @PostMapping("/otp/verify")
    fun verifyCode(
        @RequestBody body: OtpVerifyRequest,
    ): OtpVerifyResponse = auth.verifyOtp(body.email, body.code, body.deviceInfo)

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    fun register(
        @RequestBody body: RegisterRequest,
        request: HttpServletRequest,
    ): TokenPair =
        auth.register(body.registrationToken, body.username, body.displayName, request.clientLocale(), body.deviceInfo)

    @PostMapping("/refresh")
    fun refresh(
        @RequestBody body: RefreshRequest,
    ): TokenPair = sessions.refresh(body.refreshToken)

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun logout(
        @RequestBody body: LogoutRequest,
    ) {
        sessions.logout(body.refreshToken)
    }
}
