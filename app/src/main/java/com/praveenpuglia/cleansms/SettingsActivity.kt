package com.praveenpuglia.cleansms

import android.content.ActivityNotFoundException
import androidx.core.net.toUri
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily as ComposeFontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.praveenpuglia.cleansms.ui.theme.CleanSmsTheme

class SettingsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        FontThemeHelper.apply(this)
        super.onCreate(savedInstanceState)
        showSettingsContent()
    }

    internal fun openUrl(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
        } catch (_: ActivityNotFoundException) {
            // A browser is not guaranteed on managed or test devices.
        }
    }
}

private fun SettingsActivity.showSettingsContent() {
    setContent {
        var themeMode by remember { mutableIntStateOf(AppSettings.getThemeMode(this)) }
        var fontFamily by remember { mutableStateOf(AppSettings.getFontFamily(this)) }
        var defaultTab by remember { mutableStateOf(AppSettings.getDefaultTab(this)) }
        var allTabEnabled by remember { mutableStateOf(AppSettings.getAllTabEnabled(this)) }
        var promoNotificationsEnabled by remember {
            mutableStateOf(AppSettings.getPromoNotificationsEnabled(this))
        }
        var showSenderLogos by remember { mutableStateOf(AppSettings.getShowSenderLogos(this)) }

        CleanSmsTheme {
            SettingsScreen(
                themeMode = themeMode,
                fontFamily = fontFamily,
                defaultTab = defaultTab,
                allTabEnabled = allTabEnabled,
                promoNotificationsEnabled = promoNotificationsEnabled,
                showSenderLogos = showSenderLogos,
                versionName = BuildConfig.VERSION_NAME,
                onBack = ::finish,
                onThemeSelected = { selected ->
                    themeMode = selected
                    AppSettings.setThemeMode(this, selected)
                    AppCompatDelegate.setDefaultNightMode(selected)
                },
                onFontSelected = { selected ->
                    if (selected != fontFamily) {
                        fontFamily = selected
                        AppSettings.setFontFamily(this, selected)
                        recreate()
                    }
                },
                onDefaultTabSelected = { selected ->
                    defaultTab = selected
                    AppSettings.setDefaultTab(this, selected)
                },
                onAllTabChanged = { enabled ->
                    allTabEnabled = enabled
                    AppSettings.setAllTabEnabled(this, enabled)
                    if (!enabled && defaultTab == AppSettings.DefaultTab.ALL) {
                        defaultTab = AppSettings.DefaultTab.OTP
                        AppSettings.setDefaultTab(this, AppSettings.DefaultTab.OTP)
                    }
                },
                onPromoNotificationsChanged = { enabled ->
                    promoNotificationsEnabled = enabled
                    AppSettings.setPromoNotificationsEnabled(this, enabled)
                },
                onShowSenderLogosChanged = { enabled ->
                    showSenderLogos = enabled
                    AppSettings.setShowSenderLogos(this, enabled)
                },
                onTermsClick = { openUrl("https://clean-sms.praveenpuglia.com/tnc") },
                onPrivacyClick = { openUrl("https://clean-sms.praveenpuglia.com/privacy-policy") },
            )
        }
    }
}

object SettingsTestTags {
    const val DEFAULT_TAB = "settings_default_tab"
    const val ALL_TAB = "settings_all_tab"
    const val PROMO_NOTIFICATIONS = "settings_promo_notifications"
    const val SENDER_LOGOS = "settings_sender_logos"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScreen(
    themeMode: Int,
    fontFamily: AppSettings.FontFamily,
    defaultTab: AppSettings.DefaultTab,
    allTabEnabled: Boolean,
    promoNotificationsEnabled: Boolean,
    showSenderLogos: Boolean,
    versionName: String,
    onBack: () -> Unit,
    onThemeSelected: (Int) -> Unit,
    onFontSelected: (AppSettings.FontFamily) -> Unit,
    onDefaultTabSelected: (AppSettings.DefaultTab) -> Unit,
    onAllTabChanged: (Boolean) -> Unit,
    onPromoNotificationsChanged: (Boolean) -> Unit,
    onShowSenderLogosChanged: (Boolean) -> Unit,
    onTermsClick: () -> Unit,
    onPrivacyClick: () -> Unit,
) {
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.systemBarsPadding()) {
            SettingsHeader(onBack)
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
            ) {
                SettingsSectionTitle(stringResource(R.string.settings_appearance), topPadding = 8.dp)
                SettingsLabel(stringResource(R.string.settings_theme_label), topPadding = 8.dp)
                ThemeSelector(themeMode, onThemeSelected)
                SettingsLabel(stringResource(R.string.settings_font_label), topPadding = 16.dp)
                FontSelector(fontFamily, onFontSelected)
                SettingsSwitchRow(
                    title = stringResource(R.string.settings_sender_logos_title),
                    subtitle = stringResource(R.string.settings_sender_logos_subtitle),
                    checked = showSenderLogos,
                    testTag = SettingsTestTags.SENDER_LOGOS,
                    onCheckedChange = onShowSenderLogosChanged,
                )

                SettingsSectionTitle(stringResource(R.string.settings_organization), topPadding = 8.dp)
                DefaultTabRow(defaultTab, allTabEnabled, onDefaultTabSelected)
                SettingsSwitchRow(
                    title = stringResource(R.string.settings_all_tab_title),
                    subtitle = stringResource(R.string.settings_all_tab_subtitle),
                    checked = allTabEnabled,
                    testTag = SettingsTestTags.ALL_TAB,
                    onCheckedChange = onAllTabChanged,
                )

                SettingsSectionTitle(stringResource(R.string.settings_notifications), topPadding = 24.dp)
                SettingsSwitchRow(
                    title = stringResource(R.string.settings_promo_notifications_title),
                    subtitle = stringResource(R.string.settings_promo_notifications_subtitle),
                    checked = promoNotificationsEnabled,
                    testTag = SettingsTestTags.PROMO_NOTIFICATIONS,
                    onCheckedChange = onPromoNotificationsChanged,
                )

                SettingsSectionTitle(stringResource(R.string.settings_about), topPadding = 24.dp)
                Column(modifier = Modifier.padding(vertical = 8.dp)) {
                    Text(stringResource(R.string.settings_version), style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = versionName,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                SettingsDivider()
                SettingsLink(stringResource(R.string.settings_terms), onTermsClick)
                SettingsDivider()
                SettingsLink(stringResource(R.string.settings_privacy), onPrivacyClick)
                Text(
                    stringResource(R.string.settings_trademark_notice),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 16.dp),
                )
                DebugSettings.Content()
            }
        }
    }
}

@Composable
private fun SettingsHeader(onBack: () -> Unit) {
    Surface {
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 4.dp, end = 20.dp, top = 12.dp, bottom = 12.dp),
            ) {
                IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                    Icon(
                        painter = painterResource(R.drawable.ic_arrow_back),
                        contentDescription = stringResource(R.string.settings_back),
                    )
                }
                Text(
                    text = stringResource(R.string.settings_title),
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.42.sp,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}

@Composable
private fun SettingsSectionTitle(text: String, topPadding: androidx.compose.ui.unit.Dp) {
    Text(
        text = text,
        color = MaterialTheme.colorScheme.primary,
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.7.sp,
        modifier = Modifier.padding(top = topPadding, bottom = 4.dp),
    )
}

@Composable
private fun SettingsLabel(text: String, topPadding: androidx.compose.ui.unit.Dp) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(top = topPadding, bottom = 8.dp),
    )
}

private data class ThemeOption(
    val mode: Int,
    @param:StringRes val label: Int,
    @param:DrawableRes val icon: Int,
)

private val themeOptions = listOf(
    ThemeOption(AppSettings.THEME_LIGHT, R.string.settings_theme_light, R.drawable.ic_sun),
    ThemeOption(AppSettings.THEME_DARK, R.string.settings_theme_dark, R.drawable.ic_moon),
    ThemeOption(AppSettings.THEME_SYSTEM, R.string.settings_theme_system, R.drawable.ic_computer),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ThemeSelector(selectedMode: Int, onSelected: (Int) -> Unit) {
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        themeOptions.forEachIndexed { index, option ->
            SegmentedButton(
                selected = selectedMode == option.mode,
                onClick = { onSelected(option.mode) },
                shape = SegmentedButtonDefaults.itemShape(index, themeOptions.size),
                icon = {
                    Icon(
                        painter = painterResource(option.icon),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                },
                label = { Text(stringResource(option.label), fontSize = 14.sp) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FontSelector(
    selectedFont: AppSettings.FontFamily,
    onSelected: (AppSettings.FontFamily) -> Unit,
) {
    val fonts = AppSettings.FontFamily.entries
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        fonts.forEachIndexed { index, font ->
            val sampleFont = when (font) {
                AppSettings.FontFamily.SANS_SERIF -> ComposeFontFamily(Font(R.font.google_sans_flex))
                AppSettings.FontFamily.MONOSPACE -> ComposeFontFamily(Font(R.font.google_sans_code))
                AppSettings.FontFamily.SYSTEM -> ComposeFontFamily.SansSerif
            }
            SegmentedButton(
                selected = selectedFont == font,
                onClick = { onSelected(font) },
                shape = SegmentedButtonDefaults.itemShape(index, fonts.size),
                icon = {},
                label = {
                    Text(
                        text = stringResource(R.string.settings_font_sample),
                        fontFamily = sampleFont,
                        fontSize = 14.sp,
                    )
                },
                modifier = Modifier.weight(1f),
            )
        }
    }
    Spacer(Modifier.height(8.dp))
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun DefaultTabRow(
    selectedTab: AppSettings.DefaultTab,
    allTabEnabled: Boolean,
    onSelected: (AppSettings.DefaultTab) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
    ) {
        Text(
            text = stringResource(R.string.settings_default_tab),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f),
        )
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = it },
        ) {
            TextButton(
                onClick = { expanded = true },
                modifier = Modifier
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                    .testTag(SettingsTestTags.DEFAULT_TAB),
            ) {
                Text(stringResource(selectedTab.labelResource()))
                Icon(
                    painter = painterResource(R.drawable.ic_arrow_drop_down),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
            }
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.width(168.dp),
            ) {
                (listOf(AppSettings.DefaultTab.ALL) +
                    AppSettings.DefaultTab.entries.filterNot { it == AppSettings.DefaultTab.ALL })
                    .filter { it != AppSettings.DefaultTab.ALL || allTabEnabled }
                    .forEach { tab ->
                        DropdownMenuItem(
                            text = { Text(stringResource(tab.labelResource())) },
                            onClick = {
                                onSelected(tab)
                                expanded = false
                            },
                        )
                    }
            }
        }
    }
}

@Composable
private fun SettingsSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    testTag: String,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .testTag(testTag)
            .toggleable(
                value = checked,
                role = Role.Switch,
                onValueChange = onCheckedChange,
            )
            .padding(vertical = 8.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                text = subtitle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = null,
            modifier = Modifier.padding(start = 16.dp),
        )
    }
}

@Composable
private fun SettingsDivider() {
    HorizontalDivider(
        color = MaterialTheme.colorScheme.outlineVariant,
        modifier = Modifier.padding(vertical = 4.dp),
    )
}

@Composable
private fun SettingsLink(text: String, onClick: () -> Unit) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 16.dp),
    )
}

@StringRes
private fun AppSettings.DefaultTab.labelResource(): Int = when (this) {
    AppSettings.DefaultTab.ALL -> R.string.tab_all
    AppSettings.DefaultTab.OTP -> R.string.tab_otp
    AppSettings.DefaultTab.PERSONAL -> R.string.category_personal
    AppSettings.DefaultTab.TRANSACTIONAL -> R.string.category_transactions
    AppSettings.DefaultTab.SERVICE -> R.string.category_service
    AppSettings.DefaultTab.PROMOTIONAL -> R.string.category_promotions
    AppSettings.DefaultTab.GOVERNMENT -> R.string.category_government
}
