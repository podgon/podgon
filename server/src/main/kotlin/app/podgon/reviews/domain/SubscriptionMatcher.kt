package app.podgon.reviews.domain

import app.podgon.groups.GroupAccess
import app.podgon.notifications.Notifier
import app.podgon.reviews.persistence.Criteria
import app.podgon.reviews.persistence.ReviewRepository
import app.podgon.reviews.persistence.SavedFilterRepository
import app.podgon.reviews.persistence.SearchFilters
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.util.UUID

/**
 * Подписки на сохранённые фильтры (этап 8.3.5): после публикации рекомендации в группы проверяет фильтры
 * с подпиской у участников этих групп (кроме автора) и создаёт уведомление — одно на рекомендацию, даже если
 * подходит несколько фильтров. Фильтр проверяется только в тех новых группах, где состоит его владелец.
 * Перенос рекомендаций при вступлении в группу уведомлений не создаёт (иначе — поток уведомлений о старом).
 */
@Component
class SubscriptionMatcher(
    private val reviews: ReviewRepository,
    private val savedFilters: SavedFilterRepository,
    private val filters: FeedFilters,
    private val access: GroupAccess,
    private val notifier: Notifier,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /** Вызывается после фиксации транзакции; сбой уведомлений не отменяет публикацию. */
    fun published(
        reviewId: UUID,
        authorId: UUID,
        title: String,
        groupIds: Collection<UUID>,
    ) {
        if (groupIds.isEmpty()) return
        try {
            val groupsByUser =
                access
                    .members(groupIds)
                    .flatMap { (group, users) -> users.map { it to group } }
                    .groupBy({ it.first }, { it.second })
                    .minus(authorId)
            val notified = mutableSetOf<UUID>()
            for (filter in savedFilters.subscribed(groupsByUser.keys).sortedBy { it.name }) {
                if (filter.userId in notified) continue
                val definition = filters.decode(filter.definition ?: continue)
                val criteria = filters.sectionCriteria(definition)
                val search =
                    SearchFilters(
                        listOf(criteria).filter { it != Criteria() },
                        definition.q?.trim()?.takeIf { it.isNotEmpty() },
                        definition.titleOnly,
                    )
                if (reviews.matches(reviewId, groupsByUser.getValue(filter.userId), search)) {
                    notifier.filterMatch(filter.userId, reviewId, title, filter.name.orEmpty())
                    notified += filter.userId
                }
            }
        } catch (
            @Suppress("TooGenericExceptionCaught") e: RuntimeException,
        ) {
            log.warn("Не удалось проверить подписки для рекомендации {}", reviewId, e)
        }
    }
}
