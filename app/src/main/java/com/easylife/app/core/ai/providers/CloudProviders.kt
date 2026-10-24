package com.easylife.app.core.ai.providers

import com.easylife.app.core.ai.AiError
import com.easylife.app.core.ai.AiInput
import com.easylife.app.core.ai.AiProvider
import com.easylife.app.core.ai.AiResult
import com.easylife.app.core.ai.AiTask
import com.easylife.app.core.ai.Availability
import com.easylife.app.core.ai.CloudImagePreparer
import com.easylife.app.core.ai.CloudJson
import com.easylife.app.core.ai.ProviderId
import com.easylife.app.core.cloud.FirebaseGateway
import com.easylife.app.core.cloud.RemoteConfigRepository
import com.easylife.app.core.data.prefs.PrefsRepository
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.QuotaExceededException
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import com.google.firebase.functions.FirebaseFunctionsException
import com.google.firebase.functions.functions
import kotlinx.coroutines.tasks.await
import kotlinx.serialization.Serializable
import java.io.IOException
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/** Network/Firebase readiness, as an interface so cloud providers are unit-testable. */
interface CloudStatus {
    val isConfigured: Boolean
    fun isOnline(): Boolean
}

class FirebaseCloudStatus @Inject constructor(private val firebase: FirebaseGateway) : CloudStatus {
    override val isConfigured: Boolean get() = firebase.isConfigured
    override fun isOnline(): Boolean = firebase.isOnline()
}

/** Turns a page photo into a redacted, downscaled JPEG (base64) for a cloud provider. */
fun interface ImageEncoder {
    suspend fun encodeForCloud(imageRef: String): String
}

class RedactingImageEncoder @Inject constructor(private val preparer: CloudImagePreparer) : ImageEncoder {
    override suspend fun encodeForCloud(imageRef: String): String = preparer.toJpegBase64(preparer.prepare(imageRef).bitmap)
}

// ------------------------------------------------------------------------------------------------ Gemini (free tier)

/** Smart AI calls per phone per day. The free Gemini quota is shared by every phone in the Firebase project. */
interface DailyUsage {
    suspend fun usedToday(): Int
    suspend fun countOne()
}

class PrefsDailyUsage @Inject constructor(private val prefs: PrefsRepository) : DailyUsage {
    override suspend fun usedToday(): Int = prefs.geminiCallsOn(LocalDate.now().toString())
    override suspend fun countOne() {
        prefs.countGeminiCall(LocalDate.now().toString())
    }
}

/**
 * Smart AI: Gemini Developer API through Firebase AI Logic, free tier (the project stays on the no-cost Spark plan,
 * so it can never be billed). Switched on by Remote Config `gemini_enabled` (default true), then by the user in
 * Settings → Smart AI. Never used for SMS/budget data (the router enforces this, and [supports] refuses sensitive
 * tasks too). Photos have numbers, names and addresses blacked out first; at most `gemini_daily_limit` calls a day.
 */
@Singleton
class GeminiFreeProvider @Inject constructor(
    private val status: CloudStatus,
    private val config: RemoteConfigRepository,
    private val preparer: CloudImagePreparer,
    private val usage: DailyUsage,
) : AiProvider {
    override val id = ProviderId.GEMINI_FREE

    override fun supports(task: AiTask<*>): Boolean = !task.sensitive

    override suspend fun isAvailable(task: AiTask<*>): Availability {
        val limit = config.current.geminiDailyLimit
        return when {
            !status.isConfigured -> Availability.no("Firebase not connected in this build")
            !status.isOnline() -> Availability.no("no internet")
            usage.usedToday() >= limit -> Availability.no("today's Smart AI limit on this phone is used ($limit)")
            else -> Availability.Yes
        }
    }

    override suspend fun <R : Any> run(task: AiTask<R>, input: AiInput): AiResult<R> {
        if (task.sensitive) return AiResult.failure(id, AiError.NotSupported)
        val cfg = config.current
        val sendImages = task.usesImage && input.imagePaths.isNotEmpty()
        val userPrompt = cfg.prompts.userPrompt(
            task.key,
            if (sendImages) null else input.text,
            input.params + ("language" to input.lang.tag),
        ) ?: return AiResult.failure(id, AiError.NotSupported)
        // Every attempt uses the shared free quota, so it counts even if it fails.
        usage.countOne()
        return try {
            val bitmaps = if (sendImages) input.imagePaths.take(3).map { preparer.prepare(it, hidePersonal = true).bitmap } else emptyList()
            val model = Firebase.ai(backend = GenerativeBackend.googleAI()).generativeModel(
                modelName = cfg.geminiModel,
                generationConfig = generationConfig { responseMimeType = "application/json" },
            )
            val response = model.generateContent(
                content {
                    bitmaps.forEach { image(it) }
                    text(cfg.prompts.system + "\n\n" + userPrompt)
                },
            )
            CloudJson.decode(task, response.text, id)
        } catch (e: QuotaExceededException) {
            AiResult.failure(id, AiError.RateLimited)
        } catch (e: IOException) {
            AiResult.failure(id, AiError.Network)
        } catch (e: Exception) {
            val msg = e.message.orEmpty()
            AiResult.failure(id, if (msg.contains("429") || msg.contains("RESOURCE_EXHAUSTED")) AiError.RateLimited else AiError.Failed(e.javaClass.simpleName))
        }
    }
}

// ------------------------------------------------------------------------------------------------ Claude (after funding)

@Serializable
data class CloudAiRequest(
    val task: String,
    val payload: String? = null,
    val params: Map<String, String> = emptyMap(),
    /** Base64 JPEGs (already redacted on the phone). The first one is also sent as imageBase64. */
    val images: List<String> = emptyList(),
    val lang: String = "en",
)

class CloudAiException(val code: String, message: String) : Exception(message)

/** The server function that holds the Anthropic key (spec section 9). No API key ever lives in the app. */
interface CloudAiClient {
    /** Returns the raw JSON produced by the model (validated again on the phone). */
    suspend fun call(request: CloudAiRequest): String
}

@Singleton
class FunctionsCloudAiClient @Inject constructor(private val firebase: FirebaseGateway) : CloudAiClient {
    override suspend fun call(request: CloudAiRequest): String {
        firebase.ensureSignedIn() ?: throw CloudAiException("unauthenticated", "not signed in")
        val data = hashMapOf<String, Any?>(
            "task" to request.task,
            "payload" to request.payload,
            "params" to request.params,
            "imageBase64" to request.images.firstOrNull(),
            "images" to request.images,
            "lang" to request.lang,
        )
        try {
            val result = Firebase.functions.getHttpsCallable("ai").call(data).await()
            val map = result.getData() as? Map<*, *> ?: throw CloudAiException("invalid", "unexpected reply")
            return map["json"] as? String ?: throw CloudAiException("invalid", "no json in reply")
        } catch (e: FirebaseFunctionsException) {
            throw CloudAiException(e.code.name, e.message ?: e.code.name)
        }
    }
}

/**
 * Smart AI via Claude: calls the Cloud Function `ai`. Fully implemented and unit-tested against a fake server,
 * switched off (Remote Config `claude_enabled=false`) until funding.
 */
@Singleton
class ClaudeProvider @Inject constructor(
    private val client: CloudAiClient,
    private val status: CloudStatus,
    private val images: ImageEncoder,
) : AiProvider {
    override val id = ProviderId.CLAUDE

    override fun supports(task: AiTask<*>): Boolean = true

    override suspend fun isAvailable(task: AiTask<*>): Availability = when {
        !status.isConfigured -> Availability.no("Firebase not connected in this build")
        !status.isOnline() -> Availability.no("no internet")
        else -> Availability.Yes
    }

    override suspend fun <R : Any> run(task: AiTask<R>, input: AiInput): AiResult<R> {
        val sendImages = task.usesImage && input.imagePaths.isNotEmpty()
        return try {
            val request = CloudAiRequest(
                task = task.key,
                payload = if (sendImages) null else input.text,
                params = input.params,
                images = if (sendImages) input.imagePaths.take(3).map { images.encodeForCloud(it) } else emptyList(),
                lang = input.lang.tag,
            )
            CloudJson.decode(task, client.call(request), id)
        } catch (e: CloudAiException) {
            AiResult.failure(id, mapCode(e.code))
        } catch (e: IOException) {
            AiResult.failure(id, AiError.Network)
        }
    }

    companion object {
        fun mapCode(code: String): AiError = when (code.uppercase()) {
            "RESOURCE_EXHAUSTED" -> AiError.RateLimited
            "DEADLINE_EXCEEDED" -> AiError.Timeout
            "UNAVAILABLE" -> AiError.Network
            "UNAUTHENTICATED", "PERMISSION_DENIED" -> AiError.Unavailable("not signed in")
            "INVALID" -> AiError.InvalidJson("server reply")
            else -> AiError.Failed(code)
        }
    }
}
