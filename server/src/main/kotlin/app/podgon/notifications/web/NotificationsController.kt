package app.podgon.notifications.web

import app.podgon.contract.MarkReadRequest
import app.podgon.contract.NotificationPage
import app.podgon.contract.NotificationSettings
import app.podgon.contract.UnreadCount
import app.podgon.identity.AuthenticatedUser
import app.podgon.notifications.internal.NotificationService
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

/** Уведомления в приложении и их настройки (этап 8.3.5). */
@RestController
@RequestMapping("/api/v1/me")
class NotificationsController(
    private val notifications: NotificationService,
) {
    @GetMapping("/notifications")
    fun list(
        @AuthenticationPrincipal user: AuthenticatedUser,
        @RequestParam(required = false) cursor: String?,
        @RequestParam(required = false) limit: Int?,
    ): NotificationPage = notifications.page(user.userId, cursor, limit)

    @GetMapping("/notifications/unread")
    fun unread(
        @AuthenticationPrincipal user: AuthenticatedUser,
    ): UnreadCount = UnreadCount(notifications.unread(user.userId))

    @PostMapping("/notifications/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun markRead(
        @AuthenticationPrincipal user: AuthenticatedUser,
        @RequestBody body: MarkReadRequest,
    ) = notifications.markRead(user.userId, body.ids)

    @GetMapping("/notification-settings")
    fun settings(
        @AuthenticationPrincipal user: AuthenticatedUser,
    ): NotificationSettings = notifications.settings(user.userId)

    @PutMapping("/notification-settings")
    fun saveSettings(
        @AuthenticationPrincipal user: AuthenticatedUser,
        @RequestBody body: NotificationSettings,
    ): NotificationSettings = notifications.saveSettings(user.userId, body)
}
