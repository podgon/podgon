package app.podgon.groups

import java.util.UUID

/**
 * Порт для модуля рекомендаций: число рекомендаций, опубликованных в группах (для `GroupSummary.reviewCount`).
 * Объявлен здесь, реализован в `reviews`, — так модуль групп не зависит от модуля рекомендаций.
 */
interface GroupReviewCounts {
    fun count(groupIds: Collection<UUID>): Map<UUID, Int>
}
