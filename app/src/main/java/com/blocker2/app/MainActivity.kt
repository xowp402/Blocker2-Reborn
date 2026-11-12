package com.blocker2.app

import android.Manifest
import android.content.pm.PackageManager
import android.content.Intent
import android.content.ComponentName
import android.net.VpnService
import android.provider.Settings
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.blocker2.app.ui.CommitmentScreen
import com.blocker2.app.ui.BackupScreen
import com.blocker2.app.ui.ActiveBlocksScreen
import com.blocker2.app.ui.ActivityLogScreen
import com.blocker2.app.ui.MonitoredAppsScreen
import com.blocker2.app.ui.WordRulesScreen
import com.blocker2.app.ui.TelegramRulesScreen
import com.blocker2.app.ui.SiteRulesScreen
import com.blocker2.app.ui.theme.Blocker2Theme
import com.blocker2.app.vpn.LocalDnsVpnService
import com.blocker2.app.accessibility.AccessibilityMonitorService
import com.blocker2.app.startup.StartupPreferences
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    private var accessibilityPermissionEnabled by mutableStateOf(false)
    private var appThemeMode by mutableStateOf("system")
    private var appLanguage by mutableStateOf("fa")

    private fun refreshAccessibilityStatus() {
        val expected = ComponentName(this, AccessibilityMonitorService::class.java)
        val enabledServices = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ).orEmpty()
        accessibilityPermissionEnabled = enabledServices.split(':').any { flattened ->
            ComponentName.unflattenFromString(flattened) == expected
        }
    }

    override fun onResume() {
        super.onResume()
        refreshAccessibilityStatus()
    }
    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (!granted) {
                android.util.Log.i("Blocker2", "Notification permission was not granted")
            }
        }

    private val vpnPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                StartupPreferences.setAutoStartSiteFilter(this, true)
                startLocalDnsVpn()
            }
        }

    private fun requestLocalDnsVpn() {
        val permissionIntent = VpnService.prepare(this)
        if (permissionIntent == null) {
            StartupPreferences.setAutoStartSiteFilter(this, true)
            startLocalDnsVpn()
        } else {
            vpnPermissionLauncher.launch(permissionIntent)
        }
    }

    private fun startLocalDnsVpn() {
        val intent = Intent(this, LocalDnsVpnService::class.java).setAction(LocalDnsVpnService.ACTION_START)
        ContextCompat.startForegroundService(this, intent)
    }

    private fun stopLocalDnsVpn() {
        StartupPreferences.setAutoStartSiteFilter(this, false)
        stopService(Intent(this, LocalDnsVpnService::class.java))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        refreshAccessibilityStatus()
        val preferences = getSharedPreferences("blocker2_app_preferences", MODE_PRIVATE)
        appThemeMode = preferences.getString("theme_mode", "system") ?: "system"
        appLanguage = preferences.getString("language", "fa") ?: "fa"
        val needsNotificationPermission =
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED

        setContent {
            Blocker2Theme(darkTheme = when (appThemeMode) {
                "light" -> false
                "dark" -> true
                else -> androidx.compose.foundation.isSystemInDarkTheme()
            }) {
                androidx.compose.runtime.CompositionLocalProvider(
                    LocalLayoutDirection provides if (appLanguage == "en") LayoutDirection.Ltr else LayoutDirection.Rtl
                ) {
                Blocker2App(
                    language = appLanguage,
                    themeMode = appThemeMode,
                    onLanguageChange = { value ->
                        appLanguage = value
                        preferences.edit().putString("language", value).apply()
                    },
                    onThemeChange = { value ->
                        appThemeMode = value
                        preferences.edit().putString("theme_mode", value).apply()
                    },
                    showNotificationPermissionButton = needsNotificationPermission,
                    accessibilityPermissionEnabled = accessibilityPermissionEnabled,
                    onRequestNotifications = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    },
                    onStartVpn = { requestLocalDnsVpn() },
                    onStopVpn = { stopLocalDnsVpn() },
                    onOpenAccessibilitySettings = {
                        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    }
                )
                }
            }
        }
    }
}

private enum class MainSection(val title: String, val subtitle: String) {
    HOME("خانه", "وضعیت کلی محافظت"),
    WORDS("واژه‌ها", "مدیریت واژه‌ها و عبارت‌ها"),
    TELEGRAM("تلگرام", "فیلتر واژه‌ها و کانال‌های تلگرام"),
    SITES("سایت‌ها", "مدیریت دامنه‌ها و نشانی‌ها"),
    APPS("برنامه‌ها", "انتخاب برنامه‌های تحت نظارت"),
    BLOCKS("مسدودی‌ها", "مشاهده و مدیریت برنامه‌های مسدودشده"),
    ACTIVITY("گزارش‌ها", "تاریخچهٔ شناسایی‌ها و واکنش‌ها"),
    COMMITMENT("تعهد", "حالت تعهد شخصی و خروج اضطراری"),
    BACKUP("پشتیبان", "ذخیره و بازیابی اطلاعات"),
    SETTINGS("تنظیمات", "زبان و پوستهٔ برنامه")
}

@androidx.compose.runtime.Composable
private fun Blocker2App(
    language: String,
    themeMode: String,
    onLanguageChange: (String) -> Unit,
    onThemeChange: (String) -> Unit,
    showNotificationPermissionButton: Boolean,
    accessibilityPermissionEnabled: Boolean,
    onRequestNotifications: () -> Unit,
    onStartVpn: () -> Unit,
    onStopVpn: () -> Unit,
    onOpenAccessibilitySettings: () -> Unit
) {
    var selectedSection by remember { mutableIntStateOf(0) }
    val appContext = androidx.compose.ui.platform.LocalContext.current.applicationContext as Blocker2Application
    val commitmentSession by appContext.database.commitmentSessionDao()
        .observeSession().collectAsState(initial = null)
    // Room emits when a session changes, not merely when its expiry timestamp passes.
    // Schedule a one-shot UI refresh at the deadline so the Apps section unlocks on time.
    var commitmentClock by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(commitmentSession?.expiresAt) {
        val expiry = commitmentSession?.expiresAt
        if (expiry != null && expiry > commitmentClock) {
            delay(expiry - commitmentClock)
            commitmentClock = System.currentTimeMillis()
        }
    }
    val commitmentActive = commitmentSession?.expiresAt?.let { it > commitmentClock } == true
    val accessibilityConnected by AccessibilityMonitorService.connectionState.collectAsState()
    val dnsVpnConnected by LocalDnsVpnService.connectionState.collectAsState()
    val monitoredApps by appContext.database.monitoredAppDao().observeMonitored().collectAsState(initial = emptyList())
    val enabledWordRules by appContext.database.wordRuleDao().observeEnabled().collectAsState(initial = emptyList())
    val enabledTelegramRules by appContext.database.telegramFilterRuleDao().observeEnabled().collectAsState(initial = emptyList())
    val enabledSiteRules by appContext.database.siteRuleDao().observeEnabledBlockedSites().collectAsState(initial = emptyList())
    val enabledMonitoredAppCount = monitoredApps.count { it.monitoringEnabled }
    val sections = MainSection.entries

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "BLOCKER2",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.4.sp
                    )
                    Text(
                        text = if (language == "en") "Mindful control, better focus" else "کنترل آگاهانه، تمرکز بیشتر",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = if (language == "en") "Version 1.0.0" else "نسخه ۱.۰.۰",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = if (language == "en") sections[selectedSection].englishTitle() else sections[selectedSection].title,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (language == "en") sections[selectedSection].englishSubtitle() else sections[selectedSection].subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                when (sections[selectedSection]) {
                    MainSection.HOME -> HomeOverview(
                        language = language,
                        showNotificationPermissionButton = showNotificationPermissionButton,
                        accessibilityPermissionEnabled = accessibilityPermissionEnabled,
                        accessibilityConnected = accessibilityConnected,
                        enabledMonitoredAppCount = enabledMonitoredAppCount,
                        enabledWordRuleCount = enabledWordRules.size,
                        enabledTelegramRuleCount = enabledTelegramRules.size,
                        enabledSiteRuleCount = enabledSiteRules.size,
                        dnsVpnConnected = dnsVpnConnected,
                        onRequestNotifications = onRequestNotifications,
                        onOpenAccessibilitySettings = onOpenAccessibilitySettings
                    )
                    MainSection.WORDS -> WordRulesScreen(
                        wordDao = (androidx.compose.ui.platform.LocalContext.current.applicationContext as Blocker2Application)
                            .database.wordRuleDao(),
                        appDao = (androidx.compose.ui.platform.LocalContext.current.applicationContext as Blocker2Application)
                            .database.monitoredAppDao(),
                        language = language
                    )
                    MainSection.TELEGRAM -> TelegramRulesScreen(
                        dao = (androidx.compose.ui.platform.LocalContext.current.applicationContext as Blocker2Application)
                            .database.telegramFilterRuleDao(),
                        language = language
                    )
                    MainSection.SITES -> SiteRulesScreen(
                        dao = (androidx.compose.ui.platform.LocalContext.current.applicationContext as Blocker2Application)
                            .database.siteRuleDao(),
                        onStartVpn = onStartVpn,
                        onStopVpn = onStopVpn,
                        language = language
                    )
                    MainSection.APPS -> {
                        if (commitmentActive) {
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(18.dp),
                                color = MaterialTheme.colorScheme.surface,
                                tonalElevation = 2.dp
                            ) {
                                Column(
                                    modifier = Modifier.padding(18.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(if (language == "en") "App settings are temporarily locked" else "تنظیمات برنامه‌ها موقتاً قفل است", fontWeight = FontWeight.Bold)
                                    Text(
                                        if (language == "en") "You cannot change monitored apps or their categories until commitment ends. To leave early, use the emergency-exit process in the Commitment section." else "تا پایان حالت تعهد نمی‌توانی فهرست برنامه‌های تحت نظارت یا دسته‌بندی آن‌ها را تغییر بدهی. برای خروج زودهنگام، فقط از بخش «تعهد» و فرایند خروج اضطراری استفاده کن.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        } else {
                            MonitoredAppsScreen(dao = appContext.database.monitoredAppDao(), language = language)
                        }
                    }
                    MainSection.BLOCKS -> ActiveBlocksScreen(
                        dao = (androidx.compose.ui.platform.LocalContext.current.applicationContext as Blocker2Application)
                            .database.activeAppBlockDao(),
                        language = language
                    )
                    MainSection.ACTIVITY -> ActivityLogScreen(
                        dao = appContext.database.activityLogDao(),
                        language = language
                    )
                    MainSection.COMMITMENT -> CommitmentScreen(
                        dao = (androidx.compose.ui.platform.LocalContext.current.applicationContext as Blocker2Application)
                            .database.commitmentSessionDao(),
                        language = language
                    )
                    MainSection.BACKUP -> BackupScreen(database = appContext.database, language = language)
                    MainSection.SETTINGS -> com.blocker2.app.ui.SettingsScreen(
                        language = language,
                        themeMode = themeMode,
                        onLanguageChange = onLanguageChange,
                        onThemeChange = onThemeChange
                    )
                }
            }

            NavigationBar {
                sections.forEachIndexed { index, section ->
                    NavigationBarItem(
                        selected = selectedSection == index,
                        enabled = selectedSection == index || !(commitmentActive && section == MainSection.APPS),
                        onClick = { selectedSection = index },
                        icon = {},
                        label = { Text(if (language == "en") section.englishTitle() else section.title) }
                    )
                }
            }
        }
    }
}


private fun MainSection.englishTitle(): String = when (this) {
    MainSection.HOME -> "Home"
    MainSection.WORDS -> "Words"
    MainSection.TELEGRAM -> "Telegram"
    MainSection.SITES -> "Sites"
    MainSection.APPS -> "Apps"
    MainSection.BLOCKS -> "Blocks"
    MainSection.ACTIVITY -> "Activity"
    MainSection.COMMITMENT -> "Commitment"
    MainSection.BACKUP -> "Backup"
    MainSection.SETTINGS -> "Settings"
}

private fun MainSection.englishSubtitle(): String = when (this) {
    MainSection.HOME -> "Protection overview"
    MainSection.WORDS -> "Manage words and phrases"
    MainSection.TELEGRAM -> "Telegram words and channel filters"
    MainSection.SITES -> "Manage domains and addresses"
    MainSection.APPS -> "Choose monitored apps"
    MainSection.BLOCKS -> "View and manage blocked apps"
    MainSection.ACTIVITY -> "Detection and action history"
    MainSection.COMMITMENT -> "Personal commitment mode"
    MainSection.BACKUP -> "Save and restore your data"
    MainSection.SETTINGS -> "Language and appearance"
}

@androidx.compose.runtime.Composable
private fun HomeOverview(
    language: String,
    showNotificationPermissionButton: Boolean,
    accessibilityPermissionEnabled: Boolean,
    accessibilityConnected: Boolean,
    enabledMonitoredAppCount: Int,
    enabledWordRuleCount: Int,
    enabledTelegramRuleCount: Int,
    enabledSiteRuleCount: Int,
    dnsVpnConnected: Boolean,
    onRequestNotifications: () -> Unit,
    onOpenAccessibilitySettings: () -> Unit
) {
    val english = language == "en"
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        StatusCard(if (english) "Active word rules" else "قوانین فعال واژه‌ها", if (english) "$enabledWordRuleCount word rules are enabled. Enforcement requires Accessibility connection and selected target apps." else "$enabledWordRuleCount قانون واژه‌ای فعال است. اجرای آن به اتصال سرویس دسترس‌پذیری و انتخاب برنامه‌های هدف وابسته است.")
        StatusCard(if (english) "Active Telegram rules" else "قوانین فعال تلگرام", if (english) "$enabledTelegramRuleCount Telegram rules are enabled. Detection is limited to text visible to Accessibility." else "$enabledTelegramRuleCount قانون تلگرام فعال است؛ شناسایی فقط بر اساس متن قابل مشاهده برای Accessibility انجام می‌شود.")
        StatusCard(if (english) "Blocked-site rules" else "قوانین مسدودسازی سایت", if (english) "$enabledSiteRuleCount site rules are enabled. Enforcement requires the local VPN connection." else "$enabledSiteRuleCount قانون مسدودسازی سایت فعال است. اجرای آن به اتصال VPN محلی وابسته است.")
        StatusCard(if (english) "Accessibility permission" else "سرویس دسترس‌پذیری", if (accessibilityPermissionEnabled) { if (english) "Permission is enabled in Android settings." else "مجوز دسترس‌پذیری در تنظیمات اندروید فعال است." } else { if (english) "Permission is not enabled yet." else "مجوز دسترس‌پذیری هنوز فعال نشده است." })
        StatusCard(if (english) "Service connection" else "اتصال زندهٔ سرویس", if (accessibilityConnected) { if (english) "Accessibility service is connected." else "سرویس Accessibility به سیستم متصل است." } else { if (english) "Service connection is not confirmed. Permission alone does not mean the service is connected." else "سرویس در حال حاضر اتصال تأییدشده ندارد. فعال بودن مجوز به‌تنهایی به معنی اتصال سرویس نیست." })
        StatusCard(if (english) "Monitored apps" else "برنامه‌های تحت نظارت", if (english) "$enabledMonitoredAppCount apps are selected and enabled for monitoring." else "$enabledMonitoredAppCount برنامه برای پایش انتخاب و فعال شده‌اند. این عدد تعداد برنامه‌های انتخاب‌شده است، نه برنامهٔ در حال اجرا.")
        Button(onClick = onOpenAccessibilitySettings, modifier = Modifier.fillMaxWidth()) { Text(if (english) "Open Accessibility settings" else "بازکردن تنظیمات دسترس‌پذیری") }
        if (showNotificationPermissionButton) {
            Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface, tonalElevation = 2.dp) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(if (english) "Notification permission" else "اجازهٔ اعلان‌ها", fontWeight = FontWeight.SemiBold)
                    Text(if (english) "Allow notifications for timed-block alerts. Blocking still works without this permission." else "برای نمایش هشدار هنگام شروع مسدودسازی زمان‌دار، اجازهٔ اعلان‌ها را فعال کنید. مسدودسازی حتی بدون این اجازه نیز اجرا می‌شود.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Button(onClick = onRequestNotifications) { Text(if (english) "Enable notifications" else "فعال‌کردن اعلان‌ها") }
                }
            }
        }
        StatusCard(if (english) "Local VPN connection" else "اتصال VPN محلی", if (dnsVpnConnected) { if (english) "Local DNS tunnel is connected." else "تونل DNS محلی برقرار است." } else { if (english) "Local DNS tunnel is disconnected; site rules are not enforced." else "تونل DNS محلی متصل نیست؛ قوانین سایت در حال حاضر اعمال نمی‌شوند." })
        Spacer(modifier = Modifier.padding(2.dp))
        Text(text = if (accessibilityPermissionEnabled) { if (english) "Accessibility permission is enabled. Select apps to monitor in the Apps section." else "مجوز دسترس‌پذیری فعال است. برای شروع پایش، برنامه‌های موردنظر را در بخش «برنامه‌ها» انتخاب کن." } else { if (english) "To enable word filtering, turn on Accessibility in Android settings. Status refreshes when you return." else "برای فعال‌سازی فیلتر واژه‌ها، مجوز دسترس‌پذیری را در تنظیمات اندروید روشن کن. پس از بازگشت به برنامه، وضعیت مجوز به‌روزرسانی می‌شود." }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@androidx.compose.runtime.Composable
private fun StatusCard(title: String, status: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(
                status,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@androidx.compose.runtime.Composable
private fun EmptySection(title: String, description: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(title, fontWeight = FontWeight.Bold)
            Text(
                description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
