package com.easylife.app.core.designsystem

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.max

/**
 * Big progress ring for "units this cycle / limit". [fraction] may exceed 1 (crossed): the ring is then full
 * and drawn in the crossed colour. [projectedFraction] draws a faint arc showing where the cycle is heading.
 */
@Composable
fun ProgressRing(
    fraction: Float,
    color: Color,
    modifier: Modifier = Modifier,
    projectedFraction: Float? = null,
    size: Dp = 200.dp,
    stroke: Dp = 18.dp,
    description: String = "",
    content: @Composable () -> Unit,
) {
    val animated by animateFloatAsState(fraction.coerceIn(0f, 1f), tween(700), label = "ring")
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    Box(modifier.size(size).semantics { contentDescription = description }, contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val w = stroke.toPx()
            val inset = w / 2
            val arcSize = Size(this.size.width - w, this.size.height - w)
            val topLeft = Offset(inset, inset)
            val sweepSign = if (rtl) -1f else 1f
            drawArc(track, -90f, 360f, false, topLeft, arcSize, style = Stroke(w, cap = StrokeCap.Round))
            projectedFraction?.let { p ->
                val pf = p.coerceIn(0f, 1f)
                if (pf > animated) {
                    drawArc(color.copy(alpha = 0.28f), -90f, sweepSign * 360f * pf, false, topLeft, arcSize, style = Stroke(w, cap = StrokeCap.Round))
                }
            }
            if (animated > 0f) {
                drawArc(color, -90f, sweepSign * 360f * animated, false, topLeft, arcSize, style = Stroke(w, cap = StrokeCap.Round))
            }
        }
        content()
    }
}

/** A bar with label. Values above [limitLine] are coloured with [overColor]. */
data class BarEntry(val label: String, val value: Float, val highlight: Boolean = false)

@Composable
fun BarChart(
    entries: List<BarEntry>,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    overColor: Color = EasyTheme.colors.crossed,
    limitLine: Float? = null,
    height: Dp = 160.dp,
    showLabelsEvery: Int = 1,
    description: String = "",
) {
    if (entries.isEmpty()) return
    val maxValue = max(entries.maxOf { it.value }, limitLine ?: 0f).coerceAtLeast(1f) * 1.1f
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val highlightColor = MaterialTheme.colorScheme.tertiary
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    Column(modifier.fillMaxWidth().semantics { contentDescription = description }) {
        Canvas(Modifier.fillMaxWidth().height(height)) {
            val n = entries.size
            val slot = size.width / n
            val barWidth = (slot * 0.62f).coerceAtMost(40.dp.toPx())
            entries.forEachIndexed { i, e ->
                val index = if (rtl) n - 1 - i else i
                val h = (e.value / maxValue) * size.height
                val x = index * slot + (slot - barWidth) / 2
                val c = when {
                    limitLine != null && e.value > limitLine -> overColor
                    e.highlight -> highlightColor
                    else -> color
                }
                drawRoundRect(c, Offset(x, size.height - h), Size(barWidth, h), CornerRadius(6.dp.toPx(), 6.dp.toPx()))
            }
            limitLine?.let {
                val y = size.height - (it / maxValue) * size.height
                drawLine(
                    overColor,
                    Offset(0f, y),
                    Offset(size.width, y),
                    strokeWidth = 2.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 10f)),
                )
            }
            drawLine(gridColor, Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = 1.dp.toPx())
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
            entries.forEachIndexed { i, e ->
                Text(
                    if (i % showLabelsEvery == 0) e.label else "",
                    style = MaterialTheme.typography.labelSmall,
                    color = labelColor,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/** Horizontal bar used for breakdowns (categories, appliances). */
@Composable
fun HorizontalBar(
    label: String,
    valueText: String,
    fraction: Float,
    color: Color,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
) {
    Column(modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), maxLines = 1)
            Text(valueText, style = MaterialTheme.typography.bodyMedium.merge(NumberStyle))
            trailing?.let {
                Spacer(Modifier.width(6.dp))
                it()
            }
        }
        Spacer(Modifier.height(4.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(10.dp)
                .clip(RoundedCornerShape(5.dp)),
        ) {
            Canvas(Modifier.fillMaxWidth().height(10.dp)) {
                drawRect(color.copy(alpha = 0.15f))
            }
            Box(
                Modifier
                    .fillMaxWidth(fraction.coerceIn(0f, 1f))
                    .height(10.dp)
                    .clip(RoundedCornerShape(5.dp)),
            ) {
                Canvas(Modifier.fillMaxWidth().height(10.dp)) { drawRect(color) }
            }
        }
    }
}

/** Linear meter used on cards: used vs limit, with a marker for the projected end. */
@Composable
fun LimitBar(fraction: Float, color: Color, modifier: Modifier = Modifier, projected: Float? = null) {
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    Canvas(modifier.fillMaxWidth().height(12.dp)) {
        val r = CornerRadius(size.height / 2, size.height / 2)
        drawRoundRect(track, cornerRadius = r)
        fun xFor(f: Float): Float = if (rtl) size.width * (1 - f) else 0f
        projected?.let {
            val p = it.coerceIn(0f, 1f)
            drawRoundRect(color.copy(alpha = 0.3f), Offset(xFor(p), 0f), Size(size.width * p, size.height), r)
        }
        val f = fraction.coerceIn(0f, 1f)
        drawRoundRect(color, Offset(xFor(f), 0f), Size(size.width * f, size.height), r)
    }
}

@Composable
fun Legend(items: List<Pair<Color, String>>, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
        items.forEach { (c, label) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Canvas(Modifier.size(10.dp)) { drawCircle(c) }
                Spacer(Modifier.width(6.dp))
                Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
