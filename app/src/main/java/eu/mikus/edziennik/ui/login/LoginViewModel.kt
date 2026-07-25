/*
 * Copyright (c) Mikolaj Olszewski 2026-7-25.
 */

package eu.mikus.edziennik.ui.login

import android.content.Context
import android.os.Bundle
import androidx.annotation.DrawableRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import eu.mikus.edziennik.App
import eu.mikus.edziennik.config.AppData
import eu.mikus.edziennik.data.api.edziennik.EdziennikTask
import eu.mikus.edziennik.data.api.events.ApiTaskAllFinishedEvent
import eu.mikus.edziennik.data.api.events.ApiTaskErrorEvent
import eu.mikus.edziennik.data.api.events.ApiTaskProgressEvent
import eu.mikus.edziennik.data.api.events.ApiTaskStartedEvent
import eu.mikus.edziennik.data.api.events.FirstLoginFinishedEvent
import eu.mikus.edziennik.data.api.events.UserActionRequiredEvent
import eu.mikus.edziennik.data.api.models.ApiError
import eu.mikus.edziennik.data.db.entity.LoginStore
import eu.mikus.edziennik.data.db.entity.Profile
import eu.mikus.edziennik.data.db.enums.LoginMode
import eu.mikus.edziennik.data.db.enums.LoginType
import eu.mikus.edziennik.ext.getEnum
import eu.mikus.edziennik.ext.joinNotNullStrings
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import kotlin.math.max

/**
 * Activity-scoped source of truth for the whole login flow's shared state, and the SINGLE
 * EventBus subscriber for the 6 login events (replacing the LoginActivity mutable fields
 * profiles/loginStores/lastError). Continuous state is exposed as StateFlows; one-shot
 * navigation/error results as buffered Channels collected by the single active host. All
 * App/DB/enqueue edges are injected seams (bound in [Factory]) so the event->state logic is
 * unit-testable; EventBus register/unregister is the only un-injected edge.
 */
class LoginViewModel(
    private val dbLastProfileId: () -> Int,
    private val persist: suspend (List<Profile>, List<LoginStore>) -> Unit,
    private val enqueueFirstLogin: (Context, LoginStore) -> Unit,
    private val enqueueSync: (Context, Set<Int>) -> Unit,
    private val resolveModeIcon: (LoginStore) -> Int,
    private val dispatcher: CoroutineDispatcher,
) : ViewModel() {

    data class LoginSummaryItem(val profile: Profile, @DrawableRes val modeIcon: Int, val isSelected: Boolean)
    data class LoginSyncUiState(val progress: Float? = null, val startedProfileName: String? = null, val progressText: String? = null)
    sealed interface LoginResult { object ToSummary : LoginResult; object NoStudents : LoginResult; object Error : LoginResult }
    sealed interface SyncResult { object ToFinish : SyncResult; object ToSyncError : SyncResult }
    private enum class Phase { Progress, Sync }

    private val _profiles = MutableStateFlow<List<LoginSummaryItem>>(emptyList())
    val profiles: StateFlow<List<LoginSummaryItem>> = _profiles.asStateFlow()

    private val _syncState = MutableStateFlow(LoginSyncUiState())
    val syncState: StateFlow<LoginSyncUiState> = _syncState.asStateFlow()

    private val loginStores = mutableListOf<LoginStore>()
    val hasLoginStores: Boolean get() = loginStores.isNotEmpty()

    var lastError: ApiError? = null
        private set
    var firstProfileId: Int = 0
        private set

    private var phase: Phase = Phase.Progress

    private val _loginResult = Channel<LoginResult>(Channel.BUFFERED)
    val loginResult = _loginResult.receiveAsFlow()
    private val _syncResult = Channel<SyncResult>(Channel.BUFFERED)
    val syncResult = _syncResult.receiveAsFlow()
    private val _userActionEvents = Channel<UserActionRequiredEvent>(Channel.BUFFERED)
    val userActionEvents = _userActionEvents.receiveAsFlow()
    private val _errorEvents = Channel<ApiError>(Channel.BUFFERED)
    val errorEvents = _errorEvents.receiveAsFlow()

    init {
        EventBus.getDefault().register(this)
    }

    override fun onCleared() {
        EventBus.getDefault().unregister(this)
        super.onCleared()
    }

    fun startFirstLogin(context: Context, args: Bundle) {
        phase = Phase.Progress
        EventBus.getDefault().removeStickyEvent(FirstLoginFinishedEvent::class.java)
        viewModelScope.launch(dispatcher) {
            val maxId = max(
                withContext(Dispatchers.IO) { dbLastProfileId() },
                _profiles.value.maxOfOrNull { it.profile.id } ?: 0,
            )
            val loginType = args.getEnum<LoginType>("loginType") ?: return@launch
            val loginMode = args.getEnum<LoginMode>("loginMode") ?: return@launch
            val store = LoginStore(id = maxId + 1, type = loginType, mode = loginMode)
            store.copyFrom(args)
            store.removeLoginData("loginType")
            store.removeLoginData("loginMode")
            enqueueFirstLogin(context, store)
        }
    }

    fun toggleSelection(profileId: Int) {
        _profiles.value = _profiles.value.map {
            if (it.profile.id == profileId) it.copy(isSelected = !it.isSelected) else it
        }
    }

    fun persistAndSync(context: Context) {
        phase = Phase.Sync
        EventBus.getDefault().removeStickyEvent(ApiTaskAllFinishedEvent::class.java)
        EventBus.getDefault().removeStickyEvent(ApiTaskErrorEvent::class.java)
        viewModelScope.launch(dispatcher) {
            val sel = LoginSyncSelection.selectedForSync(_profiles.value, loginStores)
            persist(sel.profiles, sel.loginStores)
            firstProfileId = sel.profiles.firstOrNull()?.id ?: 0
            enqueueSync(context, sel.profiles.map { it.id }.toSet())
        }
    }

    fun clearError() { lastError = null }

    fun reportError(error: ApiError) {
        lastError = error
        _errorEvents.trySend(error)
    }

    @Subscribe(threadMode = ThreadMode.MAIN, sticky = true)
    fun onFirstLoginFinished(event: FirstLoginFinishedEvent) {
        EventBus.getDefault().removeStickyEvent(event)
        if (event.profileList.isEmpty()) {
            _loginResult.trySend(LoginResult.NoStudents)
            return
        }
        for (profile in event.profileList) {
            profile.subname = joinNotNullStrings(
                " - ",
                profile.studentClassName,
                "${profile.studentSchoolYearStart}/${profile.studentSchoolYearStart + 1}",
            )
        }
        loginStores += event.loginStore
        val icon = resolveModeIcon(event.loginStore)
        _profiles.value = _profiles.value + event.profileList.map { LoginSummaryItem(it, icon, isSelected = true) }
        _loginResult.trySend(LoginResult.ToSummary)
    }

    @Subscribe(threadMode = ThreadMode.MAIN, sticky = true)
    fun onApiTaskError(event: ApiTaskErrorEvent) {
        EventBus.getDefault().removeStickyEvent(event)
        reportError(event.error)
        when (phase) {
            Phase.Sync -> _syncResult.trySend(SyncResult.ToSyncError)
            Phase.Progress -> _loginResult.trySend(LoginResult.Error)
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onUserActionRequired(event: UserActionRequiredEvent) {
        _userActionEvents.trySend(event)
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onApiTaskStarted(event: ApiTaskStartedEvent) {
        _syncState.value = _syncState.value.copy(startedProfileName = event.profile?.name)
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onApiTaskProgress(event: ApiTaskProgressEvent) {
        _syncState.value = _syncState.value.copy(
            progress = if (event.progress <= 0f) null else event.progress,
            progressText = event.progressText,
        )
    }

    @Subscribe(threadMode = ThreadMode.MAIN, sticky = true)
    fun onApiTaskAllFinished(event: ApiTaskAllFinishedEvent) {
        EventBus.getDefault().removeStickyEvent(event)
        _syncResult.trySend(SyncResult.ToFinish)
    }

    class Factory(private val app: App) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = LoginViewModel(
            dbLastProfileId = { app.db.profileDao().lastId ?: 0 },
            persist = { profiles, stores ->
                withContext(Dispatchers.IO) {
                    profiles.forEach {
                        val data = AppData.get(it.loginStoreType)
                        for ((key, value) in data.configOverrides) it.config.set(key, value)
                        app.db.eventTypeDao().addDefaultTypes(it)
                    }
                    app.db.profileDao().addAll(profiles)
                    app.db.loginStoreDao().addAll(stores)
                }
            },
            enqueueFirstLogin = { ctx, store -> EdziennikTask.firstLogin(store).enqueue(ctx) },
            enqueueSync = { ctx, ids -> EdziennikTask.syncProfileList(ids).enqueue(ctx) },
            resolveModeIcon = { store ->
                LoginInfo.list.firstOrNull { it.loginType == store.type }
                    ?.loginModes?.firstOrNull { it.loginMode == store.mode }?.icon ?: 0
            },
            dispatcher = Dispatchers.Main,
        ) as T
    }
}
