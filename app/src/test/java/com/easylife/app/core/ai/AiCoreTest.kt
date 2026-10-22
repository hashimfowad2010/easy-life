package com.easylife.app.core.ai

import com.easylife.app.core.ai.schema.BillExtractResult
import com.easylife.app.core.ai.schema.ClassifyResult
import com.easylife.app.core.ai.schema.MeterReadResult
import com.easylife.app.core.ai.schema.SmsParseResult
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MaskerTest {
    @Test
    fun masksIdentifiersKeepsAmountsAndDates() {
        val sms = "PKR 1,250.00 debited from A/C 01234567890123 on 22-10-2026. CNIC 35202-1234567-1. Call 0300-1234567. " +
            "IBAN PK36SCBL0000001123456702 card 4111 1111 1111 1111 ref 99 11234 0001234 U"
        val out = Masker.forCloud(sms).text
        assertTrue("1,250.00" in out)
        assertTrue("22-10-2026" in out)
        assertFalse("01234567890123" in out)
        assertFalse("35202-1234567-1" in out)
        assertFalse("0300-1234567" in out)
        assertFalse("PK36SCBL0000001123456702" in out)
        assertFalse("4111 1111 1111 1111" in out)
        assertFalse("0001234" in out)
        assertTrue(Masker.forCloud(sms).maskedCount >= 6)
    }

    @Test
    fun sharingAlsoMasksNamesButNotPriceTags() {
        val text = "NAME & ADDRESS: Sample Person\nS/O Someone\nMRP 250\nUnits 186"
        val out = Masker.forSharing(text).text
        assertFalse("Sample Person" in out)
        assertFalse("Someone" in out)
        assertTrue("MRP 250" in out)
        assertTrue("Units 186" in out)
    }
}

class CloudJsonTest {
    @Test
    fun acceptsFencedJson() {
        val raw = "```json\n{\"kind\":\"meter\",\"alternatives\":[\"receipt\",\"bogus\"],\"confidence\":0.9}\n```"
        val r = CloudJson.decode(AiTask.ClassifyImage, raw, ProviderId.GEMINI_FREE)
        assertTrue(r.ok)
        assertEquals("meter", r.value!!.kind)
        assertEquals(listOf("receipt"), r.value!!.alternatives)
    }

    @Test
    fun rejectsGarbageAndUnknownKinds() {
        assertIs<AiError.InvalidJson>(CloudJson.decode(AiTask.ClassifyImage, "sorry, I can't", ProviderId.CLAUDE).error)
        assertIs<AiError.InvalidJson>(CloudJson.decode(AiTask.ClassifyImage, "{\"kind\":\"spaceship\",\"confidence\":1}", ProviderId.CLAUDE).error)
        assertIs<AiError.InvalidJson>(CloudJson.decode(AiTask.ClassifyImage, "{\"kind\": ", ProviderId.CLAUDE).error)
        assertIs<AiError.InvalidJson>(CloudJson.decode(AiTask.ClassifyImage, "", ProviderId.CLAUDE).error)
    }

    @Test
    fun implausibleBillValuesBecomeUncertain() {
        val raw = """{"billType":"electricity","dueDate":"2026-13-45","issueDate":"2026-10-11","amountWithinDue":-5,
            "amountAfterDue":3000,"unitsConsumed":186,"previousReading":100,"presentReading":200,"confidence":3.5,"uncertainFields":[]}"""
        val r = CloudJson.decode(AiTask.BillExtract, raw, ProviderId.CLAUDE)
        val bill: BillExtractResult = r.value!!
        assertNull(bill.dueDate)
        assertNull(bill.amountWithinDue)
        assertTrue("dueDate" in r.uncertainFields)
        assertTrue("amountWithinDue" in r.uncertainFields)
        assertTrue("unitsConsumed" in r.uncertainFields, "200 - 100 != 186")
        assertTrue(r.confidence < 1.0)
    }

    @Test
    fun meterReadingOutOfRange() {
        val r = CloudJson.decode(AiTask.MeterRead, """{"reading": 99999999, "digitsSeen":"99999999","unit":"kWh","confidence":0.9,"issues":[]}""", ProviderId.CLAUDE)
        assertNull(r.value!!.reading)
        assertTrue("reading" in r.uncertainFields)
    }

    @Test
    fun smsWithoutAmountIsFlagged() {
        val r = CloudJson.decode(AiTask.SmsParse, """{"isTransaction":true,"direction":"sideways","confidence":0.7}""", ProviderId.CLAUDE)
        val v: SmsParseResult = r.value!!
        assertNull(v.direction)
        assertTrue("amount" in r.uncertainFields && "direction" in r.uncertainFields)
    }
}

class AiRouterTest {

    private class FakeProvider(
        override val id: ProviderId,
        var available: Boolean = true,
        var error: AiError? = null,
        val supported: Set<AiTask<*>>? = null,
        val delayMillis: Long = 0,
    ) : AiProvider {
        val calls = mutableListOf<Pair<AiTask<*>, AiInput>>()
        override fun supports(task: AiTask<*>) = supported?.contains(task) ?: true
        override suspend fun isAvailable(task: AiTask<*>) = if (available) Availability.Yes else Availability.no("offline")

        @Suppress("UNCHECKED_CAST")
        override suspend fun <R : Any> run(task: AiTask<R>, input: AiInput): AiResult<R> {
            calls += task to input
            if (delayMillis > 0) delay(delayMillis)
            error?.let { return AiResult.failure(id, it) }
            val value: Any = when (task) {
                AiTask.ClassifyImage -> ClassifyResult("meter", confidence = 0.9)
                AiTask.MeterRead -> MeterReadResult(reading = 1.0, digitsSeen = "1", confidence = 0.9)
                AiTask.SmsParse -> SmsParseResult(isTransaction = true, amount = 10.0, direction = "debit", confidence = 0.9)
                else -> throw IllegalStateException("not in test")
            }
            return AiResult(value as R, 0.9, emptyList(), id)
        }
    }

    private val allCloudOn = AiSettings(
        routing = mapOf(
            "classify_image" to listOf("claude", "gemini_free", "on_device"),
            "sms_parse" to listOf("gemini_free", "claude", "on_device"),
        ),
        geminiEnabled = true,
        claudeEnabled = true,
        smartAiEnabled = true,
        geminiConsent = true,
        claudeConsent = true,
    )

    private fun router(settings: AiSettings, vararg providers: FakeProvider, now: () -> Long = { 0L }) =
        AiRouter(providers.associateBy { it.id }, { settings }, now)

    @Test
    fun followsRemoteConfigOrder() = runTest {
        val claude = FakeProvider(ProviderId.CLAUDE)
        val gemini = FakeProvider(ProviderId.GEMINI_FREE)
        val device = FakeProvider(ProviderId.ON_DEVICE)
        val r = router(allCloudOn, claude, gemini, device).run(AiTask.ClassifyImage, AiInput(text = "x"))
        assertEquals(ProviderId.CLAUDE, r.source)
        assertTrue(gemini.calls.isEmpty() && device.calls.isEmpty())
    }

    @Test
    fun fallsBackOnRateLimitThenCoolsDown() = runTest {
        var clock = 0L
        val claude = FakeProvider(ProviderId.CLAUDE, error = AiError.RateLimited)
        val gemini = FakeProvider(ProviderId.GEMINI_FREE, error = AiError.InvalidJson("bad"))
        val device = FakeProvider(ProviderId.ON_DEVICE)
        val router = router(allCloudOn, claude, gemini, device) { clock }
        val r = router.run(AiTask.ClassifyImage, AiInput(text = "x"))
        assertEquals(ProviderId.ON_DEVICE, r.source)
        assertEquals(listOf(ProviderId.CLAUDE, ProviderId.GEMINI_FREE, ProviderId.ON_DEVICE), r.attempts.map { it.provider })
        // Claude is now cooling down: the plan skips it.
        val plan = router.plan(AiTask.ClassifyImage)
        assertFalse(plan.steps.first { it.provider == ProviderId.CLAUDE }.willTry)
        clock += AiRouter.RATE_LIMIT_COOLDOWN + 1
        assertTrue(router.plan(AiTask.ClassifyImage).steps.first { it.provider == ProviderId.CLAUDE }.willTry)
    }

    @Test
    fun smartAiOffMeansOnDeviceOnly() = runTest {
        val claude = FakeProvider(ProviderId.CLAUDE)
        val device = FakeProvider(ProviderId.ON_DEVICE)
        val r = router(allCloudOn.copy(smartAiEnabled = false), claude, device).run(AiTask.ClassifyImage, AiInput(text = "x"))
        assertEquals(ProviderId.ON_DEVICE, r.source)
        assertTrue(claude.calls.isEmpty())
    }

    @Test
    fun noConsentNoCloud() = runTest {
        val claude = FakeProvider(ProviderId.CLAUDE)
        val gemini = FakeProvider(ProviderId.GEMINI_FREE)
        val device = FakeProvider(ProviderId.ON_DEVICE)
        val plan = router(allCloudOn.copy(claudeConsent = false, geminiConsent = false), claude, gemini, device).plan(AiTask.ClassifyImage)
        assertEquals(listOf(ProviderId.ON_DEVICE), plan.candidates)
        assertTrue(plan.steps.first { it.provider == ProviderId.CLAUDE }.reason.contains("consent"))
    }

    @Test
    fun smsNeverGoesToFreeTierEvenIfRoutedThere() = runTest {
        val claude = FakeProvider(ProviderId.CLAUDE)
        val gemini = FakeProvider(ProviderId.GEMINI_FREE)
        val device = FakeProvider(ProviderId.ON_DEVICE)
        val r = router(allCloudOn, claude, gemini, device).run(AiTask.SmsParse, AiInput(text = "PKR 500 debited from A/C 01234567890123"))
        assertTrue(gemini.calls.isEmpty(), "SMS must never reach the free tier")
        assertEquals(ProviderId.CLAUDE, r.source)
        // And it was masked before leaving the phone.
        assertFalse(claude.calls.single().second.text!!.contains("01234567890123"))
    }

    @Test
    fun remoteConfigFlagsDisableProviders() = runTest {
        val claude = FakeProvider(ProviderId.CLAUDE)
        val gemini = FakeProvider(ProviderId.GEMINI_FREE)
        val device = FakeProvider(ProviderId.ON_DEVICE)
        val plan = router(allCloudOn.copy(claudeEnabled = false, geminiEnabled = false), claude, gemini, device).plan(AiTask.ClassifyImage)
        assertEquals(listOf(ProviderId.ON_DEVICE), plan.candidates)
    }

    @Test
    fun alwaysEndsAtOnDeviceEvenIfNotConfigured() = runTest {
        val device = FakeProvider(ProviderId.ON_DEVICE)
        val plan = router(allCloudOn.copy(routing = mapOf("classify_image" to listOf("claude"))), device).plan(AiTask.ClassifyImage)
        assertEquals(ProviderId.ON_DEVICE, plan.candidates.last())
    }

    @Test
    fun unavailableProviderSkippedWithReason() = runTest {
        val claude = FakeProvider(ProviderId.CLAUDE, available = false)
        val device = FakeProvider(ProviderId.ON_DEVICE)
        val plan = router(allCloudOn, claude, device).plan(AiTask.ClassifyImage)
        assertEquals("offline", plan.steps.first { it.provider == ProviderId.CLAUDE }.reason)
    }

    @Test
    fun timeoutFallsBack() = runTest {
        val claude = FakeProvider(ProviderId.CLAUDE, delayMillis = 60_000)
        val device = FakeProvider(ProviderId.ON_DEVICE)
        val r = AiRouter(mapOf(ProviderId.CLAUDE to claude, ProviderId.ON_DEVICE to device), { allCloudOn }, { 0L }, cloudTimeoutMillis = 1_000)
            .run(AiTask.ClassifyImage, AiInput(text = "x"))
        assertEquals(ProviderId.ON_DEVICE, r.source)
        assertEquals(AiError.Timeout, r.attempts.first().error)
    }

    @Test
    fun forcedMockForDemo() = runTest {
        val mock = FakeProvider(ProviderId.MOCK)
        val device = FakeProvider(ProviderId.ON_DEVICE)
        val r = router(allCloudOn, mock, device).run(AiTask.ClassifyImage, AiInput(), force = ProviderId.MOCK)
        assertEquals(ProviderId.MOCK, r.source)
        assertEquals(Provenance.DEMO, r.provenance)
    }
}
