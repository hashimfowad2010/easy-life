package com.easylife.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.easylife.app.core.cloud.FirebaseGateway
import com.easylife.app.core.cloud.RemoteConfigRepository
import com.easylife.app.core.data.prefs.PrefsRepository
import com.easylife.app.core.data.prefs.UserPrefs
import com.easylife.app.core.data.repo.CategoryRepository
import com.easylife.app.core.impact.ImpactEvents
import com.easylife.app.core.impact.ImpactTracker
import com.easylife.app.core.scan.ScanDraftStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MainState(val loading: Boolean = true, val prefs: UserPrefs = UserPrefs())

/** Something another app or a notification asked us to open. */
sealed interface DeepLink {
    data class Route(val path: String) : DeepLink
    data class SharedText(val text: String) : DeepLink
    data class SharedImages(val uris: List<android.net.Uri>) : DeepLink
}

@HiltViewModel
class MainViewModel @Inject constructor(
    private val prefsRepo: PrefsRepository,
    private val config: RemoteConfigRepository,
    private val firebase: FirebaseGateway,
    private val categories: CategoryRepository,
    private val drafts: ScanDraftStore,
    private val impact: ImpactTracker,
) : ViewModel() {

    val state: StateFlow<MainState> = prefsRepo.prefs
        .map { MainState(loading = false, prefs = it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, MainState())

    private val _deepLink = MutableStateFlow<DeepLink?>(null)
    val deepLink: StateFlow<DeepLink?> = _deepLink.asStateFlow()

    init {
        viewModelScope.launch {
            prefsRepo.ensureInstallInfo()
            runCatching { categories.ensureDefaults() }
            drafts.cleanupOlderThan(24)
            impact.log(ImpactEvents.APP_OPEN, "app")
            firebase.ensureSignedIn()
            config.refresh()
        }
    }

    fun offerDeepLink(link: DeepLink) {
        _deepLink.value = link
    }

    fun consumeDeepLink() {
        _deepLink.value = null
    }
}
