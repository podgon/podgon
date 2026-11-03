package app.podgon.mobile.feature.reviews

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.podgon.mobile.designsystem.Avatar
import app.podgon.mobile.designsystem.PodgonBottomSheet
import app.podgon.mobile.designsystem.PodgonIconButton
import app.podgon.mobile.designsystem.PodgonIcons
import app.podgon.mobile.designsystem.SheetAction
import app.podgon.mobile.designsystem.podgonColors
import app.podgon.mobile.feature.groups.RetryError
import app.podgon.mobile.feature.home.fullDate
import app.podgon.mobile.feature.home.shortDate
import app.podgon.mobile.resources.Res
import app.podgon.mobile.resources.a11y_back
import app.podgon.mobile.resources.a11y_favorite_add
import app.podgon.mobile.resources.action_cancel
import app.podgon.mobile.resources.action_delete
import app.podgon.mobile.resources.months_short
import app.podgon.mobile.resources.nav_personal
import app.podgon.mobile.resources.review_created_at
import app.podgon.mobile.resources.review_delete_text
import app.podgon.mobile.resources.review_delete_title
import app.podgon.mobile.resources.review_edit
import app.podgon.mobile.resources.review_more
import app.podgon.mobile.resources.review_offline
import app.podgon.mobile.resources.review_published_at
import app.podgon.mobile.resources.review_published_groups
import app.podgon.mobile.resources.review_report
import app.podgon.mobile.resources.review_share
import app.podgon.mobile.resources.review_you
import org.jetbrains.compose.resources.stringResource

/**
 * Просмотр рекомендации (`docs/design.md`, «Просмотр рекомендации»): «назад» и «⋯», заголовок с сердцем,
 * оценка и категория (полный путь — по нажатию), описание, теги, автор с раскрывающимися подробностями.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReviewScreen(
    viewModel: ReviewViewModel,
    onEdit: (reviewId: String) -> Unit,
    onTag: (String) -> Unit,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { if (it is ReviewEffect.Closed) onBack() }
    }
    val c = podgonColors
    val months = stringResource(Res.string.months_short).split(',')
    var menu by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var showPath by remember { mutableStateOf(false) }
    var details by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().background(c.background).verticalScroll(rememberScrollState())) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 10.dp)) {
            PodgonIconButton(PodgonIcons.Back, stringResource(Res.string.a11y_back), onBack, outlined = true)
            Spacer(Modifier.weight(1f))
            if (state.review !=
                null
            ) {
                PodgonIconButton(
                    PodgonIcons.More,
                    stringResource(Res.string.review_more),
                    { menu = true },
                    outlined = true,
                )
            }
        }
        if (state.loading && state.review == null) CircularProgressIndicator(Modifier.padding(20.dp))
        state.error?.let { RetryError(it, viewModel::load) }
        if (state.offline) {
            Text(
                stringResource(Res.string.review_offline),
                fontSize = 13.sp,
                color = c.muted,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
        }
        val review = state.review ?: return@Column
        Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Text(
                    review.title,
                    style = MaterialTheme.typography.headlineMedium,
                    color = c.ink,
                    modifier = Modifier.weight(1f),
                )
                // избранное — backlog: сердце видно и ничего не делает (правило прототипа)
                PodgonIconButton(
                    PodgonIcons.Heart,
                    stringResource(Res.string.a11y_favorite_add),
                    {},
                    iconSize = 30.dp,
                    tint = c.muted,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Row(
                    Modifier
                        .height(30.dp)
                        .clip(CircleShape)
                        .background(c.accentSoft)
                        .padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(PodgonIcons.Star, null, tint = c.accent, modifier = Modifier.size(15.dp))
                    Text("${review.rating}/10", fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = c.ink)
                }
                state.categories[review.categoryId]?.let { cat ->
                    Box(
                        Modifier
                            .height(
                                30.dp,
                            ).clip(CircleShape)
                            .background(c.chip)
                            .combinedClickable(role = Role.Button) {
                                showPath =
                                    !showPath
                            }.padding(horizontal = 12.dp),
                        contentAlignment = Alignment.Center,
                    ) { Text(cat.name, style = MaterialTheme.typography.labelMedium, color = c.ink) }
                }
            }
            if (showPath) {
                state.categoryPath?.let {
                    Text(
                        it,
                        fontSize = 13.sp,
                        color = c.ink,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clip(
                                    RoundedCornerShape(14.dp),
                                ).background(c.chip)
                                .padding(12.dp),
                    )
                }
            }
            if (review.description.isNotBlank()) {
                Text(
                    review.description,
                    style = MaterialTheme.typography.bodyLarge,
                    color = c.ink,
                )
            }
            if (review.tags.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    review.tags.forEach { tag ->
                        Box(
                            Modifier
                                .height(
                                    30.dp,
                                ).clip(CircleShape)
                                .background(c.chip)
                                .combinedClickable(role = Role.Button) {
                                    onTag(tag)
                                }.padding(horizontal = 11.dp),
                            contentAlignment = Alignment.Center,
                        ) { Text("#$tag", style = MaterialTheme.typography.labelMedium, color = c.ink) }
                    }
                }
            }
            // автор: компактная строка, подробности — по нажатию
            val name =
                if (review.author.id ==
                    state.myUserId
                ) {
                    stringResource(Res.string.review_you)
                } else {
                    review.author.displayName.ifBlank { review.author.username }
                }
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(
                        min = 44.dp,
                    ).clip(RoundedCornerShape(14.dp))
                    .combinedClickable(role = Role.Button) {
                        details =
                            !details
                    },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Avatar(30.dp)
                Text(name, style = MaterialTheme.typography.titleSmall, color = c.ink, modifier = Modifier.weight(1f))
                Text(
                    shortDate(
                        review.publications.maxOfOrNull {
                            it.sharedAt
                        } ?: review.createdAt,
                        months,
                    ),
                    fontSize = 13.sp,
                    color = c.muted,
                )
                Icon(
                    PodgonIcons.ChevronDown,
                    null,
                    tint = c.muted,
                    modifier = Modifier.size(16.dp).rotate(if (details) 180f else 0f),
                )
            }
            if (details) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        stringResource(Res.string.review_created_at, fullDate(review.createdAt, months)),
                        fontSize = 13.sp,
                        color = c.muted,
                    )
                    review.publications.forEach { p ->
                        state.groupNames[p.groupId]?.let { g ->
                            Text(
                                stringResource(Res.string.review_published_at, g, fullDate(p.sharedAt, months)),
                                fontSize = 13.sp,
                                color = c.muted,
                            )
                        }
                    }
                    Text(
                        stringResource(Res.string.review_published_groups),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = c.ink,
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        val names =
                            if (review.personal) {
                                listOf(
                                    stringResource(Res.string.nav_personal),
                                )
                            } else {
                                review.publications.mapNotNull {
                                    state.groupNames[it.groupId]
                                }
                            }
                        names.forEach {
                            Box(
                                Modifier
                                    .height(28.dp)
                                    .clip(CircleShape)
                                    .background(c.chip)
                                    .padding(horizontal = 10.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(it, style = MaterialTheme.typography.labelMedium, color = c.ink)
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(120.dp))
        }
    }
    if (menu) {
        val review = state.review
        PodgonBottomSheet(
            title = review?.title.orEmpty(),
            onDismiss = { menu = false },
            actions =
                if (state.isAuthor) {
                    listOf(
                        SheetAction(
                            PodgonIcons.Edit,
                            stringResource(Res.string.review_edit),
                        ) { review?.let { onEdit(it.id) } },
                        // «Поделиться» — после ссылок-приглашений (этап 9–11): пункт виден и ничего не делает
                        SheetAction(PodgonIcons.Invite, stringResource(Res.string.review_share)) {},
                        SheetAction(
                            PodgonIcons.Close,
                            stringResource(Res.string.action_delete),
                        ) { confirmDelete = true },
                    )
                } else {
                    listOf(
                        SheetAction(PodgonIcons.Offline, stringResource(Res.string.review_report)) {},
                        SheetAction(PodgonIcons.Invite, stringResource(Res.string.review_share)) {},
                    )
                },
        )
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(Res.string.review_delete_title)) },
            text = { Text(stringResource(Res.string.review_delete_text)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    viewModel.delete()
                }) { Text(stringResource(Res.string.action_delete), color = c.accent) }
            },
            dismissButton = {
                TextButton(
                    onClick = { confirmDelete = false },
                ) { Text(stringResource(Res.string.action_cancel)) }
            },
        )
    }
}
