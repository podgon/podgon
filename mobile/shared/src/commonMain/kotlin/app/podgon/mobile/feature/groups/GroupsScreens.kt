package app.podgon.mobile.feature.groups

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.podgon.contract.InvitationInfo
import app.podgon.contract.MemberInfo
import app.podgon.contract.RoleInfo
import app.podgon.mobile.core.error.AppError
import app.podgon.mobile.core.error.message
import app.podgon.mobile.designsystem.Avatar
import app.podgon.mobile.designsystem.BottomBarSpace
import app.podgon.mobile.designsystem.Divider
import app.podgon.mobile.designsystem.FormScaffold
import app.podgon.mobile.designsystem.PodgonBottomSheet
import app.podgon.mobile.designsystem.PodgonIconButton
import app.podgon.mobile.designsystem.PodgonIcons
import app.podgon.mobile.designsystem.PodgonShapes
import app.podgon.mobile.designsystem.SheetAction
import app.podgon.mobile.designsystem.podgonColors
import app.podgon.mobile.designsystem.podgonElevation
import app.podgon.mobile.feature.auth.ui.ErrorText
import app.podgon.mobile.feature.auth.ui.message
import app.podgon.mobile.resources.Res
import app.podgon.mobile.resources.a11y_back
import app.podgon.mobile.resources.action_cancel
import app.podgon.mobile.resources.action_change_role
import app.podgon.mobile.resources.action_check_code
import app.podgon.mobile.resources.action_close
import app.podgon.mobile.resources.action_create
import app.podgon.mobile.resources.action_delete
import app.podgon.mobile.resources.action_delete_group
import app.podgon.mobile.resources.action_join
import app.podgon.mobile.resources.action_leave_group
import app.podgon.mobile.resources.action_remove
import app.podgon.mobile.resources.action_retry
import app.podgon.mobile.resources.action_revoke_invite
import app.podgon.mobile.resources.action_save
import app.podgon.mobile.resources.create_group_title
import app.podgon.mobile.resources.group_description_label
import app.podgon.mobile.resources.group_invitations
import app.podgon.mobile.resources.group_manage
import app.podgon.mobile.resources.group_member_count
import app.podgon.mobile.resources.group_members
import app.podgon.mobile.resources.group_name_label
import app.podgon.mobile.resources.group_roles
import app.podgon.mobile.resources.group_show_all
import app.podgon.mobile.resources.group_you_admin
import app.podgon.mobile.resources.groups_counts
import app.podgon.mobile.resources.home_invite
import app.podgon.mobile.resources.invitations_title
import app.podgon.mobile.resources.invite_created_hint
import app.podgon.mobile.resources.invite_created_title
import app.podgon.mobile.resources.invite_email_hint
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
internal fun RetryError(
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

/**
 * Вступление по коду (шаг сценария, без нижнего меню): проверка кода, карточка «Вас приглашают», правило
 * переноса своих групповых рекомендаций и «Вступить в группу» внизу. [token] — код из окна «Присоединиться».
 */
@Composable
fun JoinGroupScreen(
    viewModel: JoinGroupViewModel,
    token: String?,
    onJoined: (groupId: String) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val c = podgonColors
    LaunchedEffect(viewModel) { viewModel.joined.collect(onJoined) }
    LaunchedEffect(token) {
        if (!token.isNullOrBlank() && state.preview == null) {
            viewModel.onTokenChange(token)
            viewModel.check()
        }
    }
    Box(Modifier.fillMaxSize().background(c.background).imePadding()) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(
                    rememberScrollState(),
                ).padding(horizontal = 16.dp)
                .padding(top = 16.dp, bottom = 100.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(Res.string.join_title), style = MaterialTheme.typography.headlineSmall, color = c.ink)
            val preview = state.preview
            if (preview == null) {
                Text(stringResource(Res.string.join_hint), style = MaterialTheme.typography.bodyMedium, color = c.muted)
                OutlinedTextField(
                    value = state.token,
                    onValueChange = viewModel::onTokenChange,
                    label = { Text(stringResource(Res.string.join_code_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                Column(
                    Modifier.fillMaxWidth().podgonElevation(PodgonShapes.card, 4.dp).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        stringResource(Res.string.join_preview_title, preview.groupName),
                        style = MaterialTheme.typography.titleMedium,
                        color = c.ink,
                    )
                    if (preview.groupDescription.isNotBlank()) {
                        Text(
                            preview.groupDescription,
                            style = MaterialTheme.typography.bodyMedium,
                            color = c.ink,
                        )
                    }
                    Text(
                        stringResource(Res.string.group_member_count, preview.memberCount) + " · " +
                            stringResource(Res.string.join_invited_by, preview.invitedBy),
                        style = MaterialTheme.typography.bodySmall,
                        color = c.muted,
                    )
                }
                Column(
                    Modifier
                        .fillMaxWidth()
                        .podgonElevation(
                            PodgonShapes.card,
                            4.dp,
                        ).padding(horizontal = 14.dp, vertical = 6.dp),
                ) {
                    ShareSection(state, viewModel)
                }
            }
            ErrorText(state.error)
        }
        val preview = state.preview
        Button(
            onClick = if (preview == null) viewModel::check else viewModel::confirm,
            enabled = !state.loading && (preview != null || state.token.isNotEmpty()),
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(16.dp)
                    .height(52.dp),
        ) {
            if (state.loading) {
                CircularProgressIndicator(Modifier.height(20.dp))
            } else {
                Text(stringResource(if (preview == null) Res.string.action_check_code else Res.string.action_join))
            }
        }
    }
}

/**
 * Страница группы (`docs/design.md`, «Страница группы»): карточка группы с «Пригласить» (админу), участники
 * (первые пять, «Показать всех»; админу — лист участника), роли и приглашения (админу), выход и удаление.
 */
@Composable
fun GroupScreen(
    viewModel: GroupViewModel,
    onRoles: () -> Unit,
    onInvitations: () -> Unit,
    onBack: () -> Unit,
    onClosed: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val c = podgonColors
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { onClosed() }
    }
    var roleFor by remember { mutableStateOf<MemberInfo?>(null) }
    var removing by remember { mutableStateOf<MemberInfo?>(null) }
    var showAll by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .background(c.background)
            .verticalScroll(rememberScrollState())
            .padding(bottom = BottomBarSpace),
    ) {
        val group = state.group
        Row(Modifier.padding(start = 8.dp, top = 12.dp, end = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            PodgonIconButton(PodgonIcons.Back, stringResource(Res.string.a11y_back), onBack)
            Text(group?.name.orEmpty(), style = MaterialTheme.typography.headlineSmall, color = c.ink, maxLines = 1)
        }
        if (state.loading && group == null) CircularProgressIndicator(Modifier.padding(20.dp))
        state.error?.let { RetryError(it, viewModel::load) }
        if (group != null) {
            Column(
                Modifier
                    .padding(
                        horizontal = 16.dp,
                        vertical = 6.dp,
                    ).fillMaxWidth()
                    .podgonElevation(PodgonShapes.card, 4.dp)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(
                        Modifier.size(48.dp).clip(PodgonShapes.tile).background(c.chip),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            group.name.take(1).uppercase(),
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 20.sp,
                            color = c.ink,
                        )
                    }
                    Column(Modifier.weight(1f)) {
                        Text(group.name, style = MaterialTheme.typography.titleMedium, color = c.ink)
                        Text(
                            stringResource(Res.string.groups_counts, group.memberCount, group.reviewCount),
                            fontSize = 13.sp,
                            color = c.muted,
                        )
                    }
                }
                if (group.description.isNotBlank()) {
                    Text(
                        group.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = c.ink,
                    )
                }
                if (state.isAdmin) {
                    Text(
                        stringResource(Res.string.group_you_admin),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = c.accent,
                    )
                    Button(
                        onClick = onInvitations,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(Res.string.home_invite)) }
                }
            }
            SectionTitle(stringResource(Res.string.group_members))
            Column(Modifier.padding(horizontal = 16.dp).fillMaxWidth().podgonElevation(PodgonShapes.card, 4.dp)) {
                val shown = if (showAll) state.members else state.members.take(MEMBERS_PREVIEW)
                shown.forEachIndexed { index, member ->
                    if (index > 0) Divider()
                    MemberRow(member, manageable = state.isAdmin) { roleFor = member }
                }
                if (!showAll && state.members.size > MEMBERS_PREVIEW) {
                    Divider()
                    TextButton(onClick = { showAll = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(Res.string.group_show_all, state.members.size))
                    }
                }
            }
            if (state.isAdmin) {
                SectionTitle(stringResource(Res.string.group_manage))
                Column(Modifier.padding(horizontal = 16.dp).fillMaxWidth().podgonElevation(PodgonShapes.card, 4.dp)) {
                    LinkRow(stringResource(Res.string.group_roles), onRoles)
                    Divider()
                    LinkRow(stringResource(Res.string.group_invitations), onInvitations)
                }
            }
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = viewModel::prepareLeave, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(Res.string.action_leave_group))
                }
                if (state.isAdmin) {
                    OutlinedButton(onClick = viewModel::prepareDelete, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(Res.string.action_delete_group), color = c.accent)
                    }
                }
            }
        }
    }

    roleFor?.let { member ->
        PodgonBottomSheet(
            title = member.displayName.ifBlank { member.username },
            subtitle = "@${member.username} · ${member.role.displayTitle()}",
            onDismiss = { roleFor = null },
            leading = { Avatar(38.dp) },
            actions =
                state.roles.filter { it.id != member.role.id }.map { role ->
                    SheetAction(PodgonIcons.Group, role.displayTitle()) { viewModel.setRole(member.userId, role.id) }
                } +
                    listOfNotNull(
                        if (member.userId != state.myUserId) {
                            SheetAction(
                                PodgonIcons.Close,
                                stringResource(Res.string.action_remove),
                            ) { removing = member }
                        } else {
                            null
                        },
                    ),
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
    state.leaveImpact?.let { impact ->
        LeaveGroupDialog(
            impact,
            onConfirm = { deleteMine ->
                viewModel.dismissImpact()
                viewModel.leave(deleteMine)
            },
            onDismiss = viewModel::dismissImpact,
        )
    }
    state.deleteImpact?.let { impact ->
        DeleteGroupDialog(
            impact,
            onConfirm = { fate ->
                viewModel.dismissImpact()
                viewModel.deleteGroup(fate)
            },
            onDismiss = viewModel::dismissImpact,
        )
    }
}

@Composable
private fun MemberRow(
    member: MemberInfo,
    manageable: Boolean,
    onManage: () -> Unit,
) {
    val c = podgonColors
    val name = member.displayName.ifBlank { member.username }
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .then(if (manageable) Modifier.combinedClickable(role = Role.Button, onClick = onManage) else Modifier)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Avatar(36.dp)
        Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.titleSmall, color = c.ink)
            Text("@${member.username}", fontSize = 13.sp, color = c.muted)
        }
        Box(
            Modifier
                .height(
                    22.dp,
                ).clip(CircleShape)
                .background(if (member.role.admin) c.accentSoft else c.chip)
                .padding(horizontal = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(member.role.displayTitle(), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = c.ink)
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        fontSize = 13.sp,
        color = podgonColors.muted,
        modifier = Modifier.padding(start = 20.dp, top = 14.dp, bottom = 6.dp),
    )
}

@Composable
private fun LinkRow(
    title: String,
    onClick: () -> Unit,
) {
    val c = podgonColors
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(
                min = 52.dp,
            ).combinedClickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.bodyMedium, color = c.ink, modifier = Modifier.weight(1f))
        Icon(PodgonIcons.ChevronRight, null, tint = c.muted, modifier = Modifier.size(18.dp))
    }
}

private const val MEMBERS_PREVIEW = 5

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
                supportingText = { Text(state.emailError?.message() ?: stringResource(Res.string.invite_email_hint)) },
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
