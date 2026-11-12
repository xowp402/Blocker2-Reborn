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
import java.util.Calendar
import java.util.Locale

@Composable
fun ActivityLogScreen(dao: ActivityLogDao, language: String) {
    val english = language == "en";
    val entries by dao.observeRecent(300).collectAsState(initial = emptyList())
    val todayStart = remember {
        Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }
    val todayTotal by dao.observeCountSince(todayStart).collectAsState(initial = 0)
    val todayWords by dao.observeCountSinceForType(todayStart, "WORD").collectAsState(initial = 0)
    val todayTelegram by dao.observeCountSinceForType(todayStart, "TELEGRAM").collectAsState(initial = 0)
    val todayCommitment by dao.observeCountSinceForType(todayStart, "COMMITMENT").collectAsState(initial = 0)
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
                Text(if (english) "Recorded activity" else "گزارش‌های ثبت‌شده", style = MaterialTheme.typography.titleMedium)
                Text(
                    if (english) "Showing ${filteredEntries.size} of ${entries.size} recent events" else "نمایش ${filteredEntries.size} مورد از ${entries.size} رویداد اخیر",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            TextButton(
                onClick = { showClearConfirmation = true },
                enabled = entries.isNotEmpty()
            ) {
                Text(if (english) "Clear logs" else "پاک‌کردن گزارش‌ها")
            }
        }

        OutlinedTextField(
            value = search,
            onValueChange = { search = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(if (english) "Search activity" else "جست‌وجو در گزارش‌ها") },
            placeholder = { Text(if (english) "App, word, or action" else "نام برنامه، واژه یا واکنش") },
            singleLine = true
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Text(if (english) "Today's summary" else "خلاصهٔ امروز", style = MaterialTheme.typography.titleSmall)
                Text(if (english) "Total recorded events: $todayTotal" else "مجموع رویدادهای ثبت‌شده: $todayTotal")
                Text(if (english) "Word detections: $todayWords  •  Telegram: $todayTelegram" else "شناسایی واژه‌ها: $todayWords  •  تلگرام: $todayTelegram")
                Text(if (english) "Commitment actions: $todayCommitment" else "اجرای حالت تعهد: $todayCommitment")
                Text(
                    if (english) "Uses the device's local time. Each row is a recorded match, not necessarily an app launch." else "بر اساس زمان محلی دستگاه؛ هر ردیف گزارش یک تطبیق ثبت‌شده است، نه الزاماً یک بار بازشدن برنامه.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

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
                        if (entries.isEmpty()) { if (english) "No activity recorded yet" else "هنوز گزارشی ثبت نشده است" }
                        else { if (english) "No matches found" else "موردی با این عبارت پیدا نشد" },
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        if (entries.isEmpty()) {
                            if (english) "Events will appear here after a rule is detected."
                            else "پس از شناسایی یک قانون، رویداد آن در این صفحه نمایش داده می‌شود."
                        } else {
                            if (english) "Try a different search term."
                            else "عبارت جست‌وجو را تغییر دهید."
                        },
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
                    ActivityLogCard(entry, language)
                }
            }
        }
    }

    if (showClearConfirmation) {
        AlertDialog(
            onDismissRequest = { showClearConfirmation = false },
            title = { Text(if (english) "Clear all activity?" else "پاک‌کردن همهٔ گزارش‌ها؟") },
            text = { Text(if (english) "This permanently deletes all saved events from this device." else "این عملیات همهٔ رویدادهای ذخیره‌شده را برای همیشه از دستگاه حذف می‌کند.") },
            confirmButton = {
                Button(onClick = {
                    showClearConfirmation = false
                    scope.launch { dao.clearAll() }
                }) { Text(if (english) "Clear" else "پاک‌کردن") }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirmation = false }) { Text(if (english) "Cancel" else "انصراف") }
            }
        )
    }
}

@Composable
private fun ActivityLogCard(entry: ActivityLogEntity, language: String) {
    val english = language == "en";
    val dateText = remember(entry.occurredAt) {
        SimpleDateFormat(if (english) "MMM d, yyyy  HH:mm:ss" else "yyyy/MM/dd  HH:mm:ss", if (english) Locale.US else Locale.getDefault())
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
                    outcomeLabel(entry.outcome, language),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Text(
                if (english) "Matched item: ${entry.matchedValue}" else "مورد شناسایی‌شده: ${entry.matchedValue}",
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                if (english) "Rule: ${ruleTypeLabel(entry.ruleType, language)}  •  Action: ${reactionLabel(entry.reaction, language)}" else "نوع قانون: ${ruleTypeLabel(entry.ruleType, language)}  •  واکنش: ${reactionLabel(entry.reaction, language)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (entry.durationMillis > 0L) {
                Text(
                    if (english) "Block duration: ${entry.durationMillis / 60_000L} min" else "مدت مسدودی: ${entry.durationMillis / 60_000L} دقیقه",
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

private fun ruleTypeLabel(value: String, language: String): String = when (value.uppercase(Locale.ROOT)) {
    "WORD" -> if (language == "en") "Word" else "واژه"
    "TELEGRAM" -> if (language == "en") "Telegram" else "تلگرام"
    "SITE" -> if (language == "en") "Site" else "سایت"
    "COMMITMENT" -> if (language == "en") "Commitment" else "حالت تعهد"
    else -> value
}

private fun reactionLabel(value: String, language: String): String = when (value.uppercase(Locale.ROOT)) {
    "WARN_AND_EXIT" -> if (language == "en") "Warn and exit" else "هشدار و خروج"
    "TEMPORARY_BLOCK" -> if (language == "en") "Temporary block" else "مسدودی موقت"
    "EXIT_ONLY" -> if (language == "en") "Exit app" else "خروج از برنامه"
    "LOG_ONLY" -> if (language == "en") "Log only" else "فقط ثبت گزارش"
    else -> value
}

private fun outcomeLabel(value: String, language: String): String = when (value.uppercase(Locale.ROOT)) {
    "DETECTED" -> if (language == "en") "Detected" else "شناسایی شد"
    "EXIT_REQUESTED" -> if (language == "en") "Exit requested" else "درخواست خروج"
    "TEMPORARY_BLOCK_STARTED" -> if (language == "en") "Temporary block started" else "مسدودی آغاز شد"
    "COMMITMENT_ENFORCED" -> if (language == "en") "Commitment enforced" else "تعهد اجرا شد"
    else -> value
}
