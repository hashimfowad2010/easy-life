package com.easylife.app.core.cloud

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.easylife.app.BuildConfig
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.auth
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single entry point to Firebase. The APK can be built without google-services.json (Firebase OFF);
 * every caller checks [isConfigured] first so nothing crashes in that case.
 */
@Singleton
class FirebaseGateway @Inject constructor(@ApplicationContext private val context: Context) {

    val isConfigured: Boolean
        get() = BuildConfig.FIREBASE_CONFIGURED && FirebaseApp.getApps(context).isNotEmpty()

    /** Anonymous sign-in (no name, email or phone). Returns the anonymous uid, or null if offline/unavailable. */
    suspend fun ensureSignedIn(): String? {
        if (!isConfigured) return null
        return runCatching {
            val auth = Firebase.auth
            auth.currentUser?.uid ?: auth.signInAnonymously().await().user?.uid
        }.getOrNull()
    }

    fun isOnline(): Boolean {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return false
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    fun isOnUnmeteredNetwork(): Boolean {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return false
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
    }
}
