package com.omnibus.omnibus.feature.shell.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.omnibus.omnibus.feature.shell.chrome.ChromeTokens
import com.omnibus.omnibus.feature.shell.chrome.FabSpec
import com.omnibus.omnibus.ui.components.ChromeShadows
import com.omnibus.omnibus.ui.components.chromeShadow
import com.omnibus.omnibus.ui.components.isOmnibusDark
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import com.omnibus.omnibus.ui.theme.Radius

/**
 * The 56 dp floating action for the selected tab root's [spec] (P-FAB-2, I-STATE-2). A light key tick, then
 * [FabSpec.onClick] (I-ARCH-5).
 *
 * @param enabled False from the moment the FAB starts to leave, so its exit can't be tapped (I-MOT-8).
 */
@Composable
internal fun ShellFab(spec: FabSpec, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val palette = OmnibusTheme.palette
    val haptics = LocalHapticFeedback.current
    val label = stringResource(spec.label)
    val shape = RoundedCornerShape(Radius.chrome)
    Box(
        modifier = modifier
            .size(ChromeTokens.fabSize.dp)
            .chromeShadow(shape, ChromeShadows.fab(isOmnibusDark))
            .clip(shape)
            .background(palette.accent)
            .clickable(enabled = enabled, role = Role.Button) {
                // VirtualKey is the light key-tap tick, as the masthead button used (AND-HAP-1).
                haptics.performHapticFeedback(HapticFeedbackType.VirtualKey)
                spec.onClick()
            }
            // The label sits on the clickable node, so TalkBack reads one button.
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(spec.icon),
            contentDescription = null,
            tint = palette.accentInk,
            modifier = Modifier.size(24.dp),
        )
    }
}
