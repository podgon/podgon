package app.podgon.mobile.feature.auth.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.podgon.contract.FieldErrorReason
import app.podgon.mobile.core.error.message
import app.podgon.mobile.designsystem.Avatar
import app.podgon.mobile.designsystem.PodgonLogo
import app.podgon.mobile.designsystem.podgonColors
import app.podgon.mobile.resources.Res
import app.podgon.mobile.resources.action_continue
import app.podgon.mobile.resources.action_create_account
import app.podgon.mobile.resources.action_resend
import app.podgon.mobile.resources.code_change_email
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

/**
 * Каркас шагов входа (без нижнего меню): логотип и заголовок сверху, содержимое, основная кнопка внизу
 * над клавиатурой.
 */
@Composable
private fun AuthScaffold(
    title: String,
    subtitle: String?,
    action: String,
    actionEnabled: Boolean,
    loading: Boolean,
    onAction: () -> Unit,
    content: @Composable () -> Unit,
) {
    val c = podgonColors
    Box(
        Modifier
            .fillMaxSize()
            .background(c.background)
            .safeDrawingPadding()
            .imePadding(),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(
                    rememberScrollState(),
                ).padding(horizontal = 24.dp)
                .padding(top = 40.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            PodgonLogo(48.dp)
            Text(title, style = MaterialTheme.typography.headlineMedium, color = c.ink)
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = c.muted) }
            content()
        }
        Button(
            onClick = onAction,
            enabled = actionEnabled && !loading,
            modifier =
                Modifier
                    .align(
                        Alignment.BottomCenter,
                    ).fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp)
                    .height(52.dp),
        ) {
            if (loading) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) else Text(action)
        }
    }
}

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
    AuthScaffold(
        title = stringResource(Res.string.email_title),
        subtitle = stringResource(Res.string.email_hint),
        action = stringResource(Res.string.action_continue),
        actionEnabled = state.email.isNotBlank(),
        loading = state.loading,
        onAction = viewModel::submit,
    ) {
        OutlinedTextField(
            value = state.email,
            onValueChange = viewModel::onEmailChange,
            label = { Text(stringResource(Res.string.email_label)) },
            singleLine = true,
            isError = state.emailInvalid != null,
            supportingText = state.emailInvalid?.let { reason -> { Text(reason.message()) } },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = { viewModel.submit() }),
            modifier = Modifier.fillMaxWidth(),
        )
        // место виджета SmartCaptcha «Я не робот» и строки про условия — этап 9
        ErrorText(state.error)
    }
}

/** Код: шесть ячеек поверх одного поля (автоподстановка кода из письма), «Изменить email», повтор с таймером. */
@Composable
fun CodeScreen(
    viewModel: CodeViewModel,
    onRegistrationRequired: (registrationToken: String) -> Unit,
    onChangeEmail: () -> Unit = {},
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val c = podgonColors
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is CodeEffect.RegistrationRequired -> onRegistrationRequired(effect.registrationToken)
                CodeEffect.BackToEmail -> onChangeEmail()
            }
        }
    }
    AuthScaffold(
        title = stringResource(Res.string.code_title),
        subtitle = stringResource(Res.string.code_hint, state.email),
        action = stringResource(Res.string.action_continue),
        actionEnabled = state.code.length == CODE_LENGTH,
        loading = state.loading,
        onAction = viewModel::verify,
    ) {
        val label = stringResource(Res.string.code_label)
        BasicTextField(
            value = state.code,
            onValueChange = { viewModel.onCodeChange(it.filter(Char::isDigit).take(CODE_LENGTH)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { viewModel.verify() }),
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = label },
            decorationBox = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    repeat(CODE_LENGTH) { index ->
                        val digit = state.code.getOrNull(index)
                        Box(
                            Modifier
                                .weight(1f)
                                .height(56.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(c.surface)
                                .border(
                                    1.5.dp,
                                    if (index ==
                                        state.code.length
                                    ) {
                                        c.accent
                                    } else {
                                        c.line
                                    },
                                    RoundedCornerShape(14.dp),
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                digit?.toString() ?: "",
                                fontSize = 24.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = c.ink,
                            )
                        }
                    }
                }
            },
        )
        ErrorText(state.error)
        if (state.resent) {
            Text(
                stringResource(Res.string.code_resent),
                style = MaterialTheme.typography.bodySmall,
                color = c.muted,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onChangeEmail) { Text(stringResource(Res.string.code_change_email)) }
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

/** Новый профиль (только при регистрации): аватар по умолчанию, отображаемое имя, @username с подсказкой. */
@Composable
fun RegisterScreen(viewModel: RegisterViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    AuthScaffold(
        title = stringResource(Res.string.register_title),
        subtitle = null,
        action = stringResource(Res.string.action_create_account),
        actionEnabled = state.username.isNotBlank(),
        loading = state.loading,
        onAction = viewModel::submit,
    ) {
        Avatar(72.dp)
        OutlinedTextField(
            value = state.displayName,
            onValueChange = viewModel::onDisplayNameChange,
            label = { Text(stringResource(Res.string.display_name_label)) },
            supportingText = state.displayNameError?.let { reason -> { Text(reason.message()) } },
            isError = state.displayNameError != null,
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = state.username,
            onValueChange = viewModel::onUsernameChange,
            label = { Text(stringResource(Res.string.username_label)) },
            prefix = { Text("@") },
            supportingText = {
                Text(state.usernameError?.message() ?: stringResource(Res.string.username_hint))
            },
            isError = state.usernameError != null,
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        ErrorText(state.error)
    }
}

private const val CODE_LENGTH = 6

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
