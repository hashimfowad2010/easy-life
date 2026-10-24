package com.easylife.app.core.cloud

import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AppConfigParserTest {

    private fun asset(path: String) = File("src/main/assets/$path").readText()

    private val minimalPrompts = """{"version":"test","system":"sys","tasks":{"bill_extract":{"instruction":"do it"}}}"""

    /** The real bundled scalars (gemini_enabled, gemini_model, gemini_daily_limit, claude_enabled). */
    private val scalars: Map<String, String> =
        Json.decodeFromString(MapSerializer(String.serializer(), String.serializer()), asset("config/scalars.json"))

    private val defaults = scalars + mapOf(
        AppConfig.TARIFFS to asset("config/tariffs.json"),
        AppConfig.AI_ROUTING to asset("config/ai_routing.json"),
        AppConfig.CLAUDE_MODELS to asset("config/claude_models.json"),
        AppConfig.AI_PROMPTS to minimalPrompts,
        AppConfig.RETAILERS to asset("config/retailers.json"),
        AppConfig.FEATURE_FLAGS to asset("config/feature_flags.json"),
    )

    @Test
    fun bundledDefaultsParse() {
        val c = AppConfigParser.parse(defaults, defaults, "bundled")
        assertTrue(c.geminiEnabled, "Gemini (free) is the Smart AI; each user still has to turn it on")
        assertFalse(c.claudeEnabled, "Claude is the optional paid path")
        assertEquals(15, c.geminiDailyLimit)
        assertEquals(200, c.tariffs.protectedThreshold)
        assertEquals(listOf("on_device"), c.aiRouting["sms_parse"], "bank SMS never go to a cloud AI")
        assertEquals(listOf("on_device"), c.aiRouting["weekly_summary"], "runs on every Today visit: keep it off the shared quota")
        assertTrue(c.aiRouting.values.none { "claude" in it })
        assertTrue(AppConfig.ALL_KEYS.all { it in defaults }, "every Remote Config key has a bundled default")
        assertTrue(c.retailers.retailers.any { it.id == "alfatah" && it.grocery })
        assertTrue(c.invalidKeys.isEmpty())
    }

    @Test
    fun invalidRemoteValueFallsBackToDefault() {
        val remote = defaults + mapOf(
            AppConfig.TARIFFS to "{broken",
            AppConfig.CLAUDE_ENABLED to "maybe",
            AppConfig.GEMINI_ENABLED to "false",
            AppConfig.GEMINI_DAILY_LIMIT to "lots",
        )
        val c = AppConfigParser.parse(remote, defaults, "remote")
        assertEquals(200, c.tariffs.protectedThreshold)
        assertFalse(c.claudeEnabled)
        assertFalse(c.geminiEnabled)
        assertEquals(15, c.geminiDailyLimit)
        assertEquals(setOf(AppConfig.TARIFFS, AppConfig.CLAUDE_ENABLED, AppConfig.GEMINI_DAILY_LIMIT), c.invalidKeys.toSet())
    }

    @Test
    fun retailerSearchUrlIsEncoded() {
        val r = Retailer("x", "X", "https://example.com/search?q={q}")
        assertEquals("https://example.com/search?q=basmati+rice", r.searchUrlFor("basmati rice"))
    }
}
