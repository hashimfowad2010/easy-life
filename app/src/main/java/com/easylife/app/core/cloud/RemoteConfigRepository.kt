package com.easylife.app.core.cloud

import android.content.Context
import com.easylife.app.BuildConfig
import com.easylife.app.core.devtools.ErrorLog
import com.google.firebase.Firebase
import com.google.firebase.remoteconfig.remoteConfig
import com.google.firebase.remoteconfig.remoteConfigSettings
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Remote Config with bundled defaults for every key (assets/config/*.json and assets/ai/prompts.json).
 * Tariffs, AI routing, prompts and retailer lists can change without an app update.
 */
@Singleton
class RemoteConfigRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val firebase: FirebaseGateway,
    private val errorLog: ErrorLog,
) {
    val defaults: Map<String, String> by lazy { loadBundledDefaults() }

    private val _config = MutableStateFlow(AppConfigParser.parse(defaults, defaults, "bundled"))
    val config: StateFlow<AppConfig> = _config.asStateFlow()

    @Volatile
    var lastFetch: Instant? = null
        private set

    val current: AppConfig get() = _config.value

    suspend fun refresh(force: Boolean = false) {
        if (!firebase.isConfigured) return
        runCatching {
            val rc = Firebase.remoteConfig
            rc.setConfigSettingsAsync(
                remoteConfigSettings {
                    minimumFetchIntervalInSeconds = if (force || BuildConfig.DEBUG) 0 else 3_600
                },
            ).await()
            rc.setDefaultsAsync(defaults).await()
            rc.fetchAndActivate().await()
            val values = AppConfig.ALL_KEYS.associateWith { rc.getString(it) }
            val parsed = AppConfigParser.parse(values, defaults, "remote")
            if (parsed.invalidKeys.isNotEmpty()) errorLog.note("remote-config", "invalid keys: ${parsed.invalidKeys}")
            _config.value = parsed
            lastFetch = Instant.now()
        }.onFailure { errorLog.record("remote-config-fetch", it) }
    }

    private fun loadBundledDefaults(): Map<String, String> {
        fun asset(path: String) = context.assets.open(path).bufferedReader().use { it.readText() }
        val scalars = Json.decodeFromString(MapSerializer(String.serializer(), String.serializer()), asset("config/scalars.json"))
        return scalars + mapOf(
            AppConfig.TARIFFS to asset("config/tariffs.json"),
            AppConfig.AI_ROUTING to asset("config/ai_routing.json"),
            AppConfig.CLAUDE_MODELS to asset("config/claude_models.json"),
            AppConfig.AI_PROMPTS to asset("ai/prompts.json"),
            AppConfig.RETAILERS to asset("config/retailers.json"),
            AppConfig.FEATURE_FLAGS to asset("config/feature_flags.json"),
        )
    }
}
