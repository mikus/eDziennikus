/*
 * Copyright (c) Mikolaj Olszewski 2026-9-2.
 */

package eu.mikus.edziennik.ui.shell

import android.content.Context
import android.content.ContextWrapper
import android.graphics.drawable.Drawable
import android.view.View
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.fragment.app.FragmentActivity
import eu.mikus.edziennik.ui.base.AppSnackbarHost
import eu.mikus.edziennik.R
import eu.mikus.edziennik.utils.SwipeRefreshLayoutNoTouch
import kotlinx.coroutines.launch

/**
 * navlib leaves `DrawerLayout`'s scrim alone, so the modal drawer dims with `DEFAULT_SCRIM_COLOR` =
 * 60 % black. `DrawerDefaults.scrimColor` is `colorScheme.scrim` at 32 %, i.e. visibly lighter *and*
 * palette-dependent, so it is passed explicitly - the same reason `AppSheet`'s `ScrimColor` is.
 */
private val DrawerScrimColor = Color(0x99000000)

/**
 * `nv_miniDrawerElevation` (`nav_view.xml`): a 4 dp horizontal gradient strip that navlib shows at
 * the content region's leading edge in **both** [DrawerMode.Mini] and [DrawerMode.Permanent]
 * (`decideDrawerMode`). `AppRail` draws its own copy for Mini, so this one exists only for Permanent
 * - hence the duplicated constants: `AppRail`'s `RailShadowWidth`/`RailShadowColors` are private to
 * that file and hoisting them would mean editing it. If a later phase needs a third copy, hoist then.
 *
 * It lives **inside** the `Scaffold` body, not beside the permanent drawer outside it, because
 * navlib's strip is a child of `nv_content` - inside the `CoordinatorLayout`, i.e. below the toolbar
 * and above the bottom bar - while the permanent drawer (`nv_drawerContainerLandscape`) is a sibling
 * outside it. Same reasoning as the rail's placement (design §14.2).
 */
private val ContentEdgeShadowWidth = 4.dp
private val ContentEdgeShadowColors = listOf(Color(0x40000000), Color(0x00000000))

/**
 * The app shell: the drawer, the toolbar, the bottom bar with its FAB, the icon rail, the snackbar
 * host, the bottom sheet, and the `FrameLayout(R.id.fragment)` that all 19 fragment-backed
 * destinations are still committed into. Design §5 and §2.
 *
 * Hosted by `activity_szkolny.xml`'s `ComposeView` via `setAppThemeContent`, which is why the app
 * background, the version badge and the seasonal overlays need nothing here - they stay in XML,
 * behind this composition (§7.12).
 *
 * ### Exactly one [ModalNavigationDrawer] call site, in every [DrawerMode]
 *
 * [ModalNavigationDrawer] and `PermanentNavigationDrawer` are different composables, so branching
 * between them around this subtree would give the content a new group identity on every 900 dp
 * crossing: [AndroidView]'s factory re-runs, a **fresh** `FrameLayout(R.id.fragment)` is created, the
 * restored fragment's view stays parented to the discarded one, §2.2's orphan guard reads
 * `parent != null` and does nothing, and the content region goes blank with correct chrome around it.
 * So in [DrawerMode.Permanent] the drawer state stays closed, gestures are off, `drawerContent` is
 * empty, and [AppDrawer] is rendered as the **first child of the content `Row`** at
 * [PermanentDrawerWidth] - which is navlib's own shape, `nv_drawerContainerLandscape` being a
 * horizontal sibling of the `CoordinatorLayout`. A conditional `Row` sibling (the rail, the permanent
 * drawer) is safe in a way that branching composables is not: it leaves the `Scaffold`'s group alone.
 *
 * ### The mode is derived here, per composition
 *
 * [drawerMode] over `LocalConfiguration` and [ShellState.miniMenuVisible], so `MainActivity`'s
 * `configChanges="orientation|screenSize"` recomposes the whole shell on rotation with no Activity
 * restart and no new dependency, and `setMiniDrawerVisible` takes effect through the state read. The
 * rail is rendered from `mode == `[DrawerMode.Mini], and that value is **never written back** into
 * [ShellState]: it is a rendering decision, storing it would be a write/read loop, and it would latch
 * across rotation, since landscape 480-899 dp is Mini whatever the setting says.
 *
 * The setting is read off [state] rather than taken as a parameter on purpose. A `Boolean` parameter
 * is the shape that invites `App.config.ui.miniMenuVisible` at the `setAppThemeContent` call site -
 * a non-state read, captured once, leaving the Settings toggle inert with nothing failing.
 * @param profileImage the current profile's avatar for the toolbar, resolved by the caller from
 *   `Profile.getImageDrawable`. Kept separate from [profileImages] so the toolbar and the drawer
 *   header never share one `Drawable` - an animated `GifDrawable` has a single callback slot.
 * @param profileImages resolves any profile id to its avatar, for the drawer header and the profile
 *   switcher.
 * @param sheetBaseRows [AppSheet]'s `baseRows`: the `BOTTOM_SHEET` `NavTarget` rows, dev-only ones
 *   already filtered. It is a parameter because building it needs `NavTarget` and `App.devMode`.
 * @param sheetHintEnabled `!app.config.ui.bottomSheetOpened` - the gate `gainAttention()` applies
 *   before hinting at the sheet button.
 * @param onSyncClick shows `SyncViewListDialog(activity, navTarget)`; [AppSheet] closes itself.
 * @param onSheetDismissed latches `app.config.ui.bottomSheetOpened = true`. [AppSheet] clears
 *   [ShellState.sheetVisible] itself, so this callback owns only the config write.
 * @param onAction the [DrawerAction]s from the drawer and the rail, **already stripped of the
 *   shell's own half**: this file closes the drawer, clears the profile list and swallows
 *   [DrawerAction.CloseOnly], so the caller only has to `navigate(...)` (§7.5).
 * @param onProfileLongClick shows `ProfileConfigDialog`; the drawer is closed for you first. The
 *   toolbar avatar's long press aliases it for the current profile, as `MainActivity.kt:285` does.
 * @param onContainerReady the **first navigation** - `navTarget = NavTarget.HOME` and
 *   `handleIntent(intent?.extras)`, which cannot stay in `onCreate` (§2.1). Also re-commits a
 *   fragment left orphaned by a process-death restore (§2.2).
 * @param onRefreshLayoutReady publishes the `SwipeRefreshLayoutNoTouch` the factory built, so
 *   `isRefreshing` still has an owner. It is null until the first traversal, so every write to it
 *   must go through `?.`.
 */
@Composable
fun AppScaffold(
    state: ShellState,
    currentProfileId: Int,
    profileName: String,
    profileImage: Drawable?,
    profileImages: (Int) -> Drawable?,
    sheetBaseRows: List<SheetRow>,
    sheetHintEnabled: Boolean,
    onAction: (DrawerAction) -> Unit,
    onProfileLongClick: (Int) -> Unit,
    onProfileSettingClick: (DrawerRow) -> Unit,
    onSyncClick: () -> Unit,
    onSheetDismissed: () -> Unit,
    onContainerReady: () -> Unit,
    onRefreshLayoutReady: (SwipeRefreshLayoutNoTouch) -> Unit,
) {
    val configuration = LocalConfiguration.current
    val mode = drawerMode(
        orientation = configuration.orientation,
        screenWidthDp = configuration.screenWidthDp,
        miniMenuVisible = state.miniMenuVisible,
    )
    val scope = rememberCoroutineScope()

    // A permanent drawer has no closed state to open from, and navlib's own `toggle()` reduces to a
    // no-op there (`isOpen()` reports true for as long as the drawer is fixed, so it calls `close()`
    // on a `DrawerLayout` that ignores it). Opening only, never toggling: while the modal drawer is
    // open its scrim covers the hamburger, so the closing half is unreachable.
    val openDrawer = {
        if (mode != DrawerMode.Permanent)
            scope.launch { state.drawerState.open() }
    }
    val closeDrawer = {
        if (mode != DrawerMode.Permanent)
            scope.launch { state.drawerState.close() }
    }

    // §7.5's table, in one place: every arm closes the drawer and leaves the profile list closed;
    // only `CloseOnly` - the row that is already selected, or the profile that is already current -
    // stops there. `ToggleExpandable` never arrives: the "More" group's open state is local to
    // `AppDrawer`. Clearing `profileSelectionOpen` is a no-op for a nav row (those only render while
    // the list is closed) and is what navlib's own `AccountHeaderView` does when a profile is picked.
    val drawerAction: (DrawerAction) -> Unit = { action ->
        if (action !is DrawerAction.CloseOnly)
            onAction(action)
        state.profileSelectionOpen = false
        closeDrawer()
    }

    // "Explicit close before the dialog", per §7.5's long-press row.
    val profileLongClick: (Int) -> Unit = { id ->
        closeDrawer()
        onProfileLongClick(id)
    }

    // "Run the action, then close", per §7.5's profile-setting row.
    val profileSettingClick: (DrawerRow) -> Unit = { row ->
        onProfileSettingClick(row)
        closeDrawer()
    }

    Box(Modifier.fillMaxSize()) {
        ModalNavigationDrawer(
            drawerState = state.drawerState,
            gesturesEnabled = mode != DrawerMode.Permanent,
            scrimColor = DrawerScrimColor,
            drawerContent = {
                if (mode != DrawerMode.Permanent)
                    AppDrawer(
                        state = state,
                        currentProfileId = currentProfileId,
                        profileImages = profileImages,
                        onAction = drawerAction,
                        onProfileLongClick = profileLongClick,
                        onProfileSettingClick = profileSettingClick,
                    )
            },
        ) {
            Row(Modifier.fillMaxSize()) {
                if (mode == DrawerMode.Permanent)
                    AppDrawer(
                        state = state,
                        currentProfileId = currentProfileId,
                        profileImages = profileImages,
                        onAction = drawerAction,
                        onProfileLongClick = profileLongClick,
                        onProfileSettingClick = profileSettingClick,
                        width = PermanentDrawerWidth,
                    )

                Scaffold(
                    modifier = Modifier.weight(1f),
                    topBar = {
                        AppTopBar(
                            state = state,
                            profileName = profileName,
                            profileImage = profileImage,
                            onAvatarClick = {
                                // Everything `openProfileSelection()` does - the flag *and* the
                                // drawer - since this file owns the drawer state. In permanent mode
                                // the drawer is already on screen, so only the flag flips.
                                state.profileSelectionOpen = true
                                openDrawer()
                            },
                            onAvatarLongClick = { profileLongClick(currentProfileId) },
                        )
                    },
                    // No `floatingActionButton`: navlib docks the FAB into the bar
                    // (`fabGravity = CENTER`), so it is rendered inside `AppBottomBar`.
                    bottomBar = {
                        AppBottomBar(
                            state = state,
                            onMenuClick = openDrawer,
                            sheetHintEnabled = sheetHintEnabled,
                        )
                    },
                    // The one instance `ShellState` owns, which both snackbar hosts also receive.
                    snackbarHost = { AppSnackbarHost(state.snackbarHostState) },
                    // `rootFrame` carries the app background behind the `ComposeView`. Per §7.12 this
                    // is not sufficient to make it visible - `AppTheme` wraps content in its own
                    // opaque `Surface`, as it already does on all 18 `setAppThemeContent` hosts - but
                    // it is correct, it costs nothing, and changing `AppTheme` is out of scope.
                    containerColor = Color.Transparent,
                ) { pad ->
                    Row(
                        Modifier
                            .fillMaxSize()
                            .padding(pad),
                    ) {
                        if (mode == DrawerMode.Mini)
                            AppRail(state = state, onAction = drawerAction)
                        else if (mode == DrawerMode.Permanent)
                            ContentEdgeShadow()

                        Box(
                            Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                        ) {
                            FragmentContainer(
                                onContainerReady = onContainerReady,
                                onRefreshLayoutReady = onRefreshLayoutReady,
                            )
                        }
                    }
                }
            }
        }

        // Self-guarding on `state.sheetVisible`, so no second guard here.
        AppSheet(
            state = state,
            baseRows = sheetBaseRows,
            onSyncClick = onSyncClick,
            onDismissed = onSheetDismissed,
        )
    }
}

/**
 * The Fragment content host, and the first navigation - design §2, **all three parts mandatory**.
 * Spiked on `emulator-5554`, since reverted; the obvious implementation fails twice.
 *
 * 1. The container is `null` through `onCreate`, `onStart` **and** `onResume` - the factory below
 *    runs on the first traversal, ~0.5-0.8 s later. A transaction committed before that throws
 *    `IllegalArgumentException: No view found for id .../fragment` on **cold start**, because the
 *    FragmentManager executes pending ops at `onStart`. Hence [onContainerReady] rather than
 *    `onCreate`.
 * 2. After process death the FragmentManager restores the fragment and builds its view but never
 *    attaches it to the container that composes afterwards: `onResume` sees `container == null,
 *    fragmentView != null`, nothing crashes, and the user gets a blank screen. So a restored
 *    fragment whose view has no parent is orphaned and must be re-committed - it loses that screen's
 *    internal state, which is the accepted cost.
 * 3. `savedInstanceState?.remove("android:support:fragments")` is **inert** on `androidx.fragment`
 *    1.6.2 (the key is absent; restore goes through `SavedStateRegistry`), so it is not written here.
 *
 * `SwipeRefreshLayoutNoTouch` moves in unchanged (§7.8) with the three colours from
 * `MainActivity.kt:328-332`; it discards every touch event it is not given explicitly, so there is no
 * gesture and no `OnRefreshListener` to port.
 */
@Composable
private fun FragmentContainer(
    onContainerReady: () -> Unit,
    onRefreshLayoutReady: (SwipeRefreshLayoutNoTouch) -> Unit,
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findFragmentActivity() }

    AndroidView(
        factory = { ctx ->
            SwipeRefreshLayoutNoTouch(ctx).apply {
                setColorSchemeResources(
                    R.color.md_blue_500,
                    R.color.md_amber_500,
                    R.color.md_green_500,
                )
                addView(FrameLayout(ctx).apply { id = R.id.fragment })
                onRefreshLayoutReady(this)
            }
        },
        modifier = Modifier.fillMaxSize(),
    )

    LaunchedEffect(Unit) {
        // Unreachable: this composition is hosted by `MainActivity`, which is a `FragmentActivity`.
        // The nullability is the price of resolving it from `LocalContext` instead of demanding a
        // `MainActivity` parameter, which would make this file uncompilable until that class changes.
        val host = activity ?: return@LaunchedEffect
        if (host.findViewById<View>(R.id.fragment) == null) return@LaunchedEffect
        val existing = host.supportFragmentManager.findFragmentById(R.id.fragment)
        if (existing == null || existing.view?.parent == null) onContainerReady()
    }
}

/** [ContentEdgeShadowWidth]: `nv_miniDrawerElevation`'s permanent-mode half. */
@Composable
private fun ContentEdgeShadow() {
    Box(
        Modifier
            .width(ContentEdgeShadowWidth)
            .fillMaxHeight()
            .background(Brush.horizontalGradient(ContentEdgeShadowColors)),
    )
}

/**
 * `LocalContext` is the `ComposeView`'s context, which is the Activity today - but an
 * `android:theme` on any ancestor would hand us a `ContextThemeWrapper` instead, and this is the
 * only place that needs the `FragmentManager`.
 */
private fun Context.findFragmentActivity(): FragmentActivity? {
    var context: Context? = this
    while (context is ContextWrapper) {
        if (context is FragmentActivity) return context
        context = context.baseContext
    }
    return null
}
