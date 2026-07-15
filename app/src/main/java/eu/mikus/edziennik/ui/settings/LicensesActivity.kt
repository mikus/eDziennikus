/*
 * Copyright (c) Mikolaj Olszewski 2026-7-15.
 */

package eu.mikus.edziennik.ui.settings

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.stringResource
import eu.mikus.edziennik.R
import eu.mikus.edziennik.ui.compose.setAppThemeContent
import eu.mikus.edziennik.utils.Themes
import eu.mikus.edziennik.utils.Utils

class LicensesActivity : AppCompatActivity() {

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        // AppCompatActivity requires a Theme.AppCompat descendant; the app's @style/AppTheme
        // (parent NavView) is not one, so set a MaterialComponents NoActionBar theme at runtime
        // (the Compose Scaffold provides the top bar). Mirrors the legacy SettingsLicenseActivity.
        setTheme(
            if (Themes.isDark) R.style.Theme_MaterialComponents_NoActionBar
            else R.style.Theme_MaterialComponents_Light_NoActionBar
        )
        super.onCreate(savedInstanceState)
        setContentView(
            ComposeView(this).apply {
                setAppThemeContent {
                    Scaffold(
                        topBar = { TopAppBar(title = { Text(stringResource(R.string.settings_about_licenses_text)) }) },
                    ) { inner ->
                        Column(Modifier.fillMaxSize().padding(inner)) {
                            LicensesScreen(onOpen = { Utils.openUrl(this@LicensesActivity, it) })
                        }
                    }
                }
            },
        )
    }
}
