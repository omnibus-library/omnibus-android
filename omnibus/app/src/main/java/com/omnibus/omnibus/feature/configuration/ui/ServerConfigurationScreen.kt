package com.omnibus.omnibus.feature.configuration.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavKey
import com.omnibus.omnibus.R
import com.omnibus.omnibus.feature.configuration.ServerConfigSubmissionStatus
import com.omnibus.omnibus.feature.configuration.ServerConfigUIState
import com.omnibus.omnibus.feature.configuration.ServerConfigurationViewModel
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import com.omnibus.omnibus.ui.theme.Radius
import com.omnibus.omnibus.ui.theme.Spacing
import com.omnibus.omnibus.ui.theme.displayTextStyle
import com.omnibus.omnibus.ui.theme.uiTextStyle
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
    val palette = OmnibusTheme.palette
    val haptics = LocalHapticFeedback.current
    val focusRequester = remember { FocusRequester() }

    var serverUrl by rememberSaveable { mutableStateOf("") }
    var submittedUrl by rememberSaveable { mutableStateOf("") }

    val isProbing = uiState.submissionStatus == ServerConfigSubmissionStatus.PENDING
    val canSubmit = serverUrl.isNotBlank() && uiState.submitButtonEnabled
    val errorMessage = when (uiState.submissionStatus) {
        ServerConfigSubmissionStatus.ERROR -> stringResource(R.string.server_config_error_invalid_address)
        ServerConfigSubmissionStatus.NOT_ACCEPTED -> stringResource(R.string.server_config_error_unreachable, submittedUrl)
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
            ServerConfigSubmissionStatus.ACCEPTED -> haptics.performHapticFeedback(HapticFeedbackType.Confirm)
            ServerConfigSubmissionStatus.ERROR,
            ServerConfigSubmissionStatus.NOT_ACCEPTED -> haptics.performHapticFeedback(HapticFeedbackType.Reject)

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

                ServerAddressField(
                    value = serverUrl,
                    onValueChange = { serverUrl = it },
                    onSubmit = submit,
                    modifier = Modifier.focusRequester(focusRequester),
                )

                val hintParts = stringResource(R.string.server_config_address_hint).split("%1\$s", limit = 2)
                val httpsScheme = stringResource(R.string.server_config_https_scheme)
                Text(
                    text = buildAnnotatedString {
                        append(hintParts[0])
                        withStyle(SpanStyle(fontFamily = FontFamily.Monospace)) { append(httpsScheme) }
                        append(hintParts.getOrElse(1) { "" })
                    },
                    style = uiTextStyle(12, FontWeight.Normal, palette.ink3),
                )
            }

            AnimatedVisibility(
                visible = errorMessage != null,
                enter = fadeIn(SettleSpring) + slideInVertically(SettleSpringOffset) { -it },
                exit = fadeOut(SettleSpring) + slideOutVertically(SettleSpringOffset) { -it },
            ) {
                // Keeps the last message on screen while the exit animation runs.
                var lastMessage by remember { mutableStateOf("") }
                if (errorMessage != null) lastMessage = errorMessage

                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = WarningTriangle,
                        contentDescription = null,
                        tint = palette.bad,
                        modifier = Modifier.size(15.dp),
                    )
                    Text(
                        text = lastMessage,
                        style = uiTextStyle(13, FontWeight.Normal, palette.bad),
                    )
                }
            }

            FilledButton(
                enabled = canSubmit,
                onClick = submit,
            ) {
                if (isProbing) {
                    val connectingDescription = stringResource(R.string.server_config_connecting)
                    CircularProgressIndicator(
                        color = palette.accentInk,
                        strokeWidth = 2.dp,
                        modifier = Modifier
                            .size(20.dp)
                            .semantics { contentDescription = connectingDescription },
                    )
                } else {
                    Text(
                        text = stringResource(R.string.server_config_connect),
                        style = uiTextStyle(16, FontWeight.SemiBold, palette.accentInk),
                    )
                }
            }

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
private fun ServerAddressField(
    value: String,
    onValueChange: (String) -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = OmnibusTheme.palette
    val shape = RoundedCornerShape(Radius.md)

    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = uiTextStyle(16, FontWeight.Normal, palette.ink0),
        cursorBrush = SolidColor(palette.accent),
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Uri,
            capitalization = KeyboardCapitalization.None,
            autoCorrectEnabled = false,
            imeAction = ImeAction.Go,
        ),
        keyboardActions = KeyboardActions(onGo = { onSubmit() }),
        modifier = modifier
            .fillMaxWidth()
            .testTag("server-address"),
        decorationBox = { innerTextField ->
            Box(
                modifier = Modifier
                    .background(palette.bg1, shape)
                    .border(0.5.dp, palette.line, shape)
                    .padding(horizontal = 14.dp, vertical = 13.dp),
            ) {
                if (value.isEmpty()) {
                    Text(
                        text = stringResource(R.string.server_config_address_placeholder),
                        style = uiTextStyle(16, FontWeight.Normal, palette.ink3),
                    )
                }
                innerTextField()
            }
        },
    )
}

@Composable
private fun FilledButton(
    enabled: Boolean,
    onClick: () -> Unit,
    content: @Composable RowScope.() -> Unit,
) {
    val palette = OmnibusTheme.palette
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(if (isPressed) 0.97f else 1f, LiftSpring, label = "pressScale")
    val pressAlpha by animateFloatAsState(if (isPressed) 0.9f else 1f, LiftSpring, label = "pressAlpha")

    Button(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(Radius.md),
        // Disabled matches enabled so the button doesn't dim while the field is empty or a probe is running.
        colors = ButtonDefaults.buttonColors(
            containerColor = palette.accent,
            contentColor = palette.accentInk,
            disabledContainerColor = palette.accent,
            disabledContentColor = palette.accentInk,
        ),
        contentPadding = PaddingValues(horizontal = Spacing.xl),
        interactionSource = interactionSource,
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .scale(scale)
            .alpha(pressAlpha),
        content = content,
    )
}

private val LiftSpring = spring<Float>(dampingRatio = 0.74f, stiffness = 504f)
private val SettleSpring = spring<Float>(dampingRatio = 0.82f, stiffness = 204f)
private val SettleSpringOffset = spring(
    dampingRatio = 0.82f,
    stiffness = 204f,
    visibilityThreshold = IntOffset(1, 1),
)

private val WarningTriangle = ImageVector.Builder(
    name = "WarningTriangle",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f,
).apply {
    path(fill = SolidColor(Color.Black)) {
        moveTo(1f, 21f)
        horizontalLineToRelative(22f)
        lineTo(12f, 2f)
        close()
        moveTo(13f, 18f)
        horizontalLineToRelative(-2f)
        verticalLineToRelative(-2f)
        horizontalLineToRelative(2f)
        close()
        moveTo(13f, 14f)
        horizontalLineToRelative(-2f)
        verticalLineToRelative(-4f)
        horizontalLineToRelative(2f)
        close()
    }
}.build()

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
