package app.podgon.mobile.feature.groups

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import app.podgon.contract.CategoryInfo
import app.podgon.contract.ShareMode
import app.podgon.contract.UnmatchedReview
import app.podgon.mobile.resources.Res
import app.podgon.mobile.resources.share_mode_all
import app.podgon.mobile.resources.share_mode_categories
import app.podgon.mobile.resources.share_mode_categories_except
import app.podgon.mobile.resources.share_mode_none
import app.podgon.mobile.resources.share_mode_tags
import app.podgon.mobile.resources.share_mode_tags_except
import app.podgon.mobile.resources.share_personal
import app.podgon.mobile.resources.share_preview_summary
import app.podgon.mobile.resources.share_show
import app.podgon.mobile.resources.share_skip
import app.podgon.mobile.resources.share_subcategories
import app.podgon.mobile.resources.share_tags_label
import app.podgon.mobile.resources.share_title
import app.podgon.mobile.resources.share_unmatched_hint
import org.jetbrains.compose.resources.stringResource

private val modeLabels =
    listOf(
        ShareMode.NONE,
        ShareMode.ALL,
        ShareMode.CATEGORIES,
        ShareMode.CATEGORIES_EXCEPT,
        ShareMode.TAGS,
        ShareMode.TAGS_EXCEPT,
    )

@Composable
private fun ShareMode.label(): String =
    when (this) {
        ShareMode.NONE -> stringResource(Res.string.share_mode_none)
        ShareMode.ALL -> stringResource(Res.string.share_mode_all)
        ShareMode.CATEGORIES -> stringResource(Res.string.share_mode_categories)
        ShareMode.CATEGORIES_EXCEPT -> stringResource(Res.string.share_mode_categories_except)
        ShareMode.TAGS -> stringResource(Res.string.share_mode_tags)
        ShareMode.TAGS_EXCEPT -> stringResource(Res.string.share_mode_tags_except)
    }

/** Выбор правила переноса своих рекомендаций в группу, предпросмотр и назначение категорий для записей без пары. */
@Composable
fun ShareSection(
    state: JoinGroupState,
    viewModel: JoinGroupViewModel,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        Text(stringResource(Res.string.share_title), style = MaterialTheme.typography.titleMedium)
        modeLabels.forEach { mode ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = state.mode == mode, onClick = { viewModel.setMode(mode) })
                Text(mode.label(), style = MaterialTheme.typography.bodyMedium)
            }
        }
        when (state.mode) {
            ShareMode.CATEGORIES, ShareMode.CATEGORIES_EXCEPT -> CategoryPicker(state, viewModel)
            ShareMode.TAGS, ShareMode.TAGS_EXCEPT -> TagsInput(state, viewModel)
            else -> Unit
        }
        if (state.sharePreviewLoading) CircularProgressIndicator(Modifier.height(24.dp))
        state.sharePreview?.let { preview ->
            Text(
                stringResource(Res.string.share_preview_summary, preview.total, preview.matched),
                style = MaterialTheme.typography.bodyMedium,
            )
            if (preview.unmatched.isNotEmpty()) {
                Text(stringResource(Res.string.share_unmatched_hint), style = MaterialTheme.typography.bodySmall)
                preview.unmatched.forEach { item ->
                    UnmatchedRow(
                        item,
                        preview.categories,
                        state.assignments[item.reviewId],
                        onPick = { viewModel.assign(item.reviewId, it) },
                    )
                }
            }
        }
    }
}

@Composable
private fun CategoryPicker(
    state: JoinGroupState,
    viewModel: JoinGroupViewModel,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Switch(checked = state.withSubcategories, onCheckedChange = viewModel::setWithSubcategories)
        Text(stringResource(Res.string.share_subcategories), Modifier.padding(start = 8.dp))
    }
    state.sourceCategories.forEach { category ->
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = category.id in state.selectedCategories,
                onCheckedChange = { viewModel.toggleCategory(category.id) },
            )
            Text(category.label, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun TagsInput(
    state: JoinGroupState,
    viewModel: JoinGroupViewModel,
) {
    OutlinedTextField(
        value = state.tagsText,
        onValueChange = viewModel::onTagsChange,
        label = { Text(stringResource(Res.string.share_tags_label)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { viewModel.refreshSharePreview() }),
        modifier = Modifier.fillMaxWidth(),
    )
    TextButton(onClick = viewModel::refreshSharePreview, enabled = state.rule != null) {
        Text(stringResource(Res.string.share_show))
    }
}

@Composable
private fun UnmatchedRow(
    item: UnmatchedReview,
    categories: List<CategoryInfo>,
    assigned: String?,
    onPick: (String?) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(item.title, style = MaterialTheme.typography.titleSmall)
        Text(
            item.sourceCategory ?: stringResource(Res.string.share_personal),
            style = MaterialTheme.typography.bodySmall,
        )
        Box {
            OutlinedButton(onClick = { open = true }) {
                Text(
                    categories.firstOrNull { it.id == assigned }?.name ?: stringResource(Res.string.share_skip),
                )
            }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(Res.string.share_skip)) },
                    onClick = {
                        open = false
                        onPick(null)
                    },
                )
                categories.forEach { category ->
                    DropdownMenuItem(
                        text = { Text(category.name) },
                        onClick = {
                            open = false
                            onPick(category.id)
                        },
                    )
                }
            }
        }
    }
}
