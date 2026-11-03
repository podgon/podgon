package app.podgon.mobile.designsystem

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.podgon.mobile.resources.Res
import app.podgon.mobile.resources.a11y_collapse
import app.podgon.mobile.resources.a11y_expand
import app.podgon.mobile.resources.a11y_favorite_add
import app.podgon.mobile.resources.a11y_favorite_remove
import app.podgon.mobile.resources.a11y_filters
import app.podgon.mobile.resources.a11y_filters_count
import app.podgon.mobile.resources.a11y_new_review
import app.podgon.mobile.resources.a11y_notifications
import app.podgon.mobile.resources.a11y_offline
import app.podgon.mobile.resources.a11y_pinned
import app.podgon.mobile.resources.a11y_profile
import app.podgon.mobile.resources.a11y_remove_filter
import app.podgon.mobile.resources.a11y_saved_filter
import app.podgon.mobile.resources.a11y_saved_filters
import app.podgon.mobile.resources.a11y_search
import app.podgon.mobile.resources.a11y_section_menu
import app.podgon.mobile.resources.app_name_short
import app.podgon.mobile.resources.nav_favorites
import app.podgon.mobile.resources.nav_feed
import app.podgon.mobile.resources.nav_personal
import app.podgon.mobile.resources.nav_settings
import app.podgon.mobile.resources.offline_hint
import app.podgon.mobile.resources.rating_value
import coil3.compose.AsyncImage
import org.jetbrains.compose.resources.stringResource

/** Мягкая тень капсул и карточек: только в светлой теме, в тёмной — линия (`docs/design.md`). */
@Composable
fun Modifier.podgonElevation(
    shape: androidx.compose.ui.graphics.Shape,
    elevation: Dp = 6.dp,
): Modifier {
    val c = podgonColors
    val shadowed =
        if (c.shadows) {
            shadow(
                elevation,
                shape,
                ambientColor = c.ink.copy(alpha = 0.10f),
                spotColor = c.ink.copy(alpha = 0.12f),
            )
        } else {
            this
        }
    return shadowed.clip(shape).background(c.surface).border(1.dp, c.line, shape)
}

/**
 * Кнопка-иконка 44×44 без подписи: подсказка при долгом нажатии и описание для диктора (TalkBack, VoiceOver).
 * [badge] — число в акцентном кружке (например, включённые фильтры).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PodgonIconButton(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    iconSize: Dp = 22.dp,
    tint: Color = podgonColors.ink,
    outlined: Boolean = false,
    badge: Int? = null,
    onLongClick: (() -> Unit)? = null,
    longClickLabel: String? = null,
) {
    val c = podgonColors
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
        tooltip = { PlainTooltip { Text(description) } },
        state = rememberTooltipState(),
    ) {
        Box(modifier.size(size)) {
            Box(
                Modifier
                    .size(size)
                    .then(if (outlined) Modifier.podgonElevation(CircleShape, 4.dp) else Modifier.clip(CircleShape))
                    .combinedClickable(
                        role = Role.Button,
                        onClickLabel = description,
                        onLongClick = onLongClick,
                        onLongClickLabel = longClickLabel,
                        onClick = onClick,
                    ).semantics { contentDescription = description },
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(iconSize))
            }
            if (badge != null && badge > 0) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 4.dp, y = (-4).dp)
                        .heightIn(min = 18.dp)
                        .widthIn(min = 18.dp)
                        .border(2.dp, c.background, CircleShape)
                        .clip(CircleShape)
                        .background(c.accent),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("$badge", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

/** Временный логотип: буква «П» в скруглённом акцентном квадрате (окончательный — позже). */
@Composable
fun PodgonLogo(size: Dp = 34.dp) {
    Box(
        Modifier.size(size).clip(RoundedCornerShape(size * 0.32f)).background(podgonColors.accent),
        contentAlignment = Alignment.Center,
    ) {
        Text("П", color = Color.White, fontSize = (size.value * 0.55f).sp, fontWeight = FontWeight.ExtraBold)
    }
}

/** Аватар: картинка по [url] (этап 8.3.4), иначе буква [letter] или значок по умолчанию. */
@Composable
fun Avatar(
    size: Dp = 44.dp,
    letter: String? = null,
    url: String? = null,
) {
    val c = podgonColors
    Box(Modifier.size(size).clip(CircleShape).background(c.chip), contentAlignment = Alignment.Center) {
        if (url != null) {
            // до загрузки и при ошибке виден фон-заглушка
            AsyncImage(url, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else if (letter != null) {
            Text(
                letter.take(1).uppercase(),
                color = c.ink,
                fontWeight = FontWeight.ExtraBold,
                fontSize =
                    (
                        size.value *
                            0.4f
                    ).sp,
            )
        } else {
            Icon(PodgonIcons.Person, null, tint = c.muted, modifier = Modifier.size(size * 0.6f))
        }
    }
}

/**
 * Верхняя строка разделов: логотип и название, значок «офлайн» (при отсутствии сети), уведомления-заглушка
 * и профиль.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PodgonTopBar(
    offline: Boolean,
    onNotifications: () -> Unit,
    onProfile: () -> Unit,
    avatarUrl: String? = null,
    modifier: Modifier = Modifier,
) {
    val c = podgonColors
    Row(
        modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 8.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PodgonLogo()
        Spacer(Modifier.width(10.dp))
        Text(stringResource(Res.string.app_name_short), style = MaterialTheme.typography.titleLarge, color = c.ink)
        if (offline) {
            Spacer(Modifier.width(8.dp))
            val hint = stringResource(Res.string.offline_hint)
            TooltipBox(
                positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Below),
                tooltip = { PlainTooltip { Text(hint) } },
                state = rememberTooltipState(),
            ) {
                Icon(
                    PodgonIcons.Offline,
                    contentDescription = stringResource(Res.string.a11y_offline),
                    tint = c.muted,
                    modifier = Modifier.size(20.dp).semantics { stateDescription = hint },
                )
            }
        }
        Spacer(Modifier.weight(1f))
        PodgonIconButton(PodgonIcons.Bell, stringResource(Res.string.a11y_notifications), onNotifications)
        val profile = stringResource(Res.string.a11y_profile)
        Box(
            Modifier
                .size(52.dp)
                .clip(CircleShape)
                .combinedClickable(role = Role.Button, onClickLabel = profile, onClick = onProfile)
                .semantics { contentDescription = profile },
            contentAlignment = Alignment.Center,
        ) {
            Avatar(url = avatarUrl)
        }
    }
}

/** Строка поиска — капсула с лупой (исчезает при фокусе) — и круглая кнопка фильтров со счётчиком. */
@Composable
fun SearchRow(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    activeFilters: Int,
    onFilters: () -> Unit,
    modifier: Modifier = Modifier,
    onFocusChange: (Boolean) -> Unit = {},
    onFiltersLongPress: (() -> Unit)? = null,
) {
    val c = podgonColors
    var focused by remember { mutableStateOf(false) }
    val searchLabel = stringResource(Res.string.a11y_search)
    Row(
        modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            Modifier
                .weight(1f)
                .height(44.dp)
                .podgonElevation(CircleShape, 4.dp)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (!focused) Icon(PodgonIcons.Search, null, tint = c.ink, modifier = Modifier.size(19.dp))
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = c.ink),
                cursorBrush = SolidColor(c.accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSearch() }),
                modifier =
                    Modifier
                        .weight(1f)
                        .semantics { contentDescription = searchLabel }
                        .onFocusChanged {
                            focused = it.isFocused
                            onFocusChange(it.isFocused)
                        },
            )
        }
        PodgonIconButton(
            PodgonIcons.Filters,
            if (activeFilters > 0) {
                stringResource(Res.string.a11y_filters_count, activeFilters)
            } else {
                stringResource(Res.string.a11y_filters)
            },
            onFilters,
            outlined = true,
            badge = activeFilters,
            onLongClick = onFiltersLongPress,
            longClickLabel = onFiltersLongPress?.let { stringResource(Res.string.a11y_saved_filters) },
        )
    }
}

/** Активный фильтр чипом: снимается крестиком без открытия панели (И10). */
@Composable
fun FilterChipsRow(
    chips: List<String>,
    onRemove: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (chips.isEmpty()) return
    val c = podgonColors
    Row(
        modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        chips.forEachIndexed { index, label ->
            Row(
                Modifier
                    .height(28.dp)
                    .clip(CircleShape)
                    .background(c.chip)
                    .padding(start = 11.dp, end = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(label, style = MaterialTheme.typography.labelMedium, color = c.ink, maxLines = 1)
                PodgonIconButton(
                    PodgonIcons.Close,
                    stringResource(Res.string.a11y_remove_filter, label),
                    { onRemove(index) },
                    size = 24.dp,
                    iconSize = 12.dp,
                    tint = c.muted,
                )
            }
        }
    }
}

data class PillTab(
    val id: String,
    val label: String,
)

/** Вкладки-«таблетки» групп в карточке-капсуле; выбранная залита цветом текста; долгое нажатие — меню (И7). */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PillTabs(
    tabs: List<PillTab>,
    selectedId: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    onLongPress: ((String) -> Unit)? = null,
    longPressLabel: String? = null,
) {
    val c = podgonColors
    Box(modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
        Row(
            Modifier
                .fillMaxWidth()
                .podgonElevation(RoundedCornerShape(20.dp), 4.dp)
                .horizontalScroll(rememberScrollState())
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            tabs.forEach { tab ->
                val on = tab.id == selectedId
                Box(
                    Modifier
                        .heightIn(min = 32.dp)
                        .widthIn(min = 56.dp)
                        .clip(CircleShape)
                        .background(if (on) c.pillOn else Color.Transparent)
                        .combinedClickable(
                            role = Role.Tab,
                            onClick = { onSelect(tab.id) },
                            onLongClick = onLongPress?.let { { it(tab.id) } },
                            onLongClickLabel = longPressLabel,
                        ).semantics { selected = on }
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        tab.label,
                        color = if (on) c.pillOnText else c.ink,
                        fontSize = 14.sp,
                        fontWeight = if (on) FontWeight.ExtraBold else FontWeight.SemiBold,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/** Значок закрепления секции. */
enum class SectionMark { NONE, FILTER, PINNED }

/**
 * Заголовок секции ленты (30): значок, название, счётчик и стрелка; нажатие сворачивает и разворачивает,
 * долгое нажатие — меню секции (И27).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SectionHeader(
    name: String,
    count: Int,
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    mark: SectionMark = SectionMark.NONE,
    onLongPress: (() -> Unit)? = null,
) {
    val c = podgonColors
    val state = stringResource(if (expanded) Res.string.a11y_collapse else Res.string.a11y_expand)
    Column(modifier.fillMaxWidth().background(c.background)) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 30.dp)
                .combinedClickable(
                    onClickLabel = state,
                    onClick = onToggle,
                    onLongClick = onLongPress,
                    onLongClickLabel = stringResource(Res.string.a11y_section_menu),
                ).semantics { heading() }
                .padding(start = 20.dp, end = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            when (mark) {
                SectionMark.FILTER ->
                    Icon(
                        PodgonIcons.BookmarkFilled,
                        stringResource(Res.string.a11y_saved_filter),
                        tint = c.accent,
                        modifier = Modifier.size(13.dp),
                    )
                SectionMark.PINNED ->
                    Icon(
                        PodgonIcons.Pin,
                        stringResource(Res.string.a11y_pinned),
                        tint = c.accent,
                        modifier = Modifier.size(13.dp),
                    )
                SectionMark.NONE -> Unit
            }
            Row(
                Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    name,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = c.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                CountBadge(count)
            }
            Icon(
                PodgonIcons.ChevronDown,
                null,
                tint = c.muted,
                modifier = Modifier.size(16.dp).rotate(if (expanded) 0f else -90f),
            )
        }
        Divider()
    }
}

@Composable
fun CountBadge(count: Int) {
    val c = podgonColors
    Box(
        Modifier
            .heightIn(
                min = 18.dp,
            ).widthIn(min = 18.dp)
            .clip(CircleShape)
            .background(c.chip)
            .padding(horizontal = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text("$count", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = c.muted)
    }
}

@Composable
fun Divider(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(1.dp).background(podgonColors.line))
}

/**
 * Компактная рекомендация (около 64): название, «★ 9/10 · категория · автор», справа — сердце избранного
 * и дата. [favorite] `null` — сердце не показывается.
 */
@Composable
fun ReviewRow(
    title: String,
    rating: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    category: String? = null,
    author: String? = null,
    date: String? = null,
    favorite: Boolean? = null,
    onFavorite: () -> Unit = {},
) {
    val c = podgonColors
    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 8.dp), verticalAlignment = Alignment.Top) {
            Column(
                Modifier
                    .weight(1f)
                    .combinedClickable(role = Role.Button, onClick = onClick)
                    .padding(start = 16.dp, end = 4.dp, top = 12.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    color = c.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val ratingLabel = stringResource(Res.string.rating_value, rating)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    RatingLabel(rating, Modifier.semantics { contentDescription = ratingLabel })
                    listOfNotNull(category, author).forEach {
                        Text("·", fontSize = 13.sp, color = c.muted)
                        Text(it, fontSize = 13.sp, color = c.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            if (favorite != null || date != null) {
                Column(
                    Modifier.width(56.dp).padding(top = 4.dp, bottom = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    if (favorite != null) {
                        PodgonIconButton(
                            if (favorite) PodgonIcons.HeartFilled else PodgonIcons.Heart,
                            stringResource(
                                if (favorite) Res.string.a11y_favorite_remove else Res.string.a11y_favorite_add,
                            ),
                            onFavorite,
                            iconSize = 27.dp,
                            tint = if (favorite) c.accent else c.muted,
                        )
                    }
                    date?.let { Text(it, fontSize = 12.sp, color = c.muted, maxLines = 1) }
                }
            }
        }
        Divider()
    }
}

/** «★ 9/10» — звезда акцентного цвета. */
@Composable
fun RatingLabel(
    rating: Int,
    modifier: Modifier = Modifier,
) {
    val c = podgonColors
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        Icon(PodgonIcons.Star, null, tint = c.accent, modifier = Modifier.size(14.dp))
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = c.ink)) { append("$rating") }
                withStyle(SpanStyle(fontWeight = FontWeight.Medium, color = c.muted)) { append("/10") }
            },
            fontSize = 13.sp,
        )
    }
}

/** Пункт нижнего листа: значок, подпись, действие. */
data class SheetAction(
    val icon: ImageVector,
    val label: String,
    val onClick: () -> Unit,
)

/** Нижний лист (меню секции, меню группы): ручка, заголовок с пояснением, пункты 50. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PodgonBottomSheet(
    title: String,
    onDismiss: () -> Unit,
    actions: List<SheetAction>,
    subtitle: String? = null,
    leading: (@Composable () -> Unit)? = null,
) {
    val c = podgonColors
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = PodgonShapes.sheet,
        containerColor = c.surface,
        scrimColor = c.scrim,
    ) {
        Column(Modifier.padding(start = 8.dp, end = 8.dp, bottom = 12.dp)) {
            Row(
                Modifier.padding(start = 14.dp, end = 14.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                leading?.let {
                    it()
                    Spacer(Modifier.width(12.dp))
                }
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(title, style = MaterialTheme.typography.titleMedium, color = c.ink)
                    subtitle?.let { Text(it, fontSize = 13.sp, color = c.muted) }
                }
            }
            actions.forEach { action ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 50.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .combinedClickable(role = Role.Button) {
                            onDismiss()
                            action.onClick()
                        }.padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Icon(action.icon, null, tint = c.muted, modifier = Modifier.size(20.dp))
                    Text(
                        action.label,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = c.ink,
                    )
                }
            }
        }
    }
}

/** Нижний лист с формой (новая роль, приглашение): ручка, заголовок, поля с прокруткой над клавиатурой. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PodgonFormSheet(
    title: String,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = podgonColors
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = PodgonShapes.sheet,
        containerColor = c.surface,
        scrimColor = c.scrim,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = c.ink)
            content()
        }
    }
}

/** Скелетон ленты при первой загрузке (И13): заголовки секций и строки, пульсирующие. */
@Composable
fun FeedSkeleton(
    modifier: Modifier = Modifier,
    sections: Int = 3,
    rows: Int = 3,
) {
    val c = podgonColors
    val alpha by rememberInfiniteTransition()
        .animateFloat(1f, 0.45f, infiniteRepeatable(tween(650), RepeatMode.Reverse))
    Column(modifier.alpha(alpha).clearAndSetSemantics {}) {
        repeat(sections) { s ->
            Row(Modifier.height(30.dp).padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                Bar(width = (70 + s * 20).dp, height = 10.dp)
                Spacer(Modifier.width(8.dp))
                Bar(width = 22.dp, height = 14.dp)
            }
            Divider()
            repeat(rows) { r ->
                Row(
                    Modifier.height(64.dp).padding(horizontal = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                        Bar(fraction = 0.5f + (r % 3) * 0.12f, height = 12.dp)
                        Bar(fraction = 0.3f + (r % 2) * 0.1f, height = 9.dp)
                    }
                    Bar(width = 22.dp, height = 20.dp)
                }
                Divider()
            }
        }
    }
}

@Composable
private fun Bar(
    width: Dp? = null,
    height: Dp,
    fraction: Float = 1f,
) {
    val base = if (width != null) Modifier.width(width) else Modifier.fillMaxWidth(fraction)
    Box(base.height(height).clip(CircleShape).background(podgonColors.chip))
}

/** Кнопка пустого состояния: основная — акцентная, остальные — контурные. */
data class EmptyAction(
    val label: String,
    val primary: Boolean,
    val onClick: () -> Unit,
)

/** Пустое состояние: значок, заголовок, пояснение и до двух действий. */
@Composable
fun EmptyState(
    title: String,
    text: String,
    modifier: Modifier = Modifier,
    actions: List<EmptyAction> = emptyList(),
) {
    val c = podgonColors
    Column(
        modifier.fillMaxWidth().padding(horizontal = 36.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(PodgonIcons.Empty, null, tint = c.muted, modifier = Modifier.size(44.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, color = c.ink)
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = c.muted,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        actions.forEach { action ->
            Box(
                Modifier
                    .widthIn(max = 260.dp)
                    .fillMaxWidth()
                    .height(46.dp)
                    .clip(CircleShape)
                    .background(if (action.primary) c.accent else c.surface)
                    .border(1.dp, if (action.primary) c.accent else c.line, CircleShape)
                    .combinedClickable(role = Role.Button, onClick = action.onClick),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    action.label,
                    color = if (action.primary) Color.White else c.ink,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}

/** Пункт нижнего меню. */
enum class MainTab { FEED, FAVORITES, PERSONAL, SETTINGS }

/**
 * Нижнее меню — плавающая капсула 68 (И23): Лента · Избранное · «+» · Личное · Настройки; активный пункт —
 * подложка, «+» в акцентном круге 54.
 */
@Composable
fun PodgonBottomBar(
    selected: MainTab,
    onSelect: (MainTab) -> Unit,
    onPlus: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = podgonColors
    Row(
        modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, bottom = 22.dp)
            .height(68.dp)
            .podgonElevation(RoundedCornerShape(34.dp), 12.dp)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        NavItem(MainTab.FEED, PodgonIcons.Feed, stringResource(Res.string.nav_feed), selected, onSelect)
        NavItem(MainTab.FAVORITES, PodgonIcons.Heart, stringResource(Res.string.nav_favorites), selected, onSelect)
        val plus = stringResource(Res.string.a11y_new_review)
        Box(
            Modifier
                .size(54.dp)
                .then(if (c.shadows) Modifier.shadow(10.dp, CircleShape, spotColor = c.accent) else Modifier)
                .clip(CircleShape)
                .background(c.accent)
                .combinedClickable(role = Role.Button, onClickLabel = plus, onClick = onPlus)
                .semantics { contentDescription = plus },
            contentAlignment = Alignment.Center,
        ) {
            Icon(PodgonIcons.Plus, null, tint = Color.White, modifier = Modifier.size(26.dp))
        }
        NavItem(MainTab.PERSONAL, PodgonIcons.Key, stringResource(Res.string.nav_personal), selected, onSelect)
        NavItem(MainTab.SETTINGS, PodgonIcons.Settings, stringResource(Res.string.nav_settings), selected, onSelect)
    }
}

@Composable
private fun RowScope.NavItem(
    tab: MainTab,
    icon: ImageVector,
    label: String,
    current: MainTab,
    onSelect: (MainTab) -> Unit,
) {
    val c = podgonColors
    val on = tab == current
    Column(
        Modifier
            .weight(1f)
            .height(54.dp)
            .clip(RoundedCornerShape(27.dp))
            .background(if (on) c.chip else Color.Transparent)
            .combinedClickable(role = Role.Tab, onClick = { onSelect(tab) })
            .semantics(mergeDescendants = true) { selected = on },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, null, tint = if (on) c.ink else c.muted, modifier = Modifier.size(22.dp))
        Spacer(Modifier.height(3.dp))
        Text(
            label,
            fontSize = 11.sp,
            fontWeight = if (on) FontWeight.Bold else FontWeight.SemiBold,
            letterSpacing = (-0.3).sp,
            color = if (on) c.ink else c.muted,
            maxLines = 1,
            softWrap = false,
        )
    }
}
