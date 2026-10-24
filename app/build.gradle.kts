import groovy.json.JsonOutput
import groovy.json.JsonSlurper

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.room)
}

// Firebase is optional at build time: CI writes google-services.json from a secret.
// Without it the app builds with Firebase features switched off (see PROGRESS.md).
val firebaseConfigured = file("google-services.json").exists()
if (firebaseConfigured) {
    apply(plugin = "com.google.gms.google-services")
    apply(plugin = "com.google.firebase.crashlytics")
}

// Release version comes from the git tag (v1.2.3 -> versionName 1.2.3, versionCode 1002003),
// so every new tag automatically gets a higher versionCode.
val versionNameFromCi: String = (System.getenv("EASYLIFE_VERSION_NAME") ?: "0.0.1-dev").removePrefix("v")
val versionCodeFromCi: Int = Regex("""^(\d+)\.(\d+)\.(\d+)""").find(versionNameFromCi)
    ?.destructured?.let { (major, minor, patch) -> major.toInt() * 1_000_000 + minor.toInt() * 1_000 + patch.toInt() }
    ?.coerceAtLeast(1) ?: 1

val releaseKeystorePath: String? = System.getenv("EASYLIFE_KEYSTORE_PATH")

android {
    namespace = "com.easylife.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.easylife.app"
        minSdk = 26
        targetSdk = 37
        versionCode = versionCodeFromCi
        versionName = versionNameFromCi
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("boolean", "FIREBASE_CONFIGURED", firebaseConfigured.toString())
        buildConfigField("String", "GIT_SHA", "\"${(System.getenv("GITHUB_SHA") ?: "local").take(7)}\"")
    }

    // Google Play Protect blocks apps installed from a browser or file manager that ask to read SMS or
    // notifications. "standard" (EasyLife.apk) leaves those out: bank SMS are pasted or shared in instead.
    // "withSms" (EasyLife-with-SMS.apk) keeps automatic SMS reading for phones where it is allowed.
    flavorDimensions += "distribution"
    productFlavors {
        create("standard") {
            dimension = "distribution"
            isDefault = true
            buildConfigField("boolean", "SMS_AUTO_READ", "false")
        }
        create("withSms") {
            dimension = "distribution"
            buildConfigField("boolean", "SMS_AUTO_READ", "true")
        }
    }

    signingConfigs {
        create("release") {
            if (releaseKeystorePath != null) {
                storeFile = file(releaseKeystorePath)
                storePassword = System.getenv("EASYLIFE_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("EASYLIFE_KEY_ALIAS")
                keyPassword = System.getenv("EASYLIFE_KEY_PASSWORD")
                storeType = "pkcs12"
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Real phones only: x86/x86_64 code is for emulators and would add about 60 MB. Both ARM ABIs stay,
            // because many budget Android Go phones run 32-bit (armeabi-v7a) only.
            ndk { abiFilters += listOf("armeabi-v7a", "arm64-v8a") }
            signingConfig = if (releaseKeystorePath != null) {
                signingConfigs.getByName("release")
            } else {
                // Local/CI non-tag builds: debug-signed so the build still works without secrets.
                signingConfigs.getByName("debug")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    androidResources {
        generateLocaleConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
        // Compressed native libraries: a much smaller download on mobile data; only the phone's own ABI is unpacked.
        jniLibs {
            useLegacyPackaging = true
        }
    }

    lint {
        checkReleaseBuilds = false
        abortOnError = true
        warningsAsErrors = false
        disable += setOf("MissingTranslation", "ObsoleteLintCustomCheck", "GradleDependency", "NewerVersionAvailable", "AndroidGradlePluginVersion")
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

kotlin {
    compilerOptions {
        optIn.addAll(
            "androidx.compose.material3.ExperimentalMaterial3Api",
            "androidx.compose.foundation.ExperimentalFoundationApi",
            "androidx.compose.foundation.layout.ExperimentalLayoutApi",
            "androidx.compose.ui.text.ExperimentalTextApi",
            "kotlinx.serialization.ExperimentalSerializationApi",
            "kotlinx.coroutines.ExperimentalCoroutinesApi",
        )
        freeCompilerArgs.add("-Xannotation-default-target=param-property")
    }
}

room {
    schemaDirectory("$projectDir/schemas")
}

/**
 * Bundles the shared prompt folder (repo root /ai-prompts) into assets/ai/prompts.json so the
 * Gemini provider and the Cloud Function use exactly the same prompts and schemas.
 */
abstract class BundleAiPromptsTask : DefaultTask() {
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val promptsDir: DirectoryProperty

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun bundle() {
        val root = promptsDir.get().asFile
        val tasks = linkedMapOf<String, Any>()
        root.resolve("tasks").listFiles { f -> f.extension == "md" }.orEmpty().sortedBy { it.name }.forEach { md ->
            val name = md.nameWithoutExtension
            val schemaFile = root.resolve("schemas/$name.json")
            val task = linkedMapOf<String, Any?>("instruction" to md.readText().trim())
            if (schemaFile.exists()) task["schema"] = JsonSlurper().parse(schemaFile)
            tasks[name] = task
        }
        val bundle = linkedMapOf(
            "version" to root.resolve("VERSION").readText().trim(),
            "system" to root.resolve("system.md").readText().trim(),
            "tasks" to tasks,
        )
        val out = outputDir.get().asFile.resolve("ai")
        out.mkdirs()
        out.resolve("prompts.json").writeText(JsonOutput.prettyPrint(JsonOutput.toJson(bundle)))
    }
}

val bundleAiPrompts = tasks.register<BundleAiPromptsTask>("bundleAiPrompts") {
    promptsDir.set(rootProject.layout.projectDirectory.dir("ai-prompts"))
    outputDir.set(layout.buildDirectory.dir("generated/aiPrompts"))
}

androidComponents {
    onVariants { variant ->
        variant.sources.assets?.addGeneratedSourceDirectory(bundleAiPrompts, BundleAiPromptsTask::outputDir)
    }
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.activity.compose)
    implementation(libs.appcompat)
    implementation(libs.core.ktx)
    implementation(libs.core.splashscreen)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.process)
    implementation(libs.navigation.compose)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.viewmodel.compose)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.androidx.sqlite)
    implementation(libs.sqlcipher.android)
    implementation(libs.datastore.preferences)
    implementation(libs.work.runtime.ktx)

    implementation(libs.camera.core)
    implementation(libs.camera.camera2)
    implementation(libs.camera.lifecycle)
    implementation(libs.camera.view)
    implementation(libs.biometric)

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.play.services)

    implementation(libs.mlkit.text.recognition)
    implementation(libs.mlkit.translate)
    implementation(libs.mlkit.document.scanner)

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.config)
    implementation(libs.firebase.crashlytics)
    implementation(libs.firebase.ai)
    implementation(libs.firebase.functions)

    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)

    testImplementation(libs.junit)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlinx.coroutines.test)

    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.ext.junit)
}
