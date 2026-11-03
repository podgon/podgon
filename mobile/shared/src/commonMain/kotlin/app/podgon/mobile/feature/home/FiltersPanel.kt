package app.podgon.mobile.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.podgon.contract.SavedFilter
import app.podgon.mobile.designsystem.Avatar
import app.podgon.mobile.designsystem.Divider
import app.podgon.mobile.designsystem.PodgonIconButton
import app.podgon.mobile.designsystem.PodgonIcons
import app.podgon.mobile.designsystem.PodgonShapes
import app.podgon.mobile.designsystem.podgonColors
import app.podgon.mobile.designsystem.podgonElevation
import app.podgon.mobile.feature.reviews.ReviewFilter
import app.podgon.mobile.resources.Res
import app.podgon.mobile.resources.action_cancel
import app.podgon.mobile.resources.action_delete
import app.podgon.mobile.resources.action_save
import app.podgon.mobile.resources.filters_any
import app.podgon.mobile.resources.filters_apply
import app.podgon.mobile.resources.filters_authors
import app.podgon.mobile.resources.filters_categories
import app.podgon.mobile.resources.filters_categories_count
import app.podgon.mobile.resources.filters_find
import app.podgon.mobile.resources.filters_hide
import app.podgon.mobile.resources.filters_in_feed
import app.podgon.mobile.resources.filters_name
import app.podgon.mobile.resources.filters_rating
import app.podgon.mobile.resources.filters_rename
import app.podgon.mobile.resources.filters_reset
import app.podgon.mobile.resources.filters_save
import app.podgon.mobile.resources.filters_saved
import app.podgon.mobile.resources.filters_tag_all
import app.podgon.mobile.resources.filters_tag_any
import app.podgon.mobile.resources.filters_tags
import app.podgon.mobile.resources.filters_title
import app.podgon.mobile.resources.filters_title_only
import app.podgon.mobile.resources.filters_title_only_hint
import org.jetbrains.compose.resources.stringResource

/** Где действует фильтр — подпись в шапке («в группе „Друзья“», «во всех группах», «в Личном»). */
typealias ScopeLabel = String

/**
 * Панель фильтров на весь экран (модальная, без нижнего меню). «Применить» и «Сбросить» — круги внизу справа
 * поверх всего; «Сохранить фильтр» — вверху справа.
 */
@Composable
fun FiltersPanel(
    viewModel: FiltersViewModel,
    scope: ScopeLabel,
    showAuthors: Boolean,
    onApply: (ReviewFilter) -> Unit,
    onDismiss: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val c = podgonColors
    var saving by remember { mutableStateOf(false) }
    var savedOpen by remember { mutableStateOf(false) }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(c.background)
                .safeDrawingPadding()
                .imePadding(),
        ) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PodgonIconButton(PodgonIcons.ChevronDown, stringResource(Res.string.filters_hide), onDismiss)
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            stringResource(Res.string.filters_title),
                            style = MaterialTheme.typography.titleMedium,
                            color = c.ink,
                        )
                        Text(scope, fontSize = 12.sp, color = c.muted)
                    }
                    PodgonIconButton(PodgonIcons.Bookmark, stringResource(Res.string.filters_save), { saving = true })
                }
                SavedRow(state, onClick = { savedOpen = !savedOpen })
                if (savedOpen) SavedList(state.saved, viewModel) { savedOpen = false }
                Card {
                    TitleOnlyRow(state.draft.titleOnly, viewModel::setTitleOnly)
                }
                Card {
                    RatingCard(state.draft, viewModel::setRating)
                }
                val facets = state.facets
                val categoryItems =
                    facets?.categories.orEmpty().mapNotNull { count ->
                        state.categories[count.categoryId]?.let { cat ->
                            val parent = cat.parentId?.let { state.categories[it]?.name }
                            Choice(cat.id, cat.name, parent, count.count)
                        }
                    }
                ChoiceCard(
                    title = stringResource(Res.string.filters_categories),
                    items = categoryItems,
                    selected = state.draft.categoryIds,
                    labelOf = { id -> state.categories[id]?.name ?: "…" },
                    onToggle = viewModel::toggleCategory,
                )
                ChoiceCard(
                    title = stringResource(Res.string.filters_tags),
                    items = facets?.tags.orEmpty().map { Choice(it.name, "#${it.name}", null, it.count) },
                    selected = state.draft.tags,
                    labelOf = { "#$it" },
                    onToggle = viewModel::toggleTag,
                    trailingTitle =
                        if (state.draft.tags.size >= 2) {
                            { TagModeSwitch(state.draft.anyTag, viewModel::setAnyTag) }
                        } else {
                            null
                        },
                )
                if (showAuthors) {
                    val authors = facets?.authors.orEmpty()
                    ChoiceCard(
                        title = stringResource(Res.string.filters_authors),
                        items =
                            authors.map {
                                Choice(
                                    it.author.id,
                                    it.author.displayName.ifBlank { it.author.username },
                                    null,
                                    it.count,
                                )
                            },
                        selected = state.draft.authorIds,
                        labelOf = { id ->
                            authors.firstOrNull { it.author.id == id }?.author?.let { a ->
                                a.displayName.ifBlank { a.username }
                            }
                                ?: "…"
                        },
                        onToggle = viewModel::toggleAuthor,
                        avatars = true,
                    )
                }
                Spacer(Modifier.height(150.dp))
            }
            Column(
                Modifier.align(Alignment.BottomEnd).padding(end = 20.dp, bottom = 22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                PodgonIconButton(
                    PodgonIcons.Refresh,
                    stringResource(Res.string.filters_reset),
                    viewModel::reset,
                    outlined = true,
                    iconSize = 18.dp,
                )
                val apply = stringResource(Res.string.filters_apply)
                Box(
                    Modifier
                        .size(58.dp)
                        .clip(CircleShape)
                        .background(c.accent)
                        .combinedClickable(role = Role.Button, onClickLabel = apply) { onApply(state.draft) }
                        .semantics { contentDescription = apply },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(PodgonIcons.Check, null, tint = Color.White, modifier = Modifier.size(26.dp))
                }
            }
        }
    }
    if (saving) {
        NameDialog(
            title = stringResource(Res.string.filters_save),
            initial = defaultFilterName(state.draft, state.categories),
            onConfirm = {
                viewModel.save(it)
                saving = false
            },
            onDismiss = { saving = false },
        )
    }
}

@Composable
private fun Card(content: @Composable () -> Unit) {
    Column(
        Modifier
            .padding(
                horizontal = 16.dp,
                vertical = 5.dp,
            ).fillMaxWidth()
            .podgonElevation(PodgonShapes.card, 4.dp)
            .padding(14.dp),
    ) {
        content()
    }
}

/** Режим поиска «только по названиям» (без описаний). */
@Composable
private fun TitleOnlyRow(
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    val c = podgonColors
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(
                stringResource(Res.string.filters_title_only),
                style = MaterialTheme.typography.titleSmall,
                color = c.ink,
            )
            Text(stringResource(Res.string.filters_title_only_hint), fontSize = 12.sp, color = c.muted)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun SavedRow(
    state: FiltersState,
    onClick: () -> Unit,
) {
    val c = podgonColors
    Row(
        Modifier
            .padding(horizontal = 16.dp, vertical = 5.dp)
            .fillMaxWidth()
            .heightIn(min = 46.dp)
            .podgonElevation(CircleShape, 4.dp)
            .combinedClickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(PodgonIcons.Bookmark, null, tint = c.accent, modifier = Modifier.size(18.dp))
        Text(
            state.matching?.name ?: stringResource(Res.string.filters_saved),
            style = MaterialTheme.typography.labelLarge,
            color = c.ink,
            modifier = Modifier.weight(1f),
        )
        Icon(PodgonIcons.ChevronDown, null, tint = c.muted, modifier = Modifier.size(16.dp))
    }
}

/** Список сохранённых с поиском; у каждого — краткое содержимое и «В ленте»; долгое нажатие — переименовать или удалить. */
@Composable
private fun SavedList(
    saved: List<SavedFilter>,
    viewModel: FiltersViewModel,
    onPicked: () -> Unit,
) {
    val c = podgonColors
    var query by remember { mutableStateOf("") }
    var managing by remember { mutableStateOf<SavedFilter?>(null) }
    val inFeedLabel = stringResource(Res.string.filters_in_feed)
    Card {
        SearchField(query, { query = it }, Modifier.fillMaxWidth())
        saved.filter { it.name.orEmpty().contains(query, ignoreCase = true) }.forEach { f ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp)
                    .combinedClickable(
                        role = Role.Button,
                        onClick = {
                            viewModel.applySaved(f)
                            onPicked()
                        },
                        onLongClick = { managing = f },
                    ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(f.name.orEmpty(), style = MaterialTheme.typography.titleSmall, color = c.ink)
                    Text(summary(f), fontSize = 12.sp, color = c.muted, maxLines = 1)
                }
                Icon(PodgonIcons.Pin, null, tint = if (f.inFeed) c.accent else c.muted, modifier = Modifier.size(16.dp))
                Switch(
                    checked = f.inFeed,
                    onCheckedChange = { viewModel.setInFeed(f, it) },
                    modifier = Modifier.semantics { contentDescription = inFeedLabel },
                )
            }
            Divider()
        }
    }
    managing?.let { f ->
        var renaming by remember { mutableStateOf(false) }
        if (renaming) {
            NameDialog(stringResource(Res.string.filters_rename), f.name.orEmpty(), onConfirm = {
                viewModel.rename(f, it)
                managing = null
            }, onDismiss = { managing = null })
        } else {
            AlertDialog(
                onDismissRequest = { managing = null },
                title = { Text(f.name.orEmpty()) },
                confirmButton = {
                    TextButton(
                        onClick = { renaming = true },
                    ) { Text(stringResource(Res.string.filters_rename)) }
                },
                dismissButton = {
                    TextButton(onClick = {
                        viewModel.delete(f)
                        managing = null
                    }) { Text(stringResource(Res.string.action_delete)) }
                },
            )
        }
    }
}

@Composable
private fun summary(f: SavedFilter): String {
    val d = f.definition ?: return ""
    val categories =
        if (d.categoryIds.isEmpty()) {
            null
        } else {
            stringResource(
                Res.string.filters_categories_count,
                d.categoryIds.size,
            )
        }
    return buildList {
        categories?.let(::add)
        d.tags.forEach { add("#$it") }
        if (d.minRating != null || d.maxRating != null) add("${d.minRating ?: 1}–${d.maxRating ?: 10}")
    }.joinToString(" · ")
}

/** Оценка — одной строкой: название слева, шкала 1–10 с двумя ползунками справа. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RatingCard(
    filter: ReviewFilter,
    onChange: (Int, Int) -> Unit,
) {
    val c = podgonColors
    val min = filter.minRating ?: 1
    val max = filter.maxRating ?: 10
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.width(72.dp)) {
            Text(stringResource(Res.string.filters_rating), style = MaterialTheme.typography.titleSmall, color = c.ink)
            Text("$min–$max", fontSize = 12.sp, color = c.muted)
        }
        RangeSlider(
            value = min.toFloat()..max.toFloat(),
            onValueChange = { onChange(it.start.toInt(), it.endInclusive.toInt()) },
            valueRange = 1f..10f,
            steps = 8,
            // границы — числа внутри кружков-ползунков (макет)
            startThumb = { RatingThumb(min) },
            endThumb = { RatingThumb(max) },
            colors =
                SliderDefaults.colors(
                    thumbColor = c.accent,
                    activeTrackColor = c.accent,
                    inactiveTrackColor = c.chip,
                    activeTickColor = Color.White,
                    inactiveTickColor = c.muted,
                ),
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun RatingThumb(value: Int) {
    val c = podgonColors
    Box(Modifier.size(30.dp).clip(CircleShape).background(c.accent), contentAlignment = Alignment.Center) {
        Text("$value", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
    }
}

/** Значение в списке выбора: id, подпись, путь родителя и счётчик. */
private data class Choice(
    val id: String,
    val label: String,
    val parent: String?,
    val count: Int,
)

/**
 * Карточка «Категории / Теги / Авторы»: справа от названия — поиск; в карточке — только выбранные чипами
 * (ничего — «любые»); при фокусе поиска — список с флажками и счётчиками.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChoiceCard(
    title: String,
    items: List<Choice>,
    selected: Set<String>,
    labelOf: (String) -> String,
    onToggle: (String) -> Unit,
    trailingTitle: (@Composable () -> Unit)? = null,
    avatars: Boolean = false,
) {
    val c = podgonColors
    var query by remember { mutableStateOf("") }
    var focused by remember { mutableStateOf(false) }
    Card {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = c.ink)
            if (selected.isEmpty()) {
                Text(
                    "  " + stringResource(Res.string.filters_any),
                    fontSize = 13.sp,
                    color = c.muted,
                )
            }
            Spacer(Modifier.weight(1f))
            trailingTitle?.invoke()
            Spacer(Modifier.width(6.dp))
            SearchField(query, { query = it }, Modifier.width(164.dp).onFocusChanged { focused = it.isFocused })
        }
        if (selected.isNotEmpty()) {
            FlowRow(
                Modifier.padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                selected.forEach { id ->
                    Row(
                        Modifier
                            .height(
                                30.dp,
                            ).clip(
                                CircleShape,
                            ).background(c.chip)
                            .padding(start = if (avatars) 3.dp else 11.dp, end = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (avatars) {
                            Avatar(24.dp)
                            Spacer(Modifier.width(6.dp))
                        }
                        Text(labelOf(id), style = MaterialTheme.typography.labelMedium, color = c.ink)
                        PodgonIconButton(
                            PodgonIcons.Close,
                            labelOf(id),
                            { onToggle(id) },
                            size = 24.dp,
                            iconSize = 11.dp,
                            tint = c.muted,
                        )
                    }
                }
            }
        }
        if (focused) {
            val q = query.trim()
            val shown =
                items
                    .filter { q.isEmpty() || it.label.contains(q, true) || it.parent?.contains(q, true) == true }
                    .take(if (q.isEmpty()) TOP_ITEMS else Int.MAX_VALUE)
            Column(Modifier.padding(top = 8.dp)) {
                shown.forEach { item ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(
                                min = 44.dp,
                            ).combinedClickable(role = Role.Checkbox) { onToggle(item.id) },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = item.id in selected,
                            onCheckedChange = { onToggle(item.id) },
                            colors = CheckboxDefaults.colors(checkedColor = c.accent),
                        )
                        Column(Modifier.weight(1f)) {
                            Text(item.label, style = MaterialTheme.typography.bodyMedium, color = c.ink)
                            item.parent?.let { Text(it, fontSize = 12.sp, color = c.muted) }
                        }
                        Text(
                            "${item.count}",
                            fontSize = 12.sp,
                            color = c.muted,
                            modifier = Modifier.padding(end = 6.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TagModeSwitch(
    any: Boolean,
    onChange: (Boolean) -> Unit,
) {
    val c = podgonColors
    Row(Modifier.clip(CircleShape).background(c.chip).padding(2.dp)) {
        listOf(
            false to stringResource(Res.string.filters_tag_all),
            true to stringResource(Res.string.filters_tag_any),
        ).forEach { (value, label) ->
            Box(
                Modifier
                    .height(28.dp)
                    .clip(CircleShape)
                    .background(if (any == value) c.surface else Color.Transparent)
                    .combinedClickable(role = Role.RadioButton) { onChange(value) }
                    .padding(horizontal = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color =
                        if (any ==
                            value
                        ) {
                            c.ink
                        } else {
                            c.muted
                        },
                )
            }
        }
    }
}

@Composable
private fun SearchField(
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = podgonColors
    val hint = stringResource(Res.string.filters_find)
    Row(
        modifier
            .height(36.dp)
            .clip(CircleShape)
            .background(c.chip)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(PodgonIcons.Search, null, tint = c.muted, modifier = Modifier.size(15.dp))
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) Text(hint, fontSize = 14.sp, color = c.muted)
            BasicTextField(
                value = value,
                onValueChange = onChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = c.ink),
                cursorBrush = SolidColor(c.accent),
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = hint },
            )
        }
    }
}

@Composable
private fun NameDialog(
    title: String,
    initial: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(value = name, onValueChange = {
                name = it
            }, singleLine = true, label = { Text(stringResource(Res.string.filters_name)) })
        },
        confirmButton = {
            TextButton(onClick = {
                onConfirm(name)
            }, enabled = name.isNotBlank()) { Text(stringResource(Res.string.action_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.action_cancel)) } },
    )
}

private const val TOP_ITEMS = 8
