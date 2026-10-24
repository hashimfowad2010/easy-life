package com.easylife.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.core.content.IntentCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.easylife.app.core.designsystem.EasyLifeTheme
import com.easylife.app.core.i18n.AppLanguage
import com.easylife.app.core.i18n.LocalAppLanguage
import com.easylife.app.navigation.EasyLifeRoot
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        splash.setKeepOnScreenCondition { viewModel.state.value.loading }
        enableEdgeToEdge()
        if (savedInstanceState == null) handleIntent(intent)

        setContent {
            val state by viewModel.state.collectAsStateWithLifecycle()
            // The per-app locale (set with AppCompatDelegate) drives both text and RTL layout.
            val language = AppLanguage.fromTag(LocalConfiguration.current.locales[0].language) ?: AppLanguage.EN
            CompositionLocalProvider(LocalAppLanguage provides language) {
                EasyLifeTheme(
                    language = language,
                    urduFont = state.prefs.urduFont,
                    themeMode = state.prefs.themeMode,
                    palette = state.prefs.palette,
                    textSize = state.prefs.textSize,
                ) {
                    if (!state.loading) EasyLifeRoot(viewModel = viewModel, prefs = state.prefs)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent == null) return
        intent.getStringExtra(EXTRA_ROUTE)?.let {
            viewModel.offerDeepLink(DeepLink.Route(it))
            return
        }
        when (intent.action) {
            Intent.ACTION_SEND -> {
                val text = intent.getStringExtra(Intent.EXTRA_TEXT)
                val image = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
                when {
                    image != null -> viewModel.offerDeepLink(DeepLink.SharedImages(listOf(image)))
                    !text.isNullOrBlank() -> viewModel.offerDeepLink(DeepLink.SharedText(text))
                }
            }
            Intent.ACTION_SEND_MULTIPLE -> {
                val images = IntentCompat.getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri::class.java).orEmpty()
                if (images.isNotEmpty()) viewModel.offerDeepLink(DeepLink.SharedImages(images.take(4)))
            }
        }
    }

    companion object {
        const val EXTRA_ROUTE = "com.easylife.app.ROUTE"
    }
}
