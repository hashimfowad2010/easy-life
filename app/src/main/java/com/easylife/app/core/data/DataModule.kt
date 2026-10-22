package com.easylife.app.core.data

import android.content.Context
import androidx.room.Room
import com.easylife.app.core.data.db.AppDatabase
import com.easylife.app.core.devtools.ErrorLog
import com.easylife.app.core.security.DatabaseKeyManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class DemoBindings {
    /** Modules add their demo data via @IntoSet; the set may be empty. */
    @dagger.multibindings.Multibinds
    abstract fun demoContributors(): Set<com.easylife.app.core.data.repo.DemoContributor>
}

@Module
@InstallIn(SingletonComponent::class)
object DataModule {

    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context,
        keys: DatabaseKeyManager,
        errorLog: ErrorLog,
    ): AppDatabase {
        System.loadLibrary("sqlcipher")
        // A database without its wrapped key can never be opened again: remove it before creating a new key.
        if (!keys.hasKey()) context.deleteDatabase(AppDatabase.NAME)
        val passphrase = try {
            keys.getOrCreatePassphrase()
        } catch (e: Exception) {
            // The Keystore key is gone (rare: device reset of credentials). The old database can't be opened,
            // so start fresh instead of crashing on every launch.
            errorLog.record("db-key-unwrap-failed", e)
            context.deleteDatabase(AppDatabase.NAME)
            keys.destroy()
            keys.getOrCreatePassphrase()
        }
        return Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.NAME)
            .openHelperFactory(SupportOpenHelperFactory(passphrase))
            // Pre-1.0 builds only: schema changes reset the local data. Proper migrations from v1.0.0 on.
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()
    }

    @Provides fun meterDao(db: AppDatabase) = db.meterDao()

    @Provides fun customLimitDao(db: AppDatabase) = db.customLimitDao()

    @Provides fun applianceDao(db: AppDatabase) = db.applianceDao()

    @Provides fun billDao(db: AppDatabase) = db.billDao()

    @Provides fun budgetDao(db: AppDatabase) = db.budgetDao()

    @Provides fun shoppingDao(db: AppDatabase) = db.shoppingDao()

    @Provides fun documentDao(db: AppDatabase) = db.documentDao()

    @Provides fun reviewDao(db: AppDatabase) = db.reviewDao()

    @Provides fun impactDao(db: AppDatabase) = db.impactDao()

    @Provides fun maintenanceDao(db: AppDatabase) = db.maintenanceDao()
}
