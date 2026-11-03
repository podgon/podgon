package app.podgon.mobile.core.settings

import app.podgon.mobile.core.cache.PREF_PREFIX
import app.podgon.mobile.core.cache.ResponseCache
import app.podgon.mobile.designsystem.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Выбор темы (И11): хранится только на устройстве и не сбрасывается при выходе из аккаунта. */
class ThemeSettings(
    private val cache: ResponseCache,
) {
    private val _mode = MutableStateFlow(ThemeMode.SYSTEM)
    val mode: StateFlow<ThemeMode> = _mode.asStateFlow()

    suspend fun load() {
        cache.read<ThemeMode>(KEY)?.let { _mode.value = it }
    }

    suspend fun set(mode: ThemeMode) {
        _mode.value = mode
        cache.write(KEY, mode)
    }

    private companion object {
        const val KEY = PREF_PREFIX + "theme"
    }
}
