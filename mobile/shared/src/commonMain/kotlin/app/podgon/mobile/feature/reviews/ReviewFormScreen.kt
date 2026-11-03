package app.podgon.mobile.feature.reviews

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.podgon.contract.Validation
import app.podgon.mobile.core.media.photoUrl
import app.podgon.mobile.core.media.rememberImagePicker
import app.podgon.mobile.designsystem.Divider
import app.podgon.mobile.designsystem.PodgonIconButton
import app.podgon.mobile.designsystem.PodgonIcons
import app.podgon.mobile.designsystem.PodgonShapes
import app.podgon.mobile.designsystem.podgonColors
import app.podgon.mobile.designsystem.podgonElevation
import app.podgon.mobile.feature.auth.ui.ErrorText
import app.podgon.mobile.feature.auth.ui.message
import app.podgon.mobile.resources.Res
import app.podgon.mobile.resources.a11y_close
import app.podgon.mobile.resources.action_cancel
import app.podgon.mobile.resources.action_save
import app.podgon.mobile.resources.filters_find
import app.podgon.mobile.resources.form_add_groups
import app.podgon.mobile.resources.form_category
import app.podgon.mobile.resources.form_close_lock_text
import app.podgon.mobile.resources.form_close_lock_title
import app.podgon.mobile.resources.form_description_hint
import app.podgon.mobile.resources.form_done
import app.podgon.mobile.resources.form_groups_needed
import app.podgon.mobile.resources.form_lock_closed
import app.podgon.mobile.resources.form_lock_open
import app.podgon.mobile.resources.form_missing
import app.podgon.mobile.resources.form_personal_hint
import app.podgon.mobile.resources.form_personal_label
import app.podgon.mobile.resources.form_photo
import app.podgon.mobile.resources.form_photo_remove
import app.podgon.mobile.resources.form_photo_replace
import app.podgon.mobile.resources.form_rating
import app.podgon.mobile.resources.form_tag_hint
import app.podgon.mobile.resources.form_tags
import app.podgon.mobile.resources.form_title_hint
import app.podgon.mobile.resources.form_visibility
import app.podgon.mobile.resources.review_pick_category
import coil3.compose.AsyncImage
import org.jetbrains.compose.resources.stringResource

/**
 * Форма рекомендации на весь экран, без нижнего меню (`docs/design.md`, «Форма рекомендации»): название
 * в шапке между «закрыть» и «сохранить», оценка 1–10, категория и теги, описание, видимость (замок и группы).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReviewFormScreen(
    viewModel: ReviewFormViewModel,
    onSaved: (reviewId: String) -> Unit,
    onClose: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { if (it is ReviewFormEffect.Saved) onSaved(it.reviewId) }
    }
    val c = podgonColors
    var pickingCategory by remember { mutableStateOf(false) }
    var pickingGroups by remember { mutableStateOf(false) }
    var confirmClose by remember { mutableStateOf(false) }
    var highlight by remember { mutableStateOf(false) }
    val missing = state.missing
    Box(
        Modifier
            .fillMaxSize()
            .background(c.background)
            .safeDrawingPadding()
            .imePadding(),
    ) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 40.dp)) {
            Header(state, highlight && "title" in missing, viewModel, onClose) {
                highlight = true
                if (missing.isEmpty()) viewModel.save()
            }
            RatingRow(state.rating, highlight && "rating" in missing, viewModel::setRating)
            Card(highlight && "category" in missing) {
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 44.dp).combinedClickable(role = Role.Button) {
                        pickingCategory =
                            true
                    },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(Res.string.form_category),
                        style = MaterialTheme.typography.titleSmall,
                        color = c.ink,
                        modifier = Modifier.width(96.dp),
                    )
                    Text(
                        state.categoryPath ?: stringResource(Res.string.review_pick_category),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (state.category == null) c.muted else c.ink,
                        modifier = Modifier.weight(1f),
                    )
                    Icon(PodgonIcons.ChevronRight, null, tint = c.muted, modifier = Modifier.size(18.dp))
                }
                Divider(Modifier.padding(vertical = 8.dp))
                TagsEditor(state, viewModel)
            }
            DescriptionField(state.description, viewModel::setDescription)
            Visibility(state, highlight && "groups" in missing, viewModel, onAddGroups = {
                pickingGroups = true
            }, onCloseLock = {
                if (state.publishedIn.isNotEmpty()) confirmClose = true else viewModel.setLockOpen(false)
            })
            PhotoField(state.photo, viewModel::pickPhoto, viewModel::removePhoto)
            ErrorText(state.error)
            state.errors["tags"]?.let {
                Text(
                    stringResource(Res.string.form_tags) + ": " + it.message(),
                    color = c.accent,
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
            }
        }
    }
    if (pickingCategory) {
        CategoryPicker(state, onPick = {
            viewModel.setCategory(it)
            pickingCategory = false
        }, onDismiss = { pickingCategory = false })
    }
    if (pickingGroups) GroupsSheet(state, viewModel) { pickingGroups = false }
    if (confirmClose) {
        AlertDialog(
            onDismissRequest = { confirmClose = false },
            title = { Text(stringResource(Res.string.form_close_lock_title)) },
            text = {
                Text(
                    stringResource(Res.string.form_close_lock_text) + "\n" +
                        state.publishedIn
                            .mapNotNull { id ->
                                state.groups.firstOrNull { it.id == id }?.name
                            }.joinToString(", "),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.setLockOpen(false)
                    confirmClose = false
                }) { Text(stringResource(Res.string.form_lock_closed)) }
            },
            dismissButton = {
                TextButton(
                    onClick = { confirmClose = false },
                ) { Text(stringResource(Res.string.action_cancel)) }
            },
        )
    }
}

/** Шапка: «закрыть», поле названия по центру (в рамке, до 3 строк), «сохранить» — акцентный круг. */
@Composable
private fun Header(
    state: ReviewFormState,
    highlight: Boolean,
    viewModel: ReviewFormViewModel,
    onClose: () -> Unit,
    onSave: () -> Unit,
) {
    val c = podgonColors
    val ready = state.missing.isEmpty() && !state.saving
    val saveLabel =
        if (state.missing.isEmpty()) {
            stringResource(
                Res.string.action_save,
            )
        } else {
            stringResource(Res.string.form_missing, state.missing.joinToString())
        }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PodgonIconButton(PodgonIcons.Close, stringResource(Res.string.a11y_close), onClose)
        Box(
            Modifier
                .weight(1f)
                .padding(horizontal = 6.dp)
                .heightIn(min = 48.dp)
                .clip(RoundedCornerShape(16.dp))
                .border(1.5.dp, if (highlight) c.accent else c.line, RoundedCornerShape(16.dp))
                .background(c.surface)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            contentAlignment = Alignment.Center,
        ) {
            val hint = stringResource(Res.string.form_title_hint)
            if (state.title.isEmpty()) {
                Text(
                    hint,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold,
                    color = c.muted,
                    textAlign = TextAlign.Center,
                )
            }
            BasicTextField(
                value = state.title,
                onValueChange = viewModel::setTitle,
                maxLines = 3,
                textStyle =
                    MaterialTheme.typography.titleMedium.copy(
                        fontSize = 19.sp,
                        color = c.ink,
                        textAlign = TextAlign.Center,
                    ),
                cursorBrush = SolidColor(c.accent),
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = hint },
            )
        }
        Box(
            Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(if (ready) c.accent else c.chip)
                .combinedClickable(role = Role.Button, onClickLabel = saveLabel, onClick = onSave)
                .semantics { contentDescription = saveLabel },
            contentAlignment = Alignment.Center,
        ) {
            Icon(PodgonIcons.Check, null, tint = if (ready) Color.White else c.muted, modifier = Modifier.size(22.dp))
        }
    }
}

/** Оценка: ряд кнопок 1–10, справа крупно «9/10» (пока не выбрана — «—/10»). */
@Composable
private fun RatingRow(
    rating: Int?,
    highlight: Boolean,
    onSelect: (Int) -> Unit,
) {
    val c = podgonColors
    Card(highlight) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(Res.string.form_rating),
                style = MaterialTheme.typography.titleSmall,
                color = c.ink,
                modifier = Modifier.weight(1f),
            )
            Text("${rating ?: "—"}", fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, color = c.ink)
            Text("/10", fontSize = 16.sp, color = c.muted)
        }
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            (Validation.RATING_MIN..Validation.RATING_MAX).forEach { value ->
                val on = value == rating
                Box(
                    Modifier
                        .weight(1f)
                        .height(36.dp)
                        .clip(CircleShape)
                        .background(if (on) c.accent else c.chip)
                        .combinedClickable(role = Role.RadioButton) { onSelect(value) }
                        .semantics { selected = on },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "$value",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (on) Color.White else c.ink,
                    )
                }
            }
        }
    }
}

@Composable
private fun Card(
    highlight: Boolean = false,
    content: @Composable () -> Unit,
) {
    val c = podgonColors
    Column(
        Modifier
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .fillMaxWidth()
            .podgonElevation(PodgonShapes.card, 4.dp)
            .then(if (highlight) Modifier.border(1.5.dp, c.accent, PodgonShapes.card) else Modifier)
            .padding(14.dp),
    ) { content() }
}

/** Теги чипами с крестиком и поле «#тег» с подсказками из видимых тегов. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TagsEditor(
    state: ReviewFormState,
    viewModel: ReviewFormViewModel,
) {
    val c = podgonColors
    var input by remember { mutableStateOf("") }
    Text(stringResource(Res.string.form_tags), style = MaterialTheme.typography.titleSmall, color = c.ink)
    FlowRow(
        Modifier.padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        state.tags.forEach { tag ->
            Row(
                Modifier
                    .height(30.dp)
                    .clip(CircleShape)
                    .background(c.chip)
                    .padding(start = 11.dp, end = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("#$tag", style = MaterialTheme.typography.labelMedium, color = c.ink)
                PodgonIconButton(
                    PodgonIcons.Close,
                    tag,
                    { viewModel.removeTag(tag) },
                    size = 24.dp,
                    iconSize = 11.dp,
                    tint = c.muted,
                )
            }
        }
        if (state.tags.size < Validation.TAGS_MAX) {
            val hint = stringResource(Res.string.form_tag_hint)
            Box(
                Modifier.height(30.dp).width(120.dp).padding(horizontal = 6.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (input.isEmpty()) Text(hint, fontSize = 14.sp, color = c.muted)
                BasicTextField(
                    value = input,
                    onValueChange = { value ->
                        if (value.endsWith(' ') || value.endsWith(',')) {
                            viewModel.addTag(value.dropLast(1))
                            input = ""
                        } else {
                            input = value
                            viewModel.suggestTags(value)
                        }
                    },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = c.ink),
                    cursorBrush = SolidColor(c.accent),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions =
                        KeyboardActions(onDone = {
                            viewModel.addTag(input)
                            input = ""
                        }),
                    modifier = Modifier.fillMaxWidth().semantics { contentDescription = hint },
                )
            }
        }
    }
    if (state.tagSuggestions.isNotEmpty()) {
        FlowRow(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            state.tagSuggestions.forEach { tag ->
                Box(
                    Modifier
                        .height(
                            28.dp,
                        ).clip(CircleShape)
                        .border(1.dp, c.line, CircleShape)
                        .combinedClickable(role = Role.Button) {
                            viewModel.addTag(tag)
                            input = ""
                        }.padding(horizontal = 10.dp),
                    contentAlignment = Alignment.Center,
                ) { Text("#$tag", fontSize = 13.sp, color = c.muted) }
            }
        }
    }
}

@Composable
private fun DescriptionField(
    value: String,
    onChange: (String) -> Unit,
) {
    val c = podgonColors
    val hint = stringResource(Res.string.form_description_hint)
    Card {
        Box(Modifier.fillMaxWidth().heightIn(min = 200.dp)) {
            if (value.isEmpty()) Text(hint, style = MaterialTheme.typography.bodyLarge, color = c.muted)
            BasicTextField(
                value = value,
                onValueChange = onChange,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = c.ink),
                cursorBrush = SolidColor(c.accent),
                modifier = Modifier.fillMaxWidth().heightIn(min = 200.dp).semantics { contentDescription = hint },
            )
        }
    }
}

/** «Видимость»: слева подпись и группы чипами, справа столбик — замок и «+» (только при открытом замке). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Visibility(
    state: ReviewFormState,
    highlight: Boolean,
    viewModel: ReviewFormViewModel,
    onAddGroups: () -> Unit,
    onCloseLock: () -> Unit,
) {
    val c = podgonColors
    Card(highlight) {
        Row {
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(Res.string.form_visibility),
                    style = MaterialTheme.typography.titleSmall,
                    color = c.ink,
                )
                if (!state.lockOpen) {
                    Text(
                        stringResource(Res.string.form_personal_label),
                        style = MaterialTheme.typography.bodyMedium,
                        color = c.ink,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                    Text(stringResource(Res.string.form_personal_hint), fontSize = 13.sp, color = c.muted)
                } else {
                    FlowRow(
                        Modifier.padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        state.selected.forEach { id ->
                            val name = state.groups.firstOrNull { it.id == id }?.name ?: "…"
                            Row(
                                Modifier
                                    .height(
                                        30.dp,
                                    ).clip(CircleShape)
                                    .background(c.chip)
                                    .padding(start = 11.dp, end = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(name, style = MaterialTheme.typography.labelMedium, color = c.ink)
                                PodgonIconButton(
                                    PodgonIcons.Close,
                                    name,
                                    { viewModel.toggleGroup(id) },
                                    size = 24.dp,
                                    iconSize = 11.dp,
                                    tint = c.muted,
                                )
                            }
                        }
                    }
                    if (state.selected.isEmpty()) {
                        Text(
                            stringResource(Res.string.form_groups_needed),
                            fontSize = 13.sp,
                            color = if (highlight) c.accent else c.muted,
                        )
                    }
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PodgonIconButton(
                    if (state.lockOpen) PodgonIcons.LockOpen else PodgonIcons.Lock,
                    stringResource(if (state.lockOpen) Res.string.form_lock_open else Res.string.form_lock_closed),
                    { if (state.lockOpen) onCloseLock() else viewModel.setLockOpen(true) },
                    outlined = true,
                )
                if (state.lockOpen) {
                    PodgonIconButton(
                        PodgonIcons.Plus,
                        stringResource(Res.string.form_add_groups),
                        onAddGroups,
                        tint = c.accent,
                        outlined = true,
                    )
                }
            }
        }
    }
}

/** Фото: пунктирная кнопка «Добавить фото», после выбора — превью с «Заменить» и «Убрать». */
@Composable
private fun PhotoField(
    photo: PhotoDraft,
    onPick: (ByteArray) -> Unit,
    onRemove: () -> Unit,
) {
    val c = podgonColors
    val pick = rememberImagePicker(onPicked = onPick)
    val model: Any? =
        when (photo) {
            is PhotoDraft.Picked -> photo.jpeg
            is PhotoDraft.Saved -> photoUrl(photo.reviewId, photo.photoId)
            else -> null
        }
    if (model == null) {
        Box(
            Modifier
                .padding(horizontal = 16.dp, vertical = 6.dp)
                .fillMaxWidth()
                .height(48.dp)
                .clip(PodgonShapes.card)
                .border(1.dp, c.line, PodgonShapes.card)
                .combinedClickable(role = Role.Button, onClick = pick),
            contentAlignment = Alignment.Center,
        ) { Text(stringResource(Res.string.form_photo), fontSize = 14.sp, color = c.ink) }
        return
    }
    Box(
        Modifier
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .fillMaxWidth()
            .height(180.dp)
            .clip(PodgonShapes.card)
            .background(c.chip),
    ) {
        AsyncImage(model, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        Row(
            Modifier.align(Alignment.TopEnd).padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PodgonIconButton(PodgonIcons.Edit, stringResource(Res.string.form_photo_replace), pick, outlined = true)
            PodgonIconButton(PodgonIcons.Close, stringResource(Res.string.form_photo_remove), onRemove, outlined = true)
        }
    }
}

/** Выбор категории из общего набора с поиском (по названию и пути родителя). */
@Composable
private fun CategoryPicker(
    state: ReviewFormState,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val c = podgonColors
    var query by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.review_pick_category)) },
        text = {
            Column {
                val hint = stringResource(Res.string.filters_find)
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(
                            40.dp,
                        ).clip(CircleShape)
                        .background(c.chip)
                        .padding(horizontal = 14.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    if (query.isEmpty()) Text(hint, color = c.muted)
                    BasicTextField(
                        query,
                        {
                            query = it
                        },
                        singleLine = true,
                        textStyle =
                            MaterialTheme.typography.bodyMedium.copy(
                                color = c.ink,
                            ),
                        cursorBrush = SolidColor(c.accent),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                val roots = state.categories.associateBy { it.id }
                val shown =
                    state.choices.filter { node ->
                        val parent =
                            node.category.parentId
                                ?.let { roots[it]?.name }
                                .orEmpty()
                        query.isBlank() ||
                            node.category.name.contains(query.trim(), true) ||
                            parent.contains(query.trim(), true)
                    }
                LazyColumn(Modifier.heightIn(max = 420.dp).padding(top = 8.dp)) {
                    items(shown, key = { it.category.id }) { node ->
                        Text(
                            node.category.name,
                            style =
                                if (node.depth ==
                                    0
                                ) {
                                    MaterialTheme.typography.titleSmall
                                } else {
                                    MaterialTheme.typography.bodyMedium
                                },
                            color = c.ink,
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .combinedClickable(role = Role.Button) { onPick(node.category.id) }
                                    .padding(start = (node.depth * 16).dp, top = 10.dp, bottom = 10.dp),
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.action_cancel)) } },
    )
}

/** Нижний лист «мои группы» с флажками; «Готово» вверху листа. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GroupsSheet(
    state: ReviewFormState,
    viewModel: ReviewFormViewModel,
    onDone: () -> Unit,
) {
    val c = podgonColors
    ModalBottomSheet(
        onDismissRequest = onDone,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = c.surface,
        shape = PodgonShapes.sheet,
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(Res.string.form_add_groups),
                style = MaterialTheme.typography.titleMedium,
                color = c.ink,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onDone) { Text(stringResource(Res.string.form_done)) }
        }
        LazyColumn(Modifier.heightIn(max = 480.dp).padding(bottom = 16.dp)) {
            items(state.groups, key = { it.id }) { group ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp)
                        .combinedClickable(role = Role.Checkbox) {
                            viewModel.toggleGroup(group.id)
                        }.padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(group.id in state.selected, {
                        viewModel.toggleGroup(group.id)
                    }, colors = CheckboxDefaults.colors(checkedColor = c.accent))
                    Spacer(Modifier.width(6.dp))
                    Text(group.name, style = MaterialTheme.typography.bodyMedium, color = c.ink)
                }
            }
        }
    }
}
