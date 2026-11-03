package app.podgon.mobile.core.network

import app.podgon.contract.MarkReadRequest
import app.podgon.contract.NotificationPage
import app.podgon.contract.NotificationSettings
import app.podgon.contract.UnreadCount
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody

/** Уведомления в приложении и их настройки (`api/openapi.yaml`, этап 8.3.5; клиент с авторизацией). */
class NotificationsApi(
    private val client: HttpClient,
) {
    suspend fun list(cursor: String? = null): Result<NotificationPage> =
        apiCall { client.get("api/v1/me/notifications") { cursor?.let { parameter("cursor", it) } }.bodyOrThrow() }

    suspend fun unread(): Result<UnreadCount> = apiCall { client.get("api/v1/me/notifications/unread").bodyOrThrow() }

    /** [ids] `null` — все. */
    suspend fun markRead(ids: List<String>? = null): Result<Unit> =
        apiCall { client.post("api/v1/me/notifications/read") { setBody(MarkReadRequest(ids)) }.bodyOrThrow<Unit>() }

    suspend fun settings(): Result<NotificationSettings> =
        apiCall { client.get("api/v1/me/notification-settings").bodyOrThrow() }

    suspend fun saveSettings(settings: NotificationSettings): Result<NotificationSettings> =
        apiCall { client.put("api/v1/me/notification-settings") { setBody(settings) }.bodyOrThrow() }
}
