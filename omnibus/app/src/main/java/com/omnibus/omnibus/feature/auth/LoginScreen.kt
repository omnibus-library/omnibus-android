package com.omnibus.omnibus.feature.auth

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.omnibus.omnibus.ui.theme.OmnibusTheme

@Composable
fun LoginScreen(viewModel: LoginScreenViewModel = hiltViewModel()) {

    val uiState by viewModel.uiState.collectAsState()

    LoginScreenContent(
        uiState = uiState,
        onUsernameChanged = viewModel::updateUsername,
        onPasswordChanged = viewModel::updatePassword,
        onLoginClicked = viewModel::login,
    )
}

@Composable
private fun LoginScreenContent(
    uiState: LoginScreenUIState,
    onUsernameChanged: (String) -> Unit,
    onPasswordChanged: (String) -> Unit,
    onLoginClicked: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.padding(horizontal = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.Start
        ) {
            UsernameField(
                value = uiState.usernameField,
                onValueChanged = onUsernameChanged
            )
            PasswordField(
                value = uiState.passwordField,
                onValueChanged = onPasswordChanged
            )
            Button(
                onClick = onLoginClicked
            ) {
                Text(text = "Login")
            }
        }
    }
}

@Composable
private fun UsernameField(
    value: String,
    onValueChanged: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    TextField(
        modifier = Modifier
            .fillMaxWidth()
            .then(modifier),
        value = value,
        onValueChange = onValueChanged
    )
}

@Composable
private fun PasswordField(
    value: String,
    onValueChanged: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    TextField(
        modifier = Modifier
            .fillMaxWidth()
            .then(modifier),
        value = value,
        onValueChange = onValueChanged,
        visualTransformation = PasswordVisualTransformation()
    )
}

@Composable
@PreviewLightDark
private fun LoginScreenPreview() {
    OmnibusTheme {
        Surface {
            LoginScreenContent(
                uiState = LoginScreenUIState(
                    usernameField = "username",
                    passwordField = "password"
                ),
                onUsernameChanged = {},
                onPasswordChanged = {},
                onLoginClicked = {},
            )
        }
    }
}