package app.podgon.mobile.feature.groups

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.podgon.contract.GroupSummary
import app.podgon.mobile.core.error.AppError
import app.podgon.mobile.core.error.appError
import app.podgon.mobile.core.network.GroupsApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class GroupsState(
    val loading: Boolean = true,
    val groups: List<GroupSummary> = emptyList(),
    val error: AppError? = null,
)

/** Список групп пользователя (главный экран). */
class GroupsViewModel(
    private val api: GroupsApi,
) : ViewModel() {
    private val _state = MutableStateFlow(GroupsState())
    val state: StateFlow<GroupsState> = _state.asStateFlow()

    /** Загрузку запускает экран при каждом показе (в том числе при возврате со страницы группы). */
    fun load() {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            val result = api.list()
            _state.update {
                it.copy(
                    loading = false,
                    groups = result.getOrNull() ?: it.groups,
                    error = result.appError(),
                )
            }
        }
    }
}
