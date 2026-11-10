package com.blocker2.app.ui

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.blocker2.app.data.local.dao.MonitoredAppDao
import com.blocker2.app.data.local.entity.MonitoredAppEntity
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope

private data class InstalledLaunchableApp(
    val packageName: String,
    val label: String
)

private val appCategories = listOf(
    "SOCIAL" to "شبکه‌های اجتماعی",
    "GAMES" to "بازی‌ها",
    "BROWSERS" to "مرورگرها",
    "VIDEO" to "ویدئو",
    "ENTERTAINMENT" to "سرگرمی",
    "MESSAGING" to "پیام‌رسان‌ها",
    "OTHER" to "سایر"
)

private fun categoryLabel(value: String): String =
    appCategories.firstOrNull { it.first == value }?.second ?: "سایر"

@Composable
fun MonitoredAppsScreen(dao: MonitoredAppDao) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val savedApps by dao.observeAll().collectAsState(initial = emptyList())
    var search by remember { mutableStateOf("") }

    val installedApps = remember(context.packageName) {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val activities = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.queryIntentActivities(
                intent,
                PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_ALL.toLong())
            )
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.queryIntentActivities(intent, PackageManager.MATCH_ALL)
        }
        activities.mapNotNull { resolveInfo ->
            val appInfo = resolveInfo.activityInfo?.applicationInfo ?: return@mapNotNull null
            val packageName = appInfo.packageName
            if (packageName == context.packageName) return@mapNotNull null
            InstalledLaunchableApp(
                packageName = packageName,
                label = resolveInfo.loadLabel(context.packageManager).toString()
            )
        }.distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
    }

    val savedByPackage = savedApps.associateBy { it.packageName }
    val filteredApps = installedApps.filter {
        it.label.contains(search, ignoreCase = true) ||
            it.packageName.contains(search, ignoreCase = true)
    }
    val monitoredCount = savedApps.count { it.monitoringEnabled }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text("برنامه‌های تحت نظارت", fontWeight = FontWeight.Bold)
                Text(
                    "$monitoredCount برنامه از ${installedApps.size} برنامهٔ قابل‌اجرا انتخاب شده‌اند.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "فقط برنامه‌هایی که اینجا روشن می‌کنی در بررسی واژه‌ها هدف قرار می‌گیرند. خود Blocker2 همیشه مستثناست.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        OutlinedTextField(
            value = search,
            onValueChange = { search = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("جست‌وجوی نام یا شناسهٔ برنامه") },
            singleLine = true
        )

        if (filteredApps.isEmpty()) {
            Text(
                if (installedApps.isEmpty()) "برنامهٔ قابل‌اجرایی پیدا نشد."
                else "نتیجه‌ای برای جست‌وجوی تو پیدا نشد.",
                modifier = Modifier.padding(12.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filteredApps, key = { it.packageName }) { app ->
                    val saved = savedByPackage[app.packageName]
                    val enabled = saved?.monitoringEnabled == true
                    var categoryMenuExpanded by remember(app.packageName) { mutableStateOf(false) }
                    val category = saved?.category ?: "OTHER"
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(app.label, fontWeight = FontWeight.SemiBold)
                                Text(
                                    app.packageName,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                TextButton(onClick = { categoryMenuExpanded = true }) {
                                    Text("گروه: ${categoryLabel(category)}")
                                }
                                DropdownMenu(
                                    expanded = categoryMenuExpanded,
                                    onDismissRequest = { categoryMenuExpanded = false }
                                ) {
                                    appCategories.forEach { (key, label) ->
                                        DropdownMenuItem(
                                            text = { Text(label) },
                                            onClick = {
                                                categoryMenuExpanded = false
                                                scope.launch {
                                                    dao.upsert(
                                                        MonitoredAppEntity(
                                                            packageName = app.packageName,
                                                            appName = app.label,
                                                            category = key,
                                                            monitoringEnabled = enabled,
                                                            detectionCount = saved?.detectionCount ?: 0L,
                                                            updatedAt = System.currentTimeMillis()
                                                        )
                                                    )
                                                }
                                            }
                                        )
                                    }
                                }
                            }
                            Switch(
                                checked = enabled,
                                onCheckedChange = { checked ->
                                    scope.launch {
                                        dao.upsert(
                                            MonitoredAppEntity(
                                                packageName = app.packageName,
                                                appName = app.label,
                                                category = saved?.category ?: "OTHER",
                                                monitoringEnabled = checked,
                                                detectionCount = saved?.detectionCount ?: 0L,
                                                updatedAt = System.currentTimeMillis()
                                            )
                                        )
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
