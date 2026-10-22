package com.easylife.app.core.cloud

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AppConfigParserTest {

    private fun asset(path: String) = File("src/main/assets/$path").readText()

    private val minimalPrompts = """{"version":"test","system":"sys","tasks":{"bill_extract":{"instruction":"do it"}}}"""

    private val defaults = mapOf(
        AppConfig.TARIFFS to asset("config/tariffs.json"),
        AppConfig.AI_ROUTING to asset("config/ai_routing.json"),
        AppConfig.CLAUDE_MODELS to asset("config/claude_models.json"),
        AppConfig.AI_PROMPTS to minimalPrompts,
        AppConfig.RETAILERS to asset("config/retailers.json"),
        AppConfig.FEATURE_FLAGS to asset("config/feature_flags.json"),
        AppConfig.GEMINI_ENABLED to "false",
        AppConfig.GEMINI_MODEL to "gemini-3.8-flash",
        AppConfig.CLAUDE_ENABLED to "false",
    )

    @Test
    fun bundledDefaultsParse() {
        val c = AppConfigParser.parse(defaults, defaults, "bundled")
        assertFalse(c.geminiEnabled, "Gemini must be off by default")
        assertFalse(c.claudeEnabled, "Claude must be off until funding")
        assertEquals(200, c.tariffs.protectedThreshold)
        assertEquals(listOf("on_device", "claude"), c.aiRouting["sms_parse"])
        assertTrue(c.retailers.retailers.any { it.id == "alfatah" && it.grocery })
        assertTrue(c.invalidKeys.isEmpty())
    }

    @Test
    fun invalidRemoteValueFallsBackToDefault() {
        val remote = defaults + mapOf(AppConfig.TARIFFS to "{broken", AppConfig.CLAUDE_ENABLED to "maybe", AppConfig.GEMINI_ENABLED to "true")
        val c = AppConfigParser.parse(remote, defaults, "remote")
        assertEquals(200, c.tariffs.protectedThreshold)
        assertFalse(c.claudeEnabled)
        assertTrue(c.geminiEnabled)
        assertEquals(setOf(AppConfig.TARIFFS, AppConfig.CLAUDE_ENABLED), c.invalidKeys.toSet())
    }

    @Test
    fun retailerSearchUrlIsEncoded() {
        val r = Retailer("x", "X", "https://example.com/search?q={q}")
        assertEquals("https://example.com/search?q=basmati+rice", r.searchUrlFor("basmati rice"))
    }
}
