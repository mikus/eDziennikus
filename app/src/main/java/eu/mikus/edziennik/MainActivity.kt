package eu.mikus.edziennik

import android.app.ActivityManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.AndroidUiDispatcher
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import com.danimahardhika.cafebar.CafeBar
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.jetradarmobile.snowfall.SnowfallView
import com.mikepenz.iconics.IconicsDrawable
import com.mikepenz.iconics.typeface.library.community.material.CommunityMaterial
import com.mikepenz.iconics.utils.colorInt
import com.mikepenz.iconics.utils.sizeDp
import kotlinx.coroutines.*
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import pl.droidsonroids.gif.GifDrawable
import eu.mikus.edziennik.compat.getColorFromAttr
import eu.mikus.edziennik.data.api.edziennik.EdziennikTask
import eu.mikus.edziennik.data.api.events.*
import eu.mikus.edziennik.data.api.models.ApiError
import eu.mikus.edziennik.data.api.models.Update
import eu.mikus.edziennik.data.db.entity.Metadata.*
import eu.mikus.edziennik.data.db.entity.Profile
import eu.mikus.edziennik.databinding.ActivitySzkolnyBinding
import eu.mikus.edziennik.ext.*
import eu.mikus.edziennik.sync.AppManagerDetectedEvent
import eu.mikus.edziennik.sync.SyncWorker
import eu.mikus.edziennik.sync.UpdateStateEvent
import eu.mikus.edziennik.sync.UpdateWorker
import eu.mikus.edziennik.ui.base.MainSnackbar
import eu.mikus.edziennik.ui.base.ScreenAction
import eu.mikus.edziennik.ui.base.ScreenFab
import eu.mikus.edziennik.ui.base.enums.NavTarget
import eu.mikus.edziennik.ui.base.enums.NavTargetLocation
import eu.mikus.edziennik.ui.base.nav.NavTransition
import eu.mikus.edziennik.ui.base.nav.decideNavigation
import eu.mikus.edziennik.ui.compose.setAppThemeContent
import eu.mikus.edziennik.ui.dialogs.ChangelogDialog
import eu.mikus.edziennik.ui.dialogs.settings.ProfileConfigDialog
import eu.mikus.edziennik.ui.dialogs.sync.ServerMessageDialog
import eu.mikus.edziennik.ui.dialogs.sync.SyncViewListDialog
import eu.mikus.edziennik.ui.dialogs.sync.UpdateAvailableDialog
import eu.mikus.edziennik.ui.dialogs.sync.UpdateProgressDialog
import eu.mikus.edziennik.ui.error.ErrorDetailsDialog
import eu.mikus.edziennik.ui.error.ErrorSnackbar
import eu.mikus.edziennik.ui.event.EventManualDialog
import eu.mikus.edziennik.ui.login.LoginActivity
import eu.mikus.edziennik.ui.messages.list.MessagesFragment
import eu.mikus.edziennik.ui.shell.*
import eu.mikus.edziennik.utils.*
import eu.mikus.edziennik.utils.Utils.d
import eu.mikus.edziennik.utils.managers.UserActionManager
import eu.mikus.edziennik.utils.models.Date
import eu.mikus.edziennik.utils.models.UnreadCounter
import java.io.IOException
import java.util.*
import kotlin.coroutines.CoroutineContext

class MainActivity : AppCompatActivity(), CoroutineScope {
    companion object {
        /** `androidx.activity`'s own default navigation-bar scrim, which is `private` there. */
        private const val DEFAULT_DARK_SCRIM = 0x801b1b1b.toInt()

        private const val TAG = "MainActivity"
    }

    private var job = Job()
    override val coroutineContext: CoroutineContext
        get() = job + Dispatchers.Main

    // Private since N2.4: nothing outside this file may reach the app shell, so a shell swap is a
    // shell-only diff. `b` is now the reduced layout of §5 - rootFrame, the ComposeView and
    // nightlyText - and holds no chrome at all; the chrome reads [state].
    private val b: ActivitySzkolnyBinding by lazy { ActivitySzkolnyBinding.inflate(layoutInflater) }

    /** Every piece of chrome state the M3 shell renders. The public seam methods write these. */
    private val state = ShellState()

    val mainSnackbar: MainSnackbar by lazy { MainSnackbar(this) }
    val errorSnackbar: ErrorSnackbar by lazy { ErrorSnackbar(this) }
    val requestHandler by lazy { MainActivityRequestHandler(this) }

    // Published by AppScaffold's AndroidView factory (§7.8), so it is null until the first
    // traversal - roughly half a second after onResume. Every write goes through `?.`.
    private var swipeRefreshLayout: SwipeRefreshLayoutNoTouch? = null

    /** The last `profileDao().all` emission; [updateProfileList] derives `state.profiles` from it. */
    private var allProfiles = listOf<Profile>()

    /** The last `metadataDao().unreadCounts` emission, so [updateDrawerBadges] can recompute. */
    private var unreadCounters = listOf<UnreadCounter>()

    /**
     * `drawer.currentProfile`, as snapshot state: the toolbar avatar, its subtitle and the drawer
     * header all follow it, and `App.profileId` is not observable.
     */
    private var currentProfileId by mutableStateOf(App.profileId)

    /**
     * Navigations that arrived before `AppScaffold`'s factory created the container (§2.1), drained
     * by [onContainerReady] once it exists. See [runWhenContainerReady] for who produces them.
     */
    private val pendingContainerNavigations = mutableListOf<() -> Unit>()

    /**
     * `gainAttention()`'s request, per screen. navlib rippled the bottom bar only for the 5 screens
     * that ask; passing the bare `!config.ui.bottomSheetOpened` gate to `AppBottomBar` would hint on
     * all 19. Cleared by every navigation.
     */
    private var sheetHintRequested by mutableStateOf(false)

    var onBeforeNavigate: (() -> Boolean)? = null
    private var pausedNavigationData: PausedNavigationData? = null

    val app: App by lazy {
        applicationContext as App
    }

    private val fragmentManager by lazy { supportFragmentManager }
    lateinit var navTarget: NavTarget
        private set
    private var navArguments: Bundle? = null

    // SP2: the 9 feature screens that now own a PullToRefreshBox. The host swipeRefreshLayout wraps the
    // shared @id/fragment container, so its indicator must NOT draw over these (double spinner). All OTHER
    // screens (Notes/Teachers/Notifications/MessageRead/MessageCompose/debug) keep the host spinner.
    private val boxedNavTargets = setOf(
        NavTarget.HOME, NavTarget.TIMETABLE, NavTarget.AGENDA, NavTarget.GRADES, NavTarget.MESSAGES,
        NavTarget.HOMEWORK, NavTarget.BEHAVIOUR, NavTarget.ATTENDANCE, NavTarget.ANNOUNCEMENTS,
    )

    private val navBackStack = mutableListOf<Pair<NavTarget, Bundle?>>()
    private var navLoading = true

    /*     ____           _____                _
          / __ \         / ____|              | |
         | |  | |_ __   | |     _ __ ___  __ _| |_ ___
         | |  | | '_ \  | |    | '__/ _ \/ _` | __/ _ \
         | |__| | | | | | |____| | |  __/ (_| | ||  __/
          \____/|_| |_|  \_____|_|  \___|\__,_|\__\__*/
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        onBackPressedDispatcher.addCallback(this, preCompositionBackCallback)

        d(TAG, "Activity created")

        setTheme(Themes.appTheme)

        app.config.ui.language?.let {
            setLanguage(it)
        }

        app.buildManager.validateBuild(this)

        if (App.profileId == 0) {
            onProfileListEmptyEvent(ProfileListEmptyEvent())
            return
        }

        d(TAG, "Profile is valid, inflating views")

        // Replaces navlib's SystemBarsUtil (§7.7), whose two SDK_INT guards were dead at minSdk 23.
        // The luminance test is the one thing that block did that navlib itself did not: light bar
        // icons when the window background is light, which is not the system dark-mode setting the
        // app theme is independent of.
        val barStyle = if (
            ColorUtils.calculateLuminance(getColorFromAttr(this, android.R.attr.colorBackground)) > 0.6
        )
            // The second argument is the darkScrim, and `EdgeToEdgeApi23` applies it to the
            // navigation bar unconditionally - API 23-25 has no light-navigation-bar mode. Passing
            // TRANSPARENT there leaves light buttons on a transparent bar; this is
            // `enableEdgeToEdge`'s own default for the same reason. API 26+ picks the light scrim.
            SystemBarStyle.light(Color.TRANSPARENT, DEFAULT_DARK_SCRIM)
        else
            SystemBarStyle.dark(Color.TRANSPARENT)
        enableEdgeToEdge(statusBarStyle = barStyle, navigationBarStyle = barStyle)

        setContentView(b.root)

        // Only the nightlyText leg survives: it is the one view left outside the ComposeView. The
        // Scaffold owns its own insets, so padding rootFrame as well would double-pad the shell.
        //
        // No longer gated on API 35: that gate matched a window which only targetSdk 35 forced
        // edge-to-edge, and `enableEdgeToEdge` above now does it on every API - so below 35 the
        // badge would sit under the navigation bar.
        ViewCompat.setOnApplyWindowInsetsListener(b.rootFrame) { _: View, insets: WindowInsetsCompat ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            b.nightlyText.updateLayoutParams<FrameLayout.LayoutParams> {
                this.bottomMargin = 8.dp + bars.bottom
            }
            insets
        }

        // The ONE SnackbarHostState the Scaffold renders (§7.11). Handing either class its own
        // compiles fine and leaves that host permanently invisible.
        mainSnackbar.setHostState(state.snackbarHostState)
        errorSnackbar.setHostState(state.snackbarHostState)

        val versionBadge = app.buildManager.versionBadge
        b.nightlyText.isVisible = versionBadge != null
        b.nightlyText.text = versionBadge
        if (versionBadge != null) {
            b.nightlyText.background.setTintColor(app.buildManager.versionBadgeColor)
        }

        navLoading = true

        state.headerBackground = app.config.ui.headerBackground
        state.miniMenuVisible = app.config.ui.miniMenuVisible

        b.composeView.setAppThemeContent {
            // Everything the shell needs that is not observable state lives here, so it is re-read
            // on recomposition rather than captured once.
            val profile = state.profiles.firstOrNull { it.id == currentProfileId }
            // One Drawable per profile, memoised per emission: AppDrawer keys its painter on the
            // instance, and a fresh Drawable per recomposition would restart an animated .gif avatar
            // every frame. The toolbar's own avatar is resolved separately, because a Drawable has a
            // single callback slot and must not be shared with a second painter.
            val profileImages = remember(state.profiles) {
                val images = state.profiles.associate { it.id to it.getImageDrawable(this) }
                ({ id: Int -> images[id] })
            }

            AppScaffold(
                state = state,
                currentProfileId = currentProfileId,
                profileName = profile?.name.orEmpty(),
                profileImage = remember(profile) { profile?.getImageDrawable(this) },
                profileImages = profileImages,
                sheetBaseRows = sheetBaseRows,
                sheetHintEnabled = sheetHintRequested && !app.config.ui.bottomSheetOpened,
                // CloseOnly and ToggleExpandable never arrive - AppScaffold swallows the first and
                // the "More" group's state is local to AppDrawer - so navigating is all that is left.
                onAction = { action ->
                    when (action) {
                        is DrawerAction.Navigate -> navigate(navTarget = action.target)
                        is DrawerAction.SwitchProfile -> navigate(profileId = action.profileId)
                        else -> {}
                    }
                },
                onProfileLongClick = ::showProfileConfig,
                onProfileSettingClick = { row -> row.target?.let(::onProfileSettingClick) },
                onSyncClick = { SyncViewListDialog(this, navTarget).show() },
                onSheetDismissed = {
                    if (!app.config.ui.bottomSheetOpened)
                        app.config.ui.bottomSheetOpened = true
                },
                onContainerReady = ::onContainerReady,
                onRefreshLayoutReady = { swipeRefreshLayout = it },
            )

            // Registered AFTER AppScaffold on purpose. ModalNavigationDrawer installs its own
            // PredictiveBackHandler *before* it composes this content, and the dispatcher runs the
            // last-registered callback first - so this one wins, which is the only way the
            // openDrawerOnBackPressed branch survives (the drawer would otherwise just close).
            BackHandler { handleBackPressed() }
        }

        navTarget = NavTarget.HOME

        if (savedInstanceState != null) {
            intent?.putExtras(savedInstanceState)
            savedInstanceState.clear()
        }

        app.db.profileDao().all.observe(this) { profiles ->
            allProfiles = profiles
            updateProfileList()
            // navlib's own drawerProfileListEmptyListener, which fired from setProfileList.
            if (state.profiles.isEmpty())
                onProfileListEmptyEvent(ProfileListEmptyEvent())
        }

        setDrawerItems()

        app.db.metadataDao().unreadCounts.observe(this) { unreadCounters ->
            this.unreadCounters = unreadCounters
            updateDrawerBadges()
        }

        // setColorSchemeResources moved into AppScaffold's AndroidView factory (§7.8): the view does
        // not exist yet here, and the first navigation now runs from onContainerReady().

        SyncWorker.scheduleNext(app)
        UpdateWorker.scheduleNext(app)

        // if loaded profile is archived, switch to the up-to-date version of it
        if (app.profile.archived) {
            launch {
                if (app.profile.archiveId != null) {
                    val profile = withContext(Dispatchers.IO) {
                        app.db.profileDao().getNotArchivedOf(app.profile.archiveId!!)
                    }
                    if (profile != null)
                        runWhenContainerReady { navigate(profile = profile) }
                    else
                        runWhenContainerReady { navigate(profileId = 0) }
                } else {
                    runWhenContainerReady { navigate(profileId = 0) }
                }
            }
        }

        // APP BACKGROUND
        setAppBackground()

        // IT'S WINTER MY DUDES
        val today = Date.getToday()
        if ((today.month / 3 % 4 == 0) && app.config.ui.snowfall) {
            b.rootFrame.addView(layoutInflater.inflate(R.layout.snowfall, b.rootFrame, false))
        } else if (app.config.ui.eggfall && BigNightUtil().isDataWielkanocyNearDzisiaj()) {
            val eggfall = layoutInflater.inflate(
                R.layout.eggfall,
                b.rootFrame,
                false
            ) as SnowfallView
            eggfall.setSnowflakeBitmaps(listOf(
                BitmapFactory.decodeResource(resources, R.drawable.egg1),
                BitmapFactory.decodeResource(resources, R.drawable.egg2),
                BitmapFactory.decodeResource(resources, R.drawable.egg3),
                BitmapFactory.decodeResource(resources, R.drawable.egg4),
                BitmapFactory.decodeResource(resources, R.drawable.egg5),
                BitmapFactory.decodeResource(resources, R.drawable.egg6)
            ))
            b.rootFrame.addView(eggfall)
        }

        // WHAT'S NEW DIALOG
        if (app.config.appVersion < BuildConfig.VERSION_CODE) {
            // force an AppSync after update
            app.config.sync.lastAppSync = 0L
            ChangelogDialog(this).show()
            if (app.config.appVersion < 170) {
                //Intent intent = new Intent(this, ChangelogIntroActivity.class);
                //startActivity(intent);
            } else {
                app.config.appVersion = BuildConfig.VERSION_CODE
            }
        }

        // RATE SNACKBAR
        if (app.config.appRateSnackbarTime != 0L && app.config.appRateSnackbarTime <= System.currentTimeMillis()) {
            launch {
                delay(10_000)
                CafeBar.builder(this@MainActivity)
                    .content(R.string.rate_snackbar_text)
                    .icon(IconicsDrawable(this@MainActivity).apply {
                        icon = CommunityMaterial.Icon3.cmd_star_outline
                        sizeDp = 24
                        colorInt = Themes.getPrimaryTextColor(this@MainActivity)
                    })
                    .positiveText(R.string.rate_snackbar_positive)
                    .positiveColor(-0xb350b0)
                    .negativeText(R.string.rate_snackbar_negative)
                    .negativeColor(0xff666666.toInt())
                    .neutralText(R.string.rate_snackbar_neutral)
                    .neutralColor(0xff666666.toInt())
                    .onPositive { cafeBar ->
                        Utils.openGooglePlay(this@MainActivity)
                        cafeBar.dismiss()
                        app.config.appRateSnackbarTime = 0
                    }
                    .onNegative { cafeBar ->
                        Toast.makeText(this@MainActivity,
                            R.string.rate_snackbar_negative_message,
                            Toast.LENGTH_LONG).show()
                        cafeBar.dismiss()
                        app.config.appRateSnackbarTime = 0
                    }
                    .onNeutral { cafeBar ->
                        Toast.makeText(this@MainActivity, R.string.ok, Toast.LENGTH_LONG).show()
                        cafeBar.dismiss()
                        app.config.appRateSnackbarTime =
                            System.currentTimeMillis() + 7 * 24 * 60 * 60 * 1000
                    }
                    .autoDismiss(false)
                    .swipeToDismiss(true)
                    .floating(true)
                    .show()
            }
        }
    }

    /**
     * The bottom sheet's base rows: the `BOTTOM_SHEET` targets, `devModeOnly` ones filtered out.
     * The sync row is not here - `AppSheet` renders it from `onSyncClick`, so it cannot be dropped.
     *
     * No description, exactly as `toBottomSheetItem()` set none.
     */
    private val sheetBaseRows: List<SheetRow> by lazy {
        NavTarget.values().mapNotNull { target ->
            if (target.location != NavTargetLocation.BOTTOM_SHEET)
                return@mapNotNull null
            if (target.devModeOnly && !App.devMode)
                return@mapNotNull null
            val icon = target.icon ?: return@mapNotNull null
            SheetRow(
                titleRes = target.nameRes,
                descriptionRes = null,
                icon = icon,
                separatorBefore = false,
                onClick = { navigate(navTarget = target) },
            )
        }
    }

    /**
     * The three `PROFILE_LIST` rows, from navlib's `drawerProfileSettingClickListener`. `PROFILE_ADD`
     * is the app's only in-app add-profile route: [MainActivityRequestHandler.requestLogin] has
     * exactly two references in the whole app, and this is one of them. `AppScaffold` closes the
     * drawer afterwards.
     */
    private fun onProfileSettingClick(target: NavTarget) {
        when (target) {
            NavTarget.PROFILE_ADD -> {
                requestHandler.requestLogin()
            }
            NavTarget.PROFILE_SYNC_ALL -> {
                EdziennikTask.sync().enqueue(this)
            }
            NavTarget.PROFILE_MARK_AS_READ -> {
                launch {
                    withContext(Dispatchers.Default) {
                        app.db.profileDao().allNow.forEach { profile ->
                            if (!profile.getAppData().uiConfig.enableMarkAsReadAnnouncements)
                                app.db.metadataDao()
                                    .setAllSeenExceptMessagesAndAnnouncements(profile.id, true)
                            else
                                app.db.metadataDao().setAllSeenExceptMessages(profile.id, true)
                        }
                    }
                    Toast.makeText(this@MainActivity,
                        R.string.main_menu_mark_as_read_success,
                        Toast.LENGTH_SHORT).show()
                }
            }
            else -> {
                navigate(navTarget = target)
            }
        }
    }

    /** navlib's `drawerProfileLongClickListener`; the drawer is already closed by `AppScaffold`. */
    private fun showProfileConfig(profileId: Int) {
        launch {
            val appProfile = withContext(Dispatchers.IO) {
                App.db.profileDao().getByIdNow(profileId)
            } ?: return@launch
            ProfileConfigDialog(this@MainActivity, appProfile).show()
        }
    }

    /**
     * The first navigation, which §2.1 measured cannot run from `onCreate`, `onStart` or `onResume`:
     * `AppScaffold`'s `AndroidView` factory builds `R.id.fragment` on the first traversal, and a
     * transaction committed before that crashes on cold start. Also re-commits a fragment left
     * orphaned by a process-death restore (§2.2).
     */
    private fun onContainerReady() {
        handleIntent(intent?.extras)
        // In arrival order, which is the order these ran in before the flip: the intent's own
        // navigation, then a redelivered intent, then the archived-profile switch last.
        while (pendingContainerNavigations.isNotEmpty())
            pendingContainerNavigations.removeAt(0).invoke()
    }

    /**
     * Runs [action] now if the container exists, and from [onContainerReady] otherwise. **Every
     * navigation that can start before the first traversal has to go through this** - a transaction
     * committed against a container that is not there yet throws §2.1's `No view found for id
     * .../fragment`, and the container cannot be made to exist any earlier.
     *
     * Two producers reach it before the container does, and both did their work synchronously in
     * `onCreate` before the flip:
     *  - `onNewIntent`, because `launchMode="singleTop"` makes the system redeliver the start intent
     *    between `onStart` and `onResume` every time it recreates this Activity. Measured: without
     *    this, a process-death restore crashes in `FragmentActivity.onResume`.
     *  - the archived-profile switch below, whose `launch` resumes on a plain main-thread message
     *    while the first traversal waits for a vsync callback.
     */
    private fun runWhenContainerReady(action: () -> Unit) {
        if (findViewById<View>(R.id.fragment) != null)
            action()
        else
            pendingContainerNavigations += action
    }

    /**
     * Back during the ~0.5 s before the first composition, when the `BackHandler` inside it does not
     * exist yet (§2.1). The dispatcher runs the last-registered enabled callback, and that one is
     * registered from the composition's apply pass, so this is shadowed from then on and fires only
     * inside that window - where the platform default would otherwise finish the Activity and
     * `openDrawerOnBackPressed` would exit the app instead of opening the drawer (§8).
     */
    private val preCompositionBackCallback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() = handleBackPressed()
    }

    /**
     * navlib's `onBackPressed()` chain plus the `openDrawerOnBackPressed` gate that used to wrap it,
     * as [shellBackPolicy]. `drawerOpen` is navlib's `isOpen()`, which reports true for as long as
     * the drawer is permanent - passing false there would make back a no-op whenever the setting is
     * on.
     */
    private fun handleBackPressed() {
        val permanent = drawerMode(
            orientation = resources.configuration.orientation,
            screenWidthDp = resources.configuration.screenWidthDp,
            miniMenuVisible = state.miniMenuVisible,
        ) == DrawerMode.Permanent

        val decision = shellBackPolicy(
            sheetOpen = state.sheetVisible,
            drawerOpen = state.drawerState.isOpen || permanent,
            drawerDismissible = !permanent,
            openDrawerOnBack = App.config.ui.openDrawerOnBackPressed,
        )
        when (decision) {
            is ShellBack.CloseSheet -> state.sheetVisible = false
            is ShellBack.CloseDrawer -> closeDrawer()
            is ShellBack.OpenDrawer -> openDrawer()
            is ShellBack.Content -> navigateUp()
        }
    }

    /*     _____
          / ____|
         | (___  _   _ _ __   ___
          \___ \| | | | '_ \ / __|
          ____) | |_| | | | | (__
         |_____/ \__, |_| |_|\___|
                  __/ |
                 |__*/
    @Subscribe(threadMode = ThreadMode.MAIN, sticky = true)
    fun onUpdateEvent(event: Update) {
        EventBus.getDefault().removeStickyEvent(event)
        UpdateAvailableDialog(this, event).show()
    }

    @Subscribe(threadMode = ThreadMode.MAIN, sticky = true)
    fun onUpdateStateEvent(event: UpdateStateEvent) {
        if (!event.running)
            return
        EventBus.getDefault().removeStickyEvent(event)
        UpdateProgressDialog(this, event.update ?: return, event.downloadId).show()
    }

    /**
     * Immediate feedback for a sync started from the bottom sheet's Sync row, mirroring
     * [onApiTaskStartedEvent]'s gate: the boxed screens show it through their Compose
     * PullToRefreshBox (via SyncStatus), every other screen through the host indicator.
     *
     * Without this the row shows nothing until ApiService actually starts and posts
     * ApiTaskStartedEvent, and that is not a bounded wait - IApiTask.enqueue only calls
     * startForegroundService, so the event is posted later, inside ApiService.runTask, after service
     * creation and task.prepare. syncFeature has always marked eagerly for the same reason
     * (SyncStatus.markRefreshing's KDoc); this makes the sheet's row consistent with it.
     */
    fun markSyncStarting() {
        app.syncStatus.markRefreshing()
        if (!::navTarget.isInitialized || navTarget !in boxedNavTargets)
            swipeRefreshLayout?.isRefreshing = true
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onApiTaskStartedEvent(event: ApiTaskStartedEvent) {
        if (!::navTarget.isInitialized || navTarget !in boxedNavTargets)
            swipeRefreshLayout?.isRefreshing = true
        // The subtitle protocol (§7.1) is explicit state now; nulling navlib's two format resources
        // is what used to suppress the steady-state subtitle for the duration of a sync.
        if (event.profileId == App.profileId)
            state.subtitle = SyncSubtitle.Syncing(progress = -1f, text = null)
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onProfileListEmptyEvent(event: ProfileListEmptyEvent) {
        d(TAG, "Profile list is empty. Launch LoginActivity.")
        app.config.loginFinished = false
        startActivity(Intent(this, LoginActivity::class.java))
        finish()
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onApiTaskProgressEvent(event: ApiTaskProgressEvent) {
        // subtitleOf() owns both cases: a negative progress renders the bare text, as this did.
        if (event.profileId == App.profileId)
            state.subtitle = SyncSubtitle.Syncing(event.progress, event.progressText)
    }

    @Subscribe(threadMode = ThreadMode.MAIN, sticky = true)
    fun onApiTaskFinishedEvent(event: ApiTaskFinishedEvent) {
        EventBus.getDefault().removeStickyEvent(event)
        // AppTopBar gives Done its ~2 s lifetime and then falls back to Idle, which is what
        // restoring navlib's two format resources used to do.
        if (event.profileId == App.profileId)
            state.subtitle = SyncSubtitle.Done
    }

    @Subscribe(threadMode = ThreadMode.MAIN, sticky = true)
    fun onApiTaskAllFinishedEvent(event: ApiTaskAllFinishedEvent) {
        EventBus.getDefault().removeStickyEvent(event)
        swipeRefreshLayout?.isRefreshing = false
    }

    @Subscribe(threadMode = ThreadMode.MAIN, sticky = true)
    fun onApiTaskErrorEvent(event: ApiTaskErrorEvent) {
        EventBus.getDefault().removeStickyEvent(event)
        // A failed sync ends the subtitle the same way a finished one does - this path showed
        // "Gotowe" too - and the failure itself is reported by the error snackbar below.
        state.subtitle = SyncSubtitle.Done
        mainSnackbar.dismiss()
        errorSnackbar.addError(event.error).show()
    }

    @Subscribe(threadMode = ThreadMode.MAIN, sticky = true)
    fun onAppManagerDetectedEvent(event: AppManagerDetectedEvent) {
        EventBus.getDefault().removeStickyEvent(event)
        if (app.config.sync.dontShowAppManagerDialog)
            return
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.app_manager_dialog_title)
            .setMessage(R.string.app_manager_dialog_text)
            .setPositiveButton(R.string.ok) { _, _ ->
                try {
                    for (intent in appManagerIntentList) {
                        if (packageManager.resolveActivity(intent,
                                PackageManager.MATCH_DEFAULT_ONLY) != null
                        ) {
                            startActivity(intent)
                        }
                    }
                } catch (e: Exception) {
                    try {
                        startActivity(Intent(Settings.ACTION_SETTINGS))
                    } catch (e: Exception) {
                        e.printStackTrace()
                        Toast.makeText(this, R.string.app_manager_open_failed, Toast.LENGTH_SHORT)
                            .show()
                    }
                }
            }
            .setNeutralButton(R.string.dont_ask_again) { _, _ ->
                app.config.sync.dontShowAppManagerDialog = true
            }
            .setCancelable(false)
            .show()
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onUserActionRequiredEvent(event: UserActionRequiredEvent) {
        app.userActionManager.execute(this, event, UserActionManager.UserActionCallback())
    }

    /*    _____       _             _
         |_   _|     | |           | |
           | |  _ __ | |_ ___ _ __ | |_ ___
           | | | '_ \| __/ _ \ '_ \| __/ __|
          _| |_| | | | ||  __/ | | | |_\__ \
         |_____|_| |_|\__\___|_| |_|\__|__*/
    private val intentReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            handleIntent(intent?.extras)
        }
    }

    fun handleIntent(extras: Bundle?) {
        d(TAG, "handleIntent() {")
        extras?.keySet()?.forEach { key ->
            d(TAG, "    \"$key\": " + extras.get(key))
        }
        d(TAG, "}")

        val intentProfileId = extras.getIntOrNull("profileId").takePositive()
        var intentNavTarget = extras.getIntOrNull("fragmentId").asNavTargetOrNull()

        if (extras?.containsKey("action") == true) {
            val handled = when (extras.getString("action")) {
                "updateRequest" -> {
                    UpdateAvailableDialog(this, app.config.update).show()
                    true
                }
                "serverMessage" -> {
                    ServerMessageDialog(
                        this,
                        extras.getString("serverMessageTitle") ?: getString(R.string.app_name),
                        extras.getString("serverMessageText") ?: ""
                    ).show()
                    true
                }
                "userActionRequired" -> {
                    val event = UserActionRequiredEvent(
                        profileId = extras.getInt("profileId"),
                        type = extras.getEnum<UserActionRequiredEvent.Type>("type") ?: return,
                        params = extras.getBundle("params") ?: return,
                        errorText = 0,
                    )
                    app.userActionManager.execute(this,
                        event,
                        UserActionManager.UserActionCallback())
                    true
                }
                "createManualEvent" -> {
                    val date = extras.getString("eventDate")
                        ?.let { Date.fromY_m_d(it) }
                        ?: Date.getToday()
                    EventManualDialog(
                        this,
                        App.profileId,
                        defaultDate = date
                    ).show()
                    true
                }
                else -> false
            }
            if (handled && !navLoading) {
                return
            }
        }

        if (extras?.containsKey("reloadProfileId") == true) {
            val reloadProfileId = extras.getIntOrNull("reloadProfileId").takePositive()
            if (reloadProfileId == null || app.profile.id == reloadProfileId) {
                reloadTarget()
                return
            }
        }

        extras?.remove("profileId")
        extras?.remove("fragmentId")
        extras?.remove("reloadProfileId")

        /*if (intentTargetId == -1 && navController.currentDestination?.id == R.id.loadingFragment) {
            intentTargetId = navTarget.id
        }*/

        // The container is built by AppScaffold's factory now, so it is looked up rather than bound.
        // Nothing is inflated into it: navlib's fragment_loading placeholder was never visible
        // before this ran, and inflating it now would introduce a flash that users do not see (§5).
        if (navLoading)
            findViewById<ViewGroup>(R.id.fragment)?.removeAllViews()

        when {
            app.profile.id == 0 -> navigate(
                profileId = intentProfileId ?: app.config.lastProfileId,
                navTarget = intentNavTarget,
                args = extras,
            )
            intentProfileId != null -> navigate(
                profileId = intentProfileId,
                navTarget = intentNavTarget,
                args = extras,
            )
            intentNavTarget != null -> navigate(
                navTarget = intentNavTarget,
                args = extras,
            )
            navLoading -> navigate()
            else -> currentProfileId = app.profile.id
        }
        navLoading = false
    }

    override fun recreate() {
        recreate(navTarget)
    }

    fun recreate(navTarget: NavTarget) {
        recreate(navTarget, null)
    }

    fun recreate(navTarget: NavTarget? = null, arguments: Bundle? = null) {
        val intent = Intent(this, MainActivity::class.java)
        if (arguments != null)
            intent.putExtras(arguments)
        if (navTarget != null) {
            intent.putExtra("fragmentId", navTarget.id)
        }
        finish()
        overridePendingTransition(R.anim.fade_in, R.anim.fade_out)
        startActivity(intent)
    }

    override fun onStart() {
        d(TAG, "Activity started")
        super.onStart()
    }

    override fun onStop() {
        d(TAG, "Activity stopped")
        super.onStop()
    }

    override fun onResume() {
        d(TAG, "Activity resumed")
        val filter = IntentFilter()
        filter.addAction(Intent.ACTION_MAIN)
        ActivityCompat.registerReceiver(
            this,
            intentReceiver,
            filter,
            ContextCompat.RECEIVER_EXPORTED,
        )
        EventBus.getDefault().register(this)
        super.onResume()
    }

    override fun onPause() {
        d(TAG, "Activity paused")
        unregisterReceiver(intentReceiver)
        EventBus.getDefault().unregister(this)
        super.onPause()
    }

    override fun onDestroy() {
        d(TAG, "Activity destroyed")
        // Nothing cancelled this before, so every destruction leaked the scope - and recreate() runs
        // on every theme change (SettingsFragment's SettingsEffect.Recreate). All four launch sites
        // are lifecycle-scoped UI/DB work; the only cancellation-visible write (PROFILE_MARK_AS_READ)
        // has no internal suspension point, so it is all-or-nothing.
        job.cancel()
        super.onDestroy()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putExtras("fragmentId" to navTarget)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        val extras = intent?.extras
        runWhenContainerReady { handleIntent(extras) }
    }

    @Suppress("deprecation")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        requestHandler.handleResult(requestCode, resultCode, data)
    }

    /*    _                     _                  _   _               _
         | |                   | |                | | | |             | |
         | |     ___   __ _  __| |  _ __ ___   ___| |_| |__   ___   __| |___
         | |    / _ \ / _` |/ _` | | '_ ` _ \ / _ \ __| '_ \ / _ \ / _` / __|
         | |___| (_) | (_| | (_| | | | | | | |  __/ |_| | | | (_) | (_| \__ \
         |______\___/ \__,_|\__,_| |_| |_| |_|\___|\__|_| |_|\___/ \__,_|__*/
    private fun canNavigate(): Boolean = onBeforeNavigate?.invoke() != false

    fun resumePausedNavigation(): Boolean {
        val data = pausedNavigationData ?: return false
        navigate(
            profileId = data.profileId,
            navTarget = data.navTarget,
            args = data.args,
            skipBeforeNavigate = true,
        )
        pausedNavigationData = null
        return true
    }

    fun navigate(
        profileId: Int? = null,
        profile: Profile? = null,
        navTarget: NavTarget? = null,
        args: Bundle? = null,
        skipBeforeNavigate: Boolean = false,
    ): Boolean {
        d(TAG, "navigate(profileId = ${profile?.id ?: profileId}, target = ${navTarget?.name}, args = $args)")
        if (!(skipBeforeNavigate || navTarget == this.navTarget) && !canNavigate()) {
            state.sheetVisible = false
            closeDrawer()
            // restore the previous profile if changing it with the drawer
            currentProfileId = App.profile.id
            pausedNavigationData = PausedNavigationData(profileId, navTarget, args)
            return false
        }

        val loadNavTarget = navTarget ?: this.navTarget
        if (profile != null && profile.id != App.profileId) {
            navigateImpl(profile, loadNavTarget, args, profileChanged = true)
            return true
        }
        if (profileId != null && profileId != App.profileId) {
            app.profileLoad(profileId) {
                navigateImpl(it, loadNavTarget, args, profileChanged = true)
            }
            return true
        }
        navigateImpl(App.profile, loadNavTarget, args, profileChanged = false)
        return true
    }

    private fun navigateImpl(
        profile: Profile,
        navTarget: NavTarget,
        args: Bundle?,
        profileChanged: Boolean,
    ) {
        d(TAG, "navigateImpl(profileId = ${profile.id}, target = ${navTarget.name}, args = $args)")

        if (navTarget.featureType != null && !profile.hasUIFeature(navTarget.featureType)) {
            navigateImpl(profile, NavTarget.HOME, args, profileChanged)
            return
        }

        if (profileChanged) {
            if (App.profileId != profile.id)
                app.profileLoad(profile)
            MessagesFragment.pageSelection = -1
            // A sync already in flight for the OLD profile posts its ApiTaskFinishedEvent carrying
            // that profile's id, and onApiTaskFinishedEvent drops it because it no longer matches
            // App.profileId - so without this reset the subtitle stays on "Syncing…" indefinitely.
            // The new profile's own sync, if one starts, sets it again straight away.
            state.subtitle = SyncSubtitle.Idle
            // set new drawer items for this profile
            setDrawerItems()
            // Rebuilds the rendered profile list for the new profile - which is both halves of what
            // removeProfileById() + prependProfile() did - and moves the header onto it.
            updateProfileList()
        }

        val decision = decideNavigation(
            current = this.navTarget,
            currentArguments = this.navArguments,
            stack = navBackStack,
            target = navTarget,
            requestedArguments = args,
        )
        val arguments = decision.arguments ?: Bundle()
        // The chrome reset: the sheet closes and drops the previous screen's contextual rows, the
        // drawer closes and follows the new selection, and the FAB goes back to "no primary action"
        // until this screen's setScreenFab(...) arrives.
        state.sheetVisible = false
        state.actions = emptyList()
        sheetHintRequested = false
        closeDrawer()
        state.selectedTarget = navTarget
        state.title = getString(navTarget.titleRes ?: navTarget.nameRes)
        state.fab = null
        state.fabExtended = false

        d("NavDebug", "Navigating from ${this.navTarget.name} to ${navTarget.name}")

        val fragment = navTarget.fragmentClass?.newInstance() ?: return
        fragment.arguments = arguments
        val transaction = fragmentManager.beginTransaction()

        transaction.setCustomAnimations(
            when (decision.transition) {
                NavTransition.RELOAD -> R.anim.fade_in
                NavTransition.POP -> R.anim.task_close_enter
                NavTransition.PUSH -> R.anim.task_open_enter
            },
            when (decision.transition) {
                NavTransition.RELOAD -> R.anim.fade_out
                NavTransition.POP -> R.anim.task_close_exit
                NavTransition.PUSH -> R.anim.task_open_exit
            },
        )

        navBackStack.clear()
        navBackStack.addAll(decision.stack)
        this.navTarget = decision.target
        // RELOAD deliberately leaves navArguments alone, exactly as the inline version did.
        // Do not delete this guard on the strength of a green unit suite - the policy cannot
        // express it, so nothing in NavStackPolicyTest covers it.
        if (decision.transition != NavTransition.RELOAD)
            this.navArguments = arguments

        d("NavDebug", "Current fragment ${navTarget.name}, back stack:")
        navBackStack.forEachIndexed { index, item ->
            d("NavDebug", " - $index: ${item.first.name}")
        }

        transaction.replace(R.id.fragment, fragment)
        transaction.commitAllowingStateLoss()

        // TASK DESCRIPTION
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            val bm = BitmapFactory.decodeResource(resources, R.mipmap.ic_launcher)

            @Suppress("deprecation")
            val taskDesc = ActivityManager.TaskDescription(
                if (navTarget == NavTarget.HOME)
                    getString(R.string.app_name)
                else
                    getString(R.string.app_task_format, getString(navTarget.nameRes)),
                bm,
                getColorFromAttr(this, R.attr.colorSurface)
            )
            setTaskDescription(taskDesc)
        }
        return
    }

    fun reloadTarget() = navigate()

    private fun popBackStack(skipBeforeNavigate: Boolean = false): Boolean {
        if (navBackStack.size == 0) {
            return false
        }
        // TODO back stack argument support
        if (navTarget.popTo != null) {
            navigate(
                navTarget = navTarget.popTo,
                skipBeforeNavigate = skipBeforeNavigate,
            )
        } else {
            navBackStack.last().let {
                navigate(
                    navTarget = it.first,
                    args = it.second,
                    skipBeforeNavigate = skipBeforeNavigate,
                )
            }
        }
        return true
    }

    fun navigateUp(skipBeforeNavigate: Boolean = false) {
        if (!popBackStack(skipBeforeNavigate)) {
            finish()
        }
    }

    /**
     * Draw the user's attention to the bottom sheet, because something in it has changed.
     *
     * The pixel-targeted ripple navlib drew over the bottom bar has no Compose equivalent (§7.10),
     * so `AppBottomBar` pulses the sheet button instead - same place, same 2 s delay, and it cancels
     * on navigation instead of firing into the next screen. The flag is what keeps the hint per
     * screen: only the 5 screens that call this ask for it, as today.
     */
    fun gainAttention() {
        if (app.config.ui.bottomSheetOpened)
            return
        sheetHintRequested = true
    }

    /**
     * Kept as a no-op: `AppBottomBar`'s `FabAttentionEffect` now runs this pulse for every screen
     * that arms a FAB, keyed on the current target so it cannot outlive the screen that asked -
     * which is what the three uncancelled `postDelayed` calls here used to do. The signature stays
     * because 6 fragments call it.
     */
    @Suppress("unused")
    fun gainAttentionFAB() = Unit

    fun setAppBackground() {
        try {
            b.root.background = app.config.ui.appBackground?.let {
                if (it.endsWith(".gif"))
                    GifDrawable(it)
                else
                    BitmapDrawable.createFromPath(it)
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    /*    _____                                _ _
         |  __ \                              (_) |
         | |  | |_ __ __ ___      _____ _ __   _| |_ ___ _ __ ___  ___
         | |  | | '__/ _` \ \ /\ / / _ \ '__| | | __/ _ \ '_ ` _ \/ __|
         | |__| | | | (_| |\ V  V /  __/ |    | | ||  __/ | | | | \__ \
         |_____/|_|  \__,_| \_/\_/ \___|_|    |_|\__\___|_| |_| |_|__*/
    /**
     * The drawer's rows, both lists: `DrawerEntries.main` for the drawer and the rail,
     * `DrawerEntries.profileSettings` for the three `PROFILE_LIST` rows inside the expanded profile
     * list. The second one is not optional - it carries the only in-app add-profile route.
     *
     * The profile list is closed first, as `drawer.profileSelectionClose()` did before `setItems`.
     */
    fun setDrawerItems() {
        d("NavDebug", "setDrawerItems() app.profile = ${app.profile}")
        val entries = buildDrawerEntries(
            targets = NavTarget.values().toList(),
            devMode = App.devMode,
            profileFeatures = app.profile.loginStoreType.features,
            miniMenuButtons = app.config.ui.miniMenuButtons,
        )
        state.profileSelectionOpen = false
        state.drawerEntries = entries.main
        state.profileSettings = entries.profileSettings
    }

    /**
     * `state.profiles`, i.e. what `setProfileList` + `prependProfile` + `currentProfile` produced:
     * every non-archived profile (or every profile, if they all are), with the loaded profile
     * prepended under an "archive" name when it is archived and the others are not.
     */
    private fun updateProfileList() {
        val allArchived = allProfiles.all { it.archived }
        val profiles = allProfiles.filter {
            it.id >= 0 && (!it.archived || allArchived)
        }.toMutableList()
        //prepend the archived profile if loaded
        if (app.profile.archived && !allArchived) {
            profiles.add(0, Profile(
                id = app.profile.id,
                loginStoreId = app.profile.loginStoreId,
                loginStoreType = app.profile.loginStoreType,
                name = app.profile.name,
                // (other fields are not needed by the drawer)
                subname = getString(R.string.profile_archived_subname_format, app.profile.subname)
            ).also {
                it.archived = true
            })
        }
        state.profiles = profiles
        currentProfileId = App.profileId
        // The per-profile badges are keyed by profile id, so they follow the rendered list.
        updateDrawerBadges()
    }

    fun error(error: ApiError) = errorSnackbar.addError(error).show()
    fun snackbar(
        text: String,
        actionText: String? = null,
        onClick: (() -> Unit)? = null,
    ) = mainSnackbar.snackbar(text, actionText, onClick)

    fun snackbarDismiss() = mainSnackbar.dismiss()

    /**
     * Replace the current screen's menu rows. Replace-semantics and idempotent: a screen may
     * re-declare when its state changes (MessagesCompose grows a discard-draft row this way).
     *
     * These are the sheet's *contextual* rows; the shell's own base rows are separate state, which
     * is what navlib's `isContextual` flag used to express.
     */
    fun setScreenActions(actions: List<ScreenAction>) {
        state.actions = actions
    }

    /**
     * Set or clear the current screen's primary action. `null` means "no primary action right now" —
     * the only way to express that without the caller touching `fabEnable`.
     *
     * Deliberately does NOT call [gainAttentionFAB] — Timetable must not pulse on every page change.
     */
    fun setScreenFab(fab: ScreenFab?) {
        state.fab = fab
    }

    /** Drawer delegates (N2.4). Plain 1:1 pass-throughs so nothing outside this file names the shell.
     *  [setDrawerItems] is the precedent for a public shell method that owns the drawer — it is an
     *  item builder rather than a delegate, but MiniMenuConfigDialog already calls it beside
     *  updateDrawerBadges(), so these are its one-line siblings. Deliberately NOT combined: callers keep
     *  their own composition (e.g. SettingsFragment's set-null-then-set-value refresh). */
    fun openDrawer() {
        // A permanent drawer has no closed state to open from, and navlib's setOpen returned early
        // there for the same reason.
        if (drawerMode(
                orientation = resources.configuration.orientation,
                screenWidthDp = resources.configuration.screenWidthDp,
                miniMenuVisible = state.miniMenuVisible,
            ) == DrawerMode.Permanent
        ) return
        launch(AndroidUiDispatcher.Main) { state.drawerState.open() }
    }

    /**
     * Closing is safe in every mode: a permanent drawer's state is already closed. Guarded on
     * `isOpen` - which is a plain state read - because `close()` animates, and animating needs the
     * `Density` the drawer composable injects; `navigate()` can run before the first composition.
     *
     * [AndroidUiDispatcher.Main] rather than this scope's plain `Dispatchers.Main`, here and in
     * [openDrawer]: `DrawerState.close()` suspends on `withFrameNanos`, and a context with no
     * `MonotonicFrameClock` throws `IllegalStateException` instead of animating. Measured on the
     * emulator - it took down every drawer navigation.
     */
    private fun closeDrawer() {
        state.profileSelectionOpen = false
        if (state.drawerState.isOpen)
            launch(AndroidUiDispatcher.Main) { state.drawerState.close() }
    }

    /** navlib's own implementation did both halves; a flag alone leaves the switcher unreachable. */
    fun openProfileSelection() {
        state.profileSelectionOpen = true
        openDrawer()
    }

    /**
     * `null` falls back to `R.drawable.header`. The token forces the re-decode: every pick
     * overwrites the same `filesDir/header.<ext>`, so the path alone never changes, and
     * `SettingsFragment`'s null-then-value pair lands in a single frame.
     */
    fun setDrawerHeaderBackground(background: String?) {
        state.headerBackground = background
        state.headerBackgroundToken++
    }

    /** All five badge surfaces (§7.9), not just the drawer rows. */
    fun updateDrawerBadges() {
        state.badges = deriveBadges(
            unreadCounts = unreadCounters,
            profiles = state.profiles,
            targets = NavTarget.values().toList(),
            currentProfileId = App.profileId,
        )
    }

    /**
     * Writes the **raw** setting, never a derived value: in landscape 480-899 dp the rail is up
     * regardless of it, so storing "is the rail showing" would latch and survive a rotation into
     * portrait. The rail's real visibility is `drawerMode(...)`, computed in the composition.
     */
    fun setMiniDrawerVisible(visible: Boolean) {
        app.config.ui.miniMenuVisible = visible
        state.miniMenuVisible = visible
    }
}
