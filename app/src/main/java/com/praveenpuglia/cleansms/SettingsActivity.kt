package com.praveenpuglia.cleansms

import android.content.ActivityNotFoundException
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
    enum class DefaultTab {
        OTP,
        PERSONAL,
        TRANSACTIONAL,
        SERVICE,
        PROMOTIONAL,
        GOVERNMENT,
        ALL,
    }

    enum class FontFamily {
        SANS_SERIF, MONOSPACE, SYSTEM
    }

    companion object {
        private const val PREFS_NAME = "CleanSmsPrefs"
        private const val KEY_THEME = "theme_mode"
        private const val KEY_DEFAULT_TAB = "default_tab"
        private const val KEY_PROMO_NOTIFICATIONS_ENABLED = "promo_notifications_enabled"
        private const val KEY_ALL_TAB_ENABLED = "all_tab_enabled"
        private const val KEY_FONT_FAMILY = "font_family"
        const val THEME_LIGHT = AppCompatDelegate.MODE_NIGHT_NO
        const val THEME_DARK = AppCompatDelegate.MODE_NIGHT_YES
        const val THEME_SYSTEM = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM

        fun getThemeMode(context: Context): Int {
            return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getInt(KEY_THEME, THEME_SYSTEM)
        }

        fun setThemeMode(context: Context, mode: Int) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit().putInt(KEY_THEME, mode).apply()
        }

        fun getDefaultTab(context: Context): DefaultTab {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            if (prefs.contains(KEY_DEFAULT_TAB)) {
                try {
                    val ordinal = prefs.getInt(KEY_DEFAULT_TAB, -1)
                    if (ordinal >= 0) return DefaultTab.entries.getOrNull(ordinal) ?: DefaultTab.OTP
                } catch (_: ClassCastException) {
                    val migratedTab = when (prefs.getString(KEY_DEFAULT_TAB, null)) {
                        "OTP", "OTPs" -> DefaultTab.OTP
                        "Personal" -> DefaultTab.PERSONAL
                        "Transactions" -> DefaultTab.TRANSACTIONAL
                        "Service", "Services" -> DefaultTab.SERVICE
                        "Promotions" -> DefaultTab.PROMOTIONAL
                        "Government", "Governmental" -> DefaultTab.GOVERNMENT
                        "All" -> DefaultTab.ALL
                        else -> DefaultTab.OTP
                    }
                    setDefaultTab(context, migratedTab)
                    return migratedTab
                }
            }
            return DefaultTab.OTP
        }

        fun setDefaultTab(context: Context, tab: DefaultTab) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit().putInt(KEY_DEFAULT_TAB, tab.ordinal).apply()
        }

        fun getPromoNotificationsEnabled(context: Context): Boolean {
            return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getBoolean(KEY_PROMO_NOTIFICATIONS_ENABLED, true)
        }

        fun setPromoNotificationsEnabled(context: Context, enabled: Boolean) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit().putBoolean(KEY_PROMO_NOTIFICATIONS_ENABLED, enabled).apply()
        }

        fun getAllTabEnabled(context: Context): Boolean {
            return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getBoolean(KEY_ALL_TAB_ENABLED, false)
        }

        fun setAllTabEnabled(context: Context, enabled: Boolean) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit().putBoolean(KEY_ALL_TAB_ENABLED, enabled).apply()
        }

        fun getFontFamily(context: Context): FontFamily {
            val ordinal = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getInt(KEY_FONT_FAMILY, FontFamily.SANS_SERIF.ordinal)
            return FontFamily.entries.getOrNull(ordinal) ?: FontFamily.SANS_SERIF
        }

        fun setFontFamily(context: Context, family: FontFamily) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit().putInt(KEY_FONT_FAMILY, family.ordinal).apply()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        FontThemeHelper.apply(this)
        super.onCreate(savedInstanceState)
        showSettingsContent()
    }

    internal fun openUrl(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (_: ActivityNotFoundException) {
            // A browser is not guaranteed on managed or test devices.
        }
    }
}

private fun SettingsActivity.showSettingsContent() {
    setContent {
        var themeMode by remember { mutableIntStateOf(SettingsActivity.getThemeMode(this)) }
        var fontFamily by remember { mutableStateOf(SettingsActivity.getFontFamily(this)) }
        var defaultTab by remember { mutableStateOf(SettingsActivity.getDefaultTab(this)) }
        var allTabEnabled by remember { mutableStateOf(SettingsActivity.getAllTabEnabled(this)) }
        var promoNotificationsEnabled by remember {
            mutableStateOf(SettingsActivity.getPromoNotificationsEnabled(this))
        }

        CleanSmsTheme {
            SettingsScreen(
                themeMode = themeMode,
                fontFamily = fontFamily,
                defaultTab = defaultTab,
                allTabEnabled = allTabEnabled,
                promoNotificationsEnabled = promoNotificationsEnabled,
                versionName = BuildConfig.VERSION_NAME,
                onBack = ::finish,
                onThemeSelected = { selected ->
                    themeMode = selected
                    SettingsActivity.setThemeMode(this, selected)
                    AppCompatDelegate.setDefaultNightMode(selected)
                },
                onFontSelected = { selected ->
                    if (selected != fontFamily) {
                        fontFamily = selected
                        SettingsActivity.setFontFamily(this, selected)
                        recreate()
                    }
                },
                onDefaultTabSelected = { selected ->
                    defaultTab = selected
                    SettingsActivity.setDefaultTab(this, selected)
                },
                onAllTabChanged = { enabled ->
                    allTabEnabled = enabled
                    SettingsActivity.setAllTabEnabled(this, enabled)
                    if (!enabled && defaultTab == SettingsActivity.DefaultTab.ALL) {
                        defaultTab = SettingsActivity.DefaultTab.OTP
                        SettingsActivity.setDefaultTab(this, SettingsActivity.DefaultTab.OTP)
                    }
                },
                onPromoNotificationsChanged = { enabled ->
                    promoNotificationsEnabled = enabled
                    SettingsActivity.setPromoNotificationsEnabled(this, enabled)
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
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScreen(
    themeMode: Int,
    fontFamily: SettingsActivity.FontFamily,
    defaultTab: SettingsActivity.DefaultTab,
    allTabEnabled: Boolean,
    promoNotificationsEnabled: Boolean,
    versionName: String,
    onBack: () -> Unit,
    onThemeSelected: (Int) -> Unit,
    onFontSelected: (SettingsActivity.FontFamily) -> Unit,
    onDefaultTabSelected: (SettingsActivity.DefaultTab) -> Unit,
    onAllTabChanged: (Boolean) -> Unit,
    onPromoNotificationsChanged: (Boolean) -> Unit,
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
                DebugSettings.Content()
            }
        }
    }
}

@Composable
private fun SettingsHeader(onBack: () -> Unit) {
    Surface(shadowElevation = 3.dp) {
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
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
    ThemeOption(SettingsActivity.THEME_LIGHT, R.string.settings_theme_light, R.drawable.ic_sun),
    ThemeOption(SettingsActivity.THEME_DARK, R.string.settings_theme_dark, R.drawable.ic_moon),
    ThemeOption(SettingsActivity.THEME_SYSTEM, R.string.settings_theme_system, R.drawable.ic_computer),
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
    selectedFont: SettingsActivity.FontFamily,
    onSelected: (SettingsActivity.FontFamily) -> Unit,
) {
    val fonts = SettingsActivity.FontFamily.entries
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        fonts.forEachIndexed { index, font ->
            val sampleFont = when (font) {
                SettingsActivity.FontFamily.SANS_SERIF -> ComposeFontFamily(Font(R.font.google_sans_flex))
                SettingsActivity.FontFamily.MONOSPACE -> ComposeFontFamily(Font(R.font.google_sans_code))
                SettingsActivity.FontFamily.SYSTEM -> ComposeFontFamily.SansSerif
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
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
            )
        }
    }
    Spacer(Modifier.height(8.dp))
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun DefaultTabRow(
    selectedTab: SettingsActivity.DefaultTab,
    allTabEnabled: Boolean,
    onSelected: (SettingsActivity.DefaultTab) -> Unit,
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
                (listOf(SettingsActivity.DefaultTab.ALL) +
                    SettingsActivity.DefaultTab.entries.filterNot { it == SettingsActivity.DefaultTab.ALL })
                    .filter { it != SettingsActivity.DefaultTab.ALL || allTabEnabled }
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
private fun SettingsActivity.DefaultTab.labelResource(): Int = when (this) {
    SettingsActivity.DefaultTab.ALL -> R.string.tab_all
    SettingsActivity.DefaultTab.OTP -> R.string.tab_otp
    SettingsActivity.DefaultTab.PERSONAL -> R.string.category_personal
    SettingsActivity.DefaultTab.TRANSACTIONAL -> R.string.category_transactions
    SettingsActivity.DefaultTab.SERVICE -> R.string.category_service
    SettingsActivity.DefaultTab.PROMOTIONAL -> R.string.category_promotions
    SettingsActivity.DefaultTab.GOVERNMENT -> R.string.category_government
}
