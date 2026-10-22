package com.easylife.app.core.ocr

import kotlinx.serialization.Serializable
import kotlin.math.max
import kotlin.math.min

@Serializable
data class OcrBox(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top
    val centerY: Int get() = (top + bottom) / 2
    val centerX: Int get() = (left + right) / 2

    fun verticalOverlap(other: OcrBox): Int = max(0, min(bottom, other.bottom) - max(top, other.top))
}

@Serializable
data class OcrElement(val text: String, val box: OcrBox? = null)

@Serializable
data class OcrLine(val text: String, val box: OcrBox? = null, val elements: List<OcrElement> = emptyList())

/**
 * Text recognised on a page, independent of ML Kit so parsers can be unit-tested with fictional fixtures.
 */
@Serializable
data class OcrDocument(
    val lines: List<OcrLine>,
    val imageWidth: Int = 0,
    val imageHeight: Int = 0,
) {
    /** Lines in the recogniser's own order. */
    val plainText: String get() = lines.joinToString("\n") { it.text }

    val isEmpty: Boolean get() = lines.all { it.text.isBlank() }

    /**
     * Reading-order rows rebuilt from line positions: lines whose vertical centres fall inside the same band are
     * joined left-to-right with a wide gap ("   "), so table rows such as
     * "READING DATE   ISSUE DATE   DUE DATE" come out as one row even when the recogniser split them into columns.
     * Lines without positions (fixtures, pasted text) are kept one per row in order.
     */
    fun rows(): List<String> {
        if (lines.none { it.box != null }) return lines.map { it.text }.filter { it.isNotBlank() }
        val boxed = lines.filter { it.box != null && it.text.isNotBlank() }.sortedBy { it.box!!.centerY }
        val rows = mutableListOf<MutableList<OcrLine>>()
        for (line in boxed) {
            val box = line.box!!
            val row = rows.lastOrNull()
            if (row != null && belongsToRow(row, box)) row += line else rows += mutableListOf(line)
        }
        val rebuilt = rows.map { r -> r.sortedBy { it.box!!.left }.joinToString(COLUMN_GAP) { it.text.trim() } }
        val unboxed = lines.filter { it.box == null && it.text.isNotBlank() }.map { it.text }
        return rebuilt + unboxed
    }

    val rowText: String get() = rows().joinToString("\n")

    private fun belongsToRow(row: List<OcrLine>, box: OcrBox): Boolean {
        val top = row.minOf { it.box!!.top }
        val bottom = row.maxOf { it.box!!.bottom }
        val bandHeight = bottom - top
        val overlap = max(0, min(bottom, box.bottom) - max(top, box.top))
        val minHeight = min(bandHeight, box.height).coerceAtLeast(1)
        return box.centerY in top..bottom && overlap * 2 >= minHeight
    }

    companion object {
        const val COLUMN_GAP = "   "

        fun fromPlainText(text: String): OcrDocument =
            OcrDocument(text.lines().map { OcrLine(it) })
    }
}
