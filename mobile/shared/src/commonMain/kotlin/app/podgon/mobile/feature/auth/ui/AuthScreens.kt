package app.podgon.mobile.feature.auth.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.podgon.contract.FieldErrorReason
import app.podgon.mobile.core.error.message
import app.podgon.mobile.designsystem.FormScaffold
import app.podgon.mobile.resources.Res
import app.podgon.mobile.resources.action_continue
import app.podgon.mobile.resources.action_create_account
import app.podgon.mobile.resources.action_resend
import app.podgon.mobile.resources.code_hint
import app.podgon.mobile.resources.code_label
import app.podgon.mobile.resources.code_resend_in
import app.podgon.mobile.resources.code_resent
import app.podgon.mobile.resources.code_title
import app.podgon.mobile.resources.display_name_label
import app.podgon.mobile.resources.email_hint
import app.podgon.mobile.resources.email_label
import app.podgon.mobile.resources.email_title
import app.podgon.mobile.resources.field_invalid
import app.podgon.mobile.resources.field_required
import app.podgon.mobile.resources.field_too_long
import app.podgon.mobile.resources.field_too_short
import app.podgon.mobile.resources.register_title
import app.podgon.mobile.resources.username_hint
import app.podgon.mobile.resources.username_label
import org.jetbrains.compose.resources.stringResource

@Composable
fun EmailScreen(
    viewModel: EmailViewModel,
    onCodeRequested: (email: String) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is EmailEffect.CodeRequested -> onCodeRequested(effect.email)
            }
        }
    }
    FormScaffold {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(stringResource(Res.string.email_title), style = MaterialTheme.typography.headlineMedium)
            Text(stringResource(Res.string.email_hint), style = MaterialTheme.typography.bodyMedium)
            OutlinedTextField(
                value = state.email,
                onValueChange = viewModel::onEmailChange,
                label = { Text(stringResource(Res.string.email_label)) },
                singleLine = true,
                isError = state.emailInvalid != null,
                supportingText = state.emailInvalid?.let { reason -> { Text(reason.message()) } },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier.fillMaxWidth(),
            )
            ErrorText(state.error)
            Button(onClick = viewModel::submit, enabled = !state.loading, modifier = Modifier.fillMaxWidth()) {
                if (state.loading) {
                    CircularProgressIndicator(
                        Modifier.height(20.dp),
                    )
                } else {
                    Text(stringResource(Res.string.action_continue))
                }
            }
        }
    }
}

@Composable
fun CodeScreen(
    viewModel: CodeViewModel,
    onRegistrationRequired: (registrationToken: String) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is CodeEffect.RegistrationRequired -> onRegistrationRequired(effect.registrationToken)
                CodeEffect.BackToEmail -> Unit
            }
        }
    }
    FormScaffold {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(stringResource(Res.string.code_title), style = MaterialTheme.typography.headlineMedium)
            Text(stringResource(Res.string.code_hint, state.email), style = MaterialTheme.typography.bodyMedium)
            OutlinedTextField(
                value = state.code,
                onValueChange = viewModel::onCodeChange,
                label = { Text(stringResource(Res.string.code_label)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                modifier = Modifier.fillMaxWidth(),
            )
            ErrorText(state.error)
            if (state.resent) Text(stringResource(Res.string.code_resent), style = MaterialTheme.typography.bodySmall)
            Button(
                onClick = viewModel::verify,
                enabled = !state.loading && state.code.length == 6,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (state.loading) {
                    CircularProgressIndicator(
                        Modifier.height(20.dp),
                    )
                } else {
                    Text(stringResource(Res.string.action_continue))
                }
            }
            TextButton(onClick = viewModel::resend, enabled = !state.loading && state.resendSecondsLeft == 0) {
                Text(
                    if (state.resendSecondsLeft > 0) {
                        stringResource(Res.string.code_resend_in, state.resendSecondsLeft)
                    } else {
                        stringResource(Res.string.action_resend)
                    },
                )
            }
        }
    }
}

@Composable
fun RegisterScreen(viewModel: RegisterViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    FormScaffold {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(stringResource(Res.string.register_title), style = MaterialTheme.typography.headlineMedium)
            OutlinedTextField(
                value = state.username,
                onValueChange = viewModel::onUsernameChange,
                label = { Text(stringResource(Res.string.username_label)) },
                supportingText = {
                    Text(state.usernameError?.message() ?: stringResource(Res.string.username_hint))
                },
                isError = state.usernameError != null,
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = state.displayName,
                onValueChange = viewModel::onDisplayNameChange,
                label = { Text(stringResource(Res.string.display_name_label)) },
                supportingText = state.displayNameError?.let { reason -> { Text(reason.message()) } },
                isError = state.displayNameError != null,
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            ErrorText(state.error)
            Button(onClick = viewModel::submit, enabled = !state.loading, modifier = Modifier.fillMaxWidth()) {
                if (state.loading) {
                    CircularProgressIndicator(
                        Modifier.height(20.dp),
                    )
                } else {
                    Text(stringResource(Res.string.action_create_account))
                }
            }
        }
    }
}

@Composable
internal fun ErrorText(error: app.podgon.mobile.core.error.AppError?) {
    if (error != null) {
        Text(error.message(), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
    } else {
        Spacer(Modifier.height(0.dp))
    }
}

@Composable
internal fun FieldErrorReason.message(): String =
    when (this) {
        FieldErrorReason.REQUIRED -> stringResource(Res.string.field_required)
        FieldErrorReason.TOO_SHORT -> stringResource(Res.string.field_too_short)
        FieldErrorReason.TOO_LONG -> stringResource(Res.string.field_too_long)
        FieldErrorReason.INVALID_FORMAT, FieldErrorReason.RESERVED -> stringResource(Res.string.field_invalid)
    }
