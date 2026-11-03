package app.podgon.mobile.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.podgon.contract.SavedFilter
import app.podgon.contract.SavedFilterKind
import app.podgon.mobile.designsystem.Divider
import app.podgon.mobile.designsystem.PodgonIconButton
import app.podgon.mobile.designsystem.PodgonIcons
import app.podgon.mobile.designsystem.PodgonShapes
import app.podgon.mobile.designsystem.podgonColors
import app.podgon.mobile.designsystem.podgonElevation
import app.podgon.mobile.feature.groups.RetryError
import app.podgon.mobile.resources.Res
import app.podgon.mobile.resources.a11y_back
import app.podgon.mobile.resources.home_pin
import app.podgon.mobile.resources.home_unpin
import app.podgon.mobile.resources.sections_categories
import app.podgon.mobile.resources.sections_hint
import app.podgon.mobile.resources.sections_move_down
import app.podgon.mobile.resources.sections_move_up
import app.podgon.mobile.resources.sections_pinned
import app.podgon.mobile.resources.sections_saved
import app.podgon.mobile.resources.settings_sections
import org.jetbrains.compose.resources.stringResource

private val ROW_HEIGHT = 56.dp

/** «Секции ленты»: закреплённые (перетаскивание за ручку в пределах своей группы), сохранённые фильтры, категории. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SectionsScreen(
    viewModel: SectionsViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val c = podgonColors
    Column(Modifier.fillMaxSize().background(c.background).verticalScroll(rememberScrollState())) {
        Row(Modifier.padding(start = 8.dp, top = 12.dp, end = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            PodgonIconButton(PodgonIcons.Back, stringResource(Res.string.a11y_back), onBack)
            Text(
                stringResource(Res.string.settings_sections),
                style = MaterialTheme.typography.headlineSmall,
                color = c.ink,
            )
        }
        Text(
            stringResource(Res.string.sections_hint),
            fontSize = 13.sp,
            color = c.muted,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        )
        state.error?.let { RetryError(it) {} }
        Block(stringResource(Res.string.sections_pinned)) {
            Reorderable(state.pinnedFilters, SavedFilterKind.FILTER, viewModel)
            if (state.pinnedFilters.isNotEmpty() && state.pinnedCategories.isNotEmpty()) Divider()
            Reorderable(state.pinnedCategories, SavedFilterKind.CATEGORY, viewModel) { entry ->
                state.categories
                    .firstOrNull { it.id == entry.categoryId }
                    ?.name
                    .orEmpty()
            }
        }
        if (state.unpinnedFilters.isNotEmpty()) {
            Block(stringResource(Res.string.sections_saved)) {
                state.unpinnedFilters.forEachIndexed { i, f ->
                    if (i > 0) Divider()
                    EntryRow(PodgonIcons.Bookmark, f.name.orEmpty(), handle = null) {
                        PodgonIconButton(
                            PodgonIcons.Pin,
                            stringResource(Res.string.home_pin),
                            { viewModel.pinFilter(f) },
                            tint = c.muted,
                        )
                    }
                }
            }
        }
        Block(stringResource(Res.string.sections_categories)) {
            FlowRow(
                Modifier.padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                state.unpinnedRoots.forEach { cat ->
                    Box(
                        Modifier
                            .height(32.dp)
                            .clip(CircleShape)
                            .background(c.chip)
                            .combinedClickable(role = Role.Button, onClickLabel = stringResource(Res.string.home_pin)) {
                                viewModel.pinCategory(cat.id)
                            }.padding(horizontal = 12.dp),
                        contentAlignment = Alignment.Center,
                    ) { Text(cat.name, style = MaterialTheme.typography.labelMedium, color = c.ink) }
                }
            }
        }
        Box(Modifier.height(120.dp))
    }
}

@Composable
private fun Block(
    title: String,
    content: @Composable () -> Unit,
) {
    val c = podgonColors
    Text(
        title,
        fontSize = 13.sp,
        color = c.muted,
        modifier = Modifier.padding(start = 20.dp, top = 14.dp, bottom = 6.dp),
    )
    Column(Modifier.padding(horizontal = 16.dp).fillMaxWidth().podgonElevation(PodgonShapes.card, 4.dp)) { content() }
}

/** Закреплённые одного вида: ручка перетаскивания; для диктора — действия «выше» и «ниже». */
@Composable
private fun Reorderable(
    items: List<SavedFilter>,
    kind: SavedFilterKind,
    viewModel: SectionsViewModel,
    title: (SavedFilter) -> String = { it.name.orEmpty() },
) {
    val c = podgonColors
    val rowPx = with(LocalDensity.current) { ROW_HEIGHT.toPx() }
    var dragging by remember { mutableIntStateOf(-1) }
    var offset by remember { mutableFloatStateOf(0f) }
    val up = stringResource(Res.string.sections_move_up)
    val down = stringResource(Res.string.sections_move_down)
    items.forEachIndexed { index, entry ->
        if (index > 0) Divider()
        val handle =
            Modifier.pointerInput(items) {
                detectDragGestures(
                    onDragStart = {
                        dragging = index
                        offset = 0f
                    },
                    onDragEnd = {
                        dragging = -1
                        offset = 0f
                        viewModel.commitOrder()
                    },
                    onDragCancel = {
                        dragging = -1
                        offset = 0f
                    },
                ) { change, amount ->
                    change.consume()
                    offset += amount.y
                    val steps = (offset / rowPx).toInt()
                    if (steps != 0 && dragging >= 0) {
                        val target = (dragging + steps).coerceIn(0, items.lastIndex)
                        viewModel.move(kind, dragging, target)
                        offset -= (target - dragging) * rowPx
                        dragging = target
                    }
                }
            }
        Box(
            Modifier
                .graphicsLayer { translationY = if (index == dragging) offset else 0f }
                .semantics {
                    customActions =
                        listOf(
                            CustomAccessibilityAction(up) {
                                viewModel.move(kind, index, index - 1)
                                viewModel.commitOrder()
                                true
                            },
                            CustomAccessibilityAction(down) {
                                viewModel.move(kind, index, index + 1)
                                viewModel.commitOrder()
                                true
                            },
                        )
                },
        ) {
            EntryRow(
                if (kind ==
                    SavedFilterKind.FILTER
                ) {
                    PodgonIcons.BookmarkFilled
                } else {
                    PodgonIcons.Pin
                },
                title(entry),
                handle,
            ) {
                PodgonIconButton(PodgonIcons.Close, stringResource(Res.string.home_unpin), {
                    viewModel.unpin(entry)
                }, tint = c.muted, iconSize = 16.dp)
            }
        }
    }
}

@Composable
private fun EntryRow(
    icon: ImageVector,
    title: String,
    handle: Modifier?,
    trailing: @Composable () -> Unit,
) {
    val c = podgonColors
    Row(
        Modifier.fillMaxWidth().height(ROW_HEIGHT).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (handle != null) {
            Box(handle.size(36.dp), contentAlignment = Alignment.Center) {
                Icon(PodgonIcons.Order, null, tint = c.muted, modifier = Modifier.size(18.dp))
            }
        }
        Icon(icon, null, tint = c.accent, modifier = Modifier.padding(horizontal = 8.dp).size(15.dp))
        Text(title, style = MaterialTheme.typography.bodyMedium, color = c.ink, modifier = Modifier.weight(1f))
        trailing()
    }
}
