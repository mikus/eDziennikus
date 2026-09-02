/*
 * Copyright (c) Mikolaj Olszewski 2026-9-2.
 */

package eu.mikus.edziennik.ui.shell

import android.graphics.drawable.Animatable
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.RememberObserver
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mikepenz.iconics.typeface.library.community.material.CommunityMaterial
import eu.mikus.edziennik.R
import eu.mikus.edziennik.ui.compose.IconicsIcon
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/** navlib's `nv_toolbar_image` is 30 dp, `centerCrop`, and circular only via its `defStyleRes`. */
private const val AvatarSizeDp = 30

/** M3's minimum touch target; the 30 dp avatar alone is too small to long-press reliably. */
private const val AvatarTouchTargetDp = 48

/**
 * navlib's toolbar subtitle is `?attr/textAppearanceSubtitle1` (16 sp, regular) at
 * `material_on_surface_emphasis_medium` (60%) - `Widget.MaterialComponents.Toolbar`, which
 * `nav_view.xml`'s `nv_toolbar` picks up through `Widget.MaterialComponents.Toolbar.Surface`. So
 * `bodyLarge` at this alpha is the M3 pair closest to what the app shows today. M3's own two-line
 * small bar would use `labelMedium` (12 sp, `AppBarSmallTokens.SubtitleFont`), which is visibly
 * smaller than today's.
 */
private const val SubtitleAlpha = 0.6f

/** How long `SyncSubtitle.Done` ("Gotowe") stays up before the subtitle falls back to `Idle`. */
private const val SyncDoneTimeoutMs = 2_000L

/**
 * The M3 replacement for navlib's `nv_toolbar` (§7.1 of the N4a design): the screen title, the
 * subtitle protocol, and the profile avatar. A **plain** [TopAppBar] - navlib's toolbar does not
 * collapse, so a scroll-aware one would be an unrequested behaviour change.
 *
 * Nothing composes this yet; `AppScaffold` does, in a later task.
 *
 * It takes callbacks rather than the `MainActivity` so that it compiles before `MainActivity` is
 * rewired - [onAvatarLongClick] ends up showing `ProfileConfigDialog`, whose constructor needs the
 * activity.
 *
 * @param profileName the current profile's display name; the steady-state subtitle is built from it.
 * @param profileImage the current profile's avatar, resolved by the caller from
 *   `Profile.getImageDrawable`. `null` renders a placeholder icon.
 * @param onAvatarClick must do everything `openProfileSelection()` does - set the flag **and** open
 *   the drawer, or the avatar is inert.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppTopBar(
    state: ShellState,
    profileName: String,
    profileImage: Drawable?,
    onAvatarClick: () -> Unit,
    onAvatarLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val subtitle = state.subtitle

    // The `Done` -> `Idle` timeout lives here, not in `AppScaffold`: this is the only composable
    // that renders `SyncSubtitle`, so the lifetime is a property of rendering it, and a later task
    // cannot forget to add it - without it "Gotowe" would persist forever, which compiles clean.
    if (subtitle is SyncSubtitle.Done) {
        LaunchedEffect(Unit) {
            delay(SyncDoneTimeoutMs)
            state.subtitle = SyncSubtitle.Idle
        }
    }

    // Both lines go in the `title` slot. material3 1.4.0 does have a `TopAppBar(title, subtitle,
    // ...)` overload, but it is `internal` - public in bytecode, `internal` in Kotlin metadata, so
    // only the compiler can tell. Stacking them here also reproduces `NavToolbar`, which is a
    // `MaterialToolbar` with its ordinary title + subtitle, rather than adopting a different
    // component. The title needs no explicit style - the bar provides its own via the slot.
    TopAppBar(
        title = {
            Column {
                Text(state.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    text = subtitleOf(subtitle, state.badges.total, profileName).resolve(),
                    style = MaterialTheme.typography.bodyLarge,
                    color = LocalContentColor.current.copy(alpha = SubtitleAlpha),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        },
        modifier = modifier,
        actions = { ProfileAvatar(profileImage, onAvatarClick, onAvatarLongClick) },
    )
}

/**
 * Resolves [subtitleOf]'s res-id descriptor. All six cases are decided in [subtitleOf]; this only
 * picks the right resource getter, so none of that logic is rebuilt here.
 */
@Composable
private fun SubtitleText.resolve(): String {
    val formatArgs = args.toTypedArray()
    val count = quantity
    return when {
        count != null -> pluralStringResource(res, count, *formatArgs)
        formatArgs.isEmpty() -> stringResource(res)
        else -> stringResource(res, *formatArgs)
    }
}

/**
 * The toolbar avatar. Circular **explicitly**: this is a sixth `BezelImageView`, circular today
 * only because MaterialDrawer's constructor passes a `defStyleRes`, and Phase 30 had to restore the
 * circle by hand in two other places after exactly this trap.
 *
 * A long press needs `combinedClickable`, which [androidx.compose.material3.IconButton] cannot
 * express, so the touch target and its ripple are the 48 dp [Box] rather than the 30 dp image.
 */
@Composable
private fun ProfileAvatar(
    image: Drawable?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val description = stringResource(R.string.choose_profile)
    val painter = remember(image) { image?.let(::DrawableAvatarPainter) }

    Box(
        modifier = Modifier
            .size(AvatarTouchTargetDp.dp)
            .clip(CircleShape)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        contentAlignment = Alignment.Center,
    ) {
        if (painter != null)
            Image(
                painter = painter,
                contentDescription = description,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(AvatarSizeDp.dp)
                    .clip(CircleShape),
            )
        else
            IconicsIcon(
                icon = CommunityMaterial.Icon.cmd_account_circle,
                contentDescription = description,
                sizeDp = AvatarSizeDp,
            )
    }
}

/**
 * Draws a [Drawable] straight onto the Compose canvas, keeping its own animation running.
 *
 * The house pattern (`SettingsScreen.kt:174-176`) bakes a `BitmapPainter` instead, but
 * `Profile.getDrawable` returns a `pl.droidsonroids.gif.GifDrawable` for a `.gif` avatar, and that
 * animates today in navlib's `ImageView`; a baked bitmap would silently freeze it.
 * `accompanist-drawablepainter` is not a dependency, hence the local class.
 *
 * [intrinsicSize] is the drawable's own, so `ContentScale.Crop` reproduces the `centerCrop` of the
 * `ImageView` this replaces. As a [RememberObserver] it is started and stopped by `remember`, so it
 * owns the [drawable] it is given - do not share that instance with a View.
 */
internal class DrawableAvatarPainter(private val drawable: Drawable) : Painter(), RememberObserver {

    /** Bumped per animation frame and read in [onDraw], which is what schedules the redraw. */
    private var redrawTicker by mutableIntStateOf(0)

    private val handler by lazy { Handler(Looper.getMainLooper()) }

    private val callback = object : Drawable.Callback {
        override fun invalidateDrawable(who: Drawable) {
            redrawTicker++
        }

        override fun scheduleDrawable(who: Drawable, what: Runnable, `when`: Long) {
            handler.postAtTime(what, `when`)
        }

        override fun unscheduleDrawable(who: Drawable, what: Runnable) {
            handler.removeCallbacks(what)
        }
    }

    override val intrinsicSize: Size
        get() = if (drawable.intrinsicWidth > 0 && drawable.intrinsicHeight > 0)
            Size(drawable.intrinsicWidth.toFloat(), drawable.intrinsicHeight.toFloat())
        else
            Size.Unspecified

    override fun onRemembered() {
        drawable.callback = callback
        drawable.setVisible(true, true)
        (drawable as? Animatable)?.start()
    }

    override fun onForgotten() {
        (drawable as? Animatable)?.stop()
        drawable.setVisible(false, false)
        drawable.callback = null
    }

    override fun onAbandoned() = onForgotten()

    override fun DrawScope.onDraw() {
        drawIntoCanvas { canvas ->
            redrawTicker
            drawable.setBounds(0, 0, size.width.roundToInt(), size.height.roundToInt())
            drawable.draw(canvas.nativeCanvas)
        }
    }
}
