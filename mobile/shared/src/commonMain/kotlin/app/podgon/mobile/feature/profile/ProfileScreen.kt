package app.podgon.mobile.feature.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.podgon.mobile.core.error.message
import app.podgon.mobile.designsystem.FormScaffold
import app.podgon.mobile.resources.Res
import app.podgon.mobile.resources.action_logout
import app.podgon.mobile.resources.action_revoke
import app.podgon.mobile.resources.profile_sessions
import app.podgon.mobile.resources.profile_title
import app.podgon.mobile.resources.session_current
import app.podgon.mobile.resources.session_unknown_device
import org.jetbrains.compose.resources.stringResource

@Composable
fun ProfileScreen(viewModel: ProfileViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    FormScaffold {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(Res.string.profile_title), style = MaterialTheme.typography.headlineMedium)
            if (state.loading && state.profile == null) CircularProgressIndicator()
            state.profile?.let { profile ->
                Text(profile.displayName, style = MaterialTheme.typography.titleLarge)
                Text("@${profile.username}", style = MaterialTheme.typography.bodyLarge)
                Text(profile.email, style = MaterialTheme.typography.bodyMedium)
                if (profile.bio.isNotBlank()) Text(profile.bio, style = MaterialTheme.typography.bodyMedium)
            }
            state.error?.let { Text(it.message(), color = MaterialTheme.colorScheme.error) }

            Text(stringResource(Res.string.profile_sessions), style = MaterialTheme.typography.titleMedium)
            HorizontalDivider()
            LazyColumn(modifier = Modifier.weight(1f, fill = false)) {
                items(state.sessions, key = { it.id }) { session ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(session.deviceInfo ?: stringResource(Res.string.session_unknown_device))
                            if (session.current) {
                                Text(
                                    stringResource(Res.string.session_current),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                        if (!session.current) {
                            TextButton(onClick = { viewModel.revokeSession(session.id) }) {
                                Text(stringResource(Res.string.action_revoke))
                            }
                        }
                    }
                }
            }
            Button(onClick = viewModel::logout, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(Res.string.action_logout))
            }
        }
    }
}
