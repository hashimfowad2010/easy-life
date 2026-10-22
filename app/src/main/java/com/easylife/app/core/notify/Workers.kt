package com.easylife.app.core.notify

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.easylife.app.core.devtools.ErrorLog
import com.easylife.app.core.impact.FeedbackRepository
import com.easylife.app.core.impact.ImpactUploader
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** Checks limits, bills, budgets and reminders a few times a day. */
@HiltWorker
class DailyCheckWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val engine: AlertEngine,
    private val errorLog: ErrorLog,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = try {
        engine.run()
        Result.success()
    } catch (e: Exception) {
        errorLog.record("daily-check", e)
        Result.retry()
    }
}

/** Uploads anonymous impact events (only if the user opted in, checked inside the uploader) and unsent feedback. */
@HiltWorker
class ImpactUploadWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val uploader: ImpactUploader,
    private val feedback: FeedbackRepository,
    private val errorLog: ErrorLog,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = try {
        uploader.uploadPending()
        feedback.retryPending()
        Result.success()
    } catch (e: Exception) {
        errorLog.record("impact-upload", e)
        Result.retry()
    }
}

@Singleton
class WorkScheduler @Inject constructor(@ApplicationContext private val context: Context) {

    private val wm get() = WorkManager.getInstance(context)

    fun schedulePeriodic() {
        wm.enqueueUniquePeriodicWork(
            "daily-check",
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<DailyCheckWorker>(3, TimeUnit.HOURS, 30, TimeUnit.MINUTES).build(),
        )
        wm.enqueueUniquePeriodicWork(
            "impact-upload",
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<ImpactUploadWorker>(12, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build(),
        )
    }

    /** Run the checks soon (after saving a bill or a reading). */
    fun checkSoon() {
        wm.enqueueUniqueWork(
            "check-soon",
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<DailyCheckWorker>().setInitialDelay(5, TimeUnit.SECONDS).build(),
        )
    }

    fun uploadSoon() {
        wm.enqueueUniqueWork(
            "impact-upload-now",
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<ImpactUploadWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build(),
        )
    }
}
