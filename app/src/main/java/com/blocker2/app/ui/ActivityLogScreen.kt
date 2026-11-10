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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.blocker2.app.data.local.dao.ActivityLogDao
import com.blocker2.app.data.local.entity.ActivityLogEntity
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ActivityLogScreen(dao: ActivityLogDao) {
    val entries by dao.observeRecent(300).collectAsState(initial = emptyList())
    var search by remember { mutableStateOf("") }
    var showClearConfirmation by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val filteredEntries = remember(entries, search) {
        val query = search.trim().lowercase(Locale.ROOT)
        if (query.isEmpty()) entries else entries.filter { entry ->
            listOfNotNull(
                entry.sourceLabel,
                entry.sourcePackage,
                entry.matchedValue,
                entry.ruleType,
                entry.reaction,
                entry.outcome
            ).any { it.lowercase(Locale.ROOT).contains(query) }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("گزارش‌های ثبت‌شده", style = MaterialTheme.typography.titleMedium)
                Text(
                    "نمایش ${filteredEntries.size} مورد از ${entries.size} رویداد اخیر",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            TextButton(
                onClick = { showClearConfirmation = true },
                enabled = entries.isNotEmpty()
            ) {
                Text("پاک‌کردن گزارش‌ها")
            }
        }

        OutlinedTextField(
            value = search,
            onValueChange = { search = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("جست‌وجو در گزارش‌ها") },
            placeholder = { Text("نام برنامه، واژه یا واکنش") },
            singleLine = true
        )

        if (filteredEntries.isEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        if (entries.isEmpty()) "هنوز گزارشی ثبت نشده است"
                        else "موردی با این عبارت پیدا نشد",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        if (entries.isEmpty())
                            "پس از شناسایی یک قانون، رویداد آن در این صفحه نمایش داده می‌شود."
                        else "عبارت جست‌وجو را تغییر دهید.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filteredEntries, key = { it.id }) { entry ->
                    ActivityLogCard(entry)
                }
            }
        }
    }

    if (showClearConfirmation) {
        AlertDialog(
            onDismissRequest = { showClearConfirmation = false },
            title = { Text("پاک‌کردن همهٔ گزارش‌ها؟") },
            text = { Text("این عملیات همهٔ رویدادهای ذخیره‌شده را برای همیشه از دستگاه حذف می‌کند.") },
            confirmButton = {
                Button(onClick = {
                    showClearConfirmation = false
                    scope.launch { dao.clearAll() }
                }) { Text("پاک‌کردن") }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirmation = false }) { Text("انصراف") }
            }
        )
    }
}

@Composable
private fun ActivityLogCard(entry: ActivityLogEntity) {
    val dateText = remember(entry.occurredAt) {
        SimpleDateFormat("yyyy/MM/dd  HH:mm:ss", Locale.getDefault())
            .format(Date(entry.occurredAt))
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    entry.sourceLabel?.takeIf { it.isNotBlank() } ?: entry.sourcePackage,
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    outcomeLabel(entry.outcome),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Text(
                "مورد شناسایی‌شده: ${entry.matchedValue}",
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                "نوع قانون: ${ruleTypeLabel(entry.ruleType)}  •  واکنش: ${reactionLabel(entry.reaction)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (entry.durationMillis > 0L) {
                Text(
                    "مدت مسدودی: ${entry.durationMillis / 60_000L} دقیقه",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Text(
                dateText,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                entry.sourcePackage,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline
            )
        }
    }
}

private fun ruleTypeLabel(value: String): String = when (value.uppercase(Locale.ROOT)) {
    "WORD" -> "واژه"
    "TELEGRAM" -> "تلگرام"
    "SITE" -> "سایت"
    else -> value
}

private fun reactionLabel(value: String): String = when (value.uppercase(Locale.ROOT)) {
    "WARN_AND_EXIT" -> "هشدار و خروج"
    "TEMPORARY_BLOCK" -> "مسدودی موقت"
    "EXIT_ONLY" -> "خروج از برنامه"
    "LOG_ONLY" -> "فقط ثبت گزارش"
    else -> value
}

private fun outcomeLabel(value: String): String = when (value.uppercase(Locale.ROOT)) {
    "DETECTED" -> "شناسایی شد"
    "EXIT_REQUESTED" -> "درخواست خروج"
    "TEMPORARY_BLOCK_STARTED" -> "مسدودی آغاز شد"
    else -> value
}
