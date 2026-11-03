package app.podgon.mobile.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.podgon.mobile.core.network.GroupsApi
import app.podgon.mobile.core.network.SavedFiltersApi
import app.podgon.mobile.core.settings.ThemeSettings
import app.podgon.mobile.designsystem.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsState(
    val groups: Int? = null,
    val pinned: Int? = null,
)

/** «Настройки» (И17): число групп, закреплённых секций и тема (хранится на устройстве). */
class SettingsViewModel(
    private val groups: GroupsApi,
    private val filters: SavedFiltersApi,
    private val theme: ThemeSettings,
) : ViewModel() {
    private val _state = MutableStateFlow(SettingsState())
    val state: StateFlow<SettingsState> = _state.asStateFlow()
    val themeMode: StateFlow<ThemeMode> = theme.mode

    fun load() {
        viewModelScope.launch {
            val list = groups.list().getOrNull()
            val saved = filters.list().getOrNull()
            _state.update {
                it.copy(
                    groups = list?.size ?: it.groups,
                    pinned = saved?.count { f -> f.inFeed } ?: it.pinned,
                )
            }
        }
    }

    fun setTheme(mode: ThemeMode) {
        viewModelScope.launch { theme.set(mode) }
    }
}
