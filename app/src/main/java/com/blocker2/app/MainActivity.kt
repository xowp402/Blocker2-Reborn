package com.blocker2.app

import android.Manifest
import android.content.pm.PackageManager
import android.content.Intent
import android.net.VpnService
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.blocker2.app.ui.CommitmentScreen
import com.blocker2.app.ui.ActiveBlocksScreen
import com.blocker2.app.ui.ActivityLogScreen
import com.blocker2.app.ui.MonitoredAppsScreen
import com.blocker2.app.ui.WordRulesScreen
import com.blocker2.app.ui.TelegramRulesScreen
import com.blocker2.app.ui.SiteRulesScreen
import com.blocker2.app.ui.theme.Blocker2Theme
import com.blocker2.app.vpn.LocalDnsVpnService
import com.blocker2.app.startup.StartupPreferences

class MainActivity : ComponentActivity() {
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
        val needsNotificationPermission =
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED

        setContent {
            Blocker2Theme {
                Blocker2App(
                    showNotificationPermissionButton = needsNotificationPermission,
                    onRequestNotifications = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    },
                    onStartVpn = { requestLocalDnsVpn() },
                    onStopVpn = { stopLocalDnsVpn() }
                )
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
    COMMITMENT("تعهد", "حالت تعهد شخصی و خروج اضطراری")
}

@androidx.compose.runtime.Composable
private fun Blocker2App(
    showNotificationPermissionButton: Boolean,
    onRequestNotifications: () -> Unit,
    onStartVpn: () -> Unit,
    onStopVpn: () -> Unit
) {
    var selectedSection by remember { mutableIntStateOf(0) }
    val appContext = androidx.compose.ui.platform.LocalContext.current.applicationContext as Blocker2Application
    val commitmentSession by appContext.database.commitmentSessionDao()
        .observeSession().collectAsState(initial = null)
    val commitmentActive = commitmentSession?.expiresAt?.let { it > System.currentTimeMillis() } == true
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
                        text = "کنترل آگاهانه، تمرکز بیشتر",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = "نسخه ۱.۰.۰",
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
                    text = sections[selectedSection].title,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = sections[selectedSection].subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                when (sections[selectedSection]) {
                    MainSection.HOME -> HomeOverview(showNotificationPermissionButton, onRequestNotifications)
                    MainSection.WORDS -> WordRulesScreen(
                        wordDao = (androidx.compose.ui.platform.LocalContext.current.applicationContext as Blocker2Application)
                            .database.wordRuleDao(),
                        appDao = (androidx.compose.ui.platform.LocalContext.current.applicationContext as Blocker2Application)
                            .database.monitoredAppDao()
                    )
                    MainSection.TELEGRAM -> TelegramRulesScreen(
                        dao = (androidx.compose.ui.platform.LocalContext.current.applicationContext as Blocker2Application)
                            .database.telegramFilterRuleDao()
                    )
                    MainSection.SITES -> SiteRulesScreen(
                        dao = (androidx.compose.ui.platform.LocalContext.current.applicationContext as Blocker2Application)
                            .database.siteRuleDao(),
                        onStartVpn = onStartVpn,
                        onStopVpn = onStopVpn
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
                                    Text("تنظیمات برنامه‌ها موقتاً قفل است", fontWeight = FontWeight.Bold)
                                    Text(
                                        "تا پایان حالت تعهد نمی‌توانی فهرست برنامه‌های تحت نظارت یا دسته‌بندی آن‌ها را تغییر بدهی. برای خروج زودهنگام، فقط از بخش «تعهد» و فرایند خروج اضطراری استفاده کن.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        } else {
                            MonitoredAppsScreen(dao = appContext.database.monitoredAppDao())
                        }
                    }
                    MainSection.BLOCKS -> ActiveBlocksScreen(
                        dao = (androidx.compose.ui.platform.LocalContext.current.applicationContext as Blocker2Application)
                            .database.activeAppBlockDao()
                    )
                    MainSection.ACTIVITY -> ActivityLogScreen(
                        dao = (androidx.compose.ui.platform.LocalContext.current.applicationContext as Blocker2Application)
                            .database.activityLogDao()
                    )
                    MainSection.COMMITMENT -> CommitmentScreen(
                        dao = (androidx.compose.ui.platform.LocalContext.current.applicationContext as Blocker2Application)
                            .database.commitmentSessionDao()
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
                        label = { Text(section.title) }
                    )
                }
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun HomeOverview(
    showNotificationPermissionButton: Boolean,
    onRequestNotifications: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        StatusCard(
            title = "فیلتر واژه‌ها",
            status = "هنوز راه‌اندازی نشده"
        )
        StatusCard(
            title = "فیلتر سایت‌ها",
            status = "موتور DNS آمادهٔ راه‌اندازی از صفحهٔ سایت‌ها"
        )
        StatusCard(
            title = "سرویس دسترس‌پذیری",
            status = "نیازمند فعال‌سازی مجوز در تنظیمات اندروید"
        )
        if (showNotificationPermissionButton) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 2.dp
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("اجازهٔ اعلان‌ها", fontWeight = FontWeight.SemiBold)
                    Text(
                        "برای نمایش هشدار هنگام شروع مسدودسازی زمان‌دار، اجازهٔ اعلان‌ها را فعال کنید. مسدودسازی حتی بدون این اجازه نیز اجرا می‌شود.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(onClick = onRequestNotifications) {
                        Text("فعال‌کردن اعلان‌ها")
                    }
                }
            }
        }
        StatusCard(
            title = "اتصال VPN محلی",
            status = "برای شروع، مجوز VPN را در صفحهٔ سایت‌ها تأیید کنید"
        )
        Spacer(modifier = Modifier.padding(2.dp))
        Text(
            text = "این نسخه، پوسته اولیه پروژه است. وضعیت‌ها عمداً واقعی و شفاف نمایش داده می‌شوند؛ هیچ فیلتری تا زمان تکمیل موتور مربوطه فعال اعلام نمی‌شود.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
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
