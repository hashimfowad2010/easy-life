package com.easylife.app.core.scan

import android.app.Activity
import android.net.Uri
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult

/** Handles to start the ML Kit Document Scanner or the photo picker. */
class ScanLaunchers(val scanDocument: () -> Unit, val pickPhoto: () -> Unit)

/**
 * ML Kit Document Scanner (edge detection, crop, gallery import) with a photo-picker fallback when Google Play
 * services' scanner is unavailable. No camera permission is needed for either.
 */
@Composable
fun rememberScanLaunchers(
    onImages: (List<Uri>) -> Unit,
    onScannerUnavailable: () -> Unit,
    pageLimit: Int = 4,
): ScanLaunchers {
    val activity = LocalActivity.current
    val currentOnImages = rememberUpdatedState(onImages)
    val currentOnUnavailable = rememberUpdatedState(onScannerUnavailable)

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(pageLimit)) { uris ->
        if (uris.isNotEmpty()) currentOnImages.value(uris)
    }
    val scannerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val pages = GmsDocumentScanningResult.fromActivityResultIntent(result.data)?.pages.orEmpty()
            val uris = pages.map { it.imageUri }
            if (uris.isNotEmpty()) currentOnImages.value(uris)
        }
    }
    return remember(activity) {
        ScanLaunchers(
            scanDocument = {
                val options = GmsDocumentScannerOptions.Builder()
                    .setGalleryImportAllowed(true)
                    .setPageLimit(pageLimit)
                    .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_JPEG)
                    .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
                    .build()
                if (activity == null) {
                    currentOnUnavailable.value()
                } else {
                    GmsDocumentScanning.getClient(options).getStartScanIntent(activity)
                        .addOnSuccessListener { sender -> scannerLauncher.launch(IntentSenderRequest.Builder(sender).build()) }
                        .addOnFailureListener { currentOnUnavailable.value() }
                }
            },
            pickPhoto = {
                picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            },
        )
    }
}
