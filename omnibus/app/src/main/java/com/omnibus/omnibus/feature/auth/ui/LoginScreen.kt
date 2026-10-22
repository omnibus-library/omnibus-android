package com.omnibus.omnibus.feature.auth.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.omnibus.omnibus.R
import com.omnibus.omnibus.feature.auth.LoginStatus
import com.omnibus.omnibus.feature.auth.LoginUIState
import com.omnibus.omnibus.feature.auth.LoginViewModel
import com.omnibus.omnibus.ui.components.FilledButton
import com.omnibus.omnibus.ui.components.InlineError
import com.omnibus.omnibus.ui.components.OmnibusTextField
import com.omnibus.omnibus.ui.components.TextLinkButton
import com.omnibus.omnibus.ui.theme.Motion
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import com.omnibus.omnibus.ui.theme.Spacing
import com.omnibus.omnibus.ui.theme.displayTextStyle
import com.omnibus.omnibus.ui.theme.monoTextStyle
import com.omnibus.omnibus.ui.theme.uiTextStyle
import kotlinx.serialization.Serializable

@Serializable
data object LoginScreenRoute : NavKey

private enum class LoginMode { LOGIN, REGISTER }

abstract class LoginScreenAction {
    data object Login : LoginScreenAction()
    data object Register : LoginScreenAction()
    data object ChangeServer : LoginScreenAction()
}

@Composable
fun LoginScreen(
    onAction: (LoginScreenAction) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LoginViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val currentAction by rememberUpdatedState(onAction)

    // The ViewModel can outlive this entry, so only act on LOGGED_IN results from a sign-in made during this visit.
    var awaitingLogin by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(uiState.loginStatus, awaitingLogin) {
        when (uiState.loginStatus) {
            LoginStatus.LOGGED_IN -> if (awaitingLogin) {
                awaitingLogin = false
                currentAction(LoginScreenAction.Login)
            }

            LoginStatus.MISSING_SERVER_CONFIG -> currentAction(LoginScreenAction.ChangeServer)
            else -> {
                /* do nothing specific */
            }
        }
    }

    LoginContent(
        modifier = modifier, uiState = uiState,
        onSignIn = { username, password ->
            awaitingLogin = true
            viewModel.attemptLogin(username, password)
        },
        onChangeServer = {
            currentAction(LoginScreenAction.ChangeServer)
        }
    )
}

@Composable
private fun LoginContent(
    modifier: Modifier,
    uiState: LoginUIState,
    initialMode: LoginMode = LoginMode.LOGIN,
    onSignIn: (username: String, password: String) -> Unit,
    onChangeServer: () -> Unit
) {
    val palette = OmnibusTheme.palette
    val haptics = LocalHapticFeedback.current
    val usernameFocusRequester = remember { FocusRequester() }
    val passwordFocusRequester = remember { FocusRequester() }

    var mode by rememberSaveable { mutableStateOf(initialMode) }
    var username by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var showError by rememberSaveable {
        mutableStateOf(uiState.loginStatus == LoginStatus.AUTH_FAILED)
    }

    val isBusy = uiState.loginStatus == LoginStatus.AUTH_PENDING
    val canSubmit = username.isNotBlank() && password.isNotEmpty() && !isBusy
    val errorMessage = stringResource(R.string.login_error_invalid_credentials)
        .takeIf { showError && uiState.loginStatus == LoginStatus.AUTH_FAILED }

    val submit = {
        if (canSubmit) {
            when (mode) {
                LoginMode.LOGIN -> onSignIn(username, password)
                // TODO: Call /api/auth/register with the same credentials, then sign in with the
                //  returned token exactly as a login does.
                LoginMode.REGISTER -> Unit
            }
        }
    }

    LaunchedEffect(Unit) { usernameFocusRequester.requestFocus() }

    LaunchedEffect(uiState.loginStatus) {
        when (uiState.loginStatus) {
            LoginStatus.AUTH_PENDING -> showError = false
            LoginStatus.LOGGED_IN -> haptics.performHapticFeedback(HapticFeedbackType.Confirm)
            LoginStatus.AUTH_FAILED -> {
                showError = true
                haptics.performHapticFeedback(HapticFeedbackType.Reject)
            }

            else -> Unit
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.bg0)
            .then(modifier)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.screen)
                .padding(top = 72.dp),
            verticalArrangement = Arrangement.spacedBy(Spacing.xl),
        ) {
            Header(mode = mode, serverAddress = uiState.serverAddress)

            Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                OmnibusTextField(
                    value = username,
                    onValueChange = { username = it },
                    placeholder = stringResource(R.string.login_username_placeholder),
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.None,
                        autoCorrectEnabled = false,
                        imeAction = ImeAction.Next,
                    ),
                    keyboardActions = KeyboardActions(
                        onNext = { passwordFocusRequester.requestFocus() }
                    ),
                    modifier = Modifier
                        .focusRequester(usernameFocusRequester)
                        .semantics {
                            contentType = when (mode) {
                                LoginMode.LOGIN -> ContentType.Username
                                LoginMode.REGISTER -> ContentType.NewUsername
                            }
                        },
                )

                OmnibusTextField(
                    value = password,
                    onValueChange = { password = it },
                    placeholder = stringResource(R.string.login_password_placeholder),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        autoCorrectEnabled = false,
                        imeAction = ImeAction.Go,
                    ),
                    keyboardActions = KeyboardActions(onGo = { submit() }),
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier
                        .focusRequester(passwordFocusRequester)
                        .semantics {
                            contentType = when (mode) {
                                LoginMode.LOGIN -> ContentType.Password
                                LoginMode.REGISTER -> ContentType.NewPassword
                            }
                        },
                )
            }

            InlineError(message = errorMessage)

            AnimatedVisibility(
                visible = mode == LoginMode.REGISTER,
                enter = fadeIn(Motion.settle()),
                exit = fadeOut(Motion.settle()),
            ) {
                Text(
                    text = stringResource(R.string.login_password_requirement),
                    style = uiTextStyle(12, FontWeight.Normal, palette.ink3),
                )
            }

            FilledButton(
                text = when (mode) {
                    LoginMode.LOGIN -> stringResource(R.string.login_sign_in)
                    LoginMode.REGISTER -> stringResource(R.string.login_create_account)
                },
                onClick = submit,
                enabled = canSubmit,
                isLoading = isBusy,
                loadingDescription = stringResource(R.string.login_signing_in),
            )

            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = when (mode) {
                        LoginMode.LOGIN -> stringResource(R.string.login_no_account)
                        LoginMode.REGISTER -> stringResource(R.string.login_have_account)
                    },
                    style = uiTextStyle(14, FontWeight.Normal, palette.ink2),
                )
                TextLinkButton(
                    text = when (mode) {
                        LoginMode.LOGIN -> stringResource(R.string.login_register)
                        LoginMode.REGISTER -> stringResource(R.string.login_sign_in)
                    },
                    color = palette.accent,
                    size = 14,
                    onClick = {
                        mode = when (mode) {
                            LoginMode.LOGIN -> LoginMode.REGISTER
                            LoginMode.REGISTER -> LoginMode.LOGIN
                        }
                        showError = false
                    },
                )
            }

            TextLinkButton(
                text = stringResource(R.string.login_change_server),
                color = palette.ink3,
                size = 13,
                // TODO: Sign out, clear the saved server address, and navigate back to the server
                //  configuration screen, replacing this screen on the back stack.
                onClick = onChangeServer,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )

            Spacer(modifier = Modifier.height(Spacing.xl))
        }
    }
}

@Composable
private fun Header(mode: LoginMode, serverAddress: String) {
    val palette = OmnibusTheme.palette

    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Text(
            text = when (mode) {
                LoginMode.LOGIN -> stringResource(R.string.login_title)
                LoginMode.REGISTER -> stringResource(R.string.login_title_register)
            },
            style = displayTextStyle(34, palette.ink0),
        )
        if (serverAddress.isNotEmpty()) {
            Text(
                text = serverAddress.removePrefix("http://"),
                style = monoTextStyle(13, palette.ink3),
            )
        }
    }
}

@Composable
@PreviewLightDark
fun LoginContentRoutePreviewBasic() {
    OmnibusTheme {
        LoginContent(
            modifier = Modifier,
            uiState = LoginUIState(serverAddress = "192.168.1.10:3000"),
            onSignIn = { _, _ -> },
            onChangeServer = {}
        )
    }
}

@Composable
@PreviewLightDark
fun LoginContentRoutePreviewPending() {
    OmnibusTheme {
        LoginContent(
            modifier = Modifier,
            uiState = LoginUIState(
                loginStatus = LoginStatus.AUTH_PENDING,
                serverAddress = "192.168.1.10:3000",
            ),
            onSignIn = { _, _ -> },
            onChangeServer = {}
        )
    }
}

@Composable
@PreviewLightDark
fun LoginContentRoutePreviewFailed() {
    OmnibusTheme {
        LoginContent(
            modifier = Modifier,
            uiState = LoginUIState(
                loginStatus = LoginStatus.AUTH_FAILED,
                serverAddress = "192.168.1.10:3000",
            ),
            onSignIn = { _, _ -> },
            onChangeServer = {}
        )
    }
}

@Composable
@PreviewLightDark
fun LoginContentRoutePreviewRegister() {
    OmnibusTheme {
        LoginContent(
            modifier = Modifier,
            uiState = LoginUIState(serverAddress = "192.168.1.10:3000"),
            initialMode = LoginMode.REGISTER,
            onSignIn = { _, _ -> },
            onChangeServer = {}
        )
    }
}
