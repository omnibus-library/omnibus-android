package com.omnibus.omnibus.feature.configuration.ui

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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavKey
import com.omnibus.omnibus.feature.configuration.ServerConfigSubmissionStatus
import com.omnibus.omnibus.feature.configuration.ServerConfigUIState
import com.omnibus.omnibus.feature.configuration.ServerConfigurationViewModel
import kotlinx.serialization.Serializable

@Serializable
data object ServerConfigurationScreenRoute: NavKey

@Composable
fun ServerConfigurationScreen(
    onServerConfigurationSaved: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ServerConfigurationViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val currentOnServerConfigurationSaved by rememberUpdatedState(onServerConfigurationSaved)

    LaunchedEffect(uiState.submissionStatus) {
        if (uiState.submissionStatus == ServerConfigSubmissionStatus.ACCEPTED) {
            currentOnServerConfigurationSaved()
        }
    }

    ServerConfigurationContent(
        modifier = modifier,
        uiState = uiState
    ) { serverUrl ->
        viewModel.submitServerUrl(serverUrl)
    }
}

@Composable
private fun ServerConfigurationContent(
    modifier: Modifier,
    uiState: ServerConfigUIState,
    onConfigSubmitted: (serverUrl: String) -> Unit
) {
    var serverUrl by remember { mutableStateOf("") }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .then(modifier),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.Start
        ) {
            OutlinedTextField(
                label = {
                    Text(text = "Server Url")
                },
                value = serverUrl,
                onValueChange = { newValue: String ->
                    serverUrl = newValue
                }
            )
            Button(
                enabled = uiState.submitButtonEnabled,
                onClick = { onConfigSubmitted(serverUrl) }
            ) {
                Text(text = "Submit")
            }
        }
    }
}

@Composable
@PreviewLightDark
fun ServerConfigurationContentRoutePreviewBasic() {
    ServerConfigurationContent(
        modifier = Modifier,
        uiState = ServerConfigUIState()
    ) {
        //todo
    }
}

@Composable
@PreviewLightDark
fun ServerConfigurationContentRoutePreviewDisabled() {
    ServerConfigurationContent(
        modifier = Modifier,
        uiState = ServerConfigUIState(submissionStatus = ServerConfigSubmissionStatus.PENDING)
    ) {
        //todo
    }
}