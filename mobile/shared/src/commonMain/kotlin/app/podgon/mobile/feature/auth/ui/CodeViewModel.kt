package app.podgon.mobile.feature.auth.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.podgon.contract.ErrorCode
import app.podgon.contract.Validation
import app.podgon.mobile.core.error.AppError
import app.podgon.mobile.core.error.appError
import app.podgon.mobile.feature.auth.data.AuthRepository
import app.podgon.mobile.feature.auth.data.VerifyOutcome
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CodeState(
    val email: String,
    val code: String = "",
    val loading: Boolean = false,
    val error: AppError? = null,
    /** Сколько секунд осталось до повторной отправки (0 — можно отправлять). */
    val resendSecondsLeft: Int = RESEND_INTERVAL_SECONDS,
    /** Код только что отправлен повторно. */
    val resent: Boolean = false,
)

sealed interface CodeEffect {
    /** Нужно завершить регистрацию. */
    data class RegistrationRequired(
        val registrationToken: String,
    ) : CodeEffect

    /** Код неверен — запросить новый (экран возвращается к вводу email). */
    data object BackToEmail : CodeEffect
}

const val RESEND_INTERVAL_SECONDS = 60

class CodeViewModel(
    email: String,
    private val auth: AuthRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(CodeState(email = email))
    val state: StateFlow<CodeState> = _state.asStateFlow()

    private val _effects = Channel<CodeEffect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    init {
        startResendTimer(RESEND_INTERVAL_SECONDS)
    }

    fun onCodeChange(value: String) {
        val digits = value.filter { it in '0'..'9' }.take(Validation.CODE_LENGTH)
        _state.update { it.copy(code = digits, error = null) }
        if (digits.length == Validation.CODE_LENGTH) verify()
    }

    fun verify() {
        val current = _state.value
        if (current.loading || current.code.length != Validation.CODE_LENGTH) return
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            val result = auth.verify(current.email, current.code)
            val error = result.appError()
            _state.update { it.copy(loading = false, error = error, code = if (error == null) it.code else "") }
            when (val outcome = result.getOrNull()) {
                is VerifyOutcome.RegistrationRequired ->
                    _effects.send(
                        CodeEffect.RegistrationRequired(outcome.registrationToken),
                    )
                // SignedIn обрабатывает состояние сессии: приложение переключается на основной экран
                VerifyOutcome.SignedIn, null -> Unit
            }
        }
    }

    fun resend() {
        val current = _state.value
        if (current.loading || current.resendSecondsLeft > 0) return
        _state.update { it.copy(loading = true, error = null, resent = false) }
        viewModelScope.launch {
            val result = auth.requestCode(current.email)
            val error = result.appError()
            _state.update { it.copy(loading = false, error = error, resent = error == null, code = "") }
            val retry = (error as? AppError.Api)?.takeIf { it.code == ErrorCode.RATE_LIMITED }?.retryAfterSeconds
            startResendTimer(retry?.toInt() ?: RESEND_INTERVAL_SECONDS)
        }
    }

    private fun startResendTimer(seconds: Int) {
        _state.update { it.copy(resendSecondsLeft = seconds) }
        viewModelScope.launch {
            while (_state.value.resendSecondsLeft > 0) {
                delay(ONE_SECOND_MS)
                _state.update { it.copy(resendSecondsLeft = (it.resendSecondsLeft - 1).coerceAtLeast(0)) }
            }
        }
    }

    private companion object {
        const val ONE_SECOND_MS = 1000L
    }
}
