package app.podgon.identity

import java.util.UUID

/**
 * Пользователь текущего запроса, полученный из проверенного access-токена.
 * Единственный тип модуля identity, который используют другие модули (через `@AuthenticationPrincipal`).
 */
data class AuthenticatedUser(
    val userId: UUID,
    /** Идентификатор сессии (устройства). */
    val sessionId: UUID,
)
