package app.podgon.mobile.feature.reviews

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.podgon.contract.ReviewCard
import app.podgon.contract.Validation
import app.podgon.mobile.designsystem.FormScaffold
import app.podgon.mobile.feature.auth.ui.ErrorText
import app.podgon.mobile.feature.auth.ui.message
import app.podgon.mobile.feature.groups.RetryError
import app.podgon.mobile.feature.groups.flattenTree
import app.podgon.mobile.resources.Res
import app.podgon.mobile.resources.action_cancel
import app.podgon.mobile.resources.action_delete
import app.podgon.mobile.resources.action_edit
import app.podgon.mobile.resources.action_save
import app.podgon.mobile.resources.review_author
import app.podgon.mobile.resources.review_category_missing
import app.podgon.mobile.resources.review_delete_text
import app.podgon.mobile.resources.review_delete_title
import app.podgon.mobile.resources.review_description_label
import app.podgon.mobile.resources.review_feed_empty
import app.podgon.mobile.resources.review_feed_title
import app.podgon.mobile.resources.review_form_edit_title
import app.podgon.mobile.resources.review_form_new_title
import app.podgon.mobile.resources.review_groups_label
import app.podgon.mobile.resources.review_groups_none
import app.podgon.mobile.resources.review_load_more
import app.podgon.mobile.resources.review_my_title
import app.podgon.mobile.resources.review_new
import app.podgon.mobile.resources.review_pick_category
import app.podgon.mobile.resources.review_published_in
import app.podgon.mobile.resources.review_rating
import app.podgon.mobile.resources.review_tags_hint
import app.podgon.mobile.resources.review_tags_label
import app.podgon.mobile.resources.review_title_label
import org.jetbrains.compose.resources.stringResource

/** Лента группы или «мои рекомендации» ([title] подбирает вызывающий). */
@Composable
fun FeedScreen(
    viewModel: FeedViewModel,
    myOwn: Boolean,
    onOpen: (reviewId: String) -> Unit,
    onNew: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // при возврате с карточки или формы список обновляется
    LaunchedEffect(viewModel) { viewModel.refresh() }
    FormScaffold {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                stringResource(if (myOwn) Res.string.review_my_title else Res.string.review_feed_title),
                style = MaterialTheme.typography.headlineMedium,
            )
            Button(onClick = onNew, modifier = Modifier.fillMaxWidth()) { Text(stringResource(Res.string.review_new)) }
            state.error?.let { RetryError(it, viewModel::refresh) }
            if (state.loading && state.items.isEmpty()) CircularProgressIndicator()
            if (!state.loading && state.items.isEmpty() && state.error == null) {
                Text(stringResource(Res.string.review_feed_empty), style = MaterialTheme.typography.bodyMedium)
            }
            LazyColumn(modifier = Modifier.weight(1f, fill = false)) {
                items(state.items, key = { it.id }) { card -> ReviewCardRow(card, onClick = { onOpen(card.id) }) }
                if (state.nextCursor != null) {
                    item {
                        TextButton(onClick = viewModel::loadMore, enabled = !state.loadingMore) {
                            Text(stringResource(Res.string.review_load_more))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReviewCardRow(
    card: ReviewCard,
    onClick: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp)) {
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text(card.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Text("${card.rating}/${Validation.RATING_MAX}", style = MaterialTheme.typography.titleMedium)
        }
        if (card.excerpt.isNotBlank()) Text(card.excerpt, style = MaterialTheme.typography.bodyMedium, maxLines = 3)
        if (card.tags.isNotEmpty()) {
            Text(
                card.tags.joinToString(" ") { "#$it" },
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Text(card.author.displayName.ifBlank { card.author.username }, style = MaterialTheme.typography.bodySmall)
        HorizontalDivider(Modifier.padding(top = 10.dp))
    }
}

@Composable
fun ReviewScreen(
    viewModel: ReviewViewModel,
    onEdit: (reviewId: String) -> Unit,
    onClosed: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) { viewModel.effects.collect { onClosed() } }
    LaunchedEffect(viewModel) { viewModel.load() }
    var confirmDelete by remember { mutableStateOf(false) }
    FormScaffold {
        Column(
            modifier = Modifier.verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            state.error?.let { RetryError(it, viewModel::load) }
            if (state.loading && state.review == null) CircularProgressIndicator()
            state.review?.let { review ->
                Text(review.title, style = MaterialTheme.typography.headlineMedium)
                Text(
                    stringResource(Res.string.review_rating, review.rating, Validation.RATING_MAX),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    stringResource(
                        Res.string.review_author,
                        review.author.displayName.ifBlank { review.author.username },
                    ),
                    style = MaterialTheme.typography.bodySmall,
                )
                if (review.description.isNotBlank()) {
                    Text(
                        review.description,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
                if (review.tags.isNotEmpty()) {
                    Text(
                        review.tags.joinToString(" ") {
                            "#$it"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                if (review.publications.isNotEmpty()) {
                    Text(
                        stringResource(
                            Res.string.review_published_in,
                            review.publications.joinToString { state.groupNames[it.groupId] ?: "…" },
                        ),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                if (state.isAuthor) {
                    OutlinedButton(onClick = { onEdit(review.id) }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(Res.string.action_edit))
                    }
                    OutlinedButton(onClick = { confirmDelete = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(Res.string.action_delete))
                    }
                }
            }
        }
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
                }) { Text(stringResource(Res.string.action_delete)) }
            },
            dismissButton = {
                TextButton(
                    onClick = { confirmDelete = false },
                ) { Text(stringResource(Res.string.action_cancel)) }
            },
        )
    }
}

@Composable
fun ReviewFormScreen(
    viewModel: ReviewFormViewModel,
    editing: Boolean,
    onSaved: (reviewId: String) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { if (it is ReviewFormEffect.Saved) onSaved(it.reviewId) }
    }
    var pickingFor by remember { mutableStateOf<String?>(null) }
    FormScaffold {
        Column(
            modifier = Modifier.verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                stringResource(if (editing) Res.string.review_form_edit_title else Res.string.review_form_new_title),
                style = MaterialTheme.typography.headlineMedium,
            )
            if (state.loading) CircularProgressIndicator()
            OutlinedTextField(
                value = state.title,
                onValueChange = viewModel::setTitle,
                label = { Text(stringResource(Res.string.review_title_label)) },
                singleLine = true,
                isError = "title" in state.errors,
                supportingText = state.errors["title"]?.let { reason -> { Text(reason.message()) } },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = state.description,
                onValueChange = viewModel::setDescription,
                label = { Text(stringResource(Res.string.review_description_label)) },
                isError = "description" in state.errors,
                supportingText = state.errors["description"]?.let { reason -> { Text(reason.message()) } },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
            )
            Text(
                stringResource(Res.string.review_rating, state.rating, Validation.RATING_MAX),
                style = MaterialTheme.typography.titleSmall,
            )
            Slider(
                value = state.rating.toFloat(),
                onValueChange = { viewModel.setRating(it.toInt()) },
                valueRange = Validation.RATING_MIN.toFloat()..Validation.RATING_MAX.toFloat(),
                steps = Validation.RATING_MAX - Validation.RATING_MIN - 1,
            )
            OutlinedTextField(
                value = state.tagsText,
                onValueChange = viewModel::setTagsText,
                label = { Text(stringResource(Res.string.review_tags_label)) },
                supportingText = {
                    Text(
                        state.errors["tags"]?.message()
                            ?: stringResource(Res.string.review_tags_hint, Validation.TAGS_MAX),
                    )
                },
                isError = "tags" in state.errors,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(stringResource(Res.string.review_groups_label), style = MaterialTheme.typography.titleSmall)
            if (!state.loading && state.groups.isEmpty()) Text(stringResource(Res.string.review_groups_none))
            state.groups.forEach { group ->
                val chosen = group.id in state.selected
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = chosen, onCheckedChange = { viewModel.toggleGroup(group.id) })
                    Text(group.name, modifier = Modifier.weight(1f))
                    if (chosen) {
                        val categoryName =
                            state.categories[group.id]
                                ?.firstOrNull { it.id == state.selected[group.id] }
                                ?.name
                        TextButton(onClick = { pickingFor = group.id }) {
                            Text(categoryName ?: stringResource(Res.string.review_pick_category))
                        }
                    }
                }
            }
            if (state.categoryMissing) {
                Text(stringResource(Res.string.review_category_missing), color = MaterialTheme.colorScheme.error)
            }
            ErrorText(state.error)
            Button(onClick = viewModel::save, enabled = !state.saving, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(Res.string.action_save))
            }
        }
    }
    pickingFor?.let { groupId ->
        AlertDialog(
            onDismissRequest = { pickingFor = null },
            title = { Text(stringResource(Res.string.review_pick_category)) },
            text = {
                LazyColumn {
                    items(flattenTree(state.categories[groupId].orEmpty()), key = { it.category.id }) { node ->
                        TextButton(
                            onClick = {
                                viewModel.setCategory(groupId, node.category.id)
                                pickingFor = null
                            },
                            modifier = Modifier.padding(start = (node.depth * 12).dp),
                        ) { Text(node.category.name) }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(
                    onClick = { pickingFor = null },
                ) { Text(stringResource(Res.string.action_cancel)) }
            },
        )
    }
}
