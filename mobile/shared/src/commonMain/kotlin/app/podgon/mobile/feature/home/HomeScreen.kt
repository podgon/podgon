package app.podgon.mobile.feature.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.podgon.contract.FeedSectionKind
import app.podgon.contract.GroupSummary
import app.podgon.contract.ReviewCard
import app.podgon.mobile.designsystem.Avatar
import app.podgon.mobile.designsystem.BottomBarSpace
import app.podgon.mobile.designsystem.EmptyAction
import app.podgon.mobile.designsystem.EmptyState
import app.podgon.mobile.designsystem.FeedSkeleton
import app.podgon.mobile.designsystem.FilterChipsRow
import app.podgon.mobile.designsystem.PillTab
import app.podgon.mobile.designsystem.PillTabs
import app.podgon.mobile.designsystem.PodgonBottomSheet
import app.podgon.mobile.designsystem.PodgonIconButton
import app.podgon.mobile.designsystem.PodgonIcons
import app.podgon.mobile.designsystem.PodgonTopBar
import app.podgon.mobile.designsystem.ReviewRow
import app.podgon.mobile.designsystem.SearchRow
import app.podgon.mobile.designsystem.SectionHeader
import app.podgon.mobile.designsystem.SectionMark
import app.podgon.mobile.designsystem.SheetAction
import app.podgon.mobile.designsystem.podgonColors
import app.podgon.mobile.designsystem.podgonElevation
import app.podgon.mobile.feature.groups.RetryError
import app.podgon.mobile.feature.reviews.ReviewFilter
import app.podgon.mobile.resources.Res
import app.podgon.mobile.resources.a11y_close
import app.podgon.mobile.resources.a11y_group_menu
import app.podgon.mobile.resources.favorites_stub_text
import app.podgon.mobile.resources.favorites_stub_title
import app.podgon.mobile.resources.filters_saved
import app.podgon.mobile.resources.filters_scope_all
import app.podgon.mobile.resources.filters_scope_favorites
import app.podgon.mobile.resources.filters_scope_group
import app.podgon.mobile.resources.filters_scope_personal
import app.podgon.mobile.resources.group_member_count
import app.podgon.mobile.resources.home_add_review
import app.podgon.mobile.resources.home_authors_chip
import app.podgon.mobile.resources.home_collapse_all
import app.podgon.mobile.resources.home_create_group
import app.podgon.mobile.resources.home_edit_filter
import app.podgon.mobile.resources.home_empty_feed_text
import app.podgon.mobile.resources.home_empty_feed_title
import app.podgon.mobile.resources.home_empty_group_text
import app.podgon.mobile.resources.home_empty_group_title
import app.podgon.mobile.resources.home_empty_search_text
import app.podgon.mobile.resources.home_empty_search_title
import app.podgon.mobile.resources.home_expand_all
import app.podgon.mobile.resources.home_group_page
import app.podgon.mobile.resources.home_invite
import app.podgon.mobile.resources.home_invite_members
import app.podgon.mobile.resources.home_join_by_code
import app.podgon.mobile.resources.home_new_review
import app.podgon.mobile.resources.home_new_user_text
import app.podgon.mobile.resources.home_new_user_title
import app.podgon.mobile.resources.home_pin
import app.podgon.mobile.resources.home_popular_tags
import app.podgon.mobile.resources.home_popular_tags_all
import app.podgon.mobile.resources.home_recent
import app.podgon.mobile.resources.home_reset_filters
import app.podgon.mobile.resources.home_saved_filters_empty
import app.podgon.mobile.resources.home_section_count
import app.podgon.mobile.resources.home_section_order
import app.podgon.mobile.resources.home_title_only_chip
import app.podgon.mobile.resources.home_unpin
import app.podgon.mobile.resources.months_short
import app.podgon.mobile.resources.nav_favorites
import app.podgon.mobile.resources.nav_personal
import app.podgon.mobile.resources.personal_empty_text
import app.podgon.mobile.resources.personal_empty_title
import app.podgon.mobile.resources.tab_all
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/** Действия главного экрана, которые ведут на другие экраны. */
class HomeActions(
    val openReview: (String) -> Unit,
    val openProfile: () -> Unit,
    val openGroup: (String) -> Unit,
    val invite: (String) -> Unit,
    val openSections: () -> Unit,
    val newReview: () -> Unit,
    val joinGroup: () -> Unit,
    val createGroup: () -> Unit,
    val onTabChanged: (String?) -> Unit,
)

/** Главный экран ленты, «Личного» и «Избранного» (`docs/design.md`, «Главный экран», макет `Main`). */
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    area: HomeArea,
    actions: HomeActions,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val c = podgonColors
    val months = stringResource(Res.string.months_short).split(',')
    var query by remember(state.query) { mutableStateOf(state.query) }
    var focused by remember { mutableStateOf(false) }
    var sectionMenu by remember { mutableStateOf<SectionUi?>(null) }
    var groupMenu by remember { mutableStateOf<GroupSummary?>(null) }
    var filtersOpen by remember { mutableStateOf(false) }
    var savedOpen by remember { mutableStateOf(false) }
    LaunchedEffect(state.tab) { actions.onTabChanged(state.tab) }

    Column(Modifier.fillMaxSize().background(c.background)) {
        PodgonTopBar(offline = state.offline, onNotifications = {}, onProfile = actions.openProfile)
        SearchRow(
            query = query,
            onQueryChange = {
                query = it
                if (it.isEmpty() && state.searching) viewModel.search("")
            },
            onSearch = { viewModel.search(query) },
            activeFilters = state.filter.panelCount,
            onFilters = { filtersOpen = true },
            onFocusChange = {
                focused = it
                if (it) viewModel.loadSuggestions()
            },
            onFiltersLongPress = {
                viewModel.loadSavedFilters()
                savedOpen = true
            },
        )
        FilterChips(state, viewModel)
        Box(Modifier.weight(1f)) {
            Column {
                when (area) {
                    HomeArea.Feed, is HomeArea.Tagged ->
                        PillTabs(
                            listOf(PillTab(ALL_TAB, stringResource(Res.string.tab_all))) +
                                state.groups.map { PillTab(it.id, it.name) },
                            selectedId = state.tab ?: ALL_TAB,
                            onSelect = { viewModel.selectTab(it.takeIf { id -> id != ALL_TAB }) },
                            onLongPress = { id -> groupMenu = state.groups.firstOrNull { it.id == id } },
                            longPressLabel = stringResource(Res.string.a11y_group_menu),
                        )
                    is HomeArea.System ->
                        Text(
                            stringResource(if (area.favorites) Res.string.nav_favorites else Res.string.nav_personal),
                            style = MaterialTheme.typography.headlineSmall,
                            color = c.ink,
                            modifier = Modifier.padding(start = 20.dp, top = 8.dp, bottom = 10.dp),
                        )
                }
                // без сети при сохранённых данных достаточно значка «офлайн»; если показать нечего — ошибка с повтором
                state.error?.takeIf { !state.offline || state.nothingShown }?.let { RetryError(it, viewModel::refresh) }
                PullToRefreshBox(
                    isRefreshing = state.refreshing,
                    onRefresh = viewModel::refresh,
                    modifier =
                        Modifier
                            .weight(
                                1f,
                            ).then(if (area is HomeArea.System) Modifier else Modifier.swipeTabs(state, viewModel)),
                ) {
                    FeedList(state, area, viewModel, actions, months) { sectionMenu = it }
                }
            }
            if (focused && query.isEmpty()) Suggestions(state, viewModel) { query = it }
        }
    }
    sectionMenu?.let { section ->
        SectionMenu(section, state, viewModel, actions, onEditFilter = { filtersOpen = true }) { sectionMenu = null }
    }
    if (filtersOpen) {
        val groupId = (area as? HomeArea.System)?.groupId ?: state.tab
        FiltersPanel(
            koinViewModel(key = "filters:$groupId:${state.filter.hashCode()}", parameters = {
                parametersOf(groupId, state.filter)
            }),
            scope =
                when {
                    area is HomeArea.System && area.favorites -> stringResource(Res.string.filters_scope_favorites)
                    area is HomeArea.System -> stringResource(Res.string.filters_scope_personal)
                    state.tab == null -> stringResource(Res.string.filters_scope_all)
                    else -> stringResource(Res.string.filters_scope_group, state.groupNames[state.tab].orEmpty())
                },
            showAuthors = !(area is HomeArea.System && !area.favorites),
            onApply = {
                viewModel.setFilter(it)
                filtersOpen = false
            },
            onDismiss = { filtersOpen = false },
        )
    }
    if (savedOpen) {
        PodgonBottomSheet(
            title = stringResource(Res.string.filters_saved),
            subtitle = if (state.savedFilters.isEmpty()) stringResource(Res.string.home_saved_filters_empty) else null,
            onDismiss = { savedOpen = false },
            actions =
                state.savedFilters.map { f ->
                    SheetAction(PodgonIcons.Bookmark, f.name.orEmpty()) { viewModel.applySaved(f) }
                },
        )
    }
    groupMenu?.let { group ->
        PodgonBottomSheet(
            title = group.name,
            subtitle = stringResource(Res.string.group_member_count, group.memberCount),
            onDismiss = { groupMenu = null },
            leading = { Avatar(38.dp, group.name) },
            actions =
                listOfNotNull(
                    SheetAction(
                        PodgonIcons.Group,
                        stringResource(Res.string.home_group_page),
                    ) { actions.openGroup(group.id) },
                    if (group.myRole.admin) {
                        SheetAction(
                            PodgonIcons.Invite,
                            stringResource(Res.string.home_invite),
                        ) { actions.invite(group.id) }
                    } else {
                        null
                    },
                ),
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FeedList(
    state: HomeState,
    area: HomeArea,
    viewModel: HomeViewModel,
    actions: HomeActions,
    months: List<String>,
    onSectionMenu: (SectionUi) -> Unit,
) {
    val showAuthor = !(area is HomeArea.System && !area.favorites)
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = BottomBarSpace)) {
        if (state.loading) {
            item { FeedSkeleton() }
            return@LazyColumn
        }
        if (state.isEmpty) {
            item { Empty(state, area, actions, viewModel) }
            return@LazyColumn
        }
        if (state.searching) {
            items(state.results, key = { "r:" + it.id }) { card ->
                Row(card, state, months, showAuthor, inCategory = false, actions, viewModel::toggleFavorite)
            }
            if (state.resultsCursor != null) item { LaunchedEffect(state.results.size) { viewModel.loadMoreResults() } }
            return@LazyColumn
        }
        state.sections.forEach { section ->
            stickyHeader(key = "h:" + section.section) {
                SectionHeader(
                    name = sectionName(section, state),
                    count = section.count,
                    expanded = section.expanded,
                    onToggle = { viewModel.toggle(section.section) },
                    mark =
                        when {
                            section.kind == FeedSectionKind.FILTER -> SectionMark.FILTER
                            section.pinned -> SectionMark.PINNED
                            else -> SectionMark.NONE
                        },
                    onLongPress = { onSectionMenu(section) },
                )
            }
            if (section.expanded) {
                items(section.items, key = { section.section + ":" + it.id }) { card ->
                    Row(
                        card,
                        state,
                        months,
                        showAuthor,
                        inCategory = section.kind == FeedSectionKind.CATEGORY,
                        actions,
                        viewModel::toggleFavorite,
                    )
                }
                if (section.nextCursor != null || !section.loaded) {
                    item(key = "more:" + section.section) {
                        LaunchedEffect(section.items.size, section.loaded) { viewModel.loadMore(section.section) }
                        Box(Modifier.fillMaxWidth().height(if (section.loaded) 8.dp else 64.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun Row(
    card: ReviewCard,
    state: HomeState,
    months: List<String>,
    showAuthor: Boolean,
    inCategory: Boolean,
    actions: HomeActions,
    onFavorite: (ReviewCard) -> Unit,
) {
    val author = card.author.displayName.ifBlank { card.author.username }
    ReviewRow(
        title = card.title,
        rating = card.rating,
        onClick = { actions.openReview(card.id) },
        category = state.categoryLabel(card, inCategory),
        author = author.takeIf { showAuthor },
        date = shortDate(card.sharedAt, months),
        favorite = card.favorite,
        onFavorite = { onFavorite(card) },
    )
}

@Composable
private fun sectionName(
    section: SectionUi,
    state: HomeState,
): String = section.name ?: section.categoryId?.let { state.categories[it]?.name } ?: ""

@Composable
private fun Empty(
    state: HomeState,
    area: HomeArea,
    actions: HomeActions,
    viewModel: HomeViewModel,
) {
    if (state.searching || state.filter.panelCount > 0) {
        EmptyState(
            stringResource(Res.string.home_empty_search_title),
            stringResource(Res.string.home_empty_search_text),
            actions =
                if (state.filter.panelCount > 0) {
                    listOf(
                        EmptyAction(
                            stringResource(Res.string.home_reset_filters),
                            true,
                        ) { viewModel.setFilter(ReviewFilter()) },
                    )
                } else {
                    emptyList()
                },
        )
        return
    }
    val group = state.groups.firstOrNull { it.id == state.tab }
    when {
        area is HomeArea.System && area.favorites ->
            EmptyState(stringResource(Res.string.favorites_stub_title), stringResource(Res.string.favorites_stub_text))
        area is HomeArea.System ->
            EmptyState(
                stringResource(Res.string.personal_empty_title),
                stringResource(Res.string.personal_empty_text),
                actions = listOf(EmptyAction(stringResource(Res.string.home_new_review), true, actions.newReview)),
            )
        group != null ->
            EmptyState(
                stringResource(Res.string.home_empty_group_title),
                stringResource(Res.string.home_empty_group_text),
                actions =
                    listOfNotNull(
                        EmptyAction(stringResource(Res.string.home_add_review), true, actions.newReview),
                        if (group.myRole.admin) {
                            EmptyAction(stringResource(Res.string.home_invite_members), false) {
                                actions.invite(group.id)
                            }
                        } else {
                            null
                        },
                    ),
            )
        state.groups.isEmpty() ->
            EmptyState(
                stringResource(Res.string.home_new_user_title),
                stringResource(Res.string.home_new_user_text),
                actions =
                    listOf(
                        EmptyAction(stringResource(Res.string.home_join_by_code), true, actions.joinGroup),
                        EmptyAction(stringResource(Res.string.home_create_group), false, actions.createGroup),
                    ),
            )
        else ->
            EmptyState(
                stringResource(Res.string.home_empty_feed_title),
                stringResource(Res.string.home_empty_feed_text),
                actions = listOf(EmptyAction(stringResource(Res.string.home_new_review), true, actions.newReview)),
            )
    }
}

/** Свайп влево-вправо по ленте — соседняя вкладка (И6). */
private fun Modifier.swipeTabs(
    state: HomeState,
    viewModel: HomeViewModel,
): Modifier =
    pointerInput(state.tab, state.groups) {
        var total = 0f
        detectHorizontalDragGestures(
            onDragStart = { total = 0f },
            onDragEnd = {
                val ids = listOf<String?>(null) + state.groups.map { it.id }
                val index = ids.indexOf(state.tab)
                val threshold = SWIPE_THRESHOLD.toPx()
                val target =
                    when {
                        total < -threshold -> index + 1
                        total > threshold -> index - 1
                        else -> index
                    }
                if (target != index && target in ids.indices) viewModel.selectTab(ids[target])
            },
        ) { _, amount -> total += amount }
    }

private val SWIPE_THRESHOLD = 80.dp

/** Активные фильтры чипами (И10): снимаются по одному. */
@Composable
private fun FilterChips(
    state: HomeState,
    viewModel: HomeViewModel,
) {
    val f = state.filter
    val chips = mutableListOf<Pair<String, ReviewFilter>>()
    f.categoryIds.forEach { id ->
        chips +=
            (state.categories[id]?.name ?: "…") to f.copy(categoryIds = f.categoryIds - id)
    }
    f.tags.forEach { tag -> chips += "#$tag" to f.copy(tags = f.tags - tag) }
    if (f.authorIds.isNotEmpty()) {
        chips +=
            stringResource(Res.string.home_authors_chip, f.authorIds.size) to f.copy(authorIds = emptySet())
    }
    if (f.minRating != null || f.maxRating != null) {
        chips += "★ ${f.minRating ?: 1}–${f.maxRating ?: 10}" to f.copy(minRating = null, maxRating = null)
    }
    if (f.titleOnly) chips += stringResource(Res.string.home_title_only_chip) to f.copy(titleOnly = false)
    FilterChipsRow(chips.map { it.first }, onRemove = { viewModel.setFilter(chips[it].second) })
}

/** Подсказки при фокусе в пустом поиске (И9): недавние запросы и популярные теги вкладки. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun Suggestions(
    state: HomeState,
    viewModel: HomeViewModel,
    onPick: (String) -> Unit,
) {
    val c = podgonColors
    if (state.recent.isEmpty() && state.popularTags.isEmpty() && state.savedFilters.isEmpty()) return
    Column(
        Modifier
            .padding(
                horizontal = 16.dp,
            ).fillMaxWidth()
            .podgonElevation(RoundedCornerShape(22.dp), 10.dp)
            .padding(vertical = 10.dp, horizontal = 6.dp),
    ) {
        if (state.recent.isNotEmpty()) {
            Text(
                stringResource(Res.string.home_recent),
                fontSize = 12.sp,
                fontWeight = FontWeight.ExtraBold,
                color = c.muted,
                modifier = Modifier.padding(start = 10.dp, bottom = 4.dp),
            )
            state.recent.forEach { q ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Row(
                        Modifier
                            .weight(1f)
                            .heightIn(min = 40.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .combinedClickable(role = Role.Button) {
                                onPick(q)
                                viewModel.search(q)
                            }.padding(horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Icon(PodgonIcons.History, null, tint = c.muted, modifier = Modifier.size(17.dp))
                        Text(q, style = MaterialTheme.typography.bodyMedium, color = c.ink, maxLines = 1)
                    }
                    PodgonIconButton(PodgonIcons.Close, stringResource(Res.string.a11y_close), {
                        viewModel.forgetQuery(q)
                    }, size = 36.dp, iconSize = 13.dp, tint = c.muted)
                }
            }
        }
        if (state.popularTags.isNotEmpty()) {
            Text(
                stringResource(
                    if (state.tab ==
                        null
                    ) {
                        Res.string.home_popular_tags_all
                    } else {
                        Res.string.home_popular_tags
                    },
                ),
                fontSize = 12.sp,
                fontWeight = FontWeight.ExtraBold,
                color = c.muted,
                modifier = Modifier.padding(start = 10.dp, top = 10.dp, bottom = 6.dp),
            )
            FlowRow(
                Modifier.padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                state.popularTags.forEach { tag ->
                    Box(
                        Modifier
                            .height(30.dp)
                            .clip(CircleShape)
                            .background(c.chip)
                            .combinedClickable(role = Role.Button) {
                                viewModel.setFilter(
                                    state.filter.copy(
                                        tags =
                                            state.filter.tags + tag.name,
                                    ),
                                )
                            }.padding(horizontal = 11.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("#${tag.name}", style = MaterialTheme.typography.labelMedium, color = c.ink)
                    }
                }
            }
        }
        if (state.savedFilters.isNotEmpty()) {
            Text(
                stringResource(Res.string.filters_saved),
                fontSize = 12.sp,
                fontWeight = FontWeight.ExtraBold,
                color = c.muted,
                modifier = Modifier.padding(start = 10.dp, top = 10.dp, bottom = 6.dp),
            )
            FlowRow(
                Modifier.padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                state.savedFilters.forEach { f ->
                    Row(
                        Modifier
                            .height(30.dp)
                            .clip(CircleShape)
                            .background(c.chip)
                            .combinedClickable(role = Role.Button) {
                                f.definition?.q?.let(onPick)
                                viewModel.applySaved(f)
                            }.padding(horizontal = 11.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(PodgonIcons.Bookmark, null, tint = c.accent, modifier = Modifier.size(13.dp))
                        Text(f.name.orEmpty(), style = MaterialTheme.typography.labelMedium, color = c.ink)
                    }
                }
            }
        }
    }
}

/** Меню секции (И27): закрепить или открепить, свернуть или развернуть все, изменить фильтр, порядок секций. */
@Composable
private fun SectionMenu(
    section: SectionUi,
    state: HomeState,
    viewModel: HomeViewModel,
    actions: HomeActions,
    onEditFilter: () -> Unit,
    onDismiss: () -> Unit,
) {
    val anyExpanded = state.sections.any { it.expanded }
    PodgonBottomSheet(
        title = sectionName(section, state),
        subtitle = stringResource(Res.string.home_section_count, section.count),
        onDismiss = onDismiss,
        actions =
            listOfNotNull(
                when {
                    section.pinned ->
                        SheetAction(
                            PodgonIcons.Pin,
                            stringResource(Res.string.home_unpin),
                        ) { viewModel.unpin(section) }
                    section.categoryId != null ->
                        SheetAction(
                            PodgonIcons.Pin,
                            stringResource(Res.string.home_pin),
                        ) { viewModel.pinCategory(section.categoryId) }
                    else -> null
                },
                SheetAction(
                    PodgonIcons.Fold,
                    stringResource(if (anyExpanded) Res.string.home_collapse_all else Res.string.home_expand_all),
                ) { viewModel.setAllExpanded(!anyExpanded) },
                if (section.kind == FeedSectionKind.FILTER) {
                    SheetAction(PodgonIcons.Edit, stringResource(Res.string.home_edit_filter), onEditFilter)
                } else {
                    null
                },
                SheetAction(PodgonIcons.Order, stringResource(Res.string.home_section_order), actions.openSections),
            ),
    )
}

private const val ALL_TAB = "__all__"
