package com.easylife.app.core.ai

import com.easylife.app.core.ai.providers.ClaudeProvider
import com.easylife.app.core.ai.providers.CloudAiClient
import com.easylife.app.core.ai.providers.CloudAiException
import com.easylife.app.core.ai.providers.CloudAiRequest
import com.easylife.app.core.ai.providers.CloudStatus
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** ClaudeProvider against a fake server (the real Cloud Function is not deployed until funding). */
class ClaudeProviderTest {

    private class FakeServer(var reply: () -> String) : CloudAiClient {
        val requests = mutableListOf<CloudAiRequest>()
        override suspend fun call(request: CloudAiRequest): String {
            requests += request
            return reply()
        }
    }

    private val online = object : CloudStatus {
        override val isConfigured = true
        override fun isOnline() = true
    }

    @Test
    fun billFromImageIsValidated() = runTest {
        val server = FakeServer {
            """{"billType":"electricity","provider":"LESCO","dueDate":"2026-10-23","amountWithinDue":6407,
               "amountAfterDue":6945,"unitsConsumed":186,"previousReading":10425,"presentReading":10611,
               "consumerStatusPrinted":"non_protected","history":[{"month":"2026-07","units":201,"amount":7320}],
               "confidence":0.93,"uncertainFields":[]}"""
        }
        val provider = ClaudeProvider(server, online) { "BASE64JPEG" }
        val r = provider.run(AiTask.BillExtract, AiInput(imagePaths = listOf("/tmp/page1.jpg"), lang = com.easylife.app.core.i18n.AppLanguage.UR))
        assertTrue(r.ok)
        assertEquals(ProviderId.CLAUDE, r.source)
        assertEquals(186, r.value!!.unitsConsumed)
        val sent = server.requests.single()
        assertEquals("bill_extract", sent.task)
        assertEquals(listOf("BASE64JPEG"), sent.images)
        assertEquals(null, sent.payload, "image tasks send the redacted image, not OCR text")
        assertEquals("ur", sent.lang)
    }

    @Test
    fun serverErrorsMapToRouterErrors() = runTest {
        val server = FakeServer { throw CloudAiException("RESOURCE_EXHAUSTED", "daily limit") }
        val provider = ClaudeProvider(server, online) { "x" }
        assertEquals(AiError.RateLimited, provider.run(AiTask.Translate, AiInput(text = "hello")).error)
        server.reply = { throw CloudAiException("DEADLINE_EXCEEDED", "slow") }
        assertEquals(AiError.Timeout, provider.run(AiTask.Translate, AiInput(text = "hello")).error)
    }

    @Test
    fun malformedServerJsonIsRejected() = runTest {
        val provider = ClaudeProvider(FakeServer { "not json at all" }, online) { "x" }
        assertIs<AiError.InvalidJson>(provider.run(AiTask.Translate, AiInput(text = "hello")).error)
    }

    @Test
    fun unavailableWithoutFirebase() = runTest {
        val offline = object : CloudStatus {
            override val isConfigured = false
            override fun isOnline() = false
        }
        assertFalse(ClaudeProvider(FakeServer { "{}" }, offline) { "x" }.isAvailable(AiTask.Translate).available)
    }
}
