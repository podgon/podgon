package app.podgon.mobile.feature.groups

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.podgon.contract.CreateInvitationRequest
import app.podgon.contract.FieldErrorReason
import app.podgon.contract.InvitationCreated
import app.podgon.contract.InvitationInfo
import app.podgon.contract.Validation
import app.podgon.mobile.core.error.AppError
import app.podgon.mobile.core.error.appError
import app.podgon.mobile.core.network.GroupsApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class InvitationsState(
    val loading: Boolean = true,
    val invitations: List<InvitationInfo> = emptyList(),
    /** Только что созданное приглашение: токен показывается один раз. */
    val created: InvitationCreated? = null,
    val emailError: FieldErrorReason? = null,
    val error: AppError? = null,
)

/** Приглашения группы (для администратора): создать по коду или на email, посмотреть, отозвать. */
class InvitationsViewModel(
    private val groupId: String,
    private val api: GroupsApi,
) : ViewModel() {
    private val _state = MutableStateFlow(InvitationsState())
    val state: StateFlow<InvitationsState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            val result = api.invitations(groupId)
            _state.update {
                it.copy(loading = false, invitations = result.getOrNull() ?: it.invitations, error = result.appError())
            }
        }
    }

    /** [email] пустой — приглашение по коду. */
    fun create(
        email: String,
        maxUses: Int,
    ) {
        val address = email.trim().ifEmpty { null }
        val invalid = address?.let(Validation::email)
        if (invalid != null) {
            _state.update { it.copy(emailError = invalid) }
            return
        }
        _state.update { it.copy(emailError = null, error = null) }
        viewModelScope.launch {
            val result = api.createInvitation(groupId, CreateInvitationRequest(email = address, maxUses = maxUses))
            if (result.isSuccess) {
                _state.update { it.copy(created = result.getOrNull()) }
                load()
            } else {
                _state.update { it.copy(error = result.appError()) }
            }
        }
    }

    fun dismissCreated() = _state.update { it.copy(created = null) }

    fun revoke(invitation: InvitationInfo) {
        viewModelScope.launch {
            val result = api.revokeInvitation(groupId, invitation.id)
            if (result.isSuccess) load() else _state.update { it.copy(error = result.appError()) }
        }
    }
}
