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
import androidx.compose.ui.test.printToString
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
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
        // AGP pulls this folder to build/outputs/connected_android_test_additional_output after the run.
        (InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")?.let(::File)
            ?: File(instrumentation.targetContext.getExternalFilesDir(null), "screenshots")).apply { mkdirs() }
    }

    /**
     * On Android 13+ AppCompat reaches the system LocaleManager through a live activity, so with no activity open
     * the call silently does nothing. Always switch while the app is on screen (as a user does in Settings).
     */
    private fun setLocale(tag: String) {
        scenario!!.onActivity { AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag)) }
        compose.waitForIdle()
    }

    private fun waitText(text: String, timeout: Long = 20_000) =
        compose.waitUntil(timeout) { compose.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty() }

    private fun click(text: String) {
        waitText(text)
        val node = compose.onAllNodesWithText(text, substring = true).onFirst()
        // Taller Urdu text pushes some buttons below the screen: scroll them into view like a user would.
        runCatching { node.performScrollTo() }
        node.performClick()
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
        try {
            runFlow()
        } catch (e: Throwable) {
            // Leave evidence for CI: what was on screen and the UI tree.
            shot("zz-failure")
            runCatching {
                File(shotsDir, "zz-failure-tree.txt").writeText(compose.onAllNodes(isRoot()).onFirst().printToString(maxDepth = 40))
            }
            throw e
        }
    }

    private fun runFlow() {
        scenario = ActivityScenario.launch(MainActivity::class.java)
        setLocale("en")

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
        // Urdu: the whole UI right-to-left. Switch while the app is open, then start fresh on Today.
        setLocale("ur")
        scenario?.close()
        scenario = ActivityScenario.launch(MainActivity::class.java)
        waitText("بجلی گارڈ", 30_000)
        shot("ur-01-today")
        click("بجلی گارڈ")
        waitText("روزانہ کی حد")
        shot("ur-02-bijli-guard")
        back()
        click("حصے")
        // Urdu tiles are taller (Nastaliq), so Smart Shopping is below the fold: wait for a visible tile.
        waitText("اردو مددگار")
        shot("ur-03-modules")
        click("اردو مددگار")
        waitText("اسکین اور پڑھیں")
        click("اسکین اور پڑھیں")
        waitText("محفوظ کاغذات")
        click("مزید")
        waitText("کرنے کے کام")
        shot("ur-04-urdu-document")
        scenario?.close()

        // 200 % font size (Android Settings → Display → Font size) in Urdu, the tallest script: still usable.
        setFontScale(2.0f)
        try {
            scenario = ActivityScenario.launch(MainActivity::class.java)
            waitText("بجلی گارڈ", 30_000)
            shot("ur-large-01-today")
            click("بجلی گارڈ")
            waitText("روزانہ کی حد")
            shot("ur-large-02-bijli-guard")
            back()
            click("سیٹنگز")
            waitText("زبان")
            shot("ur-large-03-settings")
            scenario?.close()
        } finally {
            setFontScale(1.0f)
        }
    }

    private fun setFontScale(scale: Float) {
        instrumentation.uiAutomation.executeShellCommand("settings put system font_scale $scale").close()
        Thread.sleep(1_000)
    }
}
