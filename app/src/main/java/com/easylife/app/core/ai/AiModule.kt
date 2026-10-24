package com.easylife.app.core.ai

import com.easylife.app.core.ai.providers.ClaudeProvider
import com.easylife.app.core.ai.providers.CloudAiClient
import com.easylife.app.core.ai.providers.CloudStatus
import com.easylife.app.core.ai.providers.DailyUsage
import com.easylife.app.core.ai.providers.FirebaseCloudStatus
import com.easylife.app.core.ai.providers.FunctionsCloudAiClient
import com.easylife.app.core.ai.providers.GeminiFreeProvider
import com.easylife.app.core.ai.providers.ImageEncoder
import com.easylife.app.core.ai.providers.MockProvider
import com.easylife.app.core.ai.providers.OnDeviceProvider
import com.easylife.app.core.ai.providers.PrefsDailyUsage
import com.easylife.app.core.ai.providers.RedactingImageEncoder
import com.easylife.app.core.cloud.RemoteConfigRepository
import com.easylife.app.core.data.prefs.PrefsRepository
import com.easylife.app.core.impact.ImpactTracker
import com.easylife.app.core.ocr.MlKitOcrEngine
import com.easylife.app.core.ocr.OcrEngine
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton

/** Remote Config + the user's Smart AI switch and consents, as one snapshot for the router. */
@Singleton
class AiSettingsRepository @Inject constructor(
    private val config: RemoteConfigRepository,
    private val prefs: PrefsRepository,
) : AiSettingsSource {
    override suspend fun snapshot(): AiSettings {
        val c = config.current
        val p = prefs.current()
        return AiSettings(
            routing = c.aiRouting,
            geminiEnabled = c.geminiEnabled,
            claudeEnabled = c.claudeEnabled,
            smartAiEnabled = p.smartAiEnabled,
            geminiConsent = p.geminiConsentAt != null,
            claudeConsent = p.claudeConsentAt != null,
        )
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class AiBindings {
    @Binds abstract fun ocrEngine(impl: MlKitOcrEngine): OcrEngine

    @Binds abstract fun translator(impl: MlKitTranslator): OnDeviceTranslator

    @Binds abstract fun cloudClient(impl: FunctionsCloudAiClient): CloudAiClient

    @Binds abstract fun cloudStatus(impl: FirebaseCloudStatus): CloudStatus

    @Binds abstract fun imageEncoder(impl: RedactingImageEncoder): ImageEncoder

    @Binds abstract fun settingsSource(impl: AiSettingsRepository): AiSettingsSource

    @Binds abstract fun dailyUsage(impl: PrefsDailyUsage): DailyUsage
}

@Module
@InstallIn(SingletonComponent::class)
object AiModule {
    @Provides
    @Singleton
    fun router(
        onDevice: OnDeviceProvider,
        gemini: GeminiFreeProvider,
        claude: ClaudeProvider,
        mock: MockProvider,
        settings: AiSettingsSource,
        impact: ImpactTracker,
    ): AiRouter = AiRouter(
        providers = mapOf(
            ProviderId.ON_DEVICE to onDevice,
            ProviderId.GEMINI_FREE to gemini,
            ProviderId.CLAUDE to claude,
            ProviderId.MOCK to mock,
        ),
        settings = settings,
        listener = impact,
    )
}
