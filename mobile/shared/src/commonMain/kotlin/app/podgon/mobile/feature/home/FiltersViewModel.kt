package app.podgon.mobile.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.podgon.contract.Category
import app.podgon.contract.CreateSavedFilterRequest
import app.podgon.contract.FILTER_DEFINITION_VERSION
import app.podgon.contract.Facets
import app.podgon.contract.FilterDefinition
import app.podgon.contract.SavedFilter
import app.podgon.contract.SavedFilterKind
import app.podgon.contract.TagMode
import app.podgon.contract.UpdateSavedFilterRequest
import app.podgon.mobile.core.error.AppError
import app.podgon.mobile.core.error.appError
import app.podgon.mobile.core.network.ReviewsApi
import app.podgon.mobile.core.network.SavedFiltersApi
import app.podgon.mobile.feature.reviews.CategoryRepository
import app.podgon.mobile.feature.reviews.ReviewFilter
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class FiltersState(
    val draft: ReviewFilter,
    /** Счётчики выборки по черновику: что есть на вкладке. */
    val facets: Facets? = null,
    val categories: Map<String, Category> = emptyMap(),
    /** Сохранённые фильтры (без закреплённых категорий), по алфавиту. */
    val saved: List<SavedFilter> = emptyList(),
    val error: AppError? = null,
) {
    /** Сохранённый фильтр, совпадающий с черновиком (название показывается в строке сохранённых). */
    val matching: SavedFilter? get() = saved.firstOrNull { it.definition?.toFilter() == draft }
}

/** Определение сохранённого фильтра ↔ фильтр панели (без строки поиска: она не входит в панель). */
fun FilterDefinition.toFilter() =
    ReviewFilter(
        categoryIds = categoryIds.toSet(),
        tags = tags.toSet(),
        anyTag = tagMode == TagMode.ANY,
        authorIds = authorIds.toSet(),
        minRating = minRating,
        maxRating = maxRating,
    )

fun ReviewFilter.toDefinition() =
    FilterDefinition(
        v = FILTER_DEFINITION_VERSION,
        categoryIds = categoryIds.toList(),
        tags = tags.toList(),
        tagMode = if (anyTag) TagMode.ANY else TagMode.ALL,
        authorIds = authorIds.toList(),
        minRating = minRating,
        maxRating = maxRating,
    )

/**
 * Панель фильтров (`docs/design.md`, «Строка поиска и панель фильтров»): оценка, категории, теги, авторы
 * по счётчикам выборки вкладки [groupId] (`null` — «Все»); сохранённые фильтры и «В ленте» (И4, И30).
 */
class FiltersViewModel(
    private val groupId: String?,
    initial: ReviewFilter,
    private val reviews: ReviewsApi,
    private val savedFilters: SavedFiltersApi,
    private val categorySet: CategoryRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(FiltersState(initial.copy(query = "")))
    val state: StateFlow<FiltersState> = _state.asStateFlow()

    private var facetsJob: Job? = null

    init {
        viewModelScope.launch {
            val set = categorySet.set().getOrNull()
            _state.update { s -> s.copy(categories = set?.items?.associateBy { it.id } ?: s.categories) }
        }
        loadSaved()
        loadFacets()
    }

    private fun loadSaved() {
        viewModelScope.launch {
            val result = savedFilters.list()
            _state.update { s ->
                s.copy(
                    saved =
                        result.getOrNull()?.filter { it.kind == SavedFilterKind.FILTER }?.sortedBy {
                            it.name
                                ?.lowercase()
                        }
                            ?: s.saved,
                    error = result.appError() ?: s.error,
                )
            }
        }
    }

    /** Значения и счётчики вкладки без фильтра: список не сужается по мере выбора, можно отметить несколько подряд. */
    private fun loadFacets() {
        facetsJob?.cancel()
        facetsJob =
            viewModelScope.launch {
                val result = reviews.facets(groupId, ReviewFilter())
                _state.update {
                    it.copy(
                        facets = result.getOrNull() ?: it.facets,
                        error = result.appError() ?: it.error,
                    )
                }
            }
    }

    fun setRating(
        min: Int,
        max: Int,
    ) = edit { it.copy(minRating = min.takeIf { v -> v > 1 }, maxRating = max.takeIf { v -> v < 10 }) }

    fun toggleCategory(id: String) = edit { it.copy(categoryIds = it.categoryIds.toggle(id)) }

    fun toggleTag(tag: String) = edit { it.copy(tags = it.tags.toggle(tag)) }

    fun setAnyTag(any: Boolean) = edit { it.copy(anyTag = any) }

    fun toggleAuthor(id: String) = edit { it.copy(authorIds = it.authorIds.toggle(id)) }

    fun reset() = edit { ReviewFilter() }

    /** Применяет сохранённый фильтр целиком. */
    fun applySaved(filter: SavedFilter) {
        filter.definition?.let { definition -> edit { definition.toFilter() } }
    }

    /** Сохраняет черновик под именем [name]; [inFeed] — сразу закрепить секцией ленты. */
    fun save(
        name: String,
        inFeed: Boolean = false,
    ) {
        val draft = _state.value.draft
        viewModelScope.launch {
            val result =
                savedFilters.create(
                    CreateSavedFilterRequest(
                        SavedFilterKind.FILTER,
                        name = name.trim(),
                        definition = draft.toDefinition(),
                        inFeed = inFeed,
                    ),
                )
            if (result.isFailure) _state.update { it.copy(error = result.appError()) } else loadSaved()
        }
    }

    /** Переключатель «В ленте» у сохранённого фильтра. */
    fun setInFeed(
        filter: SavedFilter,
        inFeed: Boolean,
    ) = change { savedFilters.update(filter.id, UpdateSavedFilterRequest(inFeed = inFeed)) }

    fun rename(
        filter: SavedFilter,
        name: String,
    ) = change { savedFilters.update(filter.id, UpdateSavedFilterRequest(name = name.trim())) }

    fun delete(filter: SavedFilter) = change { savedFilters.delete(filter.id) }

    private fun change(call: suspend () -> Result<*>) {
        viewModelScope.launch {
            val result = call()
            if (result.isFailure) _state.update { it.copy(error = result.appError()) } else loadSaved()
        }
    }

    private fun edit(change: (ReviewFilter) -> ReviewFilter) = _state.update { it.copy(draft = change(it.draft)) }

    private fun Set<String>.toggle(value: String) = if (value in this) this - value else this + value
}

/** Название фильтра по умолчанию — из содержимого: «Кофейни · #кофе · 7–10». */
fun defaultFilterName(
    filter: ReviewFilter,
    categories: Map<String, Category>,
): String =
    buildList {
        filter.categoryIds.mapNotNull { categories[it]?.name }.forEach(::add)
        filter.tags.forEach { add("#$it") }
        if (filter.minRating != null ||
            filter.maxRating != null
        ) {
            add("${filter.minRating ?: 1}–${filter.maxRating ?: 10}")
        }
    }.joinToString(" · ").take(app.podgon.contract.Validation.SAVED_FILTER_NAME_MAX)
