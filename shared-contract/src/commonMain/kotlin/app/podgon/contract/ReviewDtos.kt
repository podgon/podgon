package app.podgon.contract

import kotlinx.serialization.Serializable

@Serializable
data class ReviewAuthor(
    val id: String,
    val username: String,
    val displayName: String,
)

/** Публикация рекомендации в обычной группе. */
@Serializable
data class PublicationInfo(
    val groupId: String,
    /** Момент публикации в группе, ISO-8601 в UTC. */
    val sharedAt: String,
)

/**
 * Рекомендация целиком. [publications] — только обычные группы: автор видит все, остальные — только
 * группы, где состоят сами. [personal] — рекомендация личная (опубликована только в личной группе).
 */
@Serializable
data class ReviewDetails(
    val id: String,
    val title: String,
    val description: String,
    val rating: Int,
    val categoryId: String,
    val tags: List<String>,
    val author: ReviewAuthor,
    val createdAt: String,
    val updatedAt: String,
    val personal: Boolean,
    val publications: List<PublicationInfo>,
)

/**
 * Новая рекомендация. [groupIds] — обычные группы, где я участник; пусто — рекомендация личная.
 * «Без категории» допустима только у личной.
 */
@Serializable
data class CreateReviewRequest(
    val title: String,
    val description: String = "",
    val rating: Int,
    val categoryId: String,
    val tags: List<String> = emptyList(),
    val groupIds: List<String> = emptyList(),
)

/** Частичное обновление: `null` — поле не менять (для [tags] `null` — теги не менять, пустой список — убрать все). */
@Serializable
data class UpdateReviewRequest(
    val title: String? = null,
    val description: String? = null,
    val rating: Int? = null,
    val categoryId: String? = null,
    val tags: List<String>? = null,
)

/**
 * Новый набор обычных групп целиком: отсутствующие в нём публикации снимаются. Пустой набор делает
 * рекомендацию личной.
 */
@Serializable
data class SetPublicationsRequest(
    val groupIds: List<String>,
)

/** Карточка рекомендации в списке; [sharedAt] и [groupId] заполнены в лентах и поиске. */
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
    val categoryId: String,
    val sharedAt: String? = null,
    /** Группа публикации: заполнена в лентах и поиске (рекомендация в двух группах — две карточки). */
    val groupId: String? = null,
)

/** Страница списка; [nextCursor] — `null`, если страница последняя. */
@Serializable
data class ReviewPage(
    val items: List<ReviewCard>,
    val nextCursor: String? = null,
)

/** Тег группы и число публикаций с ним: подсказки и панель фильтров. */
@Serializable
data class TagCount(
    val name: String,
    val count: Int,
)

const val REVIEW_EXCERPT_MAX = 200
