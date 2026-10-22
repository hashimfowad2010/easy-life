package com.easylife.app.core.speech

import android.content.Context
import android.content.Intent
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.easylife.app.core.i18n.AppLanguage
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

enum class VoiceStatus { READY, MISSING_DATA, NOT_SUPPORTED, NO_ENGINE }

/**
 * Read-aloud with Android TextToSpeech. Urdu uses the ur-PK voice if installed; if it is missing the UI shows a
 * one-time guide to install it (Google's "Speech Services" offers Urdu) and nothing crashes.
 */
@Singleton
class Tts @Inject constructor(@ApplicationContext private val context: Context) {

    private var tts: TextToSpeech? = null
    private var ready: CompletableDeferred<Boolean>? = null
    private val _speaking = MutableStateFlow(false)
    val speaking: StateFlow<Boolean> = _speaking.asStateFlow()

    private suspend fun engine(): TextToSpeech? {
        tts?.let { if (ready?.await() == true) return it }
        val deferred = CompletableDeferred<Boolean>()
        ready = deferred
        val t = TextToSpeech(context) { status -> deferred.complete(status == TextToSpeech.SUCCESS) }
        t.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) { _speaking.value = true }
            override fun onDone(utteranceId: String?) { _speaking.value = false }
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) { _speaking.value = false }
        })
        tts = t
        return if (deferred.await()) t else null
    }

    private fun locale(lang: AppLanguage): Locale = if (lang == AppLanguage.UR) Locale("ur", "PK") else Locale.US

    suspend fun status(lang: AppLanguage): VoiceStatus {
        val t = engine() ?: return VoiceStatus.NO_ENGINE
        return when (t.isLanguageAvailable(locale(lang))) {
            TextToSpeech.LANG_MISSING_DATA -> VoiceStatus.MISSING_DATA
            TextToSpeech.LANG_NOT_SUPPORTED -> {
                // Some engines only know plain "ur".
                if (lang == AppLanguage.UR && t.isLanguageAvailable(Locale("ur")) >= TextToSpeech.LANG_AVAILABLE) VoiceStatus.READY else VoiceStatus.NOT_SUPPORTED
            }
            else -> VoiceStatus.READY
        }
    }

    /** Speaks [text]; returns the voice status so the UI can show the install guide if needed. */
    suspend fun speak(text: String, lang: AppLanguage): VoiceStatus {
        val t = engine() ?: return VoiceStatus.NO_ENGINE
        val st = status(lang)
        if (st != VoiceStatus.READY) return st
        val loc = if (lang == AppLanguage.UR && t.isLanguageAvailable(Locale("ur", "PK")) < TextToSpeech.LANG_AVAILABLE) Locale("ur") else locale(lang)
        t.language = loc
        t.setSpeechRate(if (lang == AppLanguage.UR) 0.9f else 1.0f)
        // Long texts are split so the engine doesn't refuse them.
        val chunks = text.split(Regex("""(?<=[.!?۔\n])""")).map { it.trim() }.filter { it.isNotEmpty() }.flatMap { it.chunked(TextToSpeech.getMaxSpeechInputLength() - 10) }
        chunks.forEachIndexed { i, chunk ->
            t.speak(chunk, if (i == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD, null, "easylife-$i")
        }
        return VoiceStatus.READY
    }

    fun stop() {
        tts?.stop()
        _speaking.value = false
    }

    /** Opens the system screen to install voice data (Urdu). */
    fun installVoiceIntent(): Intent = Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}
