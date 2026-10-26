package app.podgon.groups

import java.util.UUID

/**
 * Событие модуля групп: группа создана. Публикуется синхронно внутри транзакции создания, поэтому
 * обработчики (например, копирование шаблона категорий) выполняются атомарно с группой.
 */
data class GroupCreated(
    val groupId: UUID,
    val creatorId: UUID,
    /** Язык создателя (`ru`, `en`) — на нём копируются названия шаблонов. */
    val locale: String,
)
