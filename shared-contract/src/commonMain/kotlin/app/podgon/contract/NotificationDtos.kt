package app.podgon.contract

import kotlinx.serialization.Serializable

/** Вид уведомления (этап 8.3.5). */
@Serializable
enum class NotificationKind {
    /** Адресное приглашение в группу на мой email. */
    INVITATION,

    /** Новая рекомендация подходит под мой сохранённый фильтр с подпиской. */
    FILTER_MATCH,
}

/**
 * Уведомление в приложении. Подписи сохраняются при создании: группу или рекомендацию могли уже переименовать
 * или удалить — тогда переход по уведомлению покажет, что объект недоступен.
 */
@Serializable
data class NotificationInfo(
    val id: String,
    val kind: NotificationKind,
    val createdAt: String,
    val read: Boolean,
    val invitationId: String? = null,
    val groupName: String? = null,
    val invitedBy: String? = null,
    val reviewId: String? = null,
    val reviewTitle: String? = null,
    val filterName: String? = null,
)

/** Страница уведомлений (новые сверху); [unread] — непрочитанных всего. */
@Serializable
data class NotificationPage(
    val items: List<NotificationInfo>,
    val unread: Int,
    val nextCursor: String? = null,
)

@Serializable
data class UnreadCount(
    val count: Int,
)

/** Отметить прочитанными: [ids] `null` — все. */
@Serializable
data class MarkReadRequest(
    val ids: List<String>? = null,
)

/** О чём уведомлять. */
@Serializable
data class NotificationSettings(
    val invitations: Boolean = true,
    val filterMatches: Boolean = true,
)

/** Входящее адресное приглашение (на мой email): вступить можно без кода, по [id]. */
@Serializable
data class IncomingInvitation(
    val id: String,
    val groupName: String,
    val groupDescription: String,
    val memberCount: Int,
    val invitedBy: String,
    val expiresAt: String,
)
