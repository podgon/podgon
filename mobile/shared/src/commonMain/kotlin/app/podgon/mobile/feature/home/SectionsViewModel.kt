package app.podgon.mobile.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.podgon.contract.Category
import app.podgon.contract.CreateSavedFilterRequest
import app.podgon.contract.SavedFilter
import app.podgon.contract.SavedFilterKind
import app.podgon.contract.SavedFilterOrderRequest
import app.podgon.contract.UpdateSavedFilterRequest
import app.podgon.mobile.core.error.AppError
import app.podgon.mobile.core.error.appError
import app.podgon.mobile.core.network.SavedFiltersApi
import app.podgon.mobile.feature.reviews.CategoryRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SectionsState(
    val loading: Boolean = true,
    /** Закреплённые фильтры и закреплённые категории — каждая группа в порядке пользователя. */
    val pinnedFilters: List<SavedFilter> = emptyList(),
    val pinnedCategories: List<SavedFilter> = emptyList(),
    val unpinnedFilters: List<SavedFilter> = emptyList(),
    val categories: List<Category> = emptyList(),
    val error: AppError? = null,
) {
    /** Корневые категории набора, ещё не закреплённые. */
    val unpinnedRoots: List<Category>
        get() {
            val pinned = pinnedCategories.mapNotNull { it.categoryId }.toSet()
            return categories.filter { it.parentId == null && !it.archived && it.id !in pinned }
        }
}

/** Экран «Секции ленты» (подэкран настроек): закрепления и порядок хранятся на сервере (И30). */
class SectionsViewModel(
    private val api: SavedFiltersApi,
    private val categorySet: CategoryRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(SectionsState())
    val state: StateFlow<SectionsState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val set = categorySet.set().getOrNull()
            _state.update { it.copy(categories = set?.items ?: it.categories) }
            load()
        }
    }

    private suspend fun load() {
        val result = api.list()
        val list = result.getOrNull()
        _state.update { s ->
            if (list == null) {
                s.copy(loading = false, error = result.appError())
            } else {
                s.copy(
                    loading = false,
                    error = null,
                    pinnedFilters =
                        list
                            .filter {
                                it.kind == SavedFilterKind.FILTER && it.inFeed
                            }.sortedBy { it.position },
                    pinnedCategories = list.filter { it.kind == SavedFilterKind.CATEGORY }.sortedBy { it.position },
                    unpinnedFilters = list.filter { it.kind == SavedFilterKind.FILTER && !it.inFeed },
                )
            }
        }
    }

    fun pinFilter(filter: SavedFilter) = call { api.update(filter.id, UpdateSavedFilterRequest(inFeed = true)) }

    fun pinCategory(categoryId: String) =
        call { api.create(CreateSavedFilterRequest(SavedFilterKind.CATEGORY, categoryId = categoryId)) }

    /** Открепить: фильтр остаётся в сохранённых, закреплённая категория удаляется. */
    fun unpin(entry: SavedFilter) =
        call {
            if (entry.kind ==
                SavedFilterKind.CATEGORY
            ) {
                api.delete(entry.id)
            } else {
                api.update(entry.id, UpdateSavedFilterRequest(inFeed = false))
            }
        }

    /** Перестановка при перетаскивании (локально); на сервер — [commitOrder] по окончании. */
    fun move(
        kind: SavedFilterKind,
        from: Int,
        to: Int,
    ) {
        _state.update { s ->
            fun List<SavedFilter>.moved() =
                if (from !in indices || to !in indices) this else toMutableList().apply { add(to, removeAt(from)) }
            if (kind ==
                SavedFilterKind.FILTER
            ) {
                s.copy(pinnedFilters = s.pinnedFilters.moved())
            } else {
                s.copy(pinnedCategories = s.pinnedCategories.moved())
            }
        }
    }

    fun commitOrder() {
        val s = _state.value
        call { api.order(SavedFilterOrderRequest(s.pinnedFilters.map { it.id }, s.pinnedCategories.map { it.id })) }
    }

    private fun call(block: suspend () -> Result<*>) {
        viewModelScope.launch {
            val result = block()
            if (result.isFailure) _state.update { it.copy(error = result.appError()) }
            load()
        }
    }
}
