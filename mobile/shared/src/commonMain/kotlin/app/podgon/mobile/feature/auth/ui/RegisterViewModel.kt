package app.podgon.mobile.feature.auth.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.podgon.contract.FieldErrorReason
import app.podgon.contract.Validation
import app.podgon.mobile.core.error.AppError
import app.podgon.mobile.core.error.appError
import app.podgon.mobile.feature.auth.data.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RegisterState(
    val username: String = "",
    val displayName: String = "",
    val usernameError: FieldErrorReason? = null,
    val displayNameError: FieldErrorReason? = null,
    val loading: Boolean = false,
    val error: AppError? = null,
)

class RegisterViewModel(
    private val registrationToken: String,
    private val auth: AuthRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(RegisterState())
    val state: StateFlow<RegisterState> = _state.asStateFlow()

    fun onUsernameChange(value: String) {
        _state.update { it.copy(username = value.trim(), usernameError = null, error = null) }
    }

    fun onDisplayNameChange(value: String) {
        _state.update { it.copy(displayName = value, displayNameError = null, error = null) }
    }

    /** После успеха сессия открывается, и приложение само переключается на основной экран. */
    fun submit() {
        val current = _state.value
        if (current.loading) return
        val usernameError = Validation.username(current.username)
        val displayNameError = Validation.displayName(current.displayName)
        if (usernameError != null || displayNameError != null) {
            _state.update { it.copy(usernameError = usernameError, displayNameError = displayNameError) }
            return
        }
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            val result = auth.register(registrationToken, current.username, current.displayName)
            _state.update { it.copy(loading = false, error = result.appError()) }
        }
    }
}
