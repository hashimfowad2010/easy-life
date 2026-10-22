package com.easylife.app.navigation

import kotlinx.serialization.Serializable

// Type-safe Navigation Compose routes.

@Serializable data object OnboardingRoute
@Serializable data object TodayRoute
@Serializable data object ModulesRoute
@Serializable data object SettingsRoute

// Settings pages
@Serializable data object SmartAiRoute
@Serializable data object PrivacyRoute
@Serializable data object NotificationSettingsRoute
@Serializable data object ImpactReportRoute
@Serializable data object FeedbackRoute

// Developer tools (hidden: tap the version 7 times)
@Serializable data object DevToolsRoute
@Serializable data object ParserLabRoute
@Serializable data object SmsLabRoute
@Serializable data object ErrorReportRoute
@Serializable data object RouterViewRoute

// Universal Scan
@Serializable data class ScanConfirmRoute(val draftId: String)
@Serializable data class MeterCameraRoute(val meterId: Long = 0)

// Limits (Bijli Guard)
@Serializable data object LimitsRoute
@Serializable data class MeterSetupRoute(val meterId: Long = 0)
@Serializable data class AddReadingRoute(val meterId: Long = 0, val draftId: String? = null)
@Serializable data class MeterHistoryRoute(val meterId: Long)
@Serializable data object AppliancesRoute
@Serializable data class CalibrateRoute(val meterId: Long)
@Serializable data object CustomLimitsRoute
@Serializable data class CustomLimitRoute(val id: Long)

// Bills
@Serializable data object BillsRoute
@Serializable data class BillDetailRoute(val id: Long)
@Serializable data class BillConfirmRoute(val draftId: String? = null, val kind: String? = null)

// Budget
@Serializable data object BudgetRoute
@Serializable data object TransactionsRoute
@Serializable data class TransactionDetailRoute(val id: Long)
@Serializable data object BankSetupRoute
@Serializable data object ReviewInboxRoute
@Serializable data object RulesRoute
@Serializable data object BudgetsRoute
@Serializable data class ReceiptConfirmRoute(val draftId: String)
@Serializable data class AddTransactionRoute(val id: Long = 0)

// Urdu Helper
@Serializable data class UrduRoute(val tab: Int = 0, val draftId: String? = null, val text: String? = null)
@Serializable data class DocumentRoute(val id: Long)

/** A module that is not built yet (shown honestly as "coming in the next update"). */
@Serializable data class ComingSoonRoute(val module: String)

// Smart Shopping
@Serializable data object ShoppingRoute
@Serializable data class ShoppingListRoute(val id: Long)
@Serializable data class CompareRoute(val listId: Long)
