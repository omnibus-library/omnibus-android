package com.omnibus.omnibus.feature.libraryconfiguration

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.omnibus.omnibus.ui.theme.OmnibusTheme

@Composable
fun LibraryConfigurationScreen(viewModel: LibraryConfigurationScreenViewModel = hiltViewModel()) {

    val uiState = viewModel.uiState.collectAsState()

    Box(modifier = Modifier.fillMaxSize()) {
        LibraryConfigurationContent(uiState.value) { serverUrl ->
            viewModel.submitServerConfig(
                serverUrl
            )
        }
    }
}

@Composable
private fun LibraryConfigurationContent(
    uiState: LibraryConfigurationUIState,
    onValidatePressed: (String) -> Unit
) {

    var serverAddressField by remember { mutableStateOf("") }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Column {
            TextField(
                value = serverAddressField,
                isError = uiState is LibraryConfigurationUIState.Error,
                onValueChange = { newVal -> serverAddressField = newVal },
                label = {
                    Text(text = "Server Address")
                },
                placeholder = {
                    Text(text = "https://192.168.1.1:8000")
                },
                supportingText = {
                    AnimatedVisibility(uiState is LibraryConfigurationUIState.Error) {
                        Text(
                            text = (uiState as LibraryConfigurationUIState.Error).message,
                            color = Color.Red
                        )
                    }
                }
            )
            Button(
                onClick = {
                    onValidatePressed(serverAddressField)
                }
            ) {
                Text(text = "Validate")
            }
        }
    }
}

@Composable
@PreviewLightDark
private fun LibraryConfigPreview() {
    OmnibusTheme {
        Surface {
            LibraryConfigurationContent(
                uiState = LibraryConfigurationUIState.Rest,
                onValidatePressed = { url -> })
        }
    }
}