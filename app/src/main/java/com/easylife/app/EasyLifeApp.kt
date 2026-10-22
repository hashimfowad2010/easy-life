package com.easylife.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.easylife.app.core.devtools.ErrorLog
import com.easylife.app.core.notify.NotificationChannels
import com.easylife.app.core.notify.WorkScheduler
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class EasyLifeApp : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory

    @Inject lateinit var errorLog: ErrorLog

    @Inject lateinit var channels: NotificationChannels

    @Inject lateinit var scheduler: WorkScheduler

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        errorLog.installCrashHandler()
        runCatching { channels.ensureCreated() }.onFailure { errorLog.record("channels", it) }
        runCatching { scheduler.schedulePeriodic() }.onFailure { errorLog.record("work-schedule", it) }
    }
}
