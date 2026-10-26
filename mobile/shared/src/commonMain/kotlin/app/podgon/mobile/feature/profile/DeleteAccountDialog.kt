package app.podgon.mobile.feature.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.podgon.mobile.core.error.message
import app.podgon.mobile.resources.Res
import app.podgon.mobile.resources.account_delete_blocked
import app.podgon.mobile.resources.account_delete_code_label
import app.podgon.mobile.resources.account_delete_confirm
import app.podgon.mobile.resources.account_delete_reviews
import app.podgon.mobile.resources.account_delete_send_code
import app.podgon.mobile.resources.account_delete_text
import app.podgon.mobile.resources.action_cancel
import app.podgon.mobile.resources.action_delete_account
import org.jetbrains.compose.resources.stringResource

/** Удаление аккаунта: последствия, судьба рекомендаций, код из письма. Если мешают группы, показывает их. */
@Composable
fun DeleteAccountDialog(
    state: DeletionState,
    viewModel: ProfileViewModel,
) {
    AlertDialog(
        onDismissRequest = viewModel::cancelDeletion,
        title = { Text(stringResource(Res.string.action_delete_account)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(Res.string.account_delete_text, state.impact.reviews, state.impact.publishedReviews),
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (state.blocked) {
                    Text(stringResource(Res.string.account_delete_blocked), color = MaterialTheme.colorScheme.error)
                    state.impact.blockingGroups.forEach { Text("• ${it.name}") }
                } else {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = state.deleteReviews, onCheckedChange = viewModel::setDeleteReviews)
                        Text(
                            stringResource(Res.string.account_delete_reviews),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    OutlinedButton(onClick = viewModel::requestCode, enabled = !state.busy) {
                        Text(stringResource(Res.string.account_delete_send_code))
                    }
                    if (state.codeSent) {
                        OutlinedTextField(
                            value = state.code,
                            onValueChange = viewModel::onCodeChange,
                            label = { Text(stringResource(Res.string.account_delete_code_label)) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                state.error?.let { Text(it.message(), color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            if (!state.blocked) {
                TextButton(onClick = viewModel::confirmDeletion, enabled = state.canConfirm) {
                    Text(stringResource(Res.string.account_delete_confirm))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = viewModel::cancelDeletion) { Text(stringResource(Res.string.action_cancel)) }
        },
    )
}
