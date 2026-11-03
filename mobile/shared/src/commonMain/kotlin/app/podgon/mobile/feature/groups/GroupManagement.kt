package app.podgon.mobile.feature.groups

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.podgon.contract.InvitationInfo
import app.podgon.contract.RoleInfo
import app.podgon.mobile.core.share.rememberTextSharer
import app.podgon.mobile.designsystem.Divider
import app.podgon.mobile.designsystem.PodgonBottomSheet
import app.podgon.mobile.designsystem.PodgonFormSheet
import app.podgon.mobile.designsystem.PodgonIconButton
import app.podgon.mobile.designsystem.PodgonIcons
import app.podgon.mobile.designsystem.PodgonShapes
import app.podgon.mobile.designsystem.SheetAction
import app.podgon.mobile.designsystem.podgonColors
import app.podgon.mobile.designsystem.podgonElevation
import app.podgon.mobile.feature.auth.ui.ErrorText
import app.podgon.mobile.feature.auth.ui.message
import app.podgon.mobile.resources.Res
import app.podgon.mobile.resources.a11y_decrease
import app.podgon.mobile.resources.a11y_increase
import app.podgon.mobile.resources.action_copy
import app.podgon.mobile.resources.action_create
import app.podgon.mobile.resources.action_delete
import app.podgon.mobile.resources.action_done
import app.podgon.mobile.resources.action_revoke_invite
import app.podgon.mobile.resources.action_save
import app.podgon.mobile.resources.invite_by_code
import app.podgon.mobile.resources.invite_by_email
import app.podgon.mobile.resources.invite_created_by
import app.podgon.mobile.resources.invite_created_hint
import app.podgon.mobile.resources.invite_created_title
import app.podgon.mobile.resources.invite_email_hint
import app.podgon.mobile.resources.invite_email_label
import app.podgon.mobile.resources.invite_max_uses_label
import app.podgon.mobile.resources.invite_new_title
import app.podgon.mobile.resources.invite_none
import app.podgon.mobile.resources.invite_share_text
import app.podgon.mobile.resources.invite_status_active
import app.podgon.mobile.resources.invite_status_inactive
import app.podgon.mobile.resources.invite_status_revoked
import app.podgon.mobile.resources.invite_uses
import app.podgon.mobile.resources.review_share
import app.podgon.mobile.resources.role_admin_label
import app.podgon.mobile.resources.role_builtin
import app.podgon.mobile.resources.role_description_label
import app.podgon.mobile.resources.role_edit_title
import app.podgon.mobile.resources.role_new_title
import app.podgon.mobile.resources.role_title_label
import org.jetbrains.compose.resources.stringResource

/**
 * Карточка «Роли» на странице группы (админу): встроенные и свои роли; своя роль по нажатию — лист
 * «Изменить / Удалить», «Новая роль» — лист с полями. [onChanged] — набор ролей изменился (лист участника).
 */
@Composable
internal fun RolesCard(
    viewModel: RolesViewModel,
    onChanged: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val c = podgonColors
    var menuFor by remember { mutableStateOf<RoleInfo?>(null) }
    var editing by remember { mutableStateOf<RoleInfo?>(null) }
    var creating by remember { mutableStateOf(false) }
    Column(Modifier.padding(horizontal = 16.dp).fillMaxWidth().podgonElevation(PodgonShapes.card, 4.dp)) {
        state.roles.forEachIndexed { index, role ->
            if (index > 0) Divider()
            val note =
                listOfNotNull(
                    if (role.system) stringResource(Res.string.role_builtin) else null,
                    if (role.admin) stringResource(Res.string.role_admin_label) else null,
                    role.description.ifBlank { null },
                ).joinToString(" · ")
            ManageRow(
                title = role.displayTitle(),
                note = note,
                onClick = if (role.system) null else ({ menuFor = role }),
            )
        }
        if (state.roles.isNotEmpty()) Divider()
        AddRow(stringResource(Res.string.role_new_title)) {
            viewModel.clearTitleError()
            creating = true
        }
    }
    ErrorText(state.error?.takeIf { !creating && editing == null })

    menuFor?.let { role ->
        PodgonBottomSheet(
            title = role.displayTitle(),
            onDismiss = { menuFor = null },
            actions =
                listOf(
                    SheetAction(PodgonIcons.Edit, stringResource(Res.string.role_edit_title)) {
                        viewModel.clearTitleError()
                        editing = role
                    },
                    SheetAction(PodgonIcons.Close, stringResource(Res.string.action_delete)) {
                        viewModel.delete(role, onChanged)
                    },
                ),
        )
    }
    if (creating) {
        RoleSheet(stringResource(Res.string.role_new_title), null, state, onDismiss = { creating = false }) { t, d, a ->
            viewModel.create(t, d, a) {
                creating = false
                onChanged()
            }
        }
    }
    editing?.let { role ->
        RoleSheet(stringResource(Res.string.role_edit_title), role, state, onDismiss = { editing = null }) { t, d, a ->
            viewModel.update(role, t, d, a) {
                editing = null
                onChanged()
            }
        }
    }
}

@Composable
private fun RoleSheet(
    title: String,
    initial: RoleInfo?,
    state: RolesState,
    onDismiss: () -> Unit,
    onSave: (title: String, description: String, admin: Boolean) -> Unit,
) {
    var name by remember { mutableStateOf(initial?.title.orEmpty()) }
    var description by remember { mutableStateOf(initial?.description.orEmpty()) }
    var admin by remember { mutableStateOf(initial?.admin ?: false) }
    PodgonFormSheet(title, onDismiss) {
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text(stringResource(Res.string.role_title_label)) },
            singleLine = true,
            isError = state.titleError != null,
            supportingText = state.titleError?.let { reason -> { Text(reason.message()) } },
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = description,
            onValueChange = { description = it },
            label = { Text(stringResource(Res.string.role_description_label)) },
            modifier = Modifier.fillMaxWidth(),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = admin, onCheckedChange = { admin = it })
            Text(stringResource(Res.string.role_admin_label), color = podgonColors.ink)
        }
        ErrorText(state.error)
        Button(onClick = { onSave(name, description, admin) }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(Res.string.action_save))
        }
    }
}

/** Карточка «Приглашения» (админу): по коду или для email, кто создал, сколько использовано, статус, «отозвать». */
@Composable
internal fun InvitationsCard(viewModel: InvitationsViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Column(Modifier.padding(horizontal = 16.dp).fillMaxWidth().podgonElevation(PodgonShapes.card, 4.dp)) {
        if (state.invitations.isEmpty() && !state.loading) {
            Text(
                stringResource(Res.string.invite_none),
                fontSize = 13.sp,
                color = podgonColors.muted,
                modifier = Modifier.padding(16.dp),
            )
        }
        state.invitations.forEachIndexed { index, invitation ->
            if (index > 0) Divider()
            InvitationRow(invitation) { viewModel.revoke(invitation) }
        }
    }
    if (state.created == null) ErrorText(state.error)
}

@Composable
private fun InvitationRow(
    invitation: InvitationInfo,
    onRevoke: () -> Unit,
) {
    val c = podgonColors
    val (status, accent) =
        when {
            invitation.revoked -> stringResource(Res.string.invite_status_revoked) to false
            invitation.active -> stringResource(Res.string.invite_status_active) to true
            else -> stringResource(Res.string.invite_status_inactive) to false
        }
    Row(
        Modifier.fillMaxWidth().heightIn(min = 60.dp).padding(start = 16.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
            Text(
                invitation.email?.let { stringResource(Res.string.invite_by_email, it) }
                    ?: stringResource(Res.string.invite_by_code),
                style = MaterialTheme.typography.titleSmall,
                color = c.ink,
            )
            Text(
                stringResource(Res.string.invite_created_by, invitation.createdBy) + " · " +
                    stringResource(Res.string.invite_uses, invitation.usedCount, invitation.maxUses),
                fontSize = 12.sp,
                color = c.muted,
            )
        }
        Box(
            Modifier
                .height(22.dp)
                .clip(CircleShape)
                .background(if (accent) c.accentSoft else c.chip)
                .padding(horizontal = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(status, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = c.ink)
        }
        if (invitation.active) {
            PodgonIconButton(
                PodgonIcons.Close,
                stringResource(Res.string.action_revoke_invite),
                onRevoke,
                iconSize = 18.dp,
            )
        } else {
            Box(Modifier.size(44.dp))
        }
    }
}

/**
 * Лист приглашения: email (необязателен) и «сколько человек может вступить» степпером; после создания — код
 * крупно, «Копировать» и «Поделиться», предупреждение, что код показывается один раз.
 */
@Composable
internal fun InviteSheet(
    viewModel: InvitationsViewModel,
    onDismiss: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val c = podgonColors
    var email by remember { mutableStateOf("") }
    var maxUses by remember { mutableIntStateOf(1) }
    val close = {
        viewModel.dismissCreated()
        onDismiss()
    }
    val created = state.created
    PodgonFormSheet(
        if (created ==
            null
        ) {
            stringResource(Res.string.invite_new_title)
        } else {
            stringResource(Res.string.invite_created_title)
        },
        close,
    ) {
        if (created == null) {
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(Res.string.invite_max_uses_label),
                    style = MaterialTheme.typography.bodyMedium,
                    color = c.ink,
                    modifier = Modifier.weight(1f),
                )
                Stepper(maxUses, 1..MAX_USES) { maxUses = it }
            }
            ErrorText(state.error)
            Button(onClick = { viewModel.create(email, maxUses) }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(Res.string.action_create))
            }
        } else {
            Text(
                created.token,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.ExtraBold,
                color = c.ink,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(c.chip)
                        .padding(vertical = 18.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            Text(stringResource(Res.string.invite_created_hint), fontSize = 13.sp, color = c.accent)
            created.email?.let {
                Text(
                    stringResource(Res.string.invite_by_email, it),
                    fontSize = 13.sp,
                    color = c.muted,
                )
            }
            @Suppress("DEPRECATION")
            val clipboard = LocalClipboardManager.current
            val share = rememberTextSharer()
            val shareText = stringResource(Res.string.invite_share_text, created.token)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = { clipboard.setText(AnnotatedString(created.token)) },
                    modifier = Modifier.weight(1f),
                ) { Text(stringResource(Res.string.action_copy)) }
                OutlinedButton(onClick = { share(shareText) }, modifier = Modifier.weight(1f)) {
                    Text(stringResource(Res.string.review_share))
                }
            }
            Button(onClick = close, modifier = Modifier.fillMaxWidth()) { Text(stringResource(Res.string.action_done)) }
        }
    }
}

/** Степпер «− N +» в пределах [range]. */
@Composable
private fun Stepper(
    value: Int,
    range: IntRange,
    onChange: (Int) -> Unit,
) {
    val c = podgonColors
    Row(
        Modifier.clip(CircleShape).background(c.chip),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PodgonIconButton(
            PodgonIcons.Minus,
            stringResource(Res.string.a11y_decrease),
            { if (value > range.first) onChange(value - 1) },
            size = 38.dp,
            iconSize = 18.dp,
        )
        Text("$value", style = MaterialTheme.typography.titleSmall, color = c.ink)
        PodgonIconButton(
            PodgonIcons.Plus,
            stringResource(Res.string.a11y_increase),
            { if (value < range.last) onChange(value + 1) },
            size = 38.dp,
            iconSize = 18.dp,
        )
    }
}

@Composable
private fun ManageRow(
    title: String,
    note: String,
    onClick: (() -> Unit)?,
) {
    val c = podgonColors
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .then(if (onClick != null) Modifier.combinedClickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = c.ink)
            if (note.isNotEmpty()) Text(note, fontSize = 12.sp, color = c.muted)
        }
        if (onClick != null) Icon(PodgonIcons.ChevronRight, null, tint = c.muted, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun AddRow(
    title: String,
    onClick: () -> Unit,
) {
    val c = podgonColors
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .combinedClickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(PodgonIcons.Plus, null, tint = c.accent, modifier = Modifier.size(18.dp))
        Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = c.accent)
    }
}

private const val MAX_USES = 100
