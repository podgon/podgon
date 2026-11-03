package app.podgon.mobile.feature.profile

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.podgon.mobile.core.error.message
import app.podgon.mobile.designsystem.Avatar
import app.podgon.mobile.designsystem.BottomBarSpace
import app.podgon.mobile.designsystem.Divider
import app.podgon.mobile.designsystem.PodgonIconButton
import app.podgon.mobile.designsystem.PodgonIcons
import app.podgon.mobile.designsystem.PodgonShapes
import app.podgon.mobile.designsystem.podgonColors
import app.podgon.mobile.designsystem.podgonElevation
import app.podgon.mobile.resources.Res
import app.podgon.mobile.resources.a11y_back
import app.podgon.mobile.resources.action_delete_account
import app.podgon.mobile.resources.action_logout
import app.podgon.mobile.resources.action_revoke
import app.podgon.mobile.resources.my_reviews
import app.podgon.mobile.resources.profile_devices
import app.podgon.mobile.resources.session_current
import app.podgon.mobile.resources.session_unknown_device
import org.jetbrains.compose.resources.stringResource

/** Профиль (И18): аватар, имя, @username и email; «Мои рекомендации», устройства, выход и удаление аккаунта. */
@Composable
fun ProfileScreen(
    viewModel: ProfileViewModel,
    onMyReviews: () -> Unit,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val c = podgonColors
    state.deletion?.let { DeleteAccountDialog(it, viewModel) }
    Column(
        Modifier
            .fillMaxSize()
            .background(c.background)
            .verticalScroll(rememberScrollState())
            .padding(bottom = BottomBarSpace),
    ) {
        Row(Modifier.padding(start = 8.dp, top = 12.dp)) {
            PodgonIconButton(PodgonIcons.Back, stringResource(Res.string.a11y_back), onBack)
        }
        if (state.loading && state.profile == null) CircularProgressIndicator(Modifier.padding(20.dp))
        state.profile?.let { profile ->
            Column(
                Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Avatar(84.dp)
                Text(
                    profile.displayName.ifBlank { profile.username },
                    style = MaterialTheme.typography.headlineSmall,
                    color = c.ink,
                )
                Text("@${profile.username}", fontSize = 14.sp, color = c.muted)
                Text(profile.email, fontSize = 14.sp, color = c.muted)
                if (profile.bio.isNotBlank()) {
                    Text(
                        profile.bio,
                        style = MaterialTheme.typography.bodyMedium,
                        color = c.ink,
                    )
                }
            }
        }
        state.error?.let { Text(it.message(), color = c.accent, modifier = Modifier.padding(horizontal = 20.dp)) }
        Card {
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(
                        min = 54.dp,
                    ).combinedClickable(role = Role.Button, onClick = onMyReviews)
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(Res.string.my_reviews),
                    style = MaterialTheme.typography.bodyMedium,
                    color = c.ink,
                    modifier = Modifier.weight(1f),
                )
                Icon(PodgonIcons.ChevronRight, null, tint = c.muted, modifier = Modifier.size(18.dp))
            }
        }
        Text(
            stringResource(Res.string.profile_devices),
            fontSize = 13.sp,
            color = c.muted,
            modifier = Modifier.padding(start = 20.dp, top = 14.dp, bottom = 6.dp),
        )
        Card {
            state.sessions.forEachIndexed { index, session ->
                if (index > 0) Divider()
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        session.deviceInfo ?: stringResource(Res.string.session_unknown_device),
                        style = MaterialTheme.typography.bodyMedium,
                        color = c.ink,
                        modifier = Modifier.weight(1f),
                    )
                    if (session.current) {
                        Box(
                            Modifier
                                .height(
                                    22.dp,
                                ).clip(CircleShape)
                                .background(c.accentSoft)
                                .padding(horizontal = 8.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                stringResource(Res.string.session_current),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = c.ink,
                            )
                        }
                    } else {
                        TextButton(
                            onClick = { viewModel.revokeSession(session.id) },
                        ) { Text(stringResource(Res.string.action_revoke)) }
                    }
                }
            }
        }
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = viewModel::logout, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(Res.string.action_logout))
            }
            OutlinedButton(onClick = viewModel::startDeletion, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(Res.string.action_delete_account), color = c.accent)
            }
        }
    }
}

@Composable
private fun Card(content: @Composable () -> Unit) {
    Column(
        Modifier.padding(horizontal = 16.dp, vertical = 4.dp).fillMaxWidth().podgonElevation(PodgonShapes.card, 4.dp),
    ) {
        content()
    }
}
