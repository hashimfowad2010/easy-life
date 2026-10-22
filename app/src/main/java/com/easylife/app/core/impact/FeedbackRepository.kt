package com.easylife.app.core.impact

import com.easylife.app.core.ai.Masker
import com.easylife.app.core.cloud.FirebaseGateway
import com.easylife.app.core.data.prefs.PrefsRepository
import com.google.firebase.Firebase
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/** The 3-question in-app feedback (spec section 8). `helped` uses fixed keys, never free text. */
@Serializable
data class FeedbackAnswer(
    val usefulness: Int,
    val helped: List<String>,
    val changedSomething: Boolean,
    val comment: String,
    val lang: String,
) {
    companion object {
        val HELPED_KEYS = listOf("electricity", "bills", "budget", "documents", "shopping", "other")
    }
}

enum class FeedbackResult { SENT, SAVED_FOR_LATER }

/**
 * Saves feedback on the phone (it counts in the impact report) and sends it anonymously to Firestore `feedback`.
 * Sending is the user's explicit choice, so it does not depend on the "share anonymous counts" switch.
 * Offline or without Firebase, it is kept and retried by the upload worker.
 */
@Singleton
class FeedbackRepository @Inject constructor(
    private val prefs: PrefsRepository,
    private val firebase: FirebaseGateway,
    private val impact: ImpactTracker,
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun submit(answer: FeedbackAnswer): FeedbackResult {
        val clean = answer.copy(
            usefulness = answer.usefulness.coerceIn(1, 5),
            helped = answer.helped.filter { it in FeedbackAnswer.HELPED_KEYS }.distinct(),
            // Phone, CNIC and account numbers typed into the comment never leave the phone.
            comment = Masker.forCloud(answer.comment.trim()).text.take(500),
            lang = if (answer.lang == "ur") "ur" else "en",
        )
        val detail = "helped=${clean.helped.joinToString("+")};changed=${if (clean.changedSomething) "yes" else "no"}"
        impact.log(ImpactEvents.FEEDBACK, "app", clean.usefulness.toDouble(), detail.take(80))
        prefs.setFeedbackDone()
        return if (upload(clean)) {
            FeedbackResult.SENT
        } else {
            prefs.setPendingFeedback(json.encodeToString(FeedbackAnswer.serializer(), clean))
            FeedbackResult.SAVED_FOR_LATER
        }
    }

    /** Called by the upload worker. */
    suspend fun retryPending() {
        val pending = prefs.pendingFeedback() ?: return
        val answer = runCatching { json.decodeFromString(FeedbackAnswer.serializer(), pending) }.getOrNull()
        if (answer == null || upload(answer)) prefs.setPendingFeedback(null)
    }

    private suspend fun upload(a: FeedbackAnswer): Boolean {
        if (!firebase.isConfigured || !firebase.isOnline()) return false
        val uid = firebase.ensureSignedIn() ?: return false
        val doc = mapOf(
            "anonId" to uid,
            "pilotCode" to (prefs.current().pilotCode ?: ""),
            "q1" to a.usefulness,
            "q2" to a.helped,
            "q3" to a.changedSomething,
            "comment" to a.comment,
            "lang" to a.lang,
            "createdAt" to FieldValue.serverTimestamp(),
        )
        // Once the write is accepted locally, Firestore keeps it and retries by itself, so a slow network is fine.
        return runCatching {
            val write = Firebase.firestore.collection("feedback").add(doc)
            withTimeoutOrNull(15_000) { write.await() }
            true
        }.getOrDefault(false)
    }
}
