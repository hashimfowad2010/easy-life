package com.easylife.app.feature.urdu

import android.content.Context
import com.easylife.app.core.ai.Provenance
import com.easylife.app.core.ai.providers.MockProvider
import com.easylife.app.core.ai.schema.DocExplainResult
import com.easylife.app.core.data.db.DocumentDao
import com.easylife.app.core.data.db.ReminderEntity
import com.easylife.app.core.data.db.SavedDocumentEntity
import com.easylife.app.core.data.repo.DemoContributor
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import kotlinx.serialization.json.Json
import java.time.LocalDate
import javax.inject.Inject

/** Demo mode: a translated (fictional) school circular and its reminder, labelled "Demo data". */
class UrduDemo @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: DocumentDao,
) : DemoContributor {
    override suspend fun load(today: LocalDate) {
        val text = context.assets.open("demo/sample_circular.txt").bufferedReader().use { it.readText() }
        val insights = MockProvider.demoCircularExplained
        val id = dao.insert(
            SavedDocumentEntity(
                title = "Annual Sports Day and Fee Reminder",
                docType = insights.docType,
                sourceText = text,
                translatedText = MockProvider.demoCircularUrdu,
                insightsJson = Json.encodeToString(DocExplainResult.serializer(), insights),
                source = Provenance.DEMO.name,
                isDemo = true,
            ),
        )
        dao.insertReminder(ReminderEntity(title = "Sign and return the consent form", date = today.plusDays(2), sourceDocId = id, isDemo = true))
    }

    override suspend fun clear() {
        dao.deleteDemoDocs()
        dao.deleteDemoReminders()
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class UrduModule {
    @Binds
    @IntoSet
    abstract fun demo(impl: UrduDemo): DemoContributor
}
