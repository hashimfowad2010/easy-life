package com.easylife.app.core.impact

import com.easylife.app.core.cloud.FirebaseGateway
import com.easylife.app.core.data.db.ImpactDao
import com.easylife.app.core.data.prefs.PrefsRepository
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.tasks.await
import java.time.temporal.ChronoUnit
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Sends anonymous impact events to Firestore `impactEvents` — ONLY if the user opted in, and only events created
 * after opting in. Each event: anonymous auth id, pilot code, event name, rounded value, time rounded to the hour.
 */
@Singleton
class ImpactUploader @Inject constructor(
    private val dao: ImpactDao,
    private val prefs: PrefsRepository,
    private val firebase: FirebaseGateway,
) {
    suspend fun uploadPending(): Int {
        val p = prefs.current()
        val optInAt = p.impactOptInAt
        if (!p.impactOptIn || optInAt == null || !firebase.isConfigured) return 0
        val uid = firebase.ensureSignedIn() ?: return 0
        val pending = dao.pending(200)
        val toSend = pending.filter { !it.createdAt.isBefore(optInAt) }
        // Events from before opting in are never sent; mark them done so they are not considered again.
        val skipped = pending.filter { it.createdAt.isBefore(optInAt) }.map { it.id }
        if (skipped.isNotEmpty()) dao.markUploaded(skipped)
        if (toSend.isEmpty()) return 0
        val db = Firebase.firestore
        val batch = db.batch()
        toSend.forEach { e ->
            val doc = db.collection("impactEvents").document()
            batch.set(
                doc,
                mapOf(
                    "anonId" to uid,
                    "pilotCode" to (p.pilotCode ?: ""),
                    "event" to e.event,
                    "module" to e.module,
                    "value" to e.value?.let(ImpactTracker::round),
                    "detail" to (e.detail ?: ""),
                    "createdAt" to Timestamp(Date.from(e.createdAt.truncatedTo(ChronoUnit.HOURS))),
                ),
            )
        }
        batch.commit().await()
        dao.markUploaded(toSend.map { it.id })
        return toSend.size
    }
}
