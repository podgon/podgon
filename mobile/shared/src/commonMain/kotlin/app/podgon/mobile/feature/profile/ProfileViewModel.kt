package app.podgon.mobile.feature.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.podgon.contract.AccountDeleteImpact
import app.podgon.contract.SessionInfo
import app.podgon.contract.UserProfile
import app.podgon.contract.Validation
import app.podgon.mobile.core.error.AppError
import app.podgon.mobile.core.error.appError
import app.podgon.mobile.core.network.MeApi
import app.podgon.mobile.feature.auth.data.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Диалог удаления аккаунта: последствия → код из письма → подтверждение. */
data class DeletionState(
    val impact: AccountDeleteImpact,
    val deleteReviews: Boolean = false,
    /** Код уже выслан: показываем поле ввода. */
    val codeSent: Boolean = false,
    val code: String = "",
    val busy: Boolean = false,
    val error: AppError? = null,
) {
    val blocked: Boolean get() = impact.blockingGroups.isNotEmpty()
    val canConfirm: Boolean get() = codeSent && !busy && Validation.code(code) == null
}

data class ProfileState(
    val loading: Boolean = true,
    val profile: UserProfile? = null,
    val sessions: List<SessionInfo> = emptyList(),
    val error: AppError? = null,
    /** `null` — диалог удаления закрыт. */
    val deletion: DeletionState? = null,
)

class ProfileViewModel(
    private val me: MeApi,
    private val auth: AuthRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(ProfileState())
    val state: StateFlow<ProfileState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            val profile = me.me()
            val sessions = me.sessions()
            _state.update {
                it.copy(
                    loading = false,
                    profile = profile.getOrNull() ?: it.profile,
                    sessions = sessions.getOrNull() ?: it.sessions,
                    error = profile.appError() ?: sessions.appError(),
                )
            }
        }
    }

    fun revokeSession(id: String) {
        viewModelScope.launch {
            val result = me.revokeSession(id)
            if (result.isSuccess) {
                me.sessions().getOrNull()?.let { list -> _state.update { it.copy(sessions = list, error = null) } }
            } else {
                _state.update { it.copy(error = result.appError()) }
            }
        }
    }

    /** Выход из аккаунта; приложение переключается на экран входа по состоянию сессии. */
    fun logout() {
        viewModelScope.launch { auth.logout() }
    }

    /** Узнаёт последствия удаления (в том числе группы, которые мешают) и открывает диалог. */
    fun startDeletion() {
        viewModelScope.launch {
            val result = me.deleteImpact()
            result.getOrNull()?.let { impact ->
                _state.update { it.copy(deletion = DeletionState(impact), error = null) }
            }
            result.appError()?.let { error -> _state.update { it.copy(error = error) } }
        }
    }

    fun cancelDeletion() = _state.update { it.copy(deletion = null) }

    fun setDeleteReviews(value: Boolean) = updateDeletion { it.copy(deleteReviews = value) }

    fun onCodeChange(value: String) =
        updateDeletion {
            it.copy(code = value.filter(Char::isDigit).take(Validation.CODE_LENGTH))
        }

    /** Высылает код подтверждения на почту аккаунта. */
    fun requestCode() {
        val current = _state.value.deletion ?: return
        if (current.busy || current.blocked) return
        updateDeletion { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            val result = me.requestDeletionCode()
            updateDeletion {
                it.copy(
                    busy = false,
                    codeSent = it.codeSent || result.isSuccess,
                    error = result.appError(),
                )
            }
        }
    }

    fun confirmDeletion() {
        val current = _state.value.deletion ?: return
        if (!current.canConfirm) return
        updateDeletion { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            val result = me.deleteAccount(current.code, current.deleteReviews)
            if (result.isSuccess) {
                auth.accountDeleted()
            } else {
                updateDeletion { it.copy(busy = false, error = result.appError()) }
            }
        }
    }

    private fun updateDeletion(change: (DeletionState) -> DeletionState) {
        _state.update { state -> state.copy(deletion = state.deletion?.let(change)) }
    }
}
