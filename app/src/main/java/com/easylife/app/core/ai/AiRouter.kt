package com.easylife.app.core.ai

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout

/** Everything the router needs to decide, as one snapshot (from Remote Config + user settings). */
data class AiSettings(
    /** Remote Config `ai_routing`, e.g. {"bill_extract": ["claude","gemini_free","on_device"]}. */
    val routing: Map<String, List<String>>,
    val geminiEnabled: Boolean,
    val claudeEnabled: Boolean,
    /** User setting "Smart AI". Off = on-device only. */
    val smartAiEnabled: Boolean,
    val geminiConsent: Boolean,
    val claudeConsent: Boolean,
)

fun interface AiSettingsSource {
    suspend fun snapshot(): AiSettings
}

data class RouteStep(val provider: ProviderId, val willTry: Boolean, val reason: String)

data class RoutePlan(val task: AiTask<*>, val steps: List<RouteStep>) {
    val candidates: List<ProviderId> get() = steps.filter { it.willTry }.map { it.provider }
    val first: ProviderId? get() = candidates.firstOrNull()
}

/** Called after every routed run, e.g. to count results per source for the impact metrics. */
fun interface AiResultListener {
    fun onResult(task: AiTask<*>, result: AiResult<*>)
}

/**
 * Picks providers per task in the order from Remote Config, skipping any that are disabled, unconsented,
 * unavailable or cooling down after a failure, and always falls back to ON_DEVICE.
 */
class AiRouter(
    private val providers: Map<ProviderId, AiProvider>,
    private val settings: AiSettingsSource,
    private val clock: () -> Long = System::currentTimeMillis,
    private val cloudTimeoutMillis: Long = 25_000,
    private val listener: AiResultListener? = null,
) {
    private val cooldownUntil = mutableMapOf<ProviderId, Long>()

    suspend fun plan(task: AiTask<*>): RoutePlan {
        val s = settings.snapshot()
        val configured = s.routing[task.key].orEmpty().mapNotNull { ProviderId.fromKey(it) }
        // MOCK is only used when asked for explicitly (demo seeding, tests), never from routing.
        val order = (configured.filter { it != ProviderId.MOCK } + ProviderId.ON_DEVICE).distinct()
        val steps = order.map { id -> RouteStep(id, false, "") }.map { step ->
            val reason = skipReason(step.provider, task, s)
            if (reason != null) step.copy(reason = reason) else step.copy(willTry = true, reason = "will try")
        }
        return RoutePlan(task, steps)
    }

    private suspend fun skipReason(id: ProviderId, task: AiTask<*>, s: AiSettings): String? {
        val provider = providers[id] ?: return "provider not installed"
        if (id.isCloud) {
            if (!s.smartAiEnabled) return "Smart AI is off (on-device only)"
            if (id == ProviderId.GEMINI_FREE && task.sensitive) return "financial data is never sent to a free-tier AI (rule 5)"
            if (id == ProviderId.GEMINI_FREE && !s.geminiEnabled) return "gemini_enabled = false (Remote Config)"
            if (id == ProviderId.CLAUDE && !s.claudeEnabled) return "claude_enabled = false (Remote Config)"
            if (id == ProviderId.GEMINI_FREE && !s.geminiConsent) return "no consent yet for Gemini"
            if (id == ProviderId.CLAUDE && !s.claudeConsent) return "no consent yet for Claude"
            val until = cooldownUntil[id]
            if (until != null && clock() < until) return "cooling down after a failure"
        }
        if (!provider.supports(task)) return "does not support ${task.key}"
        val availability = provider.isAvailable(task)
        if (!availability.available) return availability.reason
        return null
    }

    /**
     * Run [task] through the plan. [force] runs exactly one provider (used for Demo mode = MOCK and Parser Lab).
     * Text is masked on the phone before any cloud provider sees it.
     */
    suspend fun <R : Any> run(task: AiTask<R>, input: AiInput, force: ProviderId? = null): AiResult<R> {
        val candidates = if (force != null) listOf(force) else plan(task).candidates
        val attempts = mutableListOf<AiAttempt>()
        var last: AiResult<R>? = null
        for (id in candidates) {
            val provider = providers[id] ?: continue
            val started = clock()
            val prepared = if (id.isCloud) maskForCloud(input, freeTier = id == ProviderId.GEMINI_FREE) else input
            val result = try {
                if (id.isCloud) withTimeout(cloudTimeoutMillis) { provider.run(task, prepared) } else provider.run(task, prepared)
            } catch (e: TimeoutCancellationException) {
                AiResult.failure(id, AiError.Timeout)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AiResult.failure(id, AiError.Failed(e.javaClass.simpleName + ": " + (e.message ?: "")))
            }
            attempts += AiAttempt(id, result.error, clock() - started)
            if (result.ok) {
                cooldownUntil.remove(id)
                return result.copy(attempts = attempts.toList()).also { listener?.onResult(task, it) }
            }
            if (id.isCloud) {
                val pause = when (result.error) {
                    AiError.RateLimited -> RATE_LIMIT_COOLDOWN
                    is AiError.InvalidJson, AiError.Timeout -> FAILURE_COOLDOWN
                    else -> 0L
                }
                if (pause > 0) cooldownUntil[id] = clock() + pause
            }
            last = result
        }
        val final = (last ?: AiResult.failure(ProviderId.ON_DEVICE, AiError.Unavailable("no provider available")))
            .copy(attempts = attempts.toList())
        listener?.onResult(task, final)
        return final
    }

    /** The free tier (Gemini) also gets names and addresses hidden: Google may keep and review free-tier input. */
    private fun maskForCloud(input: AiInput, freeTier: Boolean): AiInput {
        val mask: (String) -> String = { if (freeTier) Masker.forFreeTier(it).text else Masker.forCloud(it).text }
        return input.copy(
            text = input.text?.let(mask),
            // OCR text is not sent to cloud providers; images are redacted by the provider using these boxes.
            params = input.params.mapValues { (_, v) -> mask(v) },
        )
    }

    companion object {
        const val RATE_LIMIT_COOLDOWN = 10 * 60_000L
        const val FAILURE_COOLDOWN = 2 * 60_000L
    }
}
