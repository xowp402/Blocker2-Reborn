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
fun BackupScreen(database: BlockerDatabase) {
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
                "فایل پشتیبان با موفقیت ذخیره شد."
            } catch (error: Exception) {
                "ذخیرهٔ پشتیبان انجام نشد: ${error.message ?: "خطای ناشناخته"}"
            } finally { busy = false }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> pendingRestoreUri = uri }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("پشتیبان‌گیری از اطلاعات", style = MaterialTheme.typography.titleMedium)
                Text(
                    "قوانین واژه و تلگرام، دامنه‌ها، برنامه‌های انتخاب‌شده، گزارش‌ها، مسدودی‌های فعال و جلسهٔ تعهد در یک فایل JSON ذخیره می‌شوند.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Button(
                    onClick = { exportLauncher.launch("blocker2-backup.json") },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("ساخت فایل پشتیبان") }
            }
        }
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("بازیابی اطلاعات", style = MaterialTheme.typography.titleMedium)
                Text(
                    "بازیابی، اطلاعات فعلی برنامه را با اطلاعات داخل فایل جایگزین می‌کند. فایل ابتدا بررسی می‌شود و جایگزینی همهٔ جدول‌ها در یک تراکنش انجام می‌شود.",
                    style = MaterialTheme.typography.bodyMedium
                )
                OutlinedButton(
                    onClick = { importLauncher.launch(arrayOf("application/json", "text/json")) },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("انتخاب فایل پشتیبان") }
            }
        }
        status?.let {
            Text(it, color = if (it.startsWith("فایل پشتیبان با موفقیت")) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
        }
        if (busy) Text("در حال پردازش فایل…", style = MaterialTheme.typography.bodySmall)
    }

    pendingRestoreUri?.let { uri ->
        AlertDialog(
            onDismissRequest = { pendingRestoreUri = null },
            title = { Text("جایگزینی اطلاعات فعلی؟") },
            text = { Text("همهٔ قوانین، برنامه‌های انتخاب‌شده، گزارش‌ها و وضعیت‌های ذخیره‌شده با نسخهٔ داخل فایل جایگزین می‌شوند. قبل از ادامه مطمئن شو از اطلاعات فعلی پشتیبان داری.") },
            confirmButton = {
                TextButton(onClick = {
                    pendingRestoreUri = null
                    scope.launch {
                        busy = true
                        status = try {
                            BlockerBackupManager.importFrom(context.contentResolver, uri, database)
                            "بازیابی اطلاعات با موفقیت انجام شد."
                        } catch (error: Exception) {
                            "بازیابی انجام نشد: ${error.message ?: "خطای ناشناخته"}"
                        } finally { busy = false }
                    }
                }) { Text("بازیابی و جایگزینی") }
            },
            dismissButton = {
                TextButton(onClick = { pendingRestoreUri = null }) { Text("انصراف") }
            }
        )
    }
}
