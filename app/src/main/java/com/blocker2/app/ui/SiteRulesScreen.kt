package com.blocker2.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.blocker2.app.data.local.dao.SiteRuleDao
import com.blocker2.app.data.local.entity.AllowedSiteEntity
import com.blocker2.app.data.local.entity.SiteRuleEntity
import kotlinx.coroutines.launch
import java.util.Locale

@Composable
fun SiteRulesScreen(dao: SiteRuleDao, onStartVpn: () -> Unit, onStopVpn: () -> Unit) {
    val blocked by dao.observeBlockedSites().collectAsState(initial = emptyList())
    val allowed by dao.observeAllowedSites().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var selectedTab by remember { mutableStateOf(0) }
    var addBlocked by remember { mutableStateOf(false) }
    var addAllowed by remember { mutableStateOf(false) }
    var deletingBlocked by remember { mutableStateOf<SiteRuleEntity?>(null) }
    var deletingAllowed by remember { mutableStateOf<AllowedSiteEntity?>(null) }

    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            "قوانین این صفحه در حافظهٔ محلی ذخیره می‌شوند. اجرای مسدودسازی شبکه‌ای به موتور VPN محلی نیاز دارد و تا فعال‌شدن آن، این فهرست به‌تنهایی ترافیک را مسدود نمی‌کند.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        TabRow(selectedTabIndex = selectedTab) {
            Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("مسدودشده‌ها (${blocked.size})") })
            Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("مجازها (${allowed.size})") })
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Button(onClick = { if (selectedTab == 0) addBlocked = true else addAllowed = true }) {
                Text(if (selectedTab == 0) "افزودن مورد مسدود" else "افزودن سایت مجاز")
            }
        }

        if (selectedTab == 0) {
            if (blocked.isEmpty()) {
                SiteEmptyCard("فهرست مسدودشده‌ها خالی است", "دامنه، زیر‌دامنه، نشانی URL یا IP اضافه کنید.")
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(blocked, key = { it.id }) { rule ->
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                            Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(rule.pattern, style = MaterialTheme.typography.titleSmall)
                                        Text(siteTypeLabel(rule.ruleType), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Switch(
                                        checked = rule.enabled,
                                        onCheckedChange = { enabled ->
                                            scope.launch { dao.setBlockedSiteEnabled(rule.id, enabled, System.currentTimeMillis()) }
                                        }
                                    )
                                }
                                Row {
                                    TextButton(onClick = { deletingBlocked = rule }) { Text("حذف") }
                                }
                            }
                        }
                    }
                }
            }
        } else {
            if (allowed.isEmpty()) {
                SiteEmptyCard("فهرست مجازها خالی است", "سایت‌هایی را که نباید با قوانین مسدود شوند اضافه کنید.")
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(allowed, key = { it.id }) { rule ->
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(rule.pattern, style = MaterialTheme.typography.titleSmall)
                                    Text(if (rule.includeSubdomains) "همراه با زیر‌دامنه‌ها" else "فقط همین میزبان", style = MaterialTheme.typography.bodySmall)
                                }
                                TextButton(onClick = { deletingAllowed = rule }) { Text("حذف") }
                            }
                        }
                    }
                }
            }
        }
    }

    if (addBlocked) {
        SiteRuleDialog(
            onDismiss = { addBlocked = false },
            onSave = { type, pattern ->
                val normalized = normalizeSite(pattern)
                scope.launch {
                    dao.upsertBlockedSite(
                        SiteRuleEntity(
                            ruleType = type,
                            pattern = pattern.trim(),
                            normalizedPattern = normalized
                        )
                    )
                }
                addBlocked = false
            }
        )
    }
    if (addAllowed) {
        AllowedSiteDialog(
            onDismiss = { addAllowed = false },
            onSave = { pattern, subdomains ->
                val normalized = normalizeSite(pattern)
                scope.launch {
                    runCatching {
                        dao.upsertAllowedSite(
                            AllowedSiteEntity(
                                pattern = pattern.trim(),
                                normalizedPattern = normalized,
                                includeSubdomains = subdomains
                            )
                        )
                    }
                }
                addAllowed = false
            }
        )
    }
    deletingBlocked?.let { item ->
        AlertDialog(
            onDismissRequest = { deletingBlocked = null },
            title = { Text("حذف قانون؟") },
            text = { Text("«${item.pattern}» از فهرست مسدودشده‌ها حذف می‌شود.") },
            confirmButton = {
                Button(onClick = {
                    scope.launch { dao.deleteBlockedSite(item.id) }
                    deletingBlocked = null
                }) { Text("حذف") }
            },
            dismissButton = { TextButton(onClick = { deletingBlocked = null }) { Text("انصراف") } }
        )
    }
    deletingAllowed?.let { item ->
        AlertDialog(
            onDismissRequest = { deletingAllowed = null },
            title = { Text("حذف سایت مجاز؟") },
            text = { Text("«${item.pattern}» از فهرست مجازها حذف می‌شود.") },
            confirmButton = {
                Button(onClick = {
                    scope.launch { dao.deleteAllowedSite(item.id) }
                    deletingAllowed = null
                }) { Text("حذف") }
            },
            dismissButton = { TextButton(onClick = { deletingAllowed = null }) { Text("انصراف") } }
        )
    }
}

@Composable
private fun SiteRuleDialog(onDismiss: () -> Unit, onSave: (String, String) -> Unit) {
    var type by remember { mutableStateOf("DOMAIN_AND_SUBDOMAINS") }
    var pattern by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("افزودن قانون مسدودسازی") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) { Text(siteTypeLabel(type)) }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    listOf("DOMAIN", "DOMAIN_AND_SUBDOMAINS", "URL_PREFIX", "IP").forEach { item ->
                        DropdownMenuItem(text = { Text(siteTypeLabel(item)) }, onClick = { type = item; expanded = false })
                    }
                }
                OutlinedTextField(
                    value = pattern,
                    onValueChange = { pattern = it; error = null },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("دامنه، URL یا IP") },
                    placeholder = { Text("example.com") },
                    supportingText = { Text("برای URL کامل، نوع «پیشوند URL» را انتخاب کنید.") }
                )
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            Button(onClick = {
                val normalized = normalizeSite(pattern)
                when {
                    normalized.isBlank() -> error = "مقدار را وارد کنید."
                    normalized.contains(" ") -> error = "نشانی نباید فاصله داشته باشد."
                    type == "URL_PREFIX" && !normalized.contains("/") -> error = "برای پیشوند URL مسیر یا / وارد کنید."
                    type == "IP" && !normalized.matches(Regex("[0-9a-fA-F:.%]+")) -> error = "قالب IP معتبر نیست."
                    else -> onSave(type, pattern)
                }
            }) { Text("ذخیره") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } }
    )
}

@Composable
private fun AllowedSiteDialog(onDismiss: () -> Unit, onSave: (String, Boolean) -> Unit) {
    var pattern by remember { mutableStateOf("") }
    var subdomains by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("افزودن سایت مجاز") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = pattern,
                    onValueChange = { pattern = it; error = null },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("دامنه یا میزبان") },
                    placeholder = { Text("example.com") }
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = subdomains, onCheckedChange = { subdomains = it })
                    Text("شامل تمام زیر‌دامنه‌ها")
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            Button(onClick = {
                val clean = normalizeSite(pattern)
                if (clean.isBlank() || clean.contains("/") || clean.contains(" ")) error = "یک دامنه یا میزبان معتبر وارد کنید."
                else onSave(pattern, subdomains)
            }) { Text("ذخیره") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } }
    )
}

@Composable
private fun SiteEmptyCard(title: String, description: String) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun normalizeSite(value: String): String =
    value.trim().lowercase(Locale.ROOT)
        .removePrefix("https://")
        .removePrefix("http://")
        .removeSuffix("/")

private fun siteTypeLabel(value: String) = when (value) {
    "DOMAIN" -> "فقط دامنه"
    "DOMAIN_AND_SUBDOMAINS" -> "دامنه و زیر‌دامنه‌ها"
    "URL_PREFIX" -> "پیشوند URL (مسیر کامل)"
    "IP" -> "نشانی IP"
    else -> value
}
