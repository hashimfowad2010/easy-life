package com.easylife.app.core.devtools

import android.content.Context
import android.os.Build
import com.easylife.app.BuildConfig
import com.easylife.app.core.ai.Masker
import com.google.firebase.FirebaseApp
import com.google.firebase.crashlytics.FirebaseCrashlytics
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Keeps the last errors and crashes on the phone for "Copy error report" (Developer tools).
 * Messages are masked so no personal numbers end up in a report. Crashlytics also gets crashes when Firebase
 * is connected.
 */
@Singleton
class ErrorLog @Inject constructor(@ApplicationContext private val context: Context) {

    private val file: File get() = File(context.filesDir, "error_log.txt")
    private val lock = Any()

    fun installCrashHandler() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { append("CRASH", "uncaught on ${thread.name}", error) }
            previous?.uncaughtException(thread, error)
        }
    }

    /** Record a handled error (also sent to Crashlytics as non-fatal when available). */
    fun record(tag: String, error: Throwable) {
        runCatching { append("ERROR", tag, error) }
        if (firebaseReady()) runCatching { FirebaseCrashlytics.getInstance().recordException(error) }
    }

    fun note(tag: String, message: String) {
        runCatching { appendText("NOTE", tag, Masker.forSharing(message).text) }
    }

    fun report(): String {
        val header = buildString {
            appendLine("Easy Life error report")
            appendLine("App: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) ${BuildConfig.BUILD_TYPE} ${BuildConfig.GIT_SHA}")
            appendLine("Phone: ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine("Firebase: ${if (firebaseReady()) "connected" else "not connected"}")
            appendLine("Generated: ${Instant.now()}")
            appendLine("----")
        }
        val body = synchronized(lock) { if (file.exists()) file.readText() else "(no errors recorded)" }
        return header + body
    }

    fun entryCount(): Int = synchronized(lock) {
        if (!file.exists()) 0 else file.readLines().count { it.startsWith("### ") }
    }

    fun clear() = synchronized(lock) { file.delete() }

    private fun append(kind: String, tag: String, error: Throwable) {
        val sw = StringWriter()
        error.printStackTrace(PrintWriter(sw))
        appendText(kind, tag, Masker.forSharing(sw.toString()).text)
    }

    private fun appendText(kind: String, tag: String, text: String) = synchronized(lock) {
        val entry = "### $kind ${Instant.now()} [$tag]\n${text.take(MAX_ENTRY)}\n"
        val existing = if (file.exists()) file.readText() else ""
        // Keep only the most recent entries so the file stays small.
        val entries = (existing + entry).split("### ").filter { it.isNotBlank() }.takeLast(MAX_ENTRIES)
        file.writeText(entries.joinToString("") { "### $it" })
    }

    private fun firebaseReady(): Boolean =
        BuildConfig.FIREBASE_CONFIGURED && FirebaseApp.getApps(context).isNotEmpty()

    private companion object {
        const val MAX_ENTRIES = 20
        const val MAX_ENTRY = 6_000
    }
}
