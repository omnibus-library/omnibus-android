package com.omnibus.omnibus.feature.scan

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.testTag
import com.omnibus.omnibus.R
import com.omnibus.omnibus.feature.scan.camera.BarcodeCamera
import com.omnibus.omnibus.feature.scan.camera.CameraAvailability
import com.omnibus.omnibus.ui.components.FilledButton
import com.omnibus.omnibus.ui.components.TextLinkButton
import com.omnibus.omnibus.ui.theme.Radius
import com.omnibus.omnibus.ui.theme.Spacing
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import com.omnibus.omnibus.ui.theme.uiTextStyle

/** The stage title, announced when the stage arrives. */
@Composable
internal fun ScanTitleText(text: String, modifier: Modifier = Modifier, align: TextAlign = TextAlign.Unspecified) {
    Text(
        text = text,
        style = uiTextStyle(16, FontWeight.SemiBold, OmnibusTheme.palette.ink0)
            .copy(textAlign = align),
        modifier = modifier
            .testTag(ScanTestTags.TITLE)
            .semantics {
                heading()
                liveRegion = LiveRegionMode.Polite
            },
    )
}

/** The 48 dp close button every stage has, which leaves the scanner. */
@Composable
internal fun ScanCloseButton(onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(48.dp).testTag(ScanTestTags.CLOSE)) {
        Icon(
            painter = painterResource(R.drawable.ic_reader_close),
            contentDescription = stringResource(R.string.scan_close),
            tint = OmnibusTheme.palette.ink0,
        )
    }
}

/** The torch switch, shown only on a device with a flash. */
@Composable
internal fun ScanTorchButton(torchOn: Boolean, onToggle: () -> Unit) {
    val palette = OmnibusTheme.palette
    val label = stringResource(R.string.scan_torch)
    val onLabel = stringResource(R.string.scan_torch_on)
    val offLabel = stringResource(R.string.scan_torch_off)
    Box(
        Modifier
            .size(48.dp)
            .testTag(ScanTestTags.TORCH)
            .toggleable(value = torchOn, role = Role.Switch, onValueChange = { onToggle() })
            .semantics {
                contentDescription = label
                stateDescription = if (torchOn) onLabel else offLabel
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(if (torchOn) palette.accent else palette.bg2),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_scan_torch),
                contentDescription = null,
                tint = if (torchOn) palette.accentInk else palette.ink0,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/** The camera stage: a live preview with a reticle, a hint or notice, and the banner or lookup overlay. */
@Composable
internal fun ScanViewfinderStage(
    resolvingIsbn: String?,
    lookupFailed: LookupFailure?,
    rejected: RejectedNotice?,
    torchOn: Boolean,
    camera: BarcodeCamera,
    actions: ScanActions,
    wide: Boolean,
) {
    val palette = OmnibusTheme.palette
    val description = stringResource(R.string.scan_viewfinder_description)
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ScanCloseButton(actions.onClose)
            ScanTitleText(stringResource(R.string.scan_title), Modifier.weight(1f))
            if (camera.hasFlash) ScanTorchButton(torchOn, actions.onTorchToggled)
        }
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .then(if (wide) Modifier.widthIn(max = SCAN_COLUMN_MAX_WIDTH) else Modifier),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(Color.Black)
                    .testTag(ScanTestTags.VIEWFINDER)
                    .semantics { contentDescription = description },
            ) {
                camera.Preview(
                    frozen = resolvingIsbn != null,
                    torchOn = torchOn,
                    onFrame = actions.onBarcodes,
                    modifier = Modifier.fillMaxSize(),
                )
                if (resolvingIsbn != null) {
                    Box(Modifier.fillMaxSize().background(palette.bg0.copy(alpha = 0.5f)))
                }
                Reticle(
                    color = when {
                        rejected != null -> palette.warn
                        resolvingIsbn != null -> palette.ok
                        else -> palette.accent
                    },
                    modifier = Modifier.align(Alignment.Center).fillMaxWidth(0.72f).aspectRatio(1.6f),
                )
                if (lookupFailed != null) {
                    LookupBanner(onTryAgain = actions.onTryAgain, modifier = Modifier.align(Alignment.TopCenter))
                } else {
                    Box(Modifier.align(Alignment.TopCenter).padding(Spacing.md)) {
                        if (rejected != null) {
                            key(rejected.value) {
                                CameraPill(
                                    text = stringResource(R.string.scan_not_isbn),
                                    color = palette.warn,
                                    modifier = Modifier
                                        .testTag(ScanTestTags.NOTICE)
                                        .then(
                                            if (rejected.announce) {
                                                Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                                            } else {
                                                Modifier
                                            },
                                        ),
                                )
                            }
                        } else {
                            CameraPill(text = stringResource(R.string.scan_hint), color = Color.White)
                        }
                    }
                }
                if (resolvingIsbn != null) {
                    Row(
                        Modifier
                            .align(Alignment.BottomCenter)
                            .padding(Spacing.md)
                            .clip(RoundedCornerShape(Radius.md))
                            .background(Color.Black.copy(alpha = 0.6f))
                            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = palette.accent,
                            strokeWidth = 2.dp,
                        )
                        Text(
                            text = stringResource(R.string.scan_looking_up, resolvingIsbn),
                            style = uiTextStyle(14, FontWeight.Normal, Color.White),
                        )
                    }
                }
            }
            if (resolvingIsbn == null) {
                FilledButton(
                    text = stringResource(R.string.scan_enter_isbn),
                    onClick = actions.onEnterIsbn,
                    prominent = false,
                    modifier = Modifier.padding(horizontal = Spacing.screen, vertical = Spacing.md),
                )
            }
        }
    }
}

/** The camera stage's failed-lookup banner, with a 48 dp Try again. */
@Composable
private fun LookupBanner(onTryAgain: () -> Unit, modifier: Modifier) {
    val palette = OmnibusTheme.palette
    Row(
        modifier
            .fillMaxWidth()
            .padding(Spacing.md)
            .testTag(ScanTestTags.BANNER)
            .clip(RoundedCornerShape(Radius.md))
            .background(Color.Black.copy(alpha = 0.7f))
            .border(0.5.dp, palette.bad, RoundedCornerShape(Radius.md))
            .padding(start = Spacing.md, end = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.scan_lookup_failed),
            style = uiTextStyle(14, FontWeight.Medium, palette.bad),
            modifier = Modifier.weight(1f),
        )
        TextLinkButton(
            text = stringResource(R.string.scan_try_again),
            color = palette.accent,
            size = 14,
            onClick = onTryAgain,
            modifier = Modifier.heightIn(min = 48.dp),
        )
    }
}

/** A pill of camera text. The camera area is dark in both themes, so the text is always light. */
@Composable
private fun CameraPill(text: String, color: Color, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = uiTextStyle(13, FontWeight.Medium, color),
        modifier = modifier
            .clip(RoundedCornerShape(Radius.md))
            .background(Color.Black.copy(alpha = 0.6f))
            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
    )
}

/** The square-cornered aim marker over the preview. It hides from TalkBack, as it is decoration. */
@Composable
private fun Reticle(color: Color, modifier: Modifier) {
    Canvas(modifier.clearAndSetSemantics {}) {
        drawReticle(color)
    }
}

private fun DrawScope.drawReticle(color: Color) {
    val stroke = 3.dp.toPx()
    val arm = size.minDimension * 0.22f
    val w = size.width
    val h = size.height
    val corners = listOf(
        Offset(0f, 0f) to (Offset(arm, 0f) to Offset(0f, arm)),
        Offset(w, 0f) to (Offset(w - arm, 0f) to Offset(w, arm)),
        Offset(0f, h) to (Offset(arm, h) to Offset(0f, h - arm)),
        Offset(w, h) to (Offset(w - arm, h) to Offset(w, h - arm)),
    )
    for ((corner, arms) in corners) {
        drawLine(color, corner, arms.first, strokeWidth = stroke)
        drawLine(color, corner, arms.second, strokeWidth = stroke)
    }
    drawLine(color.copy(alpha = 0.6f), Offset(w * 0.1f, h / 2f), Offset(w * 0.9f, h / 2f), strokeWidth = 1.5.dp.toPx())
}

/** The camera-unavailable stage: why there is no camera, with Open settings when it was permanently denied. */
@Composable
internal fun ScanCameraUnavailableStage(camera: BarcodeCamera, actions: ScanActions) {
    val palette = OmnibusTheme.palette
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth().padding(horizontal = Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
            ScanCloseButton(actions.onClose)
        }
        Column(
            Modifier.weight(1f).fillMaxWidth().padding(horizontal = Spacing.screen),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                Modifier.size(72.dp).clip(CircleShape).background(palette.bg2),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_add_books_scan),
                    contentDescription = null,
                    tint = palette.accent,
                    modifier = Modifier.size(32.dp),
                )
            }
            Spacer(Modifier.height(Spacing.md))
            ScanTitleText(stringResource(R.string.scan_camera_unavailable), align = TextAlign.Center)
            Spacer(Modifier.height(Spacing.sm))
            Text(
                text = stringResource(R.string.scan_camera_unavailable_body),
                style = uiTextStyle(15, FontWeight.Normal, palette.ink2),
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(Spacing.lg))
            if (camera.availability == CameraAvailability.PermanentlyDenied) {
                FilledButton(
                    text = stringResource(R.string.scan_open_settings),
                    onClick = camera::openSettings,
                    prominent = false,
                )
                Spacer(Modifier.height(Spacing.sm))
            }
            FilledButton(text = stringResource(R.string.scan_enter_isbn), onClick = actions.onEnterIsbn)
        }
    }
}
