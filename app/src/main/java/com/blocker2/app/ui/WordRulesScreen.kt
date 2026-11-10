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
fun WordRulesScreen(wordDao: WordRuleDao, appDao: MonitoredAppDao) {
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
                Text("قوانین واژه‌ها", style = MaterialTheme.typography.titleMedium)
                Text(
                    "${rules.count { it.enabled }} فعال از ${rules.size} قانون",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Button(onClick = { creating = true }) { Text("افزودن قانون") }
        }

        if (rules.isEmpty()) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("هنوز قانونی ثبت نشده است", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "یک واژه یا عبارت اضافه کنید تا موتور نظارت بتواند آن را در برنامه‌های انتخاب‌شده بررسی کند.",
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
                                        "${categoryLabel(rule.category)} • ${reactionLabel(rule.reaction)}",
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
                                "تعداد تشخیص: ${rule.detectionCount} • مدت مسدودی: ${rule.blockDurationMillis / 60_000L} دقیقه",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton(onClick = { editing = rule }) { Text("ویرایش") }
                                TextButton(onClick = { deleteTarget = rule }) { Text("حذف") }
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
            title = { Text("حذف قانون؟") },
            text = { Text("قانون «${target.text}» حذف خواهد شد.") },
            confirmButton = {
                Button(onClick = {
                    scope.launch { wordDao.deleteById(target.id) }
                    deleteTarget = null
                }) { Text("حذف") }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("انصراف") } }
        )
    }
}

@Composable
private fun RuleEditorDialog(
    initial: WordRuleEntity?,
    apps: List<com.blocker2.app.data.local.entity.MonitoredAppEntity>,
    onDismiss: () -> Unit,
    onSave: (WordRuleEntity) -> Unit
) {
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
        title = { Text(if (initial == null) "قانون جدید" else "ویرایش قانون") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it; error = null },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("واژه یا عبارت") },
                    singleLine = true
                )
                EditorDropdown("دسته‌بندی: ${categoryLabel(category)}", categoryExpanded, { categoryExpanded = it }) {
                    listOf("GENERAL", "SOCIAL", "GAMES", "VIDEO", "ADULT", "CUSTOM").forEach { value ->
                        DropdownMenuItem(text = { Text(categoryLabel(value)) }, onClick = { category = value; categoryExpanded = false })
                    }
                }
                EditorDropdown("زبان: ${languageLabel(language)}", languageExpanded, { languageExpanded = it }) {
                    listOf("AUTO", "FA", "EN").forEach { value ->
                        DropdownMenuItem(text = { Text(languageLabel(value)) }, onClick = { language = value; languageExpanded = false })
                    }
                }
                EditorDropdown("روش تطبیق: ${modeLabel(matchMode)}", modeExpanded, { modeExpanded = it }) {
                    RuleMatchMode.entries.forEach { value ->
                        DropdownMenuItem(text = { Text(modeLabel(value.name)) }, onClick = { matchMode = value.name; modeExpanded = false })
                    }
                }
                EditorDropdown("واکنش: ${reactionLabel(reaction)}", reactionExpanded, { reactionExpanded = it }) {
                    listOf("WARN_AND_EXIT", "TEMPORARY_BLOCK", "EXIT_ONLY", "LOG_ONLY").forEach { value ->
                        DropdownMenuItem(text = { Text(reactionLabel(value)) }, onClick = { reaction = value; reactionExpanded = false })
                    }
                }
                OutlinedTextField(
                    value = duration,
                    onValueChange = { duration = it.filter(Char::isDigit) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("مدت مسدودی به دقیقه (۰ = بدون مسدودی زمان‌دار)") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = warning,
                    onValueChange = { warning = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("هشدار سفارشی (اختیاری)") },
                    minLines = 2
                )
                Text("برنامه‌های هدف (بدون انتخاب = همهٔ برنامه‌های تحت نظارت)", style = MaterialTheme.typography.labelMedium)
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
                        Text("ابتدا برنامه‌ها را در بخش «برنامه‌ها» برای نظارت فعال کنید.", style = MaterialTheme.typography.bodySmall)
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = enabled, onCheckedChange = { enabled = it })
                    Text("قانون فعال باشد")
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = statistics, onCheckedChange = { statistics = it })
                    Text("ثبت آمار تشخیص")
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            Button(onClick = {
                val clean = text.trim()
                val minutes = duration.toLongOrNull()
                when {
                    clean.isEmpty() -> error = "واژه یا عبارت را وارد کنید."
                    WordNormalizer.normalize(clean).isBlank() -> error = "متن واردشده پس از نرمال‌سازی معتبر نیست."
                    minutes == null || minutes > 1440L -> error = "مدت باید بین ۰ تا ۱۴۴۰ دقیقه باشد."
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
            }) { Text("ذخیره") }
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

private fun categoryLabel(value: String) = when (value) {
    "GENERAL" -> "عمومی"
    "SOCIAL" -> "شبکه اجتماعی"
    "GAMES" -> "بازی"
    "VIDEO" -> "ویدئو"
    "ADULT" -> "محتوای نامناسب"
    "CUSTOM" -> "سفارشی"
    else -> value
}

private fun languageLabel(value: String) = when (value) {
    "AUTO" -> "تشخیص خودکار"
    "FA" -> "فارسی"
    "EN" -> "انگلیسی"
    else -> value
}

private fun modeLabel(value: String) = when (value) {
    "EXACT" -> "تطبیق دقیق"
    "PHRASE" -> "عبارت"
    "NORMALIZED_WORD" -> "واژه نرمال‌شده"
    "WORD_BOUNDARY" -> "مرز واژه"
    "CONTAINS" -> "شامل متن"
    else -> value
}

private fun reactionLabel(value: String) = when (value) {
    "WARN_AND_EXIT" -> "هشدار و خروج"
    "TEMPORARY_BLOCK" -> "مسدودی موقت"
    "EXIT_ONLY" -> "خروج از برنامه"
    "LOG_ONLY" -> "فقط ثبت گزارش"
    else -> value
}
