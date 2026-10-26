package app.podgon.reviews

import java.util.UUID

/**
 * Публичный порт модуля рекомендаций для категорий: сколько публикаций лежит в наборе категорий группы.
 * Нужен, чтобы предупредить об удалении и не дать удалить чужие публикации.
 */
interface ReviewPublications {
    fun count(
        groupId: UUID,
        categoryIds: Collection<UUID>,
    ): Int

    /** Публикации в этих категориях, сделанные не пользователем [userId]. */
    fun countByOthers(
        groupId: UUID,
        categoryIds: Collection<UUID>,
        userId: UUID,
    ): Int
}
