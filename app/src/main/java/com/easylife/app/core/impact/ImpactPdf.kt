package com.easylife.app.core.impact

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.res.ResourcesCompat
import com.easylife.app.R
import com.easylife.app.core.i18n.AppLanguage
import com.easylife.app.core.i18n.Fmt
import com.easylife.app.core.i18n.SImpact
import com.easylife.app.core.i18n.Txt
import java.io.OutputStream
import java.time.ZoneId

/** Labels shared by the report screen and the PDF. */
fun moduleLabel(module: String): Txt = when (module) {
    "limits" -> SImpact.moduleLimits
    "bills" -> SImpact.moduleBills
    "budget" -> SImpact.moduleBudget
    "urdu" -> SImpact.moduleUrdu
    "shopping" -> SImpact.moduleShopping
    "scan" -> SImpact.moduleScan
    else -> SImpact.moduleOther
}

fun sourceLabel(source: String): Txt = when (source) {
    "on_device" -> SImpact.sourceOnDevice
    "gemini_free" -> SImpact.sourceGemini
    "claude" -> SImpact.sourceClaude
    "demo" -> SImpact.sourceDemo
    else -> Txt(source, source)
}

/** Highlight tiles in display order: label and formatted value. */
fun highlightTiles(h: ImpactHighlights, lang: AppLanguage): List<Pair<Txt, String>> = listOf(
    SImpact.warnings to Fmt.number(h.warnings),
    SImpact.actionsAfter to Fmt.number(h.actionsAfterWarning),
    SImpact.cyclesUnder to Fmt.number(h.cyclesUnderAfterWarning),
    SImpact.paidOnTime to Fmt.number(h.billsPaidOnTime),
    SImpact.lateFees to Fmt.rupees(h.lateFeesAvoidedRs, lang),
    SImpact.docs to Fmt.number(h.documents),
    SImpact.comparisons to Fmt.number(h.comparisons),
    SImpact.shopSavings to Fmt.rupees(h.shoppingSavingsRs, lang),
    SImpact.excluded to Fmt.number(h.transactionsExcluded),
    SImpact.feedbackCount to Fmt.number(h.feedbackCount),
    SImpact.avgRating to (h.averageRating?.let { Fmt.smart(it) } ?: "—"),
)

/**
 * One A4 page: title, highlights and two bar charts (activity by module, results by AI source),
 * drawn with Android's PdfDocument so it works offline.
 */
object ImpactPdf {
    private const val W = 595
    private const val H = 842
    private const val M = 40f
    private val TEAL = Color.rgb(11, 122, 110)
    private val AMBER = Color.rgb(196, 122, 18)
    private val INK = Color.rgb(28, 32, 36)
    private val MUTED = Color.rgb(96, 104, 112)
    private val TILE = Color.rgb(236, 244, 242)

    fun write(context: Context, s: ImpactSummary, lang: AppLanguage, demo: Boolean, out: OutputStream) {
        val doc = PdfDocument()
        val page = doc.startPage(PdfDocument.PageInfo.Builder(W, H, 1).create())
        val typeface = if (lang == AppLanguage.UR) {
            ResourcesCompat.getFont(context, R.font.noto_naskh_arabic) ?: Typeface.DEFAULT
        } else {
            Typeface.DEFAULT
        }
        Painter(page.canvas, typeface).draw(s, lang, demo)
        doc.finishPage(page)
        doc.writeTo(out)
        doc.close()
    }

    private class Bar(val label: String, val value: Int, val valueText: String)

    private class Painter(val c: Canvas, val typeface: Typeface) {
        var y = M

        fun text(t: String, size: Float, color: Int = INK, bold: Boolean = false, x: Float = M, width: Float = W - 2 * M): Float {
            val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = size
                this.color = color
                typeface = if (bold) Typeface.create(this@Painter.typeface, Typeface.BOLD) else this@Painter.typeface
            }
            val layout = StaticLayout.Builder.obtain(t, 0, t.length, paint, width.toInt())
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(0f, 1.15f)
                .build()
            c.save()
            c.translate(x, y)
            layout.draw(c)
            c.restore()
            return layout.height.toFloat()
        }

        fun draw(s: ImpactSummary, lang: AppLanguage, demo: Boolean) {
            fun Txt.s() = get(lang)
            y += text(SImpact.reportTitle.s(), 22f, TEAL, bold = true) + 4
            val zone = ZoneId.systemDefault()
            val period = listOfNotNull(
                s.from?.let { SImpact.since.format(lang, Fmt.date(it.atZone(zone).toLocalDate(), lang)) },
                s.pilotCode?.let { SImpact.pilot.format(lang, it) } ?: SImpact.noPilot.s(),
            ).joinToString("  ·  ")
            y += text(period, 10f, MUTED) + 4
            y += text(SImpact.reportIntro.s(), 10f, MUTED) + 6
            if (demo) y += text(SImpact.demoWarning.s(), 10f, AMBER, bold = true) + 6

            // Highlights: 2 columns of tiles.
            y += 6
            y += text(SImpact.headline.s(), 14f, bold = true) + 6
            val tiles = highlightTiles(s.highlights, lang)
            val colW = (W - 2 * M - 10) / 2
            val tileH = 40f
            tiles.forEachIndexed { i, (label, value) ->
                val col = i % 2
                val row = i / 2
                val left = M + col * (colW + 10)
                val top = y + row * (tileH + 6)
                c.drawRoundRect(RectF(left, top, left + colW, top + tileH), 6f, 6f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = TILE })
                val saved = y
                y = top + 5
                text(value, 14f, TEAL, bold = true, x = left + 8, width = 90f)
                text(label.s(), 9f, INK, x = left + 100, width = colW - 108)
                y = saved
            }
            y += ((tiles.size + 1) / 2) * (tileH + 6) + 8

            // Chart 1: activity by module.
            y += text(SImpact.perModule.s(), 14f, bold = true) + 6
            bars(s.moduleActivity.map { (m, n) -> Bar(moduleLabel(m).s(), n, Fmt.number(n)) }, TEAL)

            // Chart 2: results by source, with how often the user corrected them.
            y += 8
            y += text(SImpact.bySource.s(), 14f, bold = true) + 4
            y += text(SImpact.bySourceNote.s(), 9f, MUTED) + 6
            val sources = s.sources.filter { it.results > 0 || it.confirmations > 0 }
            bars(
                sources.map { st ->
                    val note = if (st.confirmations > 0) "  ·  " + SImpact.corrected.format(lang, st.correctedPercent) else ""
                    Bar(sourceLabel(st.source).s(), st.results, SImpact.results.format(lang, st.results) + note)
                },
                AMBER,
            )

            y = H - M - 14
            text(SImpact.pdfFooter.s(), 8f, MUTED)
        }

        fun bars(items: List<Bar>, color: Int) {
            if (items.isEmpty()) {
                y += text("—", 10f, MUTED) + 4
                return
            }
            val max = items.maxOf { it.value }.coerceAtLeast(1)
            val labelW = 130f
            val barMax = W - 2 * M - labelW - 150
            items.take(7).forEach { bar ->
                text(bar.label, 10f, x = M, width = labelW - 6)
                val w = (barMax * bar.value / max).coerceAtLeast(2f)
                c.drawRoundRect(RectF(M + labelW, y + 2, M + labelW + w, y + 14), 3f, 3f, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color })
                text(bar.valueText, 9f, MUTED, x = M + labelW + w + 6, width = 150f)
                y += 20
            }
        }
    }
}
