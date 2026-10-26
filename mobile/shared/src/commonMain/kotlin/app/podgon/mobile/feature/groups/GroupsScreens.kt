package app.podgon.mobile.feature.groups

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.podgon.contract.GroupSummary
import app.podgon.contract.InvitationInfo
import app.podgon.contract.MemberInfo
import app.podgon.contract.RoleInfo
import app.podgon.mobile.core.error.AppError
import app.podgon.mobile.core.error.message
import app.podgon.mobile.designsystem.FormScaffold
import app.podgon.mobile.feature.auth.ui.ErrorText
import app.podgon.mobile.feature.auth.ui.message
import app.podgon.mobile.resources.Res
import app.podgon.mobile.resources.action_cancel
import app.podgon.mobile.resources.action_change_role
import app.podgon.mobile.resources.action_check_code
import app.podgon.mobile.resources.action_close
import app.podgon.mobile.resources.action_create
import app.podgon.mobile.resources.action_create_group
import app.podgon.mobile.resources.action_delete
import app.podgon.mobile.resources.action_delete_group
import app.podgon.mobile.resources.action_join
import app.podgon.mobile.resources.action_join_group
import app.podgon.mobile.resources.action_leave_group
import app.podgon.mobile.resources.action_profile
import app.podgon.mobile.resources.action_remove
import app.podgon.mobile.resources.action_retry
import app.podgon.mobile.resources.action_revoke_invite
import app.podgon.mobile.resources.action_save
import app.podgon.mobile.resources.create_group_title
import app.podgon.mobile.resources.delete_group_confirm_text
import app.podgon.mobile.resources.delete_group_confirm_title
import app.podgon.mobile.resources.group_description_label
import app.podgon.mobile.resources.group_invitations
import app.podgon.mobile.resources.group_member_count
import app.podgon.mobile.resources.group_members
import app.podgon.mobile.resources.group_name_label
import app.podgon.mobile.resources.group_roles
import app.podgon.mobile.resources.groups_empty
import app.podgon.mobile.resources.groups_title
import app.podgon.mobile.resources.invitations_title
import app.podgon.mobile.resources.invite_created_hint
import app.podgon.mobile.resources.invite_created_title
import app.podgon.mobile.resources.invite_email_label
import app.podgon.mobile.resources.invite_for
import app.podgon.mobile.resources.invite_max_uses_label
import app.podgon.mobile.resources.invite_new_title
import app.podgon.mobile.resources.invite_status_active
import app.podgon.mobile.resources.invite_status_inactive
import app.podgon.mobile.resources.invite_status_revoked
import app.podgon.mobile.resources.invite_uses
import app.podgon.mobile.resources.join_code_label
import app.podgon.mobile.resources.join_hint
import app.podgon.mobile.resources.join_invited_by
import app.podgon.mobile.resources.join_preview_title
import app.podgon.mobile.resources.join_title
import app.podgon.mobile.resources.leave_group_confirm_text
import app.podgon.mobile.resources.leave_group_confirm_title
import app.podgon.mobile.resources.remove_member_confirm
import app.podgon.mobile.resources.role_admin_label
import app.podgon.mobile.resources.role_admin_title
import app.podgon.mobile.resources.role_builtin
import app.podgon.mobile.resources.role_description_label
import app.podgon.mobile.resources.role_edit_title
import app.podgon.mobile.resources.role_member_title
import app.podgon.mobile.resources.role_new_title
import app.podgon.mobile.resources.role_title_label
import app.podgon.mobile.resources.roles_title
import org.jetbrains.compose.resources.stringResource

/** Встроенные роли названы на сервере по-русски; на клиенте их название берётся из локализации по коду. */
@Composable
internal fun RoleInfo.displayTitle(): String =
    when {
        !system -> title
        code == "ADMIN" -> stringResource(Res.string.role_admin_title)
        code == "MEMBER" -> stringResource(Res.string.role_member_title)
        else -> title
    }

@Composable
fun GroupsScreen(
    viewModel: GroupsViewModel,
    onCreate: () -> Unit,
    onJoin: () -> Unit,
    onOpen: (groupId: String) -> Unit,
    onProfile: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // при возврате с экрана группы (вышли, удалили, переименовали) список обновляется
    LaunchedEffect(viewModel) { viewModel.load() }
    FormScaffold {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(stringResource(Res.string.groups_title), style = MaterialTheme.typography.headlineMedium)
                TextButton(onClick = onProfile) { Text(stringResource(Res.string.action_profile)) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onCreate) { Text(stringResource(Res.string.action_create_group)) }
                OutlinedButton(onClick = onJoin) { Text(stringResource(Res.string.action_join_group)) }
            }
            state.error?.let { RetryError(it, viewModel::load) }
            if (state.loading && state.groups.isEmpty()) CircularProgressIndicator()
            if (!state.loading && state.groups.isEmpty() && state.error == null) {
                Text(stringResource(Res.string.groups_empty), style = MaterialTheme.typography.bodyMedium)
            }
            LazyColumn(modifier = Modifier.weight(1f, fill = false)) {
                items(state.groups, key = { it.id }) { group -> GroupRow(group, onClick = { onOpen(group.id) }) }
            }
        }
    }
}

@Composable
private fun GroupRow(
    group: GroupSummary,
    onClick: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp)) {
        Text(group.name, style = MaterialTheme.typography.titleMedium)
        Text(
            stringResource(Res.string.group_member_count, group.memberCount) + " · " + group.myRole.displayTitle(),
            style = MaterialTheme.typography.bodySmall,
        )
        if (group.description.isNotBlank()) {
            Text(
                group.description,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
            )
        }
        HorizontalDivider(Modifier.padding(top = 10.dp))
    }
}

@Composable
private fun RetryError(
    error: AppError,
    onRetry: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(error.message(), color = MaterialTheme.colorScheme.error)
        TextButton(onClick = onRetry) { Text(stringResource(Res.string.action_retry)) }
    }
}

@Composable
fun CreateGroupScreen(
    viewModel: CreateGroupViewModel,
    onCreated: (groupId: String) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) { viewModel.created.collect(onCreated) }
    FormScaffold {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(stringResource(Res.string.create_group_title), style = MaterialTheme.typography.headlineMedium)
            OutlinedTextField(
                value = state.name,
                onValueChange = viewModel::onNameChange,
                label = { Text(stringResource(Res.string.group_name_label)) },
                singleLine = true,
                isError = state.nameError != null,
                supportingText = state.nameError?.let { reason -> { Text(reason.message()) } },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = state.description,
                onValueChange = viewModel::onDescriptionChange,
                label = { Text(stringResource(Res.string.group_description_label)) },
                minLines = 2,
                isError = state.descriptionError != null,
                supportingText = state.descriptionError?.let { reason -> { Text(reason.message()) } },
                modifier = Modifier.fillMaxWidth(),
            )
            ErrorText(state.error)
            Button(onClick = viewModel::submit, enabled = !state.loading, modifier = Modifier.fillMaxWidth()) {
                if (state.loading) {
                    CircularProgressIndicator(
                        Modifier.height(20.dp),
                    )
                } else {
                    Text(stringResource(Res.string.action_create))
                }
            }
        }
    }
}

@Composable
fun JoinGroupScreen(
    viewModel: JoinGroupViewModel,
    onJoined: (groupId: String) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) { viewModel.joined.collect(onJoined) }
    FormScaffold {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(stringResource(Res.string.join_title), style = MaterialTheme.typography.headlineMedium)
            Text(stringResource(Res.string.join_hint), style = MaterialTheme.typography.bodyMedium)
            OutlinedTextField(
                value = state.token,
                onValueChange = viewModel::onTokenChange,
                label = { Text(stringResource(Res.string.join_code_label)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            ErrorText(state.error)
            val preview = state.preview
            if (preview == null) {
                Button(
                    onClick = viewModel::check,
                    enabled = !state.loading && state.token.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (state.loading) {
                        CircularProgressIndicator(
                            Modifier.height(20.dp),
                        )
                    } else {
                        Text(stringResource(Res.string.action_check_code))
                    }
                }
            } else {
                Text(
                    stringResource(Res.string.join_preview_title, preview.groupName),
                    style = MaterialTheme.typography.titleLarge,
                )
                if (preview.groupDescription.isNotBlank()) {
                    Text(
                        preview.groupDescription,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                Text(
                    stringResource(Res.string.group_member_count, preview.memberCount) + " · " +
                        stringResource(Res.string.join_invited_by, preview.invitedBy),
                    style = MaterialTheme.typography.bodySmall,
                )
                Button(onClick = viewModel::confirm, enabled = !state.loading, modifier = Modifier.fillMaxWidth()) {
                    if (state.loading) {
                        CircularProgressIndicator(
                            Modifier.height(20.dp),
                        )
                    } else {
                        Text(stringResource(Res.string.action_join))
                    }
                }
            }
        }
    }
}

@Composable
fun GroupScreen(
    viewModel: GroupViewModel,
    onRoles: () -> Unit,
    onInvitations: () -> Unit,
    onClosed: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { onClosed() }
    }
    var roleFor by remember { mutableStateOf<MemberInfo?>(null) }
    var removing by remember { mutableStateOf<MemberInfo?>(null) }
    var confirmLeave by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    FormScaffold {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val group = state.group
            if (state.loading && group == null) CircularProgressIndicator()
            group?.let {
                Text(it.name, style = MaterialTheme.typography.headlineMedium)
                if (it.description.isNotBlank()) Text(it.description, style = MaterialTheme.typography.bodyMedium)
                Text(
                    stringResource(Res.string.group_member_count, it.memberCount) + " · " + it.myRole.displayTitle(),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            state.error?.let { RetryError(it, viewModel::load) }
            if (state.isAdmin) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onInvitations) { Text(stringResource(Res.string.group_invitations)) }
                    OutlinedButton(onClick = onRoles) { Text(stringResource(Res.string.group_roles)) }
                }
            }
            Text(stringResource(Res.string.group_members), style = MaterialTheme.typography.titleMedium)
            HorizontalDivider()
            LazyColumn(modifier = Modifier.weight(1f, fill = false)) {
                items(state.members, key = { it.userId }) { member ->
                    MemberRow(
                        member,
                        manageable = state.isAdmin,
                        canRemove = member.userId != state.myUserId,
                        onChangeRole = { roleFor = member },
                        onRemove = { removing = member },
                    )
                }
            }
            if (group != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { confirmLeave = true },
                    ) { Text(stringResource(Res.string.action_leave_group)) }
                    if (state.isAdmin) {
                        OutlinedButton(
                            onClick = { confirmDelete = true },
                        ) { Text(stringResource(Res.string.action_delete_group)) }
                    }
                }
            }
        }
    }

    roleFor?.let { member ->
        RolePickerDialog(
            roles = state.roles,
            current = member.role,
            onPick = {
                viewModel.setRole(member.userId, it.id)
                roleFor = null
            },
            onDismiss = { roleFor = null },
        )
    }
    removing?.let { member ->
        ConfirmDialog(
            title = stringResource(Res.string.remove_member_confirm, member.displayName.ifBlank { member.username }),
            text = null,
            confirm = stringResource(Res.string.action_remove),
            onConfirm = {
                viewModel.removeMember(member.userId)
                removing = null
            },
            onDismiss = { removing = null },
        )
    }
    if (confirmLeave) {
        ConfirmDialog(
            title = stringResource(Res.string.leave_group_confirm_title),
            text = stringResource(Res.string.leave_group_confirm_text),
            confirm = stringResource(Res.string.action_leave_group),
            onConfirm = {
                confirmLeave = false
                viewModel.leave()
            },
            onDismiss = { confirmLeave = false },
        )
    }
    if (confirmDelete) {
        ConfirmDialog(
            title = stringResource(Res.string.delete_group_confirm_title),
            text = stringResource(Res.string.delete_group_confirm_text),
            confirm = stringResource(Res.string.action_delete_group),
            onConfirm = {
                confirmDelete = false
                viewModel.deleteGroup()
            },
            onDismiss = { confirmDelete = false },
        )
    }
}

@Composable
private fun MemberRow(
    member: MemberInfo,
    manageable: Boolean,
    canRemove: Boolean,
    onChangeRole: () -> Unit,
    onRemove: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(member.displayName.ifBlank { member.username }, style = MaterialTheme.typography.titleSmall)
        Text("@${member.username} · ${member.role.displayTitle()}", style = MaterialTheme.typography.bodySmall)
        if (manageable) {
            Row {
                TextButton(onClick = onChangeRole) { Text(stringResource(Res.string.action_change_role)) }
                if (canRemove) TextButton(onClick = onRemove) { Text(stringResource(Res.string.action_remove)) }
            }
        }
    }
}

@Composable
private fun RolePickerDialog(
    roles: List<RoleInfo>,
    current: RoleInfo,
    onPick: (RoleInfo) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.action_change_role)) },
        text = {
            Column {
                roles.forEach { role ->
                    TextButton(
                        onClick = { onPick(role) },
                        enabled = role.id != current.id,
                    ) { Text(role.displayTitle()) }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.action_cancel)) } },
    )
}

@Composable
private fun ConfirmDialog(
    title: String,
    text: String?,
    confirm: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = text?.let { { Text(it) } },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.action_cancel)) } },
    )
}

@Composable
fun RolesScreen(viewModel: RolesViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<RoleInfo?>(null) }
    var creating by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<RoleInfo?>(null) }

    FormScaffold {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(Res.string.roles_title), style = MaterialTheme.typography.headlineMedium)
            Button(onClick = {
                viewModel.clearTitleError()
                creating = true
            }) { Text(stringResource(Res.string.role_new_title)) }
            state.error?.let { RetryError(it, viewModel::load) }
            if (state.loading && state.roles.isEmpty()) CircularProgressIndicator()
            LazyColumn(modifier = Modifier.weight(1f, fill = false)) {
                items(state.roles, key = { it.id }) { role ->
                    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                        Text(role.displayTitle(), style = MaterialTheme.typography.titleSmall)
                        val note =
                            listOfNotNull(
                                if (role.system) stringResource(Res.string.role_builtin) else null,
                                if (role.admin) stringResource(Res.string.role_admin_label) else null,
                                role.description.ifBlank { null },
                            ).joinToString(" · ")
                        if (note.isNotEmpty()) Text(note, style = MaterialTheme.typography.bodySmall)
                        if (!role.system) {
                            Row {
                                TextButton(onClick = {
                                    viewModel.clearTitleError()
                                    editing = role
                                }) { Text(stringResource(Res.string.role_edit_title)) }
                                TextButton(
                                    onClick = { deleting = role },
                                ) { Text(stringResource(Res.string.action_delete)) }
                            }
                        }
                    }
                }
            }
        }
    }

    if (creating) {
        RoleDialog(
            title = stringResource(Res.string.role_new_title),
            initial = null,
            titleError = state.titleError,
            error = state.error,
            onSave = { t, d, a -> viewModel.create(t, d, a) { creating = false } },
            onDismiss = { creating = false },
        )
    }
    editing?.let { role ->
        RoleDialog(
            title = stringResource(Res.string.role_edit_title),
            initial = role,
            titleError = state.titleError,
            error = state.error,
            onSave = { t, d, a -> viewModel.update(role, t, d, a) { editing = null } },
            onDismiss = { editing = null },
        )
    }
    deleting?.let { role ->
        ConfirmDialog(
            title = stringResource(Res.string.action_delete) + ": " + role.displayTitle(),
            text = null,
            confirm = stringResource(Res.string.action_delete),
            onConfirm = {
                viewModel.delete(role)
                deleting = null
            },
            onDismiss = { deleting = null },
        )
    }
}

@Composable
private fun RoleDialog(
    title: String,
    initial: RoleInfo?,
    titleError: app.podgon.contract.FieldErrorReason?,
    error: AppError?,
    onSave: (title: String, description: String, admin: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(initial?.title.orEmpty()) }
    var description by remember { mutableStateOf(initial?.description.orEmpty()) }
    var admin by remember { mutableStateOf(initial?.admin ?: false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(Res.string.role_title_label)) },
                    singleLine = true,
                    isError = titleError != null,
                    supportingText = titleError?.let { reason -> { Text(reason.message()) } },
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text(stringResource(Res.string.role_description_label)) },
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = admin, onCheckedChange = { admin = it })
                    Text(stringResource(Res.string.role_admin_label))
                }
                ErrorText(error)
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(name, description, admin) }) { Text(stringResource(Res.string.action_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.action_cancel)) } },
    )
}

@Composable
fun InvitationsScreen(viewModel: InvitationsViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var email by remember { mutableStateOf("") }
    var maxUses by remember { mutableStateOf("1") }

    FormScaffold {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(Res.string.invitations_title), style = MaterialTheme.typography.headlineMedium)
            Text(stringResource(Res.string.invite_new_title), style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text(stringResource(Res.string.invite_email_label)) },
                singleLine = true,
                isError = state.emailError != null,
                supportingText = state.emailError?.let { reason -> { Text(reason.message()) } },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = maxUses,
                onValueChange = { maxUses = it.filter(Char::isDigit).take(3) },
                label = { Text(stringResource(Res.string.invite_max_uses_label)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = { viewModel.create(email, maxUses.toIntOrNull()?.coerceIn(1, MAX_USES) ?: 1) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(Res.string.action_create)) }
            ErrorText(state.error)
            HorizontalDivider()
            if (state.loading && state.invitations.isEmpty()) CircularProgressIndicator()
            LazyColumn(modifier = Modifier.weight(1f, fill = false)) {
                items(state.invitations, key = { it.id }) { invitation ->
                    InvitationRow(invitation, onRevoke = { viewModel.revoke(invitation) })
                }
            }
        }
    }

    state.created?.let { created ->
        AlertDialog(
            onDismissRequest = viewModel::dismissCreated,
            title = { Text(stringResource(Res.string.invite_created_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(Res.string.invite_created_hint))
                    // токен виден только здесь: выделяется и копируется системным жестом
                    SelectionContainer { Text(created.token, style = MaterialTheme.typography.titleMedium) }
                    created.email?.let {
                        Text(
                            stringResource(Res.string.invite_for, it),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = viewModel::dismissCreated,
                ) { Text(stringResource(Res.string.action_close)) }
            },
        )
    }
}

private const val MAX_USES = 100

@Composable
private fun InvitationRow(
    invitation: InvitationInfo,
    onRevoke: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        val status =
            when {
                invitation.revoked -> stringResource(Res.string.invite_status_revoked)
                invitation.active -> stringResource(Res.string.invite_status_active)
                else -> stringResource(Res.string.invite_status_inactive)
            }
        Text(invitation.email ?: invitation.createdBy, style = MaterialTheme.typography.titleSmall)
        Text(
            status + " · " + stringResource(Res.string.invite_uses, invitation.usedCount, invitation.maxUses),
            style = MaterialTheme.typography.bodySmall,
        )
        if (invitation.active) TextButton(onClick = onRevoke) { Text(stringResource(Res.string.action_revoke_invite)) }
    }
}
