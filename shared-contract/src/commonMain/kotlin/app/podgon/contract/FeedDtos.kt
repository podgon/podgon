package app.podgon.contract

import kotlinx.serialization.Serializable

/** Режим тегов в фильтре: нужны все теги или достаточно любого. */
@Serializable
enum class TagMode { ALL, ANY }

/**
 * Определение сохранённого фильтра (версия [v], сейчас [FILTER_DEFINITION_VERSION]). Группа не сохраняется. Удалённые теги при применении
 * игнорируются. [q] учитывается, только когда фильтр применяют в панели: в секции ленты поиска нет.
 */
@Serializable
data class FilterDefinition(
    val v: Int,
    val q: String? = null,
    val categoryIds: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    val tagMode: TagMode = TagMode.ALL,
    val authorIds: List<String> = emptyList(),
    val minRating: Int? = null,
    val maxRating: Int? = null,
)

const val FILTER_DEFINITION_VERSION = 1

/** Вид записи: сохранённый фильтр панели или закреплённая категория (секция ленты, И30). */
@Serializable
enum class SavedFilterKind { FILTER, CATEGORY }

/**
 * Сохранённый фильтр или закреплённая категория. [inFeed] — закреплён секцией ленты (у категории всегда),
 * [position] — порядок среди закреплённых своего вида.
 */
@Serializable
data class SavedFilter(
    val id: String,
    val kind: SavedFilterKind,
    val inFeed: Boolean,
    val name: String? = null,
    val categoryId: String? = null,
    val definition: FilterDefinition? = null,
    val position: Int? = null,
)

/** Новый фильтр (`kind = FILTER`: [name] и [definition]) или закрепление категории (`CATEGORY`: [categoryId]). */
@Serializable
data class CreateSavedFilterRequest(
    val kind: SavedFilterKind,
    val name: String? = null,
    val definition: FilterDefinition? = null,
    val inFeed: Boolean = false,
    val categoryId: String? = null,
)

/** Правка сохранённого фильтра; `null` — поле не менять. Закреплённую категорию не правят — её открепляют. */
@Serializable
data class UpdateSavedFilterRequest(
    val name: String? = null,
    val definition: FilterDefinition? = null,
    val inFeed: Boolean? = null,
)

/** Полный порядок закреплённых: [filterIds] — записи-фильтры, [categoryIds] — записи закреплённых категорий. */
@Serializable
data class SavedFilterOrderRequest(
    val filterIds: List<String>,
    val categoryIds: List<String>,
)

/** Вид секции ленты. */
@Serializable
enum class FeedSectionKind { FILTER, CATEGORY }

/**
 * Секция ленты в оглавлении. [section] — значение параметра `section` для её записей (`category:<id>` или
 * `filter:<id>`); [filterId] — запись сохранённого фильтра (у фильтра и закреплённой категории).
 */
@Serializable
data class FeedSection(
    val section: String,
    val kind: FeedSectionKind,
    val pinned: Boolean,
    val count: Int,
    val lastSharedAt: String,
    val categoryId: String? = null,
    val filterId: String? = null,
    val name: String? = null,
)

/** Оглавление ленты: секции уже в порядке показа, пустых нет (И29). */
@Serializable
data class FeedToc(
    val sections: List<FeedSection>,
)

@Serializable
data class CategoryCount(
    val categoryId: String,
    val count: Int,
)

@Serializable
data class AuthorCount(
    val author: ReviewAuthor,
    val count: Int,
)

/** Счётчики выборки для панели фильтров; [tags] — самые частые первыми (они же популярные теги в подсказках). */
@Serializable
data class Facets(
    val categories: List<CategoryCount>,
    val tags: List<TagCount>,
    val authors: List<AuthorCount>,
)

/** Префиксы значения параметра `section`. */
const val SECTION_CATEGORY_PREFIX = "category:"
const val SECTION_FILTER_PREFIX = "filter:"
