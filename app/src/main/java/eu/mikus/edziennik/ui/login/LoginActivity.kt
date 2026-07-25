/*
 * Copyright (c) Kuba Szczodrzyński 2020-4-16.
 */
package eu.mikus.edziennik.ui.login

import android.graphics.Color
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import eu.mikus.edziennik.App
import eu.mikus.edziennik.R
import eu.mikus.edziennik.ui.compose.theme.AppTheme

class LoginActivity : AppCompatActivity() {
    companion object { private const val TAG = "LoginActivity" }

    private val app: App by lazy { applicationContext as App }
    private val vm: LoginViewModel by viewModels { LoginViewModel.Factory(app) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setTheme(R.style.AppTheme_Light)
        // Login is intentionally light: force dark status/nav-bar icons regardless of device dark mode
        // (reproduces the legacy SYSTEM_UI_FLAG_LIGHT_STATUS_BAR).
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        setContent {
            AppTheme(forceLight = true) {
                LoginRoot(vm)
            }
        }

        app.buildManager.validateBuild(this)

        lifecycleScope.launch {
            app.config.loginFinished = app.db.profileDao().count > 0
            if (!app.config.loginFinished) {
                app.config.ui.miniMenuVisible = resources.configuration.smallestScreenWidthDp > 480
            }
        }
    }
}
