package app.podgon.contract

import kotlinx.serialization.Serializable

@Serializable
data class ReviewAuthor(
    val id: String,
    val username: String,
    val displayName: String,
)

/** Публикация рекомендации в группе: в какой категории группы она лежит. */
@Serializable
data class PublicationInfo(
    val groupId: String,
    val categoryId: String,
    /** Момент публикации в группе, ISO-8601 в UTC. */
    val sharedAt: String,
)

@Serializable
data class PublicationRequest(
    val groupId: String,
    val categoryId: String,
)

/**
 * Рекомендация целиком. Автор видит все публикации, остальные — только в группах, где состоят сами.
 */
@Serializable
data class ReviewDetails(
    val id: String,
    val title: String,
    val description: String,
    val rating: Int,
    val tags: List<String>,
    val author: ReviewAuthor,
    val createdAt: String,
    val updatedAt: String,
    val publications: List<PublicationInfo>,
)

@Serializable
data class CreateReviewRequest(
    val title: String,
    val description: String = "",
    val rating: Int,
    val tags: List<String> = emptyList(),
    val publications: List<PublicationRequest> = emptyList(),
)

/** Частичное обновление: `null` — поле не менять (для [tags] `null` — теги не менять, пустой список — убрать все). */
@Serializable
data class UpdateReviewRequest(
    val title: String? = null,
    val description: String? = null,
    val rating: Int? = null,
    val tags: List<String>? = null,
)

/** Новый набор публикаций целиком: отсутствующие в нём публикации снимаются. */
@Serializable
data class SetPublicationsRequest(
    val publications: List<PublicationRequest>,
)

/** Карточка рекомендации в списке; [categoryId] и [sharedAt] заполнены в ленте группы. */
@Serializable
data class ReviewCard(
    val id: String,
    val title: String,
    /** Начало описания (до [REVIEW_EXCERPT_MAX] символов). */
    val excerpt: String,
    val rating: Int,
    val tags: List<String>,
    val author: ReviewAuthor,
    val createdAt: String,
    val categoryId: String? = null,
    val sharedAt: String? = null,
)

/** Страница списка; [nextCursor] — `null`, если страница последняя. */
@Serializable
data class ReviewPage(
    val items: List<ReviewCard>,
    val nextCursor: String? = null,
)

const val REVIEW_EXCERPT_MAX = 200
