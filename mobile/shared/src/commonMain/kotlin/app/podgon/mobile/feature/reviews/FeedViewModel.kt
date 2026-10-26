package app.podgon.mobile.feature.reviews

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.podgon.contract.CategoryInfo
import app.podgon.contract.MemberInfo
import app.podgon.contract.ReviewCard
import app.podgon.contract.TagCount
import app.podgon.mobile.core.error.AppError
import app.podgon.mobile.core.error.appError
import app.podgon.mobile.core.network.GroupsApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Варианты для панели фильтров: загружаются при её первом открытии. */
data class FilterOptions(
    val categories: List<CategoryInfo> = emptyList(),
    val tags: List<TagCount> = emptyList(),
    val authors: List<MemberInfo> = emptyList(),
)

data class FeedState(
    val loading: Boolean = true,
    val loadingMore: Boolean = false,
    val items: List<ReviewCard> = emptyList(),
    /** `null` — страниц больше нет. */
    val nextCursor: String? = null,
    /** Нет сети: показан сохранённый список. */
    val offline: Boolean = false,
    val filter: ReviewFilter = ReviewFilter(),
    /** `null` — панель фильтров ещё не открывалась. */
    val options: FilterOptions? = null,
    /** Названия моих групп: подписи карточек в поиске по всем группам. */
    val groupNames: Map<String, String> = emptyMap(),
    val error: AppError? = null,
)

/**
 * Лента ([scope]) с поиском и фильтрами. Для группы последний фильтр запоминается и восстанавливается
 * при следующем открытии; результат пустого фильтра кэшируется для чтения без сети.
 */
class FeedViewModel(
    private val scope: FeedScope,
    private val repository: ReviewsRepository,
    private val groups: GroupsApi,
) : ViewModel() {
    private val _state = MutableStateFlow(FeedState())
    val state: StateFlow<FeedState> = _state.asStateFlow()

    private var loadJob: Job? = null

    init {
        loadJob =
            viewModelScope.launch {
                (scope as? FeedScope.Group)?.let { g ->
                    repository.savedFilter(g.groupId)?.let { saved -> _state.update { it.copy(filter = saved) } }
                }
                if (scope == FeedScope.AllGroups) {
                    groups.list().getOrNull()?.let { list ->
                        _state.update { it.copy(groupNames = list.associate { g -> g.id to g.name }) }
                    }
                }
                load()
            }
    }

    /**
     * Загружает первую страницу заново (старый список остаётся на экране при ошибке). Пока идёт загрузка
     * (в том числе начальная), повторный вызов ничего не делает.
     */
    fun refresh() {
        if (loadJob?.isActive == true) return
        loadJob = viewModelScope.launch { load() }
    }

    private suspend fun load() {
        _state.update { it.copy(loading = true, error = null) }
        val result = repository.feed(scope, _state.value.filter, null)
        _state.update {
            val loaded = result.getOrNull()
            val page = loaded?.value
            it.copy(
                loading = false,
                offline = loaded?.fromCache ?: it.offline,
                items = page?.items ?: it.items,
                nextCursor = if (page != null) page.nextCursor else it.nextCursor,
                error = result.appError(),
            )
        }
    }

    fun loadMore() {
        val current = _state.value
        val cursor = current.nextCursor ?: return
        if (current.loading || current.loadingMore) return
        _state.update { it.copy(loadingMore = true, error = null) }
        viewModelScope.launch {
            val result = repository.feed(scope, current.filter, cursor)
            _state.update {
                val page = result.getOrNull()?.value
                it.copy(
                    loadingMore = false,
                    items = if (page != null) it.items + page.items else it.items,
                    nextCursor = if (page != null) page.nextCursor else it.nextCursor,
                    error = result.appError(),
                )
            }
        }
    }

    /** Применяет новый фильтр: список перезагружается, фильтр группы запоминается. */
    fun setFilter(filter: ReviewFilter) {
        if (filter == _state.value.filter) return
        loadJob?.cancel()
        _state.update { it.copy(filter = filter, items = emptyList(), nextCursor = null) }
        loadJob =
            viewModelScope.launch {
                (scope as? FeedScope.Group)?.let { repository.saveFilter(it.groupId, filter) }
                load()
            }
    }

    fun search(query: String) = setFilter(_state.value.filter.copy(query = query.trim()))

    fun clearFilter() = setFilter(ReviewFilter())

    /** Подгружает категории, теги и участников группы для панели фильтров (один раз). */
    fun loadOptions() {
        val group = (scope as? FeedScope.Group)?.groupId ?: return
        if (_state.value.options != null) return
        _state.update { it.copy(options = FilterOptions()) }
        viewModelScope.launch {
            val categories = groups.categories(group)
            val tags = repository.tags(group)
            val members = groups.members(group)
            _state.update {
                it.copy(
                    options =
                        FilterOptions(
                            categories = categories.getOrNull().orEmpty(),
                            tags = tags.getOrNull().orEmpty(),
                            authors = members.getOrNull().orEmpty(),
                        ),
                    error = categories.appError() ?: tags.appError() ?: members.appError(),
                )
            }
        }
    }
}
