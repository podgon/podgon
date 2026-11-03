package app.podgon.mobile.feature.reviews

import kotlinx.serialization.Serializable

/** Условия поиска и фильтры ленты; все заданные условия действуют вместе. Хранится как «последний фильтр» группы. */
@Serializable
data class ReviewFilter(
    val query: String = "",
    /** Категории: корень включает подкатегории. */
    val categoryIds: Set<String> = emptySet(),
    val tags: Set<String> = emptySet(),
    /** `true` — достаточно любого из тегов, `false` — нужны все. */
    val anyTag: Boolean = false,
    val authorIds: Set<String> = emptySet(),
    val minRating: Int? = null,
    val maxRating: Int? = null,
) {
    val isEmpty: Boolean get() = query.isBlank() && panelCount == 0

    /** Число включённых условий панели фильтров (без строки поиска) — для подписи кнопки. */
    val panelCount: Int get() =
        listOf(
            categoryIds.isNotEmpty(),
            tags.isNotEmpty(),
            authorIds.isNotEmpty(),
            minRating != null || maxRating != null,
        ).count { it }
}

/** Что показывает лента: одна группа, мои рекомендации или поиск по всем своим группам. */
sealed interface FeedScope {
    data class Group(
        val groupId: String,
    ) : FeedScope

    data object Mine : FeedScope

    data object AllGroups : FeedScope
}
