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
import com.blocker2.app.core.site.SiteRuleMatcher
import com.blocker2.app.data.local.dao.SiteRuleDao
import com.blocker2.app.data.local.entity.AllowedSiteEntity
import com.blocker2.app.data.local.entity.SiteRuleEntity
import kotlinx.coroutines.launch
import java.util.Locale

@Composable
fun SiteRulesScreen(dao: SiteRuleDao, onStartVpn: () -> Unit, onStopVpn: () -> Unit, language: String) {
    val english = language == "en"
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
            if (english) "This engine checks domain names in DNS over UDP and IPv4/IPv6. Direct-IP and URL-path rules are not enforced; HTTPS paths and encrypted DNS are not visible. DNS over TCP and IPv6 packets with extension headers are not yet supported. In these cases, site filtering is disabled to avoid interrupting internet access." else "این موتور نام دامنه را در DNS روی UDP و IPv4/IPv6 بررسی می‌کند. قانون IP مستقیم و مسیر URL اجرا نمی‌شوند؛ مسیر HTTPS و DNS رمزگذاری‌شده قابل مشاهده نیستند. DNS روی TCP و بسته‌های IPv6 دارای Extension Header نیز هنوز پشتیبانی نمی‌شوند؛ در این حالت فیلتر سایت برای جلوگیری از قطع اینترنت خاموش می‌شود.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onStartVpn, modifier = Modifier.weight(1f)) { Text(if (english) "Start DNS filter" else "فعال‌سازی فیلتر DNS") }
            OutlinedButton(onClick = onStopVpn, modifier = Modifier.weight(1f)) { Text(if (english) "Stop filter" else "توقف فیلتر") }
        }
        Text(
            if (english) "Allowed DNS requests are forwarded to 1.1.1.1 and, if it fails, to 8.8.8.8. Domain names are sent to these resolvers, but ordinary web traffic does not pass through the tunnel." else "درخواست‌های DNS غیرمسدود ابتدا به 1.1.1.1 و در صورت خطا به 8.8.8.8 فرستاده می‌شوند؛ نام دامنه‌ها برای حل DNS به این سرویس‌ها ارسال می‌شود، اما ترافیک عادی وب از تونل عبور نمی‌کند.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        TabRow(selectedTabIndex = selectedTab) {
            Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text(if (english) "Blocked (${blocked.size})" else "مسدودشده‌ها (${blocked.size})") })
            Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text(if (english) "Allowed (${allowed.size})" else "مجازها (${allowed.size})") })
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Button(onClick = { if (selectedTab == 0) addBlocked = true else addAllowed = true }) {
                Text(if (english) { if (selectedTab == 0) "Add blocked site" else "Add allowed site" } else { if (selectedTab == 0) "افزودن سایت مسدود" else "افزودن سایت مجاز" })
            }
        }

        if (selectedTab == 0) {
            if (blocked.isEmpty()) {
                SiteEmptyCard(if (english) "No blocked sites" else "فهرست مسدودشده‌ها خالی است", if (english) "Add a domain, with or without its subdomains. URL paths and direct IP addresses are not supported by DNS filtering." else "دامنه یا دامنه به‌همراه زیر‌دامنه‌هایش را اضافه کنید. مسیر URL و IP مستقیم در فیلتر DNS پشتیبانی نمی‌شوند.")
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(blocked, key = { it.id }) { rule ->
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                            Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(rule.pattern, style = MaterialTheme.typography.titleSmall)
                                        Text(siteTypeLabel(rule.ruleType, english), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Switch(
                                        checked = rule.enabled,
                                        onCheckedChange = { enabled ->
                                            scope.launch { dao.setBlockedSiteEnabled(rule.id, enabled, System.currentTimeMillis()) }
                                        }
                                    )
                                }
                                Row {
                                    TextButton(onClick = { deletingBlocked = rule }) { Text(if (english) "Delete" else "حذف") }
                                }
                            }
                        }
                    }
                }
            }
        } else {
            if (allowed.isEmpty()) {
                SiteEmptyCard(if (english) "No allowed sites" else "فهرست مجازها خالی است", if (english) "Add sites that should be exempt from blocking rules." else "سایت‌هایی را که نباید با قوانین مسدود شوند اضافه کنید.")
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
                                    Text(if (english) { if (rule.includeSubdomains) "Including subdomains" else "This host only" } else { if (rule.includeSubdomains) "همراه با زیر‌دامنه‌ها" else "فقط همین میزبان" }, style = MaterialTheme.typography.bodySmall)
                                }
                                TextButton(onClick = { deletingAllowed = rule }) { Text(if (english) "Delete" else "حذف") }
                            }
                        }
                    }
                }
            }
        }
    }

    if (addBlocked) {
        SiteRuleDialog(
            language = language,
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
            language = language,
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
            title = { Text(if (english) "Delete rule?" else "حذف قانون؟") },
            text = { Text(if (english) "“${item.pattern}” will be removed from the blocked list." else "«${item.pattern}» از فهرست مسدودشده‌ها حذف می‌شود.") },
            confirmButton = {
                Button(onClick = {
                    scope.launch { dao.deleteBlockedSite(item.id) }
                    deletingBlocked = null
                }) { Text(if (english) "Delete" else "حذف") }
            },
            dismissButton = { TextButton(onClick = { deletingBlocked = null }) { Text(if (english) "Cancel" else "انصراف") } }
        )
    }
    deletingAllowed?.let { item ->
        AlertDialog(
            onDismissRequest = { deletingAllowed = null },
            title = { Text(if (english) "Delete allowed site?" else "حذف سایت مجاز؟") },
            text = { Text(if (english) "“${item.pattern}” will be removed from the allowed list." else "«${item.pattern}» از فهرست مجازها حذف می‌شود.") },
            confirmButton = {
                Button(onClick = {
                    scope.launch { dao.deleteAllowedSite(item.id) }
                    deletingAllowed = null
                }) { Text("حذف") }
            },
            dismissButton = { TextButton(onClick = { deletingAllowed = null }) { Text(if (english) "Cancel" else "انصراف") } }
        )
    }
}

@Composable
private fun SiteRuleDialog(language: String, onDismiss: () -> Unit, onSave: (String, String) -> Unit) {
    val english = language == "en"
    var type by remember { mutableStateOf("DOMAIN_AND_SUBDOMAINS") }
    var pattern by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (english) "Add blocking rule" else "افزودن قانون مسدودسازی") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) { Text(siteTypeLabel(type, english)) }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    listOf("DOMAIN", "DOMAIN_AND_SUBDOMAINS").forEach { item ->
                        DropdownMenuItem(text = { Text(siteTypeLabel(item, english)) }, onClick = { type = item; expanded = false })
                    }
                }
                OutlinedTextField(
                    value = pattern,
                    onValueChange = { pattern = it; error = null },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(if (english) "Domain name" else "نام دامنه") },
                    placeholder = { Text("example.com") },
                    supportingText = { Text(if (english) "Only domain names are supported; URL paths and direct IPs cannot be filtered." else "فقط نام دامنه پشتیبانی می‌شود؛ مسیر URL و IP مستقیم قابل فیلتر نیستند.") }
                )
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            Button(onClick = {
                val normalized = normalizeSite(pattern)
                if (normalized.isBlank()) {
                    error = if (english) "Enter a valid domain. URL paths and direct IPs are not supported by DNS filtering." else "فقط نام دامنهٔ معتبر وارد کنید؛ مسیر URL و IP مستقیم در فیلتر DNS پشتیبانی نمی‌شوند."
                } else {
                    onSave(type, normalized)
                }
            }) { Text(if (english) "Save" else "ذخیره") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(if (english) "Cancel" else "انصراف") } }
    )
}

@Composable
private fun AllowedSiteDialog(language: String, onDismiss: () -> Unit, onSave: (String, Boolean) -> Unit) {
    val english = language == "en"
    var pattern by remember { mutableStateOf("") }
    var subdomains by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (english) "Add allowed site" else "افزودن سایت مجاز") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = pattern,
                    onValueChange = { pattern = it; error = null },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(if (english) "Domain or host" else "دامنه یا میزبان") },
                    placeholder = { Text("example.com") }
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = subdomains, onCheckedChange = { subdomains = it })
                    Text(if (english) "Include all subdomains" else "شامل تمام زیر‌دامنه‌ها")
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            Button(onClick = {
                val clean = normalizeSite(pattern)
                if (clean.isBlank()) error = if (english) "Enter a valid domain. URL paths and direct IPs are not supported." else "فقط یک نام دامنهٔ معتبر وارد کنید؛ URL مسیر و IP مستقیم پشتیبانی نمی‌شوند."
                else onSave(clean, subdomains)
            }) { Text(if (english) "Save" else "ذخیره") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(if (english) "Cancel" else "انصراف") } }
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

private fun normalizeSite(value: String): String {
    val candidate = value.trim()
        .removePrefix("https://")
        .removePrefix("http://")
    if (candidate.matches(Regex("[0-9]+(\\.[0-9]+){3}"))) return ""
    return SiteRuleMatcher.normalizeHost(candidate)
}

private fun siteTypeLabel(value: String, english: Boolean = false) = when (value) {
    "DOMAIN" -> if (english) "Domain only" else "فقط دامنه"
    "DOMAIN_AND_SUBDOMAINS" -> if (english) "Domain and subdomains" else "دامنه و زیر‌دامنه‌ها"
    "URL_PREFIX" -> if (english) "Unsupported: URL path" else "پشتیبانی نمی‌شود: مسیر URL"
    "IP" -> if (english) "Unsupported: direct IP" else "پشتیبانی نمی‌شود: IP مستقیم"
    else -> value
}
