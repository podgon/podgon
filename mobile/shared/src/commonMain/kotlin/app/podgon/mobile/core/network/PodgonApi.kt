package app.podgon.mobile.core.network

import app.podgon.contract.LogoutRequest
import app.podgon.contract.OtpRequest
import app.podgon.contract.OtpVerifyRequest
import app.podgon.contract.OtpVerifyResponse
import app.podgon.contract.RefreshRequest
import app.podgon.contract.RegisterRequest
import app.podgon.contract.SessionInfo
import app.podgon.contract.TokenPair
import app.podgon.contract.UpdateProfileRequest
import app.podgon.contract.UserProfile
import io.ktor.client.HttpClient
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody

/** Публичные операции входа (`api/openapi.yaml`, тег auth). Возвращают `Result` с [app.podgon.mobile.core.error.AppException]. */
class AuthApi(
    private val client: HttpClient,
) {
    suspend fun requestOtp(
        email: String,
        captchaToken: String?,
    ): Result<Unit> =
        apiCall {
            client.post("api/v1/auth/otp/request") { setBody(OtpRequest(email, captchaToken)) }.bodyOrThrow<Unit>()
        }

    suspend fun verifyOtp(
        email: String,
        code: String,
        deviceInfo: String?,
    ): Result<OtpVerifyResponse> =
        apiCall {
            client.post("api/v1/auth/otp/verify") { setBody(OtpVerifyRequest(email, code, deviceInfo)) }.bodyOrThrow()
        }

    suspend fun register(
        registrationToken: String,
        username: String,
        displayName: String,
        deviceInfo: String?,
    ): Result<TokenPair> =
        apiCall {
            client
                .post("api/v1/auth/register") {
                    setBody(RegisterRequest(registrationToken, username, displayName, deviceInfo))
                }.bodyOrThrow()
        }

    suspend fun logout(refreshToken: String): Result<Unit> =
        apiCall { client.post("api/v1/auth/logout") { setBody(LogoutRequest(refreshToken)) }.bodyOrThrow<Unit>() }

    @Suppress("unused")
    suspend fun refresh(refreshToken: String): Result<TokenPair> =
        apiCall { client.post("api/v1/auth/refresh") { setBody(RefreshRequest(refreshToken)) }.bodyOrThrow() }
}

/** Профиль и сессии текущего пользователя (клиент с авторизацией). */
class MeApi(
    private val client: HttpClient,
) {
    suspend fun me(): Result<UserProfile> = apiCall { client.get("api/v1/me").bodyOrThrow() }

    suspend fun update(request: UpdateProfileRequest): Result<UserProfile> =
        apiCall { client.patch("api/v1/me") { setBody(request) }.bodyOrThrow() }

    suspend fun sessions(): Result<List<SessionInfo>> = apiCall { client.get("api/v1/me/sessions").bodyOrThrow() }

    suspend fun revokeSession(id: String): Result<Unit> =
        apiCall {
            client.delete("api/v1/me/sessions/$id").bodyOrThrow<Unit>()
        }
}
