package app.podgon.mobile.feature.auth.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.podgon.contract.FieldErrorReason
import app.podgon.contract.Validation
import app.podgon.mobile.core.error.AppError
import app.podgon.mobile.core.error.appError
import app.podgon.mobile.feature.auth.data.AuthRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class EmailState(
    val email: String = "",
    val loading: Boolean = false,
    /** Ошибка формата email (локальная проверка). */
    val emailInvalid: FieldErrorReason? = null,
    val error: AppError? = null,
)

sealed interface EmailEffect {
    /** Код отправлен — перейти к вводу кода. */
    data class CodeRequested(
        val email: String,
    ) : EmailEffect
}

class EmailViewModel(
    private val auth: AuthRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(EmailState())
    val state: StateFlow<EmailState> = _state.asStateFlow()

    private val _effects = Channel<EmailEffect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    fun onEmailChange(value: String) {
        _state.update { it.copy(email = value, emailInvalid = null, error = null) }
    }

    fun submit() {
        val current = _state.value
        if (current.loading) return
        val invalid = Validation.email(current.email)
        if (invalid != null) {
            _state.update { it.copy(emailInvalid = invalid) }
            return
        }
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            val result = auth.requestCode(current.email)
            val error = result.appError()
            _state.update { it.copy(loading = false, error = error) }
            // при 429 «слишком часто» код уже был отправлен ранее — всё равно даём ввести его
            if (error == null || isTooFrequent(error)) {
                _effects.send(EmailEffect.CodeRequested(Validation.normalizeEmail(current.email)))
            }
        }
    }

    private fun isTooFrequent(error: AppError) = error is AppError.Api && error.status == RATE_LIMITED_STATUS

    private companion object {
        const val RATE_LIMITED_STATUS = 429
    }
}
