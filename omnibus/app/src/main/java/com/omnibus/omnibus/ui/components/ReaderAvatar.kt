package com.omnibus.omnibus.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import com.omnibus.omnibus.data.household.HouseholdRepository
import com.omnibus.omnibus.data.household.di.HouseholdEntryPoint
import com.omnibus.omnibus.ui.theme.uiTextStyle
import dagger.hilt.android.EntryPointAccessors

/** The disc behind an initial. It is dark in both themes, as the design draws it. */
private val AvatarDisc = Color(0xFF282420)
private val AvatarInitial = Color(0xFFF7F5F1)

/** How many pixels of picture to request for each pixel the avatar is drawn at, so it stays sharp when scaled. */
private const val OVERSAMPLE = 2

/**
 * A reader's avatar: their picture when [hasAvatar] and it loads, otherwise the first letter of [name] on a dark
 * disc. The avatar is decorative to accessibility services, so callers name the reader beside it.
 *
 * @param userId The reader's user id, which addresses their picture.
 * @param hasAvatar Whether the server has a picture for [userId]. When false nothing is requested.
 * @param size The width and height of the disc.
 */
@Composable
fun ReaderAvatar(
    userId: Long,
    name: String,
    hasAvatar: Boolean,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    val edgePx = with(LocalDensity.current) { (size.toPx() * OVERSAMPLE).toInt() }
    val picture = rememberAvatarPicture(userId, hasAvatar, edgePx)

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(AvatarDisc)
            .clearAndSetSemantics {},
        contentAlignment = Alignment.Center,
    ) {
        val bitmap = picture
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Text(
                text = avatarInitial(name),
                style = uiTextStyle((size.value * INITIAL_SCALE).sp, FontWeight.SemiBold, AvatarInitial),
            )
        }
    }
}

/** The initial's size over the disc's size: 13 sp on the design's 32 dp avatar. */
private const val INITIAL_SCALE = 0.4f

/**
 * The picture for [userId], or null while it loads, when [hasAvatar] is false, when it fails to load, and where the
 * app has no repository (previews, hosts that are not the app).
 */
@Composable
private fun rememberAvatarPicture(userId: Long, hasAvatar: Boolean, edgePx: Int): ImageBitmap? {
    val context = LocalContext.current
    val inspecting = LocalInspectionMode.current
    val repository = remember(context, hasAvatar, inspecting) {
        if (!hasAvatar || inspecting) null else householdRepositoryOrNull(context)
    } ?: return null
    val picture by produceState<ImageBitmap?>(initialValue = null, repository, userId, edgePx) {
        value = repository.getAvatar(userId, edgePx)?.asImageBitmap()
    }
    return picture
}

private fun householdRepositoryOrNull(context: android.content.Context): HouseholdRepository? = try {
    EntryPointAccessors.fromApplication(context.applicationContext, HouseholdEntryPoint::class.java)
        .householdRepository()
} catch (_: IllegalStateException) {
    null
} catch (_: ClassCastException) {
    null
}

/**
 * The letter that stands for [name] on an avatar: its first character, upper-cased, as one whole code point so an
 * emoji or a supplementary letter isn't cut in half. A blank name gives "?".
 */
internal fun avatarInitial(name: String): String {
    val trimmed = name.trim()
    if (trimmed.isEmpty()) return "?"
    val end = Character.charCount(trimmed.codePointAt(0))
    return trimmed.substring(0, end).uppercase()
}
