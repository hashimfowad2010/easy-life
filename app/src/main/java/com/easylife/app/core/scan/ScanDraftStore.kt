package com.easylife.app.core.scan

import android.content.Context
import android.net.Uri
import com.easylife.app.core.ocr.OcrDocument
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A scan in progress. Saved as a small file in the app's private cache so a scan survives process death;
 * photos are deleted as soon as the result is saved (we never keep bill or meter photos).
 */
@Serializable
data class ScanDraft(
    val id: String,
    val imagePaths: List<String> = emptyList(),
    /** Fictional sample name (sample_lesco_bill, ...) or "shared" for text shared from another app. */
    val sampleName: String? = null,
    val text: String? = null,
    val ocr: OcrDocument? = null,
    val kind: String? = null,
    val kindSource: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
) {
    val isSample: Boolean get() = sampleName != null && sampleName != SHARED

    companion object {
        const val SHARED = "shared"
    }
}

@Singleton
class ScanDraftStore @Inject constructor(@ApplicationContext private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true }
    private val dir: File get() = File(context.cacheDir, "scan_drafts").apply { mkdirs() }

    suspend fun createFromImages(uris: List<Uri>): ScanDraft = withContext(Dispatchers.IO) {
        val id = UUID.randomUUID().toString().take(12)
        val paths = uris.mapIndexedNotNull { i, uri ->
            runCatching {
                val target = File(dir, "${id}_$i.jpg")
                context.contentResolver.openInputStream(uri)!!.use { input -> target.outputStream().use { input.copyTo(it) } }
                target.absolutePath
            }.getOrNull()
        }
        ScanDraft(id = id, imagePaths = paths).also { save(it) }
    }

    suspend fun createFromText(text: String, sampleName: String?): ScanDraft = withContext(Dispatchers.IO) {
        ScanDraft(id = UUID.randomUUID().toString().take(12), sampleName = sampleName, text = text, ocr = OcrDocument.fromPlainText(text))
            .also { save(it) }
    }

    suspend fun createSample(sampleName: String): ScanDraft {
        val text = withContext(Dispatchers.IO) { context.assets.open("demo/$sampleName.txt").bufferedReader().use { it.readText() } }
        return createFromText(text, sampleName)
    }

    suspend fun get(id: String): ScanDraft? = withContext(Dispatchers.IO) {
        val f = File(dir, "$id.json")
        if (!f.exists()) null else runCatching { json.decodeFromString(ScanDraft.serializer(), f.readText()) }.getOrNull()
    }

    suspend fun save(draft: ScanDraft) = withContext(Dispatchers.IO) {
        File(dir, "${draft.id}.json").writeText(json.encodeToString(ScanDraft.serializer(), draft))
    }

    /** Remove the draft and its photos (called after saving the result, and for old drafts). */
    suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        dir.listFiles { f -> f.name.startsWith(id) }?.forEach { it.delete() }
    }

    suspend fun cleanupOlderThan(hours: Long = 24) = withContext(Dispatchers.IO) {
        val cutoff = System.currentTimeMillis() - hours * 3_600_000
        dir.listFiles()?.filter { it.lastModified() < cutoff }?.forEach { it.delete() }
    }

    suspend fun deleteAll() = withContext(Dispatchers.IO) { dir.deleteRecursively() }
}
