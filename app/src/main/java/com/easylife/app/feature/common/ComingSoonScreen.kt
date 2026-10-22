package com.easylife.app.feature.common

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Construction
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.easylife.app.core.designsystem.EasyTopBar
import com.easylife.app.core.designsystem.EmptyState
import com.easylife.app.core.designsystem.Module
import com.easylife.app.core.designsystem.title
import com.easylife.app.core.i18n.Txt
import com.easylife.app.core.i18n.t

private val comingTitle = Txt("Coming in the next update", "اگلی اپ ڈیٹ میں آ رہا ہے")
private val comingBody = Txt(
    "This part of Easy Life is still being built. It is not hidden or faked: it will appear in a new release soon.",
    "آسان زندگی کا یہ حصہ ابھی بن رہا ہے۔ جلد ہی نئی ریلیز میں آ جائے گا۔",
)

/** Honest placeholder for a module that is not built yet (spec: never silently stub a feature). */
@Composable
fun ComingSoonScreen(module: String, onBack: () -> Unit) {
    val m = Module.entries.firstOrNull { it.name == module }
    Scaffold(topBar = { EasyTopBar(m?.title?.t() ?: "", onBack = onBack) }) { padding ->
        EmptyState(Icons.Rounded.Construction, comingTitle.t(), comingBody.t(), Modifier.padding(padding))
    }
}
