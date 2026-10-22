package com.easylife.app.feature.limits

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.easylife.app.core.designsystem.DemoChip
import com.easylife.app.core.designsystem.EasyCard
import com.easylife.app.core.designsystem.EasyTheme
import com.easylife.app.core.designsystem.LimitBar
import com.easylife.app.core.designsystem.Module
import com.easylife.app.core.designsystem.ModuleIcon
import com.easylife.app.core.designsystem.NumberStyle
import com.easylife.app.core.designsystem.RiskPill
import com.easylife.app.core.i18n.AppLanguage
import com.easylife.app.core.i18n.Fmt
import com.easylife.app.core.i18n.SLimits
import com.easylife.app.core.i18n.Txt
import com.easylife.app.core.i18n.currentLanguage
import com.easylife.app.core.i18n.t
import com.easylife.app.core.limits.LimitIssue
import com.easylife.app.core.limits.MeterDashboard
import com.easylife.app.core.limits.RateConfidence
import com.easylife.app.core.limits.Risk

fun Risk.label(): Txt = when (this) {
    Risk.SAFE -> SLimits.riskSafe
    Risk.WATCH -> SLimits.riskWatch
    Risk.DANGER -> SLimits.riskDanger
    Risk.CROSSED -> SLimits.riskCrossed
}

/** The one sentence that matters, e.g. "At this pace you'll reach 212 units by the 18th. Keep to 5 units/day…". */
fun meterHeadline(d: MeterDashboard, lang: AppLanguage): String {
    val s = d.status ?: return if (d.needsBaseline) SLimits.noBaseline.get(lang) else SLimits.needReading.get(lang)
    if (s.dailyRate == null) return SLimits.needReading.get(lang)
    val limit = Fmt.ltr(Fmt.number(d.limit), lang)
    val projected = Fmt.ltr(Fmt.number(d.projectedUnits ?: s.usedSoFar.toInt()), lang)
    val end = d.cycleEnd?.let { Fmt.shortDate(it, lang) } ?: ""
    val allowance = Fmt.ltr(Fmt.smart(s.dailyAllowance ?: 0.0), lang)
    return when (s.risk) {
        Risk.CROSSED -> SLimits.headlineCrossed.format(lang, Fmt.ltr(Fmt.number(s.usedSoFar.toInt()), lang), limit)
        Risk.DANGER -> SLimits.headlineDanger.format(lang, projected, end, allowance, limit, s.projectedCrossDate?.let { Fmt.shortDate(it, lang) } ?: end)
        Risk.WATCH -> SLimits.headlineWatch.format(lang, projected, end, allowance, limit)
        Risk.SAFE -> SLimits.headlineSafe.format(lang, projected, end, limit)
    }
}

/** Short notes about data quality: readings that went down, needing another reading, using last month's rate. */
fun meterNotes(d: MeterDashboard, lang: AppLanguage): List<String> {
    val s = d.status ?: return emptyList()
    val notes = mutableListOf<String>()
    s.issues.filterIsInstance<LimitIssue.ReadingWentDown>().forEach {
        notes += SLimits.readingDown.format(lang, Fmt.number(it.previous), Fmt.number(it.current))
    }
    if (s.issues.any { it is LimitIssue.RolloverAssumed }) notes += SLimits.rolloverNote.get(lang)
    if (s.rateConfidence == RateConfidence.PRIOR) notes += SLimits.usingPrior.get(lang)
    if (d.readingOverdue) notes += SLimits.overdue.get(lang)
    if (d.needsBaseline && s.dailyRate != null) notes += SLimits.noBaseline.get(lang)
    return notes
}

/** Compact card for Today and the Modules grid. */
@Composable
fun MeterSummaryCard(d: MeterDashboard, isDemo: Boolean, onClick: () -> Unit) {
    val lang = currentLanguage()
    val s = d.status
    val risk = s?.risk ?: Risk.SAFE
    val color = EasyTheme.colors.risk(risk)
    EasyCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ModuleIcon(Module.LIMITS, 40.dp)
            Spacer(Modifier.width(10.dp))
            Text(SLimits.title.t(), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            if (isDemo) {
                DemoChip()
                Spacer(Modifier.width(6.dp))
            }
            if (s != null) RiskPill(risk, risk.label().t())
        }
        Spacer(Modifier.height(10.dp))
        if (s != null) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(Fmt.number(s.usedSoFar.toInt()), style = MaterialTheme.typography.headlineMedium.merge(NumberStyle), color = color)
                Spacer(Modifier.width(6.dp))
                Text(SLimits.ofLimit.t(Fmt.ltr(Fmt.number(d.limit), lang)) + " " + SLimits.unitsThisCycle.t(), style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(Modifier.height(6.dp))
            LimitBar(
                fraction = (s.usedSoFar / d.limit).toFloat(),
                color = color,
                projected = d.projectedUnits?.let { (it.toDouble() / d.limit).toFloat() },
            )
            Spacer(Modifier.height(8.dp))
        }
        Text(meterHeadline(d, lang), style = MaterialTheme.typography.bodyMedium)
    }
}
