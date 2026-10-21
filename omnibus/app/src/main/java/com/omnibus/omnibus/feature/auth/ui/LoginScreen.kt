package com.omnibus.omnibus.feature.auth.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.omnibus.omnibus.feature.auth.LoginStatus
import com.omnibus.omnibus.feature.auth.LoginUIState
import com.omnibus.omnibus.feature.auth.LoginViewModel

@Composable
fun LoginScreenRoute(
    modifier: Modifier = Modifier,
    viewModel: LoginViewModel = hiltViewModel()
) {

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LoginScreen(modifier = modifier, uiState = uiState) { username, password ->
        viewModel.attemptLogin(username, password)
    }

}

@Composable
private fun LoginScreen(
    modifier: Modifier,
    uiState: LoginUIState,
    onSignIn: (username: String, password: String) -> Unit
) {
    var usernameField by rememberSaveable { mutableStateOf("") }
    var passwordField by rememberSaveable { mutableStateOf("") }

    Box(
        modifier = Modifier.fillMaxSize().then(modifier),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.Start
        ) {
            OutlinedTextField(
                label = {
                    Text(text = "Username/Email")
                },
                value = usernameField,
                onValueChange = { newValue: String ->
                    usernameField = newValue
                }
            )
            OutlinedTextField(
                label = {
                    Text(text = "Password")
                },
                visualTransformation = PasswordVisualTransformation(),
                value = passwordField,
                onValueChange = { newValue: String ->
                    passwordField = newValue
                }
            )
            Button(
                onClick = { onSignIn(usernameField, passwordField) }
            ) {
                Text(text = "Sign In")
            }
            AnimatedVisibility(uiState.loginStatus == LoginStatus.LOGGED_IN) {
                Text(
                    text = "Successfully signed in!",
                    color = Color.Green
                )
            }
            AnimatedVisibility(uiState.loginStatus == LoginStatus.AUTH_FAILED) {
                Text(
                    text = "Sign in failed, please check your credentials and try again.",
                    color = Color.Red
                )
            }
        }
    }

}

@Composable
@PreviewLightDark
fun LoginScreenRoutePreviewBasic() {
    LoginScreen(
        modifier = Modifier,
        uiState = LoginUIState()
    ) { username, password ->
        //todo
    }
}