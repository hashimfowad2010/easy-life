// AGP 9 has built-in Kotlin, so there is no org.jetbrains.kotlin.android plugin here.
// Declaring the Compose/serialization plugins puts the catalog's Kotlin Gradle plugin on the classpath.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.room) apply false
    alias(libs.plugins.google.services) apply false
    alias(libs.plugins.crashlytics) apply false
}
