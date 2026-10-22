package com.easylife.app.core.notify

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
object NotifyModule {
    /** Budget alerts are wired in with the Budget module (Phase 3); until then there are none. */
    @Provides
    fun budgetAlerts(): BudgetAlertSource = BudgetAlertSource { _, _ -> emptyList() }
}
