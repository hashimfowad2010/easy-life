package com.easylife.app.feature.shopping

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CompareArrows
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.OpenInBrowser
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.TravelExplore
import androidx.compose.material.icons.rounded.ShoppingCart
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.easylife.app.core.ai.AiError
import com.easylife.app.core.ai.Provenance
import com.easylife.app.core.cloud.OnlinePrice
import com.easylife.app.core.data.db.ShoppingItemEntity
import com.easylife.app.core.designsystem.BannerKind
import com.easylife.app.core.designsystem.BigButton
import com.easylife.app.core.designsystem.BigOutlinedButton
import com.easylife.app.core.designsystem.ConfirmField
import com.easylife.app.core.designsystem.DemoChip
import com.easylife.app.core.designsystem.EasyCard
import com.easylife.app.core.designsystem.EasyTheme
import com.easylife.app.core.designsystem.EasyTopBar
import com.easylife.app.core.designsystem.EmptyState
import com.easylife.app.core.designsystem.InfoBanner
import com.easylife.app.core.designsystem.LabeledValue
import com.easylife.app.core.designsystem.LoadingBox
import com.easylife.app.core.designsystem.Pill
import com.easylife.app.core.designsystem.SectionHeader
import com.easylife.app.core.designsystem.SmartAiChip
import com.easylife.app.core.designsystem.SourceChip
import com.easylife.app.core.i18n.AppLanguage
import com.easylife.app.core.i18n.Fmt
import com.easylife.app.core.i18n.SCommon
import com.easylife.app.core.i18n.SProvenance
import com.easylife.app.core.i18n.SShop
import com.easylife.app.core.i18n.currentLanguage
import com.easylife.app.core.i18n.t
import com.easylife.app.core.shopping.Dimension
import com.easylife.app.core.shopping.ItemOption
import com.easylife.app.core.shopping.StorePlan
import com.easylife.app.core.speech.rememberSpeechState
import com.easylife.app.navigation.CompareRoute
import com.easylife.app.navigation.LocalSnackbar
import com.easylife.app.navigation.ShoppingListRoute
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

@Composable
fun ShoppingScreen(nav: NavHostController, vm: ShoppingViewModel = hiltViewModel()) {
    val lists by vm.lists.collectAsStateWithLifecycle()
    var creating by remember { mutableStateOf(false) }
    val lang = currentLanguage()
    Scaffold(
        topBar = { EasyTopBar(SShop.title.t(), onBack = { nav.popBackStack() }) },
        floatingActionButton = { ExtendedFloatingActionButton(onClick = { creating = true }, icon = { Icon(Icons.Rounded.Add, null) }, text = { Text(SShop.newList.t()) }) },
    ) { padding ->
        val all = lists ?: run {
            LoadingBox(modifier = Modifier.padding(padding))
            return@Scaffold
        }
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (all.isEmpty()) item { EmptyState(Icons.Rounded.ShoppingCart, SShop.empty.t(), SShop.emptyBody.t()) }
            items(all, key = { it.id }) { l ->
                EasyCard(onClick = { nav.navigate(ShoppingListRoute(l.id)) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(l.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        if (l.isDemo) DemoChip()
                    }
                    Text(Fmt.shortDate(l.updatedAt.atZone(ZoneId.systemDefault()).toLocalDate(), lang), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
    if (creating) NewListDialog(onDismiss = { creating = false }) { name, text ->
        creating = false
        vm.createList(name, text) { id -> nav.navigate(ShoppingListRoute(id)) }
    }
}

@Composable
private fun NewListDialog(onDismiss: () -> Unit, onCreate: (String, String) -> Unit) {
    val defaultName = SShop.defaultName.t()
    var name by remember { mutableStateOf(defaultName) }
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(SShop.newList.t()) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ConfirmField(SShop.listName.t(), name, { name = it.take(40) })
                ListInput(text, { text = it })
            }
        },
        confirmButton = { TextButton(onClick = { onCreate(name.ifBlank { defaultName }, text) }) { Text(SCommon.save.t()) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(SCommon.cancel.t()) } },
    )
}

/** Text field + hold-free "Speak" (Urdu) for lists like "2 kg cheeni, 1 dozen anday". */
@Composable
private fun ListInput(text: String, onText: (String) -> Unit) {
    val speech = rememberSpeechState()
    val lang = currentLanguage()
    Column {
        OutlinedTextField(
            value = text,
            onValueChange = onText,
            label = { Text(SShop.typeList.t()) },
            placeholder = { Text(SShop.typeListHint.t()) },
            modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp),
        )
        if (speech.available) {
            TextButton(onClick = {
                if (speech.listening) speech.finish() else speech.start(if (lang == AppLanguage.UR) AppLanguage.UR else AppLanguage.EN) { heard ->
                    onText(listOf(text, heard).filter { it.isNotBlank() }.joinToString(", "))
                }
            }) {
                Icon(Icons.Rounded.Mic, null)
                Spacer(Modifier.width(4.dp))
                Text(if (speech.listening) com.easylife.app.core.i18n.SUrdu.listening.t() else SShop.speak.t())
            }
        }
    }
}

@Composable
fun ShoppingListScreen(id: Long, nav: NavHostController, vm: ShoppingViewModel = hiltViewModel()) {
    LaunchedEffect(id) { vm.open(id) }
    val d by vm.detail.collectAsStateWithLifecycle()
    val retailers by vm.retailers.collectAsStateWithLifecycle()
    val onlineAvailable by vm.onlineAvailable.collectAsStateWithLifecycle()
    val online by vm.online.collectAsStateWithLifecycle()
    val lang = currentLanguage()
    val context = LocalContext.current
    var input by remember { mutableStateOf("") }
    var searching by remember { mutableStateOf<ShoppingItemEntity?>(null) }
    var pricing by remember { mutableStateOf<ShoppingItemEntity?>(null) }
    var sharing by remember { mutableStateOf(false) }
    Scaffold(
        topBar = {
            EasyTopBar(d?.list?.name ?: SShop.title.t(), onBack = { nav.popBackStack() }, actions = {
                IconButton(onClick = { sharing = true }) { Icon(Icons.Rounded.Share, SShop.share.t()) }
                IconButton(onClick = { vm.deleteList { nav.popBackStack() } }) { Icon(Icons.Rounded.Delete, SShop.deleteList.t()) }
            })
        },
    ) { padding ->
        val detail = d ?: run {
            LoadingBox(modifier = Modifier.padding(padding))
            return@Scaffold
        }
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                ListInput(input) { input = it }
                TextButton(enabled = input.isNotBlank(), onClick = { vm.addItems(input); input = "" }) { Text(SShop.addItems.t()) }
            }
            item {
                EasyCard(containerColor = EasyTheme.colors.shopping.container) {
                    Text(SShop.estimateTotal.t(Fmt.rupees(detail.estimate, lang)), style = MaterialTheme.typography.titleMedium, color = EasyTheme.colors.shopping.onContainer)
                    detail.groceriesLeft?.let { left ->
                        Text(SShop.budgetLink.t(Fmt.rupees(detail.estimate, lang), Fmt.rupees(maxOf(0.0, left), lang)), style = MaterialTheme.typography.bodyMedium)
                        if (detail.estimate > left) {
                            InfoBanner(SShop.budgetOver.t(Fmt.rupees(detail.estimate - left, lang)), BannerKind.WARNING)
                            if (detail.estimateMustHave < detail.estimate) Text(SShop.withoutSkippable.t(Fmt.rupees(detail.estimateMustHave, lang)), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    BigButton(SShop.compare.t(), icon = Icons.Rounded.CompareArrows, onClick = { nav.navigate(CompareRoute(id)) }, modifier = Modifier.padding(top = 8.dp))
                    if (onlineAvailable) {
                        BigOutlinedButton(SShop.onlineCheck.t(), icon = Icons.Rounded.TravelExplore, onClick = { vm.searchOnline() }, modifier = Modifier.padding(top = 8.dp))
                    }
                }
            }
            items(detail.items, key = { it.id }) { item ->
                val best = item.itemKey?.let { detail.comparison.cheapestPerItem[it] }
                EasyCard {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.alpha(if (item.checked) 0.5f else 1f)) {
                        Checkbox(checked = item.checked, onCheckedChange = { vm.toggleChecked(item) })
                        Column(Modifier.weight(1f)) {
                            Text(
                                (if (lang == AppLanguage.UR) item.nameUr else item.nameEn) + (item.brandPref?.let { " ($it)" } ?: ""),
                                style = MaterialTheme.typography.titleSmall,
                                textDecoration = if (item.checked) TextDecoration.LineThrough else null,
                            )
                            Text("${Fmt.smart(item.qty)} ${item.unit ?: ""} · ${if (lang == AppLanguage.UR) item.nameEn else item.nameUr}", style = MaterialTheme.typography.bodySmall)
                            if (item.itemKey == null || item.category == "other" && item.nameEn == item.nameUr) {
                                Text(SShop.notRecognised.t(), style = MaterialTheme.typography.labelSmall, color = EasyTheme.colors.watch)
                            }
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            val price = item.manualPrice ?: best?.cost
                            if (price != null) Text(Fmt.rupees(price, lang), style = MaterialTheme.typography.titleSmall)
                            if (item.manualPrice != null) Text(SShop.typedByYou.t(item.manualPriceSource ?: ""), style = MaterialTheme.typography.labelSmall)
                            else best?.let { Text(detail.stores[it.storeId]?.name ?: "", style = MaterialTheme.typography.labelSmall) }
                        }
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(item.priority == 2, { vm.togglePriority(item) }, { Text(SShop.canSkip.t()) })
                        AssistChip(onClick = { searching = item }, label = { Text(SShop.searchOnline.t()) }, leadingIcon = { Icon(Icons.Rounded.OpenInBrowser, null) })
                        AssistChip(onClick = { pricing = item }, label = { Text(SShop.foundPrice.t()) })
                        IconButton(onClick = { vm.deleteItem(item.id) }) { Icon(Icons.Rounded.Delete, SCommon.delete.t()) }
                    }
                }
            }
        }
    }
    searching?.let { item ->
        ModalBottomSheet(onDismissRequest = { searching = null }) {
            Column(Modifier.padding(16.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(item.nameEn, style = MaterialTheme.typography.titleMedium)
                Text(SShop.searchOnlineNote.t(), style = MaterialTheme.typography.bodySmall)
                retailers.forEach { r ->
                    OutlinedButton(onClick = {
                        val query = listOfNotNull(item.brandPref, item.nameEn.substringBefore(" (")).joinToString(" ")
                        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(r.searchUrlFor(query))).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                    }, modifier = Modifier.fillMaxWidth()) { Text(SShop.searchOn.t(r.name)) }
                }
                if (!onlineAvailable) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SmartAiChip()
                        Spacer(Modifier.width(8.dp))
                        Text(SShop.autoSearch.t() + " — " + SProvenance.comingWithSmartAi.t(), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
    if (online != OnlineSearchState.Idle) {
        ModalBottomSheet(onDismissRequest = vm::closeOnline) {
            OnlineResults(online, d?.items.orEmpty(), onUse = vm::useOnlinePrice)
        }
    }
    pricing?.let { item -> FoundPriceDialog(item, retailers.map { it.name }, onDismiss = { pricing = null }) { price, retailer, pack, unit ->
        vm.setManualPrice(item, price, retailer, pack, unit)
        pricing = null
    } }
    if (sharing) {
        val items = d?.items.orEmpty()
        AlertDialog(
            onDismissRequest = { sharing = false },
            title = { Text(SShop.share.t()) },
            confirmButton = { TextButton(onClick = { share(context, vm.shareText(items, AppLanguage.UR)); sharing = false }) { Text(SShop.shareUrdu.t()) } },
            dismissButton = { TextButton(onClick = { share(context, vm.shareText(items, AppLanguage.EN)); sharing = false }) { Text(SShop.shareEnglish.t()) } },
        )
    }
}

/** Prices found by `shopSearch`: labelled Smart AI (Claude), dated, linked, and only used after the user taps "Use". */
@Composable
private fun OnlineResults(state: OnlineSearchState, items: List<ShoppingItemEntity>, onUse: (OnlinePrice) -> Unit) {
    val lang = currentLanguage()
    val context = LocalContext.current
    val snackbar = LocalSnackbar.current
    val usedText = SShop.onlineUsed.t()
    val used = remember { mutableStateListOf<OnlinePrice>() }
    Column(Modifier.padding(16.dp).padding(bottom = 24.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(SShop.onlineTitle.t(), style = MaterialTheme.typography.titleLarge)
        when (state) {
            OnlineSearchState.Idle -> Unit
            OnlineSearchState.Loading -> Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(24.dp))
                Spacer(Modifier.width(12.dp))
                Text(SShop.onlineChecking.t(), style = MaterialTheme.typography.bodyMedium)
            }
            is OnlineSearchState.Failed -> InfoBanner(
                when (state.error) {
                    AiError.RateLimited -> SShop.onlineBusy.t()
                    AiError.Network, AiError.Timeout -> SShop.onlineOffline.t()
                    else -> SShop.onlineFailed.t()
                },
                BannerKind.WARNING,
            )
            is OnlineSearchState.Done -> {
                val p = state.prices
                SourceChip(Provenance.CLAUDE)
                val date = runCatching { Fmt.date(java.time.LocalDate.parse(p.checkedAt.take(10)), lang) }.getOrDefault(p.checkedAt.take(10))
                Text(SShop.onlineNote.t(date), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (p.results.isEmpty()) InfoBanner(SShop.onlineNone.t())
                p.results.groupBy { it.item }.forEach { (key, results) ->
                    val item = items.firstOrNull { it.itemKey == key }
                    Text(item?.let { if (lang == AppLanguage.UR) it.nameUr else it.nameEn } ?: key, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
                    results.forEach { r ->
                        EasyCard {
                            Text(r.retailer, style = MaterialTheme.typography.labelLarge, color = EasyTheme.colors.shopping.accent)
                            Text(r.productTitle, style = MaterialTheme.typography.bodyMedium)
                            val pack = r.packSize?.let { "${Fmt.smart(it)} ${r.unit ?: ""}".trim() }
                            val perUnit = r.unitPrice?.let { up ->
                                val per = when (r.unit) { "g", "kg" -> "kg"; "ml", "l" -> "l"; "dozen", "piece" -> "piece"; else -> r.unit ?: "packet" }
                                SShop.perUnit.t(Fmt.rupees(up, lang), per)
                            }
                            Text(listOfNotNull(Fmt.rupees(r.pricePkr, lang), pack, perUnit).joinToString(" · "), style = MaterialTheme.typography.titleSmall)
                            Row {
                                TextButton(onClick = {
                                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(r.url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                                }) { Text(SShop.onlineOpen.t()) }
                                TextButton(enabled = r !in used, onClick = {
                                    onUse(r)
                                    used += r
                                    kotlinx.coroutines.MainScope().launch { snackbar.showSnackbar(usedText) }
                                }) { Text(SShop.onlineUse.t()) }
                            }
                        }
                    }
                }
                if (p.notFound.isNotEmpty()) {
                    val names = p.notFound.map { k -> items.firstOrNull { it.itemKey == k }?.let { if (lang == AppLanguage.UR) it.nameUr else it.nameEn } ?: k }
                    Text(SShop.onlineNotFound.t(names.joinToString(if (lang == AppLanguage.UR) "، " else ", ")), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

private fun share(context: android.content.Context, text: String) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

@Composable
private fun FoundPriceDialog(item: ShoppingItemEntity, retailers: List<String>, onDismiss: () -> Unit, onSave: (Double, String, Double, String) -> Unit) {
    var price by remember { mutableStateOf("") }
    var retailer by remember { mutableStateOf(retailers.firstOrNull() ?: "") }
    var pack by remember { mutableStateOf(Fmt.smart(if (item.unit == null) 1.0 else item.qty)) }
    var unit by remember { mutableStateOf(item.unit ?: "packet") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(SShop.priceFor.t(item.nameEn)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ConfirmField(SCommon.amount.t() + " (Rs)", price, { price = it.filter { c -> c.isDigit() || c == '.' }.take(8) }, numeric = true)
                ConfirmField(SShop.packSize.t(), pack, { pack = it.filter { c -> c.isDigit() || c == '.' }.take(6) }, numeric = true)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("kg", "g", "l", "ml", "dozen", "piece", "packet").forEach { u -> FilterChip(unit == u, { unit = u }, { Text(u) }) }
                }
                Text(SShop.where.t(), style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    retailers.forEach { r -> FilterChip(retailer == r, { retailer = r }, { Text(r) }) }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = (price.toDoubleOrNull() ?: 0.0) > 0 && (pack.toDoubleOrNull() ?: 0.0) > 0, onClick = {
                onSave(price.toDouble(), retailer, pack.toDouble(), unit)
            }) { Text(SCommon.save.t()) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(SCommon.cancel.t()) } },
    )
}

@Composable
fun CompareScreen(listId: Long, nav: NavHostController, vm: ShoppingViewModel = hiltViewModel()) {
    LaunchedEffect(listId) { vm.open(listId) }
    val d by vm.detail.collectAsStateWithLifecycle()
    val syncing by vm.syncing.collectAsStateWithLifecycle()
    val lang = currentLanguage()
    val snackbar = LocalSnackbar.current
    val scope = rememberCoroutineScope()
    val refreshed = SShop.refreshed.t()
    val offline = SShop.offlinePrices.t()
    LaunchedEffect(d?.list?.id) { if (d != null) vm.logComparison() }
    Scaffold(
        topBar = {
            EasyTopBar(SShop.compare.t(), onBack = { nav.popBackStack() }, actions = {
                IconButton(onClick = { vm.sync { ok -> scope.launch { snackbar.showSnackbar(if (ok) refreshed else offline) } } }) {
                    Icon(Icons.Rounded.Sync, SShop.refresh.t())
                }
            })
        },
    ) { padding ->
        val detail = d ?: run {
            LoadingBox(modifier = Modifier.padding(padding))
            return@Scaffold
        }
        val c = detail.comparison
        val itemName = { key: String -> detail.items.firstOrNull { it.itemKey == key }?.let { if (lang == AppLanguage.UR) it.nameUr else it.nameEn } ?: key }
        val storeName = { id: String -> detail.stores[id]?.let { s -> s.name + (s.area?.takeIf { it.isNotBlank() && it != "online" }?.let { " · $it" } ?: "") } ?: id }
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (syncing) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            item { InfoBanner(SShop.notGuaranteed.t(), BannerKind.INFO) }
            if (detail.prices.any { it.isDemo }) item { DemoChip() }
            item { SectionHeader(SShop.cheapestPerItem.t()) }
            items(c.cheapestPerItem.values.toList(), key = { it.itemKey }) { opt -> OptionCard(opt, itemName(opt.itemKey), storeName(opt.storeId), lang) }
            if (c.itemsWithoutPrices.isNotEmpty()) {
                item { Text(SShop.noPrices.t(c.itemsWithoutPrices.joinToString(", ") { itemName(it) }), style = MaterialTheme.typography.bodySmall) }
            }
            c.bestSingleStore?.let { plan -> item { PlanCard(SShop.bestSingle.t(), plan, detail.items.count { !it.checked && it.itemKey != null }, storeName, lang) } }
            c.bestSplit?.takeIf { it.storeIds.size == 2 }?.let { plan -> item { PlanCard(SShop.bestSplit.t(), plan, detail.items.count { !it.checked && it.itemKey != null }, storeName, lang) } }
            if (c.savingsVsMostExpensive > 0) {
                item {
                    EasyCard(containerColor = EasyTheme.colors.safe.copy(alpha = 0.12f)) {
                        Text(SShop.savings.t(Fmt.rupees(c.savingsVsMostExpensive, lang), Fmt.rupees(c.savingsVsAverage, lang)), style = MaterialTheme.typography.titleSmall)
                    }
                }
            }
        }
    }
}

private fun unitTxt(d: Dimension) = when (d) {
    Dimension.MASS -> SShop.unitKg
    Dimension.VOLUME -> SShop.unitL
    Dimension.COUNT -> SShop.unitPiece
    Dimension.PACK -> SShop.unitPack
}

@Composable
private fun OptionCard(opt: ItemOption, name: String, store: String, lang: AppLanguage) {
    EasyCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(name, style = MaterialTheme.typography.titleSmall)
                Text(store, style = MaterialTheme.typography.bodySmall)
                val age = Fmt.relativeDay(opt.price.latest.atZone(ZoneId.systemDefault()).toLocalDate(), LocalDate.now(), lang)
                Text(
                    if (opt.storeId.startsWith("online:")) SShop.typedByYou.t(store) else SShop.reported.t(age, opt.price.reports),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(Fmt.rupees(opt.cost, lang), style = MaterialTheme.typography.titleSmall)
                Pill(SShop.perUnit.t(Fmt.rupees(opt.price.unitPrice, lang), unitTxt(opt.price.dimension).t()), MaterialTheme.colorScheme.surfaceContainerHigh, MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun PlanCard(title: String, plan: StorePlan, itemCount: Int, storeName: (String) -> String, lang: AppLanguage) {
    EasyCard {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(plan.storeIds.joinToString(" + ") { storeName(it) }, style = MaterialTheme.typography.bodyMedium)
        Text(SShop.covers.t(plan.covered, itemCount, Fmt.rupees(plan.total, lang)), style = MaterialTheme.typography.titleSmall)
        plan.choices.values.forEach { o -> LabeledValue(storeName(o.storeId).substringBefore(" ·") + " · " + o.itemKey, Fmt.rupees(o.cost, lang)) }
    }
}
