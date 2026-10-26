package app.podgon.mobile.feature.groups

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.podgon.contract.CategoryInfo
import app.podgon.contract.FieldErrorReason
import app.podgon.mobile.core.error.AppError
import app.podgon.mobile.designsystem.FormScaffold
import app.podgon.mobile.feature.auth.ui.ErrorText
import app.podgon.mobile.feature.auth.ui.message
import app.podgon.mobile.resources.Res
import app.podgon.mobile.resources.action_cancel
import app.podgon.mobile.resources.action_delete
import app.podgon.mobile.resources.action_save
import app.podgon.mobile.resources.categories_empty
import app.podgon.mobile.resources.categories_title
import app.podgon.mobile.resources.category_delete_simple
import app.podgon.mobile.resources.category_delete_text
import app.podgon.mobile.resources.category_delete_title
import app.podgon.mobile.resources.category_description_label
import app.podgon.mobile.resources.category_edit_title
import app.podgon.mobile.resources.category_move
import app.podgon.mobile.resources.category_move_title
import app.podgon.mobile.resources.category_name_label
import app.podgon.mobile.resources.category_new_sub
import app.podgon.mobile.resources.category_new_title
import app.podgon.mobile.resources.category_root
import org.jetbrains.compose.resources.stringResource

/** Дерево категорий группы. Кнопки изменения видны администратору ([canEdit]); права проверяет сервер. */
@Composable
fun CategoriesScreen(
    viewModel: CategoriesViewModel,
    canEdit: Boolean,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var creatingIn by remember { mutableStateOf<CategoryInfo?>(null) }
    var creatingRoot by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<CategoryInfo?>(null) }
    var moving by remember { mutableStateOf<CategoryInfo?>(null) }

    FormScaffold {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(Res.string.categories_title), style = MaterialTheme.typography.headlineMedium)
            if (canEdit) {
                Button(onClick = {
                    viewModel.clearErrors()
                    creatingRoot = true
                }) { Text(stringResource(Res.string.category_new_title)) }
            }
            state.error?.let { RetryError(it, viewModel::load) }
            if (state.loading && state.categories.isEmpty()) CircularProgressIndicator()
            if (!state.loading && state.categories.isEmpty() && state.error == null) {
                Text(stringResource(Res.string.categories_empty))
            }
            LazyColumn(modifier = Modifier.weight(1f, fill = false)) {
                items(state.tree, key = { it.category.id }) { node ->
                    Column(Modifier.fillMaxWidth().padding(start = (node.depth * 16).dp, top = 6.dp, bottom = 6.dp)) {
                        Text(node.category.name, style = MaterialTheme.typography.titleSmall)
                        if (node.category.description.isNotBlank()) {
                            Text(node.category.description, style = MaterialTheme.typography.bodySmall)
                        }
                        if (canEdit) {
                            Row {
                                TextButton(onClick = {
                                    viewModel.clearErrors()
                                    creatingIn = node.category
                                }) { Text(stringResource(Res.string.category_new_sub)) }
                            }
                            Row {
                                TextButton(onClick = {
                                    viewModel.clearErrors()
                                    editing = node.category
                                }) { Text(stringResource(Res.string.category_edit_title)) }
                                TextButton(onClick = {
                                    viewModel.clearErrors()
                                    moving = node.category
                                }) { Text(stringResource(Res.string.category_move)) }
                                TextButton(onClick = { viewModel.requestDelete(node.category) }) {
                                    Text(stringResource(Res.string.action_delete))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (creatingRoot) {
        CategoryDialog(
            title = stringResource(Res.string.category_new_title),
            initial = null,
            nameError = state.nameError,
            error = state.error,
            onSave = { n, d -> viewModel.create(n, d, null) { creatingRoot = false } },
            onDismiss = { creatingRoot = false },
        )
    }
    creatingIn?.let { parent ->
        CategoryDialog(
            title = stringResource(Res.string.category_new_sub) + ": " + parent.name,
            initial = null,
            nameError = state.nameError,
            error = state.error,
            onSave = { n, d -> viewModel.create(n, d, parent.id) { creatingIn = null } },
            onDismiss = { creatingIn = null },
        )
    }
    editing?.let { category ->
        CategoryDialog(
            title = stringResource(Res.string.category_edit_title),
            initial = category,
            nameError = state.nameError,
            error = state.error,
            onSave = { n, d -> viewModel.update(category, n, d) { editing = null } },
            onDismiss = { editing = null },
        )
    }
    moving?.let { category ->
        MoveDialog(
            category = category,
            categories = state.categories,
            tree = state.tree,
            error = state.error,
            onPick = { parentId -> viewModel.move(category, parentId) { moving = null } },
            onDismiss = { moving = null },
        )
    }
    state.pendingDelete?.let { pending ->
        val impact = pending.impact
        AlertDialog(
            onDismissRequest = viewModel::cancelDelete,
            title = { Text(stringResource(Res.string.category_delete_title, pending.category.name)) },
            text = {
                Text(
                    if (impact.subcategories == 0 && impact.publications == 0) {
                        stringResource(Res.string.category_delete_simple)
                    } else {
                        stringResource(Res.string.category_delete_text, impact.subcategories, impact.publications)
                    },
                )
            },
            confirmButton = {
                TextButton(onClick = viewModel::confirmDelete) { Text(stringResource(Res.string.action_delete)) }
            },
            dismissButton = {
                TextButton(onClick = viewModel::cancelDelete) { Text(stringResource(Res.string.action_cancel)) }
            },
        )
    }
}

@Composable
private fun CategoryDialog(
    title: String,
    initial: CategoryInfo?,
    nameError: FieldErrorReason?,
    error: AppError?,
    onSave: (name: String, description: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(initial?.name.orEmpty()) }
    var description by remember { mutableStateOf(initial?.description.orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(Res.string.category_name_label)) },
                    singleLine = true,
                    isError = nameError != null,
                    supportingText = nameError?.let { reason -> { Text(reason.message()) } },
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text(stringResource(Res.string.category_description_label)) },
                )
                ErrorText(error)
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(name, description) }) { Text(stringResource(Res.string.action_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.action_cancel)) } },
    )
}

/** Выбор нового родителя; сама категория и её потомки не предлагаются (иначе получился бы цикл). */
@Composable
private fun MoveDialog(
    category: CategoryInfo,
    categories: List<CategoryInfo>,
    tree: List<CategoryNode>,
    error: AppError?,
    onPick: (parentId: String?) -> Unit,
    onDismiss: () -> Unit,
) {
    val excluded = remember(category, categories) { subtreeIds(categories, category.id) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.category_move_title)) },
        text = {
            Column {
                LazyColumn(modifier = Modifier.weight(1f, fill = false)) {
                    item {
                        TextButton(onClick = { onPick(null) }) { Text(stringResource(Res.string.category_root)) }
                    }
                    items(tree.filter { it.category.id !in excluded }, key = { it.category.id }) { node ->
                        TextButton(
                            onClick = { onPick(node.category.id) },
                            modifier = Modifier.padding(start = (node.depth * 12).dp),
                        ) { Text(node.category.name) }
                    }
                }
                ErrorText(error)
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.action_cancel)) } },
    )
}
