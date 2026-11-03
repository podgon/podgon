package app.podgon.mobile.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.podgon.contract.Category
import app.podgon.contract.CreateSavedFilterRequest
import app.podgon.contract.FeedSection
import app.podgon.contract.FeedSectionKind
import app.podgon.contract.GroupSummary
import app.podgon.contract.ReviewCard
import app.podgon.contract.SavedFilter
import app.podgon.contract.SavedFilterKind
import app.podgon.contract.TagCount
import app.podgon.mobile.core.cache.ResponseCache
import app.podgon.mobile.core.error.AppError
import app.podgon.mobile.core.error.appError
import app.podgon.mobile.core.network.GroupsApi
import app.podgon.mobile.core.network.ReviewsApi
import app.podgon.mobile.core.network.SavedFiltersApi
import app.podgon.mobile.feature.reviews.CategoryRepository
import app.podgon.mobile.feature.reviews.FeedScope
import app.podgon.mobile.feature.reviews.ReviewFilter
import app.podgon.mobile.feature.reviews.ReviewsRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Раздел, который показывает главный экран: лента с вкладками групп, «Личное» или «Избранное». */
sealed interface HomeArea {
    data object Feed : HomeArea

    /** Лента, открытая по тегу из карточки: вкладки как в ленте, фильтр по тегу не запоминается. */
    data class Tagged(
        val tag: String,
    ) : HomeArea

    data class System(
        val groupId: String,
        val favorites: Boolean,
    ) : HomeArea
}

/** Секция ленты: строка оглавления и загруженные записи (свой курсор у каждой секции). */
data class SectionUi(
    val section: String,
    val kind: FeedSectionKind,
    val pinned: Boolean,
    val count: Int,
    val categoryId: String?,
    val filterId: String?,
    val name: String?,
    val expanded: Boolean,
    val items: List<ReviewCard> = emptyList(),
    val nextCursor: String? = null,
    val loaded: Boolean = false,
    val loading: Boolean = false,
)

data class HomeState(
    /** Группы для вкладок (только в ленте). */
    val groups: List<GroupSummary> = emptyList(),
    /** Выбранная вкладка: `null` — «Все». */
    val tab: String? = null,
    val query: String = "",
    val filter: ReviewFilter = ReviewFilter(),
    val sections: List<SectionUi> = emptyList(),
    /** Результаты поиска — плоский список по релевантности, без секций. */
    val results: List<ReviewCard> = emptyList(),
    val resultsCursor: String? = null,
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val offline: Boolean = false,
    val error: AppError? = null,
    val categories: Map<String, Category> = emptyMap(),
    val recent: List<String> = emptyList(),
    val popularTags: List<TagCount> = emptyList(),
    /** Сохранённые фильтры для быстрого выбора (без закреплённых категорий), по алфавиту. */
    val savedFilters: List<SavedFilter> = emptyList(),
) {
    val searching: Boolean get() = query.isNotBlank()

    /** Ничего не показано: загрузка закончилась, и записей нет. */
    val nothingShown: Boolean get() = !loading && if (searching) results.isEmpty() else sections.isEmpty()

    /** Пусто по-настоящему (а не ошибка загрузки): показывается пустое состояние с действиями. */
    val isEmpty: Boolean get() = nothingShown && error == null

    val groupNames: Map<String, String> get() = groups.associate { it.id to it.name }

    /** Подпись категории в строке: в секции категории — подкатегория (корень уже в заголовке), иначе — лист. */
    fun categoryLabel(
        card: ReviewCard,
        inCategorySection: Boolean,
    ): String? {
        val category = categories[card.categoryId] ?: return null
        return if (inCategorySection && category.parentId == null) null else category.name
    }
}

/**
 * Главный экран (И29): оглавление секций и подгрузка записей по секциям; поиск — плоский список по
 * релевантности. Фильтр панели запоминается для вкладки, свёрнутые секции — для вкладки и раздела.
 */
class HomeViewModel(
    private val area: HomeArea,
    private val repository: ReviewsRepository,
    private val reviews: ReviewsApi,
    private val groupsApi: GroupsApi,
    private val savedFilters: SavedFiltersApi,
    private val categorySet: CategoryRepository,
    private val cache: ResponseCache,
) : ViewModel() {
    private val _state = MutableStateFlow(HomeState())
    val state: StateFlow<HomeState> = _state.asStateFlow()

    private var loadJob: Job? = null

    init {
        viewModelScope.launch {
            val recent = cache.read<List<String>>(RECENT_KEY).orEmpty()
            val set = categorySet.set().getOrNull()
            _state.update { s ->
                s.copy(recent = recent, categories = set?.items?.associateBy { it.id } ?: s.categories)
            }
            if (area !is HomeArea.System) loadGroups()
            restoreFilter()
            reload()
        }
        viewModelScope.launch { repository.favoriteChanges.collect { markFavorite(it.reviewId, it.favorite) } }
        viewModelScope.launch {
            categorySet.updates.collect { set ->
                _state.update { it.copy(categories = set.items.associateBy { c -> c.id }) }
            }
        }
    }

    /**
     * Сердечко в строке: меняется сразу, при ошибке возвращается обратно. В «Избранном» убранная строка остаётся
     * до обновления — её можно вернуть.
     */
    fun toggleFavorite(card: ReviewCard) {
        val favorite = !card.favorite
        markFavorite(card.id, favorite)
        viewModelScope.launch {
            val result = repository.setFavorite(card.id, favorite)
            if (result.isFailure) {
                markFavorite(card.id, !favorite)
                _state.update { it.copy(error = result.appError()) }
            }
        }
    }

    private fun markFavorite(
        reviewId: String,
        favorite: Boolean,
    ) {
        val mark = { c: ReviewCard -> if (c.id == reviewId) c.copy(favorite = favorite) else c }
        _state.update { s ->
            s.copy(
                results = s.results.map(mark),
                sections = s.sections.map { section -> section.copy(items = section.items.map(mark)) },
            )
        }
    }

    private val scope: FeedScope
        get() =
            when (area) {
                is HomeArea.System -> FeedScope.Group(area.groupId)
                else -> _state.value.tab?.let { FeedScope.Group(it) } ?: FeedScope.AllGroups
            }

    /** Группа для оглавления и счётчиков: `null` — «Все». */
    private val scopeGroup: String?
        get() = (scope as? FeedScope.Group)?.groupId

    private val scopeKey: String get() = scopeGroup ?: ALL

    private suspend fun loadGroups() {
        val result = groupsApi.list()
        result.getOrNull()?.let { list ->
            repository.retainGroups(list.map { it.id }.toSet())
            _state.update { s ->
                s.copy(groups = list, tab = s.tab?.takeIf { id -> list.any { it.id == id } })
            }
        }
    }

    private suspend fun restoreFilter() {
        val saved =
            if (area is HomeArea.Tagged) {
                ReviewFilter(tags = setOf(area.tag))
            } else {
                repository.savedFilter(scopeKey)
                    ?: ReviewFilter()
            }
        _state.update { it.copy(filter = saved.copy(query = ""), query = "") }
    }

    /** Загружает экран заново (оглавление и первые страницы развёрнутых секций или результаты поиска). */
    fun refresh() {
        _state.update { it.copy(refreshing = true) }
        viewModelScope.launch {
            if (area !is HomeArea.System) loadGroups()
            reload()
        }
    }

    private fun reload() {
        loadJob?.cancel()
        loadJob =
            viewModelScope.launch {
                _state.update { it.copy(loading = it.sections.isEmpty() && it.results.isEmpty(), error = null) }
                try {
                    if (_state.value.searching) loadResults() else loadToc()
                } finally {
                    // и при ошибке, и при отмене (новая загрузка заменила старую) скелетон не остаётся навсегда
                    _state.update { it.copy(loading = false, refreshing = false) }
                }
            }
    }

    private suspend fun loadToc() {
        val s = _state.value
        val result = repository.toc(scopeGroup, s.filter)
        val toc = result.getOrNull()
        if (toc == null) {
            _state.update { it.copy(error = result.appError(), offline = result.appError() == AppError.Network) }
            return
        }
        val collapsed = cache.read<Set<String>>(collapsedKey()).orEmpty()
        val sections = toc.value.sections.map { it.toUi(expanded = it.section !in collapsed) }
        _state.update { it.copy(sections = sections, results = emptyList(), offline = toc.fromCache) }
        sections.filter { it.expanded }.forEach { loadSection(it.section) }
    }

    private suspend fun loadResults() {
        val s = _state.value
        val result = repository.feed(scope, s.filter.copy(query = s.query), null)
        val page = result.getOrNull()
        _state.update {
            it.copy(
                results = page?.value?.items ?: it.results,
                resultsCursor = page?.value?.nextCursor,
                offline = page?.fromCache ?: (result.appError() == AppError.Network),
                error = result.appError(),
            )
        }
    }

    /** Первая или следующая страница секции; свёрнутые секции не грузятся. */
    fun loadMore(section: String) {
        val current = _state.value.sections.firstOrNull { it.section == section } ?: return
        if (current.loading || (current.loaded && current.nextCursor == null) || !current.expanded) return
        viewModelScope.launch { loadSection(section) }
    }

    private suspend fun loadSection(section: String) {
        val current = _state.value.sections.firstOrNull { it.section == section } ?: return
        if (current.loading) return
        updateSection(section) { it.copy(loading = true) }
        val cursor = if (current.loaded) current.nextCursor else null
        val result = repository.feed(scope, _state.value.filter, cursor, section)
        val page = result.getOrNull()?.value
        updateSection(section) {
            it.copy(
                loading = false,
                loaded = page != null || it.loaded,
                items =
                    if (page == null) {
                        it.items
                    } else if (cursor == null) {
                        page.items
                    } else {
                        it.items + page.items
                    },
                nextCursor = page?.nextCursor ?: it.nextCursor.takeIf { page == null },
            )
        }
        if (page == null) _state.update { it.copy(error = result.appError()) }
    }

    fun loadMoreResults() {
        val s = _state.value
        val cursor = s.resultsCursor ?: return
        viewModelScope.launch {
            val page =
                repository.feed(scope, s.filter.copy(query = s.query), cursor).getOrNull()?.value ?: return@launch
            _state.update { it.copy(results = it.results + page.items, resultsCursor = page.nextCursor) }
        }
    }

    /** Нажатие на заголовок секции: свернуть или развернуть (запоминается для вкладки). */
    fun toggle(section: String) {
        updateSection(section) { it.copy(expanded = !it.expanded) }
        persistCollapsed()
        val now = _state.value.sections.first { it.section == section }
        if (now.expanded && !now.loaded) loadMore(section)
    }

    fun setAllExpanded(expanded: Boolean) {
        _state.update { s -> s.copy(sections = s.sections.map { it.copy(expanded = expanded) }) }
        persistCollapsed()
        if (expanded) {
            _state.value.sections
                .filter { !it.loaded }
                .forEach { loadMore(it.section) }
        }
    }

    private fun persistCollapsed() {
        val collapsed =
            _state.value.sections
                .filter { !it.expanded }
                .map { it.section }
                .toSet()
        viewModelScope.launch { cache.write(collapsedKey(), collapsed) }
    }

    fun selectTab(groupId: String?) {
        if (groupId == _state.value.tab) return
        _state.update { it.copy(tab = groupId, sections = emptyList(), results = emptyList(), loading = true) }
        viewModelScope.launch {
            restoreFilter()
            reload()
        }
    }

    /** Поиск (действие клавиатуры): запрос попадает в недавние, выдача — по релевантности. */
    fun search(query: String) {
        val q = query.trim()
        _state.update { it.copy(query = q, results = emptyList()) }
        if (q.isNotEmpty()) rememberQuery(q)
        reload()
    }

    fun forgetQuery(query: String) {
        val recent = _state.value.recent - query
        _state.update { it.copy(recent = recent) }
        viewModelScope.launch { cache.write(RECENT_KEY, recent) }
    }

    private fun rememberQuery(query: String) {
        val recent = (listOf(query) + _state.value.recent.filter { it != query }).take(RECENT_MAX)
        _state.update { it.copy(recent = recent) }
        viewModelScope.launch { cache.write(RECENT_KEY, recent) }
    }

    /** Популярные теги вкладки для подсказок поиска (И9). */
    fun loadSuggestions() {
        loadSavedFilters()
        viewModelScope.launch {
            val facets = reviews.facets(scopeGroup).getOrNull() ?: return@launch
            _state.update { it.copy(popularTags = facets.tags.take(SUGGESTED_TAGS)) }
        }
    }

    /** Сохранённые фильтры для быстрого выбора: в подсказках поиска и по долгому нажатию на кнопку фильтров. */
    fun loadSavedFilters() {
        viewModelScope.launch {
            val list = savedFilters.list().getOrNull() ?: return@launch
            _state.update { s ->
                s.copy(
                    savedFilters = list.filter { it.kind == SavedFilterKind.FILTER }.sortedBy { it.name?.lowercase() },
                )
            }
        }
    }

    /** Применяет сохранённый фильтр целиком: условия панели и, если сохранён, поисковый запрос. */
    fun applySaved(filter: SavedFilter) {
        val definition = filter.definition ?: return
        val q = definition.q?.trim().orEmpty()
        if (q.isNotEmpty()) rememberQuery(q)
        _state.update { it.copy(query = q, results = emptyList()) }
        val panel = definition.toFilter()
        if (panel == _state.value.filter) reload() else setFilter(panel)
    }

    /** Новый фильтр панели: запоминается для вкладки, лента перезагружается. */
    fun setFilter(filter: ReviewFilter) {
        val clean = filter.copy(query = "")
        if (clean == _state.value.filter) return
        _state.update { it.copy(filter = clean, sections = emptyList(), results = emptyList()) }
        viewModelScope.launch {
            if (area !is HomeArea.Tagged) repository.saveFilter(scopeKey, clean)
            reload()
        }
    }

    fun pinCategory(categoryId: String) =
        changePins { savedFilters.create(CreateSavedFilterRequest(SavedFilterKind.CATEGORY, categoryId = categoryId)) }

    /** Открепить категорию или убрать фильтр из ленты (сам фильтр остаётся в сохранённых). */
    fun unpin(section: SectionUi) =
        changePins {
            val id = section.filterId ?: return@changePins Result.success(Unit)
            if (section.kind == FeedSectionKind.CATEGORY) {
                savedFilters.delete(id)
            } else {
                savedFilters.update(id, app.podgon.contract.UpdateSavedFilterRequest(inFeed = false))
            }
        }

    private fun changePins(call: suspend () -> Result<*>) {
        viewModelScope.launch {
            val result = call()
            if (result.isFailure) _state.update { it.copy(error = result.appError()) } else reload()
        }
    }

    private fun updateSection(
        section: String,
        change: (SectionUi) -> SectionUi,
    ) = _state.update { s -> s.copy(sections = s.sections.map { if (it.section == section) change(it) else it }) }

    private fun collapsedKey() = "collapsed:${if (area is HomeArea.System) "system" else "feed"}:$scopeKey"

    private fun FeedSection.toUi(expanded: Boolean) =
        SectionUi(section, kind, pinned, count, categoryId, filterId, name, expanded)

    private companion object {
        const val ALL = "all"
        const val RECENT_KEY = "recent-queries"
        const val RECENT_MAX = 5
        const val SUGGESTED_TAGS = 12
    }
}
