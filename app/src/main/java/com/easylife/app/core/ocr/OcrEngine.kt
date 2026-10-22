package com.easylife.app.core.ocr

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.tasks.await
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** Text recognition on the phone (free, offline). Interface so parsers and providers are testable without ML Kit. */
interface OcrEngine {
    suspend fun recognize(imageRef: String): OcrDocument
    suspend fun recognize(bitmap: Bitmap): OcrDocument

    /** Several pages as one document (page 2 placed below page 1). */
    suspend fun recognizeAll(imageRefs: List<String>): OcrDocument {
        val pages = imageRefs.map { recognize(it) }
        if (pages.size == 1) return pages.first()
        var offset = 0
        val lines = mutableListOf<OcrLine>()
        for (p in pages) {
            lines += p.lines.map { l -> l.copy(box = l.box?.let { it.copy(top = it.top + offset, bottom = it.bottom + offset) }) }
            offset += (p.imageHeight.takeIf { it > 0 } ?: (p.lines.mapNotNull { it.box?.bottom }.maxOrNull() ?: 0)) + 50
        }
        return OcrDocument(lines, pages.maxOf { it.imageWidth }, offset)
    }
}

fun imageUri(ref: String): Uri =
    if (ref.startsWith("content://") || ref.startsWith("file://")) Uri.parse(ref) else Uri.fromFile(File(ref))

@Singleton
class MlKitOcrEngine @Inject constructor(@ApplicationContext private val context: Context) : OcrEngine {

    // Latin script model bundled in the APK: works offline from the first launch.
    private val recognizer by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }

    override suspend fun recognize(imageRef: String): OcrDocument {
        val image = InputImage.fromFilePath(context, imageUri(imageRef))
        val text = recognizer.process(image).await()
        return text.toDocument(image.width, image.height)
    }

    override suspend fun recognize(bitmap: Bitmap): OcrDocument {
        val image = InputImage.fromBitmap(bitmap, 0)
        val text = recognizer.process(image).await()
        return text.toDocument(bitmap.width, bitmap.height)
    }

    private fun Text.toDocument(width: Int, height: Int): OcrDocument = OcrDocument(
        lines = textBlocks.flatMap { block ->
            block.lines.map { line ->
                OcrLine(
                    text = line.text,
                    box = line.boundingBox?.let { OcrBox(it.left, it.top, it.right, it.bottom) },
                    elements = line.elements.map { e ->
                        OcrElement(e.text, e.boundingBox?.let { OcrBox(it.left, it.top, it.right, it.bottom) })
                    },
                )
            }
        },
        imageWidth = width,
        imageHeight = height,
    )
}
