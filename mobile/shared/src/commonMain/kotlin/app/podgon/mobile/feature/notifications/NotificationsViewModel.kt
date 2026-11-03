package app.podgon.mobile.feature.notifications

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.podgon.contract.NotificationInfo
import app.podgon.contract.NotificationSettings
import app.podgon.mobile.core.error.AppError
import app.podgon.mobile.core.error.appError
import app.podgon.mobile.core.network.NotificationsApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class NotificationsState(
    val loading: Boolean = true,
    val items: List<NotificationInfo> = emptyList(),
    val nextCursor: String? = null,
    /** Непрочитанные на момент открытия: отмечены точкой, хотя на сервере уже прочитаны. */
    val fresh: Set<String> = emptySet(),
    val error: AppError? = null,
)

/** Экран уведомлений: при открытии все отмечаются прочитанными, новые выделены до ухода с экрана. */
class NotificationsViewModel(
    private val api: NotificationsApi,
    private val counter: UnreadCounter,
) : ViewModel() {
    private val _state = MutableStateFlow(NotificationsState())
    val state: StateFlow<NotificationsState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            val result = api.list()
            val page = result.getOrNull()
            _state.update {
                it.copy(
                    loading = false,
                    items = page?.items ?: it.items,
                    nextCursor = page?.nextCursor,
                    fresh =
                        it.fresh +
                            page
                                ?.items
                                .orEmpty()
                                .filter { n -> !n.read }
                                .map { n -> n.id },
                    error = result.appError(),
                )
            }
            if (page != null && page.unread > 0 && api.markRead().isSuccess) counter.clear()
        }
    }

    fun loadMore() {
        val cursor = _state.value.nextCursor ?: return
        viewModelScope.launch {
            val page = api.list(cursor).getOrNull() ?: return@launch
            _state.update { it.copy(items = it.items + page.items, nextCursor = page.nextCursor) }
        }
    }
}

data class NotificationSettingsState(
    val settings: NotificationSettings? = null,
    val error: AppError? = null,
)

/** «О чём уведомлять»: переключатели сохраняются сразу. */
class NotificationSettingsViewModel(
    private val api: NotificationsApi,
) : ViewModel() {
    private val _state = MutableStateFlow(NotificationSettingsState())
    val state: StateFlow<NotificationSettingsState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val result = api.settings()
            _state.update { NotificationSettingsState(result.getOrNull(), result.appError()) }
        }
    }

    fun change(settings: NotificationSettings) {
        val previous = _state.value.settings
        _state.update { it.copy(settings = settings, error = null) }
        viewModelScope.launch {
            val result = api.saveSettings(settings)
            if (result.isFailure) _state.update { it.copy(settings = previous, error = result.appError()) }
        }
    }
}
