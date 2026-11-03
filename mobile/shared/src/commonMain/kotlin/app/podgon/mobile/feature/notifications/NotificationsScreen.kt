package app.podgon.mobile.feature.notifications

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.podgon.contract.NotificationInfo
import app.podgon.contract.NotificationKind
import app.podgon.mobile.core.error.message
import app.podgon.mobile.designsystem.BottomBarSpace
import app.podgon.mobile.designsystem.Divider
import app.podgon.mobile.designsystem.EmptyState
import app.podgon.mobile.designsystem.PodgonIconButton
import app.podgon.mobile.designsystem.PodgonIcons
import app.podgon.mobile.designsystem.PodgonShapes
import app.podgon.mobile.designsystem.podgonColors
import app.podgon.mobile.designsystem.podgonElevation
import app.podgon.mobile.feature.home.shortDate
import app.podgon.mobile.resources.Res
import app.podgon.mobile.resources.a11y_back
import app.podgon.mobile.resources.months_short
import app.podgon.mobile.resources.notifications_empty_text
import app.podgon.mobile.resources.notifications_empty_title
import app.podgon.mobile.resources.notifications_filter_match
import app.podgon.mobile.resources.notifications_invitation
import app.podgon.mobile.resources.notifications_invited_by
import app.podgon.mobile.resources.notifications_settings
import app.podgon.mobile.resources.notifications_settings_filters
import app.podgon.mobile.resources.notifications_settings_filters_hint
import app.podgon.mobile.resources.notifications_settings_invitations
import app.podgon.mobile.resources.notifications_settings_invitations_hint
import app.podgon.mobile.resources.notifications_title
import org.jetbrains.compose.resources.stringResource

/**
 * Уведомления (этап 8.3.5): входящие приглашения (переход к вступлению) и новые рекомендации по подпискам
 * (переход к карточке). Новые — с точкой; шестерёнка — «О чём уведомлять».
 */
@Composable
fun NotificationsScreen(
    viewModel: NotificationsViewModel,
    onBack: () -> Unit,
    onSettings: () -> Unit,
    onInvitation: (invitationId: String) -> Unit,
    onReview: (reviewId: String) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val c = podgonColors
    val months = stringResource(Res.string.months_short).split(',')
    Column(Modifier.fillMaxSize().background(c.background)) {
        Row(Modifier.padding(start = 8.dp, top = 12.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            PodgonIconButton(PodgonIcons.Back, stringResource(Res.string.a11y_back), onBack)
            Text(
                stringResource(Res.string.notifications_title),
                style = MaterialTheme.typography.headlineSmall,
                color = c.ink,
                modifier = Modifier.weight(1f),
            )
            PodgonIconButton(PodgonIcons.Settings, stringResource(Res.string.notifications_settings), onSettings)
        }
        state.error?.let { Text(it.message(), color = c.accent, modifier = Modifier.padding(horizontal = 20.dp)) }
        when {
            state.loading && state.items.isEmpty() -> CircularProgressIndicator(Modifier.padding(20.dp))
            state.items.isEmpty() ->
                EmptyState(
                    stringResource(Res.string.notifications_empty_title),
                    stringResource(Res.string.notifications_empty_text),
                )
            else ->
                LazyColumn(contentPadding = PaddingValues(bottom = BottomBarSpace)) {
                    items(state.items, key = { it.id }) { n ->
                        NotificationRow(n, n.id in state.fresh, months) {
                            when (n.kind) {
                                NotificationKind.INVITATION -> n.invitationId?.let(onInvitation)
                                NotificationKind.FILTER_MATCH -> n.reviewId?.let(onReview)
                            }
                        }
                    }
                    if (state.nextCursor != null) item { LaunchedEffect(state.items.size) { viewModel.loadMore() } }
                }
        }
    }
}

@Composable
private fun NotificationRow(
    n: NotificationInfo,
    fresh: Boolean,
    months: List<String>,
    onClick: () -> Unit,
) {
    val c = podgonColors
    val (icon, title, subtitle) =
        when (n.kind) {
            NotificationKind.INVITATION ->
                Triple(
                    PodgonIcons.Invite,
                    stringResource(Res.string.notifications_invitation, n.groupName.orEmpty()),
                    stringResource(Res.string.notifications_invited_by, n.invitedBy.orEmpty()),
                )
            NotificationKind.FILTER_MATCH ->
                Triple(
                    PodgonIcons.Bookmark,
                    n.reviewTitle.orEmpty(),
                    stringResource(Res.string.notifications_filter_match, n.filterName.orEmpty()),
                )
        }
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .combinedClickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(c.chip), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = c.accent, modifier = Modifier.size(20.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = if (fresh) FontWeight.ExtraBold else FontWeight.SemiBold,
                color = c.ink,
                maxLines = 2,
            )
            Text(subtitle, fontSize = 13.sp, color = c.muted, maxLines = 1)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(shortDate(n.createdAt, months), fontSize = 12.sp, color = c.muted)
            if (fresh) {
                Spacer(Modifier.size(6.dp))
                Box(Modifier.size(8.dp).clip(CircleShape).background(c.accent))
            }
        }
    }
    Divider()
}

/** «О чём уведомлять»: приглашения в группы и новые рекомендации по подпискам на фильтры. */
@Composable
fun NotificationSettingsScreen(
    viewModel: NotificationSettingsViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val c = podgonColors
    Column(Modifier.fillMaxSize().background(c.background).verticalScroll(rememberScrollState())) {
        Row(Modifier.padding(start = 8.dp, top = 12.dp, end = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            PodgonIconButton(PodgonIcons.Back, stringResource(Res.string.a11y_back), onBack)
            Text(
                stringResource(Res.string.notifications_settings),
                style = MaterialTheme.typography.headlineSmall,
                color = c.ink,
            )
        }
        state.error?.let { Text(it.message(), color = c.accent, modifier = Modifier.padding(horizontal = 20.dp)) }
        val settings = state.settings ?: return@Column
        Column(Modifier.padding(16.dp).podgonElevation(PodgonShapes.card, 4.dp)) {
            SwitchRow(
                stringResource(Res.string.notifications_settings_invitations),
                stringResource(Res.string.notifications_settings_invitations_hint),
                settings.invitations,
            ) { viewModel.change(settings.copy(invitations = it)) }
            Divider()
            SwitchRow(
                stringResource(Res.string.notifications_settings_filters),
                stringResource(Res.string.notifications_settings_filters_hint),
                settings.filterMatches,
            ) { viewModel.change(settings.copy(filterMatches = it)) }
        }
    }
}

@Composable
private fun SwitchRow(
    title: String,
    hint: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    val c = podgonColors
    Row(
        Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, color = c.ink)
            Text(hint, fontSize = 12.sp, color = c.muted)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
