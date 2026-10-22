package com.easylife.app.core.impact

import com.easylife.app.core.ai.AiResult
import com.easylife.app.core.ai.AiResultListener
import com.easylife.app.core.ai.AiTask
import com.easylife.app.core.data.db.ImpactDao
import com.easylife.app.core.data.db.ImpactEventEntity
import com.easylife.app.core.di.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToLong

/** Names of anonymous impact events (spec section 8). Counts and rounded values only. */
object ImpactEvents {
    const val LIMIT_WARNING_SHOWN = "limit_warning_shown"
    const val ACTION_AFTER_WARNING = "action_after_warning"
    const val CYCLE_UNDER_AFTER_WARNING = "cycle_under_limit_after_warning"
    const val BILL_REMINDER_SHOWN = "bill_reminder_shown"
    const val BILL_PAID_BEFORE_DUE = "bill_paid_before_due"
    const val LATE_FEE_AVOIDED = "late_fee_avoided_rs"
    const val DOC_READ = "document_read"
    const val DOC_TRANSLATED = "document_translated"
    const val SHOPPING_COMPARED = "shopping_compared"
    const val SHOPPING_SAVINGS = "shopping_savings_rs"
    const val TX_EXCLUDED = "transaction_excluded"
    const val AI_RESULT = "ai_result"
    const val AI_CORRECTED = "ai_corrected"
    const val SCAN_DONE = "scan_done"
    const val METER_READING = "meter_reading_added"
    const val APP_OPEN = "app_open"
}

/**
 * Logs impact events on the phone. They are uploaded (anonymously, rounded) only if the user opted in
 * (see ImpactUploadWorker). Also records which AI source produced each result and how often users corrected it:
 * the evidence for funding Smart AI.
 */
@Singleton
class ImpactTracker @Inject constructor(
    private val dao: ImpactDao,
    @ApplicationScope private val scope: CoroutineScope,
) : AiResultListener {

    fun log(event: String, module: String, value: Double? = null, detail: String? = null) {
        scope.launch {
            runCatching { dao.insert(ImpactEventEntity(event = event, module = module, value = value?.let(::round), detail = detail)) }
        }
    }

    override fun onResult(task: AiTask<*>, result: AiResult<*>) {
        log(ImpactEvents.AI_RESULT, "ai", null, "${task.key}:${result.source.key}:${if (result.ok) "ok" else "fail"}")
    }

    /** Called from confirm screens: how many fields the user had to change. */
    fun logCorrections(task: AiTask<*>, source: String, changedFields: Int) {
        log(ImpactEvents.AI_CORRECTED, "ai", changedFields.toDouble(), "${task.key}:$source")
    }

    companion object {
        /** Values leave the phone rounded: money to Rs 10, others to whole numbers. */
        fun round(v: Double): Double = if (v >= 100) (v / 10).roundToLong() * 10.0 else v.roundToLong().toDouble()
    }
}
