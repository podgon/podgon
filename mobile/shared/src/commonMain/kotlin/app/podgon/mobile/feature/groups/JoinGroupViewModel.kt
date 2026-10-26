package app.podgon.mobile.feature.groups

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.podgon.contract.InvitationPreview
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

data class JoinGroupState(
    val token: String = "",
    /** Сведения о группе после проверки кода: пользователь подтверждает вступление. */
    val preview: InvitationPreview? = null,
    val loading: Boolean = false,
    val error: AppError? = null,
)

/** Вступление по коду: ввод кода → предпросмотр группы → подтверждение. */
class JoinGroupViewModel(
    private val api: GroupsApi,
) : ViewModel() {
    private val _state = MutableStateFlow(JoinGroupState())
    val state: StateFlow<JoinGroupState> = _state.asStateFlow()

    private val _joined = Channel<String>(Channel.BUFFERED)
    val joined = _joined.receiveAsFlow()

    fun onTokenChange(value: String) {
        _state.update { it.copy(token = value.trim(), preview = null, error = null) }
    }

    fun check() {
        val current = _state.value
        if (current.loading || current.token.isEmpty()) return
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            val result = api.previewInvitation(current.token)
            _state.update { it.copy(loading = false, preview = result.getOrNull(), error = result.appError()) }
        }
    }

    fun confirm() {
        val current = _state.value
        if (current.loading || current.preview == null) return
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            val result = api.acceptInvitation(current.token)
            _state.update { it.copy(loading = false, error = result.appError()) }
            result.getOrNull()?.let { _joined.send(it.id) }
        }
    }
}
