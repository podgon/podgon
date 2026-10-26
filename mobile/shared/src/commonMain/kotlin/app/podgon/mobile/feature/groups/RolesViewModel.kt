package app.podgon.mobile.feature.groups

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.podgon.contract.CreateRoleRequest
import app.podgon.contract.FieldErrorReason
import app.podgon.contract.RoleInfo
import app.podgon.contract.UpdateRoleRequest
import app.podgon.contract.Validation
import app.podgon.mobile.core.error.AppError
import app.podgon.mobile.core.error.appError
import app.podgon.mobile.core.network.GroupsApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RolesState(
    val loading: Boolean = true,
    val roles: List<RoleInfo> = emptyList(),
    val error: AppError? = null,
    /** Ошибка названия в диалоге создания/изменения роли (локальная проверка). */
    val titleError: FieldErrorReason? = null,
)

/** Управление ролями группы (для администратора). */
class RolesViewModel(
    private val groupId: String,
    private val api: GroupsApi,
) : ViewModel() {
    private val _state = MutableStateFlow(RolesState())
    val state: StateFlow<RolesState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            val result = api.roles(groupId)
            _state.update {
                it.copy(
                    loading = false,
                    roles = result.getOrNull() ?: it.roles,
                    error = result.appError(),
                )
            }
        }
    }

    /** Возвращает `true`, если запрос принят (диалог можно закрыть). */
    fun create(
        title: String,
        description: String,
        admin: Boolean,
        onDone: () -> Unit,
    ) = save(title, onDone) { api.createRole(groupId, CreateRoleRequest(title.trim(), description.trim(), admin)) }

    fun update(
        role: RoleInfo,
        title: String,
        description: String,
        admin: Boolean,
        onDone: () -> Unit,
    ) = save(title, onDone) {
        api.updateRole(groupId, role.id, UpdateRoleRequest(title.trim(), description.trim(), admin))
    }

    fun delete(role: RoleInfo) {
        viewModelScope.launch {
            val result = api.deleteRole(groupId, role.id)
            if (result.isSuccess) load() else _state.update { it.copy(error = result.appError()) }
        }
    }

    fun clearTitleError() = _state.update { it.copy(titleError = null, error = null) }

    private fun save(
        title: String,
        onDone: () -> Unit,
        call: suspend () -> Result<*>,
    ) {
        val invalid = Validation.roleTitle(title)
        if (invalid != null) {
            _state.update { it.copy(titleError = invalid) }
            return
        }
        viewModelScope.launch {
            val result = call()
            if (result.isSuccess) {
                _state.update { it.copy(titleError = null, error = null) }
                load()
                onDone()
            } else {
                _state.update { it.copy(error = result.appError()) }
            }
        }
    }
}
