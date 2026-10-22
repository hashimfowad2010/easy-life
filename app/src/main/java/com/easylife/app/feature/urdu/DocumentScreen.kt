package com.easylife.app.feature.urdu

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.easylife.app.core.ai.Provenance
import com.easylife.app.core.ai.schema.DocExplainResult
import com.easylife.app.core.data.db.DocumentDao
import com.easylife.app.core.data.db.ReminderEntity
import com.easylife.app.core.data.db.SavedDocumentEntity
import com.easylife.app.core.designsystem.EasyTopBar
import com.easylife.app.core.designsystem.LoadingBox
import com.easylife.app.core.i18n.SCommon
import com.easylife.app.core.i18n.SUrdu
import com.easylife.app.core.i18n.currentLanguage
import com.easylife.app.core.i18n.t
import com.easylife.app.core.parse.DocInsights
import com.easylife.app.navigation.LocalSnackbar
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class SavedDocumentsViewModel @Inject constructor(private val dao: DocumentDao) : ViewModel() {
    val docs: StateFlow<List<SavedDocumentEntity>> = dao.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val id = MutableStateFlow<Long?>(null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val doc: StateFlow<SavedDocumentEntity?> = id.flatMapLatest { if (it == null) flowOf(null) else dao.observe(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun load(docId: Long) {
        id.value = docId
    }

    fun delete(onDone: () -> Unit) = viewModelScope.launch {
        id.value?.let { dao.delete(it) }
        onDone()
    }

    fun addReminder(title: String, date: LocalDate, isDemo: Boolean) = viewModelScope.launch {
        dao.insertReminder(ReminderEntity(title = title.take(80), date = date, sourceDocId = id.value, isDemo = isDemo))
    }

    private val json = Json { ignoreUnknownKeys = true }

    fun insights(d: SavedDocumentEntity): DocExplainResult =
        d.insightsJson?.let { runCatching { json.decodeFromString(DocExplainResult.serializer(), it) }.getOrNull() } ?: DocInsights.explain(d.sourceText)
}

@Composable
fun DocumentScreen(id: Long, onBack: () -> Unit, vm: SavedDocumentsViewModel = hiltViewModel(), urdu: UrduViewModel = hiltViewModel()) {
    LaunchedEffect(id) { vm.load(id) }
    val d by vm.doc.collectAsStateWithLifecycle()
    val model by urdu.model.collectAsStateWithLifecycle()
    val lang = currentLanguage()
    val snackbar = LocalSnackbar.current
    val scope = rememberCoroutineScope()
    val added = SUrdu.reminderAdded.t()
    Scaffold(
        topBar = {
            EasyTopBar(d?.title ?: SUrdu.title.t(), onBack = onBack, actions = {
                IconButton(onClick = { vm.delete(onBack) }) { Icon(Icons.Rounded.Delete, SCommon.delete.t()) }
            })
        },
    ) { padding ->
        val doc = d ?: run {
            LoadingBox(modifier = Modifier.padding(padding))
            return@Scaffold
        }
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            DocumentInsightsView(
                insights = vm.insights(doc),
                source = if (doc.isDemo) Provenance.DEMO else Provenance.parse(doc.source),
                translation = doc.translatedText,
                original = doc.sourceText,
                lang = lang,
                vm = urdu,
                model = model,
                onAddReminder = { title, date ->
                    vm.addReminder(title, date, doc.isDemo)
                    scope.launch { snackbar.showSnackbar(added) }
                },
            )
        }
    }
}
