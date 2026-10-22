package com.easylife.app.core.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.util.Base64
import com.easylife.app.core.ocr.OcrEngine
import com.easylife.app.core.ocr.imageUri
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max

/**
 * Prepares photos for a cloud provider (spec rule 4 applied to images):
 * downscale, then black out every OCR word that looks like an account, card, CNIC, IBAN, reference or phone number.
 */
@Singleton
class CloudImagePreparer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val ocr: OcrEngine,
) {
    data class Prepared(val bitmap: Bitmap, val redactedRegions: Int)

    suspend fun prepare(imageRef: String, maxSide: Int = 1600): Prepared {
        val bitmap = decodeSampled(imageRef, maxSide).copy(Bitmap.Config.ARGB_8888, true)
        val doc = ocr.recognize(bitmap)
        val canvas = Canvas(bitmap)
        val paint = Paint().apply { color = Color.BLACK; style = Paint.Style.FILL }
        var count = 0
        for (line in doc.lines) {
            if (Masker.forCloud(line.text).maskedCount == 0) continue
            // Prefer word-level boxes; fall back to the whole line.
            val words = line.elements.filter { Masker.forCloud(it.text).maskedCount > 0 || it.text.count(Char::isDigit) >= 4 }
            val boxes = words.mapNotNull { it.box }.ifEmpty { listOfNotNull(line.box) }
            for (b in boxes) {
                canvas.drawRect(Rect(b.left - 4, b.top - 4, b.right + 4, b.bottom + 4), paint)
                count++
            }
        }
        return Prepared(bitmap, count)
    }

    fun toJpegBase64(bitmap: Bitmap, quality: Int = 80): String {
        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
        return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }

    private fun decodeSampled(ref: String, maxSide: Int): Bitmap {
        val uri = imageUri(ref)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / sample > maxSide * 2) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val decoded = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
            ?: error("Cannot read image")
        val scale = maxSide.toFloat() / max(decoded.width, decoded.height)
        return if (scale < 1f) {
            Bitmap.createScaledBitmap(decoded, (decoded.width * scale).toInt(), (decoded.height * scale).toInt(), true)
        } else {
            decoded
        }
    }
}
