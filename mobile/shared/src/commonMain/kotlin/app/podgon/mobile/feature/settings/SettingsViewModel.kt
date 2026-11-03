package app.podgon.mobile.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.podgon.contract.UpdateProfileRequest
import app.podgon.mobile.core.network.GroupsApi
import app.podgon.mobile.core.network.MeApi
import app.podgon.mobile.core.network.SavedFiltersApi
import app.podgon.mobile.core.settings.AppLanguage
import app.podgon.mobile.core.settings.LanguageSettings
import app.podgon.mobile.core.settings.ThemeSettings
import app.podgon.mobile.core.settings.systemLanguage
import app.podgon.mobile.designsystem.ThemeMode
import app.podgon.mobile.feature.reviews.CategoryRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsState(
    val groups: Int? = null,
    val pinned: Int? = null,
)

/** «Настройки» (И17): число групп, закреплённых секций, тема и язык (хранятся на устройстве). */
class SettingsViewModel(
    private val groups: GroupsApi,
    private val filters: SavedFiltersApi,
    private val theme: ThemeSettings,
    private val languages: LanguageSettings,
    private val me: MeApi,
    private val categories: CategoryRepository? = null,
) : ViewModel() {
    private val _state = MutableStateFlow(SettingsState())
    val state: StateFlow<SettingsState> = _state.asStateFlow()
    val themeMode: StateFlow<ThemeMode> = theme.mode
    val language: StateFlow<AppLanguage> = languages.language

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

    /**
     * Сначала язык профиля на сервере (письма и названия категорий), затем интерфейс: на Android смена языка
     * пересоздаёт экран. Без сети язык профиля обновится при следующей смене.
     */
    fun setLanguage(language: AppLanguage) {
        if (language == languages.language.value) return
        viewModelScope.launch {
            val code = language.code ?: if (systemLanguage() == "ru") "ru" else "en"
            me.update(UpdateProfileRequest(locale = code))
            languages.set(language)
            // названия категорий — на языке профиля: открытые экраны получат новый набор
            categories?.set()
        }
    }
}
