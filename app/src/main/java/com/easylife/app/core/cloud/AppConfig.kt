package com.easylife.app.core.cloud

import com.easylife.app.core.ai.PromptBundle
import com.easylife.app.core.limits.TariffConfig
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

@Serializable
data class Retailer(
    val id: String,
    val name: String,
    val searchUrl: String,
    val domains: List<String> = emptyList(),
    val grocery: Boolean = true,
    val verifiedOn: String? = null,
) {
    fun searchUrlFor(query: String): String =
        searchUrl.replace("{q}", URLEncoder.encode(query.trim(), StandardCharsets.UTF_8.name()))
}

@Serializable
data class RetailerConfig(val version: String = "", val note: String = "", val retailers: List<Retailer> = emptyList())

/** Typed view of all Remote Config keys. Every key has a bundled default, so the app works offline. */
data class AppConfig(
    val tariffs: TariffConfig,
    val aiRouting: Map<String, List<String>>,
    val geminiEnabled: Boolean,
    val geminiModel: String,
    /** Smart AI (Gemini) calls allowed per phone per day, so one phone can't use up the project's shared free quota. */
    val geminiDailyLimit: Int,
    val claudeEnabled: Boolean,
    val claudeModels: Map<String, String>,
    val prompts: PromptBundle,
    val retailers: RetailerConfig,
    val featureFlags: Map<String, Boolean>,
    /** "bundled" or "remote". */
    val source: String,
    /** Keys whose remote value was invalid and fell back to the bundled default. */
    val invalidKeys: List<String> = emptyList(),
) {
    fun flag(name: String, default: Boolean = false): Boolean = featureFlags[name] ?: default

    companion object {
        const val TARIFFS = "tariffs"
        const val AI_ROUTING = "ai_routing"
        const val GEMINI_ENABLED = "gemini_enabled"
        const val GEMINI_MODEL = "gemini_model"
        const val GEMINI_DAILY_LIMIT = "gemini_daily_limit"
        const val CLAUDE_ENABLED = "claude_enabled"
        const val CLAUDE_MODELS = "claude_models"
        const val AI_PROMPTS = "ai_prompts"
        const val RETAILERS = "retailers"
        const val FEATURE_FLAGS = "feature_flags"

        val ALL_KEYS = listOf(TARIFFS, AI_ROUTING, GEMINI_ENABLED, GEMINI_MODEL, GEMINI_DAILY_LIMIT, CLAUDE_ENABLED, CLAUDE_MODELS, AI_PROMPTS, RETAILERS, FEATURE_FLAGS)
    }
}

/** Pure parser so the bundled defaults and any remote values are unit-tested. */
object AppConfigParser {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val routingSerializer = MapSerializer(String.serializer(), ListSerializer(String.serializer()))
    private val stringMap = MapSerializer(String.serializer(), String.serializer())
    private val boolMap = MapSerializer(String.serializer(), Boolean.serializer())

    /**
     * @param values the values to use (remote, or bundled)
     * @param defaults bundled defaults, used for any key that is missing or invalid in [values]
     */
    fun parse(values: Map<String, String>, defaults: Map<String, String>, source: String): AppConfig {
        val invalid = mutableListOf<String>()
        fun <T> pick(key: String, decode: (String) -> T): T {
            val v = values[key]
            if (!v.isNullOrBlank()) {
                runCatching { return decode(v) }.onFailure { invalid += key }
            }
            return decode(defaults.getValue(key))
        }
        return AppConfig(
            tariffs = pick(AppConfig.TARIFFS) { json.decodeFromString(TariffConfig.serializer(), it) },
            aiRouting = pick(AppConfig.AI_ROUTING) { json.decodeFromString(routingSerializer, it) },
            geminiEnabled = pick(AppConfig.GEMINI_ENABLED) { parseBool(it) },
            geminiModel = pick(AppConfig.GEMINI_MODEL) { it.trim().also { m -> require(m.isNotEmpty()) } },
            geminiDailyLimit = pick(AppConfig.GEMINI_DAILY_LIMIT) { it.trim().toInt().also { n -> require(n in 0..500) } },
            claudeEnabled = pick(AppConfig.CLAUDE_ENABLED) { parseBool(it) },
            claudeModels = pick(AppConfig.CLAUDE_MODELS) { json.decodeFromString(stringMap, it) },
            prompts = pick(AppConfig.AI_PROMPTS) { PromptBundle.parse(it) },
            retailers = pick(AppConfig.RETAILERS) { json.decodeFromString(RetailerConfig.serializer(), it) },
            featureFlags = pick(AppConfig.FEATURE_FLAGS) { json.decodeFromString(boolMap, it) },
            source = source,
            invalidKeys = invalid,
        )
    }

    private fun parseBool(v: String): Boolean = when (v.trim().lowercase()) {
        "true", "1", "yes" -> true
        "false", "0", "no" -> false
        else -> throw IllegalArgumentException("not a boolean: $v")
    }
}
