/*
 * Copyright (c) Kuba Szczodrzyński 2020-4-16.
 */

package eu.mikus.edziennik.ui.login

import android.app.Activity
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
import android.widget.FrameLayout
import androidx.activity.OnBackPressedCallback
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavOptions
import androidx.navigation.Navigation
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import eu.mikus.edziennik.App
import eu.mikus.edziennik.R
import eu.mikus.edziennik.data.api.models.ApiError
import eu.mikus.edziennik.databinding.LoginActivityBinding
import eu.mikus.edziennik.ext.dp
import eu.mikus.edziennik.ui.error.ErrorSnackbar
import eu.mikus.edziennik.utils.SwipeRefreshLayoutNoTouch
import kotlin.coroutines.CoroutineContext

class LoginActivity : AppCompatActivity(), CoroutineScope {
    companion object {
        private const val TAG = "LoginActivity"
    }

    private val app: App by lazy { applicationContext as App }
    private val vm: LoginViewModel by viewModels { LoginViewModel.Factory(app) }
    private lateinit var b: LoginActivityBinding
    lateinit var navOptions: NavOptions
    lateinit var navOptionsBuilder: NavOptions.Builder
    val nav by lazy { Navigation.findNavController(this, R.id.nav_host_fragment) }
    val errorSnackbar: ErrorSnackbar by lazy { ErrorSnackbar(this) }
    val swipeRefreshLayout: SwipeRefreshLayoutNoTouch by lazy { b.swipeRefreshLayout }

    private val job: Job = Job()
    override val coroutineContext: CoroutineContext
        get() = job + Dispatchers.Main

    fun getRootView() = b.root

    /** Public error entry point for external callers (e.g. LabProfileFragment); routes through the VM. */
    fun error(error: ApiError) { vm.reportError(error) }

    private val onBackPressedCallback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            val destination = nav.currentDestination ?: run {
                nav.navigateUp()
                return
            }
            if (destination.id == R.id.loginSyncErrorFragment)
                return
            if (destination.id == R.id.loginProgressFragment)
                return
            if (destination.id == R.id.loginSyncFragment)
                return
            if (destination.id == R.id.loginFinishFragment)
                return
            if (destination.id == R.id.loginChooserFragment && !vm.hasLoginStores) {
                setResult(Activity.RESULT_CANCELED)
                finish()
                return
            }
            if (destination.id == R.id.loginSummaryFragment) {
                MaterialAlertDialogBuilder(this@LoginActivity)
                        .setTitle(R.string.are_you_sure)
                        .setMessage(R.string.login_cancel_confirmation)
                        .setPositiveButton(R.string.yes) { _, _ ->
                            setResult(Activity.RESULT_CANCELED)
                            finish()
                        }
                        .setNegativeButton(R.string.no, null)
                        .show()
                return
            }
            nav.navigateUp()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setTheme(R.style.AppTheme_Light)

        navOptionsBuilder = NavOptions.Builder()
                .setEnterAnim(R.anim.slide_in_right)
                .setExitAnim(R.anim.slide_out_left)
                .setPopEnterAnim(R.anim.slide_in_left)
                .setPopExitAnim(R.anim.slide_out_right)
        navOptions = navOptionsBuilder.build()

        b = LoginActivityBinding.inflate(layoutInflater)
        setContentView(b.root)
        errorSnackbar.setCoordinator(b.coordinator, b.snackbarAnchor)
        onBackPressedDispatcher.addCallback(this, onBackPressedCallback)

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                vm.errorEvents.collect { error ->
                    errorSnackbar.addError(error).show()
                }
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            window.decorView.systemUiVisibility = window.decorView.systemUiVisibility or SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
            ViewCompat.setOnApplyWindowInsetsListener(b.root) { view: View, insets: WindowInsetsCompat ->
                val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
                view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
                insets
            }
        }

        app.buildManager.validateBuild(this)

        launch {
            app.config.loginFinished = app.db.profileDao().count > 0
            if (!app.config.loginFinished) {
                app.config.ui.miniMenuVisible = resources.configuration.smallestScreenWidthDp > 480
            }
        }
    }
}
