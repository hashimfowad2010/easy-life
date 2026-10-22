package com.easylife.app.feature.limits

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.lifecycle.awaitInstance
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Camera
import androidx.compose.material.icons.rounded.FlashlightOff
import androidx.compose.material.icons.rounded.FlashlightOn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavHostController
import com.easylife.app.core.designsystem.BigButton
import com.easylife.app.core.designsystem.BigOutlinedButton
import com.easylife.app.core.designsystem.EasyTopBar
import com.easylife.app.core.designsystem.EmptyState
import com.easylife.app.core.i18n.SCommon
import com.easylife.app.core.i18n.SScan
import com.easylife.app.core.i18n.t
import com.easylife.app.core.scan.ScanDraftStore
import com.easylife.app.navigation.AddReadingRoute
import com.easylife.app.navigation.MeterCameraRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Framing box as a fraction of the preview: wide and short, like a meter display. */
private const val BOX_W = 0.86f
private const val BOX_H = 0.30f

@HiltViewModel
class MeterCameraViewModel @Inject constructor(private val drafts: ScanDraftStore) : ViewModel() {
    /** Crop + save off the main thread, then call back on the main thread (navigation). */
    fun capture(file: File, onReady: (String) -> Unit) = viewModelScope.launch { onReady(draftFrom(file)) }

    suspend fun draftFrom(file: File): String {
        val cropped = withContext(Dispatchers.Default) { cropToBox(file) }
        return drafts.createFromImages(listOf(android.net.Uri.fromFile(cropped))).id.also {
            file.delete()
            cropped.delete()
        }
    }

    /** Rotate per EXIF and keep the band around the framing box (with a margin), so serial numbers are mostly excluded. */
    private fun cropToBox(file: File): File {
        val raw = BitmapFactory.decodeFile(file.absolutePath) ?: return file
        val rotation = when (ExifInterface(file.absolutePath).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
        val upright = if (rotation == 0f) raw else Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, Matrix().apply { postRotate(rotation) }, true)
        val w = upright.width
        val h = upright.height
        val cw = (w * minOf(1f, BOX_W + 0.1f)).toInt()
        val ch = (h * minOf(1f, BOX_H + 0.2f)).toInt()
        val cropped = Bitmap.createBitmap(upright, (w - cw) / 2, (h - ch) / 2, cw, ch)
        val out = File(file.parentFile, "crop_${file.name}")
        out.outputStream().use { cropped.compress(Bitmap.CompressFormat.JPEG, 92, it) }
        return out
    }
}

@Composable
fun MeterCameraScreen(meterId: Long, nav: NavHostController, vm: MeterCameraViewModel = hiltViewModel()) {
    val context = LocalContext.current
    var granted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    var denied by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        granted = ok
        denied = !ok
    }
    val typeInstead = { nav.navigate(AddReadingRoute(meterId)) { popUpTo<MeterCameraRoute> { inclusive = true } } }

    if (!granted) {
        Scaffold(topBar = { EasyTopBar(SScan.meter.t(), onBack = { nav.popBackStack() }) }) { padding ->
            Column(Modifier.padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                EmptyState(Icons.Rounded.Camera, SScan.meter.t(), SScan.cameraPermission.t())
                if (denied) Text(SCommon.permissionDenied.t(), style = MaterialTheme.typography.bodyMedium)
                BigButton(SScan.allowCamera.t(), onClick = { launcher.launch(Manifest.permission.CAMERA) })
                BigOutlinedButton(SScan.typeInstead.t(), onClick = typeInstead)
            }
        }
        return
    }
    CameraContent(
        onBack = { nav.popBackStack() },
        onTypeInstead = typeInstead,
        onCaptured = { file ->
            vm.capture(file) { id ->
                nav.navigate(AddReadingRoute(meterId, id)) { popUpTo<MeterCameraRoute> { inclusive = true } }
            }
        },
    )
}

@Composable
private fun CameraContent(onBack: () -> Unit, onTypeInstead: () -> Unit, onCaptured: (File) -> Unit) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val previewView = remember { PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER } }
    val capture = remember { ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY).build() }
    var camera by remember { mutableStateOf<Camera?>(null) }
    var torch by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val provider = ProcessCameraProvider.awaitInstance(context)
        val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
        provider.unbindAll()
        camera = runCatching { provider.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, preview, capture) }.getOrNull()
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
        Canvas(Modifier.fillMaxSize()) {
            val bw = size.width * BOX_W
            val bh = size.height * BOX_H
            val left = (size.width - bw) / 2
            val top = (size.height - bh) / 2
            // Dim outside the box.
            val dim = Color.Black.copy(alpha = 0.45f)
            drawRect(dim, Offset.Zero, Size(size.width, top))
            drawRect(dim, Offset(0f, top + bh), Size(size.width, size.height - top - bh))
            drawRect(dim, Offset(0f, top), Size(left, bh))
            drawRect(dim, Offset(left + bw, top), Size(size.width - left - bw, bh))
            drawRoundRect(Color.White, Offset(left, top), Size(bw, bh), CornerRadius(24f, 24f), style = Stroke(width = 6f))
        }
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, SCommon.back.t(), tint = Color.White) }
            Text(SScan.fitDigits.t(), color = Color.White, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
            IconButton(onClick = {
                torch = !torch
                camera?.cameraControl?.enableTorch(torch)
            }) {
                Icon(if (torch) Icons.Rounded.FlashlightOn else Icons.Rounded.FlashlightOff, SScan.torch.t(), tint = Color.White)
            }
        }
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (busy) {
                CircularProgressIndicator(color = Color.White)
            } else {
                FilledIconButton(
                    onClick = {
                        busy = true
                        scope.launch {
                            val file = File(context.cacheDir, "meter_${System.currentTimeMillis()}.jpg")
                            val ok = runCatching { capture.takePictureTo(context, file) }.isSuccess
                            if (ok) onCaptured(file) else busy = false
                        }
                    },
                    modifier = Modifier.size(76.dp),
                    shape = CircleShape,
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color.White, contentColor = Color.Black),
                ) { Icon(Icons.Rounded.Camera, SScan.capture.t(), modifier = Modifier.size(36.dp)) }
            }
            androidx.compose.material3.TextButton(onClick = onTypeInstead) { Text(SScan.typeInstead.t(), color = Color.White) }
        }
    }
}

private suspend fun ImageCapture.takePictureTo(context: Context, file: File) = suspendCancellableCoroutine { cont ->
    takePicture(
        ImageCapture.OutputFileOptions.Builder(file).build(),
        ContextCompat.getMainExecutor(context),
        object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) = cont.resume(Unit)
            override fun onError(exception: ImageCaptureException) = cont.resumeWithException(exception)
        },
    )
}
