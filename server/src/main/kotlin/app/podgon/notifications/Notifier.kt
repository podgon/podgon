package app.podgon.notifications

import java.util.UUID

/**
 * Публичный порт уведомлений в приложении (этап 8.3.5): модули сообщают о событиях, модуль уведомлений
 * учитывает настройки пользователя и не создаёт второго уведомления об одном объекте. Push нет — клиент
 * спрашивает число непрочитанных сам.
 */
interface Notifier {
    /** Адресное приглашение в группу на email пользователя. */
    fun invitation(
        userId: UUID,
        invitationId: UUID,
        groupName: String,
        invitedBy: String,
    )

    /** Новая рекомендация подходит под сохранённый фильтр пользователя с подпиской. */
    fun filterMatch(
        userId: UUID,
        reviewId: UUID,
        reviewTitle: String,
        filterName: String,
    )
}
