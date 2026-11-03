package app.podgon.contract

import kotlinx.serialization.Serializable

/**
 * Категория общего набора (ADR-011). Два уровня: корень и подкатегория ([parentId]). Названия — на языке
 * запроса; [system] — «Без категории» (только для личных рекомендаций), [archived] — скрыта из выбора,
 * но нужна для подписи старых рекомендаций.
 */
@Serializable
data class Category(
    val id: String,
    /** Стабильный ключ из `docs/categories.md` (`food.coffee`). */
    val key: String,
    val parentId: String?,
    val name: String,
    val sortOrder: Int,
    val system: Boolean,
    val archived: Boolean = false,
)

/** Весь набор: корни и подкатегории вперемешку в порядке [Category.sortOrder]; [version] совпадает с `ETag`. */
@Serializable
data class CategorySet(
    val version: String,
    val items: List<Category>,
)

/** Ключ системной категории «Без категории». */
const val CATEGORY_NONE_KEY = "none"
