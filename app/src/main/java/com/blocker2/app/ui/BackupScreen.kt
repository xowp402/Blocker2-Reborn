package com.blocker2.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.blocker2.app.data.local.BlockerBackupManager
import com.blocker2.app.data.local.BlockerDatabase
import kotlinx.coroutines.launch

@Composable
fun BackupScreen(database: BlockerDatabase, language: String) {
    val english = language == "en"
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf<String?>(null) }
    var pendingRestoreUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var busy by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) scope.launch {
            busy = true
            status = try {
                BlockerBackupManager.exportTo(context.contentResolver, uri, database)
                if (english) "Backup saved successfully." else "فایل پشتیبان با موفقیت ذخیره شد."
            } catch (error: Exception) {
                if (english) "Backup failed: ${error.message ?: "Unknown error"}" else "ذخیرهٔ پشتیبان انجام نشد: ${error.message ?: "خطای ناشناخته"}"
            } finally { busy = false }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> pendingRestoreUri = uri }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(if (english) "Back up your data" else "پشتیبان‌گیری از اطلاعات", style = MaterialTheme.typography.titleMedium)
                Text(
                    if (english) "Word and Telegram rules, domains, selected apps, activity logs, active blocks, and the commitment session are saved in a JSON file." else "قوانین واژه و تلگرام، دامنه‌ها، برنامه‌های انتخاب‌شده، گزارش‌ها، مسدودی‌های فعال و جلسهٔ تعهد در یک فایل JSON ذخیره می‌شوند.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Button(
                    onClick = { exportLauncher.launch("blocker2-backup.json") },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth()
                ) { Text(if (english) "Create backup file" else "ساخت فایل پشتیبان") }
            }
        }
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(if (english) "Restore data" else "بازیابی اطلاعات", style = MaterialTheme.typography.titleMedium)
                Text(
                    if (english) "Restore replaces current app data with the contents of the file. The file is validated first, then all tables are replaced in one transaction." else "بازیابی، اطلاعات فعلی برنامه را با اطلاعات داخل فایل جایگزین می‌کند. فایل ابتدا بررسی می‌شود و جایگزینی همهٔ جدول‌ها در یک تراکنش انجام می‌شود.",
                    style = MaterialTheme.typography.bodyMedium
                )
                OutlinedButton(
                    onClick = { importLauncher.launch(arrayOf("application/json", "text/json")) },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth()
                ) { Text(if (english) "Choose backup file" else "انتخاب فایل پشتیبان") }
            }
        }
        status?.let {
            Text(it, color = if (it.startsWith(if (english) "Backup saved successfully." else "فایل پشتیبان با موفقیت")) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
        }
        if (busy) Text(if (english) "Processing file…" else "در حال پردازش فایل…", style = MaterialTheme.typography.bodySmall)
    }

    pendingRestoreUri?.let { uri ->
        AlertDialog(
            onDismissRequest = { pendingRestoreUri = null },
            title = { Text(if (english) "Replace current data?" else "جایگزینی اطلاعات فعلی؟") },
            text = { Text(if (english) "All rules, selected apps, logs, and saved states will be replaced by the backup. Make sure you have a backup of current data before continuing." else "همهٔ قوانین، برنامه‌های انتخاب‌شده، گزارش‌ها و وضعیت‌های ذخیره‌شده با نسخهٔ داخل فایل جایگزین می‌شوند. قبل از ادامه مطمئن شو از اطلاعات فعلی پشتیبان داری.") },
            confirmButton = {
                TextButton(onClick = {
                    pendingRestoreUri = null
                    scope.launch {
                        busy = true
                        status = try {
                            BlockerBackupManager.importFrom(context.contentResolver, uri, database)
                            if (english) "Data restored successfully." else "بازیابی اطلاعات با موفقیت انجام شد."
                        } catch (error: Exception) {
                            if (english) "Restore failed: ${error.message ?: "Unknown error"}" else "بازیابی انجام نشد: ${error.message ?: "خطای ناشناخته"}"
                        } finally { busy = false }
                    }
                }) { Text(if (english) "Restore and replace" else "بازیابی و جایگزینی") }
            },
            dismissButton = {
                TextButton(onClick = { pendingRestoreUri = null }) { Text(if (english) "Cancel" else "انصراف") }
            }
        )
    }
}
