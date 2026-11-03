package app.podgon.mobile.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.podgon.mobile.designsystem.Divider
import app.podgon.mobile.designsystem.PodgonIcons
import app.podgon.mobile.designsystem.PodgonShapes
import app.podgon.mobile.designsystem.ThemeMode
import app.podgon.mobile.designsystem.podgonColors
import app.podgon.mobile.designsystem.podgonElevation
import app.podgon.mobile.resources.Res
import app.podgon.mobile.resources.settings_groups
import app.podgon.mobile.resources.settings_groups_count
import app.podgon.mobile.resources.settings_sections
import app.podgon.mobile.resources.settings_theme
import app.podgon.mobile.resources.settings_title
import app.podgon.mobile.resources.settings_version
import app.podgon.mobile.resources.theme_dark
import app.podgon.mobile.resources.theme_light
import app.podgon.mobile.resources.theme_system
import org.jetbrains.compose.resources.stringResource

/** «Настройки»: группы, секции ленты, тема, версия приложения. */
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    version: String,
    onGroups: () -> Unit,
    onSections: () -> Unit,
    header: @Composable () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val mode by viewModel.themeMode.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) { viewModel.load() }
    val c = podgonColors
    Column(Modifier.fillMaxSize().background(c.background).verticalScroll(rememberScrollState())) {
        header()
        Text(
            stringResource(Res.string.settings_title),
            style = MaterialTheme.typography.headlineSmall,
            color = c.ink,
            modifier = Modifier.padding(start = 20.dp, top = 8.dp, bottom = 10.dp),
        )
        Column(Modifier.padding(horizontal = 16.dp).podgonElevation(PodgonShapes.card, 4.dp)) {
            SettingsRow(
                stringResource(Res.string.settings_groups),
                state.groups?.let {
                    stringResource(Res.string.settings_groups_count, it)
                },
                onGroups,
            )
            Divider()
            SettingsRow(stringResource(Res.string.settings_sections), state.pinned?.toString(), onSections)
            Divider()
            Row(
                Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(start = 18.dp, end = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(Res.string.settings_theme),
                    style = MaterialTheme.typography.bodyMedium,
                    color = c.ink,
                    modifier = Modifier.weight(1f),
                )
                ThemeSwitch(mode, viewModel::setTheme)
            }
        }
        Text(
            stringResource(Res.string.settings_version, version),
            style = MaterialTheme.typography.bodySmall,
            color = c.muted,
            modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 120.dp),
        )
    }
}

@Composable
private fun SettingsRow(
    title: String,
    value: String?,
    onClick: () -> Unit,
) {
    val c = podgonColors
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .combinedClickable(role = Role.Button, onClick = onClick)
            .padding(start = 18.dp, end = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(title, style = MaterialTheme.typography.bodyMedium, color = c.ink, modifier = Modifier.weight(1f))
        value?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = c.muted) }
        Icon(PodgonIcons.ChevronRight, null, tint = c.muted, modifier = Modifier.size(18.dp))
    }
}

/** Переключатель темы из трёх иконок: как в системе · светлая · тёмная (у каждой — описание для диктора). */
@Composable
fun ThemeSwitch(
    mode: ThemeMode,
    onSelect: (ThemeMode) -> Unit,
) {
    val c = podgonColors
    val options =
        listOf(
            Triple(ThemeMode.SYSTEM, PodgonIcons.Monitor, stringResource(Res.string.theme_system)),
            Triple(ThemeMode.LIGHT, PodgonIcons.Sun, stringResource(Res.string.theme_light)),
            Triple(ThemeMode.DARK, PodgonIcons.Moon, stringResource(Res.string.theme_dark)),
        )
    Row(Modifier.clip(CircleShape).background(c.chip).padding(3.dp)) {
        options.forEach { (value, icon, label) -> ThemeOption(value == mode, icon, label) { onSelect(value) } }
    }
}

@Composable
private fun ThemeOption(
    on: Boolean,
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    val c = podgonColors
    Box(
        Modifier
            .size(width = 44.dp, height = 34.dp)
            .clip(CircleShape)
            .background(if (on) c.surface else Color.Transparent)
            .combinedClickable(role = Role.RadioButton, onClickLabel = label, onClick = onClick)
            .semantics {
                contentDescription = label
                selected = on
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = if (on) c.ink else c.muted, modifier = Modifier.size(19.dp))
    }
    Spacer(Modifier.height(0.dp))
}
