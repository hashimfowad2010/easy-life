package com.easylife.app.core.ai

import com.easylife.app.core.i18n.AppLanguage
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.TranslateRemoteModel
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

enum class ModelState { UNKNOWN, NOT_DOWNLOADED, DOWNLOADING, READY, FAILED }

/** English <-> Urdu translation on the phone (ML Kit). The Urdu model (~30 MB) is downloaded once. */
interface OnDeviceTranslator {
    val state: StateFlow<ModelState>
    suspend fun refreshState(): ModelState
    suspend fun download(wifiOnly: Boolean)
    suspend fun delete()
    suspend fun translate(text: String, from: AppLanguage, to: AppLanguage): String
}

@Singleton
class MlKitTranslator @Inject constructor() : OnDeviceTranslator {

    private val _state = MutableStateFlow(ModelState.UNKNOWN)
    override val state: StateFlow<ModelState> = _state.asStateFlow()

    private val mutex = Mutex()
    private var enUr: Translator? = null
    private var urEn: Translator? = null

    private fun client(from: AppLanguage, to: AppLanguage): Translator {
        val options = TranslatorOptions.Builder()
            .setSourceLanguage(if (from == AppLanguage.UR) TranslateLanguage.URDU else TranslateLanguage.ENGLISH)
            .setTargetLanguage(if (to == AppLanguage.UR) TranslateLanguage.URDU else TranslateLanguage.ENGLISH)
            .build()
        return if (from == AppLanguage.EN) {
            enUr ?: Translation.getClient(options).also { enUr = it }
        } else {
            urEn ?: Translation.getClient(options).also { urEn = it }
        }
    }

    override suspend fun refreshState(): ModelState {
        if (_state.value == ModelState.DOWNLOADING) return ModelState.DOWNLOADING
        val downloaded = runCatching {
            RemoteModelManager.getInstance().getDownloadedModels(TranslateRemoteModel::class.java).await()
        }.getOrNull()
        val ready = downloaded?.any { it.language == TranslateLanguage.URDU } == true
        _state.value = if (ready) ModelState.READY else ModelState.NOT_DOWNLOADED
        return _state.value
    }

    override suspend fun download(wifiOnly: Boolean) {
        _state.value = ModelState.DOWNLOADING
        val conditions = DownloadConditions.Builder().apply { if (wifiOnly) requireWifi() }.build()
        try {
            client(AppLanguage.EN, AppLanguage.UR).downloadModelIfNeeded(conditions).await()
            client(AppLanguage.UR, AppLanguage.EN).downloadModelIfNeeded(conditions).await()
            _state.value = ModelState.READY
        } catch (e: Exception) {
            _state.value = ModelState.FAILED
            throw e
        }
    }

    override suspend fun delete() {
        runCatching {
            RemoteModelManager.getInstance().deleteDownloadedModel(TranslateRemoteModel.Builder(TranslateLanguage.URDU).build()).await()
        }
        enUr?.close(); urEn?.close()
        enUr = null; urEn = null
        _state.value = ModelState.NOT_DOWNLOADED
    }

    /** Translates paragraph by paragraph so line breaks (lists, addresses) are kept. */
    override suspend fun translate(text: String, from: AppLanguage, to: AppLanguage): String = mutex.withLock {
        if (from == to) return text
        val translator = client(from, to)
        val out = mutableListOf<String>()
        for (line in text.split("\n")) {
            out += if (line.none { it.isLetter() }) line else translator.translate(line).await()
        }
        out.joinToString("\n")
    }
}
