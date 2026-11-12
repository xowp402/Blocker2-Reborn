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
fun TelegramRulesScreen(dao: TelegramFilterRuleDao, language: String) {
    val english = language == "en"
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
                Text(if (english) "Telegram filters" else "فیلتر اختصاصی تلگرام", style = MaterialTheme.typography.titleMedium)
                Text(if (english) "${rules.count { it.enabled }} of ${rules.size} active" else "${rules.count { it.enabled }} فعال از ${rules.size} قانون", style = MaterialTheme.typography.bodySmall)
            }
            Button(onClick = { creating = true }) { Text(if (english) "Add" else "افزودن") }
        }
        Text(
            "این فیلتر فقط هنگام پایش برنامه‌های تلگرام اجرا می‌شود. تشخیص به متن قابل مشاهده برای سرویس دسترس‌پذیری محدود است.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (rules.isEmpty()) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.padding(16.dp)) {
                    Text(if (english) "No Telegram rules added yet" else "هنوز قانون تلگرامی ثبت نشده است")
                    Text(if (english) "Add a word, phrase, channel link, channel ID, or username." else "می‌توانید واژه، عبارت، لینک کانال، شناسه یا نام کاربری اضافه کنید.", style = MaterialTheme.typography.bodySmall)
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
                                    Text("${telegramTypeLabel(rule.ruleType, english)} • ${telegramReactionLabel(rule.reaction, english)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Switch(
                                    checked = rule.enabled,
                                    onCheckedChange = { value -> scope.launch { dao.setEnabled(rule.id, value, System.currentTimeMillis()) } }
                                )
                            }
                            Text(if (english) "Detections: ${rule.detectionCount} • Block: ${rule.blockDurationMillis / 60_000L} min" else "تشخیص: ${rule.detectionCount} • مسدودی: ${rule.blockDurationMillis / 60_000L} دقیقه", style = MaterialTheme.typography.bodySmall)
                            Row {
                                TextButton(onClick = { editing = rule }) { Text(if (english) "Edit" else "ویرایش") }
                                TextButton(onClick = { deleting = rule }) { Text(if (english) "Delete" else "حذف") }
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
            language = language,
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
            title = { Text(if (english) "Delete Telegram rule?" else "حذف قانون تلگرام؟") },
            text = { Text(if (english) "“${rule.value}” will be deleted." else "«${rule.value}» حذف می‌شود.") },
            confirmButton = {
                Button(onClick = {
                    scope.launch { dao.deleteById(rule.id) }
                    deleting = null
                }) { Text("حذف") }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text(if (english) "Cancel" else "انصراف") } }
        )
    }
}

@Composable
private fun TelegramRuleDialog(
    initial: TelegramFilterRuleEntity?,
    language: String,
    onDismiss: () -> Unit,
    onSave: (TelegramFilterRuleEntity) -> Unit
) {
    val english = language == "en"
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
        title = { Text(if (english) { if (initial == null) "New Telegram rule" else "Edit Telegram rule" } else { if (initial == null) "قانون جدید تلگرام" else "ویرایش قانون تلگرام" }) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TelegramDropdown(if (english) "Type: ${telegramTypeLabel(type, true)}" else "نوع: ${telegramTypeLabel(type)}", typeExpanded, { typeExpanded = it }) {
                    listOf("WORD", "PHRASE", "CHANNEL_URL", "CHANNEL_ID", "USERNAME").forEach { item ->
                        DropdownMenuItem(text = { Text(telegramTypeLabel(item, english)) }, onClick = { type = item; typeExpanded = false })
                    }
                }
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it; error = null },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(when (type) {
                        "WORD" -> if (english) "Word" else "واژه"
                        "PHRASE" -> if (english) "Phrase" else "عبارت"
                        "CHANNEL_URL" -> if (english) "Channel link" else "لینک کانال"
                        "CHANNEL_ID" -> if (english) "Channel ID" else "شناسه کانال"
                        else -> if (english) "Username (with or without @)" else "نام کاربری (با یا بدون @)"
                    }) },
                    supportingText = { Text(if (english) "The value must appear in Telegram text visible to Accessibility." else "مقدار باید در متن قابل مشاهدهٔ تلگرام وجود داشته باشد.") }
                )
                TelegramDropdown(if (english) "Action: ${telegramReactionLabel(reaction, true)}" else "واکنش: ${telegramReactionLabel(reaction)}", reactionExpanded, { reactionExpanded = it }) {
                    listOf("WARN_AND_EXIT", "TEMPORARY_BLOCK", "EXIT_ONLY", "LOG_ONLY").forEach { item ->
                        DropdownMenuItem(text = { Text(telegramReactionLabel(item, english)) }, onClick = { reaction = item; reactionExpanded = false })
                    }
                }
                OutlinedTextField(
                    value = duration,
                    onValueChange = { duration = it.filter(Char::isDigit) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(if (english) "Block duration in minutes (0–1440)" else "مدت مسدودی به دقیقه (۰ تا ۱۴۴۰)") },
                    singleLine = true
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = enabled, onCheckedChange = { enabled = it })
                    Text(if (english) "Enable rule" else "قانون فعال باشد")
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            Button(onClick = {
                val clean = value.trim()
                val minutes = duration.toLongOrNull()
                when {
                    clean.isBlank() -> error = if (english) "Enter a rule value." else "مقدار قانون را وارد کنید."
                    minutes == null || minutes > 1440L -> error = if (english) "Duration must be between 0 and 1440 minutes." else "مدت باید بین صفر تا ۱۴۴۰ دقیقه باشد."
                    type == "CHANNEL_ID" && clean.length < 3 -> error = if (english) "The channel ID looks invalid." else "شناسه کانال معتبر به نظر نمی‌رسد."
                    else -> {
                        val normalized = when (type) {
                            "WORD", "PHRASE" -> WordNormalizer.normalize(clean)
                            "USERNAME" -> clean.removePrefix("@").lowercase(Locale.ROOT)
                            else -> clean.lowercase(Locale.ROOT)
                        }
                        if (normalized.isBlank()) {
                            error = if (english) "The value is empty after normalization." else "مقدار پس از نرمال‌سازی خالی است."
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
            }) { Text(if (english) "Save" else "ذخیره") }
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

private fun telegramTypeLabel(value: String, english: Boolean = false) = when (value) {
    "WORD" -> "واژه"
    "PHRASE" -> "عبارت"
    "CHANNEL_URL" -> "لینک کانال"
    "CHANNEL_ID" -> "شناسه کانال"
    "USERNAME" -> "نام کاربری"
    else -> value
}

private fun telegramReactionLabel(value: String, english: Boolean = false) = when (value) {
    "WARN_AND_EXIT" -> if (english) "Warn and exit" else "هشدار و خروج"
    "TEMPORARY_BLOCK" -> if (english) "Temporary block" else "مسدودی موقت"
    "EXIT_ONLY" -> if (english) "Exit Telegram" else "خروج از تلگرام"
    "LOG_ONLY" -> if (english) "Log only" else "فقط ثبت گزارش"
    else -> value
}
