package app.podgon.mobile.feature.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.podgon.contract.SessionInfo
import app.podgon.contract.UserProfile
import app.podgon.mobile.core.error.AppError
import app.podgon.mobile.core.error.appError
import app.podgon.mobile.core.network.MeApi
import app.podgon.mobile.feature.auth.data.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ProfileState(
    val loading: Boolean = true,
    val profile: UserProfile? = null,
    val sessions: List<SessionInfo> = emptyList(),
    val error: AppError? = null,
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
}
