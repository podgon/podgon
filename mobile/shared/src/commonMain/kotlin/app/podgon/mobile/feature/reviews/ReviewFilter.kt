package app.podgon.mobile.feature.reviews

import kotlinx.serialization.Serializable

/** Условия поиска и фильтры ленты; все заданные условия действуют вместе. Хранится как «последний фильтр» группы. */
@Serializable
data class ReviewFilter(
    val query: String = "",
    /** Категории: сервер ищет в них вместе с подкатегориями. */
    val categoryIds: Set<String> = emptySet(),
    val tags: Set<String> = emptySet(),
    /** `true` — достаточно любого из тегов, `false` — нужны все. */
    val anyTag: Boolean = false,
    val authorId: String? = null,
) {
    val isEmpty: Boolean get() = query.isBlank() && categoryIds.isEmpty() && tags.isEmpty() && authorId == null

    /** Число включённых условий панели фильтров (без строки поиска) — для подписи кнопки. */
    val panelCount: Int get() =
        (if (categoryIds.isEmpty()) 0 else 1) + (if (tags.isEmpty()) 0 else 1) +
            (if (authorId == null) 0 else 1)
}

/** Что показывает лента: одна группа, мои рекомендации или поиск по всем своим группам. */
sealed interface FeedScope {
    data class Group(
        val groupId: String,
    ) : FeedScope

    data object Mine : FeedScope

    data object AllGroups : FeedScope
}
