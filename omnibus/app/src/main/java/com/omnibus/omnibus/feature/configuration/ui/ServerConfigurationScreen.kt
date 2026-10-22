package com.omnibus.omnibus.feature.configuration.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.omnibus.omnibus.R
import com.omnibus.omnibus.feature.configuration.ServerConfigSubmissionStatus
import com.omnibus.omnibus.feature.configuration.ServerConfigUIState
import com.omnibus.omnibus.feature.configuration.ServerConfigurationViewModel
import com.omnibus.omnibus.ui.components.FilledButton
import com.omnibus.omnibus.ui.components.InlineError
import com.omnibus.omnibus.ui.components.OmnibusTextField
import com.omnibus.omnibus.ui.theme.MonoFontFamily
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import com.omnibus.omnibus.ui.theme.Spacing
import com.omnibus.omnibus.ui.theme.displayTextStyle
import com.omnibus.omnibus.ui.theme.uiTextStyle
import kotlinx.serialization.Serializable

@Serializable
data object ServerConfigurationScreenRoute : NavKey

abstract class ServerConfigurationScreenAction {
    data object SaveServerConfiguration : ServerConfigurationScreenAction()
}

@Composable
fun ServerConfigurationScreen(
    onAction: (ServerConfigurationScreenAction) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ServerConfigurationViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val currentOnAction by rememberUpdatedState(onAction)

    // The ViewModel can outlive this entry, so only act on ACCEPTED results submitted during this visit.
    var awaitingValidation by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(uiState.submissionStatus, awaitingValidation) {
        if (awaitingValidation && uiState.submissionStatus == ServerConfigSubmissionStatus.ACCEPTED) {
            awaitingValidation = false
            currentOnAction(ServerConfigurationScreenAction.SaveServerConfiguration)
        }
    }

    ServerConfigurationContent(
        modifier = modifier,
        uiState = uiState
    ) { serverUrl ->
        awaitingValidation = true
        viewModel.submitServerUrl(serverUrl)
    }
}

@Composable
private fun ServerConfigurationContent(
    modifier: Modifier,
    uiState: ServerConfigUIState,
    onConfigSubmitted: (serverUrl: String) -> Unit
) {
    val palette = OmnibusTheme.palette
    val haptics = LocalHapticFeedback.current
    val focusRequester = remember { FocusRequester() }

    var serverUrl by rememberSaveable { mutableStateOf("") }
    var submittedUrl by rememberSaveable { mutableStateOf("") }

    val isProbing = uiState.submissionStatus == ServerConfigSubmissionStatus.PENDING
    val canSubmit = serverUrl.isNotBlank() && uiState.submitButtonEnabled
    val errorMessage = when (uiState.submissionStatus) {
        ServerConfigSubmissionStatus.ERROR -> stringResource(R.string.server_config_error_invalid_address)
        ServerConfigSubmissionStatus.NOT_ACCEPTED -> stringResource(
            R.string.server_config_error_unreachable,
            submittedUrl
        )

        else -> null
    }

    val submit = {
        if (canSubmit) {
            submittedUrl = serverUrl.trim()
            onConfigSubmitted(serverUrl)
        }
    }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    LaunchedEffect(uiState.submissionStatus) {
        when (uiState.submissionStatus) {
            ServerConfigSubmissionStatus.ACCEPTED -> haptics.performHapticFeedback(
                HapticFeedbackType.Confirm
            )

            ServerConfigSubmissionStatus.ERROR,
            ServerConfigSubmissionStatus.NOT_ACCEPTED -> haptics.performHapticFeedback(
                HapticFeedbackType.Reject
            )

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
            Header()

            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Text(
                    text = stringResource(R.string.server_config_address_label),
                    style = uiTextStyle(13, FontWeight.Medium, palette.ink2),
                )

                OmnibusTextField(
                    value = serverUrl,
                    onValueChange = { serverUrl = it },
                    placeholder = stringResource(R.string.server_config_address_placeholder),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                        capitalization = KeyboardCapitalization.None,
                        autoCorrectEnabled = false,
                        imeAction = ImeAction.Go,
                    ),
                    keyboardActions = KeyboardActions(onGo = { submit() }),
                    modifier = Modifier
                        .focusRequester(focusRequester)
                        .testTag("server-address"),
                )

                val hintParts =
                    stringResource(R.string.server_config_address_hint).split("%1\$s", limit = 2)
                val httpsScheme = stringResource(R.string.server_config_https_scheme)
                Text(
                    text = buildAnnotatedString {
                        append(hintParts[0])
                        withStyle(SpanStyle(fontFamily = MonoFontFamily)) { append(httpsScheme) }
                        append(hintParts.getOrElse(1) { "" })
                    },
                    style = uiTextStyle(12, FontWeight.Normal, palette.ink3),
                )
            }

            InlineError(message = errorMessage)

            FilledButton(
                text = stringResource(R.string.server_config_connect),
                onClick = submit,
                enabled = canSubmit,
                isLoading = isProbing,
                loadingDescription = stringResource(R.string.server_config_connecting),
            )

            Spacer(modifier = Modifier.height(Spacing.xl))
        }
    }
}

@Composable
private fun Header() {
    val palette = OmnibusTheme.palette

    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Image(
            painter = painterResource(R.drawable.omnibus_stoat),
            contentDescription = null,
            modifier = Modifier
                .padding(bottom = Spacing.xs)
                .size(36.dp),
        )
        Text(
            text = stringResource(R.string.server_config_title),
            style = displayTextStyle(34, palette.ink0),
        )
        Text(
            text = stringResource(R.string.server_config_subtitle),
            style = uiTextStyle(15, FontWeight.Normal, palette.ink2),
        )
    }
}

@Composable
@PreviewLightDark
fun ServerConfigurationContentRoutePreviewBasic() {
    OmnibusTheme {
        ServerConfigurationContent(
            modifier = Modifier,
            uiState = ServerConfigUIState()
        ) {
            //todo
        }
    }
}

@Composable
@PreviewLightDark
fun ServerConfigurationContentRoutePreviewDisabled() {
    OmnibusTheme {
        ServerConfigurationContent(
            modifier = Modifier,
            uiState = ServerConfigUIState(submissionStatus = ServerConfigSubmissionStatus.PENDING)
        ) {
            //todo
        }
    }
}

@Composable
@PreviewLightDark
fun ServerConfigurationContentRoutePreviewNotFound() {
    OmnibusTheme {
        ServerConfigurationContent(
            modifier = Modifier,
            uiState = ServerConfigUIState(submissionStatus = ServerConfigSubmissionStatus.NOT_ACCEPTED)
        ) {
            //todo
        }
    }
}
