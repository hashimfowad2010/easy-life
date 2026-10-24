package com.easylife.app.navigation

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.easylife.app.DeepLink
import com.easylife.app.MainViewModel
import com.easylife.app.core.data.prefs.UserPrefs
import com.easylife.app.core.designsystem.Module
import com.easylife.app.core.devtools.DevToolsScreen
import com.easylife.app.core.i18n.SBudget
import com.easylife.app.core.i18n.currentLanguage
import com.easylife.app.core.devtools.ErrorReportScreen
import com.easylife.app.core.devtools.ParserLabScreen
import com.easylife.app.core.devtools.RouterViewScreen
import com.easylife.app.core.devtools.SmsLabScreen
import com.easylife.app.feature.bills.BillConfirmScreen
import com.easylife.app.feature.bills.BillDetailScreen
import com.easylife.app.feature.bills.BillsScreen
import com.easylife.app.feature.budget.AddTransactionScreen
import com.easylife.app.feature.budget.BankSetupScreen
import com.easylife.app.feature.budget.BudgetScreen
import com.easylife.app.feature.budget.BudgetsScreen
import com.easylife.app.feature.budget.ReviewInboxScreen
import com.easylife.app.feature.budget.RulesScreen
import com.easylife.app.feature.budget.TransactionDetailScreen
import com.easylife.app.feature.budget.TransactionsScreen
import com.easylife.app.feature.common.ComingSoonScreen
import com.easylife.app.feature.limits.AddReadingScreen
import com.easylife.app.feature.limits.AppliancesScreen
import com.easylife.app.feature.limits.CalibrateScreen
import com.easylife.app.feature.limits.CustomLimitScreen
import com.easylife.app.feature.limits.CustomLimitsScreen
import com.easylife.app.feature.limits.LimitsScreen
import com.easylife.app.feature.limits.MeterCameraScreen
import com.easylife.app.feature.limits.MeterHistoryScreen
import com.easylife.app.feature.limits.MeterSetupScreen
import com.easylife.app.feature.modules.ModulesScreen
import com.easylife.app.feature.onboarding.OnboardingScreen
import com.easylife.app.feature.scan.ScanConfirmScreen
import com.easylife.app.feature.scan.ScanSheet
import com.easylife.app.feature.scan.ScanSheetViewModel
import com.easylife.app.feature.settings.FeedbackScreen
import com.easylife.app.feature.settings.ImpactReportScreen
import com.easylife.app.feature.settings.NotificationSettingsScreen
import com.easylife.app.feature.settings.PrivacyScreen
import com.easylife.app.feature.settings.SettingsScreen
import com.easylife.app.feature.settings.SmartAiScreen
import com.easylife.app.feature.shopping.CompareScreen
import com.easylife.app.feature.shopping.ReceiptConfirmScreen
import com.easylife.app.feature.shopping.ShoppingListScreen
import com.easylife.app.feature.shopping.ShoppingScreen
import com.easylife.app.feature.today.TodayScreen
import com.easylife.app.feature.urdu.DocumentScreen
import com.easylife.app.feature.urdu.UrduScreen
import kotlinx.coroutines.launch

/** App-wide snackbar, so any screen can show "Saved" / "Undo". */
val LocalSnackbar = staticCompositionLocalOf { SnackbarHostState() }

/** Where each module lives. Modules not built yet open an honest "coming soon" page. */
fun Module.route(): Any = when (this) {
    Module.LIMITS -> LimitsRoute
    Module.BILLS -> BillsRoute
    Module.BUDGET -> BudgetRoute
    Module.URDU -> UrduRoute()
    Module.SHOPPING -> ShoppingRoute
}

private val topLevel = listOf(TodayRoute::class, ModulesRoute::class, SettingsRoute::class)

@Composable
fun EasyLifeRoot(viewModel: MainViewModel, prefs: UserPrefs) {
    val nav = rememberNavController()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var showScanSheet by rememberSaveable { mutableStateOf(false) }
    val scanVm: ScanSheetViewModel = hiltViewModel()
    val backStack by nav.currentBackStackEntryAsState()
    val destination = backStack?.destination
    val showBottomBar = prefs.onboardingDone && topLevel.any { r -> destination?.hasRoute(r) == true }
    val deepLink by viewModel.deepLink.collectAsStateWithLifecycle()
    val lang = currentLanguage()

    LaunchedEffect(deepLink, prefs.onboardingDone) {
        val link = deepLink ?: return@LaunchedEffect
        if (!prefs.onboardingDone) return@LaunchedEffect
        viewModel.consumeDeepLink()
        when (link) {
            is DeepLink.Route -> nav.openPath(link.path)
            is DeepLink.SharedText -> scanVm.fromSharedText(
                link.text,
                onBudget = { s ->
                    nav.navigate(TransactionsRoute)
                    scope.launch { snackbar.showSnackbar(if (s.added + s.toReview > 0) SBudget.pasteResult.format(lang, s.added, s.toReview) else SBudget.pasteNothing.get(lang)) }
                },
            ) { id -> nav.navigate(UrduRoute(tab = 1, draftId = id)) }
            is DeepLink.SharedImages -> scanVm.fromImages(link.uris) { id -> nav.navigate(ScanConfirmRoute(id)) }
        }
    }

    CompositionLocalProvider(LocalSnackbar provides snackbar) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbar) },
            bottomBar = {
                if (showBottomBar) {
                    EasyBottomBar(
                        current = destination,
                        onSelect = { route -> nav.navigateTopLevel(route) },
                        onScan = { showScanSheet = true },
                    )
                }
            },
        ) { padding ->
            NavHost(
                navController = nav,
                startDestination = if (prefs.onboardingDone) TodayRoute else OnboardingRoute,
                modifier = Modifier.padding(bottom = if (showBottomBar) padding.calculateBottomPadding() else 0.dp),
                enterTransition = { fadeIn() },
                exitTransition = { fadeOut() },
            ) {
                composable<OnboardingRoute> {
                    OnboardingScreen(onDone = {
                        nav.navigate(TodayRoute) { popUpTo(OnboardingRoute) { inclusive = true } }
                    })
                }
                composable<TodayRoute> { TodayScreen(nav = nav, onScan = { showScanSheet = true }) }
                composable<ModulesRoute> { ModulesScreen(nav = nav) }
                composable<SettingsRoute> { SettingsScreen(nav = nav) }
                composable<SmartAiRoute> { SmartAiScreen(onBack = { nav.popBackStack() }) }
                composable<PrivacyRoute> { PrivacyScreen(onBack = { nav.popBackStack() }) }
                composable<NotificationSettingsRoute> { NotificationSettingsScreen(onBack = { nav.popBackStack() }) }
                composable<ImpactReportRoute> { ImpactReportScreen(onBack = { nav.popBackStack() }) }
                composable<FeedbackRoute> { FeedbackScreen(onBack = { nav.popBackStack() }) }

                composable<DevToolsRoute> { DevToolsScreen(nav = nav) }
                composable<ParserLabRoute> { ParserLabScreen(onBack = { nav.popBackStack() }) }
                composable<SmsLabRoute> { SmsLabScreen(onBack = { nav.popBackStack() }) }
                composable<ErrorReportRoute> { ErrorReportScreen(onBack = { nav.popBackStack() }) }
                composable<RouterViewRoute> { RouterViewScreen(onBack = { nav.popBackStack() }) }

                composable<ScanConfirmRoute> { entry -> ScanConfirmScreen(draftId = entry.toRoute<ScanConfirmRoute>().draftId, nav = nav) }
                composable<MeterCameraRoute> { entry -> MeterCameraScreen(meterId = entry.toRoute<MeterCameraRoute>().meterId, nav = nav) }

                composable<LimitsRoute> { LimitsScreen(nav = nav, onScan = { showScanSheet = true }) }
                composable<MeterSetupRoute> { entry -> MeterSetupScreen(meterId = entry.toRoute<MeterSetupRoute>().meterId, onBack = { nav.popBackStack() }) }
                composable<AddReadingRoute> { entry ->
                    val r = entry.toRoute<AddReadingRoute>()
                    AddReadingScreen(meterId = r.meterId, draftId = r.draftId, nav = nav)
                }
                composable<MeterHistoryRoute> { entry -> MeterHistoryScreen(meterId = entry.toRoute<MeterHistoryRoute>().meterId, onBack = { nav.popBackStack() }) }
                composable<AppliancesRoute> { AppliancesScreen(onBack = { nav.popBackStack() }) }
                composable<CalibrateRoute> { entry -> CalibrateScreen(meterId = entry.toRoute<CalibrateRoute>().meterId, onBack = { nav.popBackStack() }) }
                composable<CustomLimitsRoute> { CustomLimitsScreen(nav = nav) }
                composable<CustomLimitRoute> { entry -> CustomLimitScreen(id = entry.toRoute<CustomLimitRoute>().id, onBack = { nav.popBackStack() }) }

                composable<BillsRoute> { BillsScreen(nav = nav, onScan = { showScanSheet = true }) }
                composable<BillDetailRoute> { entry -> BillDetailScreen(id = entry.toRoute<BillDetailRoute>().id, nav = nav) }
                composable<BillConfirmRoute> { entry ->
                    val r = entry.toRoute<BillConfirmRoute>()
                    BillConfirmScreen(draftId = r.draftId, kind = r.kind, nav = nav)
                }

                composable<UrduRoute> { entry ->
                    val r = entry.toRoute<UrduRoute>()
                    UrduScreen(tab = r.tab, draftId = r.draftId, nav = nav)
                }
                composable<DocumentRoute> { entry -> DocumentScreen(id = entry.toRoute<DocumentRoute>().id, onBack = { nav.popBackStack() }) }

                composable<BudgetRoute> { BudgetScreen(nav = nav) }
                composable<TransactionsRoute> { TransactionsScreen(nav = nav) }
                composable<TransactionDetailRoute> { entry -> TransactionDetailScreen(id = entry.toRoute<TransactionDetailRoute>().id, nav = nav) }
                composable<BankSetupRoute> { BankSetupScreen(nav = nav) }
                composable<ReviewInboxRoute> { ReviewInboxScreen(nav = nav) }
                composable<RulesRoute> { RulesScreen(nav = nav) }
                composable<BudgetsRoute> { BudgetsScreen(nav = nav) }
                composable<AddTransactionRoute> { AddTransactionScreen(nav = nav) }

                composable<ShoppingRoute> { ShoppingScreen(nav = nav) }
                composable<ShoppingListRoute> { entry -> ShoppingListScreen(id = entry.toRoute<ShoppingListRoute>().id, nav = nav) }
                composable<CompareRoute> { entry -> CompareScreen(listId = entry.toRoute<CompareRoute>().listId, nav = nav) }
                composable<ReceiptConfirmRoute> { entry -> ReceiptConfirmScreen(draftId = entry.toRoute<ReceiptConfirmRoute>().draftId, nav = nav) }

                composable<ComingSoonRoute> { entry -> ComingSoonScreen(module = entry.toRoute<ComingSoonRoute>().module, onBack = { nav.popBackStack() }) }
            }
        }

        if (showScanSheet) {
            ScanSheet(
                viewModel = scanVm,
                onDismiss = { showScanSheet = false },
                onDraft = { draftId ->
                    showScanSheet = false
                    nav.navigate(ScanConfirmRoute(draftId))
                },
                onMeter = {
                    showScanSheet = false
                    nav.navigate(MeterCameraRoute())
                },
            )
        }
    }
}

fun NavHostController.navigateTopLevel(route: Any) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

/** Paths used by notifications: "limits", "limits/reading", "bills", "bills/12", "budget", "today". */
fun NavHostController.openPath(path: String) {
    val parts = path.split('/')
    when (parts.first()) {
        "limits" -> if (parts.getOrNull(1) == "reading") navigate(AddReadingRoute()) else navigate(LimitsRoute)
        "bills" -> parts.getOrNull(1)?.toLongOrNull()?.let { navigate(BillDetailRoute(it)) } ?: navigate(BillsRoute)
        "budget" -> navigate(Module.BUDGET.route())
        "urdu" -> navigate(Module.URDU.route())
        else -> navigateTopLevel(TodayRoute)
    }
}
