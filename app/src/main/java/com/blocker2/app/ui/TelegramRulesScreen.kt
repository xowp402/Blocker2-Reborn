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
import com.blocker2.app.data.local.dao.TelegramFilterRuleDao
import com.blocker2.app.data.local.entity.TelegramFilterRuleEntity
import com.blocker2.app.core.text.WordNormalizer
import kotlinx.coroutines.launch
import java.util.Locale

@Composable
fun TelegramRulesScreen(dao: TelegramFilterRuleDao) {
    val rules by dao.observeAll().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var editing by remember { mutableStateOf<TelegramFilterRuleEntity?>(null) }
    var creating by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<TelegramFilterRuleEntity?>(null) }

    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("فیلتر اختصاصی تلگرام", style = MaterialTheme.typography.titleMedium)
                Text("${rules.count { it.enabled }} فعال از ${rules.size} قانون", style = MaterialTheme.typography.bodySmall)
            }
            Button(onClick = { creating = true }) { Text("افزودن") }
        }
        Text(
            "این فیلتر فقط هنگام پایش برنامه‌های تلگرام اجرا می‌شود. تشخیص به متن قابل مشاهده برای سرویس دسترس‌پذیری محدود است.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (rules.isEmpty()) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.padding(16.dp)) {
                    Text("هنوز قانون تلگرامی ثبت نشده است")
                    Text("می‌توانید واژه، عبارت، لینک کانال، شناسه یا نام کاربری اضافه کنید.", style = MaterialTheme.typography.bodySmall)
                }
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(rules, key = { it.id }) { rule ->
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(rule.value, style = MaterialTheme.typography.titleSmall)
                                    Text("${telegramTypeLabel(rule.ruleType)} • ${telegramReactionLabel(rule.reaction)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Switch(
                                    checked = rule.enabled,
                                    onCheckedChange = { value -> scope.launch { dao.setEnabled(rule.id, value, System.currentTimeMillis()) } }
                                )
                            }
                            Text("تشخیص: ${rule.detectionCount} • مسدودی: ${rule.blockDurationMillis / 60_000L} دقیقه", style = MaterialTheme.typography.bodySmall)
                            Row {
                                TextButton(onClick = { editing = rule }) { Text("ویرایش") }
                                TextButton(onClick = { deleting = rule }) { Text("حذف") }
                            }
                        }
                    }
                }
            }
        }
    }

    if (creating || editing != null) {
        TelegramRuleDialog(
            initial = editing,
            onDismiss = { creating = false; editing = null },
            onSave = { rule ->
                scope.launch { dao.upsert(rule) }
                creating = false
                editing = null
            }
        )
    }
    deleting?.let { rule ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("حذف قانون تلگرام؟") },
            text = { Text("«${rule.value}» حذف می‌شود.") },
            confirmButton = {
                Button(onClick = {
                    scope.launch { dao.deleteById(rule.id) }
                    deleting = null
                }) { Text("حذف") }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("انصراف") } }
        )
    }
}

@Composable
private fun TelegramRuleDialog(
    initial: TelegramFilterRuleEntity?,
    onDismiss: () -> Unit,
    onSave: (TelegramFilterRuleEntity) -> Unit
) {
    var type by remember(initial) { mutableStateOf(initial?.ruleType ?: "WORD") }
    var value by remember(initial) { mutableStateOf(initial?.value.orEmpty()) }
    var reaction by remember(initial) { mutableStateOf(initial?.reaction ?: "WARN_AND_EXIT") }
    var duration by remember(initial) { mutableStateOf(((initial?.blockDurationMillis ?: 120_000L) / 60_000L).toString()) }
    var enabled by remember(initial) { mutableStateOf(initial?.enabled ?: true) }
    var typeExpanded by remember { mutableStateOf(false) }
    var reactionExpanded by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "قانون جدید تلگرام" else "ویرایش قانون تلگرام") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TelegramDropdown("نوع: ${telegramTypeLabel(type)}", typeExpanded, { typeExpanded = it }) {
                    listOf("WORD", "PHRASE", "CHANNEL_URL", "CHANNEL_ID", "USERNAME").forEach { item ->
                        DropdownMenuItem(text = { Text(telegramTypeLabel(item)) }, onClick = { type = item; typeExpanded = false })
                    }
                }
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it; error = null },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(when (type) {
                        "WORD" -> "واژه"
                        "PHRASE" -> "عبارت"
                        "CHANNEL_URL" -> "لینک کانال"
                        "CHANNEL_ID" -> "شناسه کانال"
                        else -> "نام کاربری (با یا بدون @)"
                    }) },
                    supportingText = { Text("مقدار باید در متن قابل مشاهدهٔ تلگرام وجود داشته باشد.") }
                )
                TelegramDropdown("واکنش: ${telegramReactionLabel(reaction)}", reactionExpanded, { reactionExpanded = it }) {
                    listOf("WARN_AND_EXIT", "TEMPORARY_BLOCK", "EXIT_ONLY", "LOG_ONLY").forEach { item ->
                        DropdownMenuItem(text = { Text(telegramReactionLabel(item)) }, onClick = { reaction = item; reactionExpanded = false })
                    }
                }
                OutlinedTextField(
                    value = duration,
                    onValueChange = { duration = it.filter(Char::isDigit) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("مدت مسدودی به دقیقه (۰ تا ۱۴۴۰)") },
                    singleLine = true
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = enabled, onCheckedChange = { enabled = it })
                    Text("قانون فعال باشد")
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            Button(onClick = {
                val clean = value.trim()
                val minutes = duration.toLongOrNull()
                when {
                    clean.isBlank() -> error = "مقدار قانون را وارد کنید."
                    minutes == null || minutes > 1440L -> error = "مدت باید بین صفر تا ۱۴۴۰ دقیقه باشد."
                    type == "CHANNEL_ID" && clean.length < 3 -> error = "شناسه کانال معتبر به نظر نمی‌رسد."
                    else -> {
                        val normalized = when (type) {
                            "WORD", "PHRASE" -> WordNormalizer.normalize(clean)
                            "USERNAME" -> clean.removePrefix("@").lowercase(Locale.ROOT)
                            else -> clean.lowercase(Locale.ROOT)
                        }
                        if (normalized.isBlank()) {
                            error = "مقدار پس از نرمال‌سازی خالی است."
                        } else {
                            val now = System.currentTimeMillis()
                            onSave(TelegramFilterRuleEntity(
                                id = initial?.id ?: 0,
                                ruleType = type,
                                value = clean,
                                normalizedValue = normalized,
                                enabled = enabled,
                                reaction = reaction,
                                blockDurationMillis = minutes * 60_000L,
                                detectionCount = initial?.detectionCount ?: 0,
                                createdAt = initial?.createdAt ?: now,
                                updatedAt = now
                            ))
                        }
                    }
                }
            }) { Text("ذخیره") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } }
    )
}

@Composable
private fun TelegramDropdown(label: String, expanded: Boolean, onExpanded: (Boolean) -> Unit, content: @Composable () -> Unit) {
    Column {
        OutlinedButton(onClick = { onExpanded(true) }, modifier = Modifier.fillMaxWidth()) { Text(label) }
        DropdownMenu(expanded = expanded, onDismissRequest = { onExpanded(false) }) { content() }
    }
}

private fun telegramTypeLabel(value: String) = when (value) {
    "WORD" -> "واژه"
    "PHRASE" -> "عبارت"
    "CHANNEL_URL" -> "لینک کانال"
    "CHANNEL_ID" -> "شناسه کانال"
    "USERNAME" -> "نام کاربری"
    else -> value
}

private fun telegramReactionLabel(value: String) = when (value) {
    "WARN_AND_EXIT" -> "هشدار و خروج"
    "TEMPORARY_BLOCK" -> "مسدودی موقت"
    "EXIT_ONLY" -> "خروج از تلگرام"
    "LOG_ONLY" -> "فقط ثبت گزارش"
    else -> value
}
