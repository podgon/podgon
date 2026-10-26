package app.podgon.reviews.domain

import app.podgon.groups.GroupCreated
import app.podgon.reviews.persistence.CategoryRepository
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import java.time.Clock
import java.util.UUID

/**
 * Копирует базовый набор категорий в новую группу. Событие синхронное, поэтому копирование идёт
 * в транзакции создания группы: группа без категорий не появляется.
 */
@Component
class TemplateCopier(
    private val categories: CategoryRepository,
    private val clock: Clock,
) {
    @EventListener
    fun on(event: GroupCreated) {
        val now = clock.instant()
        val ids = mutableMapOf<UUID, UUID>()
        categories.templates(event.locale).forEach { t ->
            val id = UUID.randomUUID().also { ids[t.id] = it }
            categories.insert(
                id,
                event.groupId,
                t.parentId?.let(ids::getValue),
                t.id,
                t.name,
                t.description,
                sortOrder = ids.size,
                createdBy = event.creatorId,
                now = now,
            )
        }
    }
}
