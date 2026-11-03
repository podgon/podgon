package app.podgon.mobile.feature.groups

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import app.podgon.contract.ShareMode
import app.podgon.mobile.resources.Res
import app.podgon.mobile.resources.share_mode_all
import app.podgon.mobile.resources.share_mode_categories
import app.podgon.mobile.resources.share_mode_categories_except
import app.podgon.mobile.resources.share_mode_none
import app.podgon.mobile.resources.share_mode_tags
import app.podgon.mobile.resources.share_mode_tags_except
import app.podgon.mobile.resources.share_personal_note
import app.podgon.mobile.resources.share_preview_summary
import app.podgon.mobile.resources.share_show
import app.podgon.mobile.resources.share_tags_label
import app.podgon.mobile.resources.share_title
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

/** Выбор правила переноса своих групповых рекомендаций в группу и предпросмотр того, что будет опубликовано. */
@Composable
fun ShareSection(
    state: JoinGroupState,
    viewModel: JoinGroupViewModel,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        Text(stringResource(Res.string.share_title), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(Res.string.share_personal_note), style = MaterialTheme.typography.bodySmall)
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
                stringResource(Res.string.share_preview_summary, preview.total),
                style = MaterialTheme.typography.bodyMedium,
            )
            preview.items.forEach { item -> Text("• ${item.title}", style = MaterialTheme.typography.bodySmall) }
        }
    }
}

@Composable
private fun CategoryPicker(
    state: JoinGroupState,
    viewModel: JoinGroupViewModel,
) {
    state.categories.forEach { node ->
        Row(
            Modifier.fillMaxWidth().padding(start = (node.depth * 16).dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(
                checked = node.category.id in state.selectedCategories,
                onCheckedChange = { viewModel.toggleCategory(node.category.id) },
            )
            Text(node.category.name, style = MaterialTheme.typography.bodyMedium)
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
