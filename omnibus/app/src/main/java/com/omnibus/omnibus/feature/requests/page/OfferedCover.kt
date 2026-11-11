package com.omnibus.omnibus.feature.requests.page

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import com.omnibus.omnibus.data.requests.RequestsRepository
import com.omnibus.omnibus.data.requests.RequestsResult
import com.omnibus.omnibus.data.requests.di.RequestsEntryPoint
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The longest edge, in pixels, an offered cover is decoded to. The card shows it 64 dp wide. */
private const val OFFER_EDGE_PX = 512

/**
 * The cover a finished download brought, at 2:3, on a plain ground until it loads. It stays on that ground when it
 * can't load, and where the app has no repository (previews).
 */
@Composable
internal fun OfferedCover(requestId: Long, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val inspecting = LocalInspectionMode.current
    val repository = remember(context, inspecting) { if (inspecting) null else requestsRepositoryOrNull(context) }
    val picture by produceState<ImageBitmap?>(initialValue = null, repository, requestId) {
        val bytes = (repository?.offeredCover(requestId) as? RequestsResult.Success)?.value
        value = bytes?.let { withContext(Dispatchers.Default) { decodeDownsampled(it) } }
    }
    Box(
        modifier
            .aspectRatio(2f / 3f)
            .clip(RoundedCornerShape(4.dp))
            .background(OmnibusTheme.palette.bg2)
            .clearAndSetSemantics {},
    ) {
        picture?.let { Image(it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
    }
}

/** [bytes] decoded to an image no larger than about [OFFER_EDGE_PX] on its long edge, or null when they aren't one. */
private fun decodeDownsampled(bytes: ByteArray): ImageBitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= OFFER_EDGE_PX) sample *= 2
    val options = BitmapFactory.Options().apply { inSampleSize = sample }
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)?.asImageBitmap()
}

private fun requestsRepositoryOrNull(context: Context): RequestsRepository? = try {
    EntryPointAccessors.fromApplication(context.applicationContext, RequestsEntryPoint::class.java).requestsRepository()
} catch (_: IllegalStateException) {
    null
} catch (_: ClassCastException) {
    null
}
