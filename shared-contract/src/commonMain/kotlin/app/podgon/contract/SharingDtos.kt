package app.podgon.contract

import kotlinx.serialization.Serializable

/** Какие свои рекомендации пользователь переносит в группу при вступлении. */
@Serializable
enum class ShareMode {
    /** Ничего не переносить. */
    NONE,

    /** Все свои рекомендации. */
    ALL,

    /** Только из выбранных категорий. */
    CATEGORIES,

    /** Все, кроме выбранных категорий. */
    CATEGORIES_EXCEPT,

    /** Только с выбранными тегами. */
    TAGS,

    /** Все, кроме рекомендаций с выбранными тегами. */
    TAGS_EXCEPT,
}

/**
 * Правило переноса своих групповых рекомендаций (личные не переносятся). [categoryIds] — категории набора
 * (корень включает подкатегории); для режимов с тегами — [tags] (любой из перечисленных).
 */
@Serializable
data class ShareRule(
    val mode: ShareMode,
    val categoryIds: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
)

@Serializable
data class SharePreviewRequest(
    val token: String,
    val share: ShareRule,
)

/** Рекомендация, которая будет опубликована в новой группе. */
@Serializable
data class SharePreviewItem(
    val reviewId: String,
    val title: String,
    val categoryId: String,
)

@Serializable
data class SharePreview(
    /** Сколько рекомендаций отобрано правилом. */
    val total: Int,
    /** Первые из них (до [Validation.SHARE_PREVIEW_MAX]) — для списка «Будет опубликовано». */
    val items: List<SharePreviewItem>,
)

@Serializable
data class AcceptInvitationRequest(
    val token: String,
    val share: ShareRule? = null,
)

/** Судьба рекомендаций, опубликованных только в удаляемой группе. */
@Serializable
enum class ReviewsFate { RETURN_TO_PERSONAL, DELETE }

@Serializable
data class GroupDeleteImpact(
    /** Рекомендации, опубликованные только в этой группе. */
    val exclusiveReviews: Int,
    /** Все публикации в группе. */
    val publications: Int,
)

@Serializable
data class LeaveImpact(
    /** Публикации пользователя в группе. */
    val myPublications: Int,
)

@Serializable
data class GroupRef(
    val id: String,
    val name: String,
)

@Serializable
data class AccountDeleteImpact(
    /** Все рекомендации пользователя. */
    val reviews: Int,
    /** Из них опубликованные хотя бы в одной группе. */
    val publishedReviews: Int,
    /** Группы, где пользователь единственный админ при наличии других участников: удалить аккаунт мешают они. */
    val blockingGroups: List<GroupRef>,
)

@Serializable
data class DeleteAccountRequest(
    val code: String,
    val deleteReviews: Boolean = false,
)
