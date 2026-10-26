package app.podgon.contract

import kotlinx.serialization.Serializable

/** Категория группы; иерархия задаётся [parentId] (`null` — корневая). Список приходит упорядоченным. */
@Serializable
data class CategoryInfo(
    val id: String,
    val parentId: String?,
    val name: String,
    val description: String,
)

@Serializable
data class CreateCategoryRequest(
    val name: String,
    val description: String = "",
    val parentId: String? = null,
)

/** Частичное обновление: `null` — поле не менять. */
@Serializable
data class UpdateCategoryRequest(
    val name: String? = null,
    val description: String? = null,
)

/** Перенос категории; `null` — сделать корневой. */
@Serializable
data class MoveCategoryRequest(
    val parentId: String? = null,
)

/** Что будет удалено вместе с категорией (без неё самой): основа для диалога подтверждения. */
@Serializable
data class CategoryDeleteImpact(
    val subcategories: Int,
    val publications: Int,
    /** Из них опубликованных другими участниками: если их больше нуля, удалить категорию нельзя. */
    val foreignPublications: Int,
)
