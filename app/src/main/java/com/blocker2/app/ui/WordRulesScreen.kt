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
import com.blocker2.app.core.text.RuleMatchMode
import com.blocker2.app.core.text.WordNormalizer
import com.blocker2.app.data.local.dao.MonitoredAppDao
import com.blocker2.app.data.local.dao.WordRuleDao
import com.blocker2.app.data.local.entity.WordRuleEntity
import kotlinx.coroutines.launch

@Composable
fun WordRulesScreen(wordDao: WordRuleDao, appDao: MonitoredAppDao, language: String) {
    val english = language == "en"
    val rules by wordDao.observeAll().collectAsState(initial = emptyList())
    val apps by appDao.observeAll().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var editing by remember { mutableStateOf<WordRuleEntity?>(null) }
    var creating by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<WordRuleEntity?>(null) }

    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(if (english) "Word rules" else "قوانین واژه‌ها", style = MaterialTheme.typography.titleMedium)
                Text(
                    if (english) "${rules.count { it.enabled }} of ${rules.size} active" else "${rules.count { it.enabled }} فعال از ${rules.size} قانون",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Button(onClick = { creating = true }) { Text(if (english) "Add rule" else "افزودن قانون") }
        }

        if (rules.isEmpty()) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(if (english) "No rules added yet" else "هنوز قانونی ثبت نشده است", style = MaterialTheme.typography.titleSmall)
                    Text(
                        if (english) "Add a word or phrase for monitoring in selected apps." else "یک واژه یا عبارت اضافه کنید تا موتور نظارت بتواند آن را در برنامه‌های انتخاب‌شده بررسی کند.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(rules, key = { it.id }) { rule ->
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(rule.text, style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        "${categoryLabel(rule.category, english)} • ${reactionLabel(rule.reaction, english)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Switch(
                                    checked = rule.enabled,
                                    onCheckedChange = { enabled ->
                                        scope.launch { wordDao.setEnabled(rule.id, enabled, System.currentTimeMillis()) }
                                    }
                                )
                            }
                            Text(
                                if (english) "Detections: ${rule.detectionCount} • Block: ${rule.blockDurationMillis / 60_000L} min" else "تعداد تشخیص: ${rule.detectionCount} • مدت مسدودی: ${rule.blockDurationMillis / 60_000L} دقیقه",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton(onClick = { editing = rule }) { Text(if (english) "Edit" else "ویرایش") }
                                TextButton(onClick = { deleteTarget = rule }) { Text(if (english) "Delete" else "حذف") }
                            }
                        }
                    }
                }
            }
        }
    }

    if (creating || editing != null) {
        RuleEditorDialog(
            initial = editing,
            apps = apps,
            language = language,
            onDismiss = { creating = false; editing = null },
            onSave = { rule ->
                scope.launch { wordDao.upsert(rule) }
                creating = false
                editing = null
            }
        )
    }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(if (english) "Delete rule?" else "حذف قانون؟") },
            text = { Text(if (english) "Rule “${target.text}” will be deleted." else "قانون «${target.text}» حذف خواهد شد.") },
            confirmButton = {
                Button(onClick = {
                    scope.launch { wordDao.deleteById(target.id) }
                    deleteTarget = null
                }) { Text(if (english) "Delete" else "حذف") }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text(if (english) "Cancel" else "انصراف") } }
        )
    }
}

@Composable
private fun RuleEditorDialog(
    initial: WordRuleEntity?,
    apps: List<com.blocker2.app.data.local.entity.MonitoredAppEntity>,
    language: String,
    onDismiss: () -> Unit,
    onSave: (WordRuleEntity) -> Unit
) {
    val english = language == "en"
    var text by remember(initial) { mutableStateOf(initial?.text.orEmpty()) }
    var category by remember(initial) { mutableStateOf(initial?.category ?: "GENERAL") }
    var language by remember(initial) { mutableStateOf(initial?.language ?: "AUTO") }
    var matchMode by remember(initial) { mutableStateOf(initial?.matchMode ?: "NORMALIZED_WORD") }
    var reaction by remember(initial) { mutableStateOf(initial?.reaction ?: "WARN_AND_EXIT") }
    var duration by remember(initial) { mutableStateOf(((initial?.blockDurationMillis ?: 120_000L) / 60_000L).toString()) }
    var warning by remember(initial) { mutableStateOf(initial?.customWarning.orEmpty()) }
    var enabled by remember(initial) { mutableStateOf(initial?.enabled ?: true) }
    var statistics by remember(initial) { mutableStateOf(initial?.statisticsEnabled ?: true) }
    var targets by remember(initial, apps) {
        mutableStateOf(initial?.targetPackages?.split("|")?.filter { it.isNotBlank() }?.toSet() ?: emptySet())
    }
    var categoryExpanded by remember { mutableStateOf(false) }
    var languageExpanded by remember { mutableStateOf(false) }
    var modeExpanded by remember { mutableStateOf(false) }
    var reactionExpanded by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (english) { if (initial == null) "New rule" else "Edit rule" } else { if (initial == null) "قانون جدید" else "ویرایش قانون" }) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it; error = null },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(if (english) "Word or phrase" else "واژه یا عبارت") },
                    singleLine = true
                )
                EditorDropdown(if (english) "Category: ${categoryLabel(category, true)}" else "دسته‌بندی: ${categoryLabel(category)}", categoryExpanded, { categoryExpanded = it }) {
                    listOf("GENERAL", "SOCIAL", "GAMES", "VIDEO", "ADULT", "CUSTOM").forEach { value ->
                        DropdownMenuItem(text = { Text(categoryLabel(value, english)) }, onClick = { category = value; categoryExpanded = false })
                    }
                }
                EditorDropdown(if (english) "Language: ${languageLabel(language, true)}" else "زبان: ${languageLabel(language)}", languageExpanded, { languageExpanded = it }) {
                    listOf("AUTO", "FA", "EN").forEach { value ->
                        DropdownMenuItem(text = { Text(languageLabel(value, english)) }, onClick = { language = value; languageExpanded = false })
                    }
                }
                EditorDropdown(if (english) "Match mode: ${modeLabel(matchMode, true)}" else "روش تطبیق: ${modeLabel(matchMode)}", modeExpanded, { modeExpanded = it }) {
                    RuleMatchMode.entries.forEach { value ->
                        DropdownMenuItem(text = { Text(modeLabel(value.name, english)) }, onClick = { matchMode = value.name; modeExpanded = false })
                    }
                }
                EditorDropdown(if (english) "Action: ${reactionLabel(reaction, true)}" else "واکنش: ${reactionLabel(reaction)}", reactionExpanded, { reactionExpanded = it }) {
                    listOf("WARN_AND_EXIT", "TEMPORARY_BLOCK", "EXIT_ONLY", "LOG_ONLY").forEach { value ->
                        DropdownMenuItem(text = { Text(reactionLabel(value, english)) }, onClick = { reaction = value; reactionExpanded = false })
                    }
                }
                OutlinedTextField(
                    value = duration,
                    onValueChange = { duration = it.filter(Char::isDigit) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(if (english) "Block duration in minutes (0 = no timed block)" else "مدت مسدودی به دقیقه (۰ = بدون مسدودی زمان‌دار)") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = warning,
                    onValueChange = { warning = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(if (english) "Custom warning (optional)" else "هشدار سفارشی (اختیاری)") },
                    minLines = 2
                )
                Text(if (english) "Target apps (none selected = all monitored apps)" else "برنامه‌های هدف (بدون انتخاب = همهٔ برنامه‌های تحت نظارت)", style = MaterialTheme.typography.labelMedium)
                Column(modifier = Modifier.fillMaxWidth().weight(1f, fill = false)) {
                    apps.filter { it.monitoringEnabled }.forEach { app ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = app.packageName in targets,
                                onCheckedChange = { checked ->
                                    targets = if (checked) targets + app.packageName else targets - app.packageName
                                }
                            )
                            Text(app.appName, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    if (apps.none { it.monitoringEnabled }) {
                        Text(if (english) "Enable app monitoring in the Apps section first." else "ابتدا برنامه‌ها را در بخش «برنامه‌ها» برای نظارت فعال کنید.", style = MaterialTheme.typography.bodySmall)
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = enabled, onCheckedChange = { enabled = it })
                    Text(if (english) "Enable this rule" else "قانون فعال باشد")
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = statistics, onCheckedChange = { statistics = it })
                    Text(if (english) "Record detection statistics" else "ثبت آمار تشخیص")
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            Button(onClick = {
                val clean = text.trim()
                val minutes = duration.toLongOrNull()
                when {
                    clean.isEmpty() -> error = if (english) "Enter a word or phrase." else "واژه یا عبارت را وارد کنید."
                    WordNormalizer.normalize(clean).isBlank() -> error = if (english) "Text is invalid after normalization." else "متن واردشده پس از نرمال‌سازی معتبر نیست."
                    minutes == null || minutes > 1440L -> error = if (english) "Duration must be between 0 and 1440 minutes." else "مدت باید بین ۰ تا ۱۴۴۰ دقیقه باشد."
                    else -> {
                        val now = System.currentTimeMillis()
                        onSave(
                            WordRuleEntity(
                                id = initial?.id ?: 0,
                                text = clean,
                                normalizedText = WordNormalizer.normalize(clean),
                                category = category,
                                language = language,
                                enabled = enabled,
                                matchMode = matchMode,
                                reaction = reaction,
                                blockDurationMillis = minutes * 60_000L,
                                customWarning = warning.trim().takeIf { it.isNotEmpty() },
                                targetPackages = targets.sorted().joinToString("|"),
                                statisticsEnabled = statistics,
                                detectionCount = initial?.detectionCount ?: 0,
                                createdAt = initial?.createdAt ?: now,
                                updatedAt = now
                            )
                        )
                    }
                }
            }) { Text(if (english) "Save" else "ذخیره") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } }
    )
}

@Composable
private fun EditorDropdown(
    label: String,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    content: @Composable () -> Unit
) {
    Column {
        OutlinedButton(onClick = { onExpandedChange(true) }, modifier = Modifier.fillMaxWidth()) {
            Text(label)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { onExpandedChange(false) }) { content() }
    }
}

private fun categoryLabel(value: String, english: Boolean = false) = when (value) {
    "GENERAL" -> if (english) "General" else "عمومی"
    "SOCIAL" -> if (english) "Social" else "شبکه اجتماعی"
    "GAMES" -> if (english) "Games" else "بازی"
    "VIDEO" -> if (english) "Video" else "ویدئو"
    "ADULT" -> if (english) "Inappropriate content" else "محتوای نامناسب"
    "CUSTOM" -> if (english) "Custom" else "سفارشی"
    else -> value
}

private fun languageLabel(value: String, english: Boolean = false) = when (value) {
    "AUTO" -> if (english) "Automatic" else "تشخیص خودکار"
    "FA" -> if (english) "Persian" else "فارسی"
    "EN" -> if (english) "English" else "انگلیسی"
    else -> value
}

private fun modeLabel(value: String, english: Boolean = false) = when (value) {
    "EXACT" -> if (english) "Exact match" else "تطبیق دقیق"
    "PHRASE" -> if (english) "Phrase" else "عبارت"
    "NORMALIZED_WORD" -> if (english) "Normalized word" else "واژه نرمال‌شده"
    "WORD_BOUNDARY" -> if (english) "Word boundary" else "مرز واژه"
    "CONTAINS" -> if (english) "Contains text" else "شامل متن"
    else -> value
}

private fun reactionLabel(value: String, english: Boolean = false) = when (value) {
    "WARN_AND_EXIT" -> if (english) "Warn and exit" else "هشدار و خروج"
    "TEMPORARY_BLOCK" -> if (english) "Temporary block" else "مسدودی موقت"
    "EXIT_ONLY" -> if (english) "Exit app" else "خروج از برنامه"
    "LOG_ONLY" -> if (english) "Log only" else "فقط ثبت گزارش"
    else -> value
}
