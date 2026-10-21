package com.omnibus.omnibus.feature.auth

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.omnibus.omnibus.ui.theme.OmnibusTheme

@Composable
fun LoginScreen(viewModel: LoginScreenViewModel = hiltViewModel()) {

    val uiState by viewModel.uiState.collectAsState()

    LoginScreenContent(
        isLoading = uiState == LoginState.Loading,
        onLoginClicked = { username, password -> viewModel.login(username, password) },
    )
}

@Composable
private fun LoginScreenContent(
    isLoading: Boolean,
    onLoginClicked: (String, String) -> Unit,
    modifier: Modifier = Modifier,
    error: String? = null
) {
    var usernameField by rememberSaveable { mutableStateOf("") }
    var passwordField by rememberSaveable { mutableStateOf("") }

    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.padding(horizontal = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.Start
        ) {
            TextField(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(modifier),
                value = usernameField,
                placeholder = {
                    Text(text = "username/email")
                },
                isError = error != null,
                onValueChange = {
                    usernameField = it
                }
            )
            TextField(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(modifier),
                value = passwordField,
                visualTransformation = PasswordVisualTransformation(),
                isError = error != null,
                placeholder = {
                    Text(text = "password")
                },
                supportingText = {
                    AnimatedVisibility(visible = error != null) {
                        Text(text = error ?: "", color = Color.Red)
                    }
                },
                onValueChange = {
                    passwordField = it
                }
            )
            Button(
                onClick = { onLoginClicked(usernameField, passwordField) },
                enabled = !isLoading
            ) {
                val text = if (isLoading) "Signing in..." else "Sign in"
                Text(text = text)
            }
        }
    }
}

@Composable
@PreviewLightDark
private fun LoginScreenPreview() {
    OmnibusTheme {
        Surface {
            LoginScreenContent(
                isLoading = true,
                error = null,
                onLoginClicked = { username, password -> },
            )
        }
    }
}

@Composable
@PreviewLightDark
private fun LoginScreenErrorPreview() {
    OmnibusTheme {
        Surface {
            LoginScreenContent(
                isLoading = false,
                error = "An error occurred while logging in; please try again!",
                onLoginClicked = { username, password ->},
            )
        }
    }
}