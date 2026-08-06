/*
 * Copyright (c) Mikolaj Olszewski 2026-7-15.
 */

package eu.mikus.edziennik.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

enum class LicenseType(val label: String) { APACHE_2("Apache License 2.0"), MIT("MIT License"), BSD("BSD License") }

data class LicenseEntry(val name: String, val year: String, val copyright: String, val type: LicenseType, val url: String)

/** Ported verbatim from the former SettingsLicenseActivity (39 entries, order + text preserved). */
val LICENSES: List<LicenseEntry> = listOf(
    LicenseEntry("Kotlin", "2000-2020", "JetBrains s.r.o. and Kotlin Programming Language contributors.", LicenseType.APACHE_2, "https://github.com/JetBrains/kotlin"),
    LicenseEntry("Android Jetpack", "", "The Android Open Source Project", LicenseType.APACHE_2, "https://github.com/androidx/androidx"),
    LicenseEntry("Material Components for Android", "2014-2020", "Google, Inc.", LicenseType.APACHE_2, "https://github.com/material-components/material-components-android"),
    LicenseEntry("OkHttp", "2019", "Square, Inc.", LicenseType.APACHE_2, "https://github.com/square/okhttp"),
    LicenseEntry("Retrofit", "2013", "Square, Inc.", LicenseType.APACHE_2, "https://github.com/square/retrofit"),
    LicenseEntry("Gson", "2008", "Google Inc.", LicenseType.APACHE_2, "https://github.com/google/gson"),
    LicenseEntry("jsoup", "2009-2021", "Jonathan Hedley", LicenseType.MIT, "https://github.com/jhy/jsoup"),
    LicenseEntry("jspoon", "2017", "Droids On Roids", LicenseType.MIT, "https://github.com/DroidsOnRoids/jspoon"),
    LicenseEntry("AgendaCalendarView", "2015", "Thibault Guégan", LicenseType.APACHE_2, "https://github.com/szkolny-eu/agendacalendarview"),
    LicenseEntry("CafeBar", "2017", "Dani Mahardhika", LicenseType.APACHE_2, "https://github.com/szkolny-eu/cafebar"),
    LicenseEntry("FSLogin", "2021", "kuba2k2", LicenseType.MIT, "https://github.com/szkolny-eu/FSLogin"),
    LicenseEntry("material-about-library", "2016-2020", "Daniel Stone", LicenseType.APACHE_2, "https://github.com/szkolny-eu/material-about-library"),
    LicenseEntry("MHttp", "2018", "Mot.", LicenseType.APACHE_2, "https://github.com/szkolny-eu/mhttp"),
    LicenseEntry("Material Number Sliding Picker", "2019", "Alessandro Crugnola", LicenseType.MIT, "https://github.com/kuba2k2/NumberSlidingPicker"),
    LicenseEntry("RecyclerTabLayout", "2017", "nshmura", LicenseType.APACHE_2, "https://github.com/kuba2k2/RecyclerTabLayout"),
    LicenseEntry("Tachyon", "2019", "LinkedIn Corporation", LicenseType.BSD, "https://github.com/kuba2k2/Tachyon"),
    LicenseEntry("Android-Iconics", "2021", "Mike Penz", LicenseType.APACHE_2, "https://github.com/mikepenz/Android-Iconics"),
    LicenseEntry("Custom Activity On Crash library", "2020", "Eduard Ereza Martínez", LicenseType.APACHE_2, "https://github.com/Ereza/CustomActivityOnCrash"),
    LicenseEntry("Material-Calendar-View", "2017", "Applandeo sp. z o.o.", LicenseType.APACHE_2, "https://github.com/Applandeo/Material-Calendar-View"),
    LicenseEntry("Android Swipe Layout", "2014", "代码家", LicenseType.MIT, "https://github.com/daimajia/AndroidSwipeLayout"),
    LicenseEntry("CircularProgressIndicator", "2018", "Anton Kozyriatskyi", LicenseType.APACHE_2, "https://github.com/antonKozyriatskyi/CircularProgressIndicator"),
    LicenseEntry("ChatMessageView", "2019", "Tsubasa Nakayama", LicenseType.APACHE_2, "https://github.com/bassaer/ChatMessageView"),
    LicenseEntry("Android Image Cropper", "2016 Arthur Teplitzki,", "2013 Edmodo, Inc.", LicenseType.APACHE_2, "https://github.com/CanHub/Android-Image-Cropper"),
    LicenseEntry("Chucker", "2018-2020 Chucker Team,", "2017 Jeff Gilfelt", LicenseType.APACHE_2, "https://github.com/ChuckerTeam/chucker"),
    LicenseEntry("Android-Snowfall", "2016", "JetRadar", LicenseType.APACHE_2, "https://github.com/JetradarMobile/android-snowfall"),
    LicenseEntry("UONET+ Request Signer", "2019", "Wulkanowy", LicenseType.MIT, "https://github.com/wulkanowy/uonet-request-signer"),
    LicenseEntry("material-intro", "2017", "Jan Heinrich Reimer", LicenseType.MIT, "https://github.com/heinrichreimer/material-intro"),
    LicenseEntry("HyperLog Android", "2018", "HyperTrack", LicenseType.MIT, "https://github.com/hypertrack/hyperlog-android"),
    LicenseEntry("Color Picker", "2016 Jared Rummler,", "2015 Daniel Nilsson", LicenseType.APACHE_2, "https://github.com/jaredrummler/ColorPicker"),
    LicenseEntry("PowerPermission", "2020", "Qifan Yang", LicenseType.APACHE_2, "https://github.com/underwindfall/PowerPermission"),
    LicenseEntry("JsonViewer", "2017", "smuyyh", LicenseType.APACHE_2, "https://github.com/smuyyh/JsonViewer"),
    LicenseEntry("Coil", "2021", "Coil Contributors", LicenseType.APACHE_2, "https://github.com/coil-kt/coil"),
    LicenseEntry("Barcode Scanner (ZXing)", "2014", "Dushyanth Maguluru", LicenseType.APACHE_2, "https://github.com/dm77/barcodescanner"),
    LicenseEntry("AutoFitTextView", "2014", "Grantland Chew", LicenseType.APACHE_2, "https://github.com/grantland/android-autofittextview"),
    LicenseEntry("ShortcutBadger", "2014", "Leo Lin", LicenseType.APACHE_2, "https://github.com/leolin310148/ShortcutBadger"),
    LicenseEntry("EventBus", "2012-2020", "Markus Junginger, greenrobot", LicenseType.APACHE_2, "https://github.com/greenrobot/EventBus"),
    LicenseEntry("android-gif-drawable", "2013 - present,", "Karol Wrótniak, Droids on Roids LLC", LicenseType.MIT, "https://github.com/koral--/android-gif-drawable"),
    LicenseEntry("Android Debug Database", "2019 Amit Shekhar,", "2011 Android Open Source Project", LicenseType.APACHE_2, "https://github.com/amitshekhariitbhu/Android-Debug-Database"),
)

@Composable
fun LicensesScreen(onOpen: (String) -> Unit, modifier: Modifier = Modifier) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(LICENSES, key = { it.name }) { entry ->
            Card(
                modifier = Modifier.fillMaxWidth().clickable { onOpen(entry.url) },
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(entry.name, style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Copyright © ${listOf(entry.year, entry.copyright).filter { it.isNotBlank() }.joinToString(" ")}".trim(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(entry.type.label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}
