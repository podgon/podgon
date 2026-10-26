package app.podgon.mobile.feature.groups

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.podgon.contract.GroupDeleteImpact
import app.podgon.contract.LeaveImpact
import app.podgon.contract.ReviewsFate
import app.podgon.mobile.resources.Res
import app.podgon.mobile.resources.action_cancel
import app.podgon.mobile.resources.action_delete_group
import app.podgon.mobile.resources.action_leave_group
import app.podgon.mobile.resources.delete_group_confirm_text
import app.podgon.mobile.resources.delete_group_confirm_title
import app.podgon.mobile.resources.delete_group_keep
import app.podgon.mobile.resources.delete_group_remove
import app.podgon.mobile.resources.delete_group_reviews_hint
import app.podgon.mobile.resources.leave_group_confirm_text
import app.podgon.mobile.resources.leave_group_confirm_title
import app.podgon.mobile.resources.leave_group_delete_mine
import org.jetbrains.compose.resources.stringResource

/** Подтверждение выхода; если в группе есть публикации пользователя, предлагает убрать их вместе с выходом. */
@Composable
fun LeaveGroupDialog(
    impact: LeaveImpact,
    onConfirm: (deleteMyReviews: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var deleteMine by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.leave_group_confirm_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(Res.string.leave_group_confirm_text))
                if (impact.myPublications > 0) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = deleteMine, onCheckedChange = { deleteMine = it })
                        Text(stringResource(Res.string.leave_group_delete_mine, impact.myPublications))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(deleteMine) }) { Text(stringResource(Res.string.action_leave_group)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.action_cancel)) } },
    )
}

/** Подтверждение удаления группы; для рекомендаций, опубликованных только здесь, выбирается судьба. */
@Composable
fun DeleteGroupDialog(
    impact: GroupDeleteImpact,
    onConfirm: (ReviewsFate) -> Unit,
    onDismiss: () -> Unit,
) {
    var fate by remember { mutableStateOf(ReviewsFate.RETURN_TO_PERSONAL) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.delete_group_confirm_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(Res.string.delete_group_confirm_text))
                if (impact.exclusiveReviews > 0) {
                    Text(
                        stringResource(Res.string.delete_group_reviews_hint, impact.exclusiveReviews),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    FateOption(fate == ReviewsFate.RETURN_TO_PERSONAL, stringResource(Res.string.delete_group_keep)) {
                        fate = ReviewsFate.RETURN_TO_PERSONAL
                    }
                    FateOption(fate == ReviewsFate.DELETE, stringResource(Res.string.delete_group_remove)) {
                        fate = ReviewsFate.DELETE
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(fate) }) { Text(stringResource(Res.string.action_delete_group)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.action_cancel)) } },
    )
}

@Composable
private fun FateOption(
    selected: Boolean,
    label: String,
    onSelect: () -> Unit,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected = selected, onClick = onSelect)
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}
