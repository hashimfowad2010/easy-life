package com.easylife.app

import android.graphics.Bitmap
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.core.os.LocaleListCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * End-to-end smoke test on an emulator (CI): onboarding, demo data, every main screen in English and Urdu,
 * and a sample bill scan. Fails on any crash. Saves screenshots for the README.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class SmokeTest {

    @get:Rule
    val compose = createEmptyComposeRule()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val shotsDir: File by lazy {
        File(instrumentation.targetContext.getExternalFilesDir(null), "screenshots").apply { mkdirs() }
    }

    private fun setLocale(tag: String) = instrumentation.runOnMainSync {
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag))
    }

    private fun waitText(text: String, timeout: Long = 20_000) =
        compose.waitUntil(timeout) { compose.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty() }

    private fun click(text: String) {
        waitText(text)
        compose.onAllNodesWithText(text, substring = true).onFirst().performClick()
        compose.waitForIdle()
    }

    private fun clickDescription(description: String) {
        compose.waitUntil(10_000) { compose.onAllNodesWithContentDescription(description).fetchSemanticsNodes().isNotEmpty() }
        compose.onAllNodesWithContentDescription(description).onFirst().performClick()
        compose.waitForIdle()
    }

    private var scenario: ActivityScenario<MainActivity>? = null

    private fun back() {
        scenario?.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
    }

    private fun shot(name: String) {
        compose.waitForIdle()
        Thread.sleep(400)
        runCatching {
            val bmp = compose.onAllNodes(isRoot()).onFirst().captureToImage().asAndroidBitmap()
            File(shotsDir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }

    @Test
    fun firstRunDemoAndMainScreensInBothLanguages() {
        setLocale("en")
        scenario = ActivityScenario.launch(MainActivity::class.java)

        // Onboarding (English is already the app language, so choosing it does not restart the screen).
        waitText("Welcome to Easy Life")
        shot("en-00-onboarding")
        click("English")
        waitText("What would you like to use?")
        click("Next")
        waitText("Your city")
        click("Next")
        waitText("Help show that Easy Life works")
        click("No thanks")

        // Today -> demo data.
        waitText("Try with demo data")
        shot("en-01-today-empty")
        click("Try with demo data")
        waitText("Bijli Guard", 40_000)
        shot("en-02-today-demo")

        // Bijli Guard dashboard.
        click("Bijli Guard")
        waitText("Daily allowance")
        shot("en-03-bijli-guard")
        compose.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(hasText("See saving tips"))
        click("See saving tips")
        waitText("Appliance advisor")
        shot("en-04-appliances")
        back()
        back()

        // Modules grid -> Bills, Urdu Helper.
        click("Modules")
        waitText("Smart Shopping")
        shot("en-05-modules")
        click("Bills")
        waitText("Upcoming")
        shot("en-06-bills")
        back()
        click("Urdu Helper")
        waitText("Scan & Read")
        shot("en-07-urdu-text")
        click("Scan & Read")
        waitText("Saved papers")
        click("More")
        waitText("Things to do")
        shot("en-08-urdu-document")
        back()
        back()

        // Universal Scan with the fictional sample bill (parsed on the device).
        clickDescription("Scan")
        click("Sample LESCO bill")
        waitText("This looks like")
        shot("en-09-scan-confirm")
        click("Continue")
        waitText("Check the bill")
        shot("en-10-bill-confirm")
        back()
        back()

        // Settings and the developer tools unlock.
        click("Settings")
        waitText("Language")
        shot("en-11-settings")
        scenario?.close()

        // Urdu: the whole UI right-to-left.
        setLocale("ur")
        scenario = ActivityScenario.launch(MainActivity::class.java)
        waitText("بجلی گارڈ", 30_000)
        shot("ur-01-today")
        click("بجلی گارڈ")
        waitText("روزانہ کی حد")
        shot("ur-02-bijli-guard")
        back()
        click("حصے")
        waitText("سمارٹ شاپنگ")
        shot("ur-03-modules")
        click("اردو مددگار")
        waitText("اسکین اور پڑھیں")
        click("اسکین اور پڑھیں")
        waitText("محفوظ کاغذات")
        click("مزید")
        waitText("کرنے کے کام")
        shot("ur-04-urdu-document")
        scenario?.close()
    }
}
