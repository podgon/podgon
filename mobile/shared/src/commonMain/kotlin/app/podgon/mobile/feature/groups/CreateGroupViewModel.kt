package app.podgon.mobile.feature.groups

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.podgon.contract.CreateGroupRequest
import app.podgon.contract.FieldErrorReason
import app.podgon.contract.Validation
import app.podgon.mobile.core.error.AppError
import app.podgon.mobile.core.error.appError
import app.podgon.mobile.core.network.GroupsApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CreateGroupState(
    val name: String = "",
    val description: String = "",
    val nameError: FieldErrorReason? = null,
    val descriptionError: FieldErrorReason? = null,
    val loading: Boolean = false,
    val error: AppError? = null,
)

class CreateGroupViewModel(
    private val api: GroupsApi,
) : ViewModel() {
    private val _state = MutableStateFlow(CreateGroupState())
    val state: StateFlow<CreateGroupState> = _state.asStateFlow()

    private val _created = Channel<String>(Channel.BUFFERED)

    /** Идентификаторы созданных групп: по ним экран переходит на страницу группы. */
    val created = _created.receiveAsFlow()

    fun onNameChange(value: String) {
        _state.update { it.copy(name = value, nameError = null, error = null) }
    }

    fun onDescriptionChange(value: String) {
        _state.update { it.copy(description = value, descriptionError = null, error = null) }
    }

    fun submit() {
        val current = _state.value
        if (current.loading) return
        val nameError = Validation.groupName(current.name)
        val descriptionError = Validation.groupDescription(current.description)
        if (nameError != null || descriptionError != null) {
            _state.update { it.copy(nameError = nameError, descriptionError = descriptionError) }
            return
        }
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            val result = api.create(CreateGroupRequest(current.name.trim(), current.description.trim()))
            _state.update { it.copy(loading = false, error = result.appError()) }
            result.getOrNull()?.let { _created.send(it.id) }
        }
    }
}
