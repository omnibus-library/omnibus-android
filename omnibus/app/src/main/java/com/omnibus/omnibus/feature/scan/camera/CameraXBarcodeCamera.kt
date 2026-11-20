package com.omnibus.omnibus.feature.scan.camera

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch

/** The camera adapter over [CameraSession]. Not unit- or device-tested here; the manual cases cover it. */
internal class CameraXBarcodeCamera(
    private val session: CameraSession,
    override val availability: CameraAvailability,
    private val request: () -> Unit,
    private val settings: () -> Unit,
) : BarcodeCamera {
    override val hasFlash: Boolean get() = session.hasFlash
    override fun requestPermission() = request()
    override fun openSettings() = settings()

    @Composable
    override fun Preview(frozen: Boolean, torchOn: Boolean, onFrame: (List<String>) -> Unit, modifier: Modifier) {
        val context = LocalContext.current
        val activity = LocalActivity.current
        val lifecycleOwner = LocalLifecycleOwner.current
        val scope = rememberCoroutineScope()
        val latestOnFrame by rememberUpdatedState(onFrame)
        val previewView = remember(context) {
            PreviewView(context).apply {
                implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                scaleType = PreviewView.ScaleType.FILL_CENTER
            }
        }
        // Open while shown and started. A configuration change (rotation) neither stops nor rebinds the camera.
        DisposableEffect(lifecycleOwner, session) {
            val observer = LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_START -> scope.launch { session.start(context) }
                    Lifecycle.Event.ON_STOP -> if (activity?.isChangingConfigurations != true) session.stop()
                    else -> Unit
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            session.preview.surfaceProvider = previewView.surfaceProvider
            session.analyze { latestOnFrame(it) }
            onDispose {
                lifecycleOwner.lifecycle.removeObserver(observer)
                session.stopAnalyzing()
                if (activity?.isChangingConfigurations != true) {
                    session.preview.surfaceProvider = null
                    session.stop()
                }
            }
        }
        LaunchedEffect(torchOn, session.camera) { session.setTorch(torchOn) }
        var still by remember { mutableStateOf<ImageBitmap?>(null) }
        LaunchedEffect(frozen) { still = if (frozen) previewView.bitmap?.asImageBitmap() else null }
        Box(modifier) {
            AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
            still?.let { Image(it, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
        }
    }
}

/** The production camera: permission, settings and the CameraX session for this screen. */
@Composable
fun rememberBarcodeCamera(): BarcodeCamera {
    val context = LocalContext.current
    val activity = LocalActivity.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val session: CameraSession = viewModel()
    var answer by rememberSaveable { mutableStateOf<CameraAvailability?>(null) }
    var granted by remember { mutableStateOf(hasCameraPermission(context)) }
    // Returning from Settings with Camera allowed starts the camera (R4).
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) granted = hasCameraPermission(context)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        granted = ok
        answer = when {
            ok -> CameraAvailability.Ready
            activity != null &&
                !ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.CAMERA) ->
                CameraAvailability.PermanentlyDenied
            else -> CameraAvailability.Denied
        }
    }
    val availability = when {
        !context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY) -> CameraAvailability.NoCamera
        granted -> CameraAvailability.Ready
        else -> answer ?: CameraAvailability.Unknown
    }
    return CameraXBarcodeCamera(
        session = session,
        availability = availability,
        request = { launcher.launch(Manifest.permission.CAMERA) },
        settings = {
            context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)),
            )
        },
    )
}

private fun hasCameraPermission(context: Context) =
    ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
